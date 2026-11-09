<template>
  <div>
    <div class="page-head">
      <div>
        <h1>约束</h1>
        <p class="muted small mb-0">
          约束 = 一条"不许违反的规矩"。<b>ENFORCED</b> 违反即拒绝（HTTP 422 并告诉你改法），
          <b>WARN</b> 只记录告警、照样放行。所有写操作（编辑 / 归档 / 贡献发布）都会先过这一关。
        </p>
      </div>
      <div class="row">
        <button class="btn" @click="validateAll">全库校验</button>
        <button class="btn primary" :disabled="!app.isAdmin" @click="openCreate">
          {{ app.isAdmin ? '新建约束' : '新建（需管理员）' }}
        </button>
      </div>
    </div>

    <SkTable :columns="columns" :rows="rows" :loading="loading" empty-text="还没有约束">
      <template #constraint_type="{ row }">
        <span class="badge info">{{ row.constraint_label }}</span>
        <span class="tiny dim mono">{{ row.constraint_type }}</span>
      </template>
      <template #target_column="{ row }"><code class="mono">{{ row.target_column || '—' }}</code></template>
      <template #expression="{ row }"><code class="mono tiny">{{ row.expression || '—' }}</code></template>
      <template #severity="{ row }">
        <span class="badge" :class="row.severity === 'WARN' ? 'warn' : 'danger'">{{ row.severity }}</span>
      </template>
      <template #ops="{ row }">
        <button class="btn sm danger" :disabled="!app.isAdmin" @click="remove(row)">删除</button>
      </template>
    </SkTable>

    <!-- 校验结果 -->
    <div class="section-title" v-if="validation.length">
      <h2>全库校验结果</h2>
      <span class="dim small">对当前库里的每个 Skill 跑一遍所有约束</span>
    </div>
    <div class="card" v-if="validation.length">
      <div class="card-body">
        <div v-for="v in validation" :key="v.code" class="valid-row">
          <div class="row between">
            <b class="small">{{ v.code }} · {{ v.name }}</b>
            <span class="badge" :class="v.violated ? 'danger' : 'ok'">
              {{ v.violated ? `违反 ${v.violated} 个` : '全部通过' }}
            </span>
          </div>
          <ul v-if="v.samples.length" class="tiny dim mb-0">
            <li v-for="s in v.samples" :key="s">{{ s }}</li>
          </ul>
        </div>
      </div>
    </div>

    <SkModal :open="creating" title="新建约束" width="720px" @close="creating = false">
      <div class="col">
        <div class="two">
          <div class="field">
            <label>约束代号</label>
            <input v-model="form.code" class="input" placeholder="ck_risk_max" />
          </div>
          <div class="field">
            <label>名称</label>
            <input v-model="form.name" class="input" placeholder="风险分不超过 80" />
          </div>
        </div>
        <div class="field">
          <label>类型</label>
          <select v-model="form.constraint_type" class="select">
            <option v-for="t in types" :key="t.key" :value="t.key">{{ t.label }}（{{ t.key }}）</option>
          </select>
          <span class="hint">{{ types.find((t) => t.key === form.constraint_type)?.hint }}</span>
        </div>
        <div class="two">
          <div class="field">
            <label>作用列</label>
            <input v-model="form.target_column" class="input" placeholder="risk_score" />
          </div>
          <div class="field">
            <label>严重度</label>
            <select v-model="form.severity" class="select">
              <option value="ENFORCED">ENFORCED（违反即拒绝）</option>
              <option value="WARN">WARN（只告警）</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>表达式</label>
          <input v-model="form.expression" class="input" :placeholder="placeholder" />
          <span class="hint">{{ types.find((t) => t.key === form.constraint_type)?.example }}</span>
        </div>
      </div>
      <template #footer>
        <button class="btn" @click="creating = false">取消</button>
        <button class="btn primary" :disabled="busy" @click="save">保存</button>
      </template>
    </SkModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import SkModal from '@/components/SkModal.vue';
import { deleteConstraint, listConstraints, saveConstraint, validateConstraints } from '@/api/ms';
import { notifyError } from '@/api/client';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();

const rows = ref([]);
const validation = ref([]);
const loading = ref(true);
const creating = ref(false);
const busy = ref(false);

const types = [
  { key: 'NOT_NULL', label: '非空', hint: '目标列不能为空', example: '表达式留空' },
  { key: 'UNIQUE', label: '唯一', hint: '全库唯一（更新时排除自身）', example: '表达式留空' },
  { key: 'RANGE', label: '数值区间', hint: '数值上下限，可只写一边', example: 'min=0,max=80' },
  { key: 'ENUM', label: '枚举白名单', hint: '值必须在此列表内', example: 'published,archived' },
  { key: 'REGEX', label: '正则格式', hint: '值需要匹配该正则', example: '^[a-z0-9-]+$' },
  { key: 'CHECK_EXPR', label: '表达式', hint: '单列二元比较', example: 'risk_score<=80' },
  { key: 'REFERENCE', label: '引用完整性', hint: '目标值必须存在于另一张表', example: 'skillset_code->skillset.code' },
];

const form = reactive({
  code: '',
  name: '',
  constraint_type: 'RANGE',
  target_column: '',
  expression: '',
  severity: 'ENFORCED',
});

const placeholder = computed(() => types.find((t) => t.key === form.constraint_type)?.example || '');

const columns = [
  { key: 'code', label: '代号', width: '160px' },
  { key: 'name', label: '含义' },
  { key: 'constraint_type', label: '类型', width: '130px' },
  { key: 'target_column', label: '列', width: '120px' },
  { key: 'expression', label: '表达式', width: '180px' },
  { key: 'severity', label: '严重度', width: '100px' },
  { key: 'violation_count', label: '累计拦截', width: '90px', align: 'right' },
  { key: 'ops', label: '操作', width: '80px' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listConstraints();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, {
    code: '',
    name: '',
    constraint_type: 'RANGE',
    target_column: '',
    expression: '',
    severity: 'ENFORCED',
  });
  creating.value = true;
}

async function save() {
  busy.value = true;
  try {
    await saveConstraint(null, { ...form });
    toast.ok('约束已保存');
    creating.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function remove(row) {
  if (!window.confirm(`确认删除约束 ${row.code}？`)) return;
  try {
    await deleteConstraint(row.code);
    toast.ok('已删除');
    await load();
  } catch (e) {
    notifyError(e);
  }
}

async function validateAll() {
  try {
    validation.value = await validateConstraints();
    toast.ok(`校验完成：${validation.value.length} 条约束`);
  } catch (e) {
    notifyError(e);
  }
}

onMounted(load);
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sk-s4);
  margin-bottom: var(--sk-s4);
  flex-wrap: wrap;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.valid-row {
  padding: var(--sk-s2) 0;
  border-bottom: 1px dashed var(--sk-border);
}
.valid-row:last-child {
  border-bottom: none;
}
@media (max-width: 900px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

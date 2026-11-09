<template>
  <div>
    <div class="page-head">
      <div>
        <h1>实测中心</h1>
        <p class="muted small mb-0">
          三种实验，分别回答三个问题：
          <b>(a)</b> 一次调用好不好？ <b>(b)</b> 同一个 Skill 换模型/换参数会怎样？
          <b>(c)</b> 不同 Skill 在同一条件下谁更强？
        </p>
      </div>
      <RouterLink class="btn" to="/eval/live">单体实测（a）→</RouterLink>
    </div>

    <!-- 三种实验 -->
    <div class="grid wide">
      <div class="card exp">
        <div class="row between"><b>(a) 单体实测</b><span class="badge info">已就绪</span></div>
        <p class="small muted mt-2">
          一个 Skill + 一个 Agent + 一组超参，问一个问题，看输出与推理过程，并与期望对比。
        </p>
        <RouterLink class="btn primary sm" to="/eval/live">去实测</RouterLink>
      </div>
      <div class="card exp">
        <div class="row between"><b>(b) Skill 维度扫描</b><span class="badge info">SKILL_SCAN</span></div>
        <p class="small muted mt-2">
          固定一个 Skill，换多个 Agent、多组超参、多个用例，看它的表现在各种条件下如何变化。
        </p>
        <button class="btn primary sm" @click="openCreate('SKILL_SCAN')">创建实验</button>
      </div>
      <div class="card exp">
        <div class="row between"><b>(c) Skill 横向对比</b><span class="badge info">SKILL_COMPARE</span></div>
        <p class="small muted mt-2">
          固定条件（一个 Agent、一组参数、一个场景），让多个 Skill 同时跑，比谁更强。
        </p>
        <button class="btn primary sm" @click="openCreate('SKILL_COMPARE')">创建实验</button>
      </div>
    </div>

    <!-- 矩阵列表 -->
    <div class="section-title">
      <h2>已有实验</h2>
      <button class="btn sm" @click="load">刷新</button>
    </div>
    <SkTable :columns="columns" :rows="rows" :loading="loading" empty-text="还没有实验，点上面创建第一个">
      <template #status="{ row }">
        <span class="badge" :class="row.status === 'DONE' ? 'ok' : row.status === 'FAILED' ? 'danger' : 'warn'">
          {{ row.status }}
        </span>
      </template>
      <template #progress="{ row }">{{ row.done_cells }}/{{ row.planned_cells }}</template>
      <template #ops="{ row }">
        <div class="row">
          <RouterLink class="btn sm" :to="`/eval/matrix/${row.code}`">详情</RouterLink>
          <button class="btn sm primary" :disabled="busy === row.code" @click="run(row)">
            {{ busy === row.code ? '执行中…' : '执行' }}
          </button>
        </div>
      </template>
    </SkTable>

    <!-- 创建 -->
    <SkModal :open="creating" :title="`创建实验 · ${form.matrix_type}`" width="820px" @close="creating = false">
      <div class="col">
        <div class="field">
          <label>实验名称</label>
          <input v-model="form.name" class="input" placeholder="例如：pdf Skill 在三台模型上的表现" />
        </div>
        <div v-if="form.matrix_type === 'SKILL_COMPARE'" class="field">
          <label>要比的 Skill（多选）</label>
          <div class="pick-list">
            <label v-for="s in skillOptions" :key="s.skill_id" class="checkbox">
              <input v-model="form.skill_ids" type="checkbox" :value="s.skill_id" />
              {{ s.name }}（{{ s.category_label }}）
            </label>
          </div>
        </div>
        <div v-else class="field">
          <label>被测 Skill</label>
          <select v-model="singleSkill" class="select">
            <option v-for="s in skillOptions" :key="s.skill_id" :value="s.skill_id">{{ s.name }}</option>
          </select>
        </div>
        <div class="two">
          <div class="field">
            <label>Agent（可多选）</label>
            <div class="pick-list">
              <label v-for="a in agents" :key="a.code" class="checkbox">
                <input v-model="form.agent_codes" type="checkbox" :value="a.code" /> {{ a.name }}
              </label>
            </div>
          </div>
          <div class="field">
            <label>MCP（可多选）</label>
            <div class="pick-list">
              <label v-for="m in mcps" :key="m.code" class="checkbox">
                <input v-model="form.mcp_codes" type="checkbox" :value="m.code" /> {{ m.name }}
              </label>
            </div>
          </div>
        </div>
        <div class="two">
          <div class="field">
            <label>发散程度（可多选）</label>
            <div class="pick-list">
              <label v-for="t in [0, 0.3, 0.7]" :key="t" class="checkbox">
                <input v-model="temperatures" type="checkbox" :value="t" /> {{ t }}
              </label>
            </div>
          </div>
          <div class="field">
            <label>场景</label>
            <select v-model="form.scene_code" class="select">
              <option value="general">通用</option>
              <option value="edu">教育</option>
              <option value="fin">金融</option>
              <option value="gov">政务与法务</option>
              <option value="med">医疗健康</option>
              <option value="news">新闻传媒</option>
              <option value="tra">交通物流</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>用例（留空则服务端自拟）</label>
          <textarea
            v-model="casesText"
            class="textarea"
            placeholder="每行一条：问题 | 期望输出&#10;用一句话说明这个 Skill 能做什么 | 说明用途"
          />
          <span class="hint">每个用例会与每个「Skill × Agent × MCP × 参数」组合一次，总单元数有上限 120。</span>
        </div>
      </div>
      <template #footer>
        <button class="btn" @click="creating = false">取消</button>
        <button class="btn primary" :disabled="busy === 'create'" @click="create">
          {{ busy === 'create' ? '创建中…' : '创建' }}
        </button>
      </template>
    </SkModal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import SkTable from '@/components/SkTable.vue';
import SkModal from '@/components/SkModal.vue';
import { listSkills } from '@/api';
import { listAgents, listMcps } from '@/api/eval';
import { createMatrix, listMatrices, runMatrix } from '@/api/pro';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';

const router = useRouter();
const toast = useToastStore();

const rows = ref([]);
const loading = ref(true);
const creating = ref(false);
const busy = ref('');
const agents = ref([]);
const mcps = ref([]);
const skillOptions = ref([]);
const singleSkill = ref('');
const temperatures = ref([0.3]);
const casesText = ref('');

const form = reactive({
  name: '',
  matrix_type: 'SKILL_SCAN',
  skill_ids: [],
  agent_codes: [],
  mcp_codes: [],
  scene_code: 'general',
});

const columns = [
  { key: 'code', label: '编号', width: '150px' },
  { key: 'name', label: '名称' },
  { key: 'matrix_type', label: '类型', width: '140px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'progress', label: '单元', width: '80px', align: 'right' },
  { key: 'created_at', label: '创建时间', width: '150px' },
  { key: 'ops', label: '操作', width: '150px' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listMatrices();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function openCreate(type) {
  Object.assign(form, {
    name: '',
    matrix_type: type,
    skill_ids: [],
    agent_codes: agents.value.slice(0, 1).map((a) => a.code),
    mcp_codes: mcps.value.slice(0, 1).map((m) => m.code),
    scene_code: 'general',
  });
  temperatures.value = type === 'SKILL_SCAN' ? [0, 0.7] : [0.3];
  casesText.value = '';
  creating.value = true;
}

function parseCases() {
  return casesText.value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, i) => {
      const [input, expected] = line.split('|').map((s) => (s || '').trim());
      return { code: `c${i + 1}`, input: input || line, expected: expected || '' };
    });
}

async function create() {
  busy.value = 'create';
  try {
    const skillIds = form.matrix_type === 'SKILL_COMPARE' ? form.skill_ids : [singleSkill.value];
    if (!skillIds.length || !skillIds[0]) {
      throw new Error('请至少选一个 Skill。');
    }
    const grid = { temperature: temperatures.value.length ? temperatures.value : [0.3] };
    const item = await createMatrix({
      name: form.name || undefined,
      matrix_type: form.matrix_type,
      skill_ids: skillIds,
      agent_codes: form.agent_codes,
      mcp_codes: form.mcp_codes,
      param_grid: grid,
      scene_code: form.scene_code,
      cases: parseCases(),
    });
    toast.ok(`实验 ${item.code} 已创建，共 ${item.planned_cells} 个单元`);
    creating.value = false;
    await load();
    router.push(`/eval/matrix/${item.code}`);
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = '';
  }
}

async function run(row) {
  busy.value = row.code;
  try {
    const detail = await runMatrix(row.code);
    toast.ok(`执行完成：${detail.matrix.done_cells}/${detail.matrix.planned_cells} 个单元`);
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = '';
  }
}

onMounted(async () => {
  try {
    const page = await listSkills({ size: 30 });
    skillOptions.value = page.items;
    if (page.items.length) singleSkill.value = page.items[0].skill_id;
    agents.value = await listAgents();
    mcps.value = await listMcps();
  } catch (e) {
    notifyError(e);
  }
  await load();
});
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
.exp {
  padding: var(--sk-s4);
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.pick-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 130px;
  overflow: auto;
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: 8px;
}
@media (max-width: 900px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>索引</h1>
        <p class="muted small mb-0">
          索引 = 给 Skill 的某个属性建一个"目录页"。它不影响正确性，只影响查询速度，
          并且让「查询台」的查询计划可解释（用了哪个索引 / 为什么全表扫）。
        </p>
      </div>
      <button class="btn primary" :disabled="!app.isAdmin" @click="openCreate">
        {{ app.isAdmin ? '新建索引' : '新建（需管理员）' }}
      </button>
    </div>

    <SkTable :columns="columns" :rows="rows" :loading="loading" empty-text="还没有索引">
      <template #columns_col="{ row }"><code class="mono">{{ row.columns_col }}</code></template>
      <template #status="{ row }">
        <span class="badge" :class="row.status === 'READY' ? 'ok' : row.status === 'DROPPED' ? 'plain' : 'warn'">
          {{ row.status }}
        </span>
      </template>
      <template #ops="{ row }">
        <div class="row">
          <button class="btn sm" :disabled="!app.isAdmin" @click="rebuild(row)">重建</button>
          <button class="btn sm" @click="showEntries(row)">看条目</button>
          <button class="btn sm danger" :disabled="!app.isAdmin" @click="drop(row)">删除</button>
        </div>
      </template>
    </SkTable>

    <SkModal :open="creating" title="新建索引" @close="creating = false">
      <div class="col">
        <div class="field">
          <label>索引代号</label>
          <input v-model="form.code" class="input" placeholder="idx_author" />
        </div>
        <div class="field">
          <label>名称</label>
          <input v-model="form.name" class="input" placeholder="按作者" />
        </div>
        <div class="field">
          <label>列（可多列，逗号分隔）</label>
          <input v-model="form.columns_col" class="input" placeholder="category_code,scene_code" />
          <span class="hint">多列会用 | 拼成一个键值，例如 development|general</span>
        </div>
        <div class="field">
          <label>所属逻辑库</label>
          <select v-model="form.skillset_code" class="select">
            <option value="default">default</option>
            <option value="staging">staging</option>
          </select>
        </div>
        <label class="checkbox"><input v-model="form.unique_flag" type="checkbox" /> 唯一索引</label>
      </div>
      <template #footer>
        <button class="btn" @click="creating = false">取消</button>
        <button class="btn primary" :disabled="busy" @click="create">创建并重建</button>
      </template>
    </SkModal>

    <SkModal :open="entriesOpen" :title="`索引条目 · ${current?.code || ''}`" @close="entriesOpen = false">
      <p class="small muted">
        下面是这个索引里的**真实条目**（不是元数据）。它证明索引是"一份可查的数据"，而不是一个标签。
      </p>
      <SkTable :columns="entryColumns" :rows="entries" empty-text="索引是空的，先重建一次" />
    </SkModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import SkModal from '@/components/SkModal.vue';
import { createIndex, dropIndex, indexEntries, listIndexes, rebuildIndex } from '@/api/ms';
import { notifyError } from '@/api/client';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();

const rows = ref([]);
const loading = ref(true);
const creating = ref(false);
const busy = ref(false);
const entriesOpen = ref(false);
const entries = ref([]);
const current = ref(null);

const form = reactive({ code: '', name: '', columns_col: '', skillset_code: 'default', unique_flag: false });

const columns = [
  { key: 'code', label: '代号', width: '150px' },
  { key: 'name', label: '名称', width: '130px' },
  { key: 'columns_col', label: '列' },
  { key: 'status', label: '状态', width: '100px' },
  { key: 'entry_count', label: '条目数', width: '80px', align: 'right' },
  { key: 'hit_count', label: '命中次数', width: '90px', align: 'right' },
  { key: 'ops', label: '操作', width: '190px' },
];

const entryColumns = [
  { key: 'key_value', label: '键值', width: '40%' },
  { key: 'skill_id', label: 'Skill' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listIndexes();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, { code: '', name: '', columns_col: '', skillset_code: 'default', unique_flag: false });
  creating.value = true;
}

async function create() {
  busy.value = true;
  try {
    await createIndex({ ...form });
    toast.ok('索引已创建并重建完成');
    creating.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function rebuild(row) {
  try {
    const r = await rebuildIndex(row.code);
    toast.ok(`重建完成：${r.entry_count} 条`);
    await load();
  } catch (e) {
    notifyError(e);
  }
}

async function drop(row) {
  if (!window.confirm(`确认删除索引 ${row.code}？`)) return;
  try {
    await dropIndex(row.code);
    toast.ok('已删除（状态置 DROPPED）');
    await load();
  } catch (e) {
    notifyError(e);
  }
}

async function showEntries(row) {
  current.value = row;
  entriesOpen.value = true;
  entries.value = [];
  try {
    entries.value = await indexEntries(row.code, 20);
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
</style>

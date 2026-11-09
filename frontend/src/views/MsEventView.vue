<template>
  <div>
    <div class="page-head">
      <div>
        <h1>事件（触发器）</h1>
        <p class="muted small mb-0">
          "发生某事就自动做某事"。事件与业务写<b>在同一个事务里</b>落日志，动作单独执行并带重试与死信。
        </p>
      </div>
      <button class="btn primary" :disabled="!app.isAdmin" @click="openCreate">
        {{ app.isAdmin ? '新建事件' : '新建（需管理员）' }}
      </button>
    </div>

    <SkTable :columns="columns" :rows="rows" :loading="loading" empty-text="还没有事件">
      <template #trigger_event="{ row }"><span class="badge info mono">{{ row.trigger_event }}</span></template>
      <template #action_type="{ row }">
        <span class="badge plain">{{ row.action_label }}</span>
        <span class="tiny dim mono">{{ row.action_type }}</span>
      </template>
      <template #enabled="{ row }">
        <span class="badge" :class="row.enabled ? 'ok' : 'plain'">{{ row.enabled ? '启用' : '停用' }}</span>
      </template>
      <template #ops="{ row }">
        <button class="btn sm danger" :disabled="!app.isAdmin" @click="remove(row)">删除</button>
      </template>
    </SkTable>

    <div class="section-title">
      <h2>派发日志</h2>
      <div class="row">
        <select v-model="logStatus" class="select status-select" @change="loadLogs">
          <option value="">全部状态</option>
          <option value="DONE">DONE</option>
          <option value="PENDING">PENDING</option>
          <option value="FAILED">FAILED</option>
          <option value="DEAD">DEAD（死信）</option>
        </select>
        <button class="btn sm" @click="loadLogs">刷新</button>
      </div>
    </div>
    <SkTable :columns="logColumns" :rows="logs" :loading="logLoading" empty-text="还没有派发记录">
      <template #status="{ row }">
        <span class="badge" :class="row.status === 'DONE' ? 'ok' : row.status === 'DEAD' ? 'danger' : 'warn'">
          {{ row.status }}
        </span>
      </template>
      <template #ops="{ row }">
        <button
          v-if="row.status === 'DEAD' || row.status === 'FAILED'"
          class="btn sm"
          :disabled="!app.isAdmin"
          @click="retry(row)"
        >
          重试
        </button>
        <span v-else class="dim tiny">—</span>
      </template>
    </SkTable>

    <div class="section-title">
      <h2>事务台账</h2>
      <span class="dim small">每次写操作被哪条约束放行、动了哪些索引、触发了哪些事件</span>
    </div>
    <SkTable :columns="txColumns" :rows="txLogs" :loading="txLoading" empty-text="还没有写操作记录">
      <template #verdict="{ row }">
        <span class="badge" :class="row.verdict === 'PASS' ? 'ok' : row.verdict === 'WARN' ? 'warn' : 'danger'">
          {{ row.verdict }}
        </span>
      </template>
    </SkTable>

    <SkModal :open="creating" title="新建事件" width="720px" @close="creating = false">
      <div class="col">
        <div class="two">
          <div class="field">
            <label>事件代号</label>
            <input v-model="form.code" class="input" placeholder="ev_tag_medical" />
          </div>
          <div class="field">
            <label>名称</label>
            <input v-model="form.name" class="input" placeholder="医疗类自动打标签" />
          </div>
        </div>
        <div class="two">
          <div class="field">
            <label>触发时机</label>
            <select v-model="form.trigger_event" class="select">
              <option value="INSERT">INSERT（新增）</option>
              <option value="UPDATE">UPDATE（编辑）</option>
              <option value="STATUS_CHANGE">STATUS_CHANGE（状态变化）</option>
              <option value="RISK_HIGH">RISK_HIGH（高风险写入）</option>
            </select>
          </div>
          <div class="field">
            <label>动作</label>
            <select v-model="form.action_type" class="select">
              <option value="TAG">TAG（打标签）</option>
              <option value="AUDIT">AUDIT（写审计）</option>
              <option value="STATUS">STATUS（改状态）</option>
              <option value="NOTIFY">NOTIFY（发通知）</option>
              <option value="EVALUATE">EVALUATE（转评估）</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>条件（可空）</label>
          <input v-model="form.condition_expr" class="input" placeholder="risk_score>=80" />
          <span class="hint">写成"列 比较符 值"，例如 risk_score>=80；留空表示总是触发</span>
        </div>
        <div class="field">
          <label>动作参数</label>
          <input v-model="form.action_payload" class="input" placeholder="要打的标签 / 目标状态" />
          <span class="hint">TAG 时是要加的标签；STATUS 时是目标状态（published / archived）</span>
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
import { onMounted, reactive, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import SkModal from '@/components/SkModal.vue';
import { deleteEvent, listEventLogs, listEvents, listTxLogs, retryEventLog, saveEvent } from '@/api/ms';
import { notifyError } from '@/api/client';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();

const rows = ref([]);
const logs = ref([]);
const txLogs = ref([]);
const loading = ref(true);
const logLoading = ref(true);
const txLoading = ref(true);
const creating = ref(false);
const busy = ref(false);
const logStatus = ref('');

const form = reactive({
  code: '',
  name: '',
  trigger_event: 'INSERT',
  condition_expr: '',
  action_type: 'TAG',
  action_payload: '',
});

const columns = [
  { key: 'code', label: '代号', width: '170px' },
  { key: 'name', label: '含义' },
  { key: 'trigger_event', label: '触发', width: '130px' },
  { key: 'condition_expr', label: '条件', width: '140px' },
  { key: 'action_type', label: '动作', width: '130px' },
  { key: 'enabled', label: '状态', width: '80px' },
  { key: 'fired_count', label: '触发次数', width: '90px', align: 'right' },
  { key: 'ops', label: '操作', width: '80px' },
];

const logColumns = [
  { key: 'event_code', label: '事件', width: '150px' },
  { key: 'trigger_on', label: '触发', width: '120px' },
  { key: 'skill_id', label: 'Skill' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'created_at', label: '时间', width: '150px' },
  { key: 'ops', label: '操作', width: '80px' },
];

const txColumns = [
  { key: 'tx_id', label: '事务号', width: '170px' },
  { key: 'op_type', label: '操作', width: '90px' },
  { key: 'skill_id', label: 'Skill' },
  { key: 'verdict', label: '裁决', width: '90px' },
  { key: 'index_touched', label: '动了索引', width: '140px' },
  { key: 'events_fired', label: '触发事件', width: '140px' },
  { key: 'created_at', label: '时间', width: '150px' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listEvents();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

async function loadLogs() {
  logLoading.value = true;
  try {
    const page = await listEventLogs({ page: 1, size: 20, status: logStatus.value || undefined });
    logs.value = page.items;
  } catch (e) {
    notifyError(e);
  } finally {
    logLoading.value = false;
  }
}

async function loadTx() {
  txLoading.value = true;
  try {
    const page = await listTxLogs({ page: 1, size: 15 });
    txLogs.value = page.items;
  } catch (e) {
    notifyError(e);
  } finally {
    txLoading.value = false;
  }
}

function openCreate() {
  Object.assign(form, {
    code: '',
    name: '',
    trigger_event: 'INSERT',
    condition_expr: '',
    action_type: 'TAG',
    action_payload: '',
  });
  creating.value = true;
}

async function save() {
  busy.value = true;
  try {
    await saveEvent(null, { ...form, enabled: true });
    toast.ok('事件已保存');
    creating.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function remove(row) {
  if (!window.confirm(`确认删除事件 ${row.code}？`)) return;
  try {
    await deleteEvent(row.code);
    toast.ok('已删除');
    await load();
  } catch (e) {
    notifyError(e);
  }
}

async function retry(row) {
  try {
    await retryEventLog(row.id);
    toast.ok('已打回 PENDING，下次派发会重试');
    await loadLogs();
  } catch (e) {
    notifyError(e);
  }
}

onMounted(() => {
  load();
  loadLogs();
  loadTx();
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
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.status-select {
  width: auto;
  height: 30px;
  font-size: var(--sk-fs-sm);
}
@media (max-width: 900px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

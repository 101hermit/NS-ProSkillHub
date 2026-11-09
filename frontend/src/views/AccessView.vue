<template>
  <div>
    <div class="page-head">
      <div>
        <h1>接入管理</h1>
        <p class="muted small mb-0">
          Agent 与 MCP 的注册、探活，以及"出网到底通不通"的自述。
          内网最常见的两个坑：<b>没走代理</b>（TLS 握手被掐断）、<b>缺公司根证书</b>（PKIX 校验失败）。
        </p>
      </div>
      <button class="btn primary" :disabled="busy" @click="runDoctor">{{ busy ? '体检中…' : '一键体检' }}</button>
    </div>

    <!-- 出网 -->
    <div class="card mb-4" v-if="egress">
      <div class="card-head"><h3>出网策略</h3><span class="badge" :class="egress.ca_loaded ? 'ok' : 'warn'">
        {{ egress.ca_loaded ? '已加载根证书' : '未加载根证书' }}
      </span></div>
      <div class="card-body">
        <dl class="kv">
          <dt>代理</dt><dd class="mono">{{ egress.proxy }}</dd>
          <dt>来源</dt><dd>{{ egress.proxy_source }}</dd>
          <dt>根证书</dt><dd class="mono tiny">{{ egress.ca_bundle || '（用 JDK 默认信任库）' }}</dd>
          <dt>直连白名单</dt><dd class="tiny">{{ (egress.no_proxy || []).join(' / ') }}</dd>
        </dl>
      </div>
    </div>

    <!-- 体检结果 -->
    <div class="card mb-4" v-if="doctorRows.length">
      <div class="card-head">
        <h3>连通性</h3>
        <span class="badge" :class="doctorOk === doctorRows.length ? 'ok' : 'warn'">
          {{ doctorOk }} / {{ doctorRows.length }} 可用
        </span>
      </div>
      <div class="card-body">
        <SkTable :columns="doctorColumns" :rows="doctorRows" empty-text="没有登记任何 Agent/MCP">
          <template #ok="{ row }">
            <span class="badge" :class="row.ok ? 'ok' : 'danger'">{{ row.ok ? '通' : '不通' }}</span>
          </template>
        </SkTable>
      </div>
    </div>

    <!-- Agent -->
    <div class="section-title"><h2>智能体（Agent）</h2><span class="dim small">标准版只要求一个能连通</span></div>
    <SkTable :columns="agentColumns" :rows="agents" :loading="loading" empty-text="没有登记智能体">
      <template #api_key_masked="{ row }"><code class="mono tiny">{{ row.api_key_masked || '（未设置）' }}</code></template>
      <template #status="{ row }">
        <span class="badge" :class="row.status === 'ok' ? 'ok' : row.status === 'error' ? 'danger' : 'plain'">
          {{ row.status }}
        </span>
      </template>
      <template #ops="{ row }">
        <button class="btn sm" @click="probeA(row)">体检</button>
      </template>
    </SkTable>

    <!-- MCP -->
    <div class="section-title"><h2>MCP 服务</h2><span class="dim small">外部一个 + 内置一个（断网兜底）</span></div>
    <SkTable :columns="mcpColumns" :rows="mcps" :loading="loading" empty-text="没有登记 MCP">
      <template #status="{ row }">
        <span class="badge" :class="row.status === 'ok' ? 'ok' : row.status === 'error' ? 'danger' : 'plain'">
          {{ row.status }}
        </span>
      </template>
      <template #ops="{ row }">
        <button class="btn sm" @click="probeM(row)">体检</button>
      </template>
    </SkTable>

    <div class="card mt-4">
      <div class="card-head"><h3>怎么配 Key</h3></div>
      <div class="card-body">
        <p class="small muted">
          <code>api_key</code> 支持两种写法：直接填真实 Key（会脱敏展示），或写
          <code>env:DEEPSEEK_API_KEY</code> 表示"从环境变量读"。<b>推荐后者</b> ——
          这样 Key 不会落到数据库里。
        </p>
        <p class="small muted mb-0">
          出网需要代理与公司根证书时：设置 <code>HTTPS_PROXY=http://proxy.neusoft.com:8080</code>，
          并把 <code>ca-bundle.pem</code> 放到仓库根 <code>config/certs/</code>。
          详见仓库文档 <code>docs/05 §2</code>。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import { doctor, listAgents, listMcps, probeAgent, probeMcp } from '@/api/eval';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';

const toast = useToastStore();
const agents = ref([]);
const mcps = ref([]);
const egress = ref(null);
const doctorRows = ref([]);
const loading = ref(true);
const busy = ref(false);

const doctorOk = computed(() => doctorRows.value.filter((r) => r.ok).length);

const agentColumns = [
  { key: 'name', label: '名称', width: '150px' },
  { key: 'code', label: '代号', width: '130px' },
  { key: 'model', label: '模型', width: '150px' },
  { key: 'base_url', label: '地址' },
  { key: 'api_key_masked', label: 'Key', width: '150px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'ops', label: '操作', width: '80px' },
];

const mcpColumns = [
  { key: 'name', label: '名称', width: '180px' },
  { key: 'code', label: '代号', width: '130px' },
  { key: 'tool_name', label: '工具', width: '130px' },
  { key: 'endpoint', label: '地址' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'ops', label: '操作', width: '80px' },
];

const doctorColumns = [
  { key: 'kind', label: '类型', width: '80px' },
  { key: 'name', label: '名称', width: '180px' },
  { key: 'ok', label: '连通', width: '80px' },
  { key: 'latency_ms', label: '耗时', width: '90px', align: 'right' },
  { key: 'detail', label: '说明' },
];

async function load() {
  loading.value = true;
  try {
    const [a, m] = await Promise.all([listAgents(), listMcps()]);
    agents.value = a;
    mcps.value = m;
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

async function runDoctor() {
  busy.value = true;
  try {
    const r = await doctor();
    egress.value = r.egress;
    doctorRows.value = r.rows;
    toast.ok(`体检完成：${r.ok_count} / ${r.total} 可用`);
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function probeA(row) {
  try {
    const r = await probeAgent(row.code);
    toast.push(r.ok ? 'success' : 'warn', `${row.name}：${r.detail}`);
    await load();
  } catch (e) {
    notifyError(e);
  }
}

async function probeM(row) {
  try {
    const r = await probeMcp(row.code);
    toast.push(r.ok ? 'success' : 'warn', `${row.name}：${r.detail}`);
    await load();
  } catch (e) {
    notifyError(e);
  }
}

onMounted(async () => {
  await load();
  await runDoctor();
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
.kv {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 4px var(--sk-s3);
  margin: 0;
  font-size: var(--sk-fs-sm);
}
.kv dt {
  color: var(--sk-text-3);
}
.kv dd {
  margin: 0;
  word-break: break-all;
}
</style>

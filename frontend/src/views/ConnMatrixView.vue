<template>
  <div>
    <div class="page-head">
      <div>
        <h1>连通矩阵</h1>
        <p class="muted small mb-0">
          把<b>每个智能体 × 每个 MCP 服务</b>都探一次，看哪些组合真的能用。
          格子里的数字是两边的探活耗时之和；点格子看具体归因。
        </p>
      </div>
      <div class="row">
        <button class="btn" :disabled="busy" @click="load">{{ busy ? '探活中…' : '重新探活' }}</button>
        <RouterLink class="btn" to="/access">接入管理</RouterLink>
      </div>
    </div>

    <p v-if="loading" class="dim">加载中…</p>
    <template v-else>
      <div class="grid narrow">
        <div class="card stat">
          <div class="v">{{ matrix?.ok_count ?? 0 }}/{{ matrix?.total_count ?? 0 }}</div>
          <div class="l">可用组合</div>
        </div>
        <div class="card stat"><div class="v">{{ matrix?.agents?.length || 0 }}</div><div class="l">智能体</div></div>
        <div class="card stat"><div class="v">{{ matrix?.mcps?.length || 0 }}</div><div class="l">MCP 服务</div></div>
        <div class="card stat">
          <div class="v" :style="{ color: matrix?.egress?.ca_loaded ? 'var(--sk-ok)' : 'var(--sk-warn)' }">
            {{ matrix?.egress?.ca_loaded ? '已装' : '未装' }}
          </div>
          <div class="l">公司根证书</div>
        </div>
      </div>

      <div class="card mt-4">
        <div class="card-head">
          <h3>Agent × MCP 热力图</h3>
          <span class="dim tiny">代理：{{ matrix?.egress?.proxy || '直连' }}</span>
        </div>
        <div class="card-body">
          <SkChart
            v-if="heat"
            type="heatmap"
            :rows="heat.rows"
            :cols="heat.cols"
            :values="heat.values"
            :height="Math.max(140, heat.rows.length * 46 + 30)"
            aria="Agent 与 MCP 的连通性热力图"
          />
          <p v-else class="dim small mb-0">还没有登记 Agent 或 MCP 服务。</p>
        </div>
      </div>

      <div class="section-title"><h2>逐格结论</h2></div>
      <SkTable :columns="columns" :rows="matrix?.cells || []" empty-text="没有可探活的组合">
        <template #ok="{ row }">
          <span class="badge" :class="row.ok ? 'ok' : 'danger'">{{ row.ok ? '通' : '不通' }}</span>
        </template>
      </SkTable>

      <div class="card mt-4">
        <div class="card-head"><h3>不通的时候，先看这三条</h3></div>
        <div class="card-body">
          <ol class="small muted mb-0">
            <li><b>没走代理</b>：报错是「TLS 握手被中断」。设 <code>HTTPS_PROXY=http://proxy.neusoft.com:8080</code>。</li>
            <li><b>缺根证书</b>：报错是「PKIX path building failed」。把 <code>ca-bundle.pem</code> 放进 <code>config/certs/</code>。</li>
            <li><b>服务没起</b>：报错是「连接超时 / 拒绝」。确认 MCP 的 endpoint 正确且服务已启动。</li>
          </ol>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import SkChart from '@/components/SkChart.vue';
import SkTable from '@/components/SkTable.vue';
import { connHeatmap, connMatrix } from '@/api/pro';
import { notifyError } from '@/api/client';

const matrix = ref(null);
const heat = ref(null);
const loading = ref(true);
const busy = ref(false);

const columns = [
  { key: 'agentName', label: '智能体', width: '180px' },
  { key: 'mcpName', label: 'MCP 服务', width: '200px' },
  { key: 'ok', label: '连通', width: '80px' },
  { key: 'latencyMs', label: '耗时', width: '90px', align: 'right' },
  { key: 'detail', label: '说明' },
];

async function load() {
  busy.value = true;
  if (!matrix.value) loading.value = true;
  try {
    const [m, h] = await Promise.all([connMatrix(), connHeatmap()]);
    matrix.value = m;
    heat.value = h;
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
    busy.value = false;
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
.stat {
  padding: var(--sk-s4);
  text-align: center;
}
.stat .v {
  font-size: var(--sk-fs-xl);
  font-weight: 700;
  color: var(--sk-primary-strong);
}
.stat .l {
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-3);
}
</style>

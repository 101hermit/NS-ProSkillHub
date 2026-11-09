<template>
  <div>
    <div class="page-head">
      <div>
        <h1>矩阵详情</h1>
        <p class="muted small mb-0" v-if="detail">
          {{ detail.matrix.name }} · <span class="mono">{{ detail.matrix.code }}</span> ·
          {{ detail.matrix.matrix_type }} · {{ detail.matrix.done_cells }}/{{ detail.matrix.planned_cells }} 个单元
        </p>
      </div>
      <div class="row">
        <button class="btn primary" :disabled="busy" @click="run">{{ busy ? '执行中…' : '执行全部单元' }}</button>
        <button class="btn" :disabled="busy" @click="loadCompare">看分组对比</button>
        <a class="btn ghost" :href="reportUrl" :download="reportFile">
          导出报告（.md）
        </a>
        <RouterLink class="btn ghost" to="/eval/lab">返回实测中心</RouterLink>
      </div>
    </div>

    <p v-if="loading" class="dim">加载中…</p>
    <template v-else-if="detail">
      <!-- 概览 -->
      <div class="grid narrow">
        <div class="card stat"><div class="v">{{ detail.matrix.done_cells }}</div><div class="l">已完成单元</div></div>
        <div class="card stat">
          <div class="v">{{ summary.avg_score != null ? Number(summary.avg_score).toFixed(1) : '—' }}</div>
          <div class="l">综合分均值</div>
        </div>
        <div class="card stat">
          <div class="v">{{ summary.std != null ? Number(summary.std).toFixed(2) : '—' }}</div>
          <div class="l">标准差</div>
        </div>
        <div class="card stat">
          <div class="v">{{ detail.matrix.scene_code === 'general' ? '通用' : detail.matrix.scene_code }}</div>
          <div class="l">场景</div>
        </div>
      </div>

      <!-- 直方图 -->
      <div class="card mt-4" v-if="histogram.length">
        <div class="card-head"><h3>分数分布</h3><span class="dim tiny">看稳定性：集中还是散开</span></div>
        <div class="card-body">
          <SkChart type="bar" :items="histogram" :height="160" aria="综合分分布" />
        </div>
      </div>

      <!-- 分组对比 -->
      <template v-if="compare">
        <div class="section-title">
          <h2>分组对比</h2>
          <span class="dim small">{{ compare.conclusion }}</span>
        </div>
        <div class="compare-grid">
          <div class="card">
            <div class="card-head"><h3>按 Skill</h3></div>
            <div class="card-body">
              <SkTable :columns="compareColumns" :rows="compare.by_skill" empty-text="没有数据" />
            </div>
          </div>
          <div class="card">
            <div class="card-head"><h3>按 Agent</h3></div>
            <div class="card-body">
              <SkTable :columns="agentColumns" :rows="compare.by_agent" empty-text="没有数据" />
            </div>
          </div>
        </div>
        <div class="card mt-4">
          <div class="card-head"><h3>按参数</h3></div>
          <div class="card-body">
            <SkTable :columns="paramColumns" :rows="compare.by_params" empty-text="没有数据" />
          </div>
        </div>
      </template>

      <!-- 单元 -->
      <div class="section-title">
        <h2>全部单元</h2>
        <span class="dim small">点一行看输出与推理过程</span>
      </div>
      <SkTable :columns="cellColumns" :rows="detail.cells" :loading="false" empty-text="还没有单元"
               @row-click="openCell">
        <template #status="{ row }">
          <span class="badge" :class="row.status === 'DONE' ? 'ok' : row.status === 'FAILED' ? 'danger' : 'warn'">
            {{ row.status }}
          </span>
        </template>
        <template #score_total="{ row }">{{ row.score_total.toFixed(1) }}</template>
      </SkTable>

      <SkModal :open="!!activeCell" title="单元详情" width="820px" @close="activeCell = null">
        <template v-if="activeCell">
          <div class="col">
            <div class="row wrap">
              <span class="badge plain">{{ activeCell.agentCode }}</span>
              <span class="badge plain">{{ activeCell.mcpCode || '未用 MCP' }}</span>
              <span class="badge plain">{{ activeCell.caseCode }}</span>
              <span class="badge" :class="activeCell.status === 'DONE' ? 'ok' : 'danger'">
                {{ activeCell.scoreTotal.toFixed(1) }} 分
              </span>
              <span class="badge plain">{{ activeCell.latencyMs }} ms</span>
            </div>
            <p v-if="activeCell.error" class="warn-box small mb-0">{{ activeCell.error }}</p>
            <div><b class="small">输入</b><pre class="box">{{ activeCell.inputText }}</pre></div>
            <div><b class="small">期望输出</b><pre class="box">{{ activeCell.expectedText || '（未填）' }}</pre></div>
            <div><b class="small">推理过程</b><pre class="box">{{ activeCell.reasoningText || '（无）' }}</pre></div>
            <div><b class="small">输出</b><pre class="box">{{ activeCell.outputText || '（空）' }}</pre></div>
          </div>
        </template>
      </SkModal>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import SkTable from '@/components/SkTable.vue';
import SkModal from '@/components/SkModal.vue';
import SkChart from '@/components/SkChart.vue';
import { compareMatrix, getMatrix, matrixReportUrl, runMatrix } from '@/api/pro';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';

const route = useRoute();

/**
 * 报告下载地址与文件名。
 *
 * 用 computed 而不是在点击时拼：矩阵编号来自路由，切换实验时地址要跟着变，
 * 否则"换个实验点导出"会下载到上一个实验的报告 —— 这种错很难被发现。
 */
const matrixCode = computed(() => String(route.params.code || ''));
const reportUrl = computed(() => matrixReportUrl(matrixCode.value));
const reportFile = computed(() => `matrix-${matrixCode.value || 'report'}.md`);
const toast = useToastStore();

const detail = ref(null);
const compare = ref(null);
const loading = ref(true);
const busy = ref(false);
const activeCell = ref(null);

const summary = computed(() => detail.value?.summary || {});

const histogram = computed(() => {
  const h = summary.value.histogram || {};
  return Object.entries(h).map(([k, v]) => ({ key: k, label: k, count: v }));
});

const compareColumns = [
  { key: 'rank', label: '#', width: '44px' },
  { key: 'name', label: 'Skill' },
  { key: 'n', label: '单元', width: '64px', align: 'right' },
  { key: 'avg_score', label: '均分', width: '72px', align: 'right' },
  { key: 'topsis', label: 'TOPSIS', width: '80px', align: 'right' },
];
const agentColumns = [
  { key: 'rank', label: '#', width: '44px' },
  { key: 'key', label: 'Agent' },
  { key: 'n', label: '单元', width: '64px', align: 'right' },
  { key: 'avg_score', label: '均分', width: '72px', align: 'right' },
  { key: 'topsis', label: 'TOPSIS', width: '80px', align: 'right' },
];
const paramColumns = [
  { key: 'rank', label: '#', width: '44px' },
  { key: 'params', label: '参数组合' },
  { key: 'n', label: '单元', width: '64px', align: 'right' },
  { key: 'avg_score', label: '均分', width: '72px', align: 'right' },
  { key: 'latency_efficiency', label: '时延效率', width: '90px', align: 'right' },
];
const cellColumns = [
  { key: 'skillName', label: 'Skill', width: '160px' },
  { key: 'agentCode', label: 'Agent', width: '120px' },
  { key: 'caseCode', label: '用例', width: '80px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'score_total', label: '分数', width: '70px', align: 'right' },
  { key: 'latencyMs', label: '时延', width: '80px', align: 'right' },
];

function openCell(row) {
  activeCell.value = row;
}

async function load() {
  loading.value = true;
  compare.value = null;
  try {
    detail.value = await getMatrix(route.params.code);
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

async function run() {
  busy.value = true;
  try {
    detail.value = await runMatrix(route.params.code);
    toast.ok(`执行完成：${detail.value.matrix.done_cells}/${detail.value.matrix.planned_cells}`);
    if (detail.value.matrix.done_cells > 0) await loadCompare();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function loadCompare() {
  try {
    compare.value = await compareMatrix(route.params.code);
  } catch (e) {
    notifyError(e);
  }
}

watch(() => route.params.code, load);
onMounted(async () => {
  await load();
  if (detail.value?.matrix.done_cells > 0) await loadCompare();
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
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s4);
  align-items: start;
}
.box {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--sk-bg);
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: var(--sk-s3);
  font-size: var(--sk-fs-sm);
  margin: 6px 0 0;
  max-height: 200px;
  overflow: auto;
}
.warn-box {
  background: #fdf3e3;
  border-left: 3px solid var(--sk-warn);
  padding: 8px 10px;
  border-radius: 0 var(--sk-r-sm) var(--sk-r-sm) 0;
}
@media (max-width: 1000px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }
}
</style>

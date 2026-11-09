<template>
  <div>
    <div class="page-head">
      <div>
        <h1>知识级分析</h1>
        <p class="muted small mb-0">
          基于实测矩阵数据做统计与最优化分析。六种方法，每种都会给一段<b>中文结论</b>，不只是一堆数字。
        </p>
      </div>
      <RouterLink class="btn" to="/eval/lab">去跑实验 →</RouterLink>
    </div>

    <div class="two">
      <!-- 左：选方法 -->
      <div class="card">
        <div class="card-head"><h3>选一个方法</h3></div>
        <div class="card-body col">
          <div class="method-list">
            <button
              v-for="m in methods"
              :key="m.key"
              class="method"
              :class="{ active: method === m.key }"
              @click="method = m.key"
            >
              <b>{{ m.label }}</b>
              <span class="tiny dim">{{ m.desc }}</span>
            </button>
          </div>
          <div class="field">
            <label>分组维度</label>
            <select v-model="groupBy" class="select">
              <option value="agent_code">按 Agent（看模型差异）</option>
              <option value="skill_id">按 Skill（看谁更强）</option>
              <option value="params">按参数（看超参影响）</option>
            </select>
          </div>
          <button class="btn primary block" :disabled="busy" @click="run">
            {{ busy ? '分析中…' : '开始分析' }}
          </button>
          <p class="tiny dim mb-0">
            数据来源：所有实验矩阵里已完成的单元。先去「实测中心」跑一个矩阵，这里才有数据。
          </p>
        </div>
      </div>

      <!-- 右：结果 -->
      <div class="card">
        <div class="card-head">
          <h3>{{ report?.title || '分析结果' }}</h3>
          <span v-if="report" class="badge info mono">{{ report.method }}</span>
        </div>
        <div class="card-body">
          <p v-if="!report" class="dim small mb-0">选一个方法然后点「开始分析」。</p>
          <template v-else>
            <div class="conclusion">{{ report.conclusion }}</div>
            <div class="section-title"><h2>明细</h2></div>
            <pre class="result">{{ pretty }}</pre>
          </template>
        </div>
      </div>
    </div>

    <!-- 历史报告 -->
    <div class="section-title">
      <h2>历史报告</h2>
      <button class="btn sm" @click="loadReports">刷新</button>
    </div>
    <SkTable :columns="reportColumns" :rows="reports" empty-text="还没有分析报告"
             @row-click="openReport">
      <template #method="{ row }"><span class="badge plain mono">{{ row.method }}</span></template>
    </SkTable>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import { analyze, listReports } from '@/api/pro';
import { notifyError } from '@/api/client';

const methods = [
  { key: 'DESCRIBE', label: '描述统计', desc: '均值 / 标准差 / 中位数 / 直方图' },
  { key: 'HYPOTHESIS', label: '假设检验', desc: '两组均值的 Welch t 检验 + 效应量' },
  { key: 'TOPSIS', label: 'TOPSIS 排序', desc: '又高又稳 vs 偶尔很高' },
  { key: 'CLUSTER', label: '聚类', desc: 'k-means 把表现分层' },
  { key: 'REGRESSION', label: '回归', desc: '时延能否解释分数差异' },
  { key: 'AHP', label: 'AHP 权重', desc: '层次分析法给出的指标权重与一致性' },
];

const method = ref('DESCRIBE');
const groupBy = ref('agent_code');
const report = ref(null);
const reports = ref([]);
const busy = ref(false);

const pretty = computed(() => {
  if (!report.value?.result_json) return '';
  try {
    return JSON.stringify(JSON.parse(report.value.result_json), null, 2);
  } catch {
    return report.value.result_json;
  }
});

const reportColumns = [
  { key: 'code', label: '编号', width: '150px' },
  { key: 'title', label: '标题' },
  { key: 'method', label: '方法', width: '140px' },
  { key: 'created_at', label: '时间', width: '150px' },
];

async function run() {
  busy.value = true;
  report.value = null;
  try {
    report.value = await analyze({ method: method.value, groupBy: groupBy.value });
    await loadReports();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function loadReports() {
  try {
    reports.value = await listReports(20);
  } catch {
    /* 忽略 */
  }
}

function openReport(row) {
  report.value = row;
}

onMounted(loadReports);
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
  grid-template-columns: minmax(0, 320px) minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
.method-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.method {
  text-align: left;
  border: 1px solid var(--sk-border);
  background: var(--sk-surface);
  border-radius: var(--sk-r-sm);
  padding: 8px 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-family: inherit;
  font-size: var(--sk-fs-sm);
}
.method.active {
  border-color: var(--sk-primary);
  background: var(--sk-primary-weak);
}
.conclusion {
  background: var(--sk-bg);
  border-left: 3px solid var(--sk-accent);
  border-radius: 0 var(--sk-r-sm) var(--sk-r-sm) 0;
  padding: var(--sk-s3) var(--sk-s4);
  font-size: var(--sk-fs-sm);
  line-height: 1.7;
}
.result {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: var(--sk-r-sm);
  padding: var(--sk-s3);
  font-size: var(--sk-fs-xs);
  max-height: 360px;
  overflow: auto;
  margin: 0;
}
@media (max-width: 1000px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

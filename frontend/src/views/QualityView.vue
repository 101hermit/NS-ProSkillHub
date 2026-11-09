<template>
  <div>
    <div class="page-head">
      <div>
        <h1>质量评估</h1>
        <p class="muted small mb-0">
          只用<b>统计学与最优化</b>：加权评分、z-score、TOPSIS 排名。指标分两维 ——
          <b>任务效能</b>（答得对不对）与<b>通信性能</b>（快不快）。
        </p>
      </div>
      <RouterLink class="btn" to="/eval/live">去做实测 →</RouterLink>
    </div>

    <div class="card mb-4">
      <div class="card-head"><h3>选一个 Skill 看汇总</h3></div>
      <div class="card-body row wrap">
        <input v-model="skillKw" class="input" style="max-width: 320px" placeholder="搜索 Skill" @keyup.enter="search" />
        <button class="btn" @click="search">搜索</button>
        <select v-model="skillId" class="select" style="max-width: 380px" @change="loadSummary">
          <option value="">请选择 Skill</option>
          <option v-for="s in options" :key="s.skill_id" :value="s.skill_id">
            {{ s.name }}（已实测 {{ s.eval_count }} 次 · 均分 {{ s.avg_score }}）
          </option>
        </select>
      </div>
    </div>

    <!-- 汇总 -->
    <template v-if="summary">
      <div class="grid narrow">
        <div class="card stat"><div class="v">{{ summary.run_count }}</div><div class="l">实测次数</div></div>
        <div class="card stat"><div class="v">{{ summary.avg_score.toFixed(1) }}</div><div class="l">平均综合分</div></div>
        <div class="card stat"><div class="v">{{ summary.best_score.toFixed(1) }}</div><div class="l">最好一次</div></div>
        <div class="card stat"><div class="v">{{ summary.worst_score.toFixed(1) }}</div><div class="l">最差一次</div></div>
      </div>

      <div class="card mt-4">
        <div class="card-head"><h3>结论</h3></div>
        <div class="card-body">
          <p class="small mb-0">{{ summary.conclusion }}</p>
        </div>
      </div>

      <div class="two mt-4">
        <div class="card">
          <div class="card-head"><h3>指标均值</h3></div>
          <div class="card-body">
            <div v-for="m in summary.metric_averages" :key="m.code" class="metric-row">
              <span class="small">{{ m.label }}</span>
              <span class="badge plain tiny">{{ m.dimension === 'EFFICACY' ? '效能' : '通信' }}</span>
              <span class="bar"><i :style="{ width: Math.min(100, m.avg * 100) + '%' }" /></span>
              <span class="tiny dim nowrap">{{ (m.avg * 100).toFixed(0) }}%</span>
            </div>
            <p v-if="!summary.metric_averages.length" class="dim small mb-0">还没有指标数据。</p>
          </div>
        </div>
        <div class="card">
          <div class="card-head"><h3>Skill 排名（TOPSIS）</h3></div>
          <div class="card-body">
            <SkTable :columns="rankColumns" :rows="summary.ranking" empty-text="至少需要 2 个有实测记录的 Skill 才能排名" />
          </div>
        </div>
      </div>
    </template>

    <!-- 指标字典 -->
    <div class="section-title"><h2>指标字典</h2><span class="dim small">口径唯一来源：EvalService.SPECS</span></div>
    <SkTable :columns="metricColumns" :rows="metrics" :loading="loading">
      <template #dimension="{ row }">
        <span class="badge" :class="row.dimension === 'EFFICACY' ? 'info' : 'plain'">{{ row.dimension_label }}</span>
      </template>
      <template #higher_better="{ row }">{{ row.higher_better ? '越大越好' : '越小越好' }}</template>
    </SkTable>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import SkTable from '@/components/SkTable.vue';
import { listSkills } from '@/api';
import { getMetrics, getSummary } from '@/api/eval';
import { notifyError } from '@/api/client';

const route = useRoute();
const skillKw = ref('');
const options = ref([]);
const skillId = ref(route.query.skill_id || '');
const summary = ref(null);
const metrics = ref([]);
const loading = ref(true);

const rankColumns = [
  { key: 'rank', label: '#', width: '48px' },
  { key: 'name', label: 'Skill' },
  { key: 'avg_score', label: '均分', width: '80px', align: 'right' },
  { key: 'eval_count', label: '实测', width: '70px', align: 'right' },
  { key: 'topsis', label: 'TOPSIS', width: '90px', align: 'right' },
];

const metricColumns = [
  { key: 'label', label: '指标', width: '120px' },
  { key: 'code', label: '代号', width: '130px' },
  { key: 'dimension', label: '维度', width: '110px' },
  { key: 'weight', label: '权重', width: '70px', align: 'right' },
  { key: 'formula', label: '算法' },
];

async function search() {
  try {
    const page = await listSkills({ kw: skillKw.value || undefined, size: 20 });
    options.value = page.items;
    if (!skillId.value && page.items.length) {
      skillId.value = page.items[0].skill_id;
      await loadSummary();
    }
  } catch (e) {
    notifyError(e);
  }
}

async function loadSummary() {
  if (!skillId.value) return;
  try {
    summary.value = await getSummary(skillId.value);
  } catch (e) {
    summary.value = null;
    notifyError(e);
  }
}

onMounted(async () => {
  try {
    metrics.value = await getMetrics();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
  await search();
  if (skillId.value) await loadSummary();
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
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s4);
  align-items: start;
}
.metric-row {
  display: grid;
  grid-template-columns: 96px 48px 1fr 56px;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
.bar {
  height: 6px;
  background: var(--sk-muted);
  border-radius: 999px;
  overflow: hidden;
}
.bar i {
  display: block;
  height: 100%;
  background: var(--sk-primary);
}
@media (max-width: 1000px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

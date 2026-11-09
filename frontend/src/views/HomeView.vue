<template>
  <div>
    <!-- 概览条 -->
    <section class="hero card">
      <div class="hero-main">
        <h1>{{ app.siteName }}</h1>
        <p class="muted">{{ app.tagline }}</p>
        <div class="quick">
          <input
            v-model="kw"
            class="input"
            placeholder="试试搜索：pdf / 教学 / 合同 / 数据"
            @keyup.enter="doSearch"
          />
          <button class="btn accent" @click="doSearch">搜索 Skill</button>
        </div>
        <p class="tiny dim mt-2">
          Skill = 一份写给 AI 的"工作手册"：一个文件夹 + 一份 SKILL.md 说明书。
          <RouterLink to="/docs/glossary">看不懂的词点这里</RouterLink>
        </p>
      </div>
      <div class="hero-stats">
        <div v-for="s in statCards" :key="s.label" class="stat">
          <div class="v">{{ s.value }}</div>
          <div class="l">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 分类格子 -->
    <div class="section-title">
      <h2>按分类逛</h2>
      <span class="dim small">15 个分类：8 个工程类 + 6 个行业类 + 1 个兜底</span>
    </div>
    <div class="grid narrow">
      <RouterLink
        v-for="c in categories"
        :key="c.code"
        :to="{ path: '/skills', query: { category: c.code } }"
        class="cat card"
        :class="{ empty: c.count === 0 }"
      >
        <div class="cat-label">{{ c.label }}</div>
        <div class="cat-count">{{ c.count }}<span class="tiny"> 个</span></div>
        <div class="tiny dim">{{ c.kind === 'domain' ? '行业场景' : c.kind === 'engineering' ? '工程能力' : '通用' }}</div>
      </RouterLink>
    </div>

    <!-- 热门 -->
    <div class="section-title">
      <h2>下载最多</h2>
      <RouterLink class="small" to="/skills?sort=download_count">查看全部 →</RouterLink>
    </div>
    <div class="grid" v-if="popular.length">
      <SkSkillCard v-for="s in popular" :key="s.skill_id" :card="s" />
    </div>
    <p v-else class="dim">加载中…</p>

    <!-- 最新 -->
    <div class="section-title">
      <h2>最近更新</h2>
      <RouterLink class="small" to="/skills?sort=updated_at">查看全部 →</RouterLink>
    </div>
    <div class="grid" v-if="latest.length">
      <SkSkillCard v-for="s in latest" :key="s.skill_id" :card="s" />
    </div>
    <p v-else class="dim">加载中…</p>

    <!-- 两栏：风险分布 + 场景分布 -->
    <div class="two-col mt-6">
      <div class="card">
        <div class="card-head"><h3>风险分布</h3><RouterLink class="small" to="/risk">口径说明</RouterLink></div>
        <div class="card-body">
          <SkChart type="bar" :items="riskItems" :height="170" aria="风险等级分布" />
        </div>
      </div>
      <div class="card">
        <div class="card-head"><h3>行业场景分布</h3><span class="dim small">{{ sceneTotal }} 个行业 Skill</span></div>
        <div class="card-body">
          <SkChart v-if="sceneItems.length" type="bar" :items="sceneItems" :height="170" aria="场景分布" />
          <p v-else class="dim small mb-0">当前库内没有标注行业场景的 Skill。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import SkSkillCard from '@/components/SkSkillCard.vue';
import SkChart from '@/components/SkChart.vue';
import { useAppStore } from '@/store/app';
import { getCategories, getStats, listSkills } from '@/api';
import { RISK_COLOR, num } from '@/utils/format';

const app = useAppStore();
const router = useRouter();
const kw = ref('');
const stats = ref(null);
const categories = ref([]);
const popular = ref([]);
const latest = ref([]);

const statCards = computed(() => [
  { label: 'Skill 总数', value: num(stats.value?.skill_count ?? app.health?.skill_count ?? 0) },
  { label: '版本快照', value: num(stats.value?.version_count ?? 0) },
  { label: '贡献者', value: num(stats.value?.contributor_count ?? 0) },
  { label: '累计下载', value: num(stats.value?.download_total ?? 0) },
  { label: '风险信号', value: num(stats.value?.risk_signal_count ?? 0) },
]);

const riskItems = computed(() =>
  (stats.value?.risk_distribution || []).map((d) => ({
    key: d.key,
    label: d.label,
    count: d.count,
    color: RISK_COLOR[d.key] || 'var(--sk-primary)',
  })),
);

const sceneItems = computed(() =>
  (stats.value?.scene_distribution || [])
    .filter((d) => d.key !== 'general' && d.count > 0)
    .map((d) => ({ key: d.key, label: d.label, count: d.count })),
);

const sceneTotal = computed(() => sceneItems.value.reduce((a, d) => a + d.count, 0));

function doSearch() {
  const q = kw.value.trim();
  router.push({ path: '/skills', query: q ? { kw: q } : {} });
}

onMounted(async () => {
  const [s, c, p, n] = await Promise.allSettled([
    getStats(),
    getCategories(),
    listSkills({ size: 8, sort: 'download_count' }),
    listSkills({ size: 8, sort: 'updated_at' }),
  ]);
  if (s.status === 'fulfilled') stats.value = s.value;
  if (c.status === 'fulfilled') categories.value = c.value;
  if (p.status === 'fulfilled') popular.value = p.value.items;
  if (n.status === 'fulfilled') latest.value = n.value.items;
});
</script>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: var(--sk-s6);
  padding: var(--sk-s6);
  margin-bottom: var(--sk-s4);
  background: linear-gradient(120deg, #ffffff 0%, #f4f7ff 100%);
}
.hero-main h1 {
  font-size: var(--sk-fs-2xl);
  margin-bottom: 6px;
}
.quick {
  display: flex;
  gap: 8px;
  margin-top: var(--sk-s4);
  max-width: 520px;
}
.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
  gap: var(--sk-s3);
  align-content: center;
}
.stat {
  background: var(--sk-surface);
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r);
  padding: var(--sk-s3);
  text-align: center;
}
.stat .v {
  font-size: var(--sk-fs-lg);
  font-weight: 700;
  color: var(--sk-primary-strong);
}
.stat .l {
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-3);
}
.cat {
  padding: var(--sk-s3) var(--sk-s4);
  text-decoration: none;
  color: inherit;
  transition: border-color var(--sk-dur) var(--sk-ease), box-shadow var(--sk-dur) var(--sk-ease);
}
.cat:hover {
  border-color: var(--sk-primary);
  box-shadow: var(--sk-shadow-2);
  text-decoration: none;
}
.cat.empty {
  opacity: 0.55;
}
.cat-label {
  font-weight: 650;
}
.cat-count {
  font-size: var(--sk-fs-lg);
  color: var(--sk-primary-strong);
  font-weight: 700;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s4);
}
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>

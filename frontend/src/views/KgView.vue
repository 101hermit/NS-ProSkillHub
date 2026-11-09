<template>
  <div>
    <div class="page-head">
      <div>
        <h1>知识图谱</h1>
        <p class="muted small mb-0">
          把 Skill、分类、场景、标签、Agent 画成一张"关系网"。<b>实体只来自三处</b>：Skill 库、分类本体、接入注册表，
          不允许外部注入知识。图谱落在关系表里，不用图数据库。
        </p>
      </div>
      <div class="row">
        <select v-model="scene" class="select scene-select" @change="load">
          <option value="global">全局</option>
          <option value="edu">教育</option>
          <option value="fin">金融</option>
          <option value="gov">政务与法务</option>
          <option value="med">医疗健康</option>
          <option value="news">新闻传媒</option>
          <option value="tra">交通物流</option>
        </select>
        <button class="btn primary" :disabled="busy" @click="rebuild">{{ busy ? '重建中…' : '重建图谱' }}</button>
        <a class="btn" :href="exportUrl('json')" target="_blank" rel="noopener">导出 JSON</a>
        <a class="btn" :href="exportUrl('csv')" target="_blank" rel="noopener">CSV</a>
        <a class="btn" :href="exportUrl('graphml')" target="_blank" rel="noopener">GraphML</a>
      </div>
    </div>

    <p v-if="loading" class="dim">加载中…</p>
    <template v-else>
      <div class="grid narrow">
        <div class="card stat"><div class="v">{{ stats.entities_total ?? 0 }}</div><div class="l">实体总数</div></div>
        <div class="card stat"><div class="v">{{ stats.relations_total ?? 0 }}</div><div class="l">关系总数</div></div>
        <div class="card stat"><div class="v">{{ stats.nodes ?? 0 }}</div><div class="l">图上节点</div></div>
        <div class="card stat"><div class="v">{{ stats.edges ?? 0 }}</div><div class="l">图上连线</div></div>
      </div>

      <div class="card mt-4">
        <div class="card-head">
          <h3>关系网</h3>
          <span class="dim tiny">节点越大 = 连接越多；颜色按类型区分</span>
        </div>
        <div class="card-body">
          <p v-if="!graph.nodes.length" class="dim small mb-0">
            图谱是空的。点右上角「重建图谱」，会从 Skill 库 + 分类本体 + 接入注册表生成。
          </p>
          <svg v-else class="graph" :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="知识图谱">
            <line
              v-for="(e, i) in layoutEdges"
              :key="'e' + i"
              :x1="e.x1"
              :y1="e.y1"
              :x2="e.x2"
              :y2="e.y2"
              :stroke="edgeColor(e.type)"
              :stroke-width="Math.min(2.4, 0.4 + e.weight * 0.25)"
              stroke-opacity="0.45"
            />
            <g v-for="n in layoutNodes" :key="n.code" @click="pick(n)">
              <circle :cx="n.x" :cy="n.y" :r="n.r" :fill="nodeColor(n.type)" fill-opacity="0.82" stroke="#fff" />
              <text :x="n.x" :y="n.y + 3" font-size="9" text-anchor="middle" fill="#fff">
                {{ n.short }}
              </text>
              <title>{{ n.type }} · {{ n.name }}{{ n.label ? ' · ' + n.label : '' }}（连接 {{ n.degree }} 条）</title>
            </g>
          </svg>
          <div class="legend">
            <span v-for="l in legends" :key="l.type" class="legend-item">
              <i :style="{ background: l.color }" />{{ l.label }}
            </span>
          </div>
        </div>
      </div>

      <div class="two mt-4">
        <div class="card">
          <div class="card-head"><h3>选中节点</h3></div>
          <div class="card-body">
            <p v-if="!selected" class="dim small mb-0">点图上的任意节点看详情。</p>
            <template v-else>
              <div class="row wrap">
                <span class="badge" :style="{ background: nodeColor(selected.type), color: '#fff' }">
                  {{ selected.type }}
                </span>
                <b>{{ selected.name }}</b>
              </div>
              <p class="small muted mt-2 mb-0">{{ selected.label || '（无描述）' }}</p>
              <p class="tiny dim mb-0">连接数 {{ selected.degree }}</p>
              <RouterLink
                v-if="selected.type === 'SKILL'"
                class="small"
                :to="`/skills/${encodeURIComponent(selected.code.replace('skill:', ''))}`"
              >
                打开这个 Skill →
              </RouterLink>
            </template>
          </div>
        </div>
        <div class="card">
          <div class="card-head"><h3>相似 Skill</h3><span class="dim tiny">按共享标签计算</span></div>
          <div class="card-body">
            <div class="row">
              <input v-model="similarKw" class="input" placeholder="输入 Skill 名搜索" @keyup.enter="searchSimilar" />
              <button class="btn sm" @click="searchSimilar">搜索</button>
            </div>
            <SkTable :columns="similarColumns" :rows="similar" empty-text="先搜一个 Skill" />
          </div>
        </div>
      </div>

      <div class="card mt-4" v-if="snapshots.length">
        <div class="card-head"><h3>图谱快照</h3><span class="dim tiny">每次重建留一个</span></div>
        <div class="card-body">
          <SkTable :columns="snapshotColumns" :rows="snapshots" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import { listSkills } from '@/api';
import { kgExportUrl, kgGraph, kgSimilar, kgSnapshots, rebuildKg } from '@/api/pro';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';

const toast = useToastStore();

const W = 800;
const H = 460;
const graph = ref({ nodes: [], edges: [], stats: {} });
const scene = ref('global');
const snapshots = ref([]);
const loading = ref(true);
const busy = ref(false);
const selected = ref(null);
const similar = ref([]);
const similarKw = ref('');

const stats = computed(() => graph.value.stats || {});
const exportUrl = (fmt) => kgExportUrl(fmt, scene.value);

const COLORS = {
  SKILL: '#002fa7',
  CAPABILITY: '#7b3fe4',
  SCENE: '#0f9d58',
  TAG: '#8a909c',
  AGENT: '#d97706',
  MCP: '#2563eb',
};
const legends = [
  { type: 'SKILL', label: 'Skill' },
  { type: 'CAPABILITY', label: '能力/分类' },
  { type: 'SCENE', label: '场景' },
  { type: 'TAG', label: '标签' },
  { type: 'AGENT', label: 'Agent' },
  { type: 'MCP', label: 'MCP' },
];

function nodeColor(t) {
  return COLORS[t] || '#545a67';
}
function edgeColor(t) {
  return { SIMILAR_TO: '#7b3fe4', BELONGS_TO: '#002fa7', IN_SCENE: '#0f9d58', HAS_TAG: '#8a909c',
    EVALUATED_BY: '#d97706' }[t] || '#cfd4de';
}

/** 极简力导向布局：环形初始化 + 弹簧松弛（无需任何图库）。 */
const layout = computed(() => {
  const nodes = graph.value.nodes.map((n, i) => {
    const angle = (i / Math.max(1, graph.value.nodes.length)) * Math.PI * 2;
    return {
      ...n,
      x: W / 2 + Math.cos(angle) * (W * 0.32),
      y: H / 2 + Math.sin(angle) * (H * 0.32),
      r: Math.min(18, 6 + n.degree * 0.7),
      short: (n.name || n.code).slice(0, 6),
    };
  });
  const byCode = new Map(nodes.map((n) => [n.code, n]));
  const edges = graph.value.edges
    .map((e) => ({ ...e, a: byCode.get(e.source), b: byCode.get(e.target) }))
    .filter((e) => e.a && e.b);

  for (let iter = 0; iter < 60; iter++) {
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        let dx = b.x - a.x;
        let dy = b.y - a.y;
        let d2 = dx * dx + dy * dy || 0.01;
        const force = 900 / d2;
        const d = Math.sqrt(d2);
        dx /= d;
        dy /= d;
        a.x -= dx * force;
        a.y -= dy * force;
        b.x += dx * force;
        b.y += dy * force;
      }
    }
    for (const e of edges) {
      let dx = e.b.x - e.a.x;
      let dy = e.b.y - e.a.y;
      const d = Math.sqrt(dx * dx + dy * dy) || 0.01;
      const target = 90;
      const force = (d - target) * 0.012;
      dx /= d;
      dy /= d;
      e.a.x += dx * force;
      e.a.y += dy * force;
      e.b.x -= dx * force;
      e.b.y -= dy * force;
    }
    for (const n of nodes) {
      n.x = Math.max(20, Math.min(W - 20, n.x));
      n.y = Math.max(20, Math.min(H - 20, n.y));
    }
  }
  return { nodes, edges };
});

const layoutNodes = computed(() => layout.value.nodes);
const layoutEdges = computed(() =>
  layout.value.edges.map((e) => ({ x1: e.a.x, y1: e.a.y, x2: e.b.x, y2: e.b.y, weight: e.weight, type: e.type })),
);

const similarColumns = [
  { key: 'name', label: 'Skill' },
  { key: 'score', label: '相似度', width: '90px', align: 'right' },
  { key: 'why', label: '依据', width: '40%' },
];
const snapshotColumns = [
  { key: 'code', label: '编号', width: '150px' },
  { key: 'name', label: '名称' },
  { key: 'entity_count', label: '实体', width: '80px', align: 'right' },
  { key: 'relation_count', label: '关系', width: '80px', align: 'right' },
  { key: 'created_at', label: '时间', width: '150px' },
];

function pick(n) {
  selected.value = n;
}

async function load() {
  loading.value = true;
  try {
    const [g, s] = await Promise.all([
      kgGraph({ scene: scene.value, limit: 150 }),
      kgSnapshots().catch(() => []),
    ]);
    graph.value = g;
    snapshots.value = s;
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

async function rebuild() {
  busy.value = true;
  try {
    const r = await rebuildKg();
    toast.ok(`图谱已重建：${r.entity_count} 个实体 / ${r.relation_count} 条关系`);
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function searchSimilar() {
  try {
    const page = await listSkills({ kw: similarKw.value || undefined, size: 1 });
    if (!page.items.length) {
      similar.value = [];
      return;
    }
    similar.value = await kgSimilar(page.items[0].skill_id, 6);
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
.scene-select {
  width: auto;
  height: 34px;
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
.graph {
  width: 100%;
  height: auto;
  background: #fbfcff;
  border-radius: var(--sk-r-sm);
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-2);
}
.legend-item i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.two {
  display: grid;
  grid-template-columns: minmax(0, 320px) minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
@media (max-width: 1000px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

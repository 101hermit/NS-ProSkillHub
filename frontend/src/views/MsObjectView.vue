<template>
  <div>
    <div class="page-head">
      <div>
        <h1>对象浏览（树 + DDL）</h1>
        <p class="muted small mb-0">
          左边是树、右边是详情 —— 数据库工具最经典的一种看法。
          点开任意一个对象，能看到它的<b>DDL</b>（这段声明式文本说了它在管什么）、
          <b>等价 SQL</b>（能在本站查询台真跑一遍）、以及<b>改变它要调哪个接口</b>。
          三样都给你，是为了让"这个对象到底在做什么"可以被验证，而不是只能相信。
        </p>
      </div>
      <div class="row">
        <button class="btn" :disabled="loading" @click="load">刷新</button>
        <RouterLink class="btn ghost" to="/ms/query">查询台</RouterLink>
      </div>
    </div>

    <p v-if="loading" class="dim">加载对象树…</p>
    <p v-else-if="error" class="error-box">{{ error }}</p>

    <div v-else class="split">
      <!-- ---------------------------------------------------------- 左：树 -->
      <aside class="tree card">
        <div class="card-body">
          <div v-for="set in tree.skillsets" :key="set.code" class="set-block">
            <button
              class="node set"
              :class="{ active: isActive('skillset', set.code) }"
              @click="select('skillset', set.code)"
            >
              <SkIcon name="database" :size="14" />
              <span class="mono">{{ set.code }}</span>
              <span class="badge plain">{{ set.skillCount }}</span>
            </button>

            <template v-for="group in GROUPS" :key="group.type">
              <div class="group-label">
                {{ group.label }}
                <span class="dim">{{ (set[group.field] || []).length }}</span>
              </div>
              <button
                v-for="obj in set[group.field]"
                :key="group.type + obj.code"
                class="node child"
                :class="{ active: isActive(group.type, obj.code) }"
                :title="obj.subtitle"
                @click="select(group.type, obj.code)"
              >
                <SkIcon :name="group.icon" :size="13" />
                <span class="mono">{{ obj.code }}</span>
              </button>
              <div v-if="!(set[group.field] || []).length" class="none tiny dim">（空）</div>
            </template>
          </div>
        </div>
      </aside>

      <!-- -------------------------------------------------------- 右：详情 -->
      <section class="detail">
        <div v-if="!ddl" class="card empty">
          <div class="card-body">
            <b>还没有选对象</b>
            <p class="small dim mb-0 mt-2">
              左边的树里点任意一项。想看"整个库都长什么样"，先点库名（如 <code class="mono">default</code>）。
            </p>
          </div>
        </div>

        <template v-else>
          <div class="card">
            <div class="card-body">
              <div class="row between">
                <div>
                  <h2 class="mb-0">{{ ddl.title }}</h2>
                  <div class="tiny dim">
                    类型 <span class="mono">{{ ddl.type }}</span> ·
                    编号 <span class="mono">{{ ddl.code }}</span>
                  </div>
                </div>
                <button class="btn ghost sm" @click="copy(ddl.ddl)">复制 DDL</button>
              </div>

              <pre class="ddl mono">{{ ddl.ddl }}</pre>

              <ul class="notes small">
                <li v-for="(n, i) in ddl.notes" :key="i">{{ n }}</li>
              </ul>
            </div>
          </div>

          <div class="grid wide mt-3">
            <div class="card">
              <div class="card-body">
                <div class="row between">
                  <b>等价 SQL</b>
                  <button class="btn ghost sm" @click="copy(ddl.equivalentSql)">复制</button>
                </div>
                <p class="tiny dim">
                  这段能在「查询台」直接跑：它查的就是这个对象管的那批数据。
                </p>
                <pre class="sql mono">{{ ddl.equivalentSql }}</pre>
                <RouterLink class="btn sm" to="/ms/query">去查询台跑</RouterLink>
              </div>
            </div>

            <div class="card">
              <div class="card-body">
                <div class="row between">
                  <b>改变它的接口</b>
                  <button class="btn ghost sm" @click="copy(ddl.api)">复制</button>
                </div>
                <p class="tiny dim">DDL 是"描述"，下面这一行才是"动作"。</p>
                <pre class="sql mono">{{ ddl.api }}</pre>
                <div v-if="ddl.rebuildApi && ddl.rebuildApi !== ddl.api" class="mt-2">
                  <div class="tiny dim">重建 / 复跑：</div>
                  <pre class="sql mono">{{ ddl.rebuildApi }}</pre>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getDdl, getObjects } from '@/api/ms';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';
import SkIcon from '@/components/SkIcon.vue';

const toast = useToastStore();

const GROUPS = [
  { type: 'index', field: 'indexes', label: '索引', icon: 'key-round' },
  { type: 'constraint', field: 'constraints', label: '约束', icon: 'shield-check' },
  { type: 'event', field: 'events', label: '事件', icon: 'zap' },
];

const tree = ref({ skillsets: [], totals: {} });
const loading = ref(true);
const error = ref('');
const ddl = ref(null);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    tree.value = await getObjects();
    // 树刷新后，原来选中的对象可能已被删：这里主动纠正，避免右侧显示"幽灵对象"
    if (ddl.value) {
      await select(ddl.value.type, ddl.value.code, true);
    }
  } catch (e) {
    error.value = e?.message || '对象树加载失败';
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function isActive(type, code) {
  return !!ddl.value && ddl.value.type === type && ddl.value.code === code;
}

async function select(type, code, quiet = false) {
  try {
    ddl.value = await getDdl(type, code);
  } catch (e) {
    ddl.value = null;
    if (!quiet) {
      notifyError(e);
    }
  }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast.ok('已复制 DDL');
  } catch {
    toast.warn('复制失败：请手工选中再复制（某些浏览器禁用了剪贴板）');
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
.split {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--sk-s4);
  align-items: start;
}
@media (max-width: 900px) {
  .split {
    grid-template-columns: 1fr;
  }
}
.tree {
  position: sticky;
  top: var(--sk-s4);
  max-height: calc(100vh - 140px);
  overflow: auto;
}
.set-block + .set-block {
  margin-top: var(--sk-s4);
  padding-top: var(--sk-s4);
  border-top: 1px dashed var(--sk-border);
}
.node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: var(--sk-r-sm);
  background: none;
  cursor: pointer;
  text-align: left;
  color: inherit;
  font-size: var(--sk-fs-sm);
}
.node:hover {
  background: var(--sk-bg);
}
.node.active {
  border-color: var(--sk-primary);
  background: var(--sk-bg);
  font-weight: 600;
}
.node.child {
  padding-left: 22px;
}
.node .badge {
  margin-left: auto;
}
.group-label {
  display: flex;
  justify-content: space-between;
  margin: var(--sk-s3) 0 4px 22px;
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-dim);
  text-transform: none;
}
.none {
  padding-left: 22px;
}
.detail h2 {
  font-size: var(--sk-fs-lg);
}
.ddl {
  margin: var(--sk-s3) 0;
  padding: var(--sk-s3);
  border: 1px solid var(--sk-border);
  border-left: 3px solid var(--sk-primary);
  border-radius: var(--sk-r-sm);
  background: var(--sk-bg);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--sk-fs-sm);
  line-height: 1.7;
}
.sql {
  margin: var(--sk-s2) 0;
  padding: var(--sk-s3);
  border: 1px dashed var(--sk-border);
  border-radius: var(--sk-r-sm);
  background: var(--sk-bg);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--sk-fs-sm);
}
.notes {
  margin: 0;
  padding-left: 18px;
  color: var(--sk-text-dim);
  line-height: 1.8;
}
.empty {
  border-style: dashed;
}
.error-box {
  padding: var(--sk-s3);
  border: 1px solid var(--sk-danger, #d93025);
  border-radius: var(--sk-r-sm);
  color: var(--sk-danger, #d93025);
  background: rgba(217, 48, 37, 0.06);
}
.mt-3 {
  margin-top: var(--sk-s4);
}
</style>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>查询台</h1>
        <p class="muted small mb-0">
          用一句类似 SQL 的话查 Skill 库。<b>只读</b>，表名与列名都走白名单，
          值一律参数绑定 —— 你的输入永远不会被拼进 SQL。
        </p>
      </div>
      <RouterLink class="btn" to="/ms">回到库管理</RouterLink>
    </div>

    <div class="query-layout">
      <div class="card">
        <div class="card-head">
          <h3>写一条查询</h3>
          <span class="dim tiny">支持 SELECT / WHERE / ORDER BY / LIMIT</span>
        </div>
        <div class="card-body col">
          <textarea v-model="sql" class="textarea code" spellcheck="false" @keydown.ctrl.enter="run" />
          <div class="row wrap">
            <button class="btn primary" :disabled="busy" @click="run">{{ busy ? '执行中…' : '执行（Ctrl+Enter）' }}</button>
            <button class="btn" :disabled="busy" @click="explain">看查询计划</button>
            <button class="btn ghost" @click="sql = SAMPLES[0]">填示例</button>
          </div>
          <div class="row wrap">
            <span class="tiny dim">示例：</span>
            <button v-for="(s, i) in SAMPLES" :key="i" class="btn ghost sm mono" @click="sql = s">
              {{ short(s) }}
            </button>
          </div>
          <p v-if="error" class="err small mb-0">{{ error }}</p>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <h3>可用表与列</h3>
          <span class="dim tiny">白名单</span>
        </div>
        <div class="card-body tables">
          <div v-for="t in tables" :key="t.table">
            <b class="mono small">{{ t.table }}</b>
            <div class="cols">
              <code v-for="c in t.columns" :key="c" class="tiny" @click="insertColumn(c)">{{ c }}</code>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 查询计划 -->
    <div class="card mt-4" v-if="plan">
      <div class="card-head">
        <h3>查询计划</h3>
        <span class="badge" :class="plan.index_available ? 'ok' : 'warn'">{{ plan.scan_type }}</span>
      </div>
      <div class="card-body">
        <p class="small mb-2">{{ plan.explanation }}</p>
        <dl class="kv">
          <dt>扫描方式</dt><dd>{{ plan.scan_type }}</dd>
          <dt>用到索引</dt><dd class="mono">{{ plan.used_index || '（无）' }}</dd>
          <dt>WHERE 列</dt><dd class="mono">{{ plan.used_columns || '（无）' }}</dd>
          <dt>预估行数</dt><dd>{{ plan.estimated_rows }}</dd>
        </dl>
      </div>
    </div>

    <!-- 结果 -->
    <div class="card mt-4" v-if="result">
      <div class="card-head">
        <h3>结果</h3>
        <span class="dim tiny">{{ result.row_count }} 行 · {{ result.elapsed_ms }} ms</span>
      </div>
      <div class="card-body">
        <SkTable :columns="resultColumns" :rows="result.rows" empty-text="没有数据" />
        <p class="tiny dim mt-2 mb-0 mono">
          {{ result.sql_echo }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import { explainQuery, getTables, runQuery } from '@/api/ms';

const SAMPLES = [
  "SELECT name, risk_level, category_code FROM skills WHERE risk_level = 'safe' LIMIT 10",
  "SELECT COUNT(*) AS n FROM skills WHERE scene_code = 'med'",
  "SELECT name, risk_score FROM skills ORDER BY risk_score DESC LIMIT 10",
  "SELECT code, name, skill_count FROM skillsets ORDER BY skill_count DESC",
  "SELECT code, columns_col, status, entry_count FROM indexes",
  "SELECT name, constraint_type, severity, violation_count FROM constraints",
  "SELECT name, avg_score, eval_count FROM skills WHERE eval_count > 0 ORDER BY avg_score DESC",
];

const sql = ref(SAMPLES[0]);
const tables = ref([]);
const result = ref(null);
const plan = ref(null);
const error = ref('');
const busy = ref(false);

const resultColumns = computed(() =>
  (result.value?.columns || []).map((c) => ({ key: c, label: c })),
);

function short(s) {
  return s.length > 42 ? s.slice(0, 42) + '…' : s;
}

function insertColumn(c) {
  sql.value += (sql.value.endsWith(' ') ? '' : ' ') + c;
}

async function run() {
  busy.value = true;
  error.value = '';
  plan.value = null;
  try {
    result.value = await runQuery(sql.value);
    if (result.value.row_count === 0) error.value = '';
  } catch (e) {
    result.value = null;
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

async function explain() {
  busy.value = true;
  error.value = '';
  try {
    plan.value = await explainQuery(sql.value);
  } catch (e) {
    plan.value = null;
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  try {
    tables.value = await getTables();
  } catch (e) {
    error.value = e.message;
  }
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
.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
.tables {
  max-height: 420px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cols {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.cols code {
  background: var(--sk-bg);
  border: 1px solid var(--sk-border);
  border-radius: 4px;
  padding: 1px 5px;
  cursor: pointer;
}
.cols code:hover {
  border-color: var(--sk-primary);
  color: var(--sk-primary);
}
.kv {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 4px var(--sk-s3);
  margin: 0;
  font-size: var(--sk-fs-sm);
}
.kv dt {
  color: var(--sk-text-3);
}
.kv dd {
  margin: 0;
}
.err {
  color: var(--sk-danger);
  white-space: pre-wrap;
}
@media (max-width: 1000px) {
  .query-layout {
    grid-template-columns: 1fr;
  }
}
</style>

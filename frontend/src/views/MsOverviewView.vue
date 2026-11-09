<template>
  <div>
    <div class="page-head">
      <div>
        <h1>库管理（Skill-MS）</h1>
        <p class="muted small mb-0">
          把 Skill 库当成一个数据库来管：<b>库 → 表 → 索引 → 约束 → 事件 → 台账</b>。
          所有写操作都经过同一套引擎，任何一次改动都能回答"被哪条约束放行、动了哪些索引、触发了哪些事件"。
        </p>
      </div>
      <div class="row">
        <RouterLink class="btn" to="/ms/objects">对象浏览器</RouterLink>
        <RouterLink class="btn" to="/ms/query">查询台</RouterLink>
        <RouterLink class="btn" to="/ms/indexes">索引</RouterLink>
        <RouterLink class="btn" to="/ms/constraints">约束</RouterLink>
        <RouterLink class="btn" to="/ms/events">事件</RouterLink>
      </div>
    </div>

    <p v-if="loading" class="dim">加载中…</p>
    <template v-else-if="schema">
      <!-- 概念解释 -->
      <div class="section-title"><h2>先弄清这五个概念</h2></div>
      <div class="grid wide">
        <div v-for="c in schema.concepts" :key="c.term" class="card concept">
          <b>{{ c.term }}</b>
          <p class="small mb-0 mt-2">{{ c.plain }}</p>
          <div class="tiny dim mt-2">类比：{{ c.analogy }}</div>
        </div>
      </div>

      <!-- 库 -->
      <div class="section-title">
        <h2>逻辑库</h2>
        <span class="dim small">默认库 default 不可删除</span>
      </div>
      <div class="grid narrow">
        <div v-for="s in schema.skillsets" :key="s.code" class="card set">
          <div class="row between">
            <b class="mono">{{ s.code }}</b>
            <span class="badge plain">{{ s.skillCount }} 个 Skill</span>
          </div>
          <div class="small">{{ s.name }}</div>
          <div class="tiny dim">{{ s.description }}</div>
        </div>
      </div>

      <!-- 四类对象计数 -->
      <div class="section-title"><h2>库里的对象</h2></div>
      <div class="grid narrow">
        <RouterLink to="/ms/indexes" class="card obj">
          <div class="n">{{ schema.stats.index }}</div>
          <div class="l">索引</div>
          <div class="tiny dim">给属性建"目录页"，查得更快</div>
        </RouterLink>
        <RouterLink to="/ms/constraints" class="card obj">
          <div class="n">{{ schema.stats.constraint }}</div>
          <div class="l">约束</div>
          <div class="tiny dim">不许违反的规矩，违反就拦<br /></div>
        </RouterLink>
        <RouterLink to="/ms/events" class="card obj">
          <div class="n">{{ schema.stats.event }}</div>
          <div class="l">事件</div>
          <div class="tiny dim">发生某事就自动做某事</div>
        </RouterLink>
        <RouterLink to="/ms/events" class="card obj">
          <div class="n">{{ schema.stats.tx_log }}</div>
          <div class="l">事务台账</div>
          <div class="tiny dim">每次写操作的解释</div>
        </RouterLink>
      </div>

      <!-- 表 -->
      <div class="section-title">
        <h2>"表"与字段</h2>
        <span class="dim small">节点：这是元数据，不是真实行</span>
      </div>
      <div class="card">
        <div class="card-body">
          <div v-for="t in schema.tables" :key="t.name" class="table-block">
            <div class="row between">
              <b class="mono">{{ t.name }}</b>
              <span class="badge plain">{{ t.rowCount }} 行</span>
            </div>
            <div class="small dim">{{ t.label }} · {{ t.description }}</div>
            <div class="cols">
              <span v-for="c in t.columns" :key="c.name" class="col-chip" :title="c.meaning">
                <code>{{ c.name }}</code>
                <span class="tiny dim">{{ c.type }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getSchema } from '@/api/ms';
import { notifyError } from '@/api/client';

const schema = ref(null);
const loading = ref(true);

onMounted(async () => {
  try {
    schema.value = await getSchema();
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
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
.concept {
  padding: var(--sk-s4);
}
.set {
  padding: var(--sk-s3) var(--sk-s4);
}
.obj {
  padding: var(--sk-s4);
  text-decoration: none;
  color: inherit;
  text-align: center;
}
.obj:hover {
  border-color: var(--sk-primary);
  text-decoration: none;
}
.obj .n {
  font-size: var(--sk-fs-xl);
  font-weight: 700;
  color: var(--sk-primary-strong);
}
.obj .l {
  font-weight: 600;
}
.table-block {
  padding: var(--sk-s3) 0;
  border-bottom: 1px dashed var(--sk-border);
}
.table-block:last-child {
  border-bottom: none;
}
.cols {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.col-chip {
  display: inline-flex;
  gap: 4px;
  align-items: baseline;
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: 2px 6px;
  background: var(--sk-bg);
}
</style>

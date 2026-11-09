<template>
  <div>
    <div class="page-head">
      <div>
        <h1>在线文档</h1>
        <p class="muted small mb-0">本站的说明书。改文档只改 Markdown，重启即生效。</p>
      </div>
    </div>

    <div class="doc-layout">
      <aside class="card catalog">
        <div class="card-head"><h3>目录</h3><span class="dim tiny">{{ docs.length }} 篇</span></div>
        <div class="card-body">
          <template v-for="(group, name) in grouped" :key="name">
            <div class="group-label">{{ name }}</div>
            <RouterLink
              v-for="d in group"
              :key="d.doc_key"
              :to="`/docs/${d.doc_key}`"
              class="doc-link"
              :class="{ active: activeKey === d.doc_key }"
            >
              {{ d.title }}
            </RouterLink>
          </template>
        </div>
      </aside>

      <section class="card">
        <div class="card-head">
          <h3>{{ page?.title || '请选择一篇文档' }}</h3>
          <span v-if="page" class="dim tiny">更新于 {{ page.updated_at }}</span>
        </div>
        <div class="card-body">
          <p v-if="page?.summary" class="muted small">{{ page.summary }}</p>
          <SkMarkdown v-if="page" :source="page.body" />
          <p v-else class="dim">从左边选一篇开始读。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import SkMarkdown from '@/components/SkMarkdown.vue';
import { getDoc, listDocs } from '@/api';
import { notifyError } from '@/api/client';

const route = useRoute();
const docs = ref([]);
const page = ref(null);

const activeKey = computed(() => route.params.key || docs.value[0]?.doc_key || '');

const grouped = computed(() => {
  const out = {};
  for (const d of docs.value) {
    const g = d.section || '其它';
    (out[g] ||= []).push(d);
  }
  return out;
});

async function load() {
  try {
    if (!docs.value.length) docs.value = await listDocs();
    const key = route.params.key || docs.value[0]?.doc_key;
    if (!key) return;
    page.value = await getDoc(key);
  } catch (e) {
    notifyError(e);
  }
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.page-head {
  margin-bottom: var(--sk-s4);
}
.doc-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
.catalog {
  position: sticky;
  top: calc(var(--sk-header-h) + 12px);
}
.catalog .card-body {
  padding: var(--sk-s2) 0 var(--sk-s3);
}
.group-label {
  padding: var(--sk-s2) var(--sk-s4) 2px;
  font-size: var(--sk-fs-xs);
  font-weight: 700;
  color: var(--sk-text-3);
}
.doc-link {
  display: block;
  padding: 6px var(--sk-s4);
  color: var(--sk-text-2);
  font-size: var(--sk-fs-sm);
  text-decoration: none;
  border-left: 3px solid transparent;
}
.doc-link:hover {
  background: var(--sk-bg);
  text-decoration: none;
}
.doc-link.active {
  color: var(--sk-primary-strong);
  background: var(--sk-primary-weak);
  border-left-color: var(--sk-accent);
  font-weight: 650;
}
@media (max-width: 900px) {
  .doc-layout {
    grid-template-columns: 1fr;
  }
  .catalog {
    position: static;
  }
}
</style>

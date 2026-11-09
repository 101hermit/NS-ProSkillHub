<template>
  <div>
    <div class="page-head">
      <div>
        <h1>贡献者</h1>
        <p class="muted small mb-0">
          两种身份：<b>个人创作者</b>与<b>部门 / 企业创作者</b>。身份只影响归类与署名，流程与审核强度一样。
        </p>
      </div>
      <div class="row">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="btn sm"
          :class="{ primary: kind === t.key }"
          @click="switchKind(t.key)"
        >
          {{ t.label }}<span v-if="kindCounts[t.key]" class="tab-count">{{ kindCounts[t.key] }}</span>
        </button>
      </div>
    </div>

    <p v-if="loading" class="dim">加载中…</p>
    <div v-else-if="!items.length" class="card empty-state">
      <h3>还没有这一类贡献者</h3>
      <p class="muted small">先把你的 Skill 交上来，你就会出现在这里。</p>
      <RouterLink class="btn primary" to="/contribute">去贡献</RouterLink>
    </div>

    <div v-else class="grid narrow">
      <RouterLink
        v-for="c in items"
        :key="c.source + '/' + c.author"
        :to="`/contributors/${encodeURIComponent(c.source)}/${encodeURIComponent(c.author)}`"
        class="card contrib"
      >
        <div class="row between">
          <span class="name ellipsis">{{ c.name || c.author }}</span>
          <span class="badge" :class="c.kind === 'personal' ? 'info' : 'plain'">{{ c.kind_label }}</span>
        </div>
        <p class="tiny dim clamp-3 mb-0">{{ c.description || '（未填写简介）' }}</p>
        <div class="foot">
          <span class="tiny dim">{{ c.source }}</span>
          <span class="tiny dim">{{ c.skill_count }} 个 Skill · ↓{{ c.download_total }}</span>
        </div>
      </RouterLink>
    </div>

    <SkPagination
      :page="page"
      :size="size"
      :total="total"
      :total-pages="totalPages"
      :size-options="[12, 24, 48, 96]"
      @change="onPage"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SkPagination from '@/components/SkPagination.vue';
import { listContributors } from '@/api';
import { notifyError } from '@/api/client';
import { intOr } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const items = ref([]);
const kindCounts = ref({});
const total = ref(0);
const totalPages = ref(0);
const loading = ref(true);
const kind = ref(route.query.kind || 'all');
const page = ref(intOr(route.query.page, 1));
const size = ref(intOr(route.query.size, 24));

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'personal', label: '个人' },
  { key: 'department', label: '部门 / 企业' },
];

async function load() {
  loading.value = true;
  try {
    const data = await listContributors({
      page: page.value,
      size: size.value,
      kind: kind.value === 'all' ? undefined : kind.value,
    });
    items.value = data.items;
    total.value = data.total;
    totalPages.value = data.total_pages;
    kindCounts.value = data.kind_counts || {};
    if (kind.value !== 'all' && (kindCounts.value[kind.value] || 0) === 0 && total.value === 0) {
      // 该类暂无数据：保留空态展示，不自动跳转（跳转会让人以为点错了）
    }
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function switchKind(k) {
  kind.value = k;
  page.value = 1;
  syncQuery();
}

function onPage({ page: p, size: s }) {
  if (s) size.value = s;
  page.value = p || 1;
  syncQuery();
}

function syncQuery() {
  const q = {};
  if (kind.value !== 'all') q.kind = kind.value;
  if (page.value > 1) q.page = String(page.value);
  if (size.value !== 24) q.size = String(size.value);
  router.push({ path: '/contributors', query: q });
  load();
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
.tab-count {
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.8;
}
.contrib {
  padding: var(--sk-s4);
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-decoration: none;
  color: inherit;
}
.contrib:hover {
  border-color: var(--sk-primary);
  box-shadow: var(--sk-shadow-2);
  text-decoration: none;
}
.name {
  font-weight: 650;
}
.foot {
  display: flex;
  justify-content: space-between;
  border-top: 1px dashed var(--sk-border);
  padding-top: 8px;
  margin-top: auto;
}
.empty-state {
  padding: var(--sk-s10);
  text-align: center;
}
</style>

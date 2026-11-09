<template>
  <div>
    <div class="page-head">
      <div>
        <h1>Skill 库</h1>
        <p class="muted small mb-0">
          共 {{ total }} 个 Skill。筛选条件会写进地址栏，刷新与分享都能保持。
        </p>
      </div>
      <div class="view-switch">
        <button class="btn sm" :class="{ primary: view === 'grid' }" @click="setView('grid')">格子</button>
        <button class="btn sm" :class="{ primary: view === 'list' }" @click="setView('list')">列表</button>
      </div>
    </div>

    <div class="layout-2">
      <!-- 筛选器 -->
      <aside class="filters card">
        <div class="card-head"><h3>筛选</h3><button class="btn ghost sm" @click="reset">重置</button></div>
        <div class="card-body col">
          <div class="field">
            <label>关键词</label>
            <input v-model="localKw" class="input" placeholder="名称/描述/标签" @keyup.enter="apply" />
          </div>
          <div class="field">
            <label>分类</label>
            <select v-model="filters.category" class="select">
              <option value="">全部分类</option>
              <option v-for="c in categories" :key="c.code" :value="c.code">
                {{ c.label }}（{{ c.count }}）
              </option>
            </select>
          </div>
          <div class="field">
            <label>行业场景</label>
            <select v-model="filters.scene" class="select">
              <option value="">全部场景</option>
              <option v-for="s in scenes" :key="s.scene_code" :value="s.scene_code">{{ s.label }}</option>
            </select>
          </div>
          <div class="field">
            <label>风险等级 <span class="hint" title="分数越低越安全">?</span></label>
            <select v-model="filters.risk_level" class="select">
              <option value="">全部等级</option>
              <option v-for="r in risks" :key="r.key" :value="r.key">{{ r.label }}</option>
            </select>
          </div>
          <div class="field">
            <label>贡献者类型</label>
            <select v-model="filters.author_kind" class="select">
              <option value="">全部</option>
              <option value="personal">个人</option>
              <option value="department">部门 / 企业</option>
            </select>
          </div>
          <div class="field">
            <label>来源</label>
            <select v-model="filters.source" class="select">
              <option value="">全部来源</option>
              <option value="library">本地库</option>
              <option value="github">GitHub</option>
              <option value="gitee">Gitee</option>
              <option value="gitlab">GitLab</option>
              <option value="upload">在线上传</option>
            </select>
          </div>
          <div class="field">
            <label>状态</label>
            <select v-model="filters.status" class="select">
              <option value="published">已发布</option>
              <option value="archived">已归档</option>
              <option value="all">全部状态</option>
            </select>
          </div>
          <button class="btn primary block" @click="apply">应用筛选</button>
        </div>
      </aside>

      <!-- 结果 -->
      <section>
        <div class="toolbar">
          <div class="row wrap">
            <span class="small dim">排序</span>
            <select :value="sortValue" class="select sort-select" @change="onSort">
              <option value="updated_at:desc">最近更新</option>
              <option value="download_count:desc">下载最多</option>
              <option value="risk_score:asc">风险最低</option>
              <option value="risk_score:desc">风险最高</option>
              <option value="name:asc">名称 A→Z</option>
              <option value="file_count:desc">附件最多</option>
            </select>
            <span v-if="activeChips.length" class="row wrap">
              <span v-for="chip in activeChips" :key="chip.key" class="badge info">
                {{ chip.label }}：{{ chip.value }}
                <button class="chip-x" @click="clearChip(chip.key)">✕</button>
              </span>
            </span>
          </div>
        </div>

        <p v-if="loading" class="dim">加载中…</p>

        <template v-else-if="items.length === 0">
          <div class="card empty-state">
            <div class="ico">⌕</div>
            <h3>没有匹配的 Skill</h3>
            <p class="muted small">换个关键词，或者点「重置」清掉筛选条件。</p>
            <button class="btn" @click="reset">重置筛选</button>
          </div>
        </template>

        <template v-else>
          <div v-if="view === 'grid'" class="grid">
            <SkSkillCard v-for="s in items" :key="s.skill_id" :card="s" :kw="filters.kw" />
          </div>
          <SkTable
            v-else
            :columns="columns"
            :rows="items"
            :sort-key="filters.sort"
            :sort-order="filters.order"
            @sort="onTableSort"
            @row-click="openSkill"
          >
            <template #name="{ row }">
              <RouterLink :to="`/skills/${encodeURIComponent(row.skill_id)}`">{{ row.name }}</RouterLink>
              <div class="tiny dim clamp-2">{{ row.description }}</div>
            </template>
            <template #risk_level="{ row }">
              <span class="badge" :class="row.risk_level">{{ row.risk_label }}</span>
            </template>
            <template #category_label="{ row }">
              <span class="badge plain">{{ row.category_label }}</span>
            </template>
            <template #source="{ row }">{{ row.source }}</template>
          </SkTable>

          <SkPagination
            :page="page"
            :size="size"
            :total="total"
            :total-pages="totalPages"
            @change="onPage"
          />
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SkSkillCard from '@/components/SkSkillCard.vue';
import SkTable from '@/components/SkTable.vue';
import SkPagination from '@/components/SkPagination.vue';
import { getCategories, listSkills } from '@/api';
import { intOr } from '@/utils/format';
import { notifyError } from '@/api/client';

const route = useRoute();
const router = useRouter();

const view = ref(localStorage.getItem('skh.view') || 'grid');
const localKw = ref('');
const categories = ref([]);
const items = ref([]);
const total = ref(0);
const totalPages = ref(0);
const loading = ref(true);

const filters = reactive({
  kw: '',
  category: '',
  scene: '',
  risk_level: '',
  author_kind: '',
  source: '',
  status: 'published',
  sort: 'updated_at',
  order: 'desc',
});
const page = ref(1);
const size = ref(20);

const scenes = computed(() => categories.value.filter((c) => c.kind === 'domain'));
const risks = [
  { key: 'safe', label: '安全（0-20）' },
  { key: 'low', label: '低风险（21-50）' },
  { key: 'medium', label: '中风险（51-80）' },
  { key: 'high', label: '高风险（81-100）' },
  { key: 'unrated', label: '未检测' },
];

const sortValue = computed(() => `${filters.sort}:${filters.order}`);

const activeChips = computed(() => {
  const out = [];
  if (filters.kw) out.push({ key: 'kw', label: '关键词', value: filters.kw });
  if (filters.category) out.push({ key: 'category', label: '分类', value: labelOf(filters.category) });
  if (filters.scene) out.push({ key: 'scene', label: '场景', value: labelOf(filters.scene) });
  if (filters.risk_level) out.push({ key: 'risk_level', label: '风险', value: labelOfRisk(filters.risk_level) });
  if (filters.author_kind) out.push({ key: 'author_kind', label: '身份', value: filters.author_kind === 'personal' ? '个人' : '部门' });
  if (filters.source) out.push({ key: 'source', label: '来源', value: filters.source });
  return out;
});

const columns = [
  { key: 'name', label: 'Skill', width: '34%' },
  { key: 'category_label', label: '分类', width: '110px' },
  { key: 'risk_level', label: '风险', width: '92px' },
  { key: 'source', label: '来源', width: '92px' },
  { key: 'version', label: '版本', width: '80px' },
  { key: 'download_count', label: '下载', width: '70px', sortable: true, align: 'right' },
  { key: 'updated_at', label: '更新于', width: '150px', sortable: true },
];

function labelOf(code) {
  const c = categories.value.find((x) => x.code === code || x.scene_code === code);
  return c ? c.label : code;
}
function labelOfRisk(key) {
  return risks.find((r) => r.key === key)?.label || key;
}

function syncFromRoute() {
  const q = route.query;
  filters.kw = q.kw || '';
  filters.category = q.category || '';
  filters.scene = q.scene || '';
  filters.risk_level = q.risk_level || '';
  filters.author_kind = q.author_kind || '';
  filters.source = q.source || '';
  filters.status = q.status || 'published';
  const [sort, order] = String(q.sort || 'updated_at:desc').split(':');
  filters.sort = sort || 'updated_at';
  filters.order = order || 'desc';
  localKw.value = filters.kw;
  page.value = intOr(q.page, 1);
  size.value = intOr(q.size, 20);
}

async function fetchList() {
  loading.value = true;
  try {
    const data = await listSkills({
      page: page.value,
      size: size.value,
      kw: filters.kw || undefined,
      category: filters.category || undefined,
      scene: filters.scene || undefined,
      risk_level: filters.risk_level || undefined,
      author_kind: filters.author_kind || undefined,
      source: filters.source || undefined,
      status: filters.status,
      sort: filters.sort,
      order: filters.order,
    });
    items.value = data.items;
    total.value = data.total;
    totalPages.value = data.total_pages;
  } catch (e) {
    notifyError(e);
  } finally {
    loading.value = false;
  }
}

function pushQuery() {
  const query = {};
  if (filters.kw) query.kw = filters.kw;
  if (filters.category) query.category = filters.category;
  if (filters.scene) query.scene = filters.scene;
  if (filters.risk_level) query.risk_level = filters.risk_level;
  if (filters.author_kind) query.author_kind = filters.author_kind;
  if (filters.source) query.source = filters.source;
  if (filters.status !== 'published') query.status = filters.status;
  if (sortValue.value !== 'updated_at:desc') query.sort = sortValue.value;
  if (page.value > 1) query.page = String(page.value);
  if (size.value !== 20) query.size = String(size.value);
  router.push({ path: '/skills', query });
}

function apply() {
  filters.kw = localKw.value.trim();
  page.value = 1;
  pushQuery();
}

function reset() {
  router.push({ path: '/skills' });
}

function clearChip(key) {
  filters[key] = '';
  pushQuery();
}

function onSort(e) {
  const [sort, order] = e.target.value.split(':');
  filters.sort = sort;
  filters.order = order;
  page.value = 1;
  pushQuery();
}

function onTableSort({ key, order }) {
  filters.sort = key;
  filters.order = order;
  pushQuery();
}

function onPage({ page: p, size: s }) {
  if (s) size.value = s;
  page.value = p || 1;
  pushQuery();
}

function setView(v) {
  view.value = v;
  localStorage.setItem('skh.view', v);
}

function openSkill(row) {
  router.push(`/skills/${encodeURIComponent(row.skill_id)}`);
}

watch(
  () => route.fullPath,
  () => {
    if (route.path !== '/skills') return;
    syncFromRoute();
    fetchList();
  },
);

onMounted(async () => {
  const c = await getCategories().catch(() => []);
  categories.value = c;
  syncFromRoute();
  await fetchList();
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
.view-switch {
  display: flex;
  gap: 6px;
}
.layout-2 {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
.filters {
  position: sticky;
  top: calc(var(--sk-header-h) + 12px);
}
.filters .card-body {
  padding: var(--sk-s4);
}
.toolbar {
  margin-bottom: var(--sk-s3);
}
.sort-select {
  width: auto;
  height: 30px;
  font-size: var(--sk-fs-sm);
}
.chip-x {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 10px;
  padding: 0 0 0 2px;
}
.empty-state {
  padding: var(--sk-s10);
  text-align: center;
}
.empty-state .ico {
  font-size: 40px;
  color: var(--sk-text-3);
  margin-bottom: var(--sk-s3);
}
@media (max-width: 1024px) {
  .layout-2 {
    grid-template-columns: 1fr;
  }
  .filters {
    position: static;
  }
}
</style>

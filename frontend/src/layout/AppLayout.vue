<template>
  <div class="layout" :class="{ collapsed: app.siderCollapsed }">
    <!-- 顶部栏 -->
    <header class="header">
      <button class="btn ghost sm menu-btn" aria-label="切换侧栏" @click="app.toggleSider()">☰</button>
      <RouterLink to="/" class="brand">
        <span class="logo">SK</span>
        <span class="brand-text">
          <b>{{ app.meta?.product_name }}</b>
          <span class="badge acc">{{ app.abbr }} {{ app.version }}</span>
        </span>
      </RouterLink>

      <form class="search" @submit.prevent="doSearch">
        <input v-model="kw" class="input" placeholder="搜索 Skill（名称 / 描述 / 标签 / 正文）…" />
        <button class="btn primary sm" type="submit">搜索</button>
      </form>

      <div class="grow" />

      <!-- 身份区：登录后显示"我是谁 + 退出"，未登录显示"登录 / 访客" -->
      <div class="identity">
        <template v-if="app.signedIn">
          <span class="dot" :class="app.role" :title="`身份：${app.actor}（${app.roleLabel}）`" />
          <span class="who ellipsis">{{ app.displayName }}</span>
          <span class="badge plain tiny">{{ app.roleLabel }}</span>
          <button class="btn ghost sm" title="退出登录" @click="onLogout">
            <SkIcon name="log-out" :size="15" />退出
          </button>
        </template>
        <template v-else>
          <RouterLink class="btn primary sm" :to="{ path: '/login', query: { next: route.fullPath } }">
            <SkIcon name="log-in" :size="15" />登录
          </RouterLink>
          <button class="btn sm" :disabled="busyGuest" @click="onGuest">访客浏览</button>
        </template>
      </div>
    </header>

    <div class="body">
      <!-- 侧栏 -->
      <aside class="sider">
        <nav>
          <template v-for="g in visibleGroups" :key="g.key">
            <div class="group-label">{{ g.label }}</div>
            <RouterLink
              v-for="item in g.items"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: isActive(item) }"
              :title="item.title"
            >
              <SkIcon class="ico" :name="item.icon" :size="17" />
              <span class="nav-text">{{ item.title }}</span>
            </RouterLink>
          </template>
        </nav>
        <div class="sider-foot">
          <div class="small dim">
            <div>{{ app.meta?.abbr }} {{ app.version }}</div>
            <div v-if="app.health" :title="`数据库：${app.health.dialect_detail}`">
              {{ app.health.dialect === 'mysql' ? 'MySQL 8' : 'H2' }} ·
              {{ app.health.skill_count }} 个 Skill
            </div>
          </div>
        </div>
      </aside>

      <!-- 内容 -->
      <main class="content">
        <div class="content-inner">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SkIcon from '@/components/SkIcon.vue';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();
const route = useRoute();
const router = useRouter();
const kw = ref('');

/** 菜单分组（组顺序即侧栏顺序；新增能力只往对应组里加一项）。 */
const GROUPS = [
  { key: 'explore', label: '探索', paths: ['/', '/skills', '/contributors'] },
  { key: 'ms', label: '库管理', paths: ['/ms', '/ms/objects', '/ms/indexes', '/ms/constraints', '/ms/events', '/ms/query'] },
  { key: 'verify', label: '验证', paths: ['/eval/live', '/eval/quality', '/access', '/conn', '/eval/matrix', '/eval/lab'] },
  { key: 'insight', label: '洞见', paths: ['/kg', '/ix'] },
  { key: 'contribute', label: '贡献', paths: ['/contribute'] },
  { key: 'site', label: '站点', paths: ['/risk', '/docs', '/about'] },
];

const ICONS = {
  '/': 'house',
  '/skills': 'library',
  '/contributors': 'users',
  '/contribute': 'upload',
  '/risk': 'alert-triangle',
  '/docs': 'book-open',
  '/about': 'info',
  '/ms': 'database',
  '/ms/objects': 'list',
  '/ms/indexes': 'key-round',
  '/ms/constraints': 'shield-check',
  '/ms/events': 'zap',
  '/ms/query': 'terminal',
  '/eval/live': 'play',
  '/eval/quality': 'bar-chart-3',
  '/access': 'plug',
  '/conn': 'network',
  '/eval/matrix': 'layers',
  '/eval/lab': 'flask-conical',
  '/kg': 'share-2',
  '/ix': 'sigma',
};

/** 由路由表推导菜单，并按能力开关过滤。 */
const visibleGroups = computed(() => {
  const byPath = new Map(router.getRoutes().map((r) => [r.path, r]));
  return GROUPS.map((g) => ({
    key: g.key,
    label: g.label,
    items: g.paths
      .map((p) => byPath.get(p))
      .filter(Boolean)
      .filter((r) => (r.meta?.cap ? app.can(r.meta.cap) : true))
      .map((r) => ({ path: r.path, name: r.name, title: r.meta.title, icon: ICONS[r.path] || '•' })),
  })).filter((g) => g.items.length > 0);
});

const busyGuest = ref(false);

/** 退出登录：回首页并提示。 */
async function onLogout() {
  await app.logout();
  toast.ok('已退出登录');
  router.push('/');
}

/** 一键访客：直接拿只读会话，不用注册。 */
async function onGuest() {
  busyGuest.value = true;
  try {
    await app.enterAsGuest();
    toast.ok('已进入（访客·只读）');
  } catch (e) {
    toast.err(e.message || '进入失败');
  } finally {
    busyGuest.value = false;
  }
}

function isActive(item) {
  if (item.path === '/') return route.path === '/';
  return route.path === item.path || route.path.startsWith(item.path + '/');
}

function doSearch() {
  const q = kw.value.trim();
  if (!q) return;
  router.push({ path: '/skills', query: { kw: q } });
}
</script>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* ------------------------------- 顶部栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--sk-header-h);
  display: flex;
  align-items: center;
  gap: var(--sk-s3);
  padding: 0 var(--sk-s4);
  background: var(--sk-surface);
  border-bottom: 1px solid var(--sk-border);
}
.menu-btn {
  display: none;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--sk-text);
  text-decoration: none;
  min-width: 0;
}
.brand:hover {
  text-decoration: none;
}
.logo {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--sk-primary), var(--sk-accent));
  color: #fff;
  font-weight: 800;
  font-size: 13px;
  letter-spacing: -0.5px;
}
.brand-text {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.badge.acc {
  background: var(--sk-primary-weak);
  color: var(--sk-primary-strong);
}
.search {
  display: flex;
  gap: 6px;
  width: min(420px, 38vw);
  margin-left: var(--sk-s4);
}
.search .input {
  height: 34px;
}
.identity {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sk-text-3);
}
.dot.editor {
  background: var(--sk-warn);
}
.dot.admin {
  background: var(--sk-danger);
}
.who {
  max-width: 120px;
  font-size: var(--sk-fs-sm);
  font-weight: 600;
}
.role-select {
  width: auto;
  height: 30px;
  font-size: var(--sk-fs-sm);
  padding: 0 6px;
}

/* ------------------------------- 主体 */
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.sider {
  width: var(--sk-sider-w);
  flex: 0 0 var(--sk-sider-w);
  background: var(--sk-surface);
  border-right: 1px solid var(--sk-border);
  padding: var(--sk-s3) 0 var(--sk-s4);
  display: flex;
  flex-direction: column;
  transition: width var(--sk-dur) var(--sk-ease), flex-basis var(--sk-dur) var(--sk-ease);
  position: sticky;
  top: var(--sk-header-h);
  height: calc(100vh - var(--sk-header-h));
  overflow-y: auto;
}
.group-label {
  padding: var(--sk-s3) var(--sk-s4) 4px;
  font-size: var(--sk-fs-xs);
  font-weight: 700;
  color: var(--sk-text-3);
  letter-spacing: 0.04em;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px var(--sk-s4);
  color: var(--sk-text-2);
  font-size: var(--sk-fs);
  text-decoration: none;
  border-left: 3px solid transparent;
  white-space: nowrap;
}
.nav-item:hover {
  background: var(--sk-bg);
  color: var(--sk-text);
  text-decoration: none;
}
.nav-item.active {
  background: var(--sk-primary-weak);
  color: var(--sk-primary-strong);
  border-left-color: var(--sk-accent);
  font-weight: 650;
}
.nav-item .ico {
  width: 18px;
  text-align: center;
  font-size: 15px;
}
.sider-foot {
  margin-top: auto;
  padding: var(--sk-s4);
  border-top: 1px solid var(--sk-border);
}
.content {
  flex: 1;
  min-width: 0;
  padding: var(--sk-s5) var(--sk-s6) var(--sk-s10);
}
.content-inner {
  max-width: var(--sk-content-max);
  margin: 0 auto;
}

/* ------------------------------- 折叠态 */
.layout.collapsed .sider {
  width: var(--sk-sider-w-collapsed);
  flex-basis: var(--sk-sider-w-collapsed);
}
.layout.collapsed .nav-text,
.layout.collapsed .group-label,
.layout.collapsed .sider-foot {
  display: none;
}
.layout.collapsed .nav-item {
  justify-content: center;
  padding: 10px 0;
}

/* ------------------------------- 响应式 */
@media (max-width: 1024px) {
  .menu-btn {
    display: inline-flex;
  }
  .layout:not(.collapsed) .sider {
    width: var(--sk-sider-w);
    flex-basis: var(--sk-sider-w);
  }
}
@media (max-width: 768px) {
  .search {
    display: none;
  }
  .content {
    padding: var(--sk-s4);
  }
  .sider {
    position: fixed;
    z-index: 200;
    height: calc(100vh - var(--sk-header-h));
  }
  .layout.collapsed .sider {
    display: none;
  }
}
</style>

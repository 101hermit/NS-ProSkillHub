import { createRouter, createWebHashHistory } from 'vue-router';

/**
 * 路由表（hash 模式：任何静态托管都不需要 rewrite 规则）。
 *
 * meta.menu 表示"出现在侧栏第几组"，meta.cap 表示"需要哪个能力开关"。
 * 能力未启用的路由**仍然可直达**（便于排障），但不出现在菜单里。
 *
 * 专业版 = 标准版的 19 页 + 连通矩阵 + 实测中心 + 矩阵详情 + 知识图谱 + 知识级分析。
 */
const routes = [
  {
    // 登录 / 注册 / 访客：三系统同一个页面，meta.public 表示不需要会话
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '首页', menu: 'explore' } },
  {
    path: '/skills',
    name: 'skills',
    component: () => import('@/views/SkillListView.vue'),
    meta: { title: 'Skill 库', menu: 'explore' },
  },
  {
    path: '/skills/:id',
    name: 'skillDetail',
    component: () => import('@/views/SkillDetailView.vue'),
    meta: { title: 'Skill 详情' },
  },
  {
    path: '/contributors',
    name: 'contributors',
    component: () => import('@/views/ContributorListView.vue'),
    meta: { title: '贡献者', menu: 'explore' },
  },
  {
    path: '/contributors/:source/:author',
    name: 'contributorDetail',
    component: () => import('@/views/ContributorDetailView.vue'),
    meta: { title: '贡献者详情' },
  },

  // ---------------- 库语义（与标准版一致） ----------------
  {
    path: '/ms',
    name: 'ms',
    component: () => import('@/views/MsOverviewView.vue'),
    meta: { title: '库管理', menu: 'ms', cap: 'ms.library' },
  },
  {
    // 对象浏览：树 + DDL。数据库工具里最经典的一种看法，也是「把 Skill 库当数据库管」最直观的入口
    path: '/ms/objects',
    name: 'msObjects',
    component: () => import('@/views/MsObjectView.vue'),
    meta: { title: '对象浏览', menu: 'ms', cap: 'ms.library' },
  },
  {
    path: '/ms/indexes',
    name: 'msIndexes',
    component: () => import('@/views/MsIndexView.vue'),
    meta: { title: '索引', menu: 'ms', cap: 'ms.library' },
  },
  {
    path: '/ms/constraints',
    name: 'msConstraints',
    component: () => import('@/views/MsConstraintView.vue'),
    meta: { title: '约束', menu: 'ms', cap: 'ms.library' },
  },
  {
    path: '/ms/events',
    name: 'msEvents',
    component: () => import('@/views/MsEventView.vue'),
    meta: { title: '事件', menu: 'ms', cap: 'ms.library' },
  },
  {
    path: '/ms/query',
    name: 'msQuery',
    component: () => import('@/views/MsQueryView.vue'),
    meta: { title: '查询台', menu: 'ms', cap: 'ms.query' },
  },

  // ---------------- 验证 ----------------
  {
    path: '/eval/live',
    name: 'evalLive',
    component: () => import('@/views/LiveEvalView.vue'),
    meta: { title: '单体实测', menu: 'verify', cap: 'eval.live' },
  },
  {
    path: '/eval/quality',
    name: 'evalQuality',
    component: () => import('@/views/QualityView.vue'),
    meta: { title: '质量评估', menu: 'verify', cap: 'eval.quality' },
  },
  {
    path: '/access',
    name: 'access',
    component: () => import('@/views/AccessView.vue'),
    meta: { title: '接入管理', menu: 'verify', cap: 'access.registry' },
  },
  {
    path: '/conn',
    name: 'conn',
    component: () => import('@/views/ConnMatrixView.vue'),
    meta: { title: '连通矩阵', menu: 'verify', cap: 'conn.matrix' },
  },
  {
    path: '/eval/lab',
    name: 'evalLab',
    component: () => import('@/views/EvalLabView.vue'),
    meta: { title: '实测中心', menu: 'verify', cap: 'eval.matrix' },
  },
  {
    path: '/eval/matrix/:code',
    name: 'matrixDetail',
    component: () => import('@/views/MatrixDetailView.vue'),
    meta: { title: '矩阵详情', menu: 'verify', cap: 'eval.matrix' },
  },

  // ---------------- 洞见 ----------------
  {
    path: '/kg',
    name: 'kg',
    component: () => import('@/views/KgView.vue'),
    meta: { title: '知识图谱', menu: 'insight', cap: 'knowledge.graph' },
  },
  {
    path: '/ix',
    name: 'ix',
    component: () => import('@/views/IxView.vue'),
    meta: { title: '知识级分析', menu: 'insight', cap: 'insight.analysis' },
  },

  // ---------------- 公共 ----------------
  {
    path: '/contribute',
    name: 'contribute',
    component: () => import('@/views/ContributeView.vue'),
    meta: { title: '在线贡献', menu: 'contribute', cap: 'skill.contribute' },
  },
  {
    path: '/risk',
    name: 'risk',
    component: () => import('@/views/RiskView.vue'),
    meta: { title: '安全评估', menu: 'site', cap: 'risk.scan' },
  },
  {
    path: '/docs',
    name: 'docs',
    component: () => import('@/views/DocsView.vue'),
    meta: { title: '在线文档', menu: 'site' },
  },
  {
    path: '/docs/:key',
    name: 'docPage',
    component: () => import('@/views/DocsView.vue'),
    meta: { title: '在线文档', menu: 'site' },
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/views/AboutView.vue'),
    meta: { title: '关于本版本', menu: 'site' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved;
    return { top: 0 };
  },
});

router.afterEach((to) => {
  const base = document.title.split(' · ')[0];
  if (to.meta?.title) document.title = `${to.meta.title} · ${base}`;
});

export default router;

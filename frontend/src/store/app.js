import { reactive } from 'vue';
import { getCapabilities, getHealth, getMeta } from '@/api';
import { guest as apiGuest, login as apiLogin, logout as apiLogout, me as apiMe, register as apiRegister } from '@/api/auth';

/**
 * 应用级状态（单例 reactive 对象，不引 Pinia）。
 *
 * 三件事在这里统一：**产品元信息 / 能力开关 / 登录身份**。
 *
 * 身份有三种来源，优先级从高到低：
 *   ① 已登录（有令牌，role 来自令牌）→ 可以写
 *   ② 访客（点了「以访客身份浏览」，拿到 viewer 令牌）→ 只读
 *   ③ 未登录（没有令牌）→ 只读，界面上提示去登录
 *
 * 令牌存 localStorage，请求时由 `api/client.js` 自动加 `Authorization: Bearer …`。
 * 令牌失效（重启换密钥、改密码）时后端会降级为只读身份而不是 401 白屏，页面据此提示"请重新登录"。
 */
const TOKEN_KEY = 'skh.token';

export const app = reactive({
  ready: false,
  meta: null,
  health: null,
  features: [],

  /** 会话：null 表示未登录/未访客。 */
  session: null,
  token: localStorage.getItem(TOKEN_KEY) || '',

  actor: localStorage.getItem('skh.actor') || 'guest',
  role: localStorage.getItem('skh.role') || 'viewer',
  siderCollapsed: localStorage.getItem('skh.sider') === '1',

  /* ------------------------------ 派生值 ------------------------------ */
  get product() {
    return this.meta?.product || 'ESKH';
  },
  get abbr() {
    return this.meta?.abbr || 'ESKH';
  },
  get version() {
    return this.meta?.version || '1.0';
  },
  get siteName() {
    return this.meta?.site_name || 'SkillHub';
  },
  get tagline() {
    return this.meta?.tagline || '';
  },
  /** 是否已登录（含访客）。 */
  get signedIn() {
    return Boolean(this.session && this.session.token);
  },
  /** 是否访客（只读）。 */
  get isGuest() {
    return !this.session || this.session.guest === true || this.role === 'viewer';
  },
  get displayName() {
    return this.session?.display_name || this.actor || '未登录';
  },
  get roleLabel() {
    return (
      this.session?.role_label ||
      { admin: '管理员', editor: '编辑者', viewer: '访客（只读）' }[this.role] ||
      '访客（只读）'
    );
  },
  get canEdit() {
    return this.role === 'editor' || this.role === 'admin';
  },
  get isAdmin() {
    return this.role === 'admin';
  },
  /** 后端下发的高层权限（前端据此灰掉按钮）。 */
  get permissions() {
    return this.session?.permissions || ['read'];
  },

  /* ------------------------------ 方法 ------------------------------ */
  can(key) {
    const f = this.features.find((x) => x.key === key);
    return Boolean(f && f.enabled);
  },

  why(key) {
    return this.features.find((x) => x.key === key)?.reason || '';
  },

  async bootstrap() {
    const meta = await getMeta();
    const [features, health] = await Promise.all([
      getCapabilities().catch(() => []),
      getHealth().catch(() => null),
    ]);
    this.meta = meta;
    this.features = features;
    this.health = health;
    this.ready = true;
    document.title = `${meta.site_name} · ${meta.abbr} ${meta.version}`;
    document.documentElement.style.setProperty('--sk-accent', meta.accent || 'var(--sk-primary)');
    if (this.token) {
      await this.restore().catch(() => this.applySession(null));
    }
    return meta;
  },

  /** 用已存的令牌恢复会话（失败即降级为未登录，不阻塞启动）。 */
  async restore() {
    try {
      const profile = await apiMe();
      this.actor = profile.username;
      this.role = profile.role;
      this.session = {
        token: this.token,
        username: profile.username,
        display_name: profile.display_name,
        role: profile.role,
        role_label: profile.role_label,
        guest: profile.status === 'guest',
        permissions: [],
      };
      return profile;
    } catch (e) {
      this.applySession(null);
      throw e;
    }
  },

  async login(username, password) {
    this.applySession(await apiLogin({ username, password }));
  },

  async register(username, password, displayName) {
    this.applySession(await apiRegister({ username, password, displayName }));
  },

  async enterAsGuest() {
    this.applySession(await apiGuest());
  },

  async logout() {
    try {
      await apiLogout();
    } catch {
      /* 退出失败也要清本地，否则会卡在"退不出去" */
    }
    this.applySession(null);
  },

  /** 把会话写进 store 与 localStorage。 */
  applySession(session) {
    if (!session || !session.token) {
      this.session = null;
      this.token = '';
      localStorage.removeItem(TOKEN_KEY);
      this.actor = 'guest';
      this.role = 'viewer';
      localStorage.setItem('skh.actor', 'guest');
      localStorage.setItem('skh.role', 'viewer');
      return;
    }
    this.session = session;
    this.token = session.token;
    this.actor = session.username;
    this.role = session.role;
    localStorage.setItem(TOKEN_KEY, session.token);
    localStorage.setItem('skh.actor', session.username);
    localStorage.setItem('skh.role', session.role);
  },

  async refreshHealth() {
    try {
      this.health = await getHealth();
    } catch {
      this.health = null;
    }
  },

  toggleSider() {
    this.siderCollapsed = !this.siderCollapsed;
    localStorage.setItem('skh.sider', this.siderCollapsed ? '1' : '0');
  },
});

export function useAppStore() {
  return app;
}

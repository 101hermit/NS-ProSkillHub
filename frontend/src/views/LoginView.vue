<template>
  <div class="login-page">
    <!-- 左：品牌与价值主张 -->
    <aside class="brand-side">
      <div class="brand">
        <span class="logo">SK</span>
        <div>
          <b>{{ app.meta?.product_name || 'SkillHub' }}</b>
          <div class="tiny dim">
            {{ app.abbr }} {{ app.version }} ·
            {{ app.meta?.tagline }}
          </div>
        </div>
      </div>

      <ul class="points">
        <li><b>同一份 Skill 库</b>：375 个真实 Skill，三个版本共用</li>
        <li><b>三种进入方式</b>：注册、登录、以访客浏览（只读）</li>
        <li><b>不联网也能用</b>：库、检索、评估全部本地完成</li>
      </ul>

      <p class="tiny dim mb-0">
        内网工具：账号只存在本机数据库里，密码用 PBKDF2 加盐哈希，不存明文。
      </p>
    </aside>

    <!-- 右：表单 -->
    <section class="form-side card">
      <div class="tabs">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ active: mode === t.key }"
          @click="switchMode(t.key)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 登录 -->
      <form v-if="mode === 'login'" class="col" @submit.prevent="doLogin">
        <h2 class="mt-0">登录</h2>
        <div class="field">
          <label>用户名</label>
          <input v-model="form.username" class="input" autocomplete="username" placeholder="例如 zhangsan" />
        </div>
        <div class="field">
          <label>密码</label>
          <input
            v-model="form.password"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="至少 8 位"
          />
        </div>
        <button class="btn primary lg block" type="submit" :disabled="busy">
          {{ busy ? '登录中…' : '登录' }}
        </button>
        <p class="tiny dim mb-0">
          还没有账号？<button type="button" class="link" @click="switchMode('register')">注册一个</button> ·
          或者<button type="button" class="link" @click="doGuest">以访客身份浏览</button>
        </p>
      </form>

      <!-- 注册 -->
      <form v-if="mode === 'register'" class="col" @submit.prevent="doRegister">
        <h2 class="mt-0">注册</h2>
        <div class="field">
          <label>用户名 <span class="req">*</span></label>
          <input v-model="form.username" class="input" autocomplete="username" placeholder="3-32 位小写字母/数字/._-" />
          <span class="hint">会用作用户标识，不能改。</span>
        </div>
        <div class="field">
          <label>显示名</label>
          <input v-model="form.displayName" class="input" placeholder="例如 张三 / 云原生部" />
        </div>
        <div class="field">
          <label>密码 <span class="req">*</span></label>
          <input v-model="form.password" class="input" type="password" autocomplete="new-password" placeholder="至少 8 位" />
        </div>
        <div class="field">
          <label>再输一次密码 <span class="req">*</span></label>
          <input v-model="form.password2" class="input" type="password" autocomplete="new-password" />
        </div>
        <button class="btn primary lg block" type="submit" :disabled="busy">
          {{ busy ? '注册中…' : '注册并进入' }}
        </button>
        <p class="tiny dim mb-0">
          第一位注册的用户自动成为<b>管理员</b>，之后注册的都是<b>编辑者</b>。
        </p>
      </form>

      <!-- 访客 -->
      <div v-if="mode === 'guest'" class="col">
        <h2 class="mt-0">以访客身份浏览</h2>
        <p class="small muted">
          访客是<b>只读</b>身份：可以浏览 Skill 库、看详情、下载、做在线试算；
          不能编辑、归档、贡献或改库结构。
        </p>
        <ul class="small muted">
          <li>不需要账号，点一下就进</li>
          <li>会拿到一个只读令牌，界面与登录后完全一致</li>
          <li>想写入时随时可以注册一个账号</li>
        </ul>
        <button class="btn primary lg block" type="button" :disabled="busy" @click="doGuest">
          {{ busy ? '进入中…' : '以访客身份进入' }}
        </button>
        <button type="button" class="btn block" @click="switchMode('login')">返回登录</button>
      </div>

      <p v-if="error" class="err small mt-3 mb-0">{{ error }}</p>
      <p v-if="notice" class="ok small mt-3 mb-0">{{ notice }}</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();
const route = useRoute();
const router = useRouter();

const tabs = [
  { key: 'login', label: '登录' },
  { key: 'register', label: '注册' },
  { key: 'guest', label: '访客' },
];

const mode = ref('login');
const busy = ref(false);
const error = ref('');
const notice = ref('');
const form = reactive({ username: '', password: '', password2: '', displayName: '' });

function switchMode(k) {
  mode.value = k;
  error.value = '';
  notice.value = '';
}

function afterSuccess(session, message) {
  toast.ok(message + '：' + (session.display_name || session.username));
  const next = route.query.next;
  if (next) {
    router.push(String(next));
  } else {
    router.push('/');
  }
}

async function doLogin() {
  error.value = '';
  if (!form.username.trim() || !form.password) {
    error.value = '请填写用户名与密码。';
    return;
  }
  busy.value = true;
  try {
    await app.login(form.username.trim(), form.password);
    afterSuccess(app.session, '已登录');
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

async function doRegister() {
  error.value = '';
  if (!form.username.trim()) {
    error.value = '请填写用户名。';
    return;
  }
  if (form.password.length < 8) {
    error.value = '密码至少 8 位。';
    return;
  }
  if (form.password !== form.password2) {
    error.value = '两次输入的密码不一致。';
    return;
  }
  busy.value = true;
  try {
    await app.register(form.username.trim(), form.password, form.displayName.trim());
    afterSuccess(app.session, '已注册');
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

async function doGuest() {
  error.value = '';
  busy.value = true;
  try {
    await app.enterAsGuest();
    afterSuccess(app.session, '已进入（访客·只读）');
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  if (route.query.mode && tabs.some((t) => t.key === route.query.mode)) {
    mode.value = String(route.query.mode);
  }
  if (route.query.notice) notice.value = String(route.query.notice);
  // 已经有会话就直接回首页
  if (app.signedIn) router.replace('/');
});
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 420px);
  gap: var(--sk-s8);
  max-width: 980px;
  margin: var(--sk-s10) auto;
  align-items: center;
}
.brand-side {
  display: flex;
  flex-direction: column;
  gap: var(--sk-s5);
}
.brand {
  display: flex;
  align-items: center;
  gap: var(--sk-s3);
}
.brand b {
  font-size: var(--sk-fs-lg);
}
.logo {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--sk-primary), var(--sk-accent));
  color: #fff;
  font-weight: 800;
}
.points {
  margin: 0;
  padding-left: 20px;
  color: var(--sk-text-2);
  font-size: var(--sk-fs-sm);
  line-height: 1.9;
}
.form-side {
  padding: var(--sk-s6);
}
.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: var(--sk-s4);
  border-bottom: 1px solid var(--sk-border);
  padding-bottom: var(--sk-s2);
}
.tab {
  flex: 1;
  height: 34px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: var(--sk-r-sm);
  font-family: inherit;
  font-size: var(--sk-fs);
  color: var(--sk-text-2);
  cursor: pointer;
}
.tab.active {
  background: var(--sk-primary-weak);
  color: var(--sk-primary-strong);
  font-weight: 650;
}
.req {
  color: var(--sk-danger);
}
.link {
  border: none;
  background: transparent;
  color: var(--sk-primary);
  cursor: pointer;
  font-size: inherit;
  padding: 0 2px;
  text-decoration: underline;
}
.err {
  color: var(--sk-danger);
}
.ok {
  color: var(--sk-ok);
}
@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
    margin: var(--sk-s6) auto;
  }
  .brand-side {
    display: none;
  }
}
</style>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>关于本版本</h1>
        <p class="muted small mb-0">
          {{ meta?.product_name }}（{{ meta?.abbr }}）{{ meta?.version }} · 本页说明"它做什么、不做什么、为什么"。
        </p>
      </div>
    </div>

    <div class="two">
      <div class="card">
        <div class="card-head"><h3>产品信息</h3></div>
        <div class="card-body">
          <dl class="kv">
            <dt>全称</dt><dd>{{ meta?.product_name }}</dd>
            <dt>缩写</dt><dd>{{ meta?.abbr }}</dd>
            <dt>版本</dt><dd>{{ meta?.version }}</dd>
            <dt>定位</dt><dd>{{ meta?.tagline }}</dd>
            <dt>端口</dt><dd class="mono">{{ meta?.port }}</dd>
            <dt>运行模式</dt><dd>{{ modeLabel }}</dd>
          </dl>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><h3>运行状态</h3><button class="btn sm" @click="refresh">刷新</button></div>
        <div class="card-body">
          <dl class="kv" v-if="health">
            <dt>就绪</dt><dd>{{ health.ready ? '是' : '否' }}</dd>
            <dt>数据库</dt><dd>{{ health.dialect_detail }}</dd>
            <dt>Skill 数</dt><dd>{{ health.skill_count }}</dd>
            <dt>Skill 库版本</dt><dd>{{ health.library_version }}</dd>
            <dt>schema 版本</dt><dd class="mono">{{ health.schema_version }}</dd>
            <dt>启动时间</dt><dd>{{ health.started_at }}</dd>
            <dt>外部依赖</dt><dd>{{ (health.extra?.external_dependencies || []).length === 0 ? '无（断网可用）' : health.extra.external_dependencies.join('、') }}</dd>
          </dl>
          <p v-else class="dim small mb-0">拿不到健康状态（后端可能刚重启）。</p>
        </div>
      </div>
    </div>

    <div class="section-title">
      <h2>能力矩阵</h2>
      <span class="dim small">未启用的能力会说明原因，并指向提供它的版本</span>
    </div>
    <div class="grid wide">
      <div v-for="f in features" :key="f.key" class="card cap" :class="{ off: !f.enabled }">
        <div class="row between">
          <b>{{ f.label }}</b>
          <span class="badge" :class="f.enabled ? 'ok' : 'plain'">{{ f.enabled ? '已启用' : '未启用' }}</span>
        </div>
        <div class="tiny dim mt-2 mono">{{ f.key }}</div>
        <p v-if="!f.enabled" class="tiny muted mt-2 mb-0">{{ f.reason }}</p>
      </div>
    </div>

    <div class="section-title"><h2>三版本递进</h2></div>
    <div class="card">
      <div class="card-body">
        <p class="small muted">
          本工程群有三个版本，界面与操作习惯一致、Skill 库是同一个。能力按下面的方式递进增补：
        </p>
        <div class="ladder">
          <div class="rung" :class="{ here: meta?.abbr === 'ESKH' }">
            <b>EasySkillHub（ESKH，30800）</b>
            <span class="small muted">资源站模式：浏览 / 编辑 / 贡献 / 分发 / 静态风险评估。不联网。</span>
          </div>
          <div class="rung" :class="{ here: meta?.abbr === 'SSKH' }">
            <b>StanSkillHub（SSKH，30801）</b>
            <span class="small muted">+ Skill-MS 库语义（索引 / 约束 / 事件 / 查询台）、在线实测、统计口径的质量评估。</span>
          </div>
          <div class="rung" :class="{ here: meta?.abbr === 'PSKH' }">
            <b>ProSkillHub（PSKH，30802）</b>
            <span class="small muted">+ 多 Agent × 多 MCP 连通矩阵、多维实验、知识图谱与统计检验。</span>
          </div>
        </div>
      </div>
    </div>

    <div class="section-title"><h2>身份（本版不做登录）</h2></div>
    <div class="card">
      <div class="card-body">
        <p class="small muted">
          本版用<b>轻量身份上下文</b>表达"谁在操作"：请求头 <code>X-SKH-Actor</code> 与
          <code>X-SKH-Role</code>。当前身份：<b>{{ app.actor }}</b>（{{ app.role }}）。
        </p>
        <div class="row">
          <input v-model="actor" class="input" style="max-width: 220px" placeholder="你的名字" />
          <select v-model="role" class="select" style="max-width: 160px">
            <option value="viewer">visitor / 只读</option>
            <option value="editor">editor / 可编辑</option>
            <option value="admin">admin / 管理员</option>
          </select>
          <button class="btn primary" @click="saveIdentity">应用</button>
        </div>
        <p class="tiny dim mt-2 mb-0">
          这不是鉴权体系：任何能访问页面的人都能切换身份。生产环境接入 SSO 属于后续路线。
        </p>
      </div>
    </div>

    <div class="section-title"><h2>术语</h2></div>
    <div class="card">
      <div class="card-body row wrap">
        <RouterLink class="btn" to="/docs/glossary">打开术语小词典 →</RouterLink>
        <RouterLink class="btn" to="/docs/intro">这个站点是什么 →</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useAppStore } from '@/store/app';
import { useToastStore } from '@/store/toast';

const app = useAppStore();
const toast = useToastStore();
const actor = ref(app.actor);
const role = ref(app.role);

const meta = computed(() => app.meta);
const health = computed(() => app.health);
const features = computed(() => app.features || []);

const MODE = {
  resourcestation: '资源站（不发起任何外部网络调用）',
  management: '管理平台（库语义 + 在线实测）',
  multifunction: '多功能平台（多维实验 + 知识级分析）',
};
const modeLabel = computed(() => MODE[meta.value?.mode] || meta.value?.mode || '—');

async function refresh() {
  await app.refreshHealth();
  toast.ok('已刷新');
}

function saveIdentity() {
  app.setIdentity(actor.value.trim() || 'guest', role.value);
  toast.ok(`身份已切换为 ${app.actor}（${app.role}）`);
}

onMounted(() => {
  if (!app.health) app.refreshHealth();
});
</script>

<style scoped>
.page-head {
  margin-bottom: var(--sk-s4);
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s4);
}
.kv {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 6px var(--sk-s3);
  margin: 0;
  font-size: var(--sk-fs-sm);
}
.kv dt {
  color: var(--sk-text-3);
}
.kv dd {
  margin: 0;
  word-break: break-word;
}
.cap.off {
  background: #fbfbfd;
  opacity: 0.92;
}
.ladder {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: var(--sk-s3);
}
.rung {
  border-left: 3px solid var(--sk-border-2);
  padding: 8px var(--sk-s3);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rung.here {
  border-left-color: var(--sk-accent);
  background: var(--sk-primary-weak);
  border-radius: 0 var(--sk-r-sm) var(--sk-r-sm) 0;
}
@media (max-width: 900px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

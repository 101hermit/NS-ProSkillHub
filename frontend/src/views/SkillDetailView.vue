<template>
  <div v-if="error" class="card empty-state">
    <div class="ico">⚠</div>
    <h3>{{ error }}</h3>
    <button class="btn mt-3" @click="$router.push('/skills')">返回 Skill 库</button>
  </div>

  <div v-else-if="!detail" class="dim">加载中…</div>

  <div v-else>
    <!-- 页头 -->
    <div class="head">
      <div class="grow">
        <div class="row wrap tiny dim">
          <RouterLink to="/skills">Skill 库</RouterLink>
          <span>/</span>
          <RouterLink :to="{ path: '/skills', query: { category: card.category_code } }">
            {{ card.category_label }}
          </RouterLink>
          <span>/</span>
          <span class="mono">{{ card.skill_id }}</span>
        </div>
        <h1 class="mt-2">{{ card.title !== '—' ? card.title : card.name }}</h1>
        <div class="row wrap">
          <span class="badge" :class="card.risk_level">{{ card.risk_label }}</span>
          <span class="badge plain">{{ card.category_label }}</span>
          <span v-if="card.scene_code !== 'general'" class="badge plain">{{ card.scene_label }}</span>
          <span class="badge plain">v{{ card.version }}</span>
          <span class="badge plain">{{ card.author_kind_label }} · {{ card.author }}</span>
          <span class="badge plain">{{ card.license }}</span>
        </div>
      </div>
      <div class="row">
        <a class="btn primary" :href="downloadHref">下载 ZIP</a>
        <button class="btn" :disabled="!app.canEdit" @click="openEdit">
          {{ app.canEdit ? '编辑' : '编辑（需编辑者身份）' }}
        </button>
        <button class="btn danger" :disabled="!app.canEdit" @click="onArchive">归档</button>
      </div>
    </div>

    <!-- 什么时候该用它：资源站最贵的失败是"装错了 Skill"。把适用范围提到正文之前，
         读者第一眼就能判断"这条是不是我要的"，而不是先读 40 KB 的 SKILL.md。 -->
    <div class="card when-card">
      <div class="card-body">
        <div class="row between wrap">
          <b>什么时候该用它</b>
          <span class="tiny dim">作者写的适用场景，不是功能清单</span>
        </div>
        <p class="small when-text">{{ card.description }}</p>
        <div v-if="card.tags.length" class="row wrap mt-2">
          <span class="tiny dim">关键词</span>
          <span v-for="t in card.tags" :key="t" class="badge plain tiny">{{ t }}</span>
        </div>
        <div class="row wrap mt-2 tiny dim">
          <span>同类还有：</span>
          <RouterLink :to="{ path: '/skills', query: { category: card.category_code } }">
            {{ card.category_label }}
          </RouterLink>
        </div>
      </div>
    </div>
    <div class="detail-grid">
      <!-- 左：正文 -->
      <section class="card">
        <div class="card-head">
          <h3>SKILL.md</h3>
          <div class="row">
            <span class="dim tiny">{{ humanBytes(card.content_bytes) }}</span>
          </div>
        </div>
        <div class="card-body md-body">
          <SkMarkdown :source="detail.content" />
        </div>
      </section>

      <!-- 右：信息卡 -->
      <aside class="col">
        <!-- 风险 -->
        <div class="card">
          <div class="card-head">
            <h3>风险评估</h3>
            <RouterLink class="small" to="/risk">口径</RouterLink>
          </div>
          <div class="card-body">
            <div class="gauge-wrap">
              <SkRiskGauge :score="audit.risk_score" :level="audit.risk_level" :size="150" />
              <div class="gauge-note small muted">{{ audit.conclusion }}</div>
            </div>
            <div class="dim-grid mt-3">
              <div v-for="d in audit.dimensions.filter((x) => x.score > 0)" :key="d.key" class="dim-row">
                <span class="small">{{ d.name }}</span>
                <span class="bar">
                  <i :style="{ width: Math.min(100, d.score) + '%' }" />
                </span>
                <span class="tiny dim">{{ d.score }}</span>
              </div>
            </div>
            <div class="row wrap mt-3">
              <span v-for="(v, k) in audit.severity_counts" :key="k" class="badge" :class="sevTone(k)">
                {{ sevLabel(k) }} {{ v }}
              </span>
              <span v-if="!Object.keys(audit.severity_counts).length" class="badge safe">无信号</span>
            </div>
            <button class="btn sm block mt-3" @click="showSignals = !showSignals">
              {{ showSignals ? '收起' : `查看 ${audit.signals.length} 条信号明细` }}
            </button>
            <div v-if="showSignals" class="signals mt-2">
              <div v-for="(s, i) in audit.signals" :key="i" class="signal">
                <div class="row between">
                  <b class="small">{{ s.code }} · {{ s.indicator }}</b>
                  <span class="badge" :class="sevTone(s.severity)">{{ s.severity_label }}</span>
                </div>
                <div class="tiny dim">
                  {{ s.dimension_name }} · 权重 {{ s.weight }} ·
                  <span class="mono">{{ s.file_path }}:{{ s.line_no }}</span>
                </div>
                <div class="tiny mt-2">{{ s.remediation }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 安装 -->
        <div class="card">
          <div class="card-head">
            <h3>安装与分发</h3>
            <span class="dim tiny">{{ detail.install.length }} 种方式</span>
          </div>
          <div class="card-body col">
            <!-- 先讲清楚"装到哪"，再给命令。顺序反了，用户就得先执行一条命令再猜它做了什么。 -->
            <div class="where-box">
              <div class="row">
                <SkIcon name="library" :size="14" />
                <b class="small">装到哪个目录？</b>
              </div>
              <p class="tiny dim mb-0">
                一个 Skill 的落地形式是一个目录：<code class="mono">&lt;名字&gt;/SKILL.md</code>（可带脚本与参考资料）。
                把它放进你所使用工具的 skills 目录即可 —— Claude Code 个人级
                <code class="mono">~/.claude/skills</code>、项目级 <code class="mono">.claude/skills</code>；
                Cursor / Qoder 等工具按各自说明选择 skills 目录。
                下面的命令默认写的是 <code class="mono">~/.claude/skills</code>，用别的工具时把最后那段路径换掉。
              </p>
            </div>

            <div v-for="m in detail.install" :key="m.type" class="install-row">
              <div class="row between">
                <b class="small">{{ m.title }}</b>
                <span class="badge plain tiny">{{ installKind(m.type) }}</span>
              </div>
              <p class="tiny dim mt-2">{{ m.description }}</p>
              <SkCopyField v-if="m.type !== 'source'" :value="m.value" @click="onCopyInstall(m)" />
              <div v-else class="row">
                <a class="small" :href="m.value" target="_blank" rel="noopener noreferrer">打开源码直链 ↗</a>
              </div>
            </div>

            <p class="tiny dim mb-0" v-if="!detail.install.some((m) => m.type === 'source')">
              这个 Skill 没有登记源码直链。支持 GitHub / Gitee / GitLab 三种格式。
            </p>
          </div>
        </div>
        <!-- 版本 -->
        <div class="card">
          <div class="card-head"><h3>版本历史</h3><span class="dim tiny">{{ detail.versions.length }} 个</span></div>
          <div class="card-body">
            <div v-for="v in detail.versions" :key="v.version" class="version-row">
              <div class="row between">
                <b class="small">v{{ v.version }}</b>
                <span class="tiny dim">{{ humanBytes(v.size_bytes) }}</span>
              </div>
              <div class="tiny dim">{{ v.created_at }} · {{ v.origin }}</div>
              <div class="tiny clamp-2">{{ v.notes || '（无说明）' }}</div>
            </div>
          </div>
        </div>

        <!-- 附件 -->
        <div class="card">
          <div class="card-head"><h3>附件清单</h3><span class="dim tiny">{{ detail.files.length }} 个文件</span></div>
          <div class="card-body">
            <SkFileTree :files="detail.files" />
          </div>
        </div>
      </aside>
    </div>

    <!-- 相关 -->
    <div class="section-title" v-if="detail.related.length">
      <h2>相关技能</h2>
      <span class="dim small">同分类里下载最多的几个</span>
    </div>
    <div class="grid" v-if="detail.related.length">
      <SkSkillCard v-for="s in detail.related" :key="s.skill_id" :card="s" />
    </div>

    <!-- 编辑弹窗 -->
    <SkModal :open="editing" title="编辑 Skill" width="760px" @close="editing = false">
      <div class="col">
        <div class="two">
          <div class="field">
            <label>名称（slug）</label>
            <input v-model="form.name" class="input" />
          </div>
          <div class="field">
            <label>标题</label>
            <input v-model="form.title" class="input" />
          </div>
        </div>
        <div class="field">
          <label>简介 <span class="hint">说明"什么时候该用它"</span></label>
          <textarea v-model="form.description" class="textarea" />
        </div>
        <div class="two">
          <div class="field">
            <label>分类</label>
            <select v-model="form.category_code" class="select">
              <option v-for="c in categories" :key="c.code" :value="c.code">{{ c.label }}</option>
            </select>
          </div>
          <div class="field">
            <label>版本号</label>
            <input v-model="form.version" class="input" placeholder="1.0.1" />
          </div>
        </div>
        <div class="field">
          <label>标签（逗号分隔）</label>
          <input v-model="form.tagsText" class="input" />
        </div>
        <div class="field">
          <label>源码直链 <span class="hint">GitHub / Gitee / GitLab</span></label>
          <input v-model="form.source_url" class="input" placeholder="https://github.com/owner/repo" />
        </div>
        <div class="field">
          <label>许可</label>
          <input v-model="form.license" class="input" />
        </div>
        <div class="field">
          <label>本次修改说明 <span class="hint">会写进版本历史</span></label>
          <input v-model="form.version_note" class="input" placeholder="例如：补充表单填写章节" />
        </div>
        <div class="field">
          <label>正文（SKILL.md）</label>
          <textarea v-model="form.content" class="textarea code" />
        </div>
        <p class="tiny dim mb-0">
          保存后会：更新主表 → 若版本号或正文变化则追加一条版本快照 → 若正文变化则重新做风险扫描。
        </p>
      </div>
      <template #footer>
        <button class="btn" @click="editing = false">取消</button>
        <button class="btn primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </SkModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SkMarkdown from '@/components/SkMarkdown.vue';
import SkRiskGauge from '@/components/SkRiskGauge.vue';
import SkCopyField from '@/components/SkCopyField.vue';
import SkIcon from '@/components/SkIcon.vue';
import SkFileTree from '@/components/SkFileTree.vue';
import SkSkillCard from '@/components/SkSkillCard.vue';
import SkModal from '@/components/SkModal.vue';
import {
  archiveSkill,
  downloadUrl,
  editSkill,
  getCategories,
  getSkill,
  recordInstall,
  restoreSkill,
} from '@/api';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';
import { useAppStore } from '@/store/app';
import { humanBytes } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const toast = useToastStore();
const app = useAppStore();

const detail = ref(null);
const error = ref('');
const showSignals = ref(false);
const editing = ref(false);
const saving = ref(false);
const categories = ref([]);

const form = reactive({
  name: '',
  title: '',
  description: '',
  category_code: '',
  version: '',
  tagsText: '',
  source_url: '',
  license: '',
  version_note: '',
  content: '',
});

const card = computed(() => detail.value?.card || {});
const audit = computed(
  () =>
    detail.value?.audit || {
      risk_score: 0,
      risk_level: 'unrated',
      conclusion: '',
      dimensions: [],
      severity_counts: {},
      signals: [],
    },
);
const downloadHref = computed(() => (card.value.skill_id ? downloadUrl(card.value.skill_id) : '#'));

const SEV_LABEL = { CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低' };
const SEV_TONE = { CRITICAL: 'danger', HIGH: 'danger', MEDIUM: 'warn', LOW: 'plain' };
function sevLabel(s) {
  return SEV_LABEL[s] || s;
}
function sevTone(s) {
  return SEV_TONE[s] || 'plain';
}

async function load() {
  error.value = '';
  detail.value = null;
  const id = route.params.id;
  try {
    detail.value = await getSkill(id);
  } catch (e) {
    error.value = e.message;
  }
}

function openEdit() {
  if (!app.canEdit) {
    toast.warn('当前是访客身份，只能查看。怎么办：在右上角把身份切换成「编辑者」。');
    return;
  }
  const c = card.value;
  Object.assign(form, {
    name: c.name,
    title: c.title === '—' ? '' : c.title,
    description: c.description === '—' ? '' : c.description,
    category_code: c.category_code,
    version: c.version,
    tagsText: (c.tags || []).join(','),
    source_url: c.source_url || '',
    license: c.license === '—' ? '' : c.license,
    version_note: '',
    content: detail.value.content,
  });
  editing.value = true;
}

async function save() {
  saving.value = true;
  try {
    await editSkill(card.value.skill_id, {
      name: form.name || null,
      title: form.title || null,
      description: form.description || null,
      category_code: form.category_code || null,
      version: form.version || null,
      tags: form.tagsText ? form.tagsText.split(',').map((s) => s.trim()).filter(Boolean) : null,
      source_url: form.source_url,
      license: form.license || null,
      content: form.content || null,
      version_note: form.version_note || null,
    });
    toast.ok('已保存');
    editing.value = false;
    await load();
  } catch (e) {
    notifyError(e);
  } finally {
    saving.value = false;
  }
}

async function onArchive() {
  if (!app.canEdit) {
    toast.warn('当前是访客身份，只能查看。');
    return;
  }
  try {
    if (card.value.status === 'archived') {
      await restoreSkill(card.value.skill_id);
      toast.ok('已恢复为「已发布」');
    } else {
      await archiveSkill(card.value.skill_id, false);
      toast.ok('已归档（软删）。可在「已归档」里找到并恢复。');
    }
    await load();
  } catch (e) {
    notifyError(e);
  }
}

/**
 * 安装方式的"人话类型名"。
 *
 * 直接把后端返回的 type（download / command / source）印在页面上，
 * 对用户是没有信息量的英文；这里翻成一句人话，同时保留 type 作为键。
 */
const INSTALL_KIND = { download: '离线包', command: '一行命令', source: '上游原文' };
function installKind(type) {
  return INSTALL_KIND[type] || type;
}

function onCopyInstall(m) {
  if (m.type === 'command') recordInstall(card.value.skill_id).catch(() => {});
}

watch(() => route.params.id, load);
onMounted(async () => {
  await load();
  categories.value = await getCategories().catch(() => []);
});
</script>

<style scoped>
/* "什么时候该用它"：浅底 + 左侧强调条，视觉上比正文轻，但位置比正文前 */
.when-card {
  margin-bottom: var(--sk-s4);
  border-left: 3px solid var(--sk-accent);
  background: var(--sk-surface);
}
.when-text {
  margin: var(--sk-s2) 0 0;
  color: var(--sk-text);
  line-height: 1.7;
}
.where-box {
  padding: var(--sk-s3);
  border: 1px dashed var(--sk-border-2);
  border-radius: var(--sk-r-sm);
  background: var(--sk-bg);
}
.install-row {
  padding-top: var(--sk-s2);
  border-top: 1px dashed var(--sk-border);
}
.head {
  display: flex;
  gap: var(--sk-s4);
  align-items: flex-start;
  margin-bottom: var(--sk-s4);
  flex-wrap: wrap;
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: var(--sk-s4);
  align-items: start;
}
.md-body {
  max-height: none;
  overflow: visible;
}
.gauge-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sk-s2);
  text-align: center;
}
.gauge-note {
  max-width: 260px;
}
.dim-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dim-row {
  display: grid;
  grid-template-columns: 104px 1fr 28px;
  align-items: center;
  gap: 6px;
}
.bar {
  height: 6px;
  background: var(--sk-muted);
  border-radius: 999px;
  overflow: hidden;
}
.bar i {
  display: block;
  height: 100%;
  background: var(--sk-warn);
}
.signals {
  max-height: 320px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.signal {
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: 8px;
  background: #fcfcfe;
}
.version-row {
  padding: 8px 0;
  border-bottom: 1px dashed var(--sk-border);
}
.version-row:last-child {
  border-bottom: none;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.empty-state {
  padding: var(--sk-s10);
  text-align: center;
}
.empty-state .ico {
  font-size: 40px;
  color: var(--sk-warn);
}
@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

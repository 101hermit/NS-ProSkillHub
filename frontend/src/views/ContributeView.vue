<template>
  <div>
    <div class="page-head">
      <div>
        <h1>在线贡献</h1>
        <p class="muted small mb-0">
          四步：上传 → 签协议 → 提交 → 评估并发布。每一步都会告诉你"下一步做什么"。
        </p>
      </div>
      <RouterLink class="btn" to="/docs/contribute">看贡献指南 →</RouterLink>
    </div>

    <!-- 步骤条 -->
    <div class="steps card card-pad">
      <div v-for="(s, i) in steps" :key="s.key" class="step" :class="{ done: step > i, active: step === i }">
        <div class="circle">{{ step > i ? '✔' : i + 1 }}</div>
        <div class="txt">
          <b>{{ s.label }}</b>
          <div class="tiny dim">{{ s.hint }}</div>
        </div>
      </div>
    </div>

    <!-- 步骤 1：上传 -->
    <div v-if="step === 0" class="card">
      <div class="card-head"><h3>第 1 步 · 上传 SKILL.md</h3></div>
      <div class="card-body col">
        <div class="two">
          <div class="field">
            <label>创作者身份</label>
            <select v-model="form.kind" class="select">
              <option value="department">部门 / 企业</option>
              <option value="personal">个人</option>
            </select>
            <span class="hint">只影响归类与署名，不影响审核强度。</span>
          </div>
          <div class="field">
            <label>贡献者名称 <span class="req">*</span></label>
            <input v-model="form.contributor_name" class="input" placeholder="你的名字或部门名" />
            <span class="hint">会作为这个 Skill 的作者。</span>
          </div>
        </div>
        <div class="two">
          <div class="field">
            <label>Skill 名称 <span class="req">*</span></label>
            <input v-model="form.name" class="input" placeholder="小写字母 / 数字 / 连字符" />
            <span class="hint">会成为目录名，例如 my-pdf-tools。</span>
          </div>
          <div class="field">
            <label>版本号</label>
            <input v-model="form.version" class="input" placeholder="1.0.0" />
          </div>
        </div>
        <div class="field">
          <label>标题</label>
          <input v-model="form.title" class="input" placeholder="一句话说明这个 Skill 是什么" />
        </div>
        <div class="field">
          <label>简介</label>
          <textarea v-model="form.description" class="textarea" placeholder="什么时候该用我？" />
        </div>
        <div class="field">
          <label>源码直链 <span class="hint">可选，支持 GitHub / Gitee / GitLab</span></label>
          <input v-model="form.source_url" class="input" placeholder="https://github.com/owner/repo/tree/main/skills/xxx" />
          <span class="hint">填了之后，别人能顺着地址找到原文，分发方式也会多一种「源码直链」。</span>
        </div>

        <div class="two">
          <div class="field">
            <label>上传 SKILL.md 文件</label>
            <input class="input" type="file" accept=".md,.markdown,.txt" @change="onFile" />
            <span class="hint">也可以直接在下面的编辑框里粘贴正文。</span>
          </div>
          <div class="field">
            <label>附件包 <span class="hint">可选，zip，≤ 32 MB</span></label>
            <input class="input" type="file" accept=".zip" @change="onBundle" />
          </div>
        </div>

        <div class="field">
          <label>SKILL.md 正文 <span class="req">*</span></label>
          <textarea
            v-model="form.skill_md"
            class="textarea code"
            placeholder="---&#10;name: my-skill&#10;description: 什么时候该用我&#10;---&#10;&#10;# 标题&#10;..."
          />
          <span class="hint">{{ form.skill_md.length }} 字符</span>
        </div>

        <div class="row end">
          <button class="btn primary lg" :disabled="busy" @click="submitStep1">
            {{ busy ? '上传中…' : '上传并预评估' }}
          </button>
        </div>
        <p v-if="formError" class="err small mb-0">{{ formError }}</p>
      </div>
    </div>

    <!-- 步骤 2/3/4 -->
    <div v-else class="card">
      <div class="card-head">
        <h3>{{ current.title }}</h3>
        <span class="badge" :class="statusTone(record.status)">{{ record.statusLabel }}</span>
      </div>
      <div class="card-body col">
        <!-- 预评估摘要 -->
        <div class="row wrap">
          <span class="badge" :class="record.risk_level">风险 {{ record.risk_level_label }} · {{ record.risk_score }}</span>
          <span class="badge plain">版本 v{{ record.version }}</span>
          <span class="badge plain">{{ record.kind_label }}</span>
          <span class="badge plain">CLA：{{ record.cla_signed ? '已签（' + record.cla_signature + '）' : '未签' }}</span>
          <span v-if="record.skill_id" class="badge info mono">{{ record.skill_id }}</span>
        </div>
        <p class="small muted mb-0">{{ detailData?.next_step }}</p>

        <div v-if="detailData?.audit" class="audit-box">
          <div class="row between">
            <b class="small">预评估结论</b>
            <span class="tiny dim">{{ detailData.audit.conclusion }}</span>
          </div>
          <div class="row wrap mt-2">
            <span
              v-for="d in detailData.audit.dimensions.filter((x) => x.score > 0)"
              :key="d.key"
              class="badge warn"
            >
              {{ d.name }} {{ d.score }}
            </span>
            <span v-if="!detailData.audit.dimensions.some((x) => x.score > 0)" class="badge safe">未命中任何风险指标</span>
          </div>
        </div>

        <div class="checklist">
          <b class="small">发布前自查</b>
          <ul class="small muted">
            <li v-for="c in detailData?.checklist || []" :key="c">{{ c }}</li>
          </ul>
        </div>

        <!-- 第 2 步 -->
        <div v-if="step === 1" class="col">
          <div class="field">
            <label>贡献者协议签名 <span class="req">*</span></label>
            <input v-model="signature" class="input" placeholder="填你的名字即可" />
            <span class="hint">这是"我确认这份内容我可以公开"的声明，记录签名人与时间，不做法律背书。</span>
          </div>
          <div class="row end">
            <button class="btn primary" :disabled="busy || !signature.trim()" @click="doCla">
              {{ busy ? '签署中…' : '签署协议' }}
            </button>
          </div>
        </div>

        <!-- 第 3 步 -->
        <div v-if="step === 2" class="row end">
          <button class="btn primary" :disabled="busy" @click="doSubmit">
            {{ busy ? '提交中…' : '提交审核' }}
          </button>
        </div>

        <!-- 第 4 步 -->
        <div v-if="step === 3" class="col">
          <p class="small muted mb-0">
            发布会在一个事务里完成：重新扫描 → 计算 Skill 身份 → 新建或追加版本 → 写评估 → 登记贡献者 → 回填记录。
            版本号重复会被拒绝（不会覆盖别人的历史版本）。
          </p>
          <div class="row end">
            <button class="btn accent lg" :disabled="busy" @click="doPublish">
              {{ busy ? '发布中…' : '评估并发布' }}
            </button>
          </div>
        </div>

        <div class="row between mt-4">
          <button class="btn ghost sm" @click="startOver">重新上传一个</button>
          <RouterLink v-if="record.skill_id" class="small" :to="`/skills/${encodeURIComponent(record.skill_id)}`">
            查看已发布的 Skill →
          </RouterLink>
        </div>
      </div>
    </div>

    <!-- 最近的贡献记录 -->
    <div class="section-title">
      <h2>最近的贡献记录</h2>
      <span class="dim small">共 {{ historyTotal }} 条</span>
    </div>
    <SkTable :columns="historyColumns" :rows="history" :loading="historyLoading" empty-text="还没有贡献记录">
      <template #name="{ row }">
        <b>{{ row.name }}</b>
        <div class="tiny dim">v{{ row.version }} · {{ row.kind_label }}</div>
      </template>
      <template #status_label="{ row }">
        <span class="badge" :class="statusTone(row.status)">{{ row.status_label }}</span>
      </template>
      <template #risk_level="{ row }">
        <span class="badge" :class="row.risk_level">{{ row.risk_level_label }} {{ row.risk_score }}</span>
      </template>
    </SkTable>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import SkTable from '@/components/SkTable.vue';
import {
  createContribution,
  getContribution,
  listContributions,
  publishContribution,
  signCla,
  submitContribution,
} from '@/api';
import { notifyError } from '@/api/client';
import { useToastStore } from '@/store/toast';

const toast = useToastStore();

const steps = [
  { key: 'upload', label: '上传', hint: '提交 SKILL.md（可带附件包）' },
  { key: 'cla', label: '签协议', hint: '记录签名人与时间' },
  { key: 'submit', label: '提交', hint: '进入待发布队列' },
  { key: 'publish', label: '评估发布', hint: '扫描并入库' },
];
const current = computed(() => ({ title: `第 ${step.value + 1} 步 · ${steps[step.value].label}`, ...steps[step.value] }));

const step = ref(0);
const busy = ref(false);
const formError = ref('');
const signature = ref('');
const mdFile = ref(null);
const bundleFile = ref(null);
const record = ref(null);
const detailData = ref(null);

const history = ref([]);
const historyTotal = ref(0);
const historyLoading = ref(false);
const historyColumns = [
  { key: 'name', label: 'Skill', width: '32%' },
  { key: 'status_label', label: '状态', width: '110px' },
  { key: 'risk_level', label: '风险', width: '110px' },
  { key: 'created_at', label: '提交时间', width: '150px' },
];

const form = reactive({
  kind: 'department',
  contributor_name: '',
  name: '',
  title: '',
  version: '1.0.0',
  description: '',
  source_url: '',
  skill_md: '',
});

function onFile(e) {
  const f = e.target.files?.[0];
  mdFile.value = f || null;
  if (!f) return;
  const reader = new FileReader();
  reader.onload = () => {
    form.skill_md = String(reader.result || '');
    // 从正文里猜一下名称，省一次手填
    const m = /^---[\s\S]*?^name:\s*(.+)$/m.exec(form.skill_md);
    if (m && !form.name) form.name = m[1].trim().replace(/^["']|["']$/g, '');
  };
  reader.readAsText(f, 'utf-8');
}

function onBundle(e) {
  bundleFile.value = e.target.files?.[0] || null;
}

async function submitStep1() {
  formError.value = '';
  if (!form.contributor_name.trim()) return (formError.value = '请填写贡献者名称。');
  if (!form.name.trim()) return (formError.value = '请填写 Skill 名称。');
  if (!form.skill_md.trim()) return (formError.value = '请上传 SKILL.md 或直接粘贴正文。');

  busy.value = true;
  try {
    const fd = new FormData();
    fd.set('kind', form.kind);
    fd.set('contributor_name', form.contributor_name.trim());
    fd.set('name', form.name.trim());
    fd.set('title', form.title || '');
    fd.set('version', form.version || '1.0.0');
    fd.set('description', form.description || '');
    fd.set('source_url', form.source_url || '');
    fd.set('skill_md', form.skill_md);
    if (bundleFile.value) fd.set('bundle', bundleFile.value);

    const created = await createContribution(fd);
    record.value = created.item;
    detailData.value = created;
    signature.value = form.contributor_name.trim();
    step.value = 1;
    toast.ok('上传成功，已完成预评估');
    await loadHistory();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function doCla() {
  busy.value = true;
  try {
    record.value = await signCla(record.value.id, signature.value.trim());
    detailData.value = await getContribution(record.value.id);
    step.value = 2;
    toast.ok('已签署贡献者协议');
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function doSubmit() {
  busy.value = true;
  try {
    record.value = await submitContribution(record.value.id);
    detailData.value = await getContribution(record.value.id);
    step.value = 3;
    toast.ok('已提交，等待发布');
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function doPublish() {
  busy.value = true;
  try {
    const published = await publishContribution(record.value.id);
    record.value = published.item;
    detailData.value = published;
    toast.ok(`已发布：${published.item.skill_id}@${published.item.version}`);
    await loadHistory();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

function startOver() {
  step.value = 0;
  record.value = null;
  detailData.value = null;
  mdFile.value = null;
  bundleFile.value = null;
  Object.assign(form, {
    contributor_name: form.contributor_name,
    kind: form.kind,
    name: '',
    title: '',
    version: '1.0.0',
    description: '',
    source_url: '',
    skill_md: '',
  });
}

async function loadHistory() {
  historyLoading.value = true;
  try {
    const data = await listContributions({ page: 1, size: 8 });
    history.value = data.items;
    historyTotal.value = data.total;
  } catch {
    /* 历史记录失败不影响主流程 */
  } finally {
    historyLoading.value = false;
  }
}

function statusTone(status) {
  return { published: 'ok', submitted: 'info', draft: 'plain', rejected: 'danger', archived: 'plain' }[status] || 'plain';
}

onMounted(loadHistory);
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
.steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--sk-s3);
  margin-bottom: var(--sk-s4);
}
.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: var(--sk-r-sm);
  border: 1px dashed var(--sk-border);
}
.step.active {
  border: 1px solid var(--sk-primary);
  background: var(--sk-primary-weak);
}
.step.done {
  border-style: solid;
  border-color: var(--sk-ok);
}
.circle {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--sk-muted);
  font-size: 12px;
  font-weight: 700;
  color: var(--sk-text-2);
}
.step.active .circle {
  background: var(--sk-primary);
  color: #fff;
}
.step.done .circle {
  background: var(--sk-ok);
  color: #fff;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.req {
  color: var(--sk-danger);
}
.err {
  color: var(--sk-danger);
}
.audit-box {
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: var(--sk-s3);
  background: #fcfcfe;
}
.checklist ul {
  margin: 4px 0 0;
  padding-left: 18px;
}
@media (max-width: 900px) {
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

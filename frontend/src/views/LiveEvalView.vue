<template>
  <div>
    <div class="page-head">
      <div>
        <h1>在线实测</h1>
        <p class="muted small mb-0">
          选一个 Skill、一个智能体、一个 MCP 服务，问它一个问题 —— 真的把它挂上去跑一次。
          返回里会同时给出<b>推理过程</b>（它打算怎么做）与<b>输出</b>（最后说了什么），并按指标打分。
        </p>
      </div>
      <RouterLink class="btn" to="/access">接入管理 →</RouterLink>
    </div>

    <div class="eval-layout">
      <!-- 左：表单 -->
      <div class="card">
        <div class="card-head"><h3>配置</h3></div>
        <div class="card-body col">
          <div class="field">
            <label>Skill <span class="req">*</span></label>
            <input v-model="skillKw" class="input" placeholder="输入关键词搜索 Skill（如 pdf / 教学 / 合同）" />
            <select v-model="form.skill_id" class="select">
              <option value="">请选择 Skill</option>
              <option v-for="s in skillOptions" :key="s.skill_id" :value="s.skill_id">
                {{ s.name }}（{{ s.category_label }} · {{ s.risk_label }}）
              </option>
            </select>
            <span class="hint">搜索后从下拉里选一个；空着说明还没搜。</span>
          </div>
          <div class="two">
            <div class="field">
              <label>智能体</label>
              <select v-model="form.agent_code" class="select">
                <option v-for="a in agents" :key="a.code" :value="a.code">
                  {{ a.name }}{{ a.status === 'ok' ? '（已连通）' : '' }}
                </option>
              </select>
            </div>
            <div class="field">
              <label>MCP 服务</label>
              <select v-model="form.mcp_code" class="select">
                <option v-for="m in mcps" :key="m.code" :value="m.code">{{ m.name }}</option>
              </select>
            </div>
          </div>
          <label class="checkbox"><input v-model="form.use_mcp" type="checkbox" /> 让它先调一次 MCP 工具拿参考资料</label>
          <div class="two">
            <div class="field">
              <label>发散程度（temperature） <span class="hint">?</span></label>
              <input v-model.number="form.temperature" class="input" type="number" step="0.1" min="0" max="2" />
              <span class="hint">越大越发散；0 表示尽量稳定</span>
            </div>
            <div class="field">
              <label>最大输出（max_tokens）</label>
              <input v-model.number="form.max_tokens" class="input" type="number" min="64" max="8192" />
            </div>
          </div>
          <div class="field">
            <label>场景</label>
            <select v-model="form.scene_code" class="select">
              <option value="general">通用</option>
              <option value="edu">教育</option>
              <option value="fin">金融</option>
              <option value="gov">政务与法务</option>
              <option value="med">医疗健康</option>
              <option value="news">新闻传媒</option>
              <option value="tra">交通物流</option>
            </select>
          </div>
          <div class="field">
            <label>问题 <span class="req">*</span></label>
            <textarea v-model="form.input_text" class="textarea" placeholder="你想让它做什么？" />
          </div>
          <div class="field">
            <label>期望输出（可选，用于打分）</label>
            <textarea v-model="form.expected_text" class="textarea" placeholder="你心里认为正确答案长什么样" />
            <span class="hint">填了才能算"正确性 / 覆盖 / 完成"；不填则这些指标记满分。</span>
          </div>
          <div class="row end">
            <button class="btn primary lg" :disabled="busy || !form.skill_id || !form.input_text.trim()" @click="run">
              {{ busy ? '实测中…' : '开始实测' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右：结果 -->
      <div class="col">
        <div class="card" v-if="result">
          <div class="card-head">
            <h3>本次结果</h3>
            <span class="badge" :class="result.offline ? 'warn' : 'ok'">
              {{ result.offline ? '离线兜底' : '真实调用' }}
            </span>
          </div>
          <div class="card-body col">
            <div class="score-row">
              <div class="total">{{ result.score_total.toFixed(1) }}</div>
              <div class="tiny dim">综合分（0-100）</div>
            </div>
            <div class="row wrap">
              <span class="badge plain">总时延 {{ result.latency_ms }} ms</span>
              <span class="badge plain">首字 {{ result.ttft_ms }} ms</span>
              <span class="badge plain">{{ result.completion_tokens }} tokens</span>
            </div>
            <p v-if="result.error" class="warn-box small mb-0">{{ result.error }}</p>

            <div>
              <b class="small">推理过程（它打算怎么做）</b>
              <pre class="reasoning">{{ result.reasoning_text || '（模型没有给出推理过程）' }}</pre>
            </div>
            <div>
              <b class="small">输出</b>
              <pre class="output">{{ result.output_text || '（空）' }}</pre>
            </div>

            <div v-if="result.scores?.length">
              <b class="small">指标明细</b>
              <div v-for="s in result.scores" :key="s.code" class="metric-row">
                <span class="small">{{ s.label }}</span>
                <span class="bar"><i :style="{ width: Math.min(100, s.value * 100) + '%' }" /></span>
                <span class="tiny dim nowrap">{{ s.display }}</span>
              </div>
            </div>
            <RouterLink class="small" :to="`/eval/quality?skill_id=${encodeURIComponent(result.skill_id)}`">
              看这个 Skill 的历史评估汇总 →
            </RouterLink>
          </div>
        </div>

        <div class="card" v-else>
          <div class="card-head"><h3>说明</h3></div>
          <div class="card-body">
            <p class="small muted">
              实测会真的发一次外部请求。如果当前没有网络，系统会自动回退到<b>本地离线桩</b>，
              结果上会标「离线兜底」并说明原因 —— 这样链路仍然可验证，问题也不会被掩盖。
            </p>
            <p class="small muted mb-0">
              想换模型或调整 Key，去 <RouterLink to="/access">接入管理</RouterLink>。
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h3>最近的实测</h3><button class="btn sm" @click="loadRecent">刷新</button></div>
          <div class="card-body">
            <div v-for="r in recent" :key="r.code" class="recent">
              <div class="row between">
                <b class="small ellipsis">{{ r.skill_name }}</b>
                <span class="badge" :class="r.status === 'DONE' ? 'ok' : 'danger'">{{ r.score_total.toFixed(1) }}</span>
              </div>
              <div class="tiny dim ellipsis">{{ r.input_text }}</div>
            </div>
            <p v-if="!recent.length" class="dim small mb-0">还没有实测记录。</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { listSkills } from '@/api';
import { listAgents, listMcps, listRuns, runEval } from '@/api/eval';
import { notifyError } from '@/api/client';
import { useAppStore } from '@/store/app';

const app = useAppStore();

const skillKw = ref('');
const skillOptions = ref([]);
const agents = ref([]);
const mcps = ref([]);
const recent = ref([]);
const result = ref(null);
const busy = ref(false);

const form = reactive({
  skill_id: '',
  agent_code: 'local-stub',
  mcp_code: 'builtin-calc',
  scene_code: 'general',
  input_text: '',
  expected_text: '',
  use_mcp: true,
  temperature: 0.3,
  max_tokens: 1024,
});

async function searchSkills() {
  try {
    const page = await listSkills({ kw: skillKw.value || undefined, size: 20 });
    skillOptions.value = page.items;
    if (!form.skill_id && page.items.length) form.skill_id = page.items[0].skill_id;
  } catch (e) {
    notifyError(e);
  }
}

async function run() {
  busy.value = true;
  result.value = null;
  try {
    result.value = await runEval({ ...form });
    if (result.value.error) {
      notifyError(new Error(result.value.error));
    }
    await loadRecent();
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}

async function loadRecent() {
  try {
    const page = await listRuns({ page: 1, size: 6 });
    recent.value = page.items;
  } catch {
    /* 忽略 */
  }
}

watch(skillKw, () => searchSkills());

onMounted(async () => {
  await searchSkills();
  try {
    agents.value = await listAgents();
    mcps.value = await listMcps();
    if (agents.value.length) form.agent_code = agents.value[0].code;
    if (mcps.value.length) form.mcp_code = mcps.value[0].code;
  } catch (e) {
    notifyError(e);
  }
  await loadRecent();
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
.eval-layout {
  display: grid;
  grid-template-columns: minmax(0, 420px) minmax(0, 1fr);
  gap: var(--sk-s4);
  align-items: start;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sk-s3);
}
.req {
  color: var(--sk-danger);
}
.score-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.total {
  font-size: var(--sk-fs-2xl);
  font-weight: 800;
  color: var(--sk-primary-strong);
  line-height: 1;
}
.reasoning,
.output {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--sk-bg);
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: var(--sk-s3);
  font-size: var(--sk-fs-sm);
  margin: 6px 0 0;
  max-height: 260px;
  overflow: auto;
}
.warn-box {
  background: #fdf3e3;
  border-left: 3px solid var(--sk-warn);
  padding: 8px 10px;
  border-radius: 0 var(--sk-r-sm) var(--sk-r-sm) 0;
}
.metric-row {
  display: grid;
  grid-template-columns: 96px 1fr 64px;
  gap: 8px;
  align-items: center;
  margin-bottom: 4px;
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
  background: var(--sk-primary);
}
.recent {
  padding: 6px 0;
  border-bottom: 1px dashed var(--sk-border);
}
.recent:last-child {
  border-bottom: none;
}
@media (max-width: 1000px) {
  .eval-layout {
    grid-template-columns: 1fr;
  }
  .two {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div>
    <div v-if="error" class="card empty-state">
      <h3>{{ error }}</h3>
      <RouterLink class="btn mt-3" to="/contributors">返回贡献者列表</RouterLink>
    </div>
    <div v-else-if="!data" class="dim">加载中…</div>
    <div v-else>
      <div class="page-head card card-pad">
        <div class="row">
          <div class="avatar">{{ (profile.name || profile.author).slice(0, 1) }}</div>
          <div>
            <h1 class="mb-0">{{ profile.name || profile.author }}</h1>
            <div class="row wrap mt-2">
              <span class="badge" :class="profile.kind === 'personal' ? 'info' : 'plain'">
                {{ profile.kind_label }}
              </span>
              <span class="badge plain">来源：{{ profile.source }}</span>
              <span class="badge plain">{{ skills.length }} 个 Skill</span>
              <span class="badge plain">累计下载 {{ profile.download_total }}</span>
            </div>
          </div>
        </div>
        <p class="muted small mb-0 mt-3">{{ profile.description }}</p>
        <a v-if="profile.git_profile" class="small" :href="profile.git_profile" target="_blank" rel="noopener">
          上游主页 ↗
        </a>
      </div>

      <div class="section-title">
        <h2>TA 的全部 Skill</h2>
      </div>
      <div class="grid">
        <SkSkillCard v-for="s in skills" :key="s.skill_id" :card="s" />
      </div>
      <p v-if="!skills.length" class="dim">这位贡献者还没有可展示的 Skill。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import SkSkillCard from '@/components/SkSkillCard.vue';
import { getContributor } from '@/api';

const route = useRoute();
const data = ref(null);
const error = ref('');

const profile = computed(() => data.value?.profile || {});
const skills = computed(() => data.value?.skills || []);

async function load() {
  data.value = null;
  error.value = '';
  try {
    data.value = await getContributor(route.params.source, route.params.author);
  } catch (e) {
    error.value = e.message;
  }
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.page-head {
  margin-bottom: var(--sk-s4);
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--sk-primary), var(--sk-accent));
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}
.empty-state {
  padding: var(--sk-s10);
  text-align: center;
}
</style>

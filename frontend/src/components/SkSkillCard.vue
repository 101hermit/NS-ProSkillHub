<template>
  <RouterLink :to="`/skills/${encodeURIComponent(card.skill_id)}`" class="skill-card card">
    <div class="top">
      <div class="name ellipsis" :title="card.name !== card.title ? `${card.title} · ${card.name}` : card.name">
        <template v-for="(s, i) in titleSegments" :key="i">
          <mark v-if="s.hit">{{ s.text }}</mark>
          <span v-else>{{ s.text }}</span>
        </template>
      </div>
      <span class="badge" :class="card.risk_level">{{ card.risk_label }}</span>
    </div>
    <p class="desc clamp-3">
      <template v-for="(s, i) in descSegments" :key="i">
        <mark v-if="s.hit">{{ s.text }}</mark>
        <span v-else>{{ s.text }}</span>
      </template>
    </p>
    <div class="tags">
      <span class="badge info">{{ card.category_label }}</span>
      <span v-if="card.scene_code !== 'general'" class="badge plain">{{ card.scene_label }}</span>
      <span v-for="t in card.tags.slice(0, 2)" :key="t" class="badge plain tiny">
        <template v-for="(s, i) in segments(t, kw)" :key="i">
          <mark v-if="s.hit">{{ s.text }}</mark>
          <span v-else>{{ s.text }}</span>
        </template>
      </span>
    </div>
    <div class="foot">
      <span class="mono tiny dim ellipsis">{{ card.skill_id }}</span>
      <span class="tiny dim nowrap">
        v{{ card.version }} · {{ card.file_count }} 文件 · ↓{{ card.download_count }}
      </span>
    </div>
    <!-- 命中位置：告诉用户"为什么这条结果相关"，省掉在一屏文字里自己找的功夫 -->
    <div v-if="hits.length" class="hits">
      <SkIcon name="search" :size="12" />
      命中 {{ hits.join(' / ') }}
    </div>
  </RouterLink>
</template>

<script setup>
import { computed } from 'vue';
import SkIcon from '@/components/SkIcon.vue';
import { matchedFields, segments } from '@/utils/highlight';

const props = defineProps({
  card: { type: Object, required: true },
  /** 当前搜索关键词；列表页会传"何时用我"之外的这个上下文，其它页面不传即可。 */
  kw: { type: String, default: '' },
});

const hits = computed(() => matchedFields(props.card, props.kw));
const titleSegments = computed(() =>
  segments(props.card.title !== '—' ? props.card.title : props.card.name, props.kw),
);
const descSegments = computed(() => segments(props.card.description, props.kw));
</script>

<style scoped>
.skill-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: var(--sk-s4);
  color: inherit;
  text-decoration: none;
  transition: box-shadow var(--sk-dur) var(--sk-ease), transform var(--sk-dur) var(--sk-ease),
    border-color var(--sk-dur) var(--sk-ease);
  height: 100%;
  min-height: 176px;
}
.skill-card:hover {
  text-decoration: none;
  border-color: var(--sk-primary);
  box-shadow: var(--sk-shadow-2);
  transform: translateY(-1px);
}
.top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.name {
  font-weight: 650;
  font-size: var(--sk-fs-md);
  color: var(--sk-text);
}
.desc {
  margin: 0;
  color: var(--sk-text-2);
  font-size: var(--sk-fs-sm);
  flex: 1;
  min-height: 3.9em;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  overflow: hidden;
  max-height: 22px;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px dashed var(--sk-border);
  padding-top: 8px;
}
.nowrap {
  white-space: nowrap;
}
.hits {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--sk-fs-xs);
  color: var(--sk-primary-strong);
}
/* 命中高亮：用 warn 色的浅底，不用纯黄 —— 三套主题（含深色）下都能读 */
mark {
  background: color-mix(in srgb, var(--sk-warn) 22%, transparent);
  color: inherit;
  padding: 0 1px;
  border-radius: 2px;
  font-weight: 600;
}
</style>
<template>
  <div class="tree">
    <div v-for="f in sorted" :key="f.path" class="node">
      <span class="path mono ellipsis" :title="f.path">{{ f.path }}</span>
      <span class="badge plain tiny" v-if="f.primary">主文件</span>
      <span class="size tiny dim nowrap">{{ humanBytes(f.bytes) }}</span>
    </div>
    <p v-if="!files.length" class="dim small mb-0">这个 Skill 没有额外附件，只有 SKILL.md。</p>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { humanBytes } from '@/utils/format';

const props = defineProps({
  files: { type: Array, default: () => [] },
});

const sorted = computed(() =>
  [...props.files].sort((a, b) => (b.primary ? 1 : 0) - (a.primary ? 1 : 0) || a.path.localeCompare(b.path)),
);
</script>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 260px;
  overflow: auto;
}
.node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 6px;
  border-radius: 4px;
  min-width: 0;
}
.node:hover {
  background: var(--sk-bg);
}
.path {
  flex: 1;
  min-width: 0;
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-2);
}
</style>

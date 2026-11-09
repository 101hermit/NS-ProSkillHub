<template>
  <svg
    class="sk-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="inner"
  />
</template>

<script setup>
import { computed } from 'vue';
import { ICONS } from '@/assets/icons';

/**
 * 统一图标组件（三系统共用）。
 *
 * 图标来自 Lucide 线性图标库（MIT），与阿里巴巴 iconfont 的线性图标规范一致：
 * 24×24 网格、2px 描边、currentColor 着色。内联 SVG 而不用字体，是为了
 * 零外部请求 + 零字体闪动 + 颜色尺寸随上下文自动适配。
 */
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
  strokeWidth: { type: [Number, String], default: 1.8 },
});

const inner = computed(() => ICONS[props.name] || ICONS['circle-alert']);
</script>

<style scoped>
.sk-icon {
  display: inline-block;
  vertical-align: -0.16em;
  flex: 0 0 auto;
}
</style>

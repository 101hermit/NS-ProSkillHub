<template>
  <div class="gauge" :style="{ width: size + 'px' }">
    <svg :viewBox="`0 0 ${size} ${size * 0.62}`" :width="size" :height="size * 0.62">
      <!-- 底环 -->
      <path :d="arc" fill="none" stroke="var(--sk-muted)" :stroke-width="thickness" stroke-linecap="round" />
      <!-- 数值环 -->
      <path
        :d="arc"
        fill="none"
        :stroke="color"
        :stroke-width="thickness"
        stroke-linecap="round"
        :stroke-dasharray="dash"
      />
    </svg>
    <div class="center">
      <div class="value" :style="{ color }">{{ score }}</div>
      <div class="label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { RISK_COLOR, RISK_LABEL } from '@/utils/format';

const props = defineProps({
  score: { type: Number, default: 0 },
  level: { type: String, default: 'unrated' },
  size: { type: Number, default: 168 },
  thickness: { type: Number, default: 14 },
});

const color = computed(() => RISK_COLOR[props.level] || RISK_COLOR.unrated);
const label = computed(() => RISK_LABEL[props.level] || '未检测');

/** 半环路径（从左到右 180°）。 */
const arc = computed(() => {
  const w = props.size;
  const h = w * 0.62;
  const r = (w - props.thickness) / 2;
  const cx = w / 2;
  const cy = h - props.thickness / 2;
  const x1 = cx - r;
  const x2 = cx + r;
  return `M ${x1} ${cy} A ${r} ${r} 0 0 1 ${x2} ${cy}`;
});

/** 用 dasharray 做进度：半环长度 = πr。 */
const dash = computed(() => {
  const r = (props.size - props.thickness) / 2;
  const len = Math.PI * r;
  const ratio = Math.max(0, Math.min(1, props.score / 100));
  return `${len * ratio} ${len}`;
});
</script>

<style scoped>
.gauge {
  position: relative;
  display: inline-block;
}
svg {
  display: block;
}
.center {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  text-align: center;
  padding-bottom: 4px;
}
.value {
  font-size: var(--sk-fs-xl);
  font-weight: 700;
  line-height: 1.1;
}
.label {
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-3);
}
</style>

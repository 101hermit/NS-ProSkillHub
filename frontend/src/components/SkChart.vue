<template>
  <div class="chart">
    <svg :viewBox="`0 0 ${W} ${H}`" width="100%" :height="height" role="img" :aria-label="aria">
      <!-- 横向柱状图 -->
      <template v-if="type === 'bar'">
        <g v-for="(d, i) in normalized" :key="d.key">
          <text x="0" :y="barY(i) + 12" font-size="11" fill="var(--sk-text-2)">{{ d.label }}</text>
          <rect
            :x="labelW"
            :y="barY(i)"
            :width="Math.max(2, (d.count / maxValue) * (W - labelW - valueW))"
            height="16"
            rx="3"
            :fill="d.color || 'var(--sk-primary)'"
          />
          <text
            :x="labelW + (d.count / maxValue) * (W - labelW - valueW) + 6"
            :y="barY(i) + 12"
            font-size="11"
            fill="var(--sk-text-3)"
          >
            {{ d.count }}
          </text>
        </g>
      </template>

      <!-- 折线图 -->
      <template v-else-if="type === 'line'">
        <polyline :points="linePoints" fill="none" stroke="var(--sk-primary)" stroke-width="2" />
        <circle v-for="(p, i) in lineDots" :key="i" :cx="p.x" :cy="p.y" r="3" fill="var(--sk-primary)" />
        <text
          v-for="(p, i) in lineDots"
          :key="'t' + i"
          :x="p.x"
          :y="H - 6"
          font-size="10"
          text-anchor="middle"
          fill="var(--sk-text-3)"
        >
          {{ normalized[i]?.label }}
        </text>
      </template>

      <!-- 热力矩阵 -->
      <template v-else-if="type === 'heatmap'">
        <g v-for="cell in heatCells" :key="cell.key">
          <rect
            :x="cell.x"
            :y="cell.y"
            :width="cell.w"
            :height="cell.h"
            :fill="cell.color"
            stroke="#fff"
            stroke-width="2"
            rx="3"
          >
            <title>{{ cell.title }}</title>
          </rect>
          <text
            :x="cell.x + cell.w / 2"
            :y="cell.y + cell.h / 2 + 4"
            font-size="10"
            text-anchor="middle"
            :fill="cell.textColor"
          >
            {{ cell.text }}
          </text>
        </g>
        <text v-for="h in heatHeaders" :key="h.key" :x="h.x" :y="14" font-size="10" fill="var(--sk-text-2)">
          {{ h.label }}
        </text>
        <text v-for="v in heatRows" :key="v.key" x="2" :y="v.y" font-size="10" fill="var(--sk-text-2)">
          {{ v.label }}
        </text>
      </template>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 极简 SVG 图表（零依赖）。
 * type: bar（横向柱状）| line（折线）| heatmap（矩阵热力）
 * items: [{key,label,count,color?}]；热力图额外接受 rows/cols/values。
 */
const props = defineProps({
  type: { type: String, default: 'bar' },
  items: { type: Array, default: () => [] },
  height: { type: Number, default: 200 },
  aria: { type: String, default: '统计图表' },
  // 热力图：values 为 [rowIndex][colIndex] = {text, color, title}
  rows: { type: Array, default: () => [] },
  cols: { type: Array, default: () => [] },
  values: { type: Array, default: () => [] },
});

const W = 620;
const H = computed(() => props.height);
const labelW = 96;
const valueW = 44;

const normalized = computed(() =>
  (props.items || []).map((d) => ({ key: d.key ?? d.label, label: d.label, count: Number(d.count) || 0, color: d.color })),
);

const maxValue = computed(() => Math.max(1, ...normalized.value.map((d) => d.count)));

function barY(i) {
  const n = normalized.value.length || 1;
  const gap = 6;
  const rowH = Math.max(18, Math.min(30, (H.value - gap) / n));
  return 6 + i * rowH;
}

const lineDots = computed(() => {
  const n = normalized.value.length;
  if (!n) return [];
  const step = n > 1 ? (W - 20) / (n - 1) : 0;
  return normalized.value.map((d, i) => ({
    x: 10 + i * step,
    y: H.value - 22 - (d.count / maxValue.value) * (H.value - 46),
  }));
});

const linePoints = computed(() => lineDots.value.map((p) => `${p.x},${p.y}`).join(' '));

const heatCells = computed(() => {
  const padLeft = 72;
  const padTop = 20;
  const rowsN = props.rows.length || 1;
  const colsN = props.cols.length || 1;
  const cw = (W - padLeft) / colsN;
  const ch = Math.max(22, (H.value - padTop) / rowsN);
  const out = [];
  for (let r = 0; r < rowsN; r++) {
    for (let c = 0; c < colsN; c++) {
      const v = props.values?.[r]?.[c] || { text: '—', color: 'var(--sk-muted)' };
      out.push({
        key: `${r}-${c}`,
        x: padLeft + c * cw + 2,
        y: padTop + r * ch + 2,
        w: cw - 4,
        h: ch - 4,
        color: v.color,
        text: v.text,
        title: v.title || '',
        textColor: v.textColor || '#fff',
      });
    }
  }
  return out;
});

const heatHeaders = computed(() =>
  props.cols.map((c, i) => ({
    key: c,
    label: c,
    x: 72 + ((W - 72) / (props.cols.length || 1)) * i + 6,
  })),
);

const heatRows = computed(() => {
  const padTop = 20;
  const rowsN = props.rows.length || 1;
  const ch = Math.max(22, (H.value - padTop) / rowsN);
  return props.rows.map((r, i) => ({ key: r, label: r, y: padTop + i * ch + ch / 2 + 4 }));
});
</script>

<style scoped>
.chart {
  width: 100%;
}
</style>

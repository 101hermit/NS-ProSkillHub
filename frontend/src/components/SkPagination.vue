<template>
  <div v-if="total > 0" class="pager">
    <span class="dim small">
      共 <b>{{ total }}</b> 条 · 第 {{ page }}/{{ totalPages }} 页
    </span>
    <div class="grow" />
    <select class="select size-select" :value="size" @change="onSize">
      <option v-for="s in sizeOptions" :key="s" :value="s">{{ s }} 条/页</option>
    </select>
    <button class="btn sm" :disabled="page <= 1" @click="go(1)">«</button>
    <button class="btn sm" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
    <button class="btn sm" :disabled="page >= totalPages" @click="go(page + 1)">下一页</button>
    <button class="btn sm" :disabled="page >= totalPages" @click="go(totalPages)">»</button>
  </div>
</template>

<script setup>
defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 },
  sizeOptions: { type: Array, default: () => [12, 20, 40, 80] },
});
const emit = defineEmits(['change']);

function go(page) {
  emit('change', { page });
}
function onSize(e) {
  emit('change', { page: 1, size: Number(e.target.value) });
}
</script>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  gap: var(--sk-s2);
  margin-top: var(--sk-s4);
  flex-wrap: wrap;
}
.size-select {
  width: auto;
  height: 28px;
  padding: 0 6px;
  font-size: var(--sk-fs-sm);
}
</style>

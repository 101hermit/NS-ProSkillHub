<template>
  <div class="table-wrap card">
    <table class="data-table">
      <thead>
        <tr>
          <th
            v-for="c in columns"
            :key="c.key"
            :style="{ width: c.width, textAlign: c.align || 'left' }"
            :class="{ sortable: c.sortable }"
            @click="c.sortable && onSort(c)"
          >
            {{ c.label }}
            <span v-if="c.sortable" class="sort-ico">{{ sortIcon(c) }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td :colspan="columns.length" class="state">加载中…</td>
        </tr>
        <tr v-else-if="!rows.length">
          <td :colspan="columns.length" class="state">{{ emptyText }}</td>
        </tr>
        <tr v-for="(row, i) in rows" v-else :key="row.__key ?? i" @click="$emit('row-click', row)">
          <td v-for="c in columns" :key="c.key" :style="{ textAlign: c.align || 'left' }">
            <slot :name="c.key" :row="row" :value="row[c.key]">{{ display(row[c.key], c) }}</slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  sortKey: { type: String, default: '' },
  sortOrder: { type: String, default: 'desc' },
});
const emit = defineEmits(['row-click', 'sort']);

function onSort(c) {
  const nextOrder = props.sortKey === c.key && props.sortOrder === 'desc' ? 'asc' : 'desc';
  emit('sort', { key: c.key, order: nextOrder });
}

function sortIcon(c) {
  if (props.sortKey !== c.key) return '↕';
  return props.sortOrder === 'asc' ? '↑' : '↓';
}

function display(value, c) {
  if (value === null || value === undefined || value === '') return '—';
  if (c.format) return c.format(value);
  return String(value);
}
</script>

<style scoped>
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--sk-fs-sm);
  background: var(--sk-surface);
  border-radius: var(--sk-r);
  overflow: hidden;
}
.data-table th {
  position: sticky;
  top: 0;
  background: var(--sk-bg);
  color: var(--sk-text-2);
  font-weight: 650;
  text-align: left;
  padding: 9px var(--sk-s3);
  border-bottom: 1px solid var(--sk-border);
  white-space: nowrap;
}
.data-table th.sortable {
  cursor: pointer;
  user-select: none;
}
.data-table th.sortable:hover {
  color: var(--sk-primary);
}
.sort-ico {
  color: var(--sk-text-3);
  font-size: 11px;
  margin-left: 2px;
}
.data-table td {
  padding: 8px var(--sk-s3);
  border-bottom: 1px solid var(--sk-border);
  vertical-align: top;
  word-break: break-word;
}
.data-table tbody tr:hover {
  background: #fafbfe;
}
.data-table tbody tr:last-child td {
  border-bottom: none;
}
.state {
  text-align: center;
  color: var(--sk-text-3);
  padding: var(--sk-s6) !important;
}
</style>

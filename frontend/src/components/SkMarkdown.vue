<template>
  <div class="md" v-html="html" />
</template>

<script setup>
import { computed } from 'vue';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps({
  source: { type: String, default: '' },
});

const html = computed(() => renderMarkdown(props.source));
</script>

<style>
/* 非 scoped：Markdown 内联 HTML 由 v-html 注入，scoped 选择器命中不到 */
.md {
  color: var(--sk-text);
  font-size: var(--sk-fs);
  line-height: 1.75;
  overflow-wrap: break-word;
}
.md > :first-child {
  margin-top: 0;
}
.md h1 {
  font-size: var(--sk-fs-lg);
  border-bottom: 1px solid var(--sk-border);
  padding-bottom: 8px;
  margin-top: var(--sk-s6);
}
.md h2 {
  font-size: var(--sk-fs-md);
  margin-top: var(--sk-s5);
}
.md h3,
.md h4 {
  font-size: var(--sk-fs);
  margin-top: var(--sk-s4);
}
.md p {
  margin: 0 0 var(--sk-s3);
}
.md ul,
.md ol {
  margin: 0 0 var(--sk-s3);
  padding-left: 22px;
}
.md li {
  margin-bottom: 4px;
}
.md code {
  background: var(--sk-muted);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.92em;
}
.md .md-pre {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: var(--sk-r-sm);
  padding: var(--sk-s3) var(--sk-s4);
  overflow-x: auto;
  margin: 0 0 var(--sk-s3);
}
.md .md-pre code {
  background: transparent;
  color: inherit;
  padding: 0;
  white-space: pre;
  font-size: var(--sk-fs-sm);
}
.md blockquote {
  margin: 0 0 var(--sk-s3);
  padding: 8px var(--sk-s4);
  border-left: 3px solid var(--sk-primary-weak);
  background: var(--sk-bg);
  color: var(--sk-text-2);
  border-radius: 0 var(--sk-r-sm) var(--sk-r-sm) 0;
}
.md .md-table {
  border-collapse: collapse;
  width: 100%;
  margin: 0 0 var(--sk-s3);
  font-size: var(--sk-fs-sm);
}
.md .md-table th,
.md .md-table td {
  border: 1px solid var(--sk-border);
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}
.md .md-table th {
  background: var(--sk-bg);
  font-weight: 650;
}
.md img {
  max-width: 100%;
}
.md a {
  word-break: break-all;
}
</style>

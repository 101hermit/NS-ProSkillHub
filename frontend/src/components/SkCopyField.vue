<template>
  <div class="copy-field">
    <code class="ellipsis">{{ value }}</code>
    <button class="btn sm" @click="copy">{{ copied ? '已复制' : '复制' }}</button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useToastStore } from '@/store/toast';

const props = defineProps({
  value: { type: String, required: true },
  toast: { type: String, default: '已复制到剪贴板' },
});

const toast = useToastStore();
const copied = ref(false);

async function copy() {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(props.value);
    } else {
      // 非安全上下文（内网 http）下的兜底：临时 textarea + execCommand
      const el = document.createElement('textarea');
      el.value = props.value;
      el.style.position = 'fixed';
      el.style.opacity = '0';
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
    }
    copied.value = true;
    toast.ok(props.toast);
    window.setTimeout(() => (copied.value = false), 1800);
  } catch {
    toast.err('复制失败。怎么办：手动选中文本复制。');
  }
}
</script>

<style scoped>
.copy-field {
  display: flex;
  align-items: center;
  gap: var(--sk-s2);
  background: var(--sk-bg);
  border: 1px solid var(--sk-border);
  border-radius: var(--sk-r-sm);
  padding: 6px 6px 6px var(--sk-s3);
  min-width: 0;
}
code {
  flex: 1;
  min-width: 0;
  font-size: var(--sk-fs-xs);
  color: var(--sk-text-2);
}
</style>

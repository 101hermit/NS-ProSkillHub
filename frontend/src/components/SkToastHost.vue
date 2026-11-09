<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in toast.items" :key="t.id" class="toast" :class="t.level" @click="toast.remove(t.id)">
        <span class="ico">{{ ICON[t.level] || 'ℹ' }}</span>
        <span class="txt">{{ t.text }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useToastStore } from '@/store/toast';

const toast = useToastStore();
const ICON = { success: '✔', warn: '!', error: '✕', info: 'ℹ' };
</script>

<style scoped>
.toast-host {
  position: fixed;
  top: calc(var(--sk-header-h) + 12px);
  right: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 9999;
  max-width: min(420px, calc(100vw - 32px));
}
.toast {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 14px;
  border-radius: var(--sk-r);
  background: var(--sk-surface);
  border: 1px solid var(--sk-border);
  border-left: 4px solid var(--sk-info);
  box-shadow: var(--sk-shadow-2);
  font-size: var(--sk-fs-sm);
  cursor: pointer;
}
.toast.success {
  border-left-color: var(--sk-ok);
}
.toast.warn {
  border-left-color: var(--sk-warn);
}
.toast.error {
  border-left-color: var(--sk-danger);
}
.ico {
  font-weight: 700;
  line-height: 1.5;
}
.error .ico {
  color: var(--sk-danger);
}
.success .ico {
  color: var(--sk-ok);
}
.warn .ico {
  color: var(--sk-warn);
}
.txt {
  white-space: pre-wrap;
  word-break: break-word;
}
.toast-enter-active,
.toast-leave-active {
  transition: all 160ms var(--sk-ease);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
</style>

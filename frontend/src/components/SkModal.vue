<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="onOverlay">
      <div class="modal card" :style="{ maxWidth: width }" role="dialog" aria-modal="true">
        <div class="modal-head">
          <h3>{{ title }}</h3>
          <button class="btn ghost sm" aria-label="关闭" @click="$emit('close')">✕</button>
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '640px' },
  closeOnOverlay: { type: Boolean, default: true },
});
const emit = defineEmits(['close']);

function onOverlay() {
  if (props.closeOnOverlay) emit('close');
}
function onKey(e) {
  if (e.key === 'Escape' && props.open) emit('close');
}

watch(
  () => props.open,
  (v) => {
    document.body.style.overflow = v ? 'hidden' : '';
  },
);

onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey);
  document.body.style.overflow = '';
});
</script>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  display: grid;
  place-items: center;
  z-index: 9000;
  padding: var(--sk-s4);
}
.modal {
  width: 100%;
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--sk-shadow-3);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sk-s3) var(--sk-s5);
  border-bottom: 1px solid var(--sk-border);
}
.modal-head h3 {
  margin: 0;
}
.modal-body {
  padding: var(--sk-s5);
  overflow: auto;
}
.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sk-s2);
  padding: var(--sk-s3) var(--sk-s5);
  border-top: 1px solid var(--sk-border);
}
</style>

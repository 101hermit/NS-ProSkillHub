<template>
  <div class="app-shell">
    <AppLayout v-if="app.ready">
      <RouterView />
    </AppLayout>
    <div v-else class="boot">
      <div class="boot-card">
        <div class="spinner" />
        <p class="muted">正在连接后端…</p>
        <p v-if="error" class="boot-error">
          {{ error }}
          <br />
          <button class="btn sm mt-2" @click="boot">重试</button>
        </p>
      </div>
    </div>
    <SkToastHost />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import AppLayout from '@/layout/AppLayout.vue';
import SkToastHost from '@/components/SkToastHost.vue';
import { useAppStore } from '@/store/app';

const app = useAppStore();
const error = ref('');

async function boot() {
  error.value = '';
  try {
    await app.bootstrap();
  } catch (e) {
    error.value = e?.message || '无法获取站点信息。怎么办：确认后端已启动（双击 start.cmd）。';
  }
}

onMounted(boot);
</script>

<style scoped>
.app-shell {
  min-height: 100%;
}
.boot {
  display: grid;
  place-items: center;
  height: 100vh;
}
.boot-card {
  text-align: center;
  padding: var(--sk-s8);
  background: var(--sk-surface);
  border-radius: var(--sk-r-lg);
  box-shadow: var(--sk-shadow-2);
  max-width: 420px;
}
.boot-error {
  color: var(--sk-danger);
  font-size: var(--sk-fs-sm);
  margin-top: var(--sk-s4);
}
.spinner {
  width: 34px;
  height: 34px;
  margin: 0 auto var(--sk-s4);
  border: 3px solid var(--sk-primary-weak);
  border-top-color: var(--sk-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

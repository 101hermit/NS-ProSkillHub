import { reactive } from 'vue';

/**
 * 轻提示队列（单例，全站唯一出口）。
 * 最多同时 4 条；error 停 6 秒，其它 3.5 秒。
 */
let seq = 0;

export const toast = reactive({
  items: [],

  push(level, text, ttl) {
    if (!text) return null;
    const item = {
      id: ++seq,
      level: level || 'info',
      text: String(text),
      ttl: ttl ?? (level === 'error' ? 6000 : 3500),
    };
    this.items.unshift(item);
    if (this.items.length > 4) this.items.length = 4;
    window.setTimeout(() => this.remove(item.id), item.ttl);
    return item.id;
  },

  ok(text) {
    return this.push('success', text);
  },
  warn(text) {
    return this.push('warn', text);
  },
  err(text) {
    return this.push('error', text);
  },
  remove(id) {
    const i = this.items.findIndex((x) => x.id === id);
    if (i >= 0) this.items.splice(i, 1);
  },
});

export function useToastStore() {
  return toast;
}

/** 格式化工具（数值 / 字节 / 百分比 / 风险等级）。 */

export function humanBytes(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1024) return `${n} B`;
  const kb = n / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  return `${(mb / 1024).toFixed(2)} GB`;
}

export function num(n) {
  const v = Number(n) || 0;
  if (v >= 10000) return `${(v / 10000).toFixed(1)} 万`;
  return String(v);
}

export function pct(n, digits = 0) {
  const v = Number(n);
  if (!Number.isFinite(v)) return '—';
  return `${(v * 100).toFixed(digits)}%`;
}

export function fixed(n, digits = 1) {
  const v = Number(n);
  return Number.isFinite(v) ? v.toFixed(digits) : '—';
}

export const RISK_TONE = {
  safe: 'safe',
  low: 'low',
  medium: 'medium',
  high: 'high',
  unrated: 'unrated',
};

export const RISK_LABEL = {
  safe: '安全',
  low: '低风险',
  medium: '中风险',
  high: '高风险',
  unrated: '未检测',
};

export const RISK_COLOR = {
  safe: 'var(--sk-risk-safe)',
  low: 'var(--sk-risk-low)',
  medium: 'var(--sk-risk-medium)',
  high: 'var(--sk-risk-high)',
  unrated: 'var(--sk-risk-none)',
};

export const STATUS_LABEL = {
  published: '已发布',
  draft: '草稿',
  submitted: '待发布',
  rejected: '已驳回',
  archived: '已归档',
};

/** 把 URL query 里的字符串转成数字（失败回退默认值）。 */
export function intOr(v, def) {
  const n = Number.parseInt(v, 10);
  return Number.isFinite(n) ? n : def;
}

/** 高亮文本里的关键词（返回已转义的 HTML）。 */
export function highlight(text, kw) {
  const src = String(text ?? '');
  const safe = src
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  if (!kw) return safe;
  const needle = String(kw).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return safe.replace(new RegExp(needle, 'gi'), (m) => `<mark>${m}</mark>`);
}

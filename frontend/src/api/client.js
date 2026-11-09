/**
 * HTTP 客户端（三系统同构，基于原生 fetch，零依赖）。
 *
 * 三条纪律：
 *  1. 只在这里发请求；页面与组件不直接碰 fetch。
 *  2. 统一解包 {code,msg,data,trace_id}：成功只把 data 交给调用方。
 *  3. 失败一律抛 Error(后端 msg)，msg 本来就是"人话原因 + 怎么办"。
 *
 * 为什么不用 axios：站内只需要 GET/POST/PUT/DELETE + multipart，
 * fetch 全都有；少一个运行期依赖，内网离线部署也少一处风险。
 */

import { useToastStore } from '@/store/toast';

const BASE = '/api/v1';

/** 当前身份（本版不做登录，用请求头表达"谁在操作"）。 */
export function identity() {
  return {
    actor: localStorage.getItem('skh.actor') || 'guest',
    role: localStorage.getItem('skh.role') || 'viewer',
  };
}

export function setIdentity(actor, role) {
  localStorage.setItem('skh.actor', actor || 'guest');
  localStorage.setItem('skh.role', role || 'viewer');
}

function buildQuery(params) {
  if (!params) return '';
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    usp.append(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : '';
}

/**
 * 底层请求。
 * @param {string} method HTTP 方法
 * @param {string} path   以 / 开头的接口路径（不含 /api/v1）
 * @param {{params?:object, body?:any, form?:FormData}} options
 */
async function request(method, path, { params, body, form } = {}) {
  const { actor, role } = identity();
  const headers = {
    'X-SKH-Actor': encodeURIComponent(actor),
    'X-SKH-Role': role,
  };
  // 已登录/访客：带上令牌（后端据此解析身份，优先级高于上面两个头）
  const token = localStorage.getItem('skh.token');
  if (token) headers.Authorization = 'Bearer ' + token;
  const init = { method, headers };
  if (form) {
    init.body = form; // 交给浏览器自动带 boundary
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(BASE + path + buildQuery(params), init);
  } catch {
    throw new Error('连不上服务。怎么办：确认后端已启动（双击 start.cmd），并检查端口是否被占用。');
  }

  const text = await res.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    /* 非 JSON（例如 ZIP 下载） */
  }

  if (!payload || typeof payload !== 'object' || payload.code === undefined) {
    if (res.ok) return null;
    throw new Error(`服务返回 ${res.status}。怎么办：用响应头 X-Trace-Id 去服务端日志里检索。`);
  }

  if (payload.code === 0) return payload.data;

  if (payload.code === 6000) {
    // 离线降级：业务成功但走了本地兜底
    useToastStore().warn(payload.msg);
    return payload.data;
  }

  const err = new Error(payload.msg || `请求失败（${payload.code}）`);
  err.code = payload.code;
  err.traceId = payload.trace_id;
  throw err;
}

const http = {
  get: (path, options) => request('GET', path, options),
  post: (path, body, options) => request('POST', path, { ...(options || {}), body }),
  put: (path, body, options) => request('PUT', path, { ...(options || {}), body }),
  del: (path, options) => request('DELETE', path, options),
  /** multipart 上传 */
  upload: (path, formData) => request('POST', path, { form: formData }),
};

/** 统一的错误提示（页面在 catch 里调用一次即可）。 */
export function notifyError(e) {
  useToastStore().err(e?.message || '未知错误');
}

export default http;

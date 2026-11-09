/**
 * 前端冒烟测试（jsdom，无浏览器）
 * ---------------------------------------------------------------------------
 * 为什么需要它：本机环境不允许启动浏览器进程（Headless Edge 被系统拒绝），
 * 但又必须验证"页面真的能渲染、真的能拿到后端数据"。
 * 做法：用 jsdom 造一个 DOM + window，把构建产物（ESM bundle）动态 import 进来，
 *       Vue 会在里面真的挂载、真的发请求（fetch 指向本机后端），
 *       然后我们检查渲染结果里是否出现了应有的文字。
 *
 * 前置：后端已启动（默认 30800），且前端已构建。
 * 用法：node test/smoke.mjs [--base http://127.0.0.1:30800] [--route /]
 */
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
function arg(name, def) {
  const i = argv.indexOf('--' + name);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : def;
}
const BASE = arg('base', 'http://127.0.0.1:30800');
const ROUTE = arg('route', '/');
const BUNDLE = path.resolve(__dirname, '../../backend/src/main/resources/static/assets/app.js');

const results = [];
function check(name, fn) {
  try {
    const memo = fn();
    results.push({ name, ok: true, memo });
  } catch (e) {
    results.push({ name, ok: false, memo: e.message });
  }
}
function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const dom = new JSDOM(
    `<!DOCTYPE html><html><body><div id="app"></div></body></html>`,
    { url: `${BASE}/#${ROUTE}`, pretendToBeVisual: true, runScripts: 'outside-only' },
  );
  const { window } = dom;

  // 把 jsdom 的 window 交给 vue / fetch
  // 注意：Node 24 的 globalThis.navigator 只有 getter，不能直接赋值，用 defineProperty 覆盖
  global.window = window;
  global.document = window.document;
  Object.defineProperty(globalThis, 'navigator', { value: window.navigator, configurable: true });
  global.HTMLElement = window.HTMLElement;
  global.Element = window.Element;
  global.Node = window.Node;
  global.SVGElement = window.SVGElement;
  global.localStorage = window.localStorage;
  global.location = window.location;
  global.history = window.history;
  global.Event = window.Event;
  global.CustomEvent = window.CustomEvent;
  // 把 jsdom 里剩下的 DOM 构造器（Document / ShadowRoot / HTMLInputElement / Range …）一并铺到 global。
  //
  // 为什么必须这么做：Vue 的运行时在 v-model 这类指令里会写 `node instanceof Document`、
  // `node instanceof ShadowRoot` 这样的判断。Node 里没有这些全局构造器时，只要页面上有一个
  // 带 v-model 的输入框（也就是几乎所有列表页），就会抛 `ReferenceError: Document is not defined`。
  // 手动一个个补是补不完的，这里按名字整体拷贝，缺什么补什么。
  for (const key of Object.getOwnPropertyNames(window)) {
    if (key in globalThis) continue;
    try {
      globalThis[key] = window[key];
    } catch {
      // getter-only 的属性（如某些 Navigator 字段）跳过即可
    }
  }
  global.getComputedStyle = window.getComputedStyle.bind(window);
  global.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  global.requestAnimationFrame = (cb) => setTimeout(() => cb(Date.now()), 0);
  global.cancelAnimationFrame = (id) => clearTimeout(id);
  window.scrollTo = () => {};

  // 相对路径的 fetch 补上 base（浏览器会这么做，Node 不会）
  const nodeFetch = globalThis.fetch;
  global.fetch = (input, init) => {
    const url = typeof input === 'string' && input.startsWith('/') ? BASE + input : input;
    return nodeFetch(url, init);
  };
  window.fetch = global.fetch;

  const errors = [];
  const origError = console.error;
  console.error = (...args) => {
    errors.push(args.map(String).join(' '));
    origError(...args);
  };

  // 先拿产品元信息，供后面"顶部栏"断言使用（同一脚本测三系统）
  let productName = 'SkillHub';
  let productAbbr = 'SKH';
  try {
    const meta = await nodeFetch(`${BASE}/api/v1/meta`).then((r) => r.json());
    productName = meta?.data?.product_name || productName;
    productAbbr = meta?.data?.abbr || productAbbr;
  } catch {
    /* 拿不到就用默认值，后续断言会暴露问题 */
  }

  await import(`file://${BUNDLE.replace(/\\/g, '/')}`);
  // 等 bootstrap 拿到 meta/health + 首页数据
  await wait(2500);
  const html = window.document.getElementById('app').innerHTML;
  const text = window.document.getElementById('app').textContent || '';

  check('应用已挂载（#app 有内容）', () => {
    assert(html.length > 200, `#app 内容只有 ${html.length} 字节`);
    return `${html.length} 字节`;
  });

  check('未出现启动错误提示', () => {
    assert(!text.includes('正在连接后端'), '仍停在「正在连接后端」——bootstrap 失败');
    assert(!text.includes('无法获取站点信息'), 'bootstrap 报错');
    return 'ok';
  });

  check('顶部栏渲染了产品名与版本徽标', () => {
    // 用 /api/v1/meta 的真实值断言，这样同一份脚本能测三个系统
    const metaText = window.document.documentElement.innerHTML;
    assert(metaText.includes(productName), `没找到产品名 ${productName}`);
    assert(metaText.includes(productAbbr), `没找到缩写 ${productAbbr}`);
    return `${productName} / ${productAbbr}`;
  });

  check('侧栏渲染了三个以上菜单项', () => {
    const links = window.document.querySelectorAll('.nav-item');
    assert(links.length >= 3, `侧栏只有 ${links.length} 项`);
    return `${links.length} 项：${[...links].map((a) => a.textContent.trim()).join(' / ')}`;
  });

  check('首页渲染了统计数字', () => {
    if (ROUTE !== '/') return '（非首页，跳过）';
    const stats = window.document.querySelectorAll('.hero-stats .stat');
    assert(stats.length >= 4, `统计块只有 ${stats.length} 个`);
    const nums = [...stats].map((s) => s.querySelector('.v')?.textContent);
    assert(nums.some((n) => n && n !== '0'), `统计数字全为 0：${nums.join(',')}`);
    return nums.join(' / ');
  });

  check('首页渲染了分类格子', () => {
    if (ROUTE !== '/') return '（非首页，跳过）';
    const cats = window.document.querySelectorAll('.cat');
    assert(cats.length >= 10, `分类格子只有 ${cats.length} 个`);
    return `${cats.length} 个分类`;
  });

  check('首页渲染了 Skill 卡片', () => {
    if (ROUTE !== '/') return '（非首页，跳过）';
    const cards = window.document.querySelectorAll('.skill-card');
    assert(cards.length > 0, '没有 Skill 卡片（库可能没导入）');
    return `${cards.length} 张卡片`;
  });

  check('页面主体渲染了标题与内容', () => {
    const h1 = window.document.querySelector('.content h1, .content h2, .content .page-head h1');
    assert(h1, '内容区没有 h1 标题（页面可能没渲染出来）');
    assert((text || '').trim().length > 200, `可见文字只有 ${text.trim().length} 字`);
    return `${h1.textContent.trim()}（可见 ${text.trim().length} 字）`;
  });

  check('没有出现错误态文案', () => {
    const bad = ['连不上服务', '服务内部错误', '请求失败'].filter((k) => text.includes(k));
    assert(bad.length === 0, `页面出现错误态：${bad.join('、')}`);
    return 'ok';
  });

  check('页面没有 Vue 运行时报错', () => {
    const real = errors.filter((e) => e.includes('[vue]') || e.includes('TypeError') || e.includes('ReferenceError'));
    assert(real.length === 0, real.slice(0, 2).join(' | '));
    return '无';
  });

  check('HTML 里没有残留的未编译模板标记', () => {
    assert(!html.includes('{{'), `渲染结果里还有 {{ }}：${html.slice(0, 200)}`);
    return 'ok';
  });

  const failed = results.filter((r) => !r.ok);
  console.log(`\n[smoke] ${BASE}/#${ROUTE}`);
  for (const r of results) console.log(`  ${r.ok ? '✔' : '✘'} ${r.name}  ·  ${r.memo}`);
  console.log(`\n[smoke] 共 ${results.length} 项：通过 ${results.length - failed.length}，失败 ${failed.length}`);
  dom.window.close();
  process.exit(failed.length === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('[smoke] 执行失败：', e);
  process.exit(2);
});

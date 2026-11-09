/**
 * 前端构建脚本（Rollup，纯 JS，不依赖 esbuild / Vite）
 * ---------------------------------------------------------------------------
 * 为什么不用 Vite：
 *   1) 真实测试机上的 Node.js 是 11.16，而 Vite 6 要求 Node 18+ —— 装不上；
 *   2) 本机环境下 esbuild 的服务进程用管道 stdio 通信，被沙箱拒绝（spawn EPERM），
 *      而 Vite 内部强依赖 esbuild（配置打包 + 转译 + 压缩），无法绕开。
 *   Rollup 是纯 JavaScript 实现（可选的原生插件在进程内加载，不开子进程），
 *   因此在这两类环境里都能跑。
 *
 * 产物：../backend/src/main/resources/static/（由 Spring 直接托管，单进程单端口）
 * 用法：node build.mjs [--no-minify]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { rollup } from 'rollup';
import vue from 'rollup-plugin-vue';
import resolve from '@rollup/plugin-node-resolve';
import terser from '@rollup/plugin-terser';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.resolve(__dirname, 'src');
const OUT_DIR = path.resolve(__dirname, '../backend/src/main/resources/static');
const MINIFY = !process.argv.includes('--no-minify');
const SITE_NAME = process.env.SKHUB_SITE_NAME || 'SkillHub';

/** @/xxx → src/xxx；vue / vue-router 指向"浏览器 ESM、已内置 NODE_ENV"的构建。 */
const PACKAGE_ALIASES = {
  vue: 'node_modules/vue/dist/vue.runtime.esm-browser.prod.js',
  'vue-router': 'node_modules/vue-router/dist/vue-router.esm-browser.prod.js',
};

const aliasPlugin = {
  name: 'skhub-alias',
  resolveId(source) {
    if (source.startsWith('@/')) return resolveFile(path.join(SRC_DIR, source.slice(2)));
    if (PACKAGE_ALIASES[source]) return path.resolve(__dirname, PACKAGE_ALIASES[source]);
    return null;
  },
};

/** 按 .js / .vue / .json / index.* 的顺序补全扩展名（等价于 node 的解析习惯）。 */
function resolveFile(base) {
  const candidates = [base, `${base}.js`, `${base}.vue`, `${base}.json`, path.join(base, 'index.js'), path.join(base, 'index.vue')];
  for (const c of candidates) {
    if (fs.existsSync(c) && fs.statSync(c).isFile()) return c;
  }
  return base;
}

/** 收集并"吃掉"样式：SFC 的 <style> 会被 rollup-plugin-vue 当成模块输出，必须换成 JS 空导出。 */
const cssChunks = [];

/** 普通 .css 文件：自己读、自己展开 @import、自己登记。 */
const loadCss = {
  name: 'skhub-load-css',
  load(id) {
    if (!id.endsWith('.css') || id.includes('?')) return null;
    cssChunks.push(readCssWithImports(id, new Set()));
    return 'export default {};';
  },
};

/** SFC 的样式子模块（App.vue?vue&type=style&...）：内容已经是 CSS，转成空导出。 */
const absorbSfcCss = {
  name: 'skhub-absorb-sfc-css',
  transform(code, id) {
    if (!id.includes('&type=style')) return null;
    if (code.trim()) cssChunks.push(code);
    return 'export default {};';
  },
};

function readCssWithImports(file, seen) {
  if (seen.has(file)) return '';
  seen.add(file);
  let code = fs.readFileSync(file, 'utf8');
  code = code.replace(/@import\s+['"](.+)['"];?/g, (_, spec) => readCssWithImports(path.resolve(path.dirname(file), spec), seen));
  return code;
}

async function main() {
  fs.mkdirSync(path.join(OUT_DIR, 'assets'), { recursive: true });

  const bundle = await rollup({
    input: path.join(SRC_DIR, 'main.js'),
    plugins: [
      aliasPlugin,
      resolve({ browser: true, extensions: ['.mjs', '.js', '.json'] }),
      vue({ css: false }),
      loadCss,
      absorbSfcCss,
    ],
    onwarn(warning, warn) {
      if (['CIRCULAR_DEPENDENCY', 'THIS_IS_UNDEFINED', 'UNUSED_EXTERNAL_IMPORT'].includes(warning.code)) return;
      warn(warning);
    },
  });

  const outFile = path.join(OUT_DIR, 'assets/app.js');
  const outputPlugins = MINIFY
    ? [terser({ compress: { passes: 2, drop_debugger: true }, format: { comments: false } })]
    : [];
  await bundle.write({
    file: outFile,
    format: 'es',
    sourcemap: false,
    inlineDynamicImports: true,
    plugins: outputPlugins,
  });
  await bundle.close();

  fs.writeFileSync(path.join(OUT_DIR, 'assets/app.css'), cssChunks.join('\n'), 'utf8');
  fs.writeFileSync(
    path.join(OUT_DIR, 'index.html'),
    `<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${SITE_NAME}</title>
    <link rel="stylesheet" href="./assets/app.css" />
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="./assets/app.js"></script>
  </body>
</html>
`,
    'utf8',
  );

  const jsSize = fs.statSync(outFile).size;
  const cssSize = fs.statSync(path.join(OUT_DIR, 'assets/app.css')).size;
  console.log(
    `[build] → ${OUT_DIR}\n[build] app.js ${(jsSize / 1024).toFixed(1)} KB · app.css ${(cssSize / 1024).toFixed(1)} KB · minify=${MINIFY}`,
  );
}

main().catch((e) => {
  console.error('[build] 失败：', e);
  process.exit(1);
});

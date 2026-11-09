#!/usr/bin/env node
/**
 * Skill 库完整性检查（「拿走即用」自足版）
 * ---------------------------------------------------------------------------
 * 只检查本工程自己的 skills/ 目录，不依赖任何外部路径。判据：
 *   1. skills/catalog.json 存在且能解析
 *   2. catalog 里的每个 slug 都有对应目录与 SKILL.md
 *   3. 目录数 ≥ catalog.skill_count（允许多，不允许少）
 *   4. 每个 SKILL.md 都带 frontmatter 的 name/description
 *
 * 用法：node scripts/check-library.mjs [--strict]
 * 退出码：0 通过 / 1 有问题
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const LIB = path.resolve(__dirname, '../skills');
const strict = process.argv.includes('--strict');

function fail(msg) {
  console.log('  ✘ ' + msg);
  problems++;
}
let problems = 0;

if (!fs.existsSync(path.join(LIB, 'catalog.json'))) {
  console.log(`[library] 缺少 ${path.join(LIB, 'catalog.json')}`);
  console.log('[library] 怎么办：skills/ 目录必须随工程一起拷贝（它不依赖任何外部路径）');
  process.exit(1);
}

const catalog = JSON.parse(fs.readFileSync(path.join(LIB, 'catalog.json'), 'utf8'));
const entries = catalog.skills || [];
const dirs = fs
  .readdirSync(LIB, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => d.name);

if (entries.length === 0) fail('catalog.json 里没有任何 Skill');
if (dirs.length < (catalog.skill_count || 0)) {
  fail(`目录数 ${dirs.length} 少于 catalog.skill_count ${catalog.skill_count}`);
}

let missingDir = 0;
let missingMd = 0;
let badFrontmatter = 0;
for (const e of entries) {
  const dir = path.join(LIB, e.slug);
  if (!fs.existsSync(dir)) {
    missingDir++;
    continue;
  }
  const md = path.join(dir, 'SKILL.md');
  if (!fs.existsSync(md)) {
    missingMd++;
    continue;
  }
  const head = fs.readFileSync(md, 'utf8').slice(0, 2000);
  if (!/^\s*---/.test(head) || !/\nname\s*:/.test(head) || !/\ndescription\s*:/.test(head)) {
    badFrontmatter++;
  }
}
if (missingDir) fail(`${missingDir} 个 Skill 目录缺失`);
if (missingMd) fail(`${missingMd} 个 Skill 缺少 SKILL.md`);
if (badFrontmatter) fail(`${badFrontmatter} 个 SKILL.md 的 frontmatter 不完整`);

const totalBytes = entries.reduce((a, e) => a + (e.bytes || 0), 0);
console.log(
  `[library] ${entries.length} 个 Skill（目录 ${dirs.length}）· ${(totalBytes / 1048576).toFixed(1)} MB · ` +
    `库版本 ${catalog.version || '?'}`,
);
if (problems === 0) {
  console.log('[library] 结论：通过');
  process.exit(0);
}
console.log(`[library] 结论：${problems} 项有问题${strict ? '' : '（加 --strict 可视为失败）'}`);
process.exit(strict ? 1 : 1);
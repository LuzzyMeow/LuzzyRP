#!/usr/bin/env node
/**
 * gen-changelog.mjs — 从仓库根 CHANGELOG.md 生成 app/src/main/assets/ext/luzzy-changelog.js
 *
 * 用途：关于页应用内 CHANGELOG（patch 014）。发布流程（AGENTS.md §3.4）在更新
 * CHANGELOG.md 后运行：`node tools/gen-changelog.mjs`。
 *
 * 转义：JS 字符串内反引号 `、反斜杠 \、${（模板字面量插值）。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'CHANGELOG.md');
const dst = join(root, 'app/src/main/assets/ext/luzzy-changelog.js');

const md = readFileSync(src, 'utf8')
    .replace(/\\/g, '\\\\')
    .replace(/`/g, '\\`')
    .replace(/\$\{/g, '\\${');

const banner = `/**
 * LuzzyRP 扩展层 · 应用内更新日志（patch 014，由 tools/gen-changelog.mjs 自动生成）
 * 来源：仓库根 CHANGELOG.md —— 请勿手改本文件，改 CHANGELOG.md 后重新运行生成脚本。
 */
(function () {
    window.LuzzyChangelog = { md: \``;

const footer = `\` };
})();
`;

writeFileSync(dst, banner + md + footer, 'utf8');
console.log(`[gen-changelog] wrote ${dst} (${md.length} chars of markdown)`);
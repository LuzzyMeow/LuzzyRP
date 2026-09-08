#!/usr/bin/env node
/**
 * gen-changelog.mjs — 从仓库根 CHANGELOG.md 生成 app/src/main/assets/ext/luzzy-changelog.js
 *
 * 用途：关于页应用内 CHANGELOG（patch 014）。发布流程（AGENTS.md §3.4）在更新
 * CHANGELOG.md 后运行：`node tools/gen-changelog.mjs`。
 *
 * 转义：JS 字符串内反引号 `、反斜杠 \、${（模板字面量插值）。
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
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

const final = banner + md + footer;

if (process.argv.includes('--check')) {
    const current = existsSync(dst) ? readFileSync(dst, 'utf8') : '';
    if (current === final) {
        console.log('[gen-changelog] --check 通过：应用内 CHANGELOG 数据与仓库根 CHANGELOG.md 一致');
        process.exit(0);
    }
    console.error('[gen-changelog] 应用内 CHANGELOG 数据过期——运行 node tools/gen-changelog.mjs 同步');
    process.exit(3);
}

writeFileSync(dst, final, 'utf8');
console.log(`[gen-changelog] wrote ${dst} (${md.length} chars of markdown)`);

// README 自动同步（硬性规定 5 减负：版本说明收敛 CHANGELOG 单一事实源，
// README 仅保留「当前版本」行与 Status 徽章，由本脚本在发版流程中自动改写）
//
// [v1.5.0] 徽章状态分支：CHANGELOG 顶部版本章节的「状态：」行决定徽章文案——
// 含「开发中」→ 琥珀色「开发中·未发布」；否则视为已发布 → 绿色「正式版·可游玩」。
// 原因：开发中版本曾被无条件标为「正式版」，与 Releases 页不符（会话 26 发现）。
const readmePath = join(root, 'README.md');
const changelogText = readFileSync(src, 'utf8');
const latestVersion = changelogText.match(/^### (v\d+\.\d+\.\d+)/m)?.[1];
if (latestVersion && existsSync(readmePath)) {
    const headingEnd = changelogText.indexOf('\n', changelogText.indexOf('### ' + latestVersion));
    const nextHeading = changelogText.indexOf('\n### v', headingEnd);
    const headBlock = changelogText.slice(headingEnd, nextHeading > 0 ? nextHeading : changelogText.length);
    const inDevelopment = /状态：\s*开发中/.test(headBlock);
    let readme = readFileSync(readmePath, 'utf8');
    const versionLine = `**当前版本**：[${latestVersion}](https://github.com/LuzzyMeow/LuzzyRP/releases/latest) —— 版本历史与各版说明以 [CHANGELOG.md](CHANGELOG.md) 为准（应用内「关于」页同源自动同步）`;
    const badgeLine = inDevelopment
        ? `![Status](https://img.shields.io/badge/Status-${latestVersion}--开发中·未发布-D4A017)`
        : `![Status](https://img.shields.io/badge/Status-${latestVersion}--正式版·可游玩-10B981)`;
    let touched = false;
    if (/^\*\*当前版本\*\*：.*$/m.test(readme)) {
        readme = readme.replace(/^\*\*当前版本\*\*：.*$/m, versionLine);
        touched = true;
    } else {
        console.warn('[gen-changelog] README 缺少「当前版本」行，跳过同步（首次启用请手工补一行）');
    }
    if (/!\[Status\]\(https:\/\/img\.shields\.io\/badge\/Status-v\d+\.\d+\.\d+--.*?\)/.test(readme)) {
        readme = readme.replace(/!\[Status\]\(https:\/\/img\.shields\.io\/badge\/Status-v\d+\.\d+\.\d+--[^)]*?\)/, badgeLine);
        touched = true;
    } else {
        console.warn('[gen-changelog] README 缺少 Status 徽章行，跳过同步');
    }
    if (touched) {
        writeFileSync(readmePath, readme, 'utf8');
        console.log(`[gen-changelog] README 已同步至 ${latestVersion}（当前版本行 + Status 徽章${inDevelopment ? '，开发中' : ''}）`);
    }
}
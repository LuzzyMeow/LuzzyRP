// 设备端端到端验证迁移导出器：把 WebView 导航到 files/ext/luzzy-migrate.html，
// 等它跑完（title = 'migrate:完成'），再把原生侧拼出来的导出文件拉回本机。
//
// 用法：node .workbuddy/mig/run-exporter.mjs [输出路径]
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const PORT = Number(process.env.CDP_PORT || 9222);
const SERIAL = process.env.ANDROID_SERIAL || 'emulator-5554';
const APP = 'file:///data/user/0/com.luzzymeow.luzzyrp/files/rphub/index.html';
const PAGE = 'file:///data/user/0/com.luzzymeow.luzzyrp/files/ext/luzzy-migrate.html?chunk=8000';
const DEVICE_EXPORT = '/data/data/com.luzzymeow.luzzyrp/files/migration/incoming/legacy-export.json';
const DEVICE_MANIFEST = '/data/data/com.luzzymeow.luzzyrp/files/migration/incoming/manifest.json';
const outPath = process.argv[2] || '.workbuddy/mig/device-export.json';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = targets.find((t) => t.type === 'page');
if (!page) { console.error('NO_PAGE', JSON.stringify(targets)); process.exit(1); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
let seq = 0;
const pending = new Map();
const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++seq;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
});
ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) {
        const p = pending.get(m.id); pending.delete(m.id);
        m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result);
    }
};
await send('Page.enable');
await send('Runtime.enable');
const evaluate = async (expression) => {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (r.exceptionDetails) return { __evalError: r.exceptionDetails.exception?.description || r.exceptionDetails.text };
    return r.result?.value;
};

console.log('== 先回主页面（保证 setAllowFileAccess… 的上下文与真实流程一致）');
await send('Page.navigate', { url: APP });
for (let i = 0; i < 30; i++) {
    await sleep(1000);
    const v = await evaluate("JSON.stringify({ready: !!(document.querySelector('#app')||{}).__vue_app__})");
    try { if (JSON.parse(v).ready) break; } catch (e) { /* 载入中 */ }
}

console.log('== 导航到迁移页');
await send('Page.navigate', { url: PAGE });
let last = null;
for (let i = 0; i < 60; i++) {
    await sleep(1000);
    const raw = await evaluate("JSON.stringify({title: document.title, stage: (document.getElementById('stage')||{}).textContent||'', detail: ((document.getElementById('detail')||{}).textContent||'').slice(0,400)})");
    try { last = JSON.parse(raw); } catch (e) { last = { raw }; }
    if (String(last.title || '').startsWith('migrate:完成') || String(last.title || '').startsWith('migrate:失败')) break;
}
console.log('== 迁移页结果');
console.log(JSON.stringify(last, null, 1));

console.log('== 拉取原生落盘结果');
const manifest = execFileSync('adb', ['-s', SERIAL, 'shell', 'cat', DEVICE_MANIFEST], { encoding: 'utf8' });
console.log('manifest:', manifest.trim());
const raw = execFileSync('adb', ['-s', SERIAL, 'exec-out', 'cat', DEVICE_EXPORT], { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
fs.writeFileSync(outPath, raw);
console.log(`导出物 -> ${outPath} (${raw.length} 字符)`);
try {
    const parsed = JSON.parse(raw);
    const main = Object.keys(parsed.databases.RPHubDB.entries);
    const legacy = Object.keys(parsed.databases.SillyTavernDB.entries);
    console.log('解析 OK：主库', main.length, '键 / 旧库', legacy.length, '键');
    console.log('主库版本', parsed.databases.RPHubDB.version, '· 旧库版本', parsed.databases.SillyTavernDB.version);
    console.log('capturedAt', parsed.capturedAt, '· exporter', parsed.exporter);
} catch (e) {
    console.error('解析失败：' + e.message);
}
ws.close();
process.exit(0);

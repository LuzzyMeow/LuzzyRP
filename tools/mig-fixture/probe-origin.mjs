// 2.2 探针（第二版）：分步读 stage，判断卡点
import fs from 'node:fs';

const PORT = Number(process.env.CDP_PORT || 9222);
const APP = 'file:///data/user/0/com.luzzymeow.luzzyrp/files/rphub/index.html';
const PROBE = 'file:///data/user/0/com.luzzymeow.luzzyrp/files/ext/luzzy-origin-probe.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = targets.find((t) => t.type === 'page');
if (!page) { console.error('NO_PAGE'); process.exit(1); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
let seq = 0;
const pending = new Map();
const exceptions = [];
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
        return;
    }
    if (m.method === 'Runtime.exceptionThrown') {
        exceptions.push(JSON.stringify(m.params.exceptionDetails?.exception?.description
            || m.params.exceptionDetails?.text || ''));
    }
};
await send('Page.enable');
await send('Runtime.enable');
const evaluate = async (expression) => {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (r.exceptionDetails) return { __evalError: r.exceptionDetails.exception?.description || r.exceptionDetails.text };
    return r.result?.value;
};

console.log('== navigate to probe');
await send('Page.navigate', { url: PROBE });
const timeline = [];
for (let i = 0; i < 20; i++) {
    await sleep(1500);
    const v = await evaluate("JSON.stringify({href: location.href, title: document.title, probe: window.__probe || null})");
    const parsed = typeof v === 'string' ? JSON.parse(v) : v;
    timeline.push(parsed);
    const stage = parsed.probe && parsed.probe.stage;
    if (stage === 'done' || stage === 'error') break;
}
console.log('== probe timeline (last 3 + first)');
console.log(JSON.stringify(timeline[0], null, 1));
console.log('...');
console.log(JSON.stringify(timeline[timeline.length - 1], null, 1));
console.log('stages seen:', timeline.map((t) => (t.probe && t.probe.stage) || (t.title || '').slice(0, 30)).join(' -> '));
if (exceptions.length) console.log('exceptions:', exceptions.slice(-3));

console.log('== navigate back to app');
await send('Page.navigate', { url: APP });
for (let i = 0; i < 30; i++) {
    await sleep(1000);
    const v = await evaluate("JSON.stringify({ready: !!(document.querySelector('#app')||{}).__vue_app__})");
    try { if (JSON.parse(v).ready) break; } catch (e) { /* loading */ }
}
const after = await evaluate(
    "(async()=>{const q=await new Promise(r=>{const x=indexedDB.open('RPHubDB');x.onsuccess=()=>r(x.result)});const get=(k)=>new Promise(r=>{const y=q.transaction(['store'],'readonly').objectStore('store').get(k);y.onsuccess=()=>r(y.result)});"
    + "const keys=await new Promise(r=>{const y=q.transaction(['store'],'readonly').objectStore('store').getAllKeys();y.onsuccess=()=>r(y.result.map(String))});"
    + "const chars=await get('rp_hub_characters');const probe=await get('rp_hub__probe_from_ext_file');"
    + "return JSON.stringify({keyCount:keys.length, charCount:Array.isArray(chars)?chars.length:null, probeFromExt: probe||null, hasProbeKey: keys.includes('rp_hub__probe_from_ext_file')})})()");
console.log('== after reload:', after);
ws.close();
process.exit(0);

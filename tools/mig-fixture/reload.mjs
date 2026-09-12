// 重新加载页面（清掉页内未决的 IndexedDB 请求队列）
// 用法：node .workbuddy/mig/reload.mjs
const PORT = Number(process.env.CDP_PORT || 9222);
const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = targets.find((t) => t.type === 'page' && t.url.includes('index.html'));
if (!page) { console.error('NO_PAGE'); process.exit(1); }
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
await send('Page.reload', { ignoreCache: false });
console.log('reload requested');
// 等页面真正就绪
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
for (let i = 0; i < 40; i++) {
    await sleep(1000);
    try {
        const r = await send('Runtime.evaluate', {
            expression: "JSON.stringify({ready: !!document.querySelector('#app')?.__vue_app__, title: document.title})",
            returnByValue: true,
        });
        const v = JSON.parse(r.result.value);
        if (v.ready) { console.log('page ready:', JSON.stringify(v)); break; }
    } catch (e) { /* 仍在导航 */ }
}
ws.close();
process.exit(0);

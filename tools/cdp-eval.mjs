// LuzzyRP CDP evaluate 一次性驱动（本地工具，不入构建）
// 用法: node tools/cdp-eval.mjs "<js expression>"
const port = 9222;
const list = await fetch(`http://127.0.0.1:${port}/json`).then(r => r.json());
const page = list.find(t => t.type === 'page' && (t.url.includes('index.html') || t.title !== ''));
if (!page) { console.error('NO_PAGE', JSON.stringify(list)); process.exit(1); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const consoleLogs = [];
const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++seq;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
});
ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
        const p = pending.get(msg.id);
        pending.delete(msg.id);
        msg.error ? p.reject(new Error(JSON.stringify(msg.error))) : p.resolve(msg.result);
        return;
    }
    if (msg.method === 'Runtime.exceptionThrown') {
        consoleLogs.push('[EXCEPTION] ' + JSON.stringify(msg.params.exceptionDetails?.exception?.description || msg.params.exceptionDetails?.text || ''));
    }
    if (msg.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(msg.params.type)) {
        consoleLogs.push(`[${msg.params.type}] ` + msg.params.args.map(a => a.value ?? a.description ?? '').join(' ').slice(0, 300));
    }
};
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
await send('Runtime.enable');
const expression = process.argv[2];
if (!expression) { console.error('usage: node cdp-eval.mjs "<expr>"'); process.exit(1); }
try {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true });
    if (result.exceptionDetails) {
        console.log('EVAL_EXCEPTION:', result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    } else {
        console.log(JSON.stringify(result.result?.value ?? null));
    }
} finally {
    if (consoleLogs.length) console.log('--- console ---\n' + consoleLogs.slice(-8).join('\n'));
    ws.close();
    process.exit(0);
}

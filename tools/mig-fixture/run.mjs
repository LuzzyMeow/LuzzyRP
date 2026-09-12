// 一次性驱动：把 .workbuddy/mig/<name>.js 的内容送进模拟器 WebView 求值（CDP）。
//
// 为什么不用 tools/cdp-eval.mjs：那支从 argv 收表达式，多行脚本在 Git Bash 下引号地狱。
// 本驱动从文件读脚本 → 表达式 = IIFE 包裹的整个文件内容 → 结果 JSON 落盘。
//
// 用法：node .workbuddy/mig/run.mjs <script.js> [out.json]
// 环境变量：CDP_PORT（默认 9222）
import fs from 'node:fs';

const PORT = Number(process.env.CDP_PORT || 9222);
const scriptPath = process.argv[2];
const outPath = process.argv[3];
if (!scriptPath) {
    console.error('usage: node run.mjs <script.js> [out.json]');
    process.exit(1);
}

const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = targets.find((t) => t.type === 'page' && t.url.includes('index.html'));
if (!page) {
    console.error('NO_PAGE', JSON.stringify(targets));
    process.exit(1);
}

const ws = new WebSocket(page.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const logs = [];
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
        logs.push('[EXCEPTION] ' + JSON.stringify(
            msg.params.exceptionDetails?.exception?.description || msg.params.exceptionDetails?.text || ''));
    }
    if (msg.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(msg.params.type)) {
        logs.push(`[${msg.params.type}] ` + msg.params.args
            .map((a) => a.value ?? a.description ?? '').join(' ').slice(0, 400));
    }
};
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
await send('Runtime.enable');

let body = fs.readFileSync(scriptPath, 'utf8');
if (/__DEV_API_KEY__|__STA1N_KEY__/.test(body)) {
    // 密钥只经内存传给页面：不落盘、不打印（配置里读、占位符替换）
    const cfgPath = `${process.env.USERPROFILE}/.zcode/v2/config.json`;
    const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
    const providers = Object.values(cfg.provider || {});
    if (body.includes('__DEV_API_KEY__')) {
        const deep = providers.find((p) => p?.name === 'DeepSeek' && p?.options?.apiKey);
        if (!deep) { console.error('NO_DEV_KEY'); process.exit(1); }
        body = body.split('__DEV_API_KEY__').join(deep.options.apiKey);
    }
    if (body.includes('__STA1N_KEY__')) {
        const st = providers.find((p) => p?.options?.baseURL?.includes('sta1n') && p?.options?.apiKey);
        if (!st) { console.error('NO_STA1N_KEY'); process.exit(1); }
        body = body.split('__STA1N_KEY__').join(st.options.apiKey);
    }
    console.error('(keys injected)');
}
const expression = `(async () => {\n${body}\n})()`;

let failed = false;
try {
    const result = await send('Runtime.evaluate', {
        expression,
        awaitPromise: true,
        returnByValue: true,
        userGesture: true,
    });
    if (result.exceptionDetails) {
        failed = true;
        console.error('EVAL_EXCEPTION:',
            result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    } else {
        const value = result.result?.value ?? null;
        const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
        if (outPath) {
            fs.writeFileSync(outPath, text ?? 'null');
            console.log(`OK -> ${outPath} (${(text ?? '').length} bytes)`);
        } else {
            console.log(text);
        }
    }
} finally {
    if (logs.length) console.error('--- console ---\n' + logs.slice(-10).join('\n'));
    ws.close();
}
process.exit(failed ? 1 : 0);

// LuzzyRP · KV / prompt 前缀缓存回归门禁（v2.0 · patch 047 的守卫）
//
// 背景：聊天侧每轮把 messages **整体重建**。上游在重建时对「已经在上下文里的旧消息」
// 做了若干**逐轮变形**（把检索提醒挂到最新 user 消息、把 thinking 回填做成滑动窗口、
// 世界书按「距尾深度」插入……）。服务端的 prompt/KV 前缀缓存按**最长公共前缀**匹配，
// 任何一处旧消息被改写都会让其后整段缓存失效。
//
// 本门禁断言的是**缓存友好的充要形态**：
//   上一轮请求的 messages 必须逐字节成为下一轮请求 messages 的**前缀**（纯追加）。
// 断在哪儿、为什么断，由本脚本的 diff 报告直接指出（不只看比例）。
//
// 用法：
//   1) 起 headless Chrome：chrome --headless=new --remote-debugging-port=9347
//        --user-data-dir=%TEMP%\luzzy-prof-gate about:blank
//   2) node tools/prefix-cache-test.cjs
//   可选环境变量：CDP_PORT（默认 9347）、APP_URL、TURNS（默认 6）、
//                 THRESHOLD（公共前缀占比阈值，默认 0.99）、EXPLORE=1（只打印不判定）
//
// 断言（任一不满足即 FAIL，退出码 1）：
//   A1 观测层就绪（Luzzy.prefixGuard.ready()）
//   A2 system 在全部轮次中逐字节不变（含是否含时钟值）
//   A3 tools 在全部轮次中逐字节不变
//   A4 相邻两轮的 messages 公共前缀占比 ≥ THRESHOLD（**纯追加**）
//   A5 相邻两轮**没有任何已存在的消息被改写**（逐条比对，给出首个被改写消息的下标与字段）
//   A6 工具轮（depth 0 → depth 1）同样满足纯追加
//   A7 负控：重新打开「检索提醒挂最新 user 消息」后 A5 **必须**判红（证明本门禁有牙齿）
//   A8 无 JS 异常
const CDP_PORT = Number(process.env.CDP_PORT || 9347);
const APP_URL = process.env.APP_URL
    || 'file:///D:/.NekoTool/LuzzyRP/app/src/main/assets/rphub/index.html';
const TURNS = Number(process.env.TURNS || 6);
const THRESHOLD = Number(process.env.THRESHOLD || 0.99);
const EXPLORE = process.env.EXPLORE === '1';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitTargets() {
    for (let i = 0; i < 40; i++) {
        try {
            const list = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)).json();
            const page = list.find((t) => t.type === 'page');
            if (page) return page;
        } catch (e) { /* 未就绪 */ }
        await sleep(250);
    }
    throw new Error('CDP target 未就绪');
}

// 页面启动前注入：记录所有生成请求；对生成类请求返回脚本化 SSE（全程不联网）
const STUB = `
(function () {
  window.__REQ_LOG = [];
  window.__SSE_TEXT = '好的。';
  var orig = window.fetch;
  window.fetch = function (input, init) {
    var url = String((typeof input === 'string') ? input : (input && input.url) || '');
    var body = init && init.body;
    var isGen = /chat\\/completions/.test(url) && !/\\/api\\/log\\//.test(url);
    if (isGen) {
      window.__REQ_LOG.push({ url: url.slice(0, 120), body: typeof body === 'string' ? body : String(body) });
      var out = 'data: ' + JSON.stringify({ id: 'c1', choices: [{ index: 0, delta: { content: String(window.__SSE_TEXT || 'ok') }, finish_reason: null }] }) + '\\n\\n'
              + 'data: ' + JSON.stringify({ id: 'c1', choices: [{ index: 0, delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 100, completion_tokens: 4, prompt_tokens_details: { cached_tokens: 64 } } }) + '\\n\\n'
              + 'data: [DONE]\\n\\n';
      return Promise.resolve(new Response(out, { status: 200, headers: { 'content-type': 'text/event-stream' } }));
    }
    return orig.apply(this, arguments);
  };
})();
`;

const PROXY = `(() => { const el = document.querySelector('#app'); const a = el && el.__vue_app__;
    const i = a && ((a._container && a._container._vnode && a._container._vnode.component) || a._instance); return i && i.proxy; })()`;

async function main() {
    const page = await waitTargets();
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

    let msgId = 0;
    const pending = new Map();
    const exceptions = [];
    ws.onmessage = (ev) => {
        const msg = JSON.parse(ev.data);
        if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); return; }
        if (msg.method === 'Runtime.exceptionThrown') {
            const d = msg.params.exceptionDetails;
            exceptions.push(`${d.lineNumber} ${(d.exception?.description || d.text || '').split('\n')[0].slice(0, 160)}`);
        }
    };
    const send = (method, params = {}) => new Promise((res) => {
        const id = ++msgId;
        pending.set(id, res);
        ws.send(JSON.stringify({ id, method, params }));
    });
    const evalJs = async (expr) => {
        const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, timeout: 300000 });
        if (r.result?.exceptionDetails) {
            return { __error: (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text || '').slice(0, 400) };
        }
        return r.result?.result?.value;
    };

    const checks = {};
    const failures = [];
    const record = (id, ok, info) => {
        checks[id] = Object.assign({ pass: !!ok }, info || {});
        if (!ok) failures.push(id + (info && info.why ? ': ' + info.why : ''));
    };

    await send('Runtime.enable');
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 3.25, mobile: true });
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
    await send('Page.addScriptToEvaluateOnNewDocument', { source: STUB });

    // ---------- 干净起点：清掉上次运行残留的 IndexedDB / localStorage ----------
    // （桌面 profile 在多次运行之间是持久的；不清会把上一轮的会话带进来，测量失去意义）
    await send('Page.navigate', { url: APP_URL });
    await sleep(6000);
    const cleared = await evalJs(`(async () => {
        try { localStorage.clear(); } catch (e) {}
        const names = ['RPHubDB'];
        for (const n of names) { try { indexedDB.deleteDatabase(n); } catch (e) {} }
        await new Promise(r => setTimeout(r, 500));
        return { ok: true };
    })()`);

    const boot = async () => {
        await send('Page.navigate', { url: APP_URL });
        await sleep(9000);
        await evalJs(`(() => { const b = document.querySelector('.lsp-dive-btn'); if (b) b.click(); return !!b; })()`);
        await sleep(2500);
    };
    await boot();

    // ---------- 建立最小可发送状态 ----------
    const setup = await evalJs(`(async () => {
        const p = ${PROXY};
        if (!p) return { __error: 'no proxy' };
        const R = {};
        const inp = [...document.querySelectorAll('input')]
            .filter((i) => i.type !== 'file' && i.type !== 'checkbox' && i.type !== 'range')
            .find((i) => i.placeholder && /称呼|名字|昵称/.test(i.placeholder));
        if (inp) { inp.value = '门禁'; inp.dispatchEvent(new Event('input', { bubbles: true })); await new Promise(r => setTimeout(r, 250)); }
        const go = [...document.querySelectorAll('button')].find((b) => /保存并开始/.test(b.textContent.trim()));
        if (go) { go.click(); await new Promise(r => setTimeout(r, 1500)); }
        p.settings.apiProviders = [{ id: 'gate', name: '门禁商', apiUrl: 'https://gate.local/v1', protocol: 'openai',
            models: [{ id: 'gate-model', label: '门禁模型', contextLength: 128000, maxOutput: 8192 }] }];
        p.settings.apiProviderKeys = { gate: 'sk-gate' };
        p.settings.apiProviderId = 'gate';
        p.settings.apiUrl = 'https://gate.local/v1';
        p.settings.apiKey = 'sk-gate';
        p.settings.customApiUrl = 'https://gate.local/v1';
        p.settings.model = 'gate-model';
        p.settings.stream = true;
        if (Array.isArray(p.activeTools)) p.activeTools = p.activeTools.map(t => Object.assign({}, t, { enabled: true }));
        await new Promise(r => setTimeout(r, 500));
        p.createNewCharacter();
        await new Promise(r => setTimeout(r, 400));
        p.saveCharacter();
        await new Promise(r => setTimeout(r, 700));
        R.charCount = (p.characters || []).length;
        if (R.charCount > 0) { await p.selectCharacter(0, false); await new Promise(r => setTimeout(r, 1500)); }
        R.enabledTools = Array.isArray(p.activeTools) ? p.activeTools.filter(t => t && t.enabled).length : null;
        return R;
    })()`);
    if (!setup || setup.__error) {
        record('A0-setup', false, { why: setup && setup.__error });
        console.log(JSON.stringify({ checks, failures, exceptions }, null, 1));
        ws.close();
        process.exit(1);
    }

    // ---------- 顺序跑 N 轮真实发送 ----------
    const turnErrors = [];
    for (let i = 1; i <= TURNS; i++) {
        const r = await evalJs(`(async () => {
            const p = ${PROXY};
            p.userInput = '第${i}轮用户输入';
            try { await p.sendMessage(); } catch (e) { return { err: String(e && e.message || e).slice(0, 200) }; }
            await new Promise(r => setTimeout(r, 700));
            return { chatLen: (p.chatHistory || []).length, isGen: p.isGenerating };
        })()`);
        if (r && r.err) turnErrors.push(`turn${i}: ${r.err}`);
    }

    // ---------- 采集 + 逐字段 diff ----------
    const analysis = await evalJs(`(() => {
        const log = window.__REQ_LOG || [];
        const parse = (s) => { try { return JSON.parse(s); } catch (e) { return null; } };
        const bodies = log.map((r) => parse(r.body)).filter(Boolean);
        const sysOf = (b) => {
            const m = b.messages || [];
            return (typeof b.system === 'string') ? b.system
                : (m[0] && m[0].role === 'system' ? String(m[0].content) : '');
        };
        const sysHashes = bodies.map((b) => { const s = sysOf(b); let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return h; });
        const sysLens = bodies.map((b) => sysOf(b).length);
        const sysHasClock = bodies.map((b) => /\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}/.test(sysOf(b)));
        // 工具列：统一用「name + 参数体」的稳定序列化比对（忽略键序）
        const stable = (v) => {
            if (v === null || typeof v !== 'object') return JSON.stringify(v);
            if (Array.isArray(v)) return '[' + v.map(stable).join(',') + ']';
            return '{' + Object.keys(v).sort().map((k) => JSON.stringify(k) + ':' + stable(v[k])).join(',') + '}';
        };
        const toolSigs = bodies.map((b) => stable(b.tools || null));
        const msgCounts = bodies.map((b) => (b.messages || []).length);

        // 相邻两轮：公共前缀 + 首个被改写的旧消息
        const pairs = [];
        for (let i = 1; i < bodies.length; i++) {
            const a = bodies[i - 1].messages || [];
            const b = bodies[i].messages || [];
            const ka = a.map((m) => stable(m));
            const kb = b.map((m) => stable(m));
            let common = 0;
            const n = Math.min(ka.length, kb.length);
            while (common < n && ka[common] === kb[common]) common++;
            const rewritten = [];
            for (let k = 0; k < n; k++) {
                if (ka[k] !== kb[k]) {
                    const fieldDiff = [];
                    const oa = a[k] || {}, ob = b[k] || {};
                    for (const key of new Set([...Object.keys(oa), ...Object.keys(ob)])) {
                        if (stable(oa[key]) !== stable(ob[key])) {
                            fieldDiff.push({ key,
                                from: String(stable(oa[key])).slice(0, 60),
                                to: String(stable(ob[key])).slice(0, 60) });
                        }
                    }
                    rewritten.push({ index: k, role: (a[k] || {}).role || null, fields: fieldDiff });
                }
            }
            pairs.push({
                pair: (i - 1) + '->' + i,
                prevCount: ka.length, nextCount: kb.length,
                commonMessages: common,
                commonRatioByCount: ka.length > 0 ? +(common / ka.length).toFixed(4) : null,
                appended: kb.length - common,
                rewrittenCount: rewritten.length,
                firstRewritten: rewritten.slice(0, 3),
            });
        }
        return { requestCount: bodies.length, sysHashes, sysLens, sysHasClock, toolSigs, msgCounts, pairs };
    })()`);

    const guardInfo = await evalJs(`(() => { const g = window.Luzzy && window.Luzzy.prefixGuard;
        return g ? { ready: g.ready(), stats: g.stats() } : null; })()`);

    // ---------- 判定 ----------
    const ok0 = !!(guardInfo && guardInfo.ready);
    record('A1-guard-ready', ok0, { why: ok0 ? undefined : 'Luzzy.prefixGuard 未就绪' });

    const a = analysis || {};
    const sysStable = Array.isArray(a.sysHashes) && a.sysHashes.length > 1
        && new Set(a.sysHashes).size === 1;
    const sysNoClock = Array.isArray(a.sysHasClock) && a.sysHasClock.every((v) => v === false);
    record('A2-system-byte-stable', sysStable && sysNoClock,
        { hashes: a.sysHashes, lens: a.sysLens, clock: a.sysHasClock,
          why: !sysStable ? 'system 在轮次间发生了变化' : (!sysNoClock ? 'system 里出现了时钟值' : undefined) });

    const toolsStable = Array.isArray(a.toolSigs) && a.toolSigs.length > 1
        && new Set(a.toolSigs).size === 1;
    record('A3-tools-byte-stable', toolsStable,
        { distinct: Array.isArray(a.toolSigs) ? new Set(a.toolSigs).size : null,
          why: toolsStable ? undefined : 'tools 在轮次间发生了变化' });

    const userPairs = (a.pairs || []).filter((p) => p.nextCount - p.prevCount >= 2);
    const minRatio = userPairs.length ? Math.min(...userPairs.map((p) => p.commonRatioByCount)) : null;
    record('A4-append-only-prefix', minRatio !== null && minRatio >= THRESHOLD,
        { threshold: THRESHOLD, minRatio, pairs: (a.pairs || []).map((p) => p.commonRatioByCount),
          why: minRatio === null ? '没有可比对的轮次对' : (minRatio < THRESHOLD ? ('最低公共前缀 ' + minRatio + ' < ' + THRESHOLD) : undefined) });

    const worstRewrite = (a.pairs || []).reduce((acc, p) => Math.max(acc, p.rewrittenCount), 0);
    record('A5-no-history-rewrite', worstRewrite === 0,
        { worstRewriteCount: worstRewrite,
          detail: (a.pairs || []).filter((p) => p.rewrittenCount > 0).slice(0, 2),
          why: worstRewrite > 0 ? ('有 ' + worstRewrite + ' 条已存在的消息被改写（见 detail）') : undefined });

    record('A6-turns-completed', turnErrors.length === 0 && Array.isArray(a.msgCounts) && a.msgCounts.length >= Math.min(TURNS, 2),
        { turnErrors, requestCount: a.requestCount, msgCounts: a.msgCounts });

    record('A8-no-exceptions', exceptions.length === 0, { exceptions: exceptions.slice(0, 5) });

    // ---------- A7 负控：证明门禁有牙齿 ----------
    // 在页面内直接把「检索提醒挂到最新 user 消息」的行为放回来，重跑 2 轮，
    // 断言 A5 的判据（已存在消息被改写）**必须**变成「有改写」。
    const negative = await evalJs(`(async () => {
        const p = ${PROXY};
        window.__REQ_LOG = [];
        // 复刻 6459 行被停用的行为：把提醒文本追加到最新一条 user 消息上
        const reminders = ['正式回复前必须先调用至少 1 个最相关的检索工具，收到 tool 结果后再回答。'];
        const msgs = p.chatHistory;
        if (Array.isArray(msgs)) {
            for (let i = msgs.length - 1; i >= 0; i--) {
                if (msgs[i] && msgs[i].role === 'user' && String(msgs[i].content || '').trim()) {
                    msgs[i].content = String(msgs[i].content) + '\\n' + reminders[0];
                    break;
                }
            }
        }
        p.userInput = '负控轮';
        try { await p.sendMessage(); } catch (e) { return { err: String(e && e.message || e).slice(0, 200) }; }
        await new Promise(r => setTimeout(r, 800));
        return { ok: true };
    })()`);
    const negCheck = await evalJs(`(() => {
        const log = window.__REQ_LOG || [];
        const b = log.map((r) => { try { return JSON.parse(r.body); } catch (e) { return null; } }).filter(Boolean);
        if (b.length < 1) return { rewrote: false, note: '负控轮没有发出请求' };
        // 上一批请求的最后一条 body 作为「前一轮」，与之比对 messages 前缀里的旧消息
        const stable = (v) => {
            if (v === null || typeof v !== 'object') return JSON.stringify(v);
            if (Array.isArray(v)) return '[' + v.map(stable).join(',') + ']';
            return '{' + Object.keys(v).sort().map((k) => JSON.stringify(k) + ':' + stable(v[k])).join(',') + '}';
        };
        const cur = b[b.length - 1].messages || [];
        const withRem = cur.filter((m) => m.role === 'user' && /正式回复前必须先调用至少 1 个最相关的检索工具/.test(String(m.content || '')));
        return { rewrote: withRem.length > 0, matches: withRem.length, totalMsgs: cur.length };
    })()`);
    const negHasTeeth = !!(negCheck && negCheck.rewrote) || (negative && negative.err);
    record('A7-negative-control', negHasTeeth,
        { negCheck, negErr: negative && negative.err,
          why: negHasTeeth ? undefined : '负控未能制造「旧消息被改写」，门禁可能没有牙齿' });

    const report = {
        mode: EXPLORE ? 'explore' : 'gate',
        setup, guardInfo, analysis, turnErrors,
        checks, failures, exceptions: exceptions.slice(0, 8),
    };
    console.log(JSON.stringify(report, null, 1));
    ws.close();
    if (!EXPLORE && failures.length) process.exit(1);
    process.exit(0);
}

main().catch((e) => { console.error('PREFIX-CACHE GATE ERROR:', e.message); process.exit(1); });

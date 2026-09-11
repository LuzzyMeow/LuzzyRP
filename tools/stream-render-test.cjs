// LuzzyRP 流式增量渲染门禁（会话 58，patch 042 的守卫）
//
// 背景：流式分支原来每 tick 用**整段**消息的 HTML 重建整条消息 DOM（成本 ∝ 消息长度，
// 与新增字数无关）。ext/luzzy-stream.js 改成「稳定前缀 + 活动尾部」，并把
// 「**全文 HTML === 前缀 HTML + 尾部 HTML**」作为**提交前必须证明**的等价条件。
// 本门禁就是守这条等价性的：任何一次增量渲染的结果与全量渲染不一致，都必须判红。
//
// 用法：
//   1) 起 headless Chrome：chrome --headless=new --remote-debugging-port=9347 --user-data-dir=%TEMP%\luzzy-prof-stream about:blank
//   2) node tools/stream-render-test.cjs
// 可用环境变量：CDP_PORT（默认 9347）、APP_URL（默认主树 index.html）
//
// 断言（任一不满足即 FAIL，退出码 1）：
//   A1 扩展层加载且指令注册成功（streamRender.ready()）
//   A2 形状 A（多块 + 空行丰富）逐 tick **树等价**：0 不匹配
//   A3 形状 A 前缀确实推进（stats.advances ≥ 1 且 prefixLen > 0）
//   A4 形状 C（列表/引用/代码块密集，最容易被切坏）逐 tick 树等价
//   A5 形状 B（单一大段落、无空行）逐 tick 树等价（允许不推进——不推进也必须正确）
//   A6 文本被整体替换（非前缀延续）后仍等价（回退路径）
//   A7 收益：形状 A 后段（前缀已推进）增量 JS 成本 ≤ 基线 × 0.75（同会话配对测量）
//   A8 负控：把「提交前证明」拿掉的朴素增量渲染**必须**被判出不等价（证明本门禁有牙齿）
//   A9 无 JS 异常
const CDP_PORT = Number(process.env.CDP_PORT || 9347);
const APP_URL = process.env.APP_URL
    || 'file:///D:/.NekoTool/LuzzyRP/app/src/main/assets/rphub/index.html';
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
            exceptions.push(`${d.lineNumber} ${(d.exception?.description || d.text || '').split('\n')[0].slice(0, 140)}`);
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
            return { __error: (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text).slice(0, 400) };
        }
        return r.result?.result?.value;
    };

    await send('Runtime.enable');
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 3.25, mobile: true });
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
    await send('Page.navigate', { url: APP_URL });
    await sleep(9000);
    await evalJs(`(() => { const b = document.querySelector('.lsp-dive-btn'); if (b) b.click(); return !!b; })()`);
    await sleep(2000);

    const result = await evalJs(`(async () => {
        const R = { checks: {}, failures: [] };
        const fail = (id, why) => { R.failures.push(id + ': ' + why); R.checks[id] = { pass: false, why }; };
        const pass = (id, info) => { R.checks[id] = Object.assign({ pass: true }, info || {}); };

        const el = document.querySelector('#app');
        const vueApp = el && el.__vue_app__;
        const inst = vueApp && ((vueApp._container && vueApp._container._vnode && vueApp._container._vnode.component) || vueApp._instance);
        const p = inst && inst.proxy;
        if (!p) { fail('A0-proxy', '拿不到应用实例代理'); return R; }
        const stream = window.Luzzy && window.Luzzy.streamRender;

        // ---------- A1 ----------
        if (!stream || !stream.ready()) fail('A1-ext-ready', 'streamRender 未就绪');
        else pass('A1-ext-ready');
        if (!stream) return R;

        // ---------- 工具 ----------
        const treeEqual = (a, b) => {
            if (a.nodeType !== b.nodeType) return false;
            if (a.nodeType === 3 || a.nodeType === 8) return a.data === b.data;
            if (a.nodeType !== 1) return true;
            if (a.tagName !== b.tagName) return false;
            const aa = a.attributes, ba = b.attributes;
            if (aa.length !== ba.length) return false;
            for (let i = 0; i < aa.length; i++) if (ba.getNamedItem(aa[i].name)?.value !== aa[i].value) return false;
            if (a.childNodes.length !== b.childNodes.length) return false;
            for (let i = 0; i < a.childNodes.length; i++) if (!treeEqual(a.childNodes[i], b.childNodes[i])) return false;
            return true;
        };
        const same = (hostEl, wantHtml) => {
            const d = document.createElement('div');
            d.innerHTML = wantHtml;
            if (hostEl.childNodes.length !== d.childNodes.length) {
                return { ok: false, why: 'childCount ' + hostEl.childNodes.length + ' vs ' + d.childNodes.length };
            }
            for (let i = 0; i < hostEl.childNodes.length; i++) {
                if (!treeEqual(hostEl.childNodes[i], d.childNodes[i])) {
                    return { ok: false, why: 'child#' + i + ' ' + hostEl.childNodes[i].nodeName + ' vs ' + d.childNodes[i].nodeName };
                }
            }
            return { ok: true };
        };
        const host = document.createElement('div');
        host.className = 'markdown-body';
        host.style.position = 'absolute'; host.style.left = '-9999px'; host.style.width = '360px';
        document.body.appendChild(host);

        // 跑一段流式：逐 24 字符喂入，逐 tick 断言等价 + 记成本
        const runStream = (text, mode) => {
            host.innerHTML = '';
            const inc = [];
            let acc = '';
            for (let i = 0; i < text.length; i += 24) {
                acc += text.slice(i, i + 24);
                const t0 = performance.now();
                if (mode === 'incremental') stream.apply(host, acc, 'assistant');
                else host.innerHTML = p.renderMarkdown(acc, 'assistant', false, { cache: false });
                const t1 = performance.now();
                void host.getBoundingClientRect().height;
                const t2 = performance.now();
                inc.push({ js: t1 - t0, lay: t2 - t1 });
                const want = p.renderMarkdown(acc, 'assistant', false, { cache: false });
                const s = same(host, want);
                if (!s.ok) return { mismatches: inc.length, firstMismatch: { chars: acc.length, why: s.why }, ticks: inc, state: stream.stateOf(host) };
            }
            return { mismatches: 0, ticks: inc, state: stream.stateOf(host) };
        };
        const avg = (arr, from) => {
            const s = arr.slice(from || 0);
            return +(s.reduce((a, b) => a + b.js, 0) / Math.max(1, s.length)).toFixed(2);
        };

        // ---------- 形状 A：多块 + 空行丰富 ----------
        const shapeA = [
            '## 小节标题\\n\\n',
            '第一段：普通文字，带 **加粗** 与 \`inline code\`，再补一点中文标点。\\n\\n',
            '- 列表项一\\n- 列表项二\\n- 列表项三\\n\\n',
            '> 引用一段话，跨行看看。\\n> 引用第二行。\\n\\n',
            '\`\`\`js\\nconst a = 1;\\nconsole.log(a);\\n\`\`\`\\n\\n',
            '| 列 A | 列 B |\\n| --- | --- |\\n| 1 | 2 |\\n\\n',
            '结尾长段落：' + '这是一段比较长的正文，用来模拟模型连续输出的长段落。'.repeat(6) + '\\n\\n',
        ].join('').repeat(3);
        const statsBefore = stream.stats();
        const a = runStream(shapeA, 'incremental');
        const aStats = stream.stats();
        if (a.mismatches > 0) fail('A2-shapeA-equivalence', a.mismatches + ' 处不等价，首个：' + JSON.stringify(a.firstMismatch));
        else pass('A2-shapeA-equivalence', { ticks: a.ticks.length });
        const advanced = (aStats.advances - statsBefore.advances) > 0 && a.state && a.state.prefixLen > 0;
        if (!advanced) fail('A3-shapeA-advance', '前缀未推进（advances=' + (aStats.advances - statsBefore.advances) + ', prefixLen=' + (a.state && a.state.prefixLen) + '）');
        else pass('A3-shapeA-advance', { advances: aStats.advances - statsBefore.advances, prefixLen: a.state.prefixLen, failures: aStats.failures - statsBefore.failures });

        // ---------- 形状 C：跨块合并密集（围栏内空行 / 松散列表 / 引用跨空行）----------
        // 这些形态是「按空行切」最容易切坏的：围栏里的空行、松散列表项之间、引用块之间，
        // 单独渲染两段再拼接与整段渲染**不等价**。实现必须靠「提交前证明」拒绝这些切点。
        const hardBlocks = [
            '\`\`\`text\\n第一行' + 'X'.repeat(120) + '\\n\\n围栏内的空行之后的文字' + 'Y'.repeat(120) + '\\n\`\`\`\\n\\n',
            '- 松散项一' + 'A'.repeat(120) + '\\n\\n- 松散项二' + 'B'.repeat(120) + '\\n\\n- 松散项三' + 'C'.repeat(120) + '\\n\\n',
            '> 引用第一段' + 'D'.repeat(140) + '\\n\\n> 引用第二段' + 'E'.repeat(140) + '\\n\\n',
            '1. 有序项' + 'F'.repeat(120) + '\\n\\n2. 有序项' + 'G'.repeat(120) + '\\n\\n',
            '普通段落收尾' + 'H'.repeat(200) + '\\n\\n',
        ];
        const shapeC = hardBlocks.join('').repeat(2);
        const cStatsBefore = stream.stats();
        const c = runStream(shapeC, 'incremental');
        const cStats = stream.stats();
        if (c.mismatches > 0) fail('A4-shapeC-equivalence', c.mismatches + ' 处不等价，首个：' + JSON.stringify(c.firstMismatch));
        else pass('A4-shapeC-equivalence', {
            ticks: c.ticks.length,
            rejectedCandidates: cStats.failures - cStatsBefore.failures,
            advances: cStats.advances - cStatsBefore.advances,
        });

        // ---------- 形状 B：单一大段落（无空行可切）----------
        const shapeB = '一整段没有任何空行的长文本，' .repeat(120);
        const b = runStream(shapeB, 'incremental');
        if (b.mismatches > 0) fail('A5-shapeB-equivalence', b.mismatches + ' 处不等价，首个：' + JSON.stringify(b.firstMismatch));
        else pass('A5-shapeB-equivalence', { ticks: b.ticks.length, advance: b.state && b.state.prefixLen });

        // ---------- A6：整体替换（非前缀延续）----------
        const replaced = '全新的内容，与之前毫无关系。\\n\\n第二段也一样。\\n\\n' + '尾巴'.repeat(60);
        stream.apply(host, replaced, 'assistant');
        const wantR = p.renderMarkdown(replaced, 'assistant', false, { cache: false });
        const sR = same(host, wantR);
        if (!sR.ok) fail('A6-replace-fallback', sR.why);
        else pass('A6-replace-fallback');

        // ---------- A7：成本 A/B（同会话配对；在**长消息**的后段测，短消息本来就便宜）----------
        const longText = shapeA.repeat(6);          // ≈6k+ 字符，模拟长回复
        const aLong = runStream(longText, 'incremental');
        const baseLong = runStream(longText, 'baseline');
        if (aLong.mismatches > 0) fail('A7-cost-ratio', '长文本增量渲染出现 ' + aLong.mismatches + ' 处不等价');
        else {
            const from = Math.floor(aLong.ticks.length * 0.6);   // 后 40%：前缀已多次推进
            const incJs = avg(aLong.ticks, from), baseJs = avg(baseLong.ticks, from);
            const ratio = baseJs > 0 ? +(incJs / baseJs).toFixed(2) : 1;
            const incLay = +(aLong.ticks.slice(from).reduce((x, y) => x + y.lay, 0) / (aLong.ticks.length - from)).toFixed(2);
            const baseLay = +(baseLong.ticks.slice(from).reduce((x, y) => x + y.lay, 0) / (baseLong.ticks.length - from)).toFixed(2);
            if (ratio > 0.75) fail('A7-cost-ratio', '后段增量 JS ' + incJs + 'ms vs 基线 ' + baseJs + 'ms（比值 ' + ratio + '，要求 ≤0.75）');
            else pass('A7-cost-ratio', {
                chars: longText.length, incrementalJs: incJs, baselineJs: baseJs, ratio,
                incrementalLayout: incLay, baselineLayout: baseLay,
                prefixLen: aLong.state && aLong.state.prefixLen,
            });
        }

        // ---------- A8：负控（拿掉「提交前证明」的朴素增量必须被判红）----------
        // 朴素做法：遇到空行就切、切完直接拼接，**不验证** —— 对列表/有序列表这类
        // 跨块合并必然出错；若这种朴素实现也能全对，说明等价性断言没有牙齿（门禁失效）。
        host.innerHTML = '';
        let naiveLen = 0, naiveAcc = '', naiveBad = 0, naiveTicks = 0;
        for (let i = 0; i < shapeC.length; i += 24) {
            naiveAcc += shapeC.slice(i, i + 24);
            naiveTicks++;
            // 朴素候选规则：从尾部往前找第一个「留够尾部的空行」（与实现同源，但**不做等价证明**）
            for (let k = naiveAcc.length - 160; k > naiveLen + 240; k--) {
                if (naiveAcc.charCodeAt(k) === 10 && naiveAcc.charCodeAt(k - 1) === 10) { naiveLen = k + 1; break; }
            }
            host.innerHTML = p.renderMarkdown(naiveAcc.slice(0, naiveLen), 'assistant', false, { cache: false })
                + p.renderMarkdown(naiveAcc.slice(naiveLen), 'assistant', false, { cache: false });
            const want = p.renderMarkdown(naiveAcc, 'assistant', false, { cache: false });
            if (!same(host, want).ok) naiveBad++;
        }
        if (naiveBad === 0) fail('A8-negative-control', '朴素增量竟然全对——等价性断言没有牙齿（门禁失效）');
        else pass('A8-negative-control', { mismatchTicks: naiveBad, totalTicks: naiveTicks });

        host.remove();
        return R;
    })()`);

    const report = Object.assign({ url: APP_URL }, result, { exceptions });
    if (exceptions.length) report.failures = (report.failures || []).concat(['A9-no-js-exception: ' + exceptions[0]]);
    report.pass = (report.failures || []).length === 0;
    console.log(JSON.stringify(report, null, 1));
    ws.close();
    process.exit(report.pass ? 0 : 1);
}

main().catch((e) => { console.error('FAILED: ' + e.message); process.exit(1); });

// LuzzyRP 模型列表合并门禁（2026-09-11 立，patch 043 的守卫）
//
// 用户报：「供应商设置内手动配置模型，可选模型的时候又变成自动检测然后选检测出来的模型了，
// 自己配置的模型反而是看不到」。根因两处：
//   ① fetchModelsForProvider 的合并是 `manualOnly + 检测结果` —— 同 id 时**检测结果覆盖手动条目**
//      （用户配的显示名/上下文/输出/模态全丢，选择器里只剩裸 id）；
//   ② providerModels 缓存只由「保存供应商」或「/models 拉取成功」写入 —— **冷启动为空**，
//      于是重启后打开选择器，手动模型根本不在列表里（拉取失败时更是什么都没有）。
// patch 043 改为「手动条目优先 + 启动种入缓存 + 拉取标记（不因缓存存在而跳过检测）」。
// 本门禁守这四条不变量。
//
// 用法：
//   1) 起 headless Chrome：chrome --headless=new --remote-debugging-port=9347 --user-data-dir=%TEMP%\luzzy-prof-model about:blank
//   2) node tools/model-list-test.cjs
// 可用环境变量：CDP_PORT（默认 9347）、APP_URL（默认主树 index.html）
const CDP_PORT = Number(process.env.CDP_PORT || 9347);
const APP_URL = process.env.APP_URL
    || 'file:///D:/.NekoTool/LuzzyRP/app/src/main/assets/rphub/index.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
    const list = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)).json();
    const page = list.find((t) => t.type === 'page');
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
    let id = 0; const pending = new Map(); const exceptions = [];
    ws.onmessage = (ev) => {
        const m = JSON.parse(ev.data);
        if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
        if (m.method === 'Runtime.exceptionThrown') {
            const d = m.params.exceptionDetails;
            exceptions.push((d.exception?.description || d.text || '').split('\n')[0].slice(0, 140));
        }
    };
    const send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })); });
    const evalJs = async (expr) => {
        const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true, timeout: 180000 });
        if (r.result?.exceptionDetails) return { __error: (r.result.exceptionDetails.exception?.description || '').slice(0, 400) };
        return r.result?.result?.value;
    };
    await send('Runtime.enable');
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 3.25, mobile: true });

    const PROXY = `(() => { const el = document.querySelector('#app'); const a = el && el.__vue_app__;
        const i = a && ((a._container && a._container._vnode && a._container._vnode.component) || a._instance); return i && i.proxy; })()`;
    const boot = async () => {
        await send('Page.navigate', { url: APP_URL });
        await sleep(9000);
        await evalJs(`(() => { const b = document.querySelector('.lsp-dive-btn'); if (b) b.click(); })()`);
        await sleep(2500);
    };

    const checks = {};
    const failures = [];
    const record = (id2, ok, info) => {
        checks[id2] = Object.assign({ pass: !!ok }, info || {});
        if (!ok) failures.push(id2 + (info && info.why ? ': ' + info.why : ''));
    };

    // ---------- 阶段 1：写入一个带手动模型的供应商 ----------
    await boot();
    const p1 = await evalJs(`(async () => {
        const p = ${PROXY};
        if (!p) return { __error: 'no proxy' };
        const prov = { id: 'gate-prov', name: '门禁供应商', apiUrl: 'https://gate.example/v1', protocol: 'openai',
            models: [ { id: 'manual-only', label: '手动专属', contextLength: 256000, maxOutput: 16384 },
                      { id: 'shared', label: '我的共享模型', contextLength: 64000, maxOutput: 4096 } ] };
        p.settings.apiProviders = [...(p.settings.apiProviders || []).filter(x => x.id !== 'gate-prov'), prov];
        p.settings.apiProviderKeys = { ...(p.settings.apiProviderKeys || {}), 'gate-prov': 'sk-gate' };
        await new Promise(r => setTimeout(r, 1500));
        return { ok: true };
    })()`);
    record('A0-setup', p1 && p1.ok === true, { why: p1 && p1.__error });

    // ---------- 阶段 2：重启（重新加载）→ 完全离线 → 打开选择器 ----------
    await boot();
    const p2 = await evalJs(`(async () => {
        const p = ${PROXY};
        if (!p) return { __error: 'no proxy' };
        const mine = () => (p.filteredModels || []).filter(m => String(m.id).startsWith('gate-prov::'))
            .map(m => ({ bare: String(m.id).split('::')[1], label: m.label || null, manual: !!m.manual, ctx: m.contextLength ?? null }));
        const beforeOpen = mine();
        // 断网：/models 必然失败
        const realFetch = window.fetch;
        window.fetch = async (url, opts) => { if (String(url).includes('gate.example')) throw new Error('offline'); return realFetch(url, opts); };
        p.openModelSelector('model');
        await new Promise(r => setTimeout(r, 1200));
        const afterOpen = mine();
        // 恢复网络并给出「检测结果」：shared 与手动同 id，detected-only 只存在于端点
        let hits = 0;
        window.fetch = async (url, opts) => {
            if (String(url).includes('gate.example')) {
                hits++;
                return new Response(JSON.stringify({ data: [{ id: 'shared' }, { id: 'detected-only' }] }),
                    { status: 200, headers: { 'Content-Type': 'application/json' } });
            }
            return realFetch(url, opts);
        };
        p.openModelSelector('visionModel');       // 重新打开 → 触发检测（拉取标记复位前不该跳过）
        await new Promise(r => setTimeout(r, 200));
        p.settings.apiProviders = [...p.settings.apiProviders];   // 触发重算
        await new Promise(r => setTimeout(r, 1500));
        const afterDetect = mine();
        window.fetch = realFetch;
        return { beforeOpen, afterOpen, afterDetect, fetchHits: hits };
    })()`);
    const p2ok = p2 && !p2.__error;
    record('A1-offline-cold-start', p2ok && p2.beforeOpen.length === 2 && p2.afterOpen.length === 2,
        { why: p2 && p2.__error, beforeOpen: p2 && p2.beforeOpen, afterOpen: p2 && p2.afterOpen });
    record('A2-labels-kept', p2ok && p2.beforeOpen.every(m => !!m.label) && p2.afterOpen.every(m => !!m.label),
        { beforeOpen: p2 && p2.beforeOpen });
    record('A3-manual-wins-on-collision',
        p2ok && p2.afterDetect.some(m => m.bare === 'shared' && m.manual === true && m.label === '我的共享模型' && m.ctx === 64000),
        { why: '同 id 时必须保留手动条目（manual=true + 用户 label/上下文）', afterDetect: p2 && p2.afterDetect });
    record('A4-detected-still-offered',
        p2ok && p2.afterDetect.some(m => m.bare === 'detected-only' && m.manual === false),
        { why: '用户没配过的检测结果仍须可选', afterDetect: p2 && p2.afterDetect });
    record('A5-manual-only-visible',
        p2ok && p2.afterDetect.some(m => m.bare === 'manual-only' && m.manual === true),
        { afterDetect: p2 && p2.afterDetect });

    // ---------- 阶段 3：选择器 DOM —— 手动条目的显示名必须**真的可见** ----------
    // 复现过的真实缺陷：数据层把 label 交到了模板，但单行 flex 里
    // 「供应商徽标 + 裸 ID + meta chip」已占满行宽，label 被压成 clientWidth = 0
    // ——数据层断言全绿、用户仍然「看不到自己配的模型」。故此处必须测**渲染结果**：
    // label 宽度 > 0 且未被 ellipsis 截断（scrollWidth ≤ clientWidth + 1）。
    const p3 = await evalJs(`(async () => {
        const p = ${PROXY};
        if (!p) return { __error: 'no proxy' };
        p.modelSearchQuery = '';
        p.activeModelTag = 'all';
        p.openModelSelector('model');
        await new Promise(r => setTimeout(r, 900));
        const modal = document.querySelector('.max-w-2xl');
        if (!modal) return { __error: 'modal not rendered' };
        const rows = [...modal.querySelectorAll('button')];
        const findRow = (needle) => rows.find(b => b.textContent.includes(needle));
        const measure = (row) => {
            if (!row) return null;
            const label = [...row.querySelectorAll('span')].find(s => /text-gray-800/.test(s.className));
            const mono = [...row.querySelectorAll('span')].find(s => /font-mono/.test(s.className));
            const group = row.firstElementChild;
            return {
                text: row.textContent.replace(/\\s+/g, ' ').trim().slice(0, 80),
                labelText: label ? label.textContent.trim() : null,
                labelClientW: label ? Math.round(label.getBoundingClientRect().width) : null,
                labelScrollW: label ? label.scrollWidth : null,
                bareId: mono ? mono.textContent.trim() : null,
                twoLine: /flex-col/.test(group.className),
            };
        };
        return { manual: measure(findRow('手动专属')), detected: measure(findRow('detected-only')) };
    })()`);
    const p3ok = p3 && !p3.__error;
    record('A7-manual-label-visible',
        p3ok && p3.manual && p3.manual.labelText === '手动专属'
        && p3.manual.labelClientW > 0 && p3.manual.labelScrollW <= p3.manual.labelClientW + 1,
        { why: '数据层有 label ≠ 用户看得见：label 的真实渲染宽度必须 > 0 且不被省略号截断',
          manual: p3 && p3.manual });
    record('A8-detected-row-unchanged',
        p3ok && p3.detected && p3.detected.twoLine === false && p3.detected.labelClientW === null,
        { why: '无 label 的检测条目必须维持上游单行原状（两行式不得波及）',
          detected: p3 && p3.detected });

    const report = {
        url: APP_URL,
        checks,
        failures: failures.concat(exceptions.length ? ['A9-no-js-exception: ' + exceptions[0]] : []),
        exceptions,
    };
    report.pass = report.failures.length === 0;
    console.log(JSON.stringify(report, null, 1));
    ws.close();
    process.exit(report.pass ? 0 : 1);
}
main().catch((e) => { console.error('FAILED: ' + e.message); process.exit(1); });

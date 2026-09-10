// LuzzyRP 页面交接（转场）回归测试 —— 用户 2026-09-11 指定编排的守卫
//
// 编排（DESIGN.md §动效「页面交接」）：侧栏左移 + 页内容交叉淡化，**同帧起跑、同时结束**。
// 本测试在**桌面 Chromium**（与系统 WebView 同引擎族）里以手机视口加载应用，
// 连切 10 次页并逐次断言不变量，同时采样一次转场的不透明度/位移时间线。
//
// 用法：
//   1) 起 headless Chrome：chrome --headless=new --remote-debugging-port=9347 --user-data-dir=%TEMP%\luzzy-prof-handoff about:blank
//   2) node tools/page-handoff-test.cjs
// 可用环境变量：CDP_PORT（默认 9347）、APP_URL（默认主树 index.html）
//
// 断言（任一不满足即 FAIL，退出码 1）：
//   A1 每次切换后「可见页面数 == 1」（扩展层注入的 .lsp-fab-row 等 chrome 不计）
//   A2 收尾后无残留 .lsp-view-out / .lsp-view-in / html.lsp-page-handoff
//   A3 chrome（.lsp-fab-row）不被误隐藏
//   A4 转场三要素（旧页透明度 / 新页透明度 / 侧栏位移）**同时结束**：末端样本相互差 ≤ 2 帧
//   A5 转场总时长落在 200ms 令牌 ±60ms
//   A6 无 JS 异常
//
// 背景（为什么有这条门禁）：首版把「.app-main 下第一个可见子元素」当页面，而扩展层自己注入的
// 恒可见 `.lsp-fab-row` 也满足该条件 → 收尾时算错「当前页」，旧页的行内 display:none 没还原，
// 真机上切几次后出现「聊天页盖在管理页上」的两页叠加。A1 + A3 就是守这个的。
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
            exceptions.push(`${d.lineNumber} ${(d.exception?.description || d.text || '').split('\n')[0].slice(0, 120)}`);
        }
    };
    const send = (method, params = {}) => new Promise((res) => {
        const id = ++msgId;
        pending.set(id, res);
        ws.send(JSON.stringify({ id, method, params }));
    });
    const evalJs = async (expr) => {
        const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
        if (r.result?.exceptionDetails) {
            return { __error: (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text).slice(0, 300) };
        }
        return r.result?.result?.value;
    };

    await send('Runtime.enable');
    await send('Page.enable');
    // 手机视口：只有窄屏才走「抽屉式侧栏 + 遮罩」这条路径（桌面是常驻侧栏，不触发交接编排）
    await send('Emulation.setDeviceMetricsOverride', {
        width: 390, height: 844, deviceScaleFactor: 3.25, mobile: true,
    });
    // **headless Chrome 默认 prefers-reduced-motion: reduce**（动画被压成 0.01ms），
    // 不关掉这个模拟，时间线断言就是在测空气——这是本测试第一版踩过的坑。
    await send('Emulation.setEmulatedMedia', {
        features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }],
    });
    await send('Page.navigate', { url: APP_URL });
    await sleep(9000);

    // 开屏「沉溺」按钮：不点掉的话它盖在最上层，点击都打不到应用
    await evalJs(`(() => { const b = document.querySelector('.lsp-dive-btn'); if (b) { b.click(); return 'splash-dismissed'; } return 'no-splash'; })()`);
    await sleep(2500);

    const report = { url: APP_URL, checks: {}, failures: [] };

    // ---- 采集脚本注入（真机同款逻辑，只是跑在桌面 Chromium 里）----
    await evalJs(`(() => {
        window.__lspT = {
            cls: (el) => (el.className || '').toString(),
            main: () => document.querySelector('.app-main'),
            isChrome(el) { const c = this.cls(el); return c === 'lsp-fab-row' || c.indexOf('lsp-fab') === 0; },
            pages() { const m = this.main(); return m ? Array.from(m.children).filter((e) => e.nodeType === 1 && !this.isChrome(e)) : []; },
            visible() { return this.pages().filter((e) => getComputedStyle(e).display !== 'none'); },
            sidebar() { return document.querySelector('.app-sidebar'); },
            hamburger() {
                // 用 <use href="#icon-menu"> 定位，比类名稳（聊天页/管理页的菜单按钮类不同）
                return Array.from(document.querySelectorAll('button'))
                    .find((b) => b.querySelector('use[href="#icon-menu"]'));
            },
            nav(name) {
                // 侧栏里同名项可能有两个（「助手」子项 与 RP-Hub 页面项，如「设置」「记忆」）：
                // 助手子项走原生桥接、桌面上不导航，必须排除（.advanced-nav-item 即助手/在线/高级的展开项）。
                const all = Array.from(document.querySelectorAll('.app-sidebar button'))
                    .filter((b) => (b.textContent || '').trim() === name);
                const top = all.filter((b) => !b.classList.contains('advanced-nav-item'));
                return top[top.length - 1] || all[all.length - 1];
            },
            // 采样一次转场：旧页/新页不透明度 + 侧栏 translateX
            probe: null,
            startProbe() {
                const root = document.documentElement;
                const sb = this.sidebar();
                const out = [];
                window.__lspT.probe = out;
                const t0 = performance.now();
                const tick = () => {
                    const outEl = document.querySelector('.lsp-view-out');
                    const inEl = document.querySelector('.lsp-view-in');
                    const m = sb && getComputedStyle(sb).transform;
                    const tx = (m && m !== 'none') ? new DOMMatrix(m).m41 : 0;
                    out.push({
                        t: Math.round(performance.now() - t0),
                        handoff: root.classList.contains('lsp-page-handoff') ? 1 : 0,
                        o: outEl ? +(+getComputedStyle(outEl).opacity).toFixed(3) : null,
                        i: inEl ? +(+getComputedStyle(inEl).opacity).toFixed(3) : null,
                        tx: Math.round(tx),
                    });
                    if (out.length < 90) requestAnimationFrame(tick);
                };
                requestAnimationFrame(tick);
            },
            lastProbe() { return window.__lspT.probe || []; },
        };
        return 'helpers-installed';
    })()`);

    // ---- 1) 连续切换 10 次，每次断言 A1/A2/A3 ----
    // 全部用 RP-Hub 真实页面项（助手子项在桌面上不导航，属另一条链路，不在本测试范围）
    const seq = ['聊天', '记忆系统', '角色卡管理', '聊天', '用量统计', 'UI模板', '聊天', '记忆系统', '聊天', '角色卡管理'];
    const steps = [];
    for (let i = 0; i < seq.length; i++) {
        const name = seq[i];
        const opened = await evalJs(`(() => {
            const t = window.__lspT; const h = t.hamburger();
            if (!h) return 'no-hamburger';
            if (!(t.sidebar() || {}).classList.contains('mobile-sidebar-open')) { h.click(); return 'opened'; }
            return 'already-open';
        })()`);
        await sleep(300);
        const clicked = await evalJs(`(() => { const b = window.__lspT.nav(${JSON.stringify(name)}); if (!b) return 'not-found'; b.click(); return 'clicked'; })()`);
        await sleep(450);
        const state = await evalJs(`(() => {
            const t = window.__lspT;
            const vis = t.visible();
            const leftover = t.pages().filter((e) => e.classList.contains('lsp-view-out') || e.classList.contains('lsp-view-in'));
            const fab = t.main() && t.main().querySelector('.lsp-fab-row');
            return {
                visibleCount: vis.length,
                visible: vis.map((e) => t.cls(e).split(' ')[0]),
                leftover: leftover.length,
                handoffClass: document.documentElement.classList.contains('lsp-page-handoff'),
                fabHidden: !!fab && getComputedStyle(fab).display === 'none',
            };
        })()`);
        steps.push({ step: i + 1, page: name, opened, clicked, ...(state || {}) });
        if (state && state.visibleCount !== 1) report.failures.push(`A1 #${i + 1} ${name}: 可见页面 ${state.visibleCount} 个 (${(state.visible || []).join(' | ')})`);
        if (state && state.leftover) report.failures.push(`A2 #${i + 1} ${name}: 残留交接类 ${state.leftover}`);
        if (state && state.handoffClass) report.failures.push(`A2 #${i + 1} ${name}: html.lsp-page-handoff 未清`);
        if (state && state.fabHidden) report.failures.push(`A3 #${i + 1} ${name}: .lsp-fab-row 被误隐藏`);
        if (clicked !== 'clicked') report.failures.push(`切换失败 #${i + 1} ${name}: ${clicked}`);
    }
    report.checks.switchStress = steps;

    // ---- 2) 采样一次完整转场，断言 A4/A5 共终止与时长 ----
    // 先回到聊天页（v-show 保活 → 会被当快照层，三条信号齐全），再切到管理页
    await evalJs(`(() => { const h = window.__lspT.hamburger(); if (h) h.click(); return 1; })()`);
    await sleep(320);
    await evalJs(`(() => { const b = window.__lspT.nav('聊天'); if (b) b.click(); return 1; })()`);
    await sleep(500);
    await evalJs(`(() => { const h = window.__lspT.hamburger(); if (h) h.click(); return 1; })()`);
    await sleep(320);
    await evalJs(`(() => { window.__lspT.startProbe(); const b = window.__lspT.nav('记忆系统'); if (b) b.click(); return 'target-clicked'; })()`);
    await sleep(1400);
    const probe = await evalJs(`window.__lspT.lastProbe()`);
    const samples = Array.isArray(probe) ? probe : [];
    const active = samples.filter((s) => s.handoff === 1);
    const firstActive = active[0] || null;
    const lastActive = active[active.length - 1] || null;
    const frame = samples.length > 1 ? (samples[samples.length - 1].t - samples[0].t) / (samples.length - 1) : 16.7;

    let ends = null;
    if (firstActive && lastActive) {
        const sbWidth = await evalJs(`(() => { const s = window.__lspT.sidebar(); return s ? Math.round(s.getBoundingClientRect().width) : 0; })()`);
        // 「共终止」的判据＝各信号**最后一次变化**的时刻（而不是跨过某个阈值——
        // ease-out 曲线下，位移 90% 与透明度 98% 天然不在同一时刻，用阈值会误判）。
        const lastChange = (key, eps) => {
            let last = null;
            for (let i = 1; i < active.length; i++) {
                const a = active[i][key];
                const b = active[i - 1][key];
                if (a === null || b === null) continue;
                if (Math.abs(a - b) > eps) last = active[i].t;
            }
            return last;
        };
        const outEnd = lastChange('o', 0.005);
        const inEnd = lastChange('i', 0.005);
        const txEnd = lastChange('tx', 1);
        const signals = { outEnd, inEnd, txEnd };
        const startT = firstActive.t;
        const allEnds = Object.values(signals).filter((v) => v !== null);
        ends = {
            ...signals,
            duration: allEnds.length ? Math.max(...allEnds) - startT : null,
            tail: lastActive.t - startT,
            frame: +frame.toFixed(2),
            sidebarWidth: sbWidth,
        };
        if (allEnds.length < 2) {
            report.failures.push(`A4 转场信号不足（至少要有新页与侧栏两条「有变化」的信号）: ${JSON.stringify(ends)}`);
        } else if (Math.max(...allEnds) - Math.min(...allEnds) > frame * 2 + 8) {
            report.failures.push(`A4 转场各要素未同时结束（差 ${Math.max(...allEnds) - Math.min(...allEnds)}ms > 2 帧）: ${JSON.stringify(ends)}`);
        }
        if (ends.duration !== null && Math.abs(ends.duration - 200) > 60) {
            report.failures.push(`A5 转场时长 ${ends.duration}ms 偏离 200ms 令牌 ±60ms`);
        }
        report.checks.handoffTimeline = { ends, samples: active.filter((_, i) => i % 3 === 0).slice(0, 14) };
    } else {
        report.failures.push('A4 没有采到任何转场样本（交接类从未出现）');
    }

    // ---- 3) 异常 ----
    report.exceptions = exceptions;
    if (exceptions.length) report.failures.push(`A6 发现 ${exceptions.length} 条 JS 异常`);

    report.pass = report.failures.length === 0;
    console.log(JSON.stringify(report, null, 2));
    ws.close();
    process.exit(report.pass ? 0 : 1);
}

main().catch((e) => { console.error('HANDOFF TEST FAIL:', e.message); process.exit(1); });

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
                const firstPage = () => {
                    const m = document.querySelector('.app-main');
                    const kids = m ? Array.from(m.children) : [];
                    for (const el of kids) {
                        const c = (el.className || '').toString();
                        // 跳过扩展层自己注入的恒可见 chrome（.lsp-fab-row）：它不是页面，
                        // 否则换页瞬间会把「可见页」误报成它，A7 就会假红。
                        if (c === 'lsp-fab-row' || c.indexOf('lsp-fab') === 0) continue;
                        if (getComputedStyle(el).display !== 'none') return c.split(' ')[0];
                    }
                    return 'none';
                };
                const tick = () => {
                    const outEl = document.querySelector('.lsp-view-out');
                    const inEl = document.querySelector('.lsp-view-in');
                    const layerEl = document.getElementById('lsp-handoff-layer');
                    const ghostNode = outEl || (layerEl && layerEl.querySelector('.lsp-view-out'));
                    const m = sb && getComputedStyle(sb).transform;
                    const tx = (m && m !== 'none') ? new DOMMatrix(m).m41 : 0;
                    out.push({
                        t: Math.round(performance.now() - t0),
                        handoff: root.classList.contains('lsp-page-handoff') ? 1 : 0,
                        o: ghostNode ? +(+getComputedStyle(ghostNode).opacity).toFixed(3) : null,
                        ghostInLayer: !!(ghostNode && ghostNode.parentElement && ghostNode.parentElement.id === 'lsp-handoff-layer'),
                        i: inEl ? +(+getComputedStyle(inEl).opacity).toFixed(3) : null,
                        tx: Math.round(tx),
                        first: firstPage(),
                    });
                    if (out.length < 120) requestAnimationFrame(tick);
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

    // ---- 2) 采样一次完整转场，断言 A4/A5 共终止与时长 + A7 无「硬切帧」 ----
    // 先回到聊天页（v-show 保活 → 会被当快照层，三条信号齐全），再切到管理页
    await evalJs(`(() => { const h = window.__lspT.hamburger(); if (h) h.click(); return 1; })()`);
    await sleep(320);
    await evalJs(`(() => { const b = window.__lspT.nav('聊天'); if (b) b.click(); return 1; })()`);
    await sleep(500);
    const samples = [];
    for (const [from, to] of [['聊天', '记忆系统'], ['记忆系统', '角色卡管理']]) {
        await evalJs(`(() => { const h = window.__lspT.hamburger(); if (h) h.click(); return 1; })()`);
        await sleep(340);
        await evalJs(`(() => { window.__lspT.startProbe(); const b = window.__lspT.nav(${JSON.stringify(to)}); if (b) b.click(); return 1; })()`);
        await sleep(900);
        const frames = await evalJs(`window.__lspT.lastProbe()`);
        samples.push({ from, to, frames: Array.isArray(frames) ? frames : [] });
    }
    const probe = samples[0].frames;
    const active = probe.filter((s) => s.handoff === 1);
    const firstActive = active[0] || null;
    const lastActive = active[active.length - 1] || null;
    const frame = probe.length > 1 ? (probe[probe.length - 1].t - probe[0].t) / (probe.length - 1) : 16.7; 

    // A7：任一切换里都不得出现「可见页已经变了、但没有任何交接层」的帧——那正是用户报的
    // 「先切换、后淡化」硬切。判据要排除两种情况，否则会假红：
    //   ① 交接**进行中**的快照层接管（cur.handoff=1）；
    //   ② 交接**收尾**时快照层被移除，可见页身份随即回落到真实页面（prev.handoff=1）。
    // 故只有「前后两帧都无交接、且可见页身份变了」才算硬切。
    for (const s of samples) {
        for (let i = 1; i < s.frames.length; i++) {
            const prev = s.frames[i - 1];
            const cur = s.frames[i];
            const switched = cur.first !== prev.first;
            if (switched && !cur.handoff && !prev.handoff && cur.o === null) {
                report.failures.push(`A7 「${s.from}→${s.to}」出现硬切帧：t=${cur.t} 可见页 ${prev.first}→${cur.first}，但无 ghost / 无交接类`);
            }
        }
    }
    // A8：管理页之间互切（旧页 v-if 摘除）也要有快照层——它必须来自 #lsp-handoff-layer
    const mgmtSample = samples[1];
    const layerGhost = mgmtSample.frames.some((s) => s.o !== null && s.ghostInLayer === true);
    if (!layerGhost) {
        report.failures.push(`A8 「${mgmtSample.from}→${mgmtSample.to}」没有覆盖层快照（旧页被 v-if 摘除时应有 #lsp-handoff-layer 承载）`);
    }

    // ---- A9：交接必须与切换**落在同一帧**（微任务阶段即应用）----
    // 用户报的「先切换、后淡化」根因：Vue 在微任务里换完 DOM，而交接若等到下一个 rAF 才应用，
    // 中间那一帧就是硬切。这里用**确定性判据**而不是「碰运气看有没有画出来」：
    //   点击后只推进微任务（不给浏览器出帧机会），此时交接类就该已经在 html 上。
    // 微任务版实现：它在捕获阶段排了两个嵌套微任务（排在 Vue flush 之后），同一帧内必然生效 ✔
    // rAF 版实现：此处读到的仍是 false → 判红（红证已验）。
    const a9 = await evalJs(`(async () => {
        const t = window.__lspT;
        const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
        // 摆到聊天页 + 开侧栏
        if (!t.sidebar().classList.contains('mobile-sidebar-open')) { const h = t.hamburger(); if (h) h.click(); await sleep(360); }
        const b1 = t.nav('聊天'); if (b1) b1.click(); await sleep(650);
        if (!t.sidebar().classList.contains('mobile-sidebar-open')) { const h2 = t.hamburger(); if (h2) h2.click(); await sleep(360); }
        const target = t.nav('记忆系统');
        if (!target) return { error: 'target-not-found' };
        const hoBefore = document.documentElement.classList.contains('lsp-page-handoff');
        target.click();                                   // 同步派发点击
        const hoSync = document.documentElement.classList.contains('lsp-page-handoff');
        await Promise.resolve(); await Promise.resolve();  // 只推进微任务：不给浏览器出帧的机会
        await Promise.resolve(); await Promise.resolve();
        const hoMicro = document.documentElement.classList.contains('lsp-page-handoff');
        const ghost = !!document.querySelector('.lsp-view-out');
        await new Promise((r) => setTimeout(r, 12));       // 再给半帧，确认它确实会起播
        const hoLater = document.documentElement.classList.contains('lsp-page-handoff');
        return { hoBefore, hoSync, hoMicro, hoLater, ghost };
    })()`);
    report.checks.sameFrameStart = a9;
    if (!a9 || a9.error) {
        report.failures.push(`A9 同帧起播检查无法执行：${JSON.stringify(a9)}`);
    } else if (!a9.hoMicro) {
        report.failures.push(
            `A9 交接没有与切换落在同一帧：微任务阶段 handoff=${a9.hoMicro}（同帧起播应为 true）——` +
            `「先切换、后淡化」的硬切就是这么来的`,
        );
    }
    report.checks.switchSamples = samples.map((s) => ({
        pair: `${s.from}→${s.to}`,
        frames: s.frames.filter((_, i) => i % 3 === 0).slice(0, 12),
    }));

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
        // 采样判据只作**信息字段**：rAF 采样会被机器负载饿死（同一断言在 gradle 并行时偶发红），
        // 概率门禁比没有更糟 → 时长/曲线的 pass/fail 交给下面的 A10（读声明值，确定性）。
        report.checks.handoffTimeline = { ends, samples: active.filter((_, i) => i % 3 === 0).slice(0, 14) };
        if (allEnds.length < 2) {
            report.failures.push(`A4 转场信号不足（至少要有新页与侧栏两条「有变化」的信号）: ${JSON.stringify(ends)}`);
        }
    } else {
        report.failures.push('A4 没有采到任何转场样本（交接类从未出现）');
    }

    // ---- A10：时长与曲线读**声明值**（确定性，与机器负载无关）----
    // 采样式判据会被负载饿死（A5 曾偶发红）；这里直接读 Web Animations API 的 timing 与
    // computed style 的 transition 声明，断言四个要素共用同一 200ms 令牌与同一 ease-out 曲线。
    const a10 = await evalJs(`(async () => {
        const t = window.__lspT;
        const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
        if (!t.sidebar().classList.contains('mobile-sidebar-open')) { const h = t.hamburger(); if (h) h.click(); await sleep(360); }
        const b1 = t.nav('聊天'); if (b1) b1.click(); await sleep(650);
        if (!t.sidebar().classList.contains('mobile-sidebar-open')) { const h2 = t.hamburger(); if (h2) h2.click(); await sleep(360); }
        const target = t.nav('记忆系统');
        if (!target) return { error: 'target-not-found' };
        target.click();
        await new Promise((r) => requestAnimationFrame(() => r()));
        const timingOf = (el) => {
            if (!el || !el.getAnimations) return null;
            const list = el.getAnimations();
            if (!list.length) return null;
            const eff = list[0].effect;
            const tm = eff.getTiming();
            // 注意：CSS 动画的缓动写在**关键帧**上，effect.getTiming().easing 对 CSS 动画
            // 恒为 'linear'（不是实现问题）。故一并读关键帧缓动。
            // ⚠ 本段在模板字符串内：注释里**不能出现反引号**，否则会截断模板字符串（已踩）。
            const kfs = eff.getKeyframes ? eff.getKeyframes() : [];
            return { duration: tm.duration, easing: tm.easing, keyEasings: kfs.map((k) => k.easing) };
        };
        const sb = t.sidebar();
        const cs = sb ? getComputedStyle(sb) : null;
        return {
            out: timingOf(document.querySelector('.lsp-view-out')),
            inFly: timingOf(document.querySelector('.lsp-view-in')),
            sidebar: cs ? { duration: cs.transitionDuration, easing: cs.transitionTimingFunction } : null,
        };
    })()`);
    report.checks.declaredMotion = a10;
    const TOKEN_EASE = 'cubic-bezier(0.23, 1, 0.32, 1)';
    const norm = (s) => (s || '').toString().replace(/\s+/g, ' ').trim();
    if (!a10 || a10.error) {
        report.failures.push(`A10 无法读取声明值：${JSON.stringify(a10)}`);
    } else {
        for (const [name, sig] of [['旧页', a10.out], ['新页', a10.inFly]]) {
            if (!sig) { report.failures.push(`A10 ${name}没有动画声明（应挂 lspViewOut/lspViewIn）`); continue; }
            if (Number(sig.duration) !== 200) report.failures.push(`A10 ${name}时长 ${sig.duration}ms ≠ 200ms 令牌`);
            // 曲线以关键帧缓动为准（CSS 动画的 effect.easing 恒为 linear，不作为判据）
            const eases = (sig.keyEasings && sig.keyEasings.length ? sig.keyEasings : [sig.easing]).map(norm);
            const bad = eases.filter((e) => e !== TOKEN_EASE && e !== 'linear');
            if (bad.length === eases.length) {
                report.failures.push(`A10 ${name}曲线 ${JSON.stringify(eases)} ≠ ${TOKEN_EASE}`);
            }
        }
        const sbDur = norm(a10.sidebar && a10.sidebar.duration);
        if (sbDur !== '0.2s') report.failures.push(`A10 侧栏时长 ${sbDur} ≠ 0.2s（应与其他三处共用一个令牌值）`);
        if (norm(a10.sidebar && a10.sidebar.easing) !== TOKEN_EASE) {
            report.failures.push(`A10 侧栏曲线 ${a10.sidebar && a10.sidebar.easing} ≠ ${TOKEN_EASE}`);
        }
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

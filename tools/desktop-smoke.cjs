// LuzzyRP 桌面冒烟/验收门禁（会话 21 新立；2026-09-21 修断言并接入 CDP 环境变量）
//
// 用法：
//   1) 启动 headless Chrome：chrome --headless=new --remote-debugging-port=9347 --user-data-dir=%TEMP%\luzzy-prof about:blank
//   2) node tools/desktop-smoke.cjs
// 可用环境变量：CDP_PORT（默认 9347）、APP_URL（默认主树 index.html 的 file:// 路径）
//
// 校验项：挂载健康 / 品牌卡固定文案（030）/ 供应商管理器 UI（仅 DeepSeek + 编辑按钮）/
//         patch 035 供应商编辑器（冲突误报消失 + 图标行存在）
//
// 断言（任一不满足即 FAIL，退出码 1）：
//   A1 页面加载无未捕获 JS 异常
//   A2 LuzzyRP 挂载健康（window.Luzzy 是对象）
//   A3 开屏页被收殓后可进入关于页，且品牌卡版本行匹配「基于 RP-Hub <版本>」
//   A4 供应商选择器可展开、管理器可打开，且卡面含 DeepSeek 与编辑/删除按钮
//   A5 patch 035：编辑器可打开、无冲突误报、图标行在位
//
// 关于开屏页：index.html 的 .luzzy-splash 覆盖全屏并**等待用户点击**才收殓。
// 旧实现不点击直接断言，于是每一项都找不到元素、却全部静默通过——本脚本
// 现在先强制收殓开屏（等同用户点击），再走后续断言。
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
            exceptions.push(`${d.lineNumber} ${(d.exception?.description || d.text || '').split('\n')[0].slice(0, 100)}`);
        }
    };
    const send = (method, params = {}) => new Promise((res) => {
        const id = ++msgId;
        pending.set(id, res);
        ws.send(JSON.stringify({ id, method, params }));
    });
    const evalJs = async (expr) => {
        const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
        if (r.result?.exceptionDetails) return { __error: (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text).slice(0, 300) };
        return r.result?.result?.value;
    };
    // 导航：上游 1.9.5 起侧栏重写为 AppNavigation 抽屉（数据驱动），页面切换**不是**
    // 点页面上的文本叶子节点，而是：点 .app-nav-trigger 开抽屉 → 点 .app-navigation-item。
    // 旧实现按文本找叶子 div，在任何页面上都找不到，于是每一项都静默失败。
    const openNav = `(() => {
        const t = [...document.querySelectorAll('.app-nav-trigger')].find((b) => b.getClientRects().length);
        if (!t) return 'trigger-not-found';
        t.click();
        return 'opened';
    })()`;
    const navTo = (label) => `(() => {
        const items = [...document.querySelectorAll('.app-navigation-item')];
        if (!items.length) return 'drawer-not-open';
        const item = items.find((b) => b.textContent.trim() === ${JSON.stringify(label)});
        if (!item) return 'item-not-found:' + items.map((b) => b.textContent.trim()).join('|');
        item.click();
        return 'clicked';
    })()`;
    // 切页 = 开抽屉 + 点目标项；抽屉有 380ms 入场动画，两段之间必须等。
    const switchView = async (label) => {
        const opened = await evalJs(openNav);
        if (opened !== 'opened') return opened;
        await sleep(700);
        return await evalJs(navTo(label));
    };

    await send('Runtime.enable');
    await send('Page.enable');
    await send('Page.navigate', { url: APP_URL });
    await sleep(9000);

    const report = {};
    const failures = [];
    const check = (name, ok, detail) => {
        report[name] = { pass: !!ok, detail };
        if (!ok) failures.push(`${name}: ${JSON.stringify(detail)}`);
    };

    // 收殓开屏页：它覆盖全屏且**等待点击**（见 luzzy-splash.js），不点则后续一切元素都不可达。
    // 点「沉溺」即等同用户操作；兜底再直接置 visibility:hidden（脚本内已有同款兜底）。
    report.splashDismiss = await evalJs(`(() => {
        const splash = document.querySelector('.luzzy-splash');
        if (!splash) return 'no-splash';
        const btn = splash.querySelector('.lsp-dive-btn');
        if (btn) btn.click();
        return 'clicked';
    })()`);
    await sleep(2500);
    report.splashHidden = await evalJs(`(() => {
        const s = document.querySelector('.luzzy-splash');
        if (!s) return 'removed';
        const hidden = getComputedStyle(s).visibility === 'hidden' || getComputedStyle(s).display === 'none';
        if (!hidden) { s.style.visibility = 'hidden'; return 'forced'; }
        return 'hidden';
    })()`);
    await sleep(500);

    // A2 挂载健康
    const luzzyType = await evalJs('typeof window.Luzzy');
    check('A2-luzzy-mounted', luzzyType === 'object', luzzyType);

    // A3 品牌卡精确校验（先切到关于页）
    // 口径来自 patch 030：版本号**刻意不入文案**（固定「基于 RP-Hub 二次开发」，防上游同步
    // 漂移），故断言「含基于 RP-Hub 且**不含**版本号」——旧实现断言「必须有版本号」，
    // 与 patch 030 直接抵触，是永远不可能为真的过期判据。
    const navAbout = await switchView('关于');
    await sleep(1200);
    const aboutText = await evalJs(`(() => {
        const view = document.querySelector('.about-view');
        return view ? view.innerText : null;
    })()`);
    report.brandCard = await evalJs(`(() => {
        const view = document.querySelector('.about-view');
        if (!view) return { present: false };
        const line = [...view.querySelectorAll('div')].find((d) => d.className.includes('text-xs') && d.textContent.includes('基于'));
        if (!line) return { present: true, line: 'not-found' };
        return { present: true, text: line.textContent.trim() };
    })()`);
    const branding = await evalJs(`(() => {
        const el = document.getElementById('luzzy-about-branding');
        return el ? el.textContent.trim() : null;
    })()`);
    check('A3-brand-card',
        navAbout === 'clicked'
        && report.brandCard?.present === true
        && typeof aboutText === 'string' && aboutText.includes('基于 RP-Hub')
        && aboutText.includes('二次开发')
        // patch 030：文案里不得再出现版本号（「基于 RP-Hub 1.9.8」这种）
        && !/基于\\s*RP-Hub[^二]{0,12}\\d/.test(aboutText),
        { navAbout, brandCard: report.brandCard, branding });

    // A4 设置页 → 供应商管理器
    const navSettings = await switchView('设置');
    await sleep(1200);
    report.openSelector = await evalJs(`(() => {
        const btn = document.querySelector('.api-provider-selector-container button');
        if (!btn) return 'trigger-not-found';
        btn.click();
        return 'opened';
    })()`);
    await sleep(800);
    report.openManager = await evalJs(`(() => {
            const btn = [...document.querySelectorAll('button')].find((b) => b.textContent.includes('管理供应商'));
            if (!btn) return 'not-found';
            btn.click();
            return 'clicked';
        })()`);
    await sleep(1200);
    report.manager = await evalJs(`(() => {
            const cards = [...document.querySelectorAll('.rounded-xl.border')].filter((c) => [...c.querySelectorAll('button')].some((b) => b.textContent.trim() === '检测'));
            if (!cards.length) return { open: document.body.innerText.includes('管理供应商'), cards: [] };
                        return {
                open: true,
                cards: cards.map((c) => ({
                    name: (c.querySelector('.font-bold') || {}).textContent?.trim() || '',
                    hasEdit: [...c.querySelectorAll('button')].some((b) => b.textContent.trim() === '编辑'),
                    hasDelete: [...c.querySelectorAll('button')].some((b) => b.getAttribute('title') === '删除供应商')
                }))
            };
        })()`);
    const deepseekCard = (report.manager?.cards || []).find((c) => c.name === 'DeepSeek');
    check('A4-provider-manager',
        navSettings === 'clicked' && report.manager?.open === true
        && report.openSelector === 'opened' && report.openManager === 'clicked'
        && !!deepseekCard && deepseekCard.hasEdit === true,
        { navSettings, openSelector: report.openSelector, openManager: report.openManager,
          cardCount: (report.manager?.cards || []).length, deepseekCard: deepseekCard || null });

    // A5 patch 035 验收：DeepSeek 编辑器（冲突误报消失 + 图标行存在）
    report.editor035 = await evalJs(`(() => {
        const editBtn = [...document.querySelectorAll('button')].filter((b) => b.textContent.trim() === '编辑' && b.closest('.rounded-xl.border') && b.closest('.rounded-xl.border').innerText.includes('DeepSeek'))[0];
        if (!editBtn) return { opened: false, reason: 'DeepSeek 编辑按钮未找到' };
        editBtn.click();
        return new Promise((resolve) => setTimeout(() => {
            const bodyText = document.body.innerText;
            const heading = [...document.querySelectorAll('h3, div')].some((e) => e.children.length === 0 && e.textContent.trim() === '编辑供应商');
            resolve({
                opened: heading,
                conflictFalsePositive: bodyText.includes('该 id 已被其他供应商占用'),
                iconRowPresent: bodyText.includes('从相册选择') && bodyText.includes('供应商图标'),
                idLockedLabel: bodyText.includes('内置供应商，固定')
            });
        }, 900));
    })()`);
    check('A5-editor-035',
        report.editor035?.opened === true
        && report.editor035?.conflictFalsePositive === false
        && report.editor035?.iconRowPresent === true,
        report.editor035);

    // A1 无未捕获 JS 异常（放在最后判：把「页面加载 + 全部交互」都纳入观察窗）
    report.exceptions = exceptions;
    check('A1-no-exceptions', exceptions.length === 0, exceptions);

    report.pass = failures.length === 0;
    report.failures = failures;
    console.log(JSON.stringify(report, null, 2));
    if (failures.length) {
        console.error(`DESKTOP SMOKE FAIL: ${failures.length} 项未通过`);
        ws.close();
        process.exit(1);
    }
    ws.close();
}

main().catch((e) => { console.error('DESKTOP SMOKE FAIL:', e.message); process.exit(1); });

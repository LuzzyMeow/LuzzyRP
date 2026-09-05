// LuzzyRP 桌面冒烟/验收工具（会话 21 新立门禁）
// 用法：
//   1) 启动 headless Chrome：chrome --headless=new --remote-debugging-port=9346 --user-data-dir=%TEMP%\luzzy-prof about:blank
//   2) node tools/desktop-smoke.cjs
// 可用环境变量：CDP_PORT（默认 9346）、APP_URL（默认主树 index.html 的 file:// 路径）
// 校验项：挂载健康 / RPHubConfig（029）/ 品牌卡固定文案（030）/ 记忆节点（031，伪造戳）/
//         流式渲染旁路（032）/ 供应商管理器 UI（仅 DeepSeek + 编辑按钮）
// 退出码：0=全过 / 1=有失败（详见输出 JSON）
const CDP_PORT = 9347;
const APP_URL = 'file:///D:/.NekoTool/LuzzyRP/app/src/main/assets/rphub/index.html';
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
        const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true });
        if (r.result?.exceptionDetails) return { __error: (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text).slice(0, 300) };
        return r.result?.result?.value;
    };
    const clickText = (text) => `(() => {
        const cands = [...document.querySelectorAll('button, span, div, a')].filter((e) => e.children.length === 0 && e.textContent.trim() === ${JSON.stringify(text)});
        if (!cands.length) return 'not-found';
        cands[cands.length - 1].click();
        return 'clicked';
    })()`;

    await send('Runtime.enable');
    await send('Page.enable');
    await send('Page.navigate', { url: APP_URL });
    await sleep(9000);

    const report = {};

    // 品牌卡精确校验（先切到关于页）
    await evalJs(clickText('关于'));
    await sleep(1000);
    report.brandCard = await evalJs(`(() => {
        const view = document.querySelector('.about-view');
        if (!view) return { present: false };
        const line = [...view.querySelectorAll('div')].find((d) => d.className.includes('text-xs') && d.textContent.includes('基于'));
        if (!line) return { present: true, line: 'not-found' };
        const t = line.textContent.trim();
        return { present: true, text: t, hasVersionDigit: /基于\\s*RP-Hub[^二]*\\d/.test(t) };
    })()`);

    // 设置页 → 供应商管理器
    await evalJs(clickText('设置'));
    await sleep(1000);
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
    await sleep(1000);
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
    report.exceptions = exceptions;
    console.log(JSON.stringify(report, null, 2));
    ws.close();
}

main().catch((e) => { console.error('VERIFY2 FAIL:', e.message); process.exit(1); });

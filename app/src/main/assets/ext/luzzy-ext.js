/**
 * LuzzyRP 扩展层 · 二创新功能（AGENTS.md §5.3）
 *
 * 本文件在 index.html 尾部加载（patch 005 挂载点），此时上游全局对象
 * （Vue app / RPHub 等）已就绪。访问上游对象必须先做存在性检测；
 * 任何扩展功能必须自带降级路径，不允许因本文件报错导致应用白屏。
 *
 * 功能清单：
 * 1. 桥接自检（开发期诊断；发布期静默）
 * 2. 关于页品牌信息注入
 *
 * [状态 2026-09-01] 旧主题系统（data-theme/data-mode）已随「暖纸书房」方案
 * 整体移除；新主题系统待设计 SKILL 三方向硬门产出并经用户选定后重建。
 * 字体扩展（luzzy 选项）也待新字体方案重新设计后恢复——当前上游
 * fontFamilies 保持原样（modern/serif/system）。
 */
(function () {
    'use strict';

    // ============================================================
    // 1. 桥接自检
    // ============================================================
    function selfCheck() {
        var info = window.Luzzy && window.Luzzy.getVersion ? window.Luzzy.getVersion() : null;
        if (!info) return;
        if (window.console && console.info) {
            console.info(
                '[LuzzyRP] v' + info.versionName +
                ' (code ' + info.versionCode + ') · upstream RP-Hub ' + info.upstream +
                ' · ' + info.device
            );
        }
    }

    // ============================================================
    // 2. 关于页品牌信息注入
    // ============================================================
    function injectAboutBranding() {
        try {
            // [LuzzyRP patch 028] v2：锚点改为 .about-view 显式容器。
            // 旧逻辑 [class*="about"] 泛匹配 + 取末位，patch 024 的置顶按钮
            // （about-top-fab）成为末位匹配 → 品牌卡被注入按钮内部（绘制错位根因）。
            var target = document.querySelector('.about-view');
            var info = window.Luzzy && window.Luzzy.getVersion ? window.Luzzy.getVersion() : null;
            if (!info || !target) return;

            var existing = document.getElementById('luzzy-about-branding');
            if (existing) {
                // 已注入但落在错误父级（旧版本残留）→ 迁移到正确锚点
                if (existing.parentElement !== target) target.appendChild(existing);
                return;
            }

            var footer = document.createElement('div');
            footer.id = 'luzzy-about-branding';
            footer.style.cssText =
                'margin-top:12px;padding:10px 14px;border-radius:10px;' +
                'background:rgba(127,127,127,0.08);font-size:12px;line-height:1.7;color:#888;' +
                'text-align:center;';
            // [LuzzyRP patch 030] 版本号不再拼入文案（v1.3.0 需求 3）：固定「基于 RP-Hub 二次开发」，
            // 不随上游同步漂移——info.upstream 仍用于 console 自检（上方第 29 行）
            footer.textContent =
                'LuzzyRP v' + info.versionName +
                ' · 基于 RP-Hub 二次开发（CC BY-NC 4.0）';
            target.appendChild(footer);
        } catch (e) {
            // 注入失败静默降级，不影响上游
        }
    }

    // ============================================================
    // 挂载
    // ============================================================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            selfCheck();
            setTimeout(injectAboutBranding, 800);
        });
    } else {
        selfCheck();
        setTimeout(injectAboutBranding, 800);
    }
})();
// ============================================================
// [LuzzyRP patch 018] 主题快照维护（配合 index.html head 内联脚本防开屏闪蓝）
// luzzy-ext.js 挂载于尾部（DOM 就绪后），用 MutationObserver 跟随
// <html> 的 data-theme/data-mode 变化，把当前主题写入 localStorage 快照；
// 下次冷启动由 head 内联脚本同步读取，开屏首帧即为正确主题色。
// localStorage 不可用时静默降级（内联脚本回退默认 luzzy+light）。
// ============================================================
(function () {
    var writeSnapshot = function () {
        try {
            var root = document.documentElement;
            localStorage.setItem('luzzy_theme_snapshot', JSON.stringify({
                theme: root.dataset.theme === 'classic' ? 'classic' : 'luzzy',
                mode: root.dataset.mode === 'dark' ? 'dark' : 'light'
            }));
        } catch (e) { /* 隐私模式等场景静默降级 */ }
    };
    writeSnapshot();
    try {
        new MutationObserver(writeSnapshot).observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-theme', 'data-mode']
        });
    } catch (e) { /* 旧内核降级：仅启动时写一次 */ }
})();

// ============================================================
// [LuzzyRP v1.5.0] 页面交接编排（用户 2026-09-11 指定）
// 统一「所有页之间」的转场：侧栏左移 + 页内容交叉淡化，两者**同帧起跑、同时结束**
// （时长/曲线/快照层规则见 ext/luzzy-theme.css 的「页面交接编排」段）。
//
// 怎么认出「换了哪两页」——**集合差分，不猜**：
//   点击前记下 `.app-main` 的可见子元素集合 `before`，下一帧（Vue 已在微任务里重渲染）
//   再取一次 `after`：
//     · 新页 = after − before（新变可见的）
//     · 旧页 = before − after（刚被隐藏的）
//     · 恒可见的 chrome（扩展层注入的 .lsp-fab-row 等）= before ∩ after → 天然被排除
//   这样既不依赖类名/高度去「猜」哪个是页面，也不怕上游以后再加常驻 chrome。
//   首版用「第一个可见子元素」判定，把恒可见的 .lsp-fab-row 当成了页面 → 收尾时算错当前页、
//   旧页的 display:none 没还原，真机上切几次就出现「聊天页盖在管理页上」（用户 2026-09-11 报告）；
//   改为差分后，该失效模式从根上不存在（有 tools/page-handoff-test.cjs 守着：连切 10 次断言
//   「可见页面数 == 1」，并采样时间线断言各要素同时结束）。
//
// 旧页处理：只有**仍在文档里**（v-show 保活）才当快照层——清掉它的行内 display:none 让它继续
// 绘制、只跑 opacity；若它已被 v-if 摘除（isConnected=false），就只做新页淡入，不做任何 display 手术。
//
// 只读 DOM、不改上游任何逻辑；任何一步失败都静默降级为「原来的硬切」，不影响主流程。
// ============================================================
(function () {
    'use strict';

    var HANDOFF_MS = 200;   // 与 CSS 的 --lsp-handoff-ms 同值（此处只作兜底清理计时）
    var MAIN_SELECTOR = '.app-main';
    var LAYER_ID = 'lsp-handoff-layer';

    var ghost = null;        // 旧页（快照层，可能为 null）
    var ghostInLayer = false;// 旧页是否被搬进了覆盖层（v-if 摘除的情形）
    var incoming = null;     // 新页
    var chrome = [];         // 本次切换中恒定可见的元素（不会被当成页面）
    var beforeSet = null;    // 点击前的可见集合
    var endTimer = 0;
    var rafId = 0;
    var tries = 0;

    function mainEl() {
        return document.querySelector(MAIN_SELECTOR);
    }

    /** 当前可见的直接子元素（快照，用于差分）。 */
    function visibleSet(main) {
        var out = [];
        if (!main) return out;
        var kids = main.children;
        for (var i = 0; i < kids.length; i++) {
            var el = kids[i];
            if (el.nodeType !== 1) continue;
            if (window.getComputedStyle(el).display !== 'none') out.push(el);
        }
        return out;
    }

    /**
     * 快照层容器：**给被 `v-if` 摘除的旧页**用。
     *
     * 为什么需要它：管理页之间互切时 Vue 把旧页整棵子树从 DOM 摘掉，元素虽还在内存里，
     * 但已经不在文档流里 → 无法原地当快照。此时把**那棵原样的子树**搬进这个覆盖层里淡出，
     * 就能得到与「原位快照层」完全一致的交叉淡化（**不克隆、不重建**，滚动位置也还在）。
     * 层级取 10：压住页面内容、但不盖侧栏（上游 `.app-sidebar` 是 `z-50`）。
     */
    function layerEl(create) {
        var el = document.getElementById(LAYER_ID);
        if (el || !create) return el;
        try {
            el = document.createElement('div');
            el.id = LAYER_ID;
            var main = mainEl();
            var rect = main ? main.getBoundingClientRect() : null;
            el.style.cssText = 'position:fixed;pointer-events:none;z-index:10;overflow:hidden;' +
                (rect
                    ? 'left:' + rect.left + 'px;top:' + rect.top + 'px;width:' + rect.width + 'px;height:' + rect.height + 'px;'
                    : 'inset:0;');
            (document.body || document.documentElement).appendChild(el);
            return el;
        } catch (e) {
            return null;
        }
    }

    function clear() {
        if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }
        if (endTimer) { clearTimeout(endTimer); endTimer = 0; }
        if (ghost) {
            ghost.classList.remove('lsp-view-out');
            if (ghostInLayer) {
                // 覆盖层里的旧页是 Vue 已经丢弃的子树：用完摘掉，引用一断即可回收
                var layer = document.getElementById(LAYER_ID);
                if (layer) {
                    if (ghost.parentElement === layer) layer.removeChild(ghost);
                    if (!layer.childElementCount) layer.remove();
                }
            } else {
                // 原位旧页：它的行内 display 是我们在 play() 里清掉的，需判断现在该不该可见
                // （排除「本次切换中恒可见的 chrome」后仍有别的页面 → 它不是当前页 → 还原隐藏；
                //  一个都没有 = 用户在这 200ms 内又切回了它 → 它就是当前页，保持可见）
                var main = mainEl();
                if (main && ghost.isConnected) {
                    var others = visibleSet(main).filter(function (el) {
                        return el !== ghost && chrome.indexOf(el) < 0;
                    });
                    if (others.length) ghost.style.display = 'none';
                }
            }
        }
        if (incoming) incoming.classList.remove('lsp-view-in');
        document.documentElement.classList.remove('lsp-page-handoff');
        ghost = null;
        ghostInLayer = false;
        incoming = null;
        chrome = [];
        beforeSet = null;
    }

    function play(prev, next, chromeSet) {
        clear();
        ghost = prev;
        incoming = next;
        chrome = chromeSet || [];
        document.documentElement.classList.add('lsp-page-handoff');
        if (prev) {
            if (prev.isConnected) {
                // 情形一：旧页还挂在文档里（`v-show` 保活）→ 原位快照层（零成本、滚动位置天然保持）。
                // 只清掉行内 display:none（**不写死 display**，让它自己的 display 类生效）。
                prev.style.removeProperty('display');
                prev.classList.add('lsp-view-out');
            } else {
                // 情形二：旧页已被 `v-if` 摘除（管理页之间互切）→ 把原样子树搬进覆盖层当快照。
                var layer = layerEl(true);
                if (layer) {
                    layer.appendChild(prev);
                    prev.classList.add('lsp-view-out');
                    ghostInLayer = true;
                } else {
                    ghost = null;   // 覆盖层建不出来：退化为「只做新页淡入」
                }
            }
        }
        if (next) next.classList.add('lsp-view-in');
        // 收尾：animation 在**下一帧**才起跑，故清理留 2~3 帧余量，别把最后一帧切掉
        // （fill:both 已把终态锁住，稍晚清理也不会闪回）
        endTimer = setTimeout(clear, HANDOFF_MS + 40);
    }

    /**
     * 比对并起播；返回 true = 已经起播或确认无需起播，false = 还没换页（调用方决定是否重试）。
     */
    function tryPlay() {
        var main = mainEl();
        if (!main || !beforeSet) return true;
        // 助手入口那条自带编排（.lsp-handoff + 原生覆盖层），Web 侧不需要交叉淡化
        if (document.documentElement.classList.contains('lsp-handoff')) { beforeSet = null; return true; }
        var after = visibleSet(main);
        var added = after.filter(function (el) { return beforeSet.indexOf(el) < 0; });
        if (!added.length) return false;
        var gone = beforeSet.filter(function (el) { return after.indexOf(el) < 0; });
        var prev = gone.length ? gone[gone.length - 1] : null;
        var next = added[added.length - 1];
        var chromeSet = after.filter(function (el) { return beforeSet.indexOf(el) >= 0; });
        beforeSet = null;
        play(prev, next, chromeSet);
        return true;
    }

    function checkRaf() {
        rafId = 0;
        if (tryPlay()) return;
        if (++tries < 2) rafId = requestAnimationFrame(checkRaf);
        else beforeSet = null;
    }

    function onCapture() {
        var main = mainEl();
        if (!main) return;
        beforeSet = visibleSet(main);
        tries = 0;
        if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }
        // **交接必须与 DOM 切换落在同一帧**：Vue 的状态更新是微任务、渲染（paint）在其后，
        // 若等到 rAF 才应用交接，中间会漏出一帧「已换页、但旧页快照还没上、侧栏也没动」的硬切
        // （2026-09-11 真机逐帧实测：dt=35 帧即为该硬切，dt=42 交接才起跑）。
        // 故先排两个微任务：第一个排在 Vue 的 flush 之前、第二个排在它之后，且都在同帧 paint 之前。
        Promise.resolve().then(function () {
            Promise.resolve().then(function () {
                if (tryPlay()) return;
                tries = 0;
                rafId = requestAnimationFrame(checkRaf);   // 异步导航兜底：再等两帧
            });
        });
    }

    /** 自愈：清掉上一次进程/异常中断可能留下的类与覆盖层。 */
    function heal() {
        try {
            document.documentElement.classList.remove('lsp-page-handoff');
            var layer = document.getElementById(LAYER_ID);
            if (layer) layer.remove();
            var main = mainEl();
            if (!main) return;
            var kids = main.children;
            for (var i = 0; i < kids.length; i++) {
                var el = kids[i];
                if (el.nodeType !== 1) continue;
                el.classList.remove('lsp-view-out');
                el.classList.remove('lsp-view-in');
            }
        } catch (e) { /* 静默 */ }
    }

    try {
        heal();
        document.addEventListener('click', onCapture, true);
    } catch (e) { /* 静默降级：无交接动画，页面照常硬切 */ }
})();
// ============================================================
// [LuzzyRP v1.5.0] 助手侧栏入口加载器（PLAN §3.3 原型段）
// 以动态注入方式加载 ext/luzzy-assistant.js，**避免修改上游 index.html**
// （硬性规定 2/3：上游文件零裸改、扩展层隔离）。
// 加载失败时静默降级——助手入口缺失不影响 RP-Hub 主流程。
// 落地形态：W1 完成后由 patch 040 把入口移入上游侧栏底部簇（登记 + 标记），
// 本加载器届时保留（luzzy-assistant.js 仍承担配置推送与主题同步职责）。
// ============================================================
(function () {
    'use strict';
    try {
        if (document.getElementById('luzzy-assistant-script')) return;
        var s = document.createElement('script');
        s.id = 'luzzy-assistant-script';
        s.src = '../ext/luzzy-assistant.js';
        s.async = false;
        s.onerror = function () {
            if (window.console && console.debug) {
                console.debug('[LuzzyRP 助手] 入口脚本加载失败（功能降级，不影响主流程）');
            }
        };
        (document.body || document.documentElement).appendChild(s);
    } catch (e) { /* 静默降级 */ }
})();

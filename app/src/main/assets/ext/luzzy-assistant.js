/**
 * LuzzyRP 扩展层 · 「助手」侧栏入口（v1.5.0 原型，PLAN §3.3）
 *
 * 形态：**DOM 注入**——不改上游文件（硬性规定 2/3），在侧栏底部簇「外观」之上插入一个
 * 与上游同款式的按钮，点击调用 `Luzzy.openAssistant()` 唤起原生覆盖层。
 *
 * 降级路径（硬性规定 3：扩展层报错不得白屏）：
 * - 桥接不可用（桌面浏览器）→ 按钮仍在，点击给出 toast 提示；
 * - 锚点缺失（上游改侧栏结构）→ 静默不注入，控制台留一行诊断，不影响主流程；
 * - 被 Vue 重渲染移除 → MutationObserver 重注入（节流，避免抖动）。
 *
 * 落地形态：W1 完成后由 patch 040 改为上游侧栏底部簇条目（`ui-components.js` + 标记），
 * 本文件届时退化为「配置推送 + 主题同步」职责（见 PLAN §3.3 两段式）。
 */
(function () {
    'use strict';

    const Luzzy = window.Luzzy || {};
    const ENTRY_ID = 'luzzy-assistant-entry';
    const LOG = '[LuzzyRP 助手] ';

    // 铅笔线稿（DESIGN.md：手作记号 = 1.5-2px SVG 线稿；禁 emoji 图标）
    const ICON_SVG = '<svg class="w-5 h-5 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" ' +
        'd="M16.86 4.49l1.69-1.69a1.88 1.88 0 1 1 2.65 2.65L6.83 19.82a4.5 4.5 0 0 1-1.9 1.13l-2.68.8.8-2.69a4.5 4.5 0 0 1 1.13-1.9L16.86 4.49z"/>' +
        '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M15 6.5l2.5 2.5"/></svg>';

    const ENTRY_CLASS = 'sidebar-nav-button flex items-center rounded-xl transition-all duration-200 ' +
        'font-medium text-gray-600 hover:bg-gray-50 hover:text-gray-900 w-full px-3 py-2.5';

    function findAnchor() {
        // 底部簇锚点：文本为「外观」的侧栏按钮（patch 019 重排后：外观 → 设置 → 关于）
        const nav = document.querySelector('.sidebar-nav');
        if (!nav) return null;
        const buttons = Array.from(nav.querySelectorAll('button.sidebar-nav-button'));
        const anchor = buttons.find(function (b) {
            return (b.textContent || '').trim() === '外观';
        });
        return anchor ? { nav: nav, anchor: anchor } : null;
    }

    function buildEntry() {
        const btn = document.createElement('button');
        btn.id = ENTRY_ID;
        btn.type = 'button';
        btn.title = '助手';
        btn.setAttribute('aria-label', '助手');
        btn.className = ENTRY_CLASS;
        btn.innerHTML = ICON_SVG + '<span class="whitespace-nowrap overflow-hidden">助手</span>';
        btn.addEventListener('click', function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            if (typeof Luzzy.openAssistant === 'function') {
                Luzzy.openAssistant();
            } else {
                // 扩展层未加载桥接封装时的兜底（正常不会发生：luzzy-bridge.js 先于本文件）
                const raw = window.LuzzyBridge;
                if (raw && typeof raw.openAssistant === 'function') raw.openAssistant();
            }
        });
        return btn;
    }

    /** 注入（幂等）。返回是否处于「已注入」状态。 */
    function inject() {
        const existing = document.getElementById(ENTRY_ID);
        const spot = findAnchor();
        if (!spot) {
            if (existing) existing.remove();
            return false;
        }
        if (existing && existing.parentElement === spot.nav) {
            // 位置漂移（上游重排）→ 校正到锚点之前
            if (existing.nextElementSibling !== spot.anchor) {
                spot.nav.insertBefore(existing, spot.anchor);
            }
            return true;
        }
        if (existing) existing.remove();
        spot.nav.insertBefore(buildEntry(), spot.anchor);
        return true;
    }

    // ---- 配置推送（只读复用 Web 端供应商 / Key / 模型；PLAN §14） ----
    // 注意：payload 含 API Key，**禁止**任何 console 输出。
    function readAppProxy() {
        try {
            const el = document.getElementById('app');
            const vnode = el && el._vnode;
            const proxy = vnode && vnode.component && vnode.component.proxy;
            return proxy || null;
        } catch (e) {
            return null;
        }
    }

    function pushConfig() {
        if (typeof Luzzy.pushAssistantConfig !== 'function') return false;
        const proxy = readAppProxy();
        if (!proxy || !proxy.settings) return false;
        const s = proxy.settings;
        try {
            const providers = Array.isArray(s.apiProviders) ? s.apiProviders.map(function (p) {
                return {
                    id: p.id, name: p.name, protocol: p.protocol || null,
                    apiUrl: p.apiUrl || null, apiKey: p.apiKey || '',
                    editable: !!p.editable,
                    models: Array.isArray(p.models) ? p.models.slice() : [],
                };
            }) : [];
            const payload = {
                version: 1,
                apiProviderId: s.apiProviderId || '',
                apiUrl: s.apiUrl || '',
                apiKey: s.apiKey || '',
                activeModelId: s.model || '',
                providers: providers,
                // 助手独立存储，不自动同步 RP 会话（PLAN §1.2）
                updatedAt: Date.now(),
            };
            return Luzzy.pushAssistantConfig(payload);
        } catch (e) {
            return false;
        }
    }

    // ---- 主题联动（助手覆盖层跟随 Web 端亮/暗；DESIGN.md 恒定「暖幕手记」） ----
    function syncTheme() {
        try {
            const mode = document.documentElement.dataset.mode === 'dark' ? 'dark' : 'light';
            if (typeof Luzzy.setAssistantThemeMode === 'function') Luzzy.setAssistantThemeMode(mode);
        } catch (e) { /* 静默 */ }
    }

    // ---- 显隐回调（原生 → JS）：可在此暂停/恢复轮询等 ----
    Luzzy.onAssistantVisibilityChanged = function (visible) {
        const btn = document.getElementById(ENTRY_ID);
        if (btn) btn.classList.toggle('bg-gray-50', !!visible);
        // 助手可见时同步一次配置（用户可能刚在设置页改过供应商）
        if (visible) pushConfig();
    };
    window.Luzzy = Luzzy;

    // ---- 启动 ----
    function boot() {
        const ok = inject();
        if (!ok) {
            // 锚点缺失：静默降级（上游改结构时不阻断主流程）
            if (window.console && console.debug) console.debug(LOG + '侧栏锚点缺失，入口未注入（功能降级）');
        }
        pushConfig();
        syncTheme();

        // 侧栏被 Vue 重渲染时重注入（节流 300ms）
        let pending = false;
        const observer = new MutationObserver(function () {
            if (pending) return;
            pending = true;
            setTimeout(function () {
                pending = false;
                inject();
            }, 300);
        });
        observer.observe(document.body, { childList: true, subtree: true });

        // 主题属性变化（patch 018 的 data-mode 快照）
        const themeObserver = new MutationObserver(syncTheme);
        themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-mode', 'data-theme'] });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();

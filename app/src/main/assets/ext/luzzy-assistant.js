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

    // ---- 助手子项组（用户 2026-09-09 指定：菜单栏归 LuzzyRP，其下「助手」展开子项） ----
    const GROUP_ID = 'luzzy-assistant-group';
    const SUB_STYLE_ID = 'luzzy-assistant-style';

    /** 子项 → 原生路由（与 AssistantRoute 对应；`conversations` 含助手切换）。 */
    const SUB_ENTRIES = [
        { label: '会话', route: 'conversations' },
        { label: '记忆', route: 'memory' },
        { label: '技能', route: 'skills' },
        { label: 'MCP', route: 'mcp' },
        { label: '工作区', route: 'workspace' },
        { label: '终端', route: 'terminal' },
        { label: '设置', route: 'settings' }
    ];

    /**
     * 子项样式：沿用侧栏 token（gray-600 → gray-900，圆角 12px），
     * 左侧一条 hairline 竖线作为「卷宗」式层级标记（DESIGN.md：手作记号，不用色块）。
     */
    const SUB_CSS = [
        '#' + GROUP_ID + ' .lz-sub { margin: 2px 0 2px 14px; padding-left: 10px;',
        '  border-left: 1px solid rgba(0,0,0,.08); display: flex; flex-direction: column; gap: 1px; }',
        '#' + GROUP_ID + ' .lz-sub-item { display: flex; align-items: center; width: 100%;',
        '  padding: 7px 10px; border-radius: 10px; font-size: 13px; line-height: 1.2;',
        '  color: #6b7280; text-align: left; transition: background-color .18s ease, color .18s ease; }',
        '#' + GROUP_ID + ' .lz-sub-item:hover { background: rgba(0,0,0,.04); color: #111827; }',
        '#' + GROUP_ID + ' .lz-sub-item:active { background: rgba(0,0,0,.06); }',
        'html[data-mode="dark"] #' + GROUP_ID + ' .lz-sub { border-left-color: rgba(255,255,255,.12); }',
        'html[data-mode="dark"] #' + GROUP_ID + ' .lz-sub-item { color: #9ca3af; }',
        'html[data-mode="dark"] #' + GROUP_ID + ' .lz-sub-item:hover { background: rgba(255,255,255,.06); color: #f3f4f6; }'
    ].join('\n');

    function ensureStyle() {
        if (document.getElementById(SUB_STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = SUB_STYLE_ID;
        style.textContent = SUB_CSS;
        document.head.appendChild(style);
    }

    function openRoute(route) {
        if (typeof Luzzy.openAssistantAt === 'function') {
            Luzzy.openAssistantAt(route);
            return;
        }
        const raw = window.LuzzyBridge;
        if (raw && typeof raw.openAssistantAt === 'function') raw.openAssistantAt(route);
        else if (typeof Luzzy.openAssistant === 'function') Luzzy.openAssistant();
    }

    function buildGroup() {
        const group = document.createElement('div');
        group.id = GROUP_ID;
        const sub = document.createElement('div');
        sub.className = 'lz-sub';
        SUB_ENTRIES.forEach(function (entry) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'lz-sub-item';
            item.textContent = entry.label;
            item.addEventListener('click', function (ev) {
                ev.preventDefault();
                ev.stopPropagation();
                openRoute(entry.route);
            });
            sub.appendChild(item);
        });
        group.appendChild(sub);
        return group;
    }

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
        const group = document.getElementById(GROUP_ID);
        const spot = findAnchor();
        if (!spot) {
            if (existing) existing.remove();
            if (group) group.remove();
            return false;
        }
        ensureStyle();

        if (existing && existing.parentElement === spot.nav) {
            // 位置漂移（上游重排）→ 校正到锚点之前
            if (existing.nextElementSibling !== spot.anchor) {
                spot.nav.insertBefore(existing, spot.anchor);
            }
        } else {
            if (existing) existing.remove();
            spot.nav.insertBefore(buildEntry(), spot.anchor);
        }

        // 子项组紧随「助手」按钮之后（Vue 重渲染会整段移除 → 这里重建）
        const entry = document.getElementById(ENTRY_ID);
        if (group && group.previousElementSibling === entry && group.parentElement === spot.nav) {
            return true;
        }
        if (group) group.remove();
        if (entry && entry.parentElement === spot.nav) {
            entry.insertAdjacentElement('afterend', buildGroup());
        }
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

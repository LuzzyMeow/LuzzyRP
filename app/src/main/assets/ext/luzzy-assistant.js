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

    // ---- 助手可折叠组（用户 2026-09-09 指定：与「在线」「高级」完全同款） ----
    const GROUP_ID = 'luzzy-assistant-group';
    const PANEL_ID = 'luzzy-assistant-panel';
    const STYLE_ID = 'luzzy-assistant-style';

    // 触发按钮图标（铅笔线稿，与旧入口一致）
    const TRIGGER_ICON = '<svg class="w-5 h-5 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" ' +
        'd="M16.86 4.49l1.69-1.69a1.88 1.88 0 1 1 2.65 2.65L6.83 19.82a4.5 4.5 0 0 1-1.9 1.13l-2.68.8.8-2.69a4.5 4.5 0 0 1 1.13-1.9L16.86 4.49z"/>' +
        '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M15 6.5l2.5 2.5"/></svg>';

    // 与上游「在线」「高级」同款 chevron（展开旋转 90°）
    const CHEVRON = '<svg class="advanced-nav-chevron ml-auto w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>';

    /** 子项：label + 原生路由 + 图标 path（全部取自上游 index.html 的 SVG）。 */
    const SUB_ENTRIES = [
        // [用户 2026-09-10] 「对话」原用铅笔线稿，与「助手」触发按钮图标重复 → 改用侧栏
        // 「聊天」项自带的气泡图标（上游原图形，语义即「对话」，零自绘）
        { label: '对话', route: '', icon: 'M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z' },
        { label: '会话', route: 'conversations', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' },
        { label: '记忆', route: 'memory', icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z' },
        { label: '技能', route: 'skills', icon: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253' },
        { label: 'MCP', route: 'mcp', icon: 'M4 7c0 1.1 3.58 2 8 2s8-.9 8-2m-16 0c0-1.1 3.58-2 8-2s8 .9 8 2m-16 0v5c0 1.1 3.58 2 8 2s8-.9 8-2V7m-16 5v5c0 1.1 3.58 2 8 2s8-.9 8-2v-5' },
        { label: '工作区', route: 'workspace', icon: 'M4 7h16M4 12h10M4 17h7' },
        { label: '终端', route: 'terminal', icon: 'M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4' },
        { label: '设置', route: 'settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z' }
    ];

    /**
     * 子项样式统一（用户 2026-09-09 指定）：
     * 「在线」「高级」展开的子项改用**助手子项的样式与尺寸**——13px、7px/10px 内边距、
     * 10px 圆角、16px 图标，外层加一条 hairline 竖线作层级标记（亮/暗双模式）。
     */
    const SUB_CSS = [
        '.sidebar-nav .advanced-nav-list { margin: 2px 0 2px 14px; padding-left: 10px;',
        '  border-left: 1px solid rgba(0,0,0,.08); display: flex; flex-direction: column; gap: 1px; }',
        '.sidebar-nav .advanced-nav-item { min-height: 0; padding: 7px 10px; border-radius: 10px;',
        '  font-size: 13px; line-height: 1.2; color: #6b7280; text-align: left;',
        '  transition: background-color .18s ease, color .18s ease; }',
        '.sidebar-nav .advanced-nav-item svg { width: 16px; height: 16px; margin-right: 8px; }',
        '.sidebar-nav .advanced-nav-item:hover { background: rgba(0,0,0,.04); color: #111827; }',
        '.sidebar-nav .advanced-nav-item:active { background: rgba(0,0,0,.06); }',
        'html[data-mode="dark"] .sidebar-nav .advanced-nav-list { border-left-color: rgba(255,255,255,.12); }',
        'html[data-mode="dark"] .sidebar-nav .advanced-nav-item { color: #9ca3af; }',
        'html[data-mode="dark"] .sidebar-nav .advanced-nav-item:hover { background: rgba(255,255,255,.06); color: #f3f4f6; }'
    ].join('\n');

    function ensureStyle() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = SUB_CSS;
        document.head.appendChild(style);
    }

    /* 协同转场（用户 2026-09-10 指定）：侧栏展开时点助手子项 → 侧栏左收 + 页面左移 +
       助手覆盖层交叉淡化，三者同令牌（200ms / cubic-bezier(.23,1,.32,1)）同帧起跑。
       原生覆盖层由 Luzzy.openAssistantAt 触发（MainActivity 用同一令牌做 alpha 0→1），
       此处只负责 WebView 侧的两条位移，并在动画结束（= 覆盖层刚好 100%）后收尾。 */
    const HANDOFF_MS = 200;

    function withDrawerHandoff(open) {
        const sidebar = document.querySelector('.app-sidebar');
        const overlay = document.querySelector('.mobile-overlay');
        const drawerOpen = !!sidebar && sidebar.classList.contains('mobile-sidebar-open');
        if (!drawerOpen) { open(); return; }
        const root = document.documentElement;
        root.classList.add('lsp-handoff');
        open(); // 与侧栏收起同帧：覆盖层同步淡化，形成交叉淡化
        setTimeout(function () {
            if (sidebar) sidebar.classList.remove('mobile-sidebar-open');
            if (overlay) overlay.classList.remove('mobile-sidebar-open');
            root.classList.remove('lsp-handoff');
        }, HANDOFF_MS);
    }

    function openRoute(route) {
        withDrawerHandoff(function () {
            if (typeof Luzzy.openAssistantAt === 'function') {
                Luzzy.openAssistantAt(route);
                return;
            }
            const raw = window.LuzzyBridge;
            if (raw && typeof raw.openAssistantAt === 'function') raw.openAssistantAt(route);
            else if (typeof Luzzy.openAssistant === 'function') Luzzy.openAssistant();
        });
    }

    function findAnchor() {
        // [用户 2026-09-10] 「助手」改挂「聊天」之下（第二入口）：锚点 = 聊天按钮的下一个
        // 兄弟节点（inject 用 insertBefore，故组落在锚点之前 = 紧随聊天）。
        const nav = document.querySelector('.sidebar-nav');
        if (!nav) return null;
        const buttons = Array.from(nav.querySelectorAll('button.sidebar-nav-button'));
        const chat = buttons.find(function (b) {
            return (b.textContent || '').trim() === '聊天';
        });
        if (chat) {
            return { nav: chat.parentElement || nav, anchor: chat.nextElementSibling };
        }
        // 降级：上游若改掉「聊天」文案/结构，仍按旧锚点（外观）挂底部簇，避免入口整体消失
        const fallback = buttons.find(function (b) {
            return (b.textContent || '').trim() === '外观';
        });
        return fallback ? { nav: nav, anchor: fallback } : null;
    }

    /** 构建「助手」可折叠组（结构与上游 .advanced-nav 完全一致）。 */
    function buildGroup() {
        const wrap = document.createElement('div');
        wrap.id = GROUP_ID;
        wrap.className = 'advanced-nav';

        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.title = '助手';
        trigger.setAttribute('aria-controls', PANEL_ID);
        trigger.setAttribute('aria-expanded', 'false');
        trigger.className = 'sidebar-nav-button advanced-nav-trigger flex items-center rounded-xl ' +
            'transition-all duration-200 font-medium text-gray-600 hover:bg-gray-50 hover:text-gray-900 ' +
            'w-full px-3 py-2.5';
        trigger.innerHTML = TRIGGER_ICON +
            '<span class="whitespace-nowrap overflow-hidden">助手</span>' + CHEVRON;

        const panel = document.createElement('div');
        panel.id = PANEL_ID;
        panel.className = 'advanced-nav-panel';
        panel.setAttribute('aria-hidden', 'true');
        panel.setAttribute('inert', '');
        const inner = document.createElement('div');
        inner.className = 'advanced-nav-panel-inner';
        const list = document.createElement('div');
        list.className = 'advanced-nav-list';

        SUB_ENTRIES.forEach(function (entry) {
            const item = document.createElement('button');
            item.type = 'button';
            item.title = entry.label;
            item.className = 'sidebar-nav-button advanced-nav-item transition-all duration-200';
            item.innerHTML = '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">' +
                '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="' + entry.icon + '"></path></svg>' +
                '<span>' + entry.label + '</span>';
            item.addEventListener('click', function (ev) {
                ev.preventDefault();
                ev.stopPropagation();
                openRoute(entry.route);
            });
            list.appendChild(item);
        });

        inner.appendChild(list);
        panel.appendChild(inner);
        wrap.appendChild(trigger);
        wrap.appendChild(panel);

        trigger.addEventListener('click', function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            const open = wrap.classList.toggle('is-open');
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            panel.setAttribute('aria-hidden', open ? 'false' : 'true');
            if (open) panel.removeAttribute('inert');
            else panel.setAttribute('inert', '');
        });
        return wrap;
    }

    /** 注入（幂等）：把「助手」组放到「外观」之前。 */
    function inject() {
        // 旧版独立入口（如已注入）→ 移除
        const legacy = document.getElementById(ENTRY_ID);
        if (legacy) legacy.remove();

        const spot = findAnchor();
        const group = document.getElementById(GROUP_ID);
        if (!spot) {
            if (group) group.remove();
            return false;
        }
        ensureStyle();
        if (group && group.parentElement === spot.nav) {
            if (group.nextElementSibling !== spot.anchor) {
                spot.nav.insertBefore(group, spot.anchor);
            }
            return true;
        }
        if (group) group.remove();
        spot.nav.insertBefore(buildGroup(), spot.anchor);
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
        const trigger = document.querySelector('#' + GROUP_ID + ' .advanced-nav-trigger');
        if (trigger) {
            trigger.classList.toggle('bg-primary-50', !!visible);
            trigger.classList.toggle('text-primary-700', !!visible);
        }
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

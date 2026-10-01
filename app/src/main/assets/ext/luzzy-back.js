/**
 * LuzzyRP 扩展层 · 系统返回键接管（零上游改动）
 *
 * 为什么需要它：
 *   MainActivity 的返回键此前只有 `webView.canGoBack()` 一条判据。而上游 RP-Hub 是
 *   **不用 History API 的单页应用**（全仓无 pushState / popstate / hashchange），
 *   页面切换走响应式 `currentView` —— 于是 `canGoBack()` 恒为 false，
 *   **在任何非对话页按返回键都会直接退出应用**，而不是回到对话页。
 *   这与本项目既有的「非对话页 → 回对话页；对话页 → 退出」返回语义
 *   （见 docs/WORKLOG.md 助手侧记录）不一致，也与安卓用户预期不符。
 *
 * 做法（本文件是唯一主改动点；原生侧只加一个转发入口）：
 *   原生 MainActivity 在返回键时经 `evaluateJavascript` 调 `window.__luzzyHandleBack()`；
 *   返回 true = 页面已消费本次返回，原生不退出。
 *   接管链按「由内到外」逐级关闭：
 *     ① 弹窗/二级弹窗（模型编辑器 → 供应商编辑器 → 供应商管理器 → 其它已知弹层）
 *     ② AppNavigation 抽屉
 *     ③ 非对话页 → 回对话页
 *   全部无事可做时返回 false，交回原生退出。
 *
 * 降级：拿不到 Vue 实例 / 桥不可用 / 任一步抛错，一律返回 false（等价于改前行为）。
 *       绝不影响主流程（硬性规定 3）。
 *
 * 契约来源（均已实测核对，非推测）：
 *   - `isNavigationOpen` 是上游 `ref(false)`，在 setup() 返回列表中（L10197）——
 *     经组件代理读写即 `.value`，`closeNavigation()` 亦已暴露（L10184）。
 *   - `currentView` 为 setup() 暴露的 ref；上游存在 `selectView(view)` 入口。
 *   - patch 040 的模型编辑器（z-[70]）叠在 patch 015 的供应商编辑器（z-[60]）之上，
 *     故关闭顺序必须由内到外。
 */
(function () {
    'use strict';

    const VIEW_CHAT = 'chat';

    // 取根组件代理：与 tools/stream-render-test.cjs L84-86 同口径（已验证可用）
    function proxy() {
        try {
            const el = document.getElementById('app');
            const app = el && el.__vue_app__;
            const inst = app && ((app._container && app._container._vnode && app._container._vnode.component) || app._instance);
            return (inst && inst.proxy) || null;
        } catch (e) {
            return null;
        }
    }

    // 弹窗层：按「层级由内到外」（后开的先关）
    const MODAL_CHAIN = [
        'showModelEditor',            // patch 040 模型编辑二级弹窗（最内层）
        'showProviderEditor',         // patch 015 供应商编辑器
        'showProviderManager',        // patch 012 供应商管理器
        'showUiTemplateSettings',
        'showAddCharacterMenu',
        'showExportModal'
    ];

    function closeModal(p) {
        for (const name of MODAL_CHAIN) {
            try {
                if ((name in p) && p[name]) { p[name] = false; return true; }
            } catch (e) { /* 单个 flag 失败不影响整链 */ }
        }
        return false;
    }

    function closeDrawer(p) {
        try {
            // 只在确实开着时接管，避免「抽屉本来没开却吃掉返回键」
            if ('isNavigationOpen' in p && p.isNavigationOpen === true) {
                if (typeof p.closeNavigation === 'function') p.closeNavigation();
                else p.isNavigationOpen = false;
                return true;
            }
            return false;
        } catch (e) {
            return false;
        }
    }

    function backToChat(p) {
        try {
            if (!('currentView' in p) || p.currentView === VIEW_CHAT) return false;
            if (typeof p.selectView === 'function') p.selectView(VIEW_CHAT);
            else p.currentView = VIEW_CHAT;
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * 尝试消费一次返回键。
     * @returns {boolean} true = 页面已接管本次返回（原生不要退出应用）
     */
    function handleBack() {
        const p = proxy();
        if (!p) return false;
        if (closeModal(p)) return true;
        if (closeDrawer(p)) return true;
        if (backToChat(p)) return true;
        return false;
    }

    window.Luzzy = window.Luzzy || {};
    // 公开给诊断/门禁使用（与主流程无关）
    window.Luzzy.handleAndroidBack = handleBack;
    // 原生侧经 evaluateJavascript 调用的入口——必须挂在 window 上且返回 JSON 可序列化的布尔值
    window.__luzzyHandleBack = handleBack;
})();

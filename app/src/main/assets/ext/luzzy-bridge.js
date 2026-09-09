/**
 * LuzzyRP 扩展层 · 桥接封装（AGENTS.md §5.1）
 *
 * 所有对原生能力的调用必须走本文件封装：存在性检测 + 浏览器降级。
 * 本文件最先加载（早于 luzzy-ext.js），与上游 RP-Hub 文件无关。
 */
(function () {
    'use strict';

    const Luzzy = window.Luzzy || {};
    const bridge = window.LuzzyBridge;

    // ---- 剪贴板 ----
    Luzzy.copyToClipboard = function (text) {
        if (bridge && typeof bridge.copyToClipboard === 'function') {
            try {
                return bridge.copyToClipboard(String(text));
            } catch (e) { /* fall through */ }
        }
        // 降级：navigator.clipboard（https 或 localhost 场景）
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(String(text)).catch(function () {});
            return true;
        }
        return false;
    };

    // ---- Toast ----
    Luzzy.toast = function (message) {
        if (bridge && typeof bridge.toast === 'function') {
            try {
                bridge.toast(String(message));
                return;
            } catch (e) { /* fall through */ }
        }
        // 降级：控制台提示
        if (window.console && console.info) {
            console.info('[LuzzyRP] ' + message);
        }
    };

    // ---- 版本信息 ----
    Luzzy.getVersion = function () {
        let v = { versionName: 'dev', versionCode: 0, upstream: 'unknown', device: '' };
        try {
            if (bridge && typeof bridge.getAppVersion === 'function') v.versionName = bridge.getAppVersion();
            if (bridge && typeof bridge.getAppVersionCode === 'function') v.versionCode = bridge.getAppVersionCode();
            if (bridge && typeof bridge.getUpstreamVersion === 'function') v.upstream = bridge.getUpstreamVersion();
            if (bridge && typeof bridge.getDeviceInfo === 'function') v.device = bridge.getDeviceInfo();
        } catch (e) { /* keep defaults */ }
        return v;
    };

    // ---- 系统栏样式（主题切换配套） ----
    Luzzy.setSystemBarStyle = function (mode) {
        if (bridge && typeof bridge.setSystemBarStyle === 'function') {
            try {
                bridge.setSystemBarStyle(mode === 'dark' ? 'dark' : 'light');
            } catch (e) { /* fall through */ }
        }
    };

    // ---- 外部链接（系统浏览器打开；WebView 内 window.open 无 onCreateWindow 是 no-op） ----
    Luzzy.openUrl = function (url) {
        const target = String(url || '');
        if (!/^https?:\/\//i.test(target)) return false;
        if (bridge && typeof bridge.openUrl === 'function') {
            try {
                bridge.openUrl(target);
                return true;
            } catch (e) { /* fall through */ }
        }
        // 降级：浏览器环境直接跳转
        try {
            window.open(target, '_blank');
            return true;
        } catch (e) {
            return false;
        }
    };

    // ------------------------------------------------------------------
    // [LuzzyRP v1.5.0 助手] 桥接封装（PLAN §14）
    // 全部走「存在性检测 + 降级」；原生侧未接线时返回安全默认值，绝不抛错。
    // ------------------------------------------------------------------

    /** 侧栏「助手」入口 → 显示原生助手覆盖层。返回是否已交由原生处理。 */
    Luzzy.openAssistant = function () {
        if (bridge && typeof bridge.openAssistant === 'function') {
            try {
                bridge.openAssistant();
                return true;
            } catch (e) { /* fall through */ }
        }
        // 降级：桌面浏览器无原生层 → 提示（不阻断页面）
        Luzzy.toast('助手页需要在 LuzzyRP 应用内使用');
        return false;
    };

    /**
     * 侧栏「助手」子项入口 → 按指定页面打开助手（PLAN §3.3）。
     *
     * @param {string} route conversations / memory / skills / mcp / workspace / terminal / settings
     */
    Luzzy.openAssistantAt = function (route) {
        if (bridge && typeof bridge.openAssistantAt === 'function') {
            try {
                bridge.openAssistantAt(String(route || ''));
                return true;
            } catch (e) { /* fall through */ }
        }
        // 旧版原生侧没有 openAssistantAt → 退回首页入口
        if (bridge && typeof bridge.openAssistant === 'function') {
            try {
                bridge.openAssistant();
                return true;
            } catch (e) { /* fall through */ }
        }
        Luzzy.toast('助手页需要在 LuzzyRP 应用内使用');
        return false;
    };

    /**
     * 助手页左上角汉堡 → 回到 LuzzyRP 原侧栏（用户 2026-09-09 指定）。
     *
     * 由原生侧调用：先隐藏助手覆盖层，再执行本函数打开侧栏。
     */
    Luzzy.openRpSidebar = function () {
        try {
            if (!document.querySelector('.sidebar-nav')) return false;
            // 上游聊天页汉堡按钮内含 <use href="#icon-menu">；点它即 toggleMobileMenu 展开侧栏。
            // （不按 @click 属性选——Vue 编译后事件绑定不落在 DOM 属性上）
            var icon = document.querySelector('button svg use[href="#icon-menu"]');
            var toggle = icon && icon.closest ? icon.closest('button') : null;
            if (toggle && typeof toggle.click === 'function') {
                toggle.click();
                return true;
            }
            return false;
        } catch (e) {
            return false;
        }
    };

    /** 助手覆盖层是否可见（原生未接线时恒 false）。 */
    Luzzy.isAssistantVisible = function () {
        try {
            if (bridge && typeof bridge.isAssistantVisible === 'function') {
                return !!bridge.isAssistantVisible();
            }
        } catch (e) { /* fall through */ }
        return false;
    };

    /**
     * 推送 Web 端供应商配置（只读复用）。
     * 由 luzzy-assistant.js 组装；**内容含 API Key，禁止写入日志/控制台**。
     */
    Luzzy.pushAssistantConfig = function (json) {
        try {
            if (bridge && typeof bridge.setAssistantConfig === 'function') {
                bridge.setAssistantConfig(typeof json === 'string' ? json : JSON.stringify(json));
                return true;
            }
        } catch (e) { /* fall through */ }
        return false;
    };

    /** 读取最近一次推送的配置（未推送时返回空串）。 */
    Luzzy.getAssistantConfig = function () {
        try {
            if (bridge && typeof bridge.getAssistantConfig === 'function') {
                return bridge.getAssistantConfig() || '';
            }
        } catch (e) { /* fall through */ }
        return '';
    };

    /** 主题模式联动（助手覆盖层跟随 Web 端亮/暗）。 */
    Luzzy.setAssistantThemeMode = function (mode) {
        try {
            if (bridge && typeof bridge.setAssistantThemeMode === 'function') {
                bridge.setAssistantThemeMode(mode === 'dark' ? 'dark' : 'light');
            }
        } catch (e) { /* fall through */ }
    };

    /**
     * 原生 → JS 回调占位：助手显隐时由 MainActivity 调用（PLAN §14）。
     * 扩展层可覆盖此函数以暂停/恢复轮询等。
     */
    Luzzy.onAssistantVisibilityChanged = Luzzy.onAssistantVisibilityChanged || function () {};

    window.Luzzy = Luzzy;
})();

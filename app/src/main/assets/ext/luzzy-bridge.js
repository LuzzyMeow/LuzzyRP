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

    window.Luzzy = Luzzy;
})();

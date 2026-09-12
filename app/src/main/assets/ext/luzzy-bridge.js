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

    // ---- [v2.0 patch 050] 原生聊天传输（AGENTS §5.4：新桥接方法必须在本文件同步封装） ----
    // 三个方法的语义见 LuzzyBridge.kt；这里只做存在性检测 + 原样透传，**不做业务判断**
    // （业务判断在 ext/luzzy-chat-native.js 与 ext/luzzy-chat-offload.js）。
    // 一律不抛异常：桥不可用/原生报错时返回中性值，让上层静默降级到 JS 传输路径。
    Luzzy.chatTransportAvailable = function () {
        return !!(bridge
            && typeof bridge.chatStart === 'function'
            && typeof bridge.chatAbort === 'function'
            && typeof bridge.chatCapabilities === 'function');
    };
    Luzzy.chatStart = function (planJson) {
        if (!Luzzy.chatTransportAvailable()) return '';
        try {
            const jobId = bridge.chatStart(String(planJson));
            return typeof jobId === 'string' ? jobId : '';
        } catch (e) {
            return '';
        }
    };
    Luzzy.chatAbort = function (jobId) {
        if (!Luzzy.chatTransportAvailable()) return false;
        try {
            return bridge.chatAbort(String(jobId)) === true;
        } catch (e) {
            return false;
        }
    };
    Luzzy.chatCapabilities = function () {
        if (!Luzzy.chatTransportAvailable()) return { available: false, reason: 'no-native-bridge' };
        try {
            const raw = bridge.chatCapabilities();
            const parsed = (typeof raw === 'string') ? JSON.parse(raw) : raw;
            return (parsed && typeof parsed === 'object') ? parsed : { available: false, reason: 'bad-payload' };
        } catch (e) {
            return { available: false, reason: 'exception' };
        }
    };

    // ------------------------------------------------------------------
    // v3.0 数据迁移通道（使用方：ext/luzzy-migrate.html）
    //
    // 迁移页**不经 Vue、不加载 luzzy-bridge.js**（它与业务前端隔离），所以本封装的主要
    // 消费方是原生侧调试入口与后续的迁移 UI。封装仍必须存在（AGENTS §5.4：新增桥接方法
    // 必须同步本文件），且降级要明确——「没桥」不等于「成功导出了空数据」。
    // ------------------------------------------------------------------
    Luzzy.migrateAvailable = function () {
        return !!(bridge
            && typeof bridge.migrateStart === 'function'
            && typeof bridge.migrateChunk === 'function'
            && typeof bridge.migrateDone === 'function');
    };
    /** 开始一次导出，返回会话 id；不可用时返回空串。 */
    Luzzy.migrateStart = function (sessionId) {
        if (!Luzzy.migrateAvailable()) return '';
        try {
            const id = bridge.migrateStart(String(sessionId));
            return typeof id === 'string' ? id : '';
        } catch (e) {
            return '';
        }
    };
    /** 追加一块；返回是否被接受（false = 顺序错乱/落盘失败，调用方须停止）。 */
    Luzzy.migrateChunk = function (seq, payload) {
        if (!Luzzy.migrateAvailable()) return false;
        try {
            return bridge.migrateChunk(Number(seq) | 0, String(payload)) === true;
        } catch (e) {
            return false;
        }
    };
    /** 收尾；返回原生报告对象，失败返回 null。 */
    Luzzy.migrateDone = function (summary) {
        if (!Luzzy.migrateAvailable()) return null;
        try {
            const raw = bridge.migrateDone(typeof summary === 'string' ? summary : JSON.stringify(summary || {}));
            if (typeof raw !== 'string' || raw === '') return null;
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    };
    /** 失败出口（不吞错）。 */
    Luzzy.migrateError = function (message) {
        if (!Luzzy.migrateAvailable() || typeof bridge.migrateError !== 'function') return false;
        try {
            bridge.migrateError(String(message));
            return true;
        } catch (e) {
            return false;
        }
    };

    // ------------------------------------------------------------------
    // [v1.5.0 移除] 原「助手」桥接封装（Luzzy.openAssistant / openAssistantAt /
    // openRpSidebar / isAssistantVisible / push·getAssistantConfig /
    // setAssistantThemeMode / onAssistantVisibilityChanged）已随助手功能
    // 按用户指示于 2026-09-11 一并移除。
    // ------------------------------------------------------------------

    window.Luzzy = Luzzy;
})();

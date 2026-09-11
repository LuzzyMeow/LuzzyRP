/**
 * LuzzyRP 扩展层 · 原生聊天传输封装（v2.0）
 *
 * 把 LuzzyBridge 的三个原生方法（chatStart / chatAbort / chatCapabilities）包成
 * 一个干净的 API 面：`window.Luzzy.chatNative`。
 *
 * 纪律（AGENTS.md §5.1 / §5.3）：
 * - **存在性检测 + 降级**：桌面浏览器或旧 APK 上没有原生方法时，
 *   `ready()` 返回 false、`start()` 返回空串，绝不抛异常（抛出去会白屏）；
 * - **不接管请求路径**：本文件只提供 API，不 patch 上游 fetch，也不动 index.html；
 *   接线由前端集成方负责；
 * - **事件是推来的**：原生侧按 ~120ms 合批调用 `onEvent(jobId, evtJson)`，
 *   这里只做 JSON 解析 + 分发给 `setHandler` 注册的回调。
 *
 * 事件四型（与原生 ChatJobs 契约一一对应）：
 *   {type:'delta', content, reasoning, toolCalls?}
 *   {type:'usage', usage}
 *   {type:'done', finishReason}
 *   {type:'error', message, retryable}
 *
 * ⚠️ 调用顺序契约（**先注册后启动**）：
 *   事件是**推**过来的，不是排队等的——原生侧在 chatStart 返回后即可开始推 delta。
 *   因此必须 `setHandler(jobId, fn)` **先于** `start(plan)`（或紧随其后、同一同步块内），
 *   否则首个事件到达时还没有处理器，会被丢弃。jobId 由调用方在 plan 里给出，
 *   原生侧**原样回显**，不会另生成。
 *
 * 回归测试：`app/src/test/js/chat-native.test.cjs`（node 直跑，14 条）。
 * 其中「仅注册 per-job 处理器时 done/error 必须送达」锁死了 onEvent 的
 * **先交付、再摘处理器**顺序——写反过一次，后果是终态事件被静默丢弃、
 * 调用方 Promise 永不 settle（真机上表现为「一直生成中」）。
 */
(function () {
    'use strict';

    const Luzzy = window.Luzzy || {};

    /** 原生桥（可能是 undefined：桌面浏览器 / 未注入 / 旧 APK）。 */
    function nativeBridge() {
        const bridge = window.LuzzyBridge;
        return (bridge && typeof bridge === 'object') ? bridge : null;
    }

    /** 三个方法齐备才算「原生可用」——缺一个发出去只会拿到静默空串。 */
    function hasNative() {
        const bridge = nativeBridge();
        return !!bridge
            && typeof bridge.chatStart === 'function'
            && typeof bridge.chatAbort === 'function'
            && typeof bridge.chatCapabilities === 'function';
    }

    function warn(message, error) {
        if (window.console && console.warn) console.warn('[LuzzyRP] ' + message, error);
    }

    /** 每个 jobId 一个处理器；未单独注册时落到全局兜底。 */
    const handlers = new Map();
    let fallbackHandler = null;

    /** 取处理器并交付事件；处理器自身抛错不外溢（原生侧还在推后续事件）。 */
    function dispatch(jobId, event) {
        const handler = handlers.get(jobId) || fallbackHandler;
        if (typeof handler !== 'function') return;
        try {
            handler(event, jobId);
        } catch (e) {
            warn('chatNative 事件处理器抛错：', e);
        }
    }

    const chatNative = {
        /** 原生传输是否可用（UI 可据此决定是否显示「原生加速」开关）。 */
        ready: function () {
            return hasNative();
        },

        /**
         * 能力探测。原生可用时返回原生 JSON 解析后的对象；
         * 否则返回 {available:false, reason:'...'}——形状与原生一致，调用方无需分支。
         */
        capabilities: function () {
            if (!hasNative()) return { available: false, reason: 'no-native-bridge' };
            try {
                const raw = nativeBridge().chatCapabilities();
                const parsed = (typeof raw === 'string') ? JSON.parse(raw) : raw;
                return (parsed && typeof parsed === 'object')
                    ? parsed
                    : { available: false, reason: 'bad-payload' };
            } catch (e) {
                return { available: false, reason: 'exception' };
            }
        },

        /**
         * 启动一次原生传输。
         *
         * @param {object|string} plan 请求计划（对象或已序列化 JSON 串）
         * @returns {string} jobId；空串 = 原生不可用或参数非法（调用方应回退到 JS 传输）
         */
        start: function (plan) {
            if (!hasNative()) return '';
            let payload;
            try {
                payload = (typeof plan === 'string') ? plan : JSON.stringify(plan);
            } catch (e) {
                return '';
            }
            if (typeof payload !== 'string' || payload.length === 0) return '';
            try {
                const jobId = nativeBridge().chatStart(payload);
                return (typeof jobId === 'string') ? jobId : '';
            } catch (e) {
                return '';
            }
        },

        /** 中止任务；返回是否真的中止了（false = 任务已结束 / 不存在 / 原生不可用）。 */
        abort: function (jobId) {
            const id = String(jobId || '');
            // 无论原生结果如何都清掉本地处理器，避免长会话下 handlers 悬留
            if (id) handlers.delete(id);
            if (!id || !hasNative()) return false;
            try {
                return nativeBridge().chatAbort(id) === true;
            } catch (e) {
                return false;
            }
        },

        /**
         * 事件入口 —— **由原生侧调用**，业务代码不要直接调。
         * [evtJson] 是字符串形式的 JSON（原生 evaluateJavascript 拼串传入）。
         */
        onEvent: function (jobId, evtJson) {
            let event;
            try {
                event = (typeof evtJson === 'string') ? JSON.parse(evtJson) : evtJson;
            } catch (e) {
                return;
            }
            if (!event || typeof event.type !== 'string') return;
            const id = String(jobId || '');
            // [v2.0 修复] 必须**先交付再摘处理器**：dispatch 内部靠 handlers.get(id) 取处理器，
            // 若先 delete，终态事件（done / error）就取不到处理器而被静默丢弃
            // → 调用方的 Promise 永不 settle，真机上表现为「一直生成中」。
            dispatch(id, event);
            // 终态事件交付后摘掉处理器：原生侧同一 jobId 不会再有事件
            if (event.type === 'done' || event.type === 'error') handlers.delete(id);
        },

        /**
         * 注册事件处理器。
         * - `setHandler(fn)`        → 全局兜底（未单独注册的 job 都用它）
         * - `setHandler(jobId, fn)` → 指定 job
         * - `setHandler(null)`      → 清空全局兜底
         */
        setHandler: function (jobId, handler) {
            if (typeof jobId === 'function' || jobId === null || jobId === undefined) {
                fallbackHandler = (typeof jobId === 'function') ? jobId : null;
                return;
            }
            const id = String(jobId);
            if (typeof handler === 'function') handlers.set(id, handler);
            else handlers.delete(id);
        }
    };

    window.Luzzy = Luzzy;
    Luzzy.chatNative = chatNative;
})();

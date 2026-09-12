/**
 * LuzzyRP 扩展层 · 原生聊天传输「卸载」适配器（v2.0 · B 方案薄切 · patch 050 配套）
 *
 * 作用：把上游 `requestChatCompletion(options)` 的那次调用**可选地**交给原生 Kotlin 后端执行
 * （HTTP + SSE 解帧 + 三协议线格式 + 工具调用增量拼装 + 取消/超时/重试），
 * 而**上下文装配与渲染仍然全在 JS**（角色卡/世界书/记忆/预设/正则都在 JS 与 IndexedDB 里，
 * 远端与原生都无法替代 —— 这是「薄切」的定义）。
 *
 * 对外契约（与上游 api-utils.js 的 requestChatCompletion 同形）：
 *   Luzzy.chatOffload.canHandle(options) -> boolean
 *   Luzzy.chatOffload.request(options, fallback) -> Promise<{content, reasoning, toolCalls,
 *                                                           assistantMessage, usage, finishReason, isStream}>
 *   `fallback()` 是上游 JS 路径的等价调用；**任何环节出问题都要回落它**，绝不把异常抛给主流程。
 *
 * 无感知升级（硬性要求）：扩展层未加载 / 桥不可用 / 协议不支持 / 非流式 / 原生首帧即失败
 * → 一律 `canHandle()===false` 或直接 `return fallback()`，用户与上游代码都察觉不到。
 *
 * 降级（硬性规定 3）：本文件任何异常都不得影响主流程；所有入口都有 try/catch 兜底。
 */
(function () {
    'use strict';

    var Luzzy = window.Luzzy || {};

    /** 只支持这三种协议（与 api-utils.js 的 dispatch 一致）。 */
    var SUPPORTED_PROTOCOLS = ['openai', 'anthropic', 'gemini'];

    /**
     * 【默认值】原生传输**默认开启**（v2.0 全面升级：原生 Kotlin 就是默认传输路径）。
     *
     * 依据（全部实测，非推断）：
     *  · Kotlin 侧 207 条 JVM 单测（含 22 条真 socket：连接/分帧/空闲超时/取消/重试）
     *    + 17 条线格式保真断言；
     *  · Android 15 模拟器：`delta → usage → delta → done` 四型事件 + 拼回完整回复；
     *  · 用户真机（小米 / Android 16）：`capabilities` 报三协议、jobId 逐字节回显、
     *    `error` 终态事件成功回传（证明桥接投递链路可用）。
     *
     * 为什么还留 `localStorage` 开关：它**不是**让用户去开的「实验开关」，而是**逃生舱（kill switch）**——
     * 万一某台设备上原生路径出问题，用户不必等新版本，置 '0' 即刻回到 JS 路径。
     * 平时**无需任何人碰它**；默认值就是走原生。
     *     localStorage.setItem('luzzy_native_transport', '0')   // 仅排障时用：强制回 JS
     *     localStorage.removeItem('luzzy_native_transport')      // 恢复默认（走原生）
     *
     * 三重保险（原生出问题也不会让用户卡住）：
     *  ① 首帧失败静默回落：还没吐出任何增量就失败 → 直接走 JS，用户完全无感；
     *  ② 本会话熔断：原生一旦出错即停用，后续请求全走 JS，不重复踩同一个坑；
     *  ③ 上述逃生舱。JS 路径**保留不删不改**，永远是可用的兜底。
     */
    var ENABLED_BY_DEFAULT = true;
    var FLAG_KEY = 'luzzy_native_transport';

    /** 熔断：本会话内一旦原生出过错，就不再重试原生（避免每条消息都踩同一个坑）。 */
    var tripped = false;

    function isEnabled() {
        if (tripped) return false;
        try {
            var flag = window.localStorage.getItem(FLAG_KEY);
            if (flag === '1') return true;
            if (flag === '0') return false;
        } catch (e) { /* 隐私模式等：回落默认值 */ }
        return ENABLED_BY_DEFAULT;
    }

    function trip(reason) {
        if (tripped) return;
        tripped = true;
        if (window.console && console.warn) {
            console.warn('[LuzzyRP] 原生传输已熔断，本次会话后续请求全部走 JS 路径：' + reason);
        }
    }

    function nativeClient() {
        return (window.Luzzy && window.Luzzy.chatNative) || null;
    }

    /** 原生传输是否可用（桥存在 + 扩展层就绪 + 原生自检通过）。 */
    function available() {
        try {
            var client = nativeClient();
            return !!(client && typeof client.ready === 'function' && client.ready());
        } catch (e) { return false; }
    }

    /**
     * 与 api-utils.js:378-385 完全一致的「剥 OpenAI 路径」逻辑。
     * 非 openai 协议由各适配器拼自己的端点，故必须先剥成裸 base —— 原生侧按同一约定接收。
     */
    function stripOpenAiPath(url) {
        return String(url || '')
            .replace(/\/chat\/completions\s*$/, '')
            .replace(/\/embeddings\s*$/, '')
            .replace(/\/v1\s*$/, '')
            .replace(/\/+$/, '');
    }

    /** 本次调用能否交给原生（保守判定：任何不确定因素都回落 JS）。 */
    function canHandle(options) {
        try {
            if (!isEnabled()) return false;
            if (!available()) return false;
            if (!options || typeof options !== 'object') return false;
            var protocol = options.protocol || 'openai';
            if (SUPPORTED_PROTOCOLS.indexOf(protocol) === -1) return false;
            if (!options.url || !options.model) return false;
            // 只卸载流式请求：非流式在 JS 侧是一次 JSON.parse，卸载收益小、形态差异大。
            if (options.stream !== true) return false;
            if (typeof options.onDelta !== 'function') return false;
            return true;
        } catch (e) { return false; }
    }

    /** 组装原生侧约定的 plan（字段与 docs/PLAN-v2.0.md §3 W4 的契约一致）。 */
    function buildPlan(options, jobId) {
        var protocol = options.protocol || 'openai';
        var baseUrl = protocol === 'openai' ? options.url : stripOpenAiPath(options.url);
        return {
            jobId: jobId,
            protocol: protocol,
            baseUrl: baseUrl,
            apiKey: options.apiKey || '',
            model: options.model,
            temperature: (typeof options.temperature === 'number') ? options.temperature : null,
            reasoningEffort: options.reasoningEffort || null,
            maxTokens: options.maxTokens || null,
            stream: true,
            extraBody: (options.extraBody && typeof options.extraBody === 'object') ? options.extraBody : null,
            replyInTool: options.replyInTool === true,
            requireTool: options.requireTool === true,
            tools: Array.isArray(options.tools) ? options.tools : [],
            messages: Array.isArray(options.messages) ? options.messages : []
        };
    }

    /** 与 api-utils.js:181-182 同形的 assistant 消息（工具轮续写要用）。 */
    function buildAssistantMessage(plainContent, reasoning, toolCalls) {
        var message = { role: 'assistant', content: plainContent ? plainContent : null };
        if (reasoning) message.reasoning_content = reasoning;
        if (toolCalls && toolCalls.length) {
            message.tool_calls = toolCalls.map(function (call) {
                var copy = {};
                for (var key in call) { if (key !== 'index') copy[key] = call[key]; }
                return copy;
            });
        }
        return message;
    }

    function newJobId() {
        try {
            if (window.crypto && typeof window.crypto.randomUUID === 'function') return 'j-' + window.crypto.randomUUID();
        } catch (e) { /* 降级 */ }
        return 'j-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
    }

    /**
     * 走原生；不可用或**首帧即失败**时回落 `fallback()`。
     * 注意：已经吐出过增量之后再失败，就不能静默重试了（界面里已有半截内容），
     * 此时按上游语义把错误抛给调用方，由 app.js 既有的 catch 分支处理。
     */
    function request(options, fallback) {
        var doFallback = (typeof fallback === 'function') ? fallback : null;
        if (!canHandle(options)) return doFallback ? doFallback() : Promise.reject(new Error('原生传输不可用'));

        var client = nativeClient();
        var jobId = newJobId();
        var settled = false;
        var sawDelta = false;
        var content = '';
        var reasoning = '';
        var toolCalls = [];
        var usage = null;
        var finishReason = null;
        var startedAt = Date.now();

        return new Promise(function (resolve, reject) {
            var cleanup = function () {
                try { client.setHandler(jobId, null); } catch (e) { /* ignore */ }
                try {
                    if (options.signal) options.signal.removeEventListener('abort', onAbort);
                } catch (e) { /* ignore */ }
            };
            var finish = function (fn, arg) {
                if (settled) return;
                settled = true;
                cleanup();
                fn(arg);
            };
            var onAbort = function () {
                try { client.abort(jobId); } catch (e) { /* ignore */ }
                finish(reject, new DOMException('Generation cancelled by user', 'AbortError'));
            };

            /** 用量记账只上报一次（与 JS 路径 finally 里「收到过 payload 就记账」的语义对齐）。 */
            var usageReported = false;
            var reportUsage = function () {
                if (usageReported) return;
                usageReported = true;
                if (typeof options.onUsage !== 'function') return;
                var n = content.length + reasoning.length + toolCalls.reduce(function (sum, call) {
                    return sum + String((call && call.function && call.function.arguments) || '').length;
                }, 0);
                try {
                    options.onUsage(usage, {
                        isStream: true,
                        durationMs: Date.now() - startedAt,
                        finishReason: finishReason || null,
                        outputCharacters: n
                    });
                } catch (e) { /* 记账失败不得影响生成 */ }
            };

            client.setHandler(jobId, function (evt) {
                if (settled || !evt || typeof evt !== 'object') return;
                try {
                    if (evt.type === 'delta') {
                        var deltaContent = typeof evt.content === 'string' ? evt.content : '';
                        var deltaReasoning = typeof evt.reasoning === 'string' ? evt.reasoning : '';
                        var deltaTools = Array.isArray(evt.toolCalls) ? evt.toolCalls : null;
                        if (deltaContent || deltaReasoning || (deltaTools && deltaTools.length)) {
                            sawDelta = true;
                            if (deltaTools) toolCalls = deltaTools;
                            content += deltaContent;
                            reasoning += deltaReasoning;
                            options.onDelta({
                                content: deltaContent,
                                reasoning: deltaReasoning,
                                ...(deltaTools ? { toolCalls: deltaTools } : {})
                            });
                        }
                        return;
                    }
                    if (evt.type === 'usage') {
                        // [v2.0 patch 052] 只暂存，**不立即上报**：结束原因（finishReason）在 done 事件里，
                        // 若在这里就上报，记账层拿不到它，「截断定性」这件事在原生路径上依然做不到。
                        usage = evt.usage || null;
                        return;
                    }
                    if (evt.type === 'done') {
                        finishReason = evt.finishReason || null;
                        reportUsage();
                        var plainContent = content;
                        finish(resolve, {
                            content: content,
                            reasoning: reasoning,
                            toolCalls: toolCalls,
                            assistantMessage: toolCalls.length
                                ? buildAssistantMessage(plainContent, reasoning, toolCalls)
                                : null,
                            usage: usage,
                            finishReason: finishReason,
                            isStream: true
                        });
                        return;
                    }
                    if (evt.type === 'error') {
                        var message = String(evt.message || '原生传输失败');
                        // 原生出过错 → 本会话熔断，后续请求直接走 JS，不再重复踩坑
                        trip(message);
                        // 首帧就失败 → 静默回落 JS 路径，用户完全无感
                        // （回落时**不**记账：改由 JS 路径自己上报，避免同一次生成记两条）
                        if (!sawDelta && doFallback) { finish(resolve, doFallback()); return; }
                        reportUsage();   // 已经吐出过增量再失败：本轮的用量不能丢
                        finish(reject, new Error(message));
                    }
                } catch (e) {
                    trip('handler threw: ' + (e && e.message));
                    if (!settled) finish(reject, e);
                }
            });

            try {
                if (options.signal) {
                    if (options.signal.aborted) { onAbort(); return; }
                    options.signal.addEventListener('abort', onAbort, { once: true });
                }
                var accepted = client.start(buildPlan(options, jobId));
                if (!accepted) {
                    // 原生拒绝接管 → 回落（**不要**在这里 reject，否则会把可用路径也拖挂）
                    finish(resolve, doFallback ? doFallback() : Promise.reject(new Error('原生传输拒绝接管')));
                }
            } catch (e) {
                if (!settled) finish(resolve, doFallback ? doFallback() : Promise.reject(e));
            }
        });
    }

    Luzzy.chatOffload = {
        available: available,
        /** 当前是否启用原生传输（默认关；见 ENABLED_BY_DEFAULT 的说明）。 */
        enabled: isEnabled,
        /** 本会话是否已被熔断。 */
        tripped: function () { return tripped; },
        canHandle: canHandle,
        request: request,
        /** 调试/门禁用：原生自检原始输出。 */
        capabilities: function () {
            try {
                var client = nativeClient();
                return client && typeof client.capabilities === 'function' ? client.capabilities() : null;
            } catch (e) { return null; }
        }
    };
    window.Luzzy = Luzzy;
})();

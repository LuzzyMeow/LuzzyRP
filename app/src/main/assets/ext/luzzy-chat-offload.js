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
     * 【默认值】原生传输**默认关闭**。
     *
     * 为什么默认关：Kotlin 侧已完成 JVM 层验证（206 条单测，含真 socket 的 SSE 与线格式保真），
     * 但 **`evaluateJavascript` 的实际投递 / JavaBridge 线程行为 / WebView 生命周期竞态
     * 只能在真机上确认**（见 `docs/RESEARCH-v2.0-kotlin-transport.md` §6.1）。本仓库的发版纪律
     * 本就要求「先真机回归再发布」（AGENTS §6.1/§6.3），因此在真机验证通过之前，
     * 把未验证的路径设为默认走法会违背「用户无感知升级」。
     *
     * 开启方式（真机验证时用，无需改代码、无需新增 UI）：
     *     localStorage.setItem('luzzy_native_transport', '1')   // '0' 可强制关闭
     * 真机验证通过后，把下面这个常量改成 true 即为默认开启。
     */
    var ENABLED_BY_DEFAULT = false;
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
                        usage = evt.usage || null;
                        if (typeof options.onUsage === 'function') {
                            var n = content.length + reasoning.length + toolCalls.reduce(function (sum, call) {
                                return sum + String((call && call.function && call.function.arguments) || '').length;
                            }, 0);
                            options.onUsage(usage, { isStream: true, durationMs: Date.now() - startedAt, outputCharacters: n });
                        }
                        return;
                    }
                    if (evt.type === 'done') {
                        finishReason = evt.finishReason || null;
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
                        if (!sawDelta && doFallback) { finish(resolve, doFallback()); return; }
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

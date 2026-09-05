// Shared model API transport for chat, memory, templates and standalone pages.
(function () {
    const { extractApiErrorMessage, formatApiErrorMessage, getApiUsagePayload } = window.RPHubUtils;
    const { extractNativeReasoning, isNativeReasoningPart } = window.RPHubCardUtils;
    const buildApiEndpoint = (baseUrl, path) => {
        const root = String(baseUrl || '').replace(/\/+$/, '');
        const apiRoot = /\/v1$/i.test(root) ? root : `${root}/v1`;
        return `${apiRoot}/${String(path || '').replace(/^\/+/, '')}`;
    };

    // [LuzzyRP patch 015] 三协议适配助手（自旧 runtime-services 移植，1.9.1 传输层重构后
    // anthropic/gemini 适配器落位于本文件；openai 路径保持上游实现零改动）
    const throwApiError = (message) => {
        const error = new Error(message);
        error.isApiError = true;
        throw error;
    };
    const parsePayloadStrict = (rawText, status) => {
        const data = JSON.parse(rawText);
        const apiError = extractApiErrorMessage(data, status);
        if (apiError) throwApiError(apiError);
        return data;
    };
    const readFailedResponse = async (response) => {
        let detail = '';
        try {
            const rawText = await response.text();
            if (rawText) {
                try {
                    detail = parsePayloadStrict(rawText, response.status);
                } catch (error) {
                    if (error.isApiError) throw error;
                    detail = rawText;
                }
            }
        } catch (error) {
            if (error.isApiError) throw error;
        }
        throw new Error(formatApiErrorMessage(response.status, detail));
    };
    // [LuzzyRP patch 032] 流式渲染降载：60→120ms（openai 路径下方 interval 与适配器共用）
    const STREAM_RENDER_INTERVAL = 120;

    const parsePayload = (text, status) => {
        const data = JSON.parse(text);
        const error = extractApiErrorMessage(data, status);
        if (error) throw new Error(error);
        return data;
    };
    const readTextContent = value => Array.isArray(value)
        ? value.filter(part => !isNativeReasoningPart(part)).map(part => part?.text || part?.content || '').join('')
        : String(value || '');

    // 超时按“多久没有响应”计算，持续输出的长回复不会因总时长被中断。
    const withApiResponse = async (options, read) => {
        const controller = new AbortController();
        const abort = () => controller.abort();
        let timer;
        let timedOut = false;
        const touch = () => {
            clearTimeout(timer);
            timer = setTimeout(() => { timedOut = true; controller.abort(); }, options.timeoutMs ?? 120000);
        };
        if (options.signal?.aborted) abort();
        else options.signal?.addEventListener('abort', abort, { once: true });
        touch();
        try {
            const response = await fetch(options.url, {
                method: options.body === undefined ? 'GET' : 'POST',
                headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${options.apiKey}` },
                ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
                signal: controller.signal
            });
            touch();
            if (!response.ok) {
                const text = await response.text();
                let payload;
                try { payload = JSON.parse(text); } catch (_) { }
                throw new Error(extractApiErrorMessage(payload, response.status) || formatApiErrorMessage(response.status, text));
            }
            return await read(response, touch);
        } catch (error) {
            if (timedOut && !options.signal?.aborted) {
                const timeout = new Error('API 响应超时，请稍后重试');
                timeout.name = 'TimeoutError';
                throw timeout;
            }
            throw error;
        } finally {
            clearTimeout(timer);
            options.signal?.removeEventListener('abort', abort);
        }
    };

    const requestJson = options => withApiResponse(options, async response => parsePayload(await response.text(), response.status));

    const requestChatCompletion = async (options) => {
        // [LuzzyRP patch 015] 三协议适配：openai（上游原路径）| anthropic（Messages API）| gemini（GenerateContent API）。
        // 调用方传入的 url 是 OpenAI 形态（buildApiEndpoint 产物，含 /chat/completions）；
        // 非 openai 协议先剥掉 OpenAI 路径得到裸 base，再由各适配器拼自己的端点。
        const protocol = options.protocol || 'openai';
        if (protocol !== 'openai') {
            const stripped = String(options.url || '')
                .replace(/\/chat\/completions\s*$/, '')
                .replace(/\/embeddings\s*$/, '')
                .replace(/\/v1\s*$/, '')
                .replace(/\/+$/, '');
            options = { ...options, url: stripped };
        }
        if (protocol === 'anthropic') return requestAnthropicCompletion(options);
        if (protocol === 'gemini') return requestGeminiCompletion(options);
        const startedAt = Date.now();
        const result = { content: '', reasoning: '', usage: null, finishReason: null, isStream: false };
        let receivedPayload = false;
        let pendingContent = '';
        let pendingReasoning = '';
        const accept = data => {
            receivedPayload = true;
            result.usage = getApiUsagePayload(data) || result.usage;
            const choice = data.choices?.[0] || {};
            const message = choice.delta || choice.message || {};
            const content = readTextContent(message.content ?? choice.text);
            const reasoning = extractNativeReasoning(message) || extractNativeReasoning(choice) || '';
            result.content += content;
            result.reasoning += reasoning;
            result.finishReason = choice.finish_reason ?? result.finishReason;
            pendingContent += content;
            pendingReasoning += reasoning;
        };
        try {
            return await withApiResponse({ ...options, body: {
                model: options.model, messages: options.messages, temperature: options.temperature,
                ...(options.reasoningEffort ? { reasoning_effort: options.reasoningEffort } : {}),
                // [LuzzyRP patch 015] max_tokens（模型元数据）与供应商级 extraBody 注入
                ...(options.maxTokens ? { max_tokens: options.maxTokens } : {}),
                ...(options.extraBody || {}),
                stream: !!options.stream,
                ...(options.stream ? { stream_options: { include_usage: true } } : {})
            } }, async (response, touch) => {
                const eventStream = response.headers.get('content-type')?.includes('text/event-stream');
                let rawText;
                if (!eventStream) {
                    rawText = await response.text();
                    if (!/^\s*(?:data:|:)/.test(rawText)) {
                        accept(parsePayload(rawText, response.status));
                        return result;
                    }
                }
                result.isStream = !!options.stream;
                let buffer = '';
                let eventLines = [];
                let done = false;
                let flushPromise = Promise.resolve();
                const flush = () => {
                    if (!result.isStream || (!pendingContent && !pendingReasoning)) return;
                    const delta = { content: pendingContent, reasoning: pendingReasoning };
                    pendingContent = pendingReasoning = '';
                    flushPromise = flushPromise.then(() => options.onDelta?.(delta));
                    // 立即挂上处理器，最终仍由 await 抛出回调错误。
                    flushPromise.catch(() => {});
                };
                const dispatch = () => {
                    if (!eventLines.length) return;
                    const payload = eventLines.join('\n');
                    eventLines = [];
                    if (payload.trim() === '[DONE]') { done = true; return; }
                    if (payload.trim()) accept(parsePayload(payload, response.status));
                };
                const readLine = line => {
                    if (done) return;
                    if (!line.trim()) dispatch();
                    else if (line.startsWith('data:')) {
                        // 部分兼容接口省略事件间空行，但多行 JSON 仍需等它完整。
                        let complete = eventLines.join('\n').trim() === '[DONE]';
                        try { JSON.parse(eventLines.join('\n')); complete = true; } catch (_) { }
                        if (complete) dispatch();
                        if (!done) eventLines.push(line.slice(5).replace(/^ /, ''));
                    }
                };
                const feed = text => {
                    buffer += text;
                    const lines = buffer.split(/\r\n|\n|\r(?!$)/);
                    buffer = lines.pop();
                    lines.forEach(readLine);
                };
                const reader = rawText === undefined ? response.body.getReader() : null;
                const decoder = new TextDecoder();
                const interval = setInterval(flush, STREAM_RENDER_INTERVAL);
                try {
                    if (reader) {
                        while (!done) {
                            const chunk = await reader.read();
                            touch();
                            if (chunk.done) break;
                            feed(decoder.decode(chunk.value, { stream: true }));
                        }
                        feed(decoder.decode());
                    } else feed(rawText);
                    // 兼容缺失最后换行的完整 JSON；损坏 JSON 必须报错，不能伪装成功。
                    if (!done) { readLine(buffer.replace(/\r$/, '')); dispatch(); }
                    if (!receivedPayload) throw new Error('API 未返回有效的模型响应');
                    return result;
                } finally {
                    clearInterval(interval);
                    if (reader) {
                        try { await reader.cancel(); } catch (_) { }
                        reader.releaseLock();
                    }
                    flush();
                    await flushPromise;
                }
            });
        } finally {
            // 在业务层 JSON/模板校验之前记账；部分流式响应后中止也不会漏掉已返回的用量。
            if (receivedPayload) options.onUsage?.(result.usage, {
                isStream: result.isStream, durationMs: Date.now() - startedAt,
                outputCharacters: result.content.length + result.reasoning.length
            });
        }
    };

    // --- [LuzzyRP patch 015] Anthropic Messages 协议适配 ---
    // 图片消息转为 base64 source；system 从 messages 抽出；max_tokens 必填。
    const toAnthropicMessages = (messages) => {
        let system = '';
        const converted = [];
        messages.forEach((message, index) => {
            const role = message.role === 'assistant' ? 'assistant' : 'user';
            // 仅首条 user 纯文本消息视为 system（上游把 system prompt 放在 messages[0]）
            if (role === 'user' && typeof message.content === 'string' && index === 0 && messages.length > 1) {
                system = message.content;
                return;
            }
            let content = message.content;
            if (Array.isArray(content)) {
                content = content.map(part => {
                    if (part?.type === 'text') return { type: 'text', text: part.text || '' };
                    if (part?.type === 'image_url') {
                        const url = String(part.image_url?.url || '');
                        const match = url.match(/^data:([^;]+);base64,(.*)$/);
                        if (match) {
                            return { type: 'image', source: { type: 'base64', media_type: match[1], data: match[2] } };
                        }
                        return null;
                    }
                    return null;
                }).filter(Boolean);
            }
            // user 文本消息直接用字符串，避免空 content 数组
            converted.push({ role, content: Array.isArray(content)
                ? (content.length > 0 ? content : [{ type: 'text', text: '' }])
                : content });
        });
        // Anthropic 要求首条为 user：前置占位兜底（仅在全是 assistant 或为空时）
        if (converted.length === 0 || converted[0].role !== 'user') {
            converted.unshift({ role: 'user', content: [{ type: 'text', text: '(begin)' }] });
        }
        // 相邻同角色合并（Anthropic 严格交替，上游消息流可能产生连续 user）
        const mergedRoles = [];
        converted.forEach(message => {
            const last = mergedRoles[mergedRoles.length - 1];
            if (last && last.role === message.role) {
                const lastParts = Array.isArray(last.content) ? last.content : [{ type: 'text', text: String(last.content || '') }];
                const msgParts = Array.isArray(message.content) ? message.content : [{ type: 'text', text: String(message.content || '') }];
                last.content = [...lastParts, ...msgParts];
            } else {
                mergedRoles.push(message);
            }
        });
        return { system, messages: mergedRoles };
    };

    // [LuzzyRP patch 015] Anthropic thinking 预算守卫：budget 必须 < max_tokens 且 ≥1024，
    // 预算放不下时（max_tokens 过小）直接不启用 thinking，避免 API 400。
    const anthropicThinkingConfig = (maxTokens) => {
        const total = Number(maxTokens) || 8192;
        if (total < 2048) return null;
        const budget = Math.max(1024, Math.min(64000, Math.round(total * 0.75)));
        return budget < total ? { type: 'enabled', budget_tokens: budget } : null;
    };

    const requestAnthropicCompletionInternal = async (options) => {
        const { system, messages } = toAnthropicMessages(options.messages || []);
        const thinkingConfig = options.reasoningEffort ? anthropicThinkingConfig(options.maxTokens) : null;
        const response = await fetch(options.url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-api-key': options.apiKey,
                'anthropic-version': '2023-06-01',
                'anthropic-dangerous-direct-browser-access': 'true'
            },
            body: JSON.stringify({
                model: options.model,
                max_tokens: options.maxTokens || 8192,
                ...(system ? { system } : {}),
                messages,
                ...(Number.isFinite(options.temperature) ? { temperature: options.temperature } : {}),
                ...(thinkingConfig ? { thinking: thinkingConfig } : {}),
                ...(options.extraBody || {}),
                stream: options.stream
            }),
            signal: options.signal
        });
        if (!response.ok) await readFailedResponse(response);

        const parseAnthropicSseChunk = (text, status) => {
            const data = JSON.parse(text);
            const apiError = extractApiErrorMessage(data, status);
            if (apiError) throwApiError(apiError);
            let content = '';
            let reasoning = '';
            if (data.type === 'content_block_delta') {
                const delta = data.delta || {};
                if (delta.type === 'text_delta') content = delta.text || '';
                else if (delta.type === 'thinking_delta') reasoning = delta.thinking || '';
            } else if (data.type === 'message') {
                (data.content || []).forEach(block => {
                    if (block.type === 'text') content += block.text || '';
                    else if (block.type === 'thinking') reasoning += block.thinking || '';
                });
            }
            return { data, content, reasoning };
        };

        const contentType = response.headers.get('content-type');
        const isStream = !!(options.stream && contentType?.includes('text/event-stream'));
        if (!isStream) {
            const rawText = await response.text();
            // 服务端可能无视 stream:false 返回 SSE：复用上游逐行兜底解析
            if (contentType?.includes('text/event-stream')) {
                let content = '';
                let reasoning = '';
                let usage = null;
                for (const line of rawText.split('\n')) {
                    const trimmedLine = line.trim();
                    if (!trimmedLine.startsWith('data: ')) continue;
                    const payload = trimmedLine.slice(6);
                    if (payload === '[DONE]') continue;
                    try {
                        const chunk = parseAnthropicSseChunk(payload, response.status);
                        usage = getApiUsagePayload(chunk.data) || usage;
                        content += chunk.content;
                        reasoning += chunk.reasoning;
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                    }
                }
                return { content, reasoning, usage, isStream: true };
            }
            const data = parsePayloadStrict(rawText, response.status);
            let content = '';
            let reasoning = '';
            (data.content || []).forEach(block => {
                if (block.type === 'text') content += block.text || '';
                else if (block.type === 'thinking') reasoning += block.thinking || '';
            });
            return { content, reasoning, usage: getApiUsagePayload(data) || null, isStream: false };
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let usage = null;
        let pendingContent = '';
        let pendingReasoning = '';
        let flushPromise = Promise.resolve();
        const flushPending = () => {
            if (!pendingContent && !pendingReasoning) return;
            const delta = { content: pendingContent, reasoning: pendingReasoning };
            pendingContent = '';
            pendingReasoning = '';
            flushPromise = flushPromise.then(() => options.onDelta?.(delta));
        };
        const flushInterval = setInterval(flushPending, STREAM_RENDER_INTERVAL);
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop();
                for (const line of lines) {
                    const trimmedLine = line.trim();
                    if (!trimmedLine.startsWith('data: ')) continue;
                    const payload = trimmedLine.slice(6);
                    if (payload === '[DONE]') continue;
                    try {
                        const chunk = parseAnthropicSseChunk(payload, response.status);
                        usage = getApiUsagePayload(chunk.data) || usage;
                        pendingContent += chunk.content;
                        pendingReasoning += chunk.reasoning;
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                        console.warn('Error parsing anthropic stream chunk:', error);
                    }
                }
            }
            return { content: '', reasoning: '', usage, isStream: true };
        } finally {
            clearInterval(flushInterval);
            flushPending();
            await flushPromise;
        }
    };

    // --- [LuzzyRP patch 015] Google Gemini 协议适配 ---
    // url 形如 {base}/v1beta/models/{id}:streamGenerateContent?alt=sse&key=...；system 抽出为 systemInstruction。
    const GEMINI_THINKING_BUDGETS = { low: 1024, medium: 8192, high: 24576, max: 32768 };
    const requestGeminiCompletionInternal = async (options) => {
        const base = String(options.url || '').replace(/\/+$/, '');
        const method = options.stream ? 'streamGenerateContent?alt=sse&' : 'generateContent?';
        const url = `${base}/v1beta/models/${encodeURIComponent(options.model)}:${method}key=${encodeURIComponent(options.apiKey)}`;
        const contents = [];
        let systemInstruction = null;
        (options.messages || []).forEach((message, index) => {
            const role = message.role === 'assistant' ? 'model' : 'user';
            // 仅首条 user 纯文本消息视为 systemInstruction（上游把 system prompt 放在 messages[0]）
            if (role === 'user' && typeof message.content === 'string' && index === 0 && (options.messages || []).length > 1) {
                systemInstruction = { parts: [{ text: message.content }] };
                return;
            }
            let parts;
            if (Array.isArray(message.content)) {
                parts = message.content.map(part => {
                    if (part?.type === 'text') return { text: part.text || '' };
                    if (part?.type === 'image_url') {
                        const match = String(part.image_url?.url || '').match(/^data:([^;]+);base64,(.*)$/);
                        if (match) return { inlineData: { mimeType: match[1], data: match[2] } };
                        return null;
                    }
                    return null;
                }).filter(Boolean);
            } else {
                parts = [{ text: String(message.content || '') }];
            }
            if (parts.length > 0) {
                // 相邻同角色合并（Gemini 多轮期望交替，上游消息流可能产生连续 user）
                const last = contents[contents.length - 1];
                if (last && last.role === role) {
                    last.parts = [...last.parts, ...parts];
                } else {
                    contents.push({ role, parts });
                }
            }
        });
        const body = {
            contents,
            ...(systemInstruction ? { systemInstruction } : {}),
            generationConfig: {
                ...(Number.isFinite(options.temperature) ? { temperature: options.temperature } : {}),
                ...(options.maxTokens ? { maxOutputTokens: options.maxTokens } : {}),
                ...(options.reasoningEffort && GEMINI_THINKING_BUDGETS[options.reasoningEffort]
                    ? { thinkingConfig: { thinkingBudget: GEMINI_THINKING_BUDGETS[options.reasoningEffort] } } : {})
            },
            ...(options.extraBody || {})
        };
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
            signal: options.signal
        });
        if (!response.ok) await readFailedResponse(response);

        const parseGeminiChunk = (text, status) => {
            const data = JSON.parse(text);
            const apiError = extractApiErrorMessage(data, status);
            if (apiError) throwApiError(apiError);
            let content = '';
            let reasoning = '';
            const parts = data.candidates?.[0]?.content?.parts || [];
            parts.forEach(part => {
                if (typeof part.text !== 'string') return;
                if (part.thought === true) reasoning += part.text;
                else content += part.text;
            });
            return { data, content, reasoning };
        };

        const contentType = response.headers.get('content-type');
        const isStream = !!(options.stream && contentType?.includes('text/event-stream'));
        if (!isStream) {
            const rawText = await response.text();
            // 服务端可能无视 stream:false 返回 SSE：复用逐行兜底解析
            if (contentType?.includes('text/event-stream')) {
                let content = '';
                let reasoning = '';
                let usage = null;
                for (const line of rawText.split('\n')) {
                    const trimmedLine = line.trim();
                    if (!trimmedLine.startsWith('data: ')) continue;
                    const payload = trimmedLine.slice(6);
                    if (payload === '[DONE]') continue;
                    try {
                        const chunk = parseGeminiChunk(payload, response.status);
                        usage = getApiUsagePayload(chunk.data) || usage;
                        content += chunk.content;
                        reasoning += chunk.reasoning;
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                    }
                }
                return { content, reasoning, usage, isStream: true };
            }
            const parsed = parseGeminiChunk(rawText, response.status);
            return { content: parsed.content, reasoning: parsed.reasoning, usage: getApiUsagePayload(parsed.data) || null, isStream: false };
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let usage = null;
        let pendingContent = '';
        let pendingReasoning = '';
        let flushPromise = Promise.resolve();
        const flushPending = () => {
            if (!pendingContent && !pendingReasoning) return;
            const delta = { content: pendingContent, reasoning: pendingReasoning };
            pendingContent = '';
            pendingReasoning = '';
            flushPromise = flushPromise.then(() => options.onDelta?.(delta));
        };
        const flushInterval = setInterval(flushPending, STREAM_RENDER_INTERVAL);
        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop();
                for (const line of lines) {
                    const trimmedLine = line.trim();
                    if (!trimmedLine.startsWith('data: ')) continue;
                    const payload = trimmedLine.slice(6);
                    if (payload === '[DONE]') continue;
                    try {
                        const chunk = parseGeminiChunk(payload, response.status);
                        usage = getApiUsagePayload(chunk.data) || usage;
                        pendingContent += chunk.content;
                        pendingReasoning += chunk.reasoning;
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                        console.warn('Error parsing gemini stream chunk:', error);
                    }
                }
            }
            return { content: '', reasoning: '', usage, isStream: true };
        } finally {
            clearInterval(flushInterval);
            flushPending();
            await flushPromise;
        }
    };

    // [LuzzyRP patch 025] 适配器统一用量指标（与上游 openai 路径 onUsage 契约对齐）
    const withUsageMetrics = (adapter) => async (options) => {
        const startedAt = Date.now();
        const result = await adapter(options);
        options.onUsage?.(result.usage, {
            isStream: result.isStream, durationMs: Date.now() - startedAt,
            outputCharacters: (result.content || '').length + (result.reasoning || '').length
        });
        return result;
    };
    const requestAnthropicCompletion = withUsageMetrics(requestAnthropicCompletionInternal);
    const requestGeminiCompletion = withUsageMetrics(requestGeminiCompletionInternal);

    window.RPHubApiUtils = Object.freeze({ buildApiEndpoint });
    window.RPHubApiClient = Object.freeze({ requestChatCompletion, requestJson });
})();

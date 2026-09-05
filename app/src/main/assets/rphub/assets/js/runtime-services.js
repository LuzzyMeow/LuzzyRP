// RP-Hub runtime services: API transport, message rendering and application composables.

// --- API client ---
(function () {
    const {
        extractApiErrorMessage,
        formatApiErrorMessage,
        getApiUsagePayload
    } = window.RPHubUtils;
    const { extractNativeReasoning } = window.RPHubCardUtils;

    const throwApiError = (message) => {
        const error = new Error(message);
        error.isApiError = true;
        throw error;
    };

    const parsePayload = (rawText, status) => {
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
                    detail = parsePayload(rawText, response.status);
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

    const parseSsePayload = (text, status) => {
        const data = JSON.parse(text);
        const apiError = extractApiErrorMessage(data, status);
        if (apiError) throwApiError(apiError);
        const choice = data.choices?.[0];
        if (!choice) return { data, content: '', reasoning: '' };
        const delta = choice.delta || choice.message || {};
        return {
            data,
            content: delta.content || '',
            reasoning: extractNativeReasoning(delta) || extractNativeReasoning(choice)
        };
    };

    // [LuzzyRP patch 032] 流式渲染降载：60→120ms——全文重算链（正则→marked→DOMPurify→DOMParser）
    // 按此间隔触发，降频即近半流式 CPU 峰值（v1.3.0 性能；三协议 readStreamingResponse 共用）。
    const STREAM_RENDER_INTERVAL = 120;

    const readStreamingResponse = async (response, onDelta) => {
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
            flushPromise = flushPromise.then(() => onDelta?.(delta));
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
                        const chunk = parseSsePayload(payload, response.status);
                        usage = getApiUsagePayload(chunk.data) || usage;
                        pendingContent += chunk.content;
                        pendingReasoning += chunk.reasoning;
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                        console.warn('Error parsing stream chunk:', error);
                    }
                }
            }
            return { content: '', reasoning: '', usage };
        } finally {
            clearInterval(flushInterval);
            flushPending();
            await flushPromise;
        }
    };

    const readNonStreamingResponse = async (response) => {
        const rawText = await response.text();
        try {
            const data = parsePayload(rawText, response.status);
            const choice = data.choices?.[0] || {};
            const message = choice.message || {};
            return {
                content: message.content || '',
                reasoning: extractNativeReasoning(message) || extractNativeReasoning(choice),
                usage: getApiUsagePayload(data)
            };
        } catch (error) {
            if (error.isApiError) throw error;
        }

        let content = '';
        let reasoning = '';
        let usage = null;
        for (const line of rawText.split('\n')) {
            const trimmedLine = line.trim();
            if (!trimmedLine.startsWith('data:')) continue;
            const payload = trimmedLine.replace(/^data:\s*/, '');
            if (payload === '[DONE]') continue;
            try {
                const chunk = parseSsePayload(payload, response.status);
                usage = getApiUsagePayload(chunk.data) || usage;
                content += chunk.content;
                reasoning += chunk.reasoning;
            } catch (error) {
                if (error.isApiError) throw error;
                if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
            }
        }
        return { content, reasoning, usage };
    };

    const requestChatCompletion = async (options) => {
        // [LuzzyRP patch 015] 三协议适配：openai（原路径）| anthropic（Messages API）| gemini（GenerateContent API）
        // 调用方传入的 url 是 OpenAI 形态（buildApiEndpoint 产物，含 /v1/chat/completions）；
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
        const response = await fetch(options.url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${options.apiKey}`
            },
            body: JSON.stringify({
                model: options.model,
                messages: options.messages,
                temperature: options.temperature,
                ...(options.reasoningEffort ? { reasoning_effort: options.reasoningEffort } : {}),
                ...(options.maxTokens ? { max_tokens: options.maxTokens } : {}),
                ...(options.extraBody || {}),
                stream: options.stream,
                ...(options.stream ? { stream_options: { include_usage: true } } : {})
            }),
            signal: options.signal
        });
        if (!response.ok) await readFailedResponse(response);

        const contentType = response.headers.get('content-type');
        const isStream = !!(options.stream && contentType?.includes('text/event-stream'));
        const result = isStream
            ? await readStreamingResponse(response, options.onDelta)
            : await readNonStreamingResponse(response);
        return { ...result, isStream };
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

    const requestAnthropicCompletion = async (options) => {
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
            const data = parsePayload(rawText, response.status);
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
    const requestGeminiCompletion = async (options) => {
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

    window.RPHubApiClient = Object.freeze({ requestChatCompletion });
})();

// --- Message renderer ---
(function () {
    const MAX_CACHE_SIZE = 2000;

    const createMessageRenderer = ({ processRegex, replaceUserPlaceholder, createExecutableHtmlIframe, marked, DOMPurify }) => {
        const renderedCache = new Map();
        const frameDetectionCache = new Map();

        const cacheValue = (cache, key, value) => {
            cache.set(key, value);
            if (cache.size > MAX_CACHE_SIZE) cache.delete(cache.keys().next().value);
            return value;
        };

        const clearCaches = () => {
            renderedCache.clear();
            frameDetectionCache.clear();
        };

        const applyDisplayRegex = (text, role, skipRegex) => {
            const replaced = replaceUserPlaceholder(text);
            return skipRegex ? replaced : processRegex(replaced, { isDisplay: true, role });
        };

        const contentUsesHtmlFrame = (text, role = 'assistant', skipRegex = false) => {
            if (!text) return false;
            const cacheKey = `${role}_${skipRegex}_${text}`;
            if (frameDetectionCache.has(cacheKey)) return frameDetectionCache.get(cacheKey);

            const trimmed = applyDisplayRegex(text, role, skipRegex).trim();
            let usesFrame = false;
            const codeFencePattern = /```([^\n`]*)\n?([\s\S]*?)```/g;
            let codeMatch;
            while ((codeMatch = codeFencePattern.exec(trimmed)) !== null) {
                const language = codeMatch[1] || '';
                const content = codeMatch[2] || '';
                if (/\b(html|xml)\b/i.test(language)
                    || /^\s*<(!doctype|html|head|body|div|span|style|script|table|img)/i.test(content)) {
                    usesFrame = true;
                    break;
                }
            }
            if (!usesFrame && !trimmed.includes('```')) {
                usesFrame = /(<!doctype html>|<html\b[^>]*>|^\s*<(style|script)\b)/i.test(trimmed);
            }
            return cacheValue(frameDetectionCache, cacheKey, usesFrame);
        };

        const cleanConfig = {
            ADD_TAGS: ['details', 'summary', 'iframe', 'svg', 'path', 'g', 'circle', 'rect', 'defs', 'linearGradient', 'stop', 'style', 'div', 'span', 'script', 'button', 'input'],
            ADD_ATTR: ['style', 'open', 'srcdoc', 'sandbox', 'frameborder', 'allow', 'allowfullscreen', 'class', 'id', 'viewBox', 'fill', 'stroke', 'stroke-width', 'd', 'stroke-linecap', 'stroke-linejoin', 'x1', 'y1', 'x2', 'y2', 'offset', 'stop-color', 'stop-opacity', 'width', 'height', 'onclick', 'type', 'value', 'checked', 'data-slash'],
            FORBID_ATTR: ['onmouseover', 'onload'],
            FORCE_BODY: true
        };

        const sanitizeMarkdown = (text) => DOMPurify.sanitize(marked.parse(text), cleanConfig);
        const createIframe = (html) => createExecutableHtmlIframe(html, 'border-t border-gray-200 shadow-sm');

        const replaceHtmlCodeBlocks = (documentNode) => {
            let modified = false;
            documentNode.querySelectorAll('pre code').forEach(block => {
                const rawHtml = block.textContent;
                const hasHtmlLanguage = block.classList.contains('language-html') || block.classList.contains('language-xml');
                const looksLikeHtml = /^\s*<(!doctype|html|head|body|div|span|style|script|table|img)/i.test(rawHtml);
                if (!hasHtmlLanguage && !looksLikeHtml) return;
                const pre = block.parentElement;
                if (!pre?.parentNode) return;
                pre.parentNode.replaceChild(createIframe(rawHtml), pre);
                modified = true;
            });
            return modified;
        };

        const replaceEscapedHtmlParagraphs = (documentNode) => {
            let modified = false;
            documentNode.querySelectorAll('p').forEach(paragraph => {
                if (!/^\s*</.test(paragraph.innerHTML)) return;
                const rawHtml = paragraph.textContent;
                if (!/^\s*<(!doctype|html|head|body|div|span|style|script|table|img)/i.test(rawHtml)) return;
                if (!paragraph.parentNode) return;
                paragraph.parentNode.replaceChild(createIframe(rawHtml), paragraph);
                modified = true;
            });
            return modified;
        };

        const replaceScriptedPanels = (documentNode) => {
            let modified = false;
            documentNode.querySelectorAll('div[style*="position"], div[style*="background"], div[class*="panel"]').forEach(panel => {
                if (!panel.querySelector('script') || !panel.parentNode) return;
                panel.parentNode.replaceChild(createIframe(panel.outerHTML), panel);
                modified = true;
            });
            return modified;
        };

        // [LuzzyRP patch 032] options.cache=false 旁路 LRU：流式期全文逐 tick 变化，中间串写缓存
        // 只会灌满 2000 上限引发内存膨胀与驱逐抖动（v1.3.0 性能；唯一调用方=index.html 流式分支）。
        const renderMarkdown = (text, role = 'assistant', skipRegex = false, options = {}) => {
            if (!text) return '';
            const cacheKey = `${role}_${skipRegex}_${text}`;
            const allowCache = options.cache !== false;
            if (allowCache && renderedCache.has(cacheKey)) return renderedCache.get(cacheKey);

            let processed = applyDisplayRegex(text, role, skipRegex);
            const trimmed = processed.trim();
            const htmlMatch = trimmed.match(/(<!doctype html>|<html\b[^>]*>)/i);

            if (htmlMatch && !trimmed.includes('```')) {
                const startIndex = htmlMatch.index;
                const closeTag = '</html>';
                const closeIndex = trimmed.toLowerCase().lastIndexOf(closeTag);
                const hasCloseTag = closeIndex !== -1 && closeIndex > startIndex;
                const endIndex = hasCloseTag ? closeIndex + closeTag.length : trimmed.length;
                const htmlContent = trimmed.substring(startIndex, endIndex);
                const preText = trimmed.substring(0, startIndex);
                const postText = hasCloseTag ? trimmed.substring(endIndex) : '';
                const container = document.createElement('div');
                container.className = 'html-card-container';
                container.style.margin = '0';
                container.style.paddingBottom = '0';
                container.style.marginBottom = '-1px';
                container.appendChild(createIframe(htmlContent));
                const result = [
                    preText.trim() ? sanitizeMarkdown(preText) : '',
                    container.outerHTML,
                    postText.trim() ? sanitizeMarkdown(postText) : ''
                ].join('');
                return allowCache ? cacheValue(renderedCache, cacheKey, result) : result;
            }

            if (/^\s*<(div|table|section|article|aside|header|footer|style|script)/i.test(trimmed)
                && !trimmed.includes('```')) {
                const sanitizedBlock = DOMPurify.sanitize(processed, cleanConfig);
                return allowCache ? cacheValue(renderedCache, cacheKey, sanitizedBlock) : sanitizedBlock;
            }

            const lowerTrimmed = trimmed.toLowerCase();
            if (lowerTrimmed.includes('<html') || lowerTrimmed.includes('<!doctype')) {
                processed = processed
                    .replace(/<!DOCTYPE html>/gi, '')
                    .replace(/<\/?html[^>]*>/gi, '')
                    .replace(/<\/?head[^>]*>/gi, '')
                    .replace(/<\/?body[^>]*>/gi, '');
            }

            const html = sanitizeMarkdown(processed);
            try {
                const documentNode = new DOMParser().parseFromString(html, 'text/html');
                const codeBlocksChanged = replaceHtmlCodeBlocks(documentNode);
                const paragraphsChanged = replaceEscapedHtmlParagraphs(documentNode);
                const panelsChanged = replaceScriptedPanels(documentNode);
                const modified = codeBlocksChanged || paragraphsChanged || panelsChanged;
                if (modified) {
                    const frameHtml = documentNode.body.innerHTML;
                    return allowCache ? cacheValue(renderedCache, cacheKey, frameHtml) : frameHtml;
                }
            } catch (error) {
                console.error('Error rendering HTML preview:', error);
            }
            return allowCache ? cacheValue(renderedCache, cacheKey, html) : html;
        };

        return { clearCaches, contentUsesHtmlFrame, renderMarkdown };
    };

    window.RPHubMessageRenderer = Object.freeze({ createMessageRenderer });
})();

// --- Application composables ---
(function () {
    const { computed, reactive, ref, watch } = Vue;

    const useTokenUsage = ({
        pageSize = 10,
        cloneForStorage,
        confirm,
        ensureStorage,
        generateUUID,
        getApiKey,
        getApiUrl,
        normalizeApiUsage,
        saveStoredValue,
        toast
    }) => {
        const tokenUsageHistory = ref([]);
        const tokenUsagePage = ref(1);
        const tokenUsageFilter = ref('all');
        const tokenUsageTimeFilter = ref('all');
        const showTokenUsageTimeFilter = ref(false);
        const tokenUsageTimeFilterOptions = Object.freeze([
            { value: 'all', label: '全部' },
            { value: '24h', label: '24小时' },
            { value: '7d', label: '7天' },
            { value: '30d', label: '30天' }
        ]);
        const tokenUsageTimeRanges = Object.freeze({
            '24h': 24 * 60 * 60 * 1000,
            '7d': 7 * 24 * 60 * 60 * 1000,
            '30d': 30 * 24 * 60 * 60 * 1000
        });
        const tokenUsageTimeFilterLabel = computed(() => (
            tokenUsageTimeFilterOptions.find(option => option.value === tokenUsageTimeFilter.value)?.label || '全部'
        ));
        const getTokenUsageCategory = (type) => {
            if (['summary', 'embedding'].includes(type)) return 'memory';
            if (type === 'ui_template') return 'variables';
            return 'chat';
        };
        const filteredTokenUsageHistory = computed(() => {
            const timeRange = tokenUsageTimeRanges[tokenUsageTimeFilter.value];
            const cutoff = timeRange ? Date.now() - timeRange : 0;
            return tokenUsageHistory.value.filter(record => {
                const matchesType = tokenUsageFilter.value === 'all'
                    || getTokenUsageCategory(record.type) === tokenUsageFilter.value;
                if (!matchesType || !timeRange) return matchesType;
                const timestamp = Number(record.timestamp);
                return Number.isFinite(timestamp) && timestamp >= cutoff;
            });
        });
        const getUncachedInputTokens = (record) => {
            if (!Number.isFinite(record?.inputTokens)) return null;
            const cached = Number.isFinite(record.cacheReadTokens) ? record.cacheReadTokens : 0;
            return Math.max(0, record.inputTokens - cached);
        };
        const tokenUsageStats = computed(() => filteredTokenUsageHistory.value.reduce((stats, record) => {
            const inputTokens = getUncachedInputTokens(record);
            if (inputTokens !== null) {
                stats.inputTokens += inputTokens;
                stats.inputTokensReports++;
            }
            ['outputTokens', 'cacheReadTokens'].forEach(key => {
                if (!Number.isFinite(record[key])) return;
                stats[key] += record[key];
                stats[`${key}Reports`]++;
            });
            return stats;
        }, {
            inputTokens: 0,
            inputTokensReports: 0,
            outputTokens: 0,
            outputTokensReports: 0,
            cacheReadTokens: 0,
            cacheReadTokensReports: 0
        }));
        const tokenUsagePageCount = computed(() => Math.max(
            1,
            Math.ceil(filteredTokenUsageHistory.value.length / pageSize)
        ));
        const displayedTokenUsageHistory = computed(() => {
            const start = (tokenUsagePage.value - 1) * pageSize;
            return filteredTokenUsageHistory.value.slice(start, start + pageSize);
        });
        const latestMainTokenUsage = computed(() => tokenUsageHistory.value.find(
            record => record.type === 'chat' || record.type === 'tool_continuation'
        ) || null);
        const formatLatestTokenCount = value => `${(Number(value || 0) / 1000).toFixed(2)}k`;
        const formatLatestUsageCost = quota => Number.isFinite(quota)
            ? `¥${(Math.trunc(quota / 500000 * 10000) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`
            : '--';

        let saveQueue = Promise.resolve();
        const saveTokenUsageHistoryNow = () => {
            const snapshot = cloneForStorage(tokenUsageHistory.value);
            const saveTask = async () => {
                await ensureStorage();
                await saveStoredValue('token_usage_history', snapshot, { clone: false });
            };
            saveQueue = saveQueue.then(saveTask, saveTask);
            return saveQueue;
        };
        const fetchLatestQuota = async (record, apiKey) => {
            try {
                const getLogKey = log => String(log?.request_id || [log?.created_at, log?.model_name, log?.prompt_tokens, log?.completion_tokens].join('|'));
                for (const delay of [500, 5000]) {
                    await new Promise(resolve => setTimeout(resolve, delay));
                    const apiRoot = record.apiUrl.replace(/\/+$/, '').replace(/\/v1$/i, '');
                    const response = await fetch(`${apiRoot}/api/log/token`, {
                        headers: { Authorization: `Bearer ${apiKey}` }
                    });
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    const payload = await response.json();
                    const logs = Array.isArray(payload?.data) ? payload.data : (payload?.data?.items || []);
                    const claimedLogs = new Set(tokenUsageHistory.value.map(item => item.usageLogKey).filter(Boolean));
                    const log = logs.filter(item => !claimedLogs.has(getLogKey(item))
                        && Number(item?.type) === 2
                        && String(item?.model_name || '') === record.model
                        && Math.abs(Number(item?.created_at) * 1000 - record.timestamp) < 120000
                        && (!Number.isFinite(record.inputTokens) || Number(item?.prompt_tokens) === record.inputTokens)
                        && (!Number.isFinite(record.outputTokens) || Number(item?.completion_tokens) === record.outputTokens))
                        .sort((a, b) => Math.abs(Number(a.created_at) * 1000 - record.timestamp) - Math.abs(Number(b.created_at) * 1000 - record.timestamp))[0];
                    if (!log || !Number.isFinite(Number(log.quota))) continue;
                    record.actualQuota = Number(log.quota);
                    record.usageGroup = String(log.group || '');
                    record.usageLogKey = getLogKey(log);
                    saveTokenUsageHistoryNow().catch(error => console.error('Token usage history save failed:', error));
                    return;
                }
            } catch (error) {
                console.warn('New API usage log fetch failed:', error);
            }
        };
        const recordApiUsage = (usage, meta = {}) => {
            const record = reactive({
                id: generateUUID(),
                timestamp: Date.now(),
                type: meta.type || 'chat',
                model: String(meta.model || ''),
                // [LuzzyRP patch 025] provider/protocol 维度落库（v1.2.3）：patch 012 调用侧已传参但构造器漏存，
                // 导致用量列表 [商名] 前缀与供应商图表分组失效；历史记录由图表层按 apiUrl 反查兜底
                provider: String(meta.provider || ''),
                protocol: String(meta.protocol || ''),
                apiUrl: String(getApiUrl?.() || ''),
                isStream: meta.isStream === true,
                durationMs: Number.isFinite(meta.durationMs) ? Math.max(0, meta.durationMs) : null,
                outputCharacters: Number.isFinite(meta.outputCharacters) ? Math.max(0, meta.outputCharacters) : null,
                ...normalizeApiUsage(usage)
            });
            tokenUsageHistory.value.unshift(record);
            const apiKey = String(getApiKey?.() || '').trim();
            if (record.apiUrl && apiKey) fetchLatestQuota(record, apiKey);
            saveTokenUsageHistoryNow().catch(error => console.error('Token usage history save failed:', error));
        };
        const clearTokenUsageHistory = () => {
            confirm('确定要清空全部 Token 用量记录吗？此操作无法撤销。', async () => {
                tokenUsageHistory.value = [];
                tokenUsagePage.value = 1;
                await saveTokenUsageHistoryNow();
                toast('Token 用量记录已清空', 'success');
            });
        };

        watch([tokenUsageFilter, tokenUsageTimeFilter], () => { tokenUsagePage.value = 1; });
        watch(tokenUsagePageCount, count => { tokenUsagePage.value = Math.min(tokenUsagePage.value, count); });

        return {
            clearTokenUsageHistory,
            displayedTokenUsageHistory,
            filteredTokenUsageHistory,
            formatLatestTokenCount,
            formatLatestUsageCost,
            formatTokenAggregate: (value, reports) => {
                if (reports <= 0 || value <= 0) return '0';
                if (value >= 100000000) return `${Number((value / 100000000).toFixed(2))}亿`;
                if (value >= 10000) return `${Number((value / 10000).toFixed(2))}万`;
                return value.toLocaleString();
            },
            formatTokenCount: (value) => Number.isFinite(value) ? value.toLocaleString() : '0',
            formatTokenUsageTime: (timestamp) => new Date(timestamp).toLocaleString('zh-CN', { hour12: false }),
            getTokenUsageTypeLabel: (type) => ({ chat: '主对话', memory: '记忆系统', variables: '变量分析' })[getTokenUsageCategory(type)],
            getUncachedInputTokens,
            latestMainTokenUsage,
            recordApiUsage,
            saveTokenUsageHistoryNow,
            showTokenUsageTimeFilter,
            tokenUsageFilter,
            tokenUsageHistory,
            tokenUsagePage,
            tokenUsagePageCount,
            tokenUsageStats,
            tokenUsageTimeFilter,
            tokenUsageTimeFilterLabel,
            tokenUsageTimeFilterOptions
        };
    };

    const useStorageManagement = ({
        characters,
        confirm,
        deleteStorageKeys,
        ensureStorage,
        getBranchOwnerId,
        getLegacyDb,
        getMainDb,
        getStorageLogicalKey,
        globalUiTemplates,
        memorySettings,
        readStorageKeys,
        saveMemorySettings,
        saveStoredValue,
        scanStorageEntries,
        scopedStorageNames,
        toast
    }) => {
        const categories = Object.freeze([
            { key: 'characters', label: '角色卡', color: '#2563eb' },
            { key: 'chat', label: '聊天记录', color: '#3b82f6' },
            { key: 'vector', label: '向量记忆', color: '#0ea5e9' },
            { key: 'classic', label: '总结记忆', color: '#38bdf8' },
            { key: 'other', label: '其他', color: '#94a3b8' }
        ]);
        const storageStats = reactive({
            loading: false,
            cleaning: false,
            hasMeasured: false,
            error: '',
            usage: 0,
            quota: 0,
            orphanedBytes: 0,
            orphanedItems: 0,
            categories: []
        });
        let unusedSnapshot = { mainKeys: [], legacyKeys: [], emptyTurnKeys: [], templateRuntimeKeys: [] };

        const formatStorageSize = (bytes) => {
            const size = Math.max(0, Number(bytes) || 0);
            if (size < 1024) return `${Math.round(size)} B`;
            const units = ['KB', 'MB', 'GB'];
            let value = size / 1024;
            let unit = units[0];
            for (let index = 1; index < units.length && value >= 1024; index++) {
                value /= 1024;
                unit = units[index];
            }
            return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)} ${unit}`;
        };
        const getScopedStorageInfo = (logicalKey) => {
            for (const name of scopedStorageNames) {
                const prefix = `${name}_`;
                if (logicalKey.startsWith(prefix)) return { name, id: logicalKey.slice(prefix.length) };
            }
            return null;
        };
        const getStorageCategory = (logicalKey) => {
            if (logicalKey === 'characters') return 'characters';
            if (logicalKey.startsWith('chat_')) return 'chat';
            if (logicalKey.startsWith('memories_')) return 'vector';
            if (logicalKey.startsWith('classic_memories_')) return 'classic';
            return 'other';
        };
        const estimateStorageValueSize = (value, seen = new WeakSet()) => {
            if (value == null) return 0;
            if (typeof value === 'string') return value.length * 2;
            if (typeof value === 'number' || typeof value === 'bigint') return 8;
            if (typeof value === 'boolean') return 4;
            if (typeof value !== 'object') return 0;
            if (value instanceof Blob) return value.size;
            if (value instanceof ArrayBuffer) return value.byteLength;
            if (ArrayBuffer.isView(value)) return value.byteLength;
            if (seen.has(value)) return 0;
            seen.add(value);

            let bytes = 0;
            if (Array.isArray(value)) {
                if (value.length && typeof value[0] === 'number') return value.length * 8;
                value.forEach(item => { bytes += estimateStorageValueSize(item, seen); });
            } else {
                Object.keys(value).forEach(key => {
                    bytes += key.length * 2 + estimateStorageValueSize(value[key], seen);
                });
            }
            return bytes;
        };
        const estimateStorageEntrySize = (key, value) => String(key).length * 2 + estimateStorageValueSize(value);

        const refreshStorageStats = async () => {
            if (storageStats.loading) return;
            storageStats.loading = true;
            storageStats.error = '';
            try {
                await ensureStorage();
                const [mainKeys, legacyKeys, estimate] = await Promise.all([
                    readStorageKeys(getMainDb()),
                    readStorageKeys(getLegacyDb()),
                    navigator.storage?.estimate?.().catch(() => ({})) || Promise.resolve({})
                ]);
                const mainLogicalKeys = new Set(mainKeys.map(getStorageLogicalKey));
                const scopedLogicalKeys = new Set([...mainKeys, ...legacyKeys]
                    .map(getStorageLogicalKey)
                    .filter(logicalKey => getScopedStorageInfo(logicalKey)));
                const liveCharacterIds = new Set(characters.value.map(character => character?.uuid).filter(Boolean));
                const isOrphanedEntry = (source, logicalKey) => {
                    if (source === 'legacy' && mainLogicalKeys.has(logicalKey)) return true;
                    const scoped = getScopedStorageInfo(logicalKey);
                    if (!scoped || liveCharacterIds.has(getBranchOwnerId(scoped.id))) return false;
                    if (scoped.name !== 'chat' || !/^\d+$/.test(scoped.id)) return true;
                    const character = characters.value[Number(scoped.id)];
                    return !character || (character.uuid && scopedLogicalKeys.has(`chat_${character.uuid}`));
                };

                const categoryBytes = new Map(categories.map(category => [category.key, 0]));
                const orphanedKeys = { main: [], legacy: [] };
                let orphanedEntryBytes = 0;
                const inspectEntry = (source, key, value) => {
                    const logicalKey = getStorageLogicalKey(key);
                    const bytes = estimateStorageEntrySize(key, value);
                    const category = getStorageCategory(logicalKey);
                    categoryBytes.set(category, categoryBytes.get(category) + bytes);
                    if (isOrphanedEntry(source, logicalKey)) {
                        orphanedKeys[source].push(key);
                        orphanedEntryBytes += bytes;
                    }
                };
                await scanStorageEntries(getMainDb(), 'main', inspectEntry);
                await scanStorageEntries(getLegacyDb(), 'legacy', inspectEntry);

                const emptyTurnKeys = Object.keys(memorySettings.emptyTurns || {})
                    .filter(key => key.endsWith(':vector') && !liveCharacterIds.has(getBranchOwnerId(key.slice(0, -7))));
                const templateRuntimeKeys = [];
                globalUiTemplates.value.forEach((template, templateIndex) => {
                    Object.keys(template.runtimeByCharacter || {}).forEach(characterId => {
                        if (!liveCharacterIds.has(getBranchOwnerId(characterId))) {
                            templateRuntimeKeys.push({ templateIndex, characterId });
                        }
                    });
                });
                const embeddedOrphanBytes = emptyTurnKeys.reduce((total, key) => (
                    total + estimateStorageEntrySize(key, memorySettings.emptyTurns[key])
                ), 0) + templateRuntimeKeys.reduce((total, item) => (
                    total + estimateStorageEntrySize(
                        item.characterId,
                        globalUiTemplates.value[item.templateIndex]?.runtimeByCharacter?.[item.characterId]
                    )
                ), 0);

                try {
                    for (let index = 0; index < localStorage.length; index++) {
                        const key = localStorage.key(index);
                        const bytes = estimateStorageEntrySize(key || '', localStorage.getItem(key) || '');
                        const category = getStorageCategory(key || '');
                        categoryBytes.set(category, categoryBytes.get(category) + bytes);
                    }
                } catch (_) { }

                const accountedBytes = [...categoryBytes.values()].reduce((total, bytes) => total + bytes, 0);
                const measuredUsage = Number(estimate.usage) || accountedBytes;
                const sizeScale = accountedBytes > 0 ? measuredUsage / accountedBytes : 1;
                storageStats.usage = measuredUsage;
                storageStats.quota = Number(estimate.quota) || 0;
                storageStats.orphanedBytes = (orphanedEntryBytes + embeddedOrphanBytes) * sizeScale;
                storageStats.orphanedItems = orphanedKeys.main.length + orphanedKeys.legacy.length
                    + emptyTurnKeys.length + templateRuntimeKeys.length;
                storageStats.categories = categories
                    .map(category => ({ ...category, bytes: (categoryBytes.get(category.key) || 0) * sizeScale }))
                    .filter(category => category.bytes > 0);
                unusedSnapshot = {
                    mainKeys: orphanedKeys.main,
                    legacyKeys: orphanedKeys.legacy,
                    emptyTurnKeys,
                    templateRuntimeKeys
                };
                storageStats.hasMeasured = true;
            } catch (error) {
                console.error('Failed to inspect storage:', error);
                storageStats.error = '读取存储信息失败，请稍后重试';
                storageStats.orphanedBytes = 0;
                storageStats.orphanedItems = 0;
                storageStats.categories = [];
                unusedSnapshot = { mainKeys: [], legacyKeys: [], emptyTurnKeys: [], templateRuntimeKeys: [] };
            } finally {
                storageStats.loading = false;
            }
        };

        const cleanupUnusedStorage = async () => {
            await refreshStorageStats();
            if (storageStats.error) return;
            if (storageStats.orphanedItems === 0) {
                toast('没有发现无用残留', 'info');
                return;
            }
            const snapshot = {
                mainKeys: [...unusedSnapshot.mainKeys],
                legacyKeys: [...unusedSnapshot.legacyKeys],
                emptyTurnKeys: [...unusedSnapshot.emptyTurnKeys],
                templateRuntimeKeys: unusedSnapshot.templateRuntimeKeys.map(item => ({ ...item }))
            };
            const orphanedBytes = storageStats.orphanedBytes;
            const orphanedItems = storageStats.orphanedItems;
            confirm(
                `将清理 ${orphanedItems} 项无用残留（约 ${formatStorageSize(orphanedBytes)}）。现有角色的数据不会受到影响。`,
                async () => {
                    storageStats.cleaning = true;
                    try {
                        await Promise.all([
                            deleteStorageKeys(getMainDb(), snapshot.mainKeys),
                            deleteStorageKeys(getLegacyDb(), snapshot.legacyKeys)
                        ]);
                        snapshot.emptyTurnKeys.forEach(key => delete memorySettings.emptyTurns?.[key]);
                        snapshot.templateRuntimeKeys.forEach(({ templateIndex, characterId }) => {
                            const runtime = globalUiTemplates.value[templateIndex]?.runtimeByCharacter;
                            if (runtime) delete runtime[characterId];
                        });
                        await Promise.all([
                            saveMemorySettings(),
                            saveStoredValue('global_ui_templates', globalUiTemplates.value)
                        ]);
                        await refreshStorageStats();
                        toast(`已清理 ${orphanedItems} 项无用残留，约 ${formatStorageSize(orphanedBytes)}`, 'success');
                    } catch (error) {
                        console.error('Failed to clean unused storage:', error);
                        toast('清理失败，请稍后重试', 'error');
                    } finally {
                        storageStats.cleaning = false;
                    }
                }
            );
        };

        return { cleanupUnusedStorage, formatStorageSize, refreshStorageStats, storageStats };
    };

    window.RPHubComposables = Object.freeze({ useStorageManagement, useTokenUsage });
})();

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
    const replyTool = {
        type: 'function',
        function: {
            name: 'output_reply',
            description: '将本次回复交给聊天界面显示。遵守现有输出规则，正文及需要附带的面板、图片标记、变量更新块等全部放入 content，不在普通消息中重复输出。检索工具返回结果后，只传新增回复内容。',
            parameters: {
                type: 'object',
                properties: { content: { type: 'string', description: '本次回复的原文，保留原有格式；作为 JSON 字符串正确转义。' } },
                required: ['content'],
                additionalProperties: false
            }
        }
    };

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

    const requestChatCompletionOnce = async (options, attempt) => {
        const startedAt = Date.now();
        const result = { content: '', reasoning: '', toolCalls: [], assistantMessage: null, usage: null, finishReason: null, isStream: false };
        let receivedPayload = false;
        let pendingContent = '';
        let pendingReasoning = '';
        const calls = new Map();
        const assistantMetadata = {};
        let toolsChanged = false;
        const toolSnapshot = () => [...calls.entries()].sort(([a], [b]) => a - b).map(([index, call]) => ({
            ...call, index, function: { ...call.function }
        }));
        const getReplyCall = () => [...calls.values()].find(call => call.function.name === replyTool.function.name);
        let plainContent = '';
        let refusal = '';
        let failure = null;
        let replyPosition = null;
        let replyClosed = false;
        const invalidReply = () => new Error('输出正文工具的参数格式错误，应为仅含 content 字符串的 JSON 对象');
        // 只解码已经收齐的字符串字符，JSON 外壳和未收齐的转义不会进入正文。
        const readReplyDelta = () => {
            const replyCall = getReplyCall();
            if (!replyCall || replyClosed) return '';
            const source = replyCall.function.arguments;
            if (replyPosition === null) {
                const header = /^\s*\{\s*"content"\s*:\s*"/.exec(source);
                if (!header) return '';
                replyPosition = header[0].length;
            }
            let text = '';
            let lastPosition = replyPosition;
            while (replyPosition < source.length) {
                const char = source[replyPosition];
                if (char === '"') { replyPosition++; replyClosed = true; break; }
                if (char.charCodeAt(0) < 32) throw invalidReply();
                let size = 1;
                if (char === '\\') {
                    const escape = source[replyPosition + 1];
                    if (!escape) break;
                    if (escape === 'u') {
                        const digits = source.slice(replyPosition + 2, replyPosition + 6);
                        if (/[^\da-f]/i.test(digits)) throw invalidReply();
                        if (digits.length < 4) break;
                        size = 6;
                    } else {
                        if (!'"\\/bfnrt'.includes(escape)) throw invalidReply();
                        size = 2;
                    }
                }
                lastPosition = replyPosition;
                text += size === 1 ? char : JSON.parse('"' + source.slice(replyPosition, replyPosition + size) + '"');
                replyPosition += size;
            }
            if (!replyClosed && /[\uD800-\uDBFF]$/.test(text)) {
                replyPosition = lastPosition;
                text = text.slice(0, -1);
            }
            return text;
        };
        const finish = () => {
            const nativeCalls = toolSnapshot();
            const replyCalls = nativeCalls.filter(call => call.function.name === replyTool.function.name);
            result.toolCalls = nativeCalls.filter(call => !options.replyInTool || call.function.name !== replyTool.function.name);
            if (nativeCalls.length && (result.finishReason === 'content_filter' || refusal.trim())) throw new Error('API 已停止工具输出');
            if (result.toolCalls.length) {
                // 工具调用必须保留服务端 ID；伪造 ID 会破坏下一轮的调用/结果配对。
                if (replyCalls.length || result.toolCalls.some(call => !call.id || call.type !== 'function' || !call.function.name)
                    || new Set(result.toolCalls.map(call => call.id)).size !== result.toolCalls.length) {
                    throw new Error('API 返回的工具调用不完整或混用了回复工具，请重新尝试');
                }
                result.assistantMessage = { role: 'assistant', content: plainContent || null, ...assistantMetadata,
                    tool_calls: result.toolCalls.map(({ index, ...call }) => call) };
                if (options.replyInTool) {
                    pendingContent += plainContent;
                    result.content = plainContent;
                }
                return result;
            }
            if (!options.replyInTool) return result;
            if (result.finishReason === 'content_filter' || refusal.trim()) throw new Error('API 已停止工具输出');
            if (replyCalls.length > 1) throw invalidReply();
            const replyArguments = getReplyCall()?.function.arguments || '';
            if (replyArguments.trim()) {
                let payload;
                try { payload = JSON.parse(replyArguments); }
                catch (_) {
                    if (replyPosition === null || (replyClosed && replyArguments.slice(replyPosition).trim())) throw invalidReply();
                    // 容忍末尾缺失的 JSON 闭合符号，保留已经解码的正文。
                }
                if (payload !== undefined) {
                    if (!payload || typeof payload.content !== 'string' || Object.keys(payload).length !== 1
                        || !payload.content.startsWith(result.content)) throw invalidReply();
                    pendingContent += payload.content.slice(result.content.length);
                    result.content = payload.content;
                }
            }
            // 响应结束后才选普通正文兜底，避免与稍后到来的工具正文重复。
            if (!result.content.trim()) {
                if (!plainContent.trim()) {
                    throw Object.assign(new Error('API 未返回抗截断输出，可能触发了空回或站点不支持，请重新尝试。'), {
                        // 已有思考或工具调用时不算真正空回，也不重试。
                        retryableEmptyToolReply: !calls.size && !result.reasoning.trim()
                    });
                }
                pendingContent += plainContent;
                result.content += plainContent;
            }
            return result;
        };
        const accept = data => {
            receivedPayload = true;
            result.usage = getApiUsagePayload(data) || result.usage;
            const choice = data.choices?.[0] || {};
            const message = choice.delta || choice.message || {};
            let content = readTextContent(message.content ?? choice.text);
            const reasoning = extractNativeReasoning(message) || extractNativeReasoning(choice) || '';
            plainContent += content;
            refusal += readTextContent(message.refusal);
            // 保留转接接口返回的签名和推理字段，原样用于本轮工具结果回传，不混进聊天正文。
            for (const key of ['reasoning_content', 'reasoning', 'reasoning_details', 'extra_content']) {
                if (message[key] == null) continue;
                assistantMetadata[key] = typeof message[key] === 'string'
                    ? (assistantMetadata[key] || '') + message[key]
                    : message[key];
            }
            for (const [position, part] of (message.tool_calls || []).entries()) {
                const index = part.index ?? position;
                if (!Number.isInteger(index) || index < 0 || (part.type && part.type !== 'function')) throw new Error('API 返回了无效的工具调用');
                let call = calls.get(index);
                if (!call) {
                    call = { id: '', type: 'function', function: { name: '', arguments: '' } };
                    calls.set(index, call);
                }
                if (part.id && call.id && part.id !== call.id) throw new Error('API 返回了冲突的工具调用 ID');
                call.id = part.id || call.id;
                for (const key of ['name', 'arguments']) {
                    if (part.function?.[key] == null) continue;
                    if (typeof part.function[key] !== 'string') throw new Error('API 工具参数应为 JSON 字符串');
                    call.function[key] += part.function[key];
                }
                if (part.extra_content) call.extra_content = { ...call.extra_content, ...part.extra_content };
                toolsChanged = true;
            }
            if (options.replyInTool) content = readReplyDelta();
            result.content += content;
            result.reasoning += reasoning;
            result.finishReason = choice.finish_reason ?? result.finishReason;
            pendingContent += content;
            pendingReasoning += reasoning;
        };
        try {
            const tools = [...(options.tools || []), ...(options.replyInTool && !options.requireTool ? [replyTool] : [])];
            return await withApiResponse({ ...options, body: {
                model: options.model, messages: options.messages, temperature: options.temperature,
                ...(options.reasoningEffort ? { reasoning_effort: options.reasoningEffort } : {}),
                // [LuzzyRP patch 015] max_tokens（模型元数据）与供应商级 extraBody 注入
                ...(options.maxTokens ? { max_tokens: options.maxTokens } : {}),
                ...(options.extraBody || {}),
                ...(tools.length ? {
                    tools,
                    tool_choice: options.requireTool || (options.replyInTool && options.tools?.length) ? 'required'
                        : options.replyInTool ? { type: 'function', function: { name: replyTool.function.name } } : 'auto',
                    parallel_tool_calls: false
                } : {}),
                stream: !!options.stream,
                ...(options.stream ? { stream_options: { include_usage: true } } : {})
            } }, async (response, touch) => {
                const eventStream = response.headers.get('content-type')?.includes('text/event-stream');
                let rawText;
                if (!eventStream) {
                    rawText = await response.text();
                    if (!/^\s*(?:data:|:)/.test(rawText)) {
                        accept(parsePayload(rawText, response.status));
                        return finish();
                    }
                }
                result.isStream = !!options.stream;
                let buffer = '';
                let eventLines = [];
                let done = false;
                let flushPromise = Promise.resolve();
                const flush = () => {
                    if (!result.isStream || (!pendingContent && !pendingReasoning && !toolsChanged)) return;
                    const delta = { content: pendingContent, reasoning: pendingReasoning,
                        ...(toolsChanged ? { toolCalls: toolSnapshot().filter(call => call.function.name
                            && !(options.replyInTool && replyTool.function.name.startsWith(call.function.name))) } : {}) };
                    pendingContent = pendingReasoning = '';
                    toolsChanged = false;
                    flushPromise = flushPromise.then(() => options.onDelta?.(delta));
                    // 立即挂上处理器，最终仍由 await 抛出回调错误。
                    flushPromise.catch(() => {});
                };
                const dispatch = () => {
                    if (!eventLines.length) return;
                    const payload = eventLines.join('\n');
                    eventLines = [];
                    if (payload.trim() === '[DONE]') {
                        done = true;
                        return;
                    }
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
                const interval = setInterval(flush, STREAM_RENDER_INTERVAL); // [LuzzyRP patch 032] 流式渲染降载：60→120ms
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
                    return finish();
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
        } catch (error) {
            failure = error;
            throw error;
        } finally {
            if (options.replyInTool) console.info('[Gemini抗截断]', {
                模型: options.model, 次数: attempt, 结果: failure ? failure.message : '成功',
                结束原因: result.finishReason, 正文全文: result.content, 普通正文全文: plainContent
            });
            // 在业务层 JSON/模板校验之前记账；部分流式响应后中止也不会漏掉已返回的用量。
            if (receivedPayload) options.onUsage?.(result.usage, {
                isStream: result.isStream, durationMs: Date.now() - startedAt,
                finishReason: result.finishReason ?? null,   // [LuzzyRP patch 052]
                outputCharacters: [...calls.values()].reduce((sum, call) => sum + call.function.arguments.length, 0) + plainContent.length + result.reasoning.length
            });
        }
    };

    const requestChatCompletion = async options => {
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
        for (let attempt = 1; ; attempt++) {
            try { return await requestChatCompletionOnce(options, attempt); }
            catch (error) {
                if (!error.retryableEmptyToolReply || attempt >= 3 || options.signal?.aborted) throw error;
            }
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

    // [LuzzyRP patch 048] B1 · Anthropic 显式缓存断点。
    // Anthropic Messages **没有** OpenAI 那样的自动前缀缓存 —— 不在内容块上显式声明
    // `cache_control:{type:'ephemeral'}` 就完全没有缓存收益。这里打两个断点：
    //   ① system 块末尾（单块 ~8KB，是最大的稳定前缀）；
    //   ② 最后一条消息的最后一个文本块末尾 —— 下一轮请求正好以它为前缀命中，
    //      配合 patch 047 的「纯追加」形态收益最大。
    // 只用官方的块数组形态，不改动任何既有文本内容；拿不到合适的文本块时原样返回（静默降级）。
    const withAnthropicCacheBreakpoint = (message) => {
        if (!message) return message;
        const content = message.content;
        if (typeof content === 'string') {
            return content ? { ...message, content: [{ type: 'text', text: content, cache_control: { type: 'ephemeral' } }] } : message;
        }
        if (!Array.isArray(content) || content.length === 0) return message;
        let target = -1;
        content.forEach((block, index) => {
            if (block && typeof block === 'object' && typeof block.text === 'string' && block.text) target = index;
        });
        if (target < 0) return message;
        return {
            ...message,
            content: content.map((block, index) => (
                index === target ? { ...block, cache_control: { type: 'ephemeral' } } : block
            ))
        };
    };

    const requestAnthropicCompletionInternal = async (options) => {
        const { system, messages } = toAnthropicMessages(options.messages || []);
        const thinkingConfig = options.reasoningEffort ? anthropicThinkingConfig(options.maxTokens) : null;
        const cachedMessages = messages.length > 0
            ? [...messages.slice(0, -1), withAnthropicCacheBreakpoint(messages[messages.length - 1])]
            : messages;
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
                ...(system ? { system: [{ type: 'text', text: system, cache_control: { type: 'ephemeral' } }] } : {}),
                messages: cachedMessages,
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
            let finishReason = null;
            if (data.type === 'content_block_delta') {
                const delta = data.delta || {};
                if (delta.type === 'text_delta') content = delta.text || '';
                else if (delta.type === 'thinking_delta') reasoning = delta.thinking || '';
            } else if (data.type === 'message_delta') {
                // [LuzzyRP patch 052] 捕获 stop_reason：Anthropic 把它放在 message_delta 帧上。
                // 「被 max_tokens 截断（max_tokens）」与「模型自己收（end_turn / stop_sequence）」
                // 是两个完全不同的锅，此前三协议里只有 OpenAI 路径捕获了该字段，另两个协议从未读取，
                // 导致截断问题无法定性。
                finishReason = (data.delta || {}).stop_reason || null;
            } else if (data.type === 'message') {
                if (data.stop_reason) finishReason = data.stop_reason;
                (data.content || []).forEach(block => {
                    if (block.type === 'text') content += block.text || '';
                    else if (block.type === 'thinking') reasoning += block.thinking || '';
                });
            }
            return { data, content, reasoning, finishReason };
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
                let finishReason = null;
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
                        if (chunk.finishReason) finishReason = chunk.finishReason;   // [LuzzyRP patch 052]
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                    }
                }
                return { content, reasoning, usage, finishReason, isStream: true };
            }
            const data = parsePayloadStrict(rawText, response.status);
            let content = '';
            let reasoning = '';
            (data.content || []).forEach(block => {
                if (block.type === 'text') content += block.text || '';
                else if (block.type === 'thinking') reasoning += block.thinking || '';
            });
            return { content, reasoning, usage: getApiUsagePayload(data) || null,
                     finishReason: data.stop_reason || null, isStream: false };   // [LuzzyRP patch 052]
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let usage = null;
        let finishReason = null;   // [LuzzyRP patch 052]
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
                        if (chunk.finishReason) finishReason = chunk.finishReason;   // [LuzzyRP patch 052]
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                        console.warn('Error parsing anthropic stream chunk:', error);
                    }
                }
            }
            return { content: '', reasoning: '', usage, finishReason, isStream: true };
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
            // [LuzzyRP patch 052] 捕获 finishReason：Gemini 用 candidates[0].finishReason。
            // 归一成与 OpenAI 同义的 'length'（MAX_TOKENS 即被输出上限截断），其余小写透传。
            const rawFinish = data.candidates?.[0]?.finishReason || null;
            const finishReason = rawFinish
                ? (String(rawFinish).toUpperCase() === 'MAX_TOKENS' ? 'length' : String(rawFinish).toLowerCase())
                : null;
            const parts = data.candidates?.[0]?.content?.parts || [];
            parts.forEach(part => {
                if (typeof part.text !== 'string') return;
                if (part.thought === true) reasoning += part.text;
                else content += part.text;
            });
            return { data, content, reasoning, finishReason };
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
                let finishReason = null;   // [LuzzyRP patch 052]
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
                        if (chunk.finishReason) finishReason = chunk.finishReason;   // [LuzzyRP patch 052]
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                    }
                }
                return { content, reasoning, usage, finishReason, isStream: true };
            }
            const parsed = parseGeminiChunk(rawText, response.status);
            return { content: parsed.content, reasoning: parsed.reasoning, usage: getApiUsagePayload(parsed.data) || null,
                     finishReason: parsed.finishReason, isStream: false };   // [LuzzyRP patch 052]
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let usage = null;
        let finishReason = null;   // [LuzzyRP patch 052]
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
                        if (chunk.finishReason) finishReason = chunk.finishReason;   // [LuzzyRP patch 052]
                    } catch (error) {
                        if (error.isApiError) throw error;
                        if (/error/i.test(payload)) throw new Error(formatApiErrorMessage(response.status, payload));
                        console.warn('Error parsing gemini stream chunk:', error);
                    }
                }
            }
            return { content: '', reasoning: '', usage, finishReason, isStream: true };   // [LuzzyRP patch 052]
        } finally {
            clearInterval(flushInterval);
            flushPending();
            await flushPromise;
        }
    };

    // [LuzzyRP patch 025] 适配器统一用量指标（与上游 openai 路径 onUsage 契约对齐）
    // [LuzzyRP patch 051] C1 · 返回契约补齐（潜伏缺陷修复）：
    // 上游 1.9.2 起 app.js 生成收尾会**无条件**读 `responseResult.toolCalls.length`
    // （app.js 两处：`activeToolDepth > 0 && !responseResult.toolCalls.length …` 与
    // `if (responseResult.toolCalls.length) { … }`），而 Anthropic / Gemini 适配器
    // **从来不返回 toolCalls 键**（返回 {content, reasoning, usage, isStream}）。
    // 后果：任何一次成功的 Anthropic / Gemini 回复都会解引用 undefined 抛 TypeError
    // —— 正文虽已流式渲染出来，但会被当成生成失败、追加错误提示且置 generationFailed。
    // LuzzyRP 的三个协议适配器（patch 015）在上游该行写入之后才合并，故从未对账过。
    // 这里在两个适配器的**唯一公共出口**把返回形态补齐为与 OpenAI 路径同构（根因修复），
    // 同时 app.js 侧保留可选链兜底（双保险）。
    const withUsageMetrics = (adapter) => async (options) => {
        const startedAt = Date.now();
        const result = await adapter(options);
        if (!Array.isArray(result.toolCalls)) result.toolCalls = [];
        if (result.finishReason === undefined) result.finishReason = null;
        options.onUsage?.(result.usage, {
            isStream: result.isStream, durationMs: Date.now() - startedAt,
            // [LuzzyRP patch 052] 三协议统一把结束原因交给记账层（否则无法区分
            // 「被输出上限截断」与「模型自己收」，截断问题永远无法定性）
            finishReason: result.finishReason ?? null,
            outputCharacters: (result.content || '').length + (result.reasoning || '').length
        });
        return result;
    };
    const requestAnthropicCompletion = withUsageMetrics(requestAnthropicCompletionInternal);
    const requestGeminiCompletion = withUsageMetrics(requestGeminiCompletionInternal);


    window.RPHubApiUtils = Object.freeze({ buildApiEndpoint });
    window.RPHubApiClient = Object.freeze({ requestChatCompletion, requestJson });
})();

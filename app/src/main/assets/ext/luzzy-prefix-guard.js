/**
 * LuzzyRP 扩展层 · KV / prompt 前缀缓存观测（patch 047 配套，v1.6.0）
 *
 * 为什么存在：聊天侧每轮把 messages **整体重建**，且历史上有 7 处「前缀逐字节不稳定」，
 * 服务端的 prompt/KV 前缀缓存因此必然大量失配 —— 而应用**从未观测过命中情况**
 * （`cached_tokens` 等字段只被记账、没有被用于任何判断）。
 * 本文件是**只读旁路**：把「相邻两轮请求的公共前缀」与「服务端返回的缓存命中」变成可读指标，
 * 使后续优化（patch 047/048/049/050）有前后可对比的量化依据，并作为门禁
 * `tools/prefix-cache-test.cjs` 的取样口。
 *
 * 挂钩点说明：`api-utils.js` 的三处请求（82 / 459 / 633 行）都直接调用**全局 fetch**，
 * 全仓没有任何提前捕获（已 grep 确认），故包装 `window.fetch` 是唯一「不改上游」的入口。
 * 反例：`app.js` 在加载期就把 `RPHubContextUtils` 等全局解构进闭包，事后替换那些全局**无效**。
 *
 * 降级（硬性规定 3）：包装失败 / 解析失败 / 非 chat 请求 → 原样透传，绝不抛错、绝不影响请求。
 */
(function () {
    'use strict';

    var Luzzy = window.Luzzy || {};

    /** 只统计这些路径（chat/completions 等生成类请求）；其它请求一律不碰。 */
    var URL_HINTS = ['/chat/completions', '/messages', '/generateContent', ':generateContent'];
    /** 记录最近 N 轮，便于门禁与探针读取。 */
    var MAX_ROUNDS = 40;

    var stats = {
        rounds: 0,            // 已观测的生成请求数
        withPrev: 0,          // 其中能跟上一条比对前缀的
        prefixCommonChars: 0, // 本轮与上轮 messages 的公共前缀字符数（累计）
        prevTotalChars: 0,    // 上轮 messages 总字符数（累计）
        cachedTokens: 0,      // 服务端报告的命中 token（累计）
        promptTokens: 0,      // 服务端报告的总输入 token（累计）
        lastCommonRatio: null,
        lastCachedRatio: null,
    };
    var rounds = [];          // 最近若干轮的明细
    var prevSig = null;       // 上一轮 messages 的规范化签名
    var wrapped = false;
    var origFetch = null;

    function isGenRequest(url) {
        try {
            var u = String(url || '');
            for (var i = 0; i < URL_HINTS.length; i++) {
                if (u.indexOf(URL_HINTS[i]) !== -1) return true;
            }
        } catch (e) { /* ignore */ }
        return false;
    }

    /** 把一条 message 规范化成「可逐字符比对」的字符串（只取会影响服务端前缀的内容）。 */
    function messageKey(m) {
        if (!m || typeof m !== 'object') return String(m == null ? '' : m);
        var parts = [];
        parts.push(m.role == null ? '' : String(m.role));
        if (typeof m.content === 'string') parts.push(m.content);
        else if (m.content != null) {
            try { parts.push(JSON.stringify(m.content)); } catch (e) { parts.push('[obj]'); }
        }
        if (m.tool_calls) {
            try { parts.push(JSON.stringify(m.tool_calls)); } catch (e) { parts.push('[tc]'); }
        }
        if (m.tool_call_id) parts.push(String(m.tool_call_id));
        if (m.name) parts.push(String(m.name));
        return parts.join('\u0001');
    }

    /**
     * 整轮签名。**字段顺序必须与三家协议在服务端的真实拼装顺序一致**，否则「前缀被破坏」
     * 会被系统性低估（三种协议都是 system → tools → messages；OpenAI 的 system 就在
     * messages[0]，故 body.system 仅在 Anthropic/Gemini 出现，不会重复计入）。
     * 键序固定（stableStringify）以消除「同内容不同键序」的假失配。
     */
    function roundSignature(body) {
        var out = '';
        if (body && body.system != null) {
            try { out += '\u0004' + (typeof body.system === 'string' ? body.system : stableStringify(body.system)) + '\u0002'; } catch (e) { /* ignore */ }
        }
        if (body && body.tools) {
            try { out += '\u0003' + stableStringify(body.tools) + '\u0002'; } catch (e) { out += '\u0003[err]\u0002'; }
        }
        var msgs = body && Array.isArray(body.messages) ? body.messages : [];
        for (var i = 0; i < msgs.length; i++) out += messageKey(msgs[i]) + '\u0002';
        return out;
    }

    /** 键序稳定的 JSON 序列化（对象键排序），消除「同一内容不同键序」的假失配。 */
    function stableStringify(value) {
        if (value === null || typeof value !== 'object') return JSON.stringify(value);
        if (Array.isArray(value)) {
            var arr = [];
            for (var i = 0; i < value.length; i++) arr.push(stableStringify(value[i]));
            return '[' + arr.join(',') + ']';
        }
        var keys = Object.keys(value).sort();
        var kv = [];
        for (var k = 0; k < keys.length; k++) kv.push(JSON.stringify(keys[k]) + ':' + stableStringify(value[keys[k]]));
        return '{' + kv.join(',') + '}';
    }

    /** 公共前缀长度（按字符，逐字符比较；两侧都已规范化）。 */
    function commonPrefixLen(a, b) {
        if (!a || !b) return 0;
        var n = Math.min(a.length, b.length);
        for (var i = 0; i < n; i++) {
            if (a.charCodeAt(i) !== b.charCodeAt(i)) return i;
        }
        return n;
    }

    /** 从请求体里读出 messages/tools（支持 OpenAI 风格与 Anthropic 风格）。 */
    function parseBody(init) {
        try {
            var raw = init && init.body;
            if (typeof raw !== 'string' || !raw) return null;
            var body = JSON.parse(raw);
            if (body && !Array.isArray(body.messages) && Array.isArray(body.input)) body.messages = body.input; // Anthropic
            return body;
        } catch (e) { return null; }
    }

    function recordRequest(url, init) {
        var body = parseBody(init);
        if (!body) return null;
        var sig = roundSignature(body);
        var entry = {
            at: Date.now(),
            url: String(url || '').slice(0, 120),
            messages: Array.isArray(body.messages) ? body.messages.length : 0,
            chars: sig.length,
            toolsChars: body.tools ? stableStringify(body.tools).length : 0,
            model: body.model || null,
            hasTools: !!body.tools,
            commonChars: null,
            commonRatio: null,
        };
        if (prevSig) {
            entry.commonChars = commonPrefixLen(sig, prevSig.sig);
            entry.commonRatio = prevSig.chars > 0 ? +(entry.commonChars / prevSig.chars).toFixed(4) : null;
            stats.withPrev++;
            stats.prefixCommonChars += entry.commonChars;
            stats.prevTotalChars += prevSig.chars;
            stats.lastCommonRatio = entry.commonRatio;
        }
        prevSig = { sig: sig, chars: sig.length };
        stats.rounds++;
        rounds.push(entry);
        if (rounds.length > MAX_ROUNDS) rounds.shift();
        return entry;
    }

    /** 从响应流/JSON 里取 usage 的缓存字段（尽量不干扰原响应）。 */
    function recordUsage(obj) {
        try {
            var u = obj && (obj.usage || obj.usageMetadata);
            if (!u) return;
            var cached = null, prompt = null;
            if (u.prompt_tokens_details && typeof u.prompt_tokens_details.cached_tokens === 'number') cached = u.prompt_tokens_details.cached_tokens;
            else if (typeof u.cache_read_input_tokens === 'number') cached = u.cache_read_input_tokens;      // Anthropic
            else if (typeof u.cachedContentTokenCount === 'number') cached = u.cachedContentTokenCount;      // Gemini
            else if (typeof u.prompt_cache_hit_tokens === 'number') cached = u.prompt_cache_hit_tokens;      // DeepSeek 风格
            if (typeof u.prompt_tokens === 'number') prompt = u.prompt_tokens;
            else if (typeof u.input_tokens === 'number') prompt = u.input_tokens;
            else if (typeof u.promptTokenCount === 'number') prompt = u.promptTokenCount;
            if (cached == null && prompt == null) return;
            if (typeof cached === 'number') stats.cachedTokens += cached;
            if (typeof prompt === 'number') stats.promptTokens += prompt;
            if (typeof cached === 'number' && typeof prompt === 'number' && prompt > 0) {
                stats.lastCachedRatio = +(cached / prompt).toFixed(4);
            }
            var last = rounds[rounds.length - 1];
            if (last) { last.cachedTokens = cached; last.promptTokens = prompt; }
        } catch (e) { /* 只读旁路：任何异常都不得影响请求 */ }
    }

    /** 扫一段 SSE 文本，抓取里面的 usage 对象（流式响应的 usage 通常在最后一块）。 */
    function scanSseText(text) {
        try {
            if (!text || text.indexOf('"usage"') === -1) return;
            var lines = String(text).split('\n');
            for (var i = 0; i < lines.length; i++) {
                var t = lines[i].trim();
                if (t.indexOf('data:') !== 0) continue;
                var payload = t.slice(5).trim();
                if (!payload || payload === '[DONE]') continue;
                try { var j = JSON.parse(payload); if (j && j.usage) recordUsage(j); } catch (e) { /* 分块不完整 */ }
            }
        } catch (e) { /* ignore */ }
    }

    function install() {
        if (wrapped) return true;
        try {
            origFetch = window.fetch;
            if (typeof origFetch !== 'function') return false;
            window.fetch = function (input, init) {
                var url = (typeof input === 'string') ? input : (input && input.url);
                var isGen = isGenRequest(url);
                var entry = null;
                if (isGen) { try { entry = recordRequest(url, init); } catch (e) { entry = null; } }
                var p = origFetch.apply(this, arguments);
                if (!isGen || !entry) return p;
                try {
                    // 非流式：直接读 JSON；流式：旁路复制一份文本扫 usage（不改动原响应体）
                    p = p.then(function (res) {
                        try {
                            var ct = (res.headers && res.headers.get && res.headers.get('content-type')) || '';
                            if (ct.indexOf('text/event-stream') !== -1) {
                                var clone = res.clone();
                                clone.text().then(scanSseText).catch(function () { });
                            } else if (ct.indexOf('application/json') !== -1) {
                                res.clone().json().then(recordUsage).catch(function () { });
                            }
                        } catch (e) { /* ignore */ }
                        return res;
                    });
                } catch (e) { /* 包装失败也不影响原 promise */ }
                return p;
            };
            wrapped = true;
            return true;
        } catch (e) {
            return false;
        }
    }

    install();

    Luzzy.prefixGuard = {
        ready: function () { return wrapped; },
        /** 累计统计 + 派生比率（供门禁/真机探针读取）。 */
        stats: function () {
            return {
                rounds: stats.rounds,
                withPrev: stats.withPrev,
                lastCommonRatio: stats.lastCommonRatio,
                lastCachedRatio: stats.lastCachedRatio,
                avgCommonRatio: stats.prevTotalChars > 0 ? +(stats.prefixCommonChars / stats.prevTotalChars).toFixed(4) : null,
                cachedRatio: stats.promptTokens > 0 ? +(stats.cachedTokens / stats.promptTokens).toFixed(4) : null,
                cachedTokens: stats.cachedTokens,
                promptTokens: stats.promptTokens,
            };
        },
        /** 最近若干轮明细（含每轮的公共前缀占比）。 */
        rounds: function () { return rounds.slice(); },
        /** 门禁/探针用：手工喂一轮请求体做前缀比对（不联网）。 */
        compare: function (bodyJson) {
            try {
                var body = typeof bodyJson === 'string' ? JSON.parse(bodyJson) : bodyJson;
                return recordRequest('(manual)', { body: JSON.stringify(body) });
            } catch (e) { return null; }
        },
        reset: function () {
            stats.rounds = 0; stats.withPrev = 0; stats.prefixCommonChars = 0; stats.prevTotalChars = 0;
            stats.cachedTokens = 0; stats.promptTokens = 0; stats.lastCommonRatio = null; stats.lastCachedRatio = null;
            rounds = []; prevSig = null;
        },
    };
    window.Luzzy = Luzzy;
})();

/**
 * LuzzyRP 扩展层 · 流式消息增量渲染（会话 58）
 *
 * 用户口径：「流式输出性能损耗太严重，不丢任何前端部分，能不能优化？跑满手机帧率」。
 *
 * 问题（桌面同引擎族实测，见 docs/WORKLOG.md 会话 58）：
 *   流式分支每 tick 把**整段**消息重新 parseCot → processMainContent → renderMarkdown → v-html；
 *   内容是全文 → 浏览器每 tick 重新解析整段 HTML、重建整条消息 DOM、重排整条消息。
 *   成本与**消息长度**成正比、与**新增字数**无关 → 越写越卡。
 *   实测（30 条历史、390×844@3.25）：1200 字 11.6ms / 4000 字 24.7ms / 8000 字 44.8ms 每 tick
 *   （120Hz 帧预算 8.33ms）。
 *
 * 做法：把「已定稿的前缀块」与「活动尾部」分开渲染
 *   - 前缀块只在边界**推进**时追加，之后不再触碰（DOM 不重建、重排不外溢）；
 *   - 尾部（仍在增长的最后一块）每 tick 只重建自身（很小）；
 *   - **前缀推进必须在提交前证明等价**：先算
 *       fullHtml = render(全文)          // 与改前完全一样的渲染
 *       nextPrefix = prefixHtml + render(新增前缀)
 *       nextTail = render(剩余尾部)
 *     仅当 `fullHtml === nextPrefix + nextTail` 成立才提交；不成立就放弃这次推进（保持原样）。
 *   于是**渲染结果永远等于全量渲染的结果**（等价性是提交前证明的，不是事后修补），
 *   而每 tick 的 DOM 工作量只与尾部长度相关。
 *   - 另有周期性全文校验（默认每 8 tick）作第二道保险：一旦不等价立即整段回退到全量渲染。
 *
 * 渲染器来源：**应用自己的** `renderMarkdown`（含显示过滤 / 显示正则 / marked / DOMPurify），
 * 经 `#app.__vue_app__._container._vnode.component.proxy` 取用（生产构建下 `app._instance` 不挂）。
 * 因此分块渲染与全量渲染走的是同一条渲染管线，不存在「另一套渲染」。
 *
 * 降级（硬性规定 3：扩展层不得影响主流程）：
 *   - 取不到应用渲染器 → 什么也不做（保留模板原有内容），不抛错、不白屏；
 *   - 任何一次验证不通过 → 本次直接全量 innerHTML（与改前行为一致）。
 *
 * ── patch 044 追加：活通道（live feed）────────────────────────────────────────
 * 真机实测（2026-09-11 会话 59，小米 25098PN5AC）推翻了「瓶颈在渲染器」的假设：
 *   · `v-lsp-stream` 自身一次更新 **≈1.4ms**；
 *   · 但**任何一次根级响应式状态变更**（含把流式正文写回 `msg.content`）都要
 *     **230–340ms**——上游是单体根组件，每次变更都会重渲染并 diff 整个界面
 *     （实测每次 diff ≈1040 vnode / 1130 DOM 节点）；把指令换成**空实现**后同样的
 *     tick 循环仍是 233ms，消息长度 1200 字 vs 5300 字也无差别。
 *   即：流式每 ~120ms 触发一次根重渲染 → 主线程被超额占用约 2.5 倍 → 跑不满帧率。
 *
 * 做法（app.js patch 044 配合）：流式期间正文**不再每 tick 写响应式状态**，
 *   而是把渲染后的正文交给本文件的 `feed()` 直接增量写进 DOM；响应式 `content`
 *   降到低频（见 app.js `LIVE_COMMIT_INTERVAL`）提交一次，流结束时追平。
 *   于是流式期间几乎没有根重渲染，文字仍按上游节奏（120ms）逐段出现。
 *   活通道渲染走的仍是应用自己的 `renderMarkdown`，且提交后 `content` 与所渲染的
 *   文本**逐字相等**——「不丢任何前端部分」的约束不变。
 */
(function () {
    'use strict';

    var Luzzy = window.Luzzy || {};

    /** 尾部短于该值不必分块。 */
    var MIN_TAIL_CHARS = 160;
    /** 前缀至少这么长才提交（极短消息直接走全量）。 */
    var MIN_PREFIX_CHARS = 240;
    /** 每 N 次更新做一次全文校验。 */
    var VERIFY_EVERY_TICKS = 8;
    /** 一次候选被证明不成立后，要等文本再长这么多字符才重新尝试（按长度门控，不用时间冷却）。 */
    var RETRY_AFTER_GROWTH = 96;

    /** 元素 → 状态。 */
    var states = new WeakMap();
    var appProxy = null;
    var directiveRegistered = false;
    /** 计数（门禁/自检用）：前缀推进次数、候选被证明不成立次数、整段回退次数。 */
    var stats = { advances: 0, failures: 0, repairs: 0 };

    /**
     * 活通道（patch 044 起；patch 045 扩到两条通道）。
     * 由 app.js 在流式期间直接投喂**渲染前的纯文本**，本文件用应用自己的 renderMarkdown
     * 渲染后写进对应元素，绕开「根状态变更 → 整个界面重渲染」。
     *
     *   content   —— 流式正文（模板 `live: true`）
     *   reasoning —— 思考面板正文（模板 `live: 'reasoning'`）
     *
     * 两条通道各自独立：元素、文本、定时器、退场判定互不影响 ——
     * 对应 rikkahub「思考块与正文块是不同 composable、互不牵连」的结构
     * （但我们是同一根组件，只能靠这条带外通道来等效实现）。
     *
     * skipRegex 必须与模板原调用一致：正文是 `renderMarkdown(text, role)`（false），
     * 思考面板是 `renderMarkdown(step.text, 'assistant', true)`（**true**）——
     * 用错会显示正则差异（app 在思考面板上刻意跳过显示正则）。
     */
    function makeChannel(skipRegex) {
        return {
            active: false, el: null, text: '', role: 'assistant',
            skipRegex: !!skipRegex, timer: 0, feeds: 0,
        };
    }
    var channels = { content: makeChannel(false), reasoning: makeChannel(true) };

    function channelOf(name) {
        return channels[name === 'reasoning' ? 'reasoning' : 'content'];
    }

    function app() {
        if (appProxy) return appProxy;
        try {
            var el = document.querySelector('#app');
            var vueApp = el && el.__vue_app__;
            if (!vueApp) return null;
            var inst = (vueApp._container && vueApp._container._vnode && vueApp._container._vnode.component)
                || vueApp._instance;
            appProxy = (inst && inst.proxy) || null;
        } catch (e) {
            appProxy = null;
        }
        return appProxy;
    }

    /** 应用自己的 Markdown 渲染；不可用返回 null（调用方一律降级）。 */
    function render(text, role, skipRegex) {
        var p = app();
        if (!p || typeof p.renderMarkdown !== 'function') return null;
        try {
            // cache:false —— 与 patch 032 同口径：流式中间串不灌渲染缓存
            return p.renderMarkdown(text, role || 'assistant', !!skipRegex, { cache: false });
        } catch (e) {
            return null;
        }
    }

    /** 解析 HTML 片段（用容器的上下文，保证 table/thead 之类的解析上下文正确）。 */
    function parseInto(container, html) {
        var range = document.createRange();
        range.selectNodeContents(container);
        return range.createContextualFragment(html);
    }

    /**
     * 候选前缀边界：优先「空行」，其次「下一行像块级开头」。
     *
     * 只产候选，**是否成立由「full === prefix + tail」证明**——所以这里可以宽松，
     * 不必自己实现 Markdown 语义（列表/围栏/表格的跨块合并由那个等式兜住：不成立就不切）。
     * 这条设计正是「不丢任何前端部分」的可证明约束。
     *
     * 但候选太差会让每次尝试都白做一遍全量渲染，故仍做两条廉价筛除：
     *   ① 切点后必须**紧跟非空白字符**（尾部要从一个真正的块开头起步，而不是一片空行）；
     *   ② 尾部必须含够多非空白字符（避免「尾部只有一个换行」这种必然不等价的候选）。
     */
    function candidateBoundary(src, from, blockedUpTo) {
        if (src.length - from < MIN_TAIL_CHARS) return from;
        var limit = src.length - MIN_TAIL_CHARS;
        var best = -1;
        for (var i = limit; i > from; i--) {
            if (src.charCodeAt(i) !== 10) continue;
            var cut = i + 1;
            if (cut <= (blockedUpTo || 0)) continue;                 // 已证明不成立的切点不再试
            var nextChar = src.charAt(cut);
            if (!nextChar || /\s/.test(nextChar)) continue;          // ① 尾部要真的从一个块开头起步
            if (!/\S/.test(src.slice(cut))) continue;                // ② 尾部要有实际内容
            var head = src.slice(Math.max(from, i - 2), i);
            if (/\n[ \t]*$/.test(head)) { best = cut; break; }        // 空行优先
            if (src.length - from > 1200) {
                var rest = src.slice(cut);
                // 下一行像块级开头（标题/列表/引用/围栏/表格/分隔线）
                if (/^(#{1,6} |[-*+] |\d+[.)] |>|\||```|~~~|---|===)/.test(rest)) best = cut;
            }
        }
        if (best > from && best - from >= MIN_PREFIX_CHARS) return best;
        return from;
    }

    function setTail(st, html) {
        while (st.el.childNodes.length > st.prefixNodes) {
            st.el.removeChild(st.el.lastChild);
        }
        if (html) st.el.appendChild(parseInto(st.el, html));
        st.tailHtml = html || '';
    }

    function appendPrefix(st, html) {
        if (!html) return;
        var frag = parseInto(st.el, html);
        var count = frag.childNodes.length;
        var ref = st.el.childNodes[st.prefixNodes] || null;
        st.el.insertBefore(frag, ref);
        st.prefixNodes += count;
    }

    function createState(el, role, skipRegex) {
        return {
            el: el, role: role || 'assistant', skipRegex: !!skipRegex, src: '',
            prefixLen: 0, prefixHtml: '', prefixNodes: 0, tailHtml: null,
            ticks: 0, blockedUpTo: 0, blockedAtLen: -1,
        };
    }

    /** 一次更新；返回是否产生可见变化。 */
    function update(el, src, role, isLive, skipRegex) {
        // [patch 044/045] 活通道期间：Vue 只会带着「低频提交的旧正文」来更新，
        // 若直接用旧文本覆盖，已经流出来的字会**倒退**。故以活文本为准；
        // 一旦低频提交追平（src === 通道文本），该通道自动退场、回到常规路径。
        // 两条通道各自判定 —— 正文与思考互不牵连。
        if (!isLive) {
            for (var name in channels) {
                var ch = channels[name];
                if (ch.active && ch.el === el) {
                    if (src === ch.text) {
                        ch.active = false; ch.el = null; ch.text = '';
                    } else {
                        src = ch.text;
                        role = ch.role;
                        skipRegex = ch.skipRegex;
                    }
                    break;
                }
            }
        }
        var skip = skipRegex === undefined ? null : !!skipRegex;
        var st = states.get(el);
        if (!st || st.role !== role || (skip !== null && st.skipRegex !== skip) || src.indexOf(st.src) !== 0) {
            st = createState(el, role, skip === null ? undefined : skip);
            states.set(el, st);
        }
        st.ticks++;
        st.src = src;

        // ---- 1) 尝试推进前缀（提交前证明等价）----
        // 上一次候选被证明不成立时，等文本再长 RETRY_AFTER_GROWTH 才重试（避免每 tick 白做全量验证）
        var canTry = st.blockedAtLen < 0 || src.length - st.blockedAtLen >= RETRY_AFTER_GROWTH;
        if (canTry) {
            var bound = candidateBoundary(src, st.prefixLen, st.blockedUpTo);
            if (bound > st.prefixLen) {
                var chunkHtml = render(src.slice(st.prefixLen, bound), st.role, st.skipRegex);
                var nextTail = render(src.slice(bound), st.role, st.skipRegex);
                var fullHtml = render(src, st.role, st.skipRegex);
                if (chunkHtml != null && nextTail != null && fullHtml != null
                    && fullHtml === st.prefixHtml + chunkHtml + nextTail) {
                    appendPrefix(st, chunkHtml);
                    st.prefixLen = bound;
                    st.prefixHtml += chunkHtml;
                    setTail(st, nextTail);
                    st.blockedUpTo = 0;
                    st.blockedAtLen = -1;
                    stats.advances++;
                    return true;
                }
                // 证明不成立：记住这个切点，别再用它；等文本长一截再找新切点
                stats.failures++;
                st.blockedUpTo = bound;
                st.blockedAtLen = src.length;
            }
        }

        // ---- 2) 只重建尾部 ----
        var tailHtml = render(src.slice(st.prefixLen), st.role, st.skipRegex);
        if (tailHtml == null) return false;         // 渲染器不可用：什么都不做（保持原样）
        var changed = tailHtml !== st.tailHtml;
        if (changed) setTail(st, tailHtml);

        // ---- 3) 周期性全文校验（第二道保险）----
        if (st.ticks % VERIFY_EVERY_TICKS === 0) {
            var check = render(src, st.role, st.skipRegex);
            if (check != null && check !== st.prefixHtml + (st.tailHtml || '')) {
                el.innerHTML = check;               // 整段回退：与改前行为完全一致
                st.prefixNodes = el.childNodes.length;
                st.prefixLen = 0;
                st.prefixHtml = '';
                st.tailHtml = '';
                st.blockedUpTo = 0;
                st.blockedAtLen = src.length;
                stats.repairs++;
                return true;
            }
        }
        return changed;
    }

    /** 指令登记：`live: true` → 正文通道；`live: 'reasoning'` → 思考通道。 */
    function registerLive(el, flag) {
        if (!flag) return;
        channelOf(flag === 'reasoning' ? 'reasoning' : 'content').el = el;
    }

    /** Vue 自定义指令：v-lsp-stream="{ src: '…', role: 'assistant', live: true|'reasoning' }"。 */
    var directive = {
        mounted: function (el, binding) {
            var v = binding.value || {};
            registerLive(el, v.live);
            update(el, String(v.src == null ? '' : v.src), v.role);
        },
        updated: function (el, binding) {
            var v = binding.value || {};
            registerLive(el, v.live);
            update(el, String(v.src == null ? '' : v.src), v.role);
        },
        unmounted: function (el) {
            states.delete(el);
            for (var name in channels) {
                var ch = channels[name];
                if (ch.el === el) { ch.active = false; ch.el = null; ch.text = ''; }
            }
        },
    };

    /**
     * 活通道投喂（patch 044）：app.js 在流式期间把**渲染后**的正文交给这里直接上屏。
     * 同一任务内多次投喂会合并成一次渲染（setTimeout 0），渲染仍走 update 的增量通道。
     * 尚无流式元素可写时返回 false（调用方无需处理，下一 tick 会带上更长的文本再投）。
     */
    function feed(text, role, channel) {
        var ch = channelOf(channel);
        ch.text = String(text == null ? '' : text);
        if (role) ch.role = role;
        ch.feeds++;
        if (!ch.el) return false;
        ch.active = true;
        if (ch.timer) return true;
        ch.timer = setTimeout(function () {
            ch.timer = 0;
            if (!ch.el || !ch.active) return;
            update(ch.el, ch.text, ch.role, true, ch.skipRegex);
        }, 0);
        return true;
    }

    /** 注册指令（幂等）。应用挂载早于本文件，但指令只需在流式分支渲染前注册即可。 */
    function register() {
        if (directiveRegistered) return true;
        try {
            var el = document.querySelector('#app');
            var vueApp = el && el.__vue_app__;
            if (!vueApp || typeof vueApp.directive !== 'function') return false;
            vueApp.directive('lsp-stream', directive);
            directiveRegistered = true;
            return true;
        } catch (e) {
            return false;
        }
    }

    if (!register()) {
        var tries = 0;
        var timer = setInterval(function () {
            if (register() || ++tries > 50) clearInterval(timer);
        }, 100);
    }

    function stateOfChannel(ch) {
        return {
            active: ch.active, hasEl: !!ch.el, textLen: ch.text.length,
            feeds: ch.feeds, skipRegex: ch.skipRegex,
        };
    }

    Luzzy.streamRender = {
        register: register,
        ready: function () { return directiveRegistered && !!app(); },
        apply: function (el, src, role) { return update(el, String(src == null ? '' : src), role); },
        feed: feed,
        // liveState() 顶层字段 = 正文通道（保持 patch 044 门禁的字段契约），另附 reasoning 子对象
        liveState: function () {
            var base = stateOfChannel(channels.content);
            base.reasoning = stateOfChannel(channels.reasoning);
            return base;
        },
        stats: function () { return { advances: stats.advances, failures: stats.failures, repairs: stats.repairs }; },
        stateOf: function (el) {
            var st = states.get(el);
            return st ? {
                prefixLen: st.prefixLen, prefixNodes: st.prefixNodes,
                ticks: st.ticks, blockedUpTo: st.blockedUpTo, blockedAtLen: st.blockedAtLen,
            } : null;
        },
    };
    window.Luzzy = Luzzy;
})();

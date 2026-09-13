package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmError

/**
 * **失败分类**（B6）——决定「重发一次」「先压缩再重发」还是「如实报错」。
 *
 * ## 为什么要单独一层
 *
 * 传输层（`OpenAiTransport`）已经会为自己的失败退避重试 2 次，但它只认「连接失败 / 5xx / 429
 * **且还没收到过数据帧**」。剩下两类它管不了，正是本层要补的：
 *
 * 1. **上下文溢出**：供应商回 400（不可重试），但正确的处置不是报错，而是**压缩后重发**；
 * 2. **已收到数据帧之后才断的流**：传输层按约定不再重试（怕重复输出），
 *    而「什么都没上屏」时重发是安全的——判据由引擎给（`visible == false`），不在本层。
 *
 * ## 判据纪律
 *
 * 全部是**确定性**的字符串/状态码判定（本仓库纪律：概率性判据不用、不可测的判据不写）。
 * 溢出的文案按三家真实报错取样（见 `RetryPolicyTest`），宁可漏判（报错给用户）
 * 也不错判（把无关的 400 当成溢出、白烧一次摘要调用）。
 */
object RetryPolicy {

    /** 失败处置。 */
    enum class Kind {
        /** 不重试：配置错、参数错、鉴权错——重试多少次都一样。 */
        None,

        /** 网络类失败：可安全重发（引擎只在「什么都没上屏」时才真发）。 */
        Network,

        /** 上下文溢出：先压缩再重发。 */
        ContextOverflow,
    }

    fun classify(error: LlmError?): Kind {
        if (error == null) return Kind.None
        if (isContextOverflow(error)) return Kind.ContextOverflow
        if (error.retryable || error.httpStatus in RETRYABLE_STATUS) return Kind.Network
        return Kind.None
    }

    /** 上下文溢出（三家协议的报错文案取样）。 */
    fun isContextOverflow(error: LlmError): Boolean {
        val text = error.message.lowercase()
        return OVERFLOW_MARKERS.any { it in text }
    }

    /**
     * 网关类的瞬时状态码。
     *
     * 传输层只认 429/5xx，而 408（请求超时）与 425（Too Early）同样是「再发一次就好」的语义。
     */
    private val RETRYABLE_STATUS = setOf(408, 409, 425)

    /**
     * 溢出文案特征。
     *
     * 顺序无关（任一命中即算）：`context length` 覆盖 DeepSeek/OpenAI 的 "maximum context length"，
     * `context_length_exceeded` 是 OpenAI 的 code，`prompt is too long` 是 Anthropic，
     * `input token count exceeds` 是 Gemini。
     */
    private val OVERFLOW_MARKERS = listOf(
        "context length",
        "context_length_exceeded",
        "context window",
        "maximum context",
        "prompt is too long",
        "input token count exceeds",
        "too many tokens",
        "reduce the length",
    )
}

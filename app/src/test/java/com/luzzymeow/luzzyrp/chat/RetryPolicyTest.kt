package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **重试判据（B6）** 的纯函数门禁。
 *
 * 为什么单独一个对象：判据必须**确定性且可测**——「这句话算不算上下文溢出」决定了
 * 我们接下来是「压缩后重发」还是「如实报错」，靠 `message.contains("...")` 散落在引擎里
 * 是不可测的，而测不了的判据迟到会变成「用户看到一句看不懂的英文报错」。
 */
class RetryPolicyTest {

    private fun http(code: Int, body: String = "") =
        LlmError(message = "HTTP $code：$body", retryable = code == 429 || code >= 500, httpStatus = code)

    @Test
    fun `没错误就什么都不做`() {
        assertEquals(RetryPolicy.Kind.None, RetryPolicy.classify(null))
    }

    @Test
    fun `网络类错误按可重试标记识别`() {
        assertEquals(
            RetryPolicy.Kind.Network,
            RetryPolicy.classify(LlmError("网络错误（UnknownHostException）", retryable = true)),
        )
        assertEquals(
            RetryPolicy.Kind.Network,
            RetryPolicy.classify(LlmError("请求超时（连接 30s / 空闲 120s）", retryable = true)),
        )
    }

    @Test
    fun `5xx 与 429 是网络类（传输层已退避过，这里是最后一层）`() {
        assertEquals(RetryPolicy.Kind.Network, RetryPolicy.classify(http(500)))
        assertEquals(RetryPolicy.Kind.Network, RetryPolicy.classify(http(503)))
        assertEquals(RetryPolicy.Kind.Network, RetryPolicy.classify(http(429)))
    }

    @Test
    fun `网关类的 408 与 425 也算网络类`() {
        assertEquals(RetryPolicy.Kind.Network, RetryPolicy.classify(http(408)))
        assertEquals(RetryPolicy.Kind.Network, RetryPolicy.classify(http(425)))
    }

    @Test
    fun `配置类错误不重试（重试一万次也还是错的）`() {
        assertEquals(
            RetryPolicy.Kind.None,
            RetryPolicy.classify(LlmError("未配置 API Key（请在供应商设置中填写后重试）", retryable = false)),
        )
        assertEquals(
            RetryPolicy.Kind.None,
            RetryPolicy.classify(LlmError("请求地址非法（请检查供应商 Base URL）", retryable = false)),
        )
        assertEquals(RetryPolicy.Kind.None, RetryPolicy.classify(http(401)))
        assertEquals(RetryPolicy.Kind.None, RetryPolicy.classify(http(404)))
    }

    @Test
    fun `上下文溢出按各家真实报错文案识别`() {
        // DeepSeek / OpenAI 兼容
        assertEquals(
            RetryPolicy.Kind.ContextOverflow,
            RetryPolicy.classify(
                http(400, """{"error":{"message":"This model's maximum context length is 65536 tokens."}}"""),
            ),
        )
        // OpenAI 的 code 形态
        assertEquals(
            RetryPolicy.Kind.ContextOverflow,
            RetryPolicy.classify(http(400, """{"error":{"code":"context_length_exceeded"}}""")),
        )
        // Anthropic
        assertEquals(
            RetryPolicy.Kind.ContextOverflow,
            RetryPolicy.classify(http(400, """{"error":{"message":"prompt is too long: 210000 tokens > 200000 maximum"}}""")),
        )
        // Gemini
        assertEquals(
            RetryPolicy.Kind.ContextOverflow,
            RetryPolicy.classify(http(400, """{"error":{"message":"The input token count exceeds the maximum number of tokens allowed"}}""")),
        )
    }

    @Test
    fun `溢出优先于网络类判定（400 不可重试但要压缩）`() {
        val overflow = http(400, "maximum context length exceeded")
        assertEquals(RetryPolicy.Kind.ContextOverflow, RetryPolicy.classify(overflow))
        // 即便供应商把它报成 500，也仍按「溢出」处理（压缩才是对的处置）
        val weird = LlmError("HTTP 500：maximum context length exceeded", retryable = true, httpStatus = 500)
        assertEquals(RetryPolicy.Kind.ContextOverflow, RetryPolicy.classify(weird))
    }

    @Test
    fun `普通的 400 既不重试也不压缩`() {
        assertEquals(
            RetryPolicy.Kind.None,
            RetryPolicy.classify(http(400, """{"error":{"message":"Invalid request: unknown field 'foo'"}}""")),
        )
    }
}

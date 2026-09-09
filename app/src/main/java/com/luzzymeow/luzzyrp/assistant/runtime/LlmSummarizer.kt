package com.luzzymeow.luzzyrp.assistant.runtime

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmTransport
import com.luzzymeow.luzzyrp.assistant.domain.prompt.Summarizer
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 用同一模型生成历史摘要（PLAN §5.4：旧轮转 system 摘要，异步、可取消、失败退化截断）。
 *
 * 设计取舍：
 * - **不新增供应商配置**——复用「当前助手」的请求模板（[templateProvider]），只换消息与参数；
 * - 摘要请求**不带工具**（避免模型在摘要时调工具）；
 * - 30s 超时 / 任何失败返回 null（[com.luzzymeow.luzzyrp.assistant.domain.prompt.ContextBuilder] 退化为截断）；
 * - 摘要文本同样**不进日志**（可能含用户内容）。
 */
class LlmSummarizer(
    private val transport: LlmTransport,
    private val templateProvider: suspend () -> LlmRequest?,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxSummaryTokens: Int = DEFAULT_MAX_SUMMARY_TOKENS,
) : Summarizer {

    override suspend fun summarize(messages: List<LlmMessage>): String? {
        if (messages.isEmpty()) return null
        val template = runCatching { templateProvider() }.getOrNull() ?: return null
        val transcript = messages.joinToString("\n") { message ->
            val role = when (message.role) {
                LlmRole.USER -> "用户"
                LlmRole.ASSISTANT -> "助手"
                LlmRole.TOOL -> "工具"
                LlmRole.SYSTEM -> "系统"
            }
            "$role：${message.content.take(2_000)}"
        }
        val request = template.copy(
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, SUMMARIZE_PROMPT),
                LlmMessage(LlmRole.USER, transcript),
            ),
            tools = emptyList(),
            requireTool = false,
            maxTokens = maxSummaryTokens,
            temperature = 0f,
            extraBody = null,
        )
        // 任何失败（网络/协议/超时）都返回 null —— ContextBuilder 会退化为截断（PLAN §5.4）
        val text = runCatching {
            withTimeoutOrNull(timeoutMs) {
                val builder = StringBuilder()
                transport.stream(request).collect { delta ->
                    delta.content?.let { builder.append(it) }
                }
                builder.toString()
            }
        }.getOrNull()
        return text?.trim()?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
        const val DEFAULT_MAX_SUMMARY_TOKENS: Int = 512

        const val SUMMARIZE_PROMPT: String =
            "把下面的对话压缩成一份要点摘要，供后续对话继续使用。要求：\n" +
                "1. 保留事实、决定、待办、用户偏好与关键上下文；\n" +
                "2. 丢弃寒暄与重复内容；\n" +
                "3. 用中文、条目式，不超过 200 字；\n" +
                "4. 直接输出摘要正文，不要前言后语。"
    }
}

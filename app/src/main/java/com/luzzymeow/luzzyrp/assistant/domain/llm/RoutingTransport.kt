package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 按 [LlmRequest.protocol] 分派到具体协议实现（PLAN §5.3 三协议）。
 *
 * 为什么要这层：`AgentLoop` 只依赖一个 [LlmTransport]；协议选择属于「供应商配置」，
 * 在每次请求的模板里（`providerId::bareId` 对应的供应商决定），因此用一个薄分派器
 * 避免为每种协议各起一个循环。
 *
 * 未知协议**回退 OpenAI 兼容**（大多数第三方网关都兼容），并记一条日志（不含密钥）。
 */
class RoutingTransport(
    private val openAi: LlmTransport,
    private val anthropic: LlmTransport,
    private val gemini: LlmTransport,
    private val log: (String) -> Unit = {},
) : LlmTransport {

    override fun stream(request: LlmRequest): Flow<LlmDelta> {
        val delegate = when (request.protocol.trim().lowercase()) {
            PROTOCOL_ANTHROPIC -> anthropic
            PROTOCOL_GEMINI -> gemini
            PROTOCOL_OPENAI -> openAi
            else -> {
                log("未知协议 \"${request.protocol}\"，回退 OpenAI 兼容（PLAN §5.3）")
                openAi
            }
        }
        return delegate.stream(request)
    }

    companion object {
        const val PROTOCOL_OPENAI = "openai"
        const val PROTOCOL_ANTHROPIC = "anthropic"
        const val PROTOCOL_GEMINI = "gemini"
    }
}

/** 便捷构造：三协议齐备的路由传输。 */
fun defaultRoutingTransport(log: (String) -> Unit = {}): LlmTransport = RoutingTransport(
    openAi = OpenAiTransport(log = log),
    anthropic = AnthropicTransport(log = log),
    gemini = GeminiTransport(log = log),
    log = log,
)

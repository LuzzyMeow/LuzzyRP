package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.coroutines.flow.Flow

/**
 * 按 [LlmRequest.protocol] 分派到具体协议实现（三协议）。
 *
 * 为什么要这层：调用方（[com.luzzymeow.luzzyrp.chat.ChatJobs]）只依赖一个 [LlmTransport]；
 * 协议选择属于「供应商配置」，在每次请求的参数里（JS 产出的 plan），
 * 因此用一个薄分派器避免为每种协议各起一条路径。
 *
 * 未知协议**回退 OpenAI 兼容**（大多数第三方网关都兼容），并记一条日志（不含密钥）。
 *
 * 本文件自 v1.5.0 的助手模块（commit 0392b662 前）原样恢复，仅改包名。
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
                log("未知协议 \"${request.protocol}\"，回退 OpenAI 兼容")
                openAi
            }
        }
        return delegate.stream(request)
    }

    companion object {
        const val PROTOCOL_OPENAI = "openai"
        const val PROTOCOL_ANTHROPIC = "anthropic"
        const val PROTOCOL_GEMINI = "gemini"

        /** 原生传输支持的协议清单（能力探测用，顺序即对外声明顺序）。 */
        val SUPPORTED_PROTOCOLS: List<String> = listOf(PROTOCOL_OPENAI, PROTOCOL_ANTHROPIC, PROTOCOL_GEMINI)
    }
}

/** 便捷构造：三协议齐备的路由传输。 */
fun defaultRoutingTransport(log: (String) -> Unit = {}): LlmTransport = RoutingTransport(
    openAi = OpenAiTransport(log = log),
    anthropic = AnthropicTransport(log = log),
    gemini = GeminiTransport(log = log),
    log = log,
)

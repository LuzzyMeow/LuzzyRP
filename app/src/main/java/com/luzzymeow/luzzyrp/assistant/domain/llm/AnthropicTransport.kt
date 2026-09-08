package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Anthropic Messages API 传输层（PLAN §5.3 三协议之一）。
 *
 * 与 [OpenAiTransport] 共享 [SseClient]（分帧/超时/取消/脱敏）与重试语义：
 * 仅连接失败 / 5xx / 429 且**尚未收到数据帧**时重试，最多 2 次、指数退避。
 *
 * 鉴权头：`x-api-key`（**不进日志**）+ `anthropic-version`。错误统一转 [LlmDelta.error]。
 */
class AnthropicTransport(
    private val sse: SseClient = SseClient(),
    private val log: (String) -> Unit = {},
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val backoffBaseMs: Long = DEFAULT_BACKOFF_BASE_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : LlmTransport {

    override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
        if (request.apiKey.isBlank()) {
            emit(LlmDelta(error = LlmError("未配置 API Key（请在供应商设置中填写后重试）")))
            return@flow
        }
        val url = AnthropicWire.messagesUrl(request.baseUrl)
        if (url.isEmpty()) {
            emit(LlmDelta(error = LlmError("未配置供应商 Base URL")))
            return@flow
        }
        val body = AnthropicWire.requestBody(request) { key ->
            log("extraBody 的 \"$key\" 属于受保护字段，已忽略（PLAN §11.1）")
        }.toString()
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("x-api-key", request.apiKey)
            put("anthropic-version", AnthropicWire.API_VERSION)
            putAll(request.extraHeaders)
        }

        var attempt = 0
        while (true) {
            var failure: LlmError? = null
            var receivedData = false
            sse.post(url, headers, body).collect { chunk ->
                when (chunk) {
                    is SseChunk.Data -> {
                        receivedData = true
                        parseAnthropicFrame(chunk.payload)?.let { emit(it) }
                    }

                    SseChunk.Ended -> Unit

                    is SseChunk.Failed -> failure = chunk.error
                }
            }

            val error = failure ?: return@flow
            val canRetry = error.retryable && !receivedData && attempt < maxRetries
            if (!canRetry) {
                log("Anthropic 流式失败（status=${error.httpStatus ?: "-"}，retryable=${error.retryable}，已收到数据=$receivedData）")
                emit(LlmDelta(error = error, finishReason = "error"))
                return@flow
            }
            attempt++
            sleep(backoffBaseMs * (1L shl (attempt - 1)))
        }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 2
        const val DEFAULT_BACKOFF_BASE_MS: Long = 500L
    }
}

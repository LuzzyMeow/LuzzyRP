package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gemini `streamGenerateContent` 传输层（PLAN §5.3 三协议之一）。
 *
 * 鉴权：`x-goog-api-key` 头（**不进 URL、不进日志**——Google 也支持 `?key=`，本实现不用）。
 * 工具调用无 id → 本层用自增计数器合成稳定 id（`call_<n>_<name>`），供后续 `functionResponse` 回填。
 */
class GeminiTransport(
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
        val url = GeminiWire.streamUrl(request.baseUrl, request.model)
        if (url.isEmpty()) {
            emit(LlmDelta(error = LlmError("未配置供应商 Base URL 或模型")))
            return@flow
        }
        val body = GeminiWire.requestBody(request) { key ->
            log("extraBody 的 \"$key\" 属于受保护字段，已忽略（PLAN §11.1）")
        }.toString()
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("x-goog-api-key", request.apiKey)
            putAll(request.extraHeaders)
        }

        val callCounter = AtomicInteger(0)
        var attempt = 0
        while (true) {
            var failure: LlmError? = null
            var receivedData = false
            sse.post(url, headers, body).collect { chunk ->
                when (chunk) {
                    is SseChunk.Data -> {
                        receivedData = true
                        val base = callCounter.get()
                        val delta = parseGeminiFrame(chunk.payload, callIndexBase = base)
                        delta?.toolCalls?.takeIf { it.isNotEmpty() }?.let { callCounter.addAndGet(it.size) }
                        delta?.let { emit(it) }
                    }

                    SseChunk.Ended -> Unit

                    is SseChunk.Failed -> failure = chunk.error
                }
            }

            val error = failure ?: return@flow
            val canRetry = error.retryable && !receivedData && attempt < maxRetries
            if (!canRetry) {
                log("Gemini 流式失败（status=${error.httpStatus ?: "-"}，retryable=${error.retryable}，已收到数据=$receivedData）")
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

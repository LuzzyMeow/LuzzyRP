package com.luzzymeow.luzzyrp.chat.llm

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Gemini `streamGenerateContent` 传输层（三协议之一）。
 *
 * - 端点由 [GeminiWire.endpoint] 拼接（**密钥在查询串里**，与 JS 同；故日志与错误
 *   文本一律经过 [redactSecrets] 脱敏，且 [SseClient] 不回显 URL）；
 * - 头**只有** `Content-Type: application/json`；
 * - 工具调用无 id → 本层用自增计数器合成稳定 id（`call_<n>_<name>`），供后续回填；
 * - 重试语义与 [OpenAiTransport] 一致。
 *
 * 本文件自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改编（鉴权由头改为查询串；URL 固定形态）。
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
            emit(LlmDelta(error = LlmError("未配置 API Key（请在供应商设置中填写后重试）", retryable = false)))
            return@flow
        }
        val url = GeminiWire.endpoint(request.baseUrl, request.model, request.apiKey, request.stream)
        if (url.isEmpty()) {
            emit(LlmDelta(error = LlmError("未配置供应商 Base URL 或模型", retryable = false)))
            return@flow
        }
        val body = GeminiWire.requestBody(request).toString()
        val headers = mapOf("Content-Type" to "application/json")

        val callCounter = AtomicInteger(0)
        var attempt = 0
        while (true) {
            var failure: LlmError? = null
            var receivedData = false
            sse.post(url, headers, body).collect { chunk ->
                when (chunk) {
                    is SseChunk.Data -> {
                        receivedData = true
                        val delta = parseGeminiFrame(chunk.payload, callIndexBase = callCounter.get())
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

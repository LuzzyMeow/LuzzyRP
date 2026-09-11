package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * OpenAI 兼容协议的流式传输实现（三协议主路径）。
 *
 * - 请求体由 [OpenAiWire] 构造（键序与 extraBody 展开语义见该文件）；
 * - URL **原样使用** [LlmRequest.baseUrl]（JS 已拼好 `/chat/completions`）；
 * - `Authorization: Bearer <apiKey>` 只出现在请求头，**永不进日志 / 异常消息**；
 * - 失败重试：**最多 2 次**、指数退避（500ms → 1s），仅对**连接失败 / 5xx / 429**；
 *   一旦已经收到过数据帧就不再重试（避免重复输出）；
 * - 错误统一转 [LlmDelta.error]，本类不抛异常（取消除外）。
 *
 * 本文件自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改编（URL 不再拼接）。
 *
 * @param sleep 退避等待（单测可注入，避免真实等待）。
 */
class OpenAiTransport(
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
        val url = OpenAiWire.messagesEndpoint(request)
        if (url.isEmpty()) {
            emit(LlmDelta(error = LlmError("未配置供应商 Base URL", retryable = false)))
            return@flow
        }
        val body = OpenAiWire.requestBody(request).toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${request.apiKey}",
        )

        var attempt = 0
        while (true) {
            var failure: LlmError? = null
            var receivedData = false
            sse.post(url, headers, body).collect { chunk ->
                when (chunk) {
                    is SseChunk.Data -> {
                        receivedData = true
                        OpenAiWire.parseFrame(chunk.payload)?.let { emit(it) }
                    }

                    SseChunk.Ended -> Unit

                    is SseChunk.Failed -> failure = chunk.error
                }
            }

            val error = failure ?: return@flow
            val canRetry = error.retryable && !receivedData && attempt < maxRetries
            if (!canRetry) {
                log("流式请求失败（status=${error.httpStatus ?: "-"}，retryable=${error.retryable}，已收到数据=$receivedData），不再重试")
                emit(LlmDelta(error = error, finishReason = "error"))
                return@flow
            }
            attempt++
            val waitMs = backoffBaseMs * (1L shl (attempt - 1))
            log("流式请求失败，${waitMs}ms 后重试（第 $attempt/$maxRetries 次）")
            sleep(waitMs)
        }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 2
        const val DEFAULT_BACKOFF_BASE_MS: Long = 500L
    }
}

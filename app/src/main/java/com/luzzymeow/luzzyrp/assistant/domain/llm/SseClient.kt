package com.luzzymeow.luzzyrp.assistant.domain.llm

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * SSE 流的一帧结果（[SseClient.post] 的输出）。
 *
 * 之所以是 sealed 而不是抛异常：PLAN §5.3 要求**错误不打断流程**，
 * 由 [OpenAiTransport] 转成 `LlmDelta(error = …)` 交上层决策是否重试。
 */
sealed interface SseChunk {
    /** 一个完整帧的 data 载荷（未做 JSON 解析）。 */
    data class Data(val payload: String) : SseChunk

    /** 收到 `[DONE]` 或服务端正常关闭连接。 */
    data object Ended : SseChunk

    /** 连接 / HTTP / 读取失败（[LlmError.retryable] 决定是否重试）。 */
    data class Failed(val error: LlmError) : SseChunk
}

/**
 * 基于 OkHttp 的 SSE 流式读取器（PLAN §5.3，OpenAI 协议主路径）。
 *
 * - **超时**：连接 30s、空闲（socket 读）120s（PLAN §5.3）；`callTimeout` 关闭
 *   （流式响应总时长不可预知，由 AgentLoop 的 [com.luzzymeow.luzzyrp.assistant.domain.loop.BudgetGuard]
 *   与取消信号兜底）；
 * - **取消**：Flow 收集被取消 → `call.cancel()` 立即中断阻塞读（见 [post] 内的
 *   `invokeOnCompletion`），不会泄漏连接；
 * - **分帧**：逐行读取后交给 [SseFrames.Parser]（避免 UTF-8 字符被读缓冲截断）；
 * - **错误**：全部转 [SseChunk.Failed]，**不抛异常**（取消除外，取消必须继续向上传播）；
 * - **密钥**：本类只把调用方给的头原样发出，日志与错误文本一律经 [redactSecrets] 脱敏。
 */
class SseClient(
    private val client: OkHttpClient = defaultClient(),
    private val log: (String) -> Unit = {},
) {

    /**
     * 发一次流式 POST，返回 SSE 帧流。
     *
     * [headers] 由调用方构造（含 `Authorization`）——**本类不打印它们**。
     */
    fun post(url: String, headers: Map<String, String>, jsonBody: String): Flow<SseChunk> = flow {
        val request = try {
            Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "text/event-stream")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
        } catch (e: IllegalArgumentException) {
            // 不回显 url（可能带查询串密钥），只给可操作提示
            emit(SseChunk.Failed(LlmError("请求地址非法（请检查供应商 Base URL）", retryable = false)))
            return@flow
        }

        val call = client.newCall(request)
        // 协程取消 → 立即关闭连接，避免阻塞读卡住直到读超时
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        var response: Response? = null
        try {
            response = call.execute()
            val code = response.code
            if (code !in 200..299) {
                val detail = runCatching { response.body.string() }.getOrNull()
                val suffix = detail?.takeIf { it.isNotBlank() }?.let { "：" + redactSecrets(it) }.orEmpty()
                val error = LlmError(
                    message = "HTTP $code$suffix",
                    retryable = code == 429 || code >= 500,
                    httpStatus = code,
                )
                log("SSE 请求失败：HTTP $code（retryable=${error.retryable}）")
                emit(SseChunk.Failed(error))
                return@flow
            }
            log("SSE 已连接（HTTP $code）")

            val source = response.body.source()
            val parser = SseFrames.Parser()
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                for (payload in parser.accept(line + "\n")) {
                    if (payload == SseFrames.DONE) {
                        emit(SseChunk.Ended)
                        return@flow
                    }
                    emit(SseChunk.Data(payload))
                }
            }
            for (payload in parser.finish()) {
                if (payload == SseFrames.DONE) {
                    emit(SseChunk.Ended)
                    return@flow
                }
                emit(SseChunk.Data(payload))
            }
            emit(SseChunk.Ended)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            val error = LlmError(
                message = "请求超时（连接 ${CONNECT_TIMEOUT_MS / 1000}s / 空闲 ${IDLE_TIMEOUT_MS / 1000}s）",
                retryable = true,
            )
            log("SSE 读取超时（空闲超时 ${IDLE_TIMEOUT_MS / 1000}s）")
            emit(SseChunk.Failed(error))
        } catch (e: IOException) {
            log("SSE 网络错误（${e.javaClass.simpleName}）")
            emit(SseChunk.Failed(LlmError("网络错误（${e.javaClass.simpleName}）", retryable = true)))
        } catch (e: Throwable) {
            log("SSE 读取失败（${e.javaClass.simpleName}）")
            emit(SseChunk.Failed(LlmError("流式读取失败（${e.javaClass.simpleName}）", retryable = false)))
        } finally {
            runCatching { response?.close() }
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** 连接超时（PLAN §5.3）。 */
        const val CONNECT_TIMEOUT_MS: Long = 30_000L

        /** 空闲超时：OkHttp 的 socket read timeout 即「两次读到数据之间」的上限（PLAN §5.3）。 */
        const val IDLE_TIMEOUT_MS: Long = 120_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 默认客户端：连接 30s / 空闲 120s / 不做自动重连（重试策略归 [OpenAiTransport]）。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

/**
 * 密钥脱敏（PLAN §13.2：API Key **禁止**写日志 / 异常消息）。
 *
 * 保守策略：`sk-` 前缀串、`key/token/authorization/bearer` 后的取值一律打码，
 * 并截断长度，避免把整个响应体写进日志。
 */
internal fun redactSecrets(text: String, maxLength: Int = 400): String {
    var out = text
        .replace(Regex("(?i)(api[-_]?key|access[-_]?token|token|authorization|bearer)\\s*[:=]?\\s*\\S+"), "$1=***")
        .replace(Regex("sk-[A-Za-z0-9_\\-]{4,}"), "sk-***")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (out.length > maxLength) out = out.take(maxLength) + "…"
    return out
}

package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI Chat Completions 协议的**纯函数**线协议层（请求体构造 + 流帧解析）。
 *
 * 拆出来的理由：
 * 1. 让「映射正确性」可单测——不依赖网络与 OkHttp（本机缓存只有 `mockwebserver` 制品、
 *    未在 `app/build.gradle.kts` 声明，任务约束禁止改构建脚本，故不引入）；
 * 2. `extraBody` 覆盖保护、消息映射、`reasoning_content` 兼容等易错点集中一处。
 *
 * **密钥红线**：[requestBody] 只处理模型/采样/扩展字段，**不接触 apiKey**
 * （密钥仅由 [OpenAiTransport] 放进 `Authorization` 头）。
 */
object OpenAiWire {

    /** `extraBody` 中禁止覆盖的字段（PLAN §11.1）。 */
    val PROTECTED_BODY_KEYS: Set<String> = setOf("model", "messages", "tools", "stream")

    /** Base URL → `/chat/completions` 端点（兼容已写全路径与只写 `…/v1` 两种填法）。 */
    fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    /**
     * 构造请求体。
     *
     * [onConflict] 在 `extraBody` 试图覆盖受保护字段时回调（键名，**不含值**），
     * 由调用方转日志——PLAN §11.1 要求「忽略并提示」而不是静默。
     */
    fun requestBody(request: LlmRequest, onConflict: (String) -> Unit = {}): JsonObject {
        val base = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("messages", messagesJson(request.messages))
            if (request.tools.isNotEmpty()) put("tools", JsonArray(request.tools))
            put("stream", JsonPrimitive(true))
            put("stream_options", buildJsonObject { put("include_usage", JsonPrimitive(true)) })
            request.temperature?.let { put("temperature", JsonPrimitive(it)) }
            request.topP?.let { put("top_p", JsonPrimitive(it)) }
            request.maxTokens?.let { put("max_tokens", JsonPrimitive(it)) }
            request.stop?.takeIf { it.isNotEmpty() }?.let { stop ->
                put("stop", JsonArray(stop.map { JsonPrimitive(it) }))
            }
            request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { put("reasoning_effort", JsonPrimitive(it)) }
            if (request.requireTool && request.tools.isNotEmpty()) put("tool_choice", JsonPrimitive("required"))
        }
        val extra = request.extraBody ?: return base
        if (extra.isEmpty()) return base
        val merged = LinkedHashMap<String, JsonElement>(base)
        for ((key, value) in extra) {
            if (key in PROTECTED_BODY_KEYS) {
                onConflict(key)
                continue
            }
            merged[key] = value
        }
        return JsonObject(merged)
    }

    /**
     * [LlmMessage] → OpenAI 消息数组。
     *
     * - ASSISTANT 带 `tool_calls`（`arguments` 用原始字符串，避免二次序列化改变模型输入）；
     * - TOOL 带 `tool_call_id`（`name` 仅在显式提供时输出，兼容严格校验的网关）；
     * - `reasoning` **不回传**：OpenAI 协议（含 DeepSeek）不接受 `reasoning_content` 回灌。
     */
    fun messagesJson(messages: List<LlmMessage>): JsonArray = JsonArray(
        messages.map { message ->
            when (message.role) {
                LlmRole.SYSTEM -> buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(message.content))
                }

                LlmRole.USER -> buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(message.content))
                }

                LlmRole.ASSISTANT -> buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonPrimitive(message.content))
                    if (message.toolCalls.isNotEmpty()) {
                        put(
                            "tool_calls",
                            JsonArray(
                                message.toolCalls.map { call ->
                                    buildJsonObject {
                                        put("id", JsonPrimitive(call.id))
                                        put("type", JsonPrimitive("function"))
                                        put(
                                            "function",
                                            buildJsonObject {
                                                put("name", JsonPrimitive(call.name))
                                                put("arguments", JsonPrimitive(call.rawArguments))
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }
                }

                LlmRole.TOOL -> buildJsonObject {
                    put("role", JsonPrimitive("tool"))
                    put("content", JsonPrimitive(message.content))
                    message.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                    message.name?.let { put("name", JsonPrimitive(it)) }
                }
            }
        }
    )

    /** 单个 SSE 帧载荷 → [LlmDelta]；非 JSON / 非对象返回 null（忽略，不打断流）。 */
    fun parseFrame(payload: String): LlmDelta? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null
        val obj = runCatching { JsonLenient.json.parseToJsonElement(trimmed) }.getOrNull() as? JsonObject ?: return null
        return parseJson(obj)
    }

    /**
     * 解析一帧 JSON。
     *
     * 覆盖：`choices[].delta.content`、`reasoning_content` / `reasoning`、
     * `delta.tool_calls[]` 增量、`finish_reason`、顶层 `usage`（`stream_options.include_usage`）、
     * 以及 OpenAI 风格的顶层 `error` 对象。
     */
    fun parseJson(json: JsonObject): LlmDelta {
        (json["error"] as? JsonObject)?.let { error ->
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "服务端返回错误"
            val type = error["type"]?.jsonPrimitive?.contentOrNull
            val code = error["code"]?.jsonPrimitive?.contentOrNull
            val retryable = sequenceOf(type, code).filterNotNull().any {
                it.contains("rate", true) || it.contains("overload", true) || it.contains("timeout", true)
            }
            val label = listOfNotNull(type, code).joinToString("/").takeIf { it.isNotEmpty() }?.let { "（$it）" }.orEmpty()
            return LlmDelta(
                error = LlmError("服务端错误$label：${redactSecrets(message)}", retryable = retryable),
                finishReason = "error",
            )
        }

        var content: String? = null
        var reasoning: String? = null
        var finishReason: String? = null
        val toolCallDeltas = ArrayList<ToolCallDelta>()

        (json["choices"] as? JsonArray)?.firstOrNull()?.let { element ->
            val choice = element as? JsonObject
            if (choice != null) {
                finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                // 标准流式走 delta；少数网关忽略 stream=true 直接返回整包 message（容错）
                val node = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)
                if (node != null) {
                    content = node["content"]?.jsonPrimitive?.contentOrNull
                    reasoning = node["reasoning_content"]?.jsonPrimitive?.contentOrNull
                        ?: node["reasoning"]?.jsonPrimitive?.contentOrNull
                    toolCallDeltas += toolCallsOf(node)
                }
            }
        }

        val usage = (json["usage"] as? JsonObject)?.let { node ->
            val input = node["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val output = node["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            if (input == 0 && output == 0) null else LlmDelta.Usage(input = input, output = output)
        }

        return LlmDelta(
            reasoning = reasoning,
            content = content,
            toolCalls = toolCallDeltas,
            usage = usage,
            finishReason = finishReason,
        )
    }

    /** `delta.tool_calls[]` / `message.tool_calls[]` → 增量分片（index 缺失时按位置兜底）。 */
    private fun toolCallsOf(node: JsonObject): List<ToolCallDelta> {
        val array = node["tool_calls"] as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { position, toolElement ->
            val tool = toolElement as? JsonObject ?: return@mapIndexedNotNull null
            val function = tool["function"] as? JsonObject
            ToolCallDelta(
                index = tool["index"]?.jsonPrimitive?.intOrNull ?: position,
                id = tool["id"]?.jsonPrimitive?.contentOrNull,
                name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                argumentsChunk = function?.get("arguments")?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}

/**
 * OpenAI 兼容协议的流式传输实现（PLAN §5.3 主路径）。
 *
 * - 请求体由 [OpenAiWire] 构造，`extraBody` 不得覆盖 `model/messages/tools/stream`；
 * - `Authorization: Bearer <apiKey>` 只出现在请求头，**永不进日志 / 异常消息**；
 * - 失败重试：**最多 2 次**、指数退避（500ms → 1s），仅对**连接失败 / 5xx / 429**；
 *   一旦已经收到过数据帧就不再重试（避免重复输出）；
 * - 错误统一转 [LlmDelta.error]，本类不抛异常（取消除外）。
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
        val url = OpenAiWire.chatCompletionsUrl(request.baseUrl)
        if (url.isEmpty()) {
            emit(LlmDelta(error = LlmError("未配置供应商 Base URL", retryable = false)))
            return@flow
        }
        val body = OpenAiWire.requestBody(request) { key ->
            // 只报键名，不报值
            log("extraBody 的 \"$key\" 属于受保护字段，已忽略（PLAN §11.1）")
        }.toString()
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${request.apiKey}")
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

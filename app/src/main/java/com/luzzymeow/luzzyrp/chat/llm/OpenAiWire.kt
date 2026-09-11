package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 抗截断「正文工具」定义（`output_reply`）。
 *
 * 与前端 JS（`assets/rphub/assets/js/api-utils.js` 的 `replyTool`）**逐字段同构**：
 * 描述文案、参数 schema、`required` / `additionalProperties` 全部照抄，
 * 否则模型在原生路径与 JS 路径下会看到不同的工具说明。
 */
object ReplyTool {

    const val NAME: String = "output_reply"

    val definition: JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put(
            "function",
            buildJsonObject {
                put("name", JsonPrimitive(NAME))
                put(
                    "description",
                    JsonPrimitive(
                        "将本次回复交给聊天界面显示。遵守现有输出规则，正文及需要附带的面板、" +
                            "图片标记、变量更新块等全部放入 content，不在普通消息中重复输出。" +
                            "检索工具返回结果后，只传新增回复内容。"
                    ),
                )
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "content",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                        put(
                                            "description",
                                            JsonPrimitive("本次回复的原文，保留原有格式；作为 JSON 字符串正确转义。"),
                                        )
                                    },
                                )
                            },
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("content"))))
                        put("additionalProperties", JsonPrimitive(false))
                    },
                )
            },
        )
    }
}

/**
 * OpenAI Chat Completions 协议的**纯函数**线协议层（请求体构造 + 流帧解析）。
 *
 * wire fidelity（保真的三条硬要求，与 JS `requestChatCompletionOnce` 逐键对应）：
 * 1. **键序固定**：`model, messages, temperature, [reasoning_effort], [max_tokens],
 *    <extraBody 展开>, [tools, tool_choice, parallel_tool_calls], stream,
 *    [stream_options]`；
 * 2. **messages 原样转发**：JS 给什么就发什么（含 `reasoning_details` / `extra_content`
 *    等本层不认识的字段），不重新序列化、不丢键、不改序；
 * 3. **extraBody 用对象展开语义**：与 JS 一致——后面显式定义的键（tools 簇 / stream /
 *    stream_options）覆盖它，它覆盖前面的 model/messages/temperature。
 */
object OpenAiWire {

    /** Base URL 原样使用（JS 已拼好 `/chat/completions`；本层不追加路径）。 */
    fun messagesEndpoint(request: LlmRequest): String = request.baseUrl.trim()

    /**
     * 构造请求体。
     *
     * **密钥红线**：本函数只处理模型/采样/扩展字段，**不接触 apiKey**
     * （密钥仅由 [OpenAiTransport] 放进 `Authorization` 头）。
     */
    fun requestBody(request: LlmRequest): JsonObject {
        val tools = toolsFor(request)
        return buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("messages", messagesJson(request.messages))
            request.temperature?.let { put("temperature", JsonPrimitive(it)) }
            request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { put("reasoning_effort", JsonPrimitive(it)) }
            request.maxTokens?.let { put("max_tokens", JsonPrimitive(it)) }
            // extraBody 展开（对象展开语义：同名键在此位置覆盖上面的值）
            request.extraBody?.forEach { (key, value) -> put(key, value) }
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
                put("tool_choice", toolChoice(request))
                put("parallel_tool_calls", JsonPrimitive(false))
            }
            put("stream", JsonPrimitive(request.stream))
            if (request.stream) {
                put("stream_options", buildJsonObject { put("include_usage", JsonPrimitive(true)) })
            }
        }
    }

    /** 最终工具列表：`replyInTool && !requireTool` 时追加抗截断工具（顺序同 JS）。 */
    fun toolsFor(request: LlmRequest): List<JsonObject> =
        if (request.replyInTool && !request.requireTool) request.tools + ReplyTool.definition else request.tools

    /**
     * `tool_choice` 取值（与 JS 三元表达式同构）：
     * - `requireTool` 或「抗截断 + 用户已带工具」→ `"required"`；
     * - 仅抗截断 → 强制点名 `output_reply`；
     * - 其余 → `"auto"`。
     */
    fun toolChoice(request: LlmRequest): kotlinx.serialization.json.JsonElement =
        when {
            request.requireTool || (request.replyInTool && request.tools.isNotEmpty()) -> JsonPrimitive("required")
            request.replyInTool -> buildJsonObject {
                put("type", JsonPrimitive("function"))
                put(
                    "function",
                    buildJsonObject { put("name", JsonPrimitive(ReplyTool.NAME)) },
                )
            }

            else -> JsonPrimitive("auto")
        }

    /** [LlmMessage] → OpenAI 消息数组（[LlmMessage.raw] 原样转发，保键序与未知字段）。 */
    fun messagesJson(messages: List<LlmMessage>): JsonArray =
        JsonArray(messages.map { it.toOpenAiJson() })

    /** 单个 SSE 帧载荷 → [LlmDelta]；非 JSON / 非对象返回 null（忽略，不打断流）。 */
    fun parseFrame(payload: String): LlmDelta? {
        val obj = parseJsonObjectOrNull(payload) ?: return null
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
        json.objOrNull("error")?.let { error ->
            val message = error.strOrNull("message") ?: "服务端返回错误"
            val type = error.strOrNull("type")
            val code = error.strOrNull("code")
            val retryable = sequenceOf(type, code).filterNotNull().any {
                it.contains("rate", true) || it.contains("overload", true) || it.contains("timeout", true)
            }
            val label = listOfNotNull(type, code).joinToString("/").takeIf { it.isNotEmpty() }
                ?.let { "（$it）" }.orEmpty()
            return LlmDelta(
                error = LlmError("服务端错误$label：${redactSecrets(message)}", retryable = retryable),
                finishReason = "error",
            )
        }

        var content: String? = null
        var reasoning: String? = null
        var finishReason: String? = null
        val toolCallDeltas = ArrayList<ToolCallDelta>()

        json.arrayOrNull("choices")?.firstObjectOrNull()?.let { choice ->
            finishReason = choice.strOrNull("finish_reason")
            // 标准流式走 delta；少数网关忽略 stream=true 直接返回整包 message（容错）
            val node = choice.objOrNull("delta") ?: choice.objOrNull("message")
            if (node != null) {
                content = node.strOrNull("content")
                reasoning = node.strOrNull("reasoning_content") ?: node.strOrNull("reasoning")
                toolCallDeltas += toolCallsOf(node)
            }
        }

        val rawUsage = json.objOrNull("usage")
        val usage = rawUsage?.let { node ->
            val input = node.usageCount("prompt_tokens", "input_tokens")
            val output = node.usageCount("completion_tokens", "output_tokens")
            if (input == 0 && output == 0) null else LlmDelta.Usage(input = input, output = output)
        }

        return LlmDelta(
            reasoning = reasoning,
            content = content,
            toolCalls = toolCallDeltas,
            usage = usage,
            rawUsage = rawUsage,
            finishReason = finishReason,
        )
    }

    /** `delta.tool_calls[]` / `message.tool_calls[]` → 增量分片（index 缺失时按位置兜底）。 */
    private fun toolCallsOf(node: JsonObject): List<ToolCallDelta> {
        val array = node.arrayOrNull("tool_calls") ?: return emptyList()
        return array.mapIndexedNotNull { position, toolElement ->
            val tool = toolElement as? JsonObject ?: return@mapIndexedNotNull null
            val function = tool.objOrNull("function")
            ToolCallDelta(
                index = tool.strOrNull("index")?.toIntOrNull() ?: position,
                id = tool.strOrNull("id"),
                name = function?.strOrNull("name"),
                argumentsChunk = function?.strOrNull("arguments"),
            )
        }
    }
}

package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Anthropic Messages API 的**纯函数**线协议层（PLAN §5.3）。
 *
 * 与 OpenAI 的关键差异（全部收敛在此处）：
 * - `system` 是**顶层字段**，不进 `messages`；
 * - 工具结果是 **user 消息里的 `tool_result` block**，不是独立 role；
 * - 思考走 `thinking` block（`thinking_delta`）；
 * - `max_tokens` **必填**（缺省给 4096）；
 * - 鉴权走 `x-api-key` + `anthropic-version`（由传输层加头，**本层不接触密钥**）。
 */
object AnthropicWire {

    const val API_VERSION = "2023-06-01"
    const val DEFAULT_MAX_TOKENS = 4096

    /** Base URL → `/v1/messages`（兼容已写全路径与只写域名两种填法）。 */
    fun messagesUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/v1/messages") || trimmed.endsWith("/messages")) trimmed
        else "$trimmed/v1/messages"
    }

    /** 构造请求体。[onConflict] 语义同 OpenAI 层（只回调键名）。 */
    fun requestBody(request: LlmRequest, onConflict: (String) -> Unit = {}): JsonObject {
        val systemText = request.messages
            .filter { it.role == LlmRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .trim()

        val base = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("max_tokens", JsonPrimitive(request.maxTokens ?: DEFAULT_MAX_TOKENS))
            put("messages", messagesJson(request.messages))
            if (systemText.isNotEmpty()) put("system", JsonPrimitive(systemText))
            if (request.tools.isNotEmpty()) put("tools", toolsJson(request.tools))
            put("stream", JsonPrimitive(true))
            request.temperature?.let { put("temperature", JsonPrimitive(it)) }
            request.topP?.let { put("top_p", JsonPrimitive(it)) }
            request.stop?.takeIf { it.isNotEmpty() }?.let { stop ->
                put("stop_sequences", JsonArray(stop.map { JsonPrimitive(it) }))
            }
            if (request.requireTool && request.tools.isNotEmpty()) {
                put("tool_choice", buildJsonObject { put("type", JsonPrimitive("any")) })
            }
        }
        val extra = request.extraBody ?: return base
        if (extra.isEmpty()) return base
        val protectedKeys = setOf("model", "messages", "system", "tools", "stream", "max_tokens")
        val merged = LinkedHashMap<String, JsonElement>(base)
        for ((key, value) in extra) {
            if (key in protectedKeys) {
                onConflict(key)
                continue
            }
            merged[key] = value
        }
        return JsonObject(merged)
    }

    /** 工具 schema：OpenAI 风格 `{type,function:{…}}` → Anthropic `{name,description,input_schema}`。 */
    fun toolsJson(tools: List<JsonObject>): JsonArray = JsonArray(
        tools.mapNotNull { tool ->
            val function = (tool["function"] as? JsonObject) ?: tool
            val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            buildJsonObject {
                put("name", JsonPrimitive(name))
                function["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", JsonPrimitive(it)) }
                put(
                    "input_schema",
                    function["parameters"] ?: buildJsonObject { put("type", JsonPrimitive("object")) },
                )
            }
        }
    )
}

/**
 * [LlmMessage] → Anthropic `messages` 数组。
 *
 * - SYSTEM 由调用方提到顶层，这里跳过；
 * - ASSISTANT 的 toolCalls → `tool_use` block；文本进 `text` block；
 * - TOOL → **user 消息**里的 `tool_result` block（`is_error` 由内容前缀无法判断，故统一 false）。
 */
internal fun messagesJson(messages: List<LlmMessage>): JsonArray = JsonArray(
    messages.mapNotNull { message ->
        when (message.role) {
            LlmRole.SYSTEM -> null

            LlmRole.USER -> buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonArray(listOf(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(message.content))
                })))
            }

            LlmRole.ASSISTANT -> {
                val blocks = mutableListOf<JsonElement>()
                if (message.content.isNotBlank()) {
                    blocks += buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(message.content))
                    }
                }
                message.toolCalls.forEach { call ->
                    blocks += buildJsonObject {
                        put("type", JsonPrimitive("tool_use"))
                        put("id", JsonPrimitive(call.id))
                        put("name", JsonPrimitive(call.name))
                        put("input", call.arguments)
                    }
                }
                if (blocks.isEmpty()) null
                else buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonArray(blocks))
                }
            }

            LlmRole.TOOL -> buildJsonObject {
                put("role", JsonPrimitive("user"))
                put(
                    "content",
                    JsonArray(listOf(buildJsonObject {
                        put("type", JsonPrimitive("tool_result"))
                        put("tool_use_id", JsonPrimitive(message.toolCallId ?: ""))
                        put("content", JsonPrimitive(message.content))
                    })),
                )
            }
        }
    }
)

/** Anthropic SSE 事件 → [LlmDelta]（纯函数，可单测）。 */
internal fun parseAnthropicFrame(payload: String): LlmDelta? {
    val root = runCatching { JsonLenient.json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    return when (root["type"]?.jsonPrimitive?.contentOrNull) {
        "content_block_delta" -> {
            val delta = root["delta"] as? JsonObject ?: return null
            when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                "text_delta" -> LlmDelta(content = delta["text"]?.jsonPrimitive?.contentOrNull)
                "thinking_delta" -> LlmDelta(reasoning = delta["thinking"]?.jsonPrimitive?.contentOrNull)
                "input_json_delta" -> LlmDelta(
                    toolCalls = listOf(
                        ToolCallDelta(
                            index = root["index"]?.jsonPrimitive?.intOrNull ?: 0,
                            argumentsChunk = delta["partial_json"]?.jsonPrimitive?.contentOrNull,
                        )
                    )
                )
                else -> null
            }
        }

        "content_block_start" -> {
            val block = root["content_block"] as? JsonObject ?: return null
            if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                LlmDelta(
                    toolCalls = listOf(
                        ToolCallDelta(
                            index = root["index"]?.jsonPrimitive?.intOrNull ?: 0,
                            id = block["id"]?.jsonPrimitive?.contentOrNull,
                            name = block["name"]?.jsonPrimitive?.contentOrNull,
                        )
                    )
                )
            } else null
        }

        "message_delta" -> {
            val usage = root["usage"] as? JsonObject
            LlmDelta(
                usage = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull?.let { out ->
                    LlmDelta.Usage(input = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0, output = out)
                },
                finishReason = (root["delta"] as? JsonObject)?.get("stop_reason")?.jsonPrimitive?.contentOrNull,
            )
        }

        "message_stop" -> LlmDelta(finishReason = "stop")

        "error" -> {
            val error = root["error"] as? JsonObject
            LlmDelta(
                error = LlmError(
                    message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Anthropic 返回错误",
                    retryable = error?.get("type")?.jsonPrimitive?.contentOrNull == "overloaded_error",
                )
            )
        }

        else -> null
    }
}

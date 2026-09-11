package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Anthropic Messages API 的**纯函数**线协议层。
 *
 * 与 JS（`assets/rphub/assets/js/api-utils.js` 的 `toAnthropicMessages` /
 * `anthropicThinkingConfig` / `requestAnthropicCompletionInternal`）逐键对应：
 * - **URL 原样使用** baseUrl（JS 已剥掉 `/chat/completions`；本层**不**追加 `/v1/messages`）；
 * - 键序固定：`model, max_tokens, [system], messages, [temperature], [thinking],
 *   <extraBody 展开>, stream`；
 * - `max_tokens` 缺省 **8192**（与 JS `options.maxTokens || 8192` 同）；
 * - `thinking` 预算守卫见 [thinkingConfig]（与 JS `anthropicThinkingConfig` 同式）；
 * - 消息转换见 [toAnthropicMessages]（角色映射 / system 抽取 / parts 转换 / 相邻合并）。
 *
 * 密钥（`x-api-key`）与版本头由 [AnthropicTransport] 加，**本层不接触密钥**。
 */
object AnthropicWire {

    const val API_VERSION = "2023-06-01"

    /** `max_tokens` 缺省值（JS 同值）。 */
    const val DEFAULT_MAX_TOKENS = 8192

    /**
     * thinking 预算守卫（与 JS `anthropicThinkingConfig` 同式）：
     * - `max_tokens < 2048` → 不启用（返回 null）；
     * - 否则 `budget = max(1024, min(64000, round(max_tokens * 0.75)))`；
     * - 仅当 `budget < max_tokens` 才返回 `{type:"enabled", budget_tokens}`，否则 null。
     */
    fun thinkingConfig(maxTokens: Int?): JsonObject? {
        val total = maxTokens?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS
        if (total < 2048) return null
        val budget = maxOf(1024, minOf(64000, Math.round(total * 0.75).toInt()))
        if (budget >= total) return null
        return buildJsonObject {
            put("type", JsonPrimitive("enabled"))
            put("budget_tokens", JsonPrimitive(budget))
        }
    }

    /** 实际生效的 `max_tokens`（缺省 8192）。 */
    fun effectiveMaxTokens(request: LlmRequest): Int =
        request.maxTokens?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS

    /** 构造请求体（键序与 extraBody 展开语义见类注释）。 */
    fun requestBody(request: LlmRequest): JsonObject {
        val maxTokens = effectiveMaxTokens(request)
        val (system, messages) = toAnthropicMessages(request.messages)
        val thinking = request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { thinkingConfig(maxTokens) }
        return buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("max_tokens", JsonPrimitive(maxTokens))
            if (system.isNotEmpty()) put("system", JsonPrimitive(system))
            put("messages", messages)
            request.temperature?.takeIf { it.isFinite() }?.let { put("temperature", JsonPrimitive(it)) }
            thinking?.let { put("thinking", it) }
            request.extraBody?.forEach { (key, value) -> put(key, value) }
            put("stream", JsonPrimitive(request.stream))
        }
    }

    /**
     * 消息转换（与 JS `toAnthropicMessages` 同构）。
     *
     * 规则：
     * 1. **首条 `system` 消息**（v2.0 归一形态）→ 顶层 `system`，不进 messages；
     * 2. 上游形态兜底：**首条纯文本 user 消息**（且总消息数 > 1）→ 顶层 `system`（JS 原规则）；
     * 3. 其余角色映射：`assistant` → `assistant`，**其它一律 → `user`**（含 `tool`）；
     * 4. content 数组 → Anthropic parts：`text` → `{type:text,text}`；
     *    `image_url` 的 data URL → `{type:image,source:{type:base64,media_type,data}}`（非 data URL 丢弃）；
     * 5. 首条必须是 user —— 否则前置 `{role:user,content:[{type:text,text:"(begin)"}]}` 占位；
     * 6. 相邻同角色合并（Anthropic 严格交替，上游消息流可能产生连续 user）。
     */
    fun toAnthropicMessages(messages: List<LlmMessage>): Pair<String, JsonArray> {
        val source = messages.map { it.toOpenAiJson() }
        var system = ""
        val converted = ArrayList<JsonObject>()

        source.forEachIndexed { index, message ->
            val rawRole = message.strOrNull("role")
            val content = message["content"]
            if (index == 0 && rawRole == "system") {
                system = content.contentText()
                return@forEachIndexed
            }
            val role = if (rawRole == "assistant") "assistant" else "user"
            if (role == "user" && content.isJsonString() && index == 0 && source.size > 1) {
                system = content.contentText()
                return@forEachIndexed
            }
            val mapped = toAnthropicContent(content)
            converted += buildJsonObject {
                put("role", JsonPrimitive(role))
                if (mapped != null) put("content", mapped)
            }
        }

        if (converted.isEmpty() || converted[0].strOrNull("role") != "user") {
            converted.add(
                0,
                buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonArray(listOf(textBlock("(begin)"))))
                },
            )
        }

        val merged = ArrayList<JsonObject>()
        for (message in converted) {
            val last = merged.lastOrNull()
            if (last != null && last.strOrNull("role") == message.strOrNull("role")) {
                merged[merged.size - 1] = last.withContent(
                    JsonArray(anthropicParts(last["content"]) + anthropicParts(message["content"])),
                )
            } else {
                merged += message
            }
        }
        return system to JsonArray(merged)
    }

    /** content 转换：数组 → parts（空数组补一个空 text part）；其它原样透传。 */
    private fun toAnthropicContent(content: JsonElement?): JsonElement? = when {
        content is JsonArray -> {
            val parts = content.objects().mapNotNull { part ->
                when (part.strOrNull("type")) {
                    "text" -> textBlock(part.strOrNull("text") ?: "")
                    "image_url" -> {
                        val url = part.objOrNull("image_url")?.strOrNull("url").orEmpty()
                        val parsed = parseBase64DataUrl(url) ?: return@mapNotNull null
                        buildJsonObject {
                            put("type", JsonPrimitive("image"))
                            put(
                                "source",
                                buildJsonObject {
                                    put("type", JsonPrimitive("base64"))
                                    put("media_type", JsonPrimitive(parsed.first))
                                    put("data", JsonPrimitive(parsed.second))
                                },
                            )
                        }
                    }

                    else -> null
                }
            }
            JsonArray(if (parts.isNotEmpty()) parts else listOf(textBlock("")))
        }

        else -> content
    }

    /** 合并时的 parts 视图：数组原样，标量包成一个 text block（同 JS 三元表达式）。 */
    private fun anthropicParts(content: JsonElement?): List<JsonElement> =
        if (content is JsonArray) content else listOf(textBlock(content.contentText()))

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("text"))
        put("text", JsonPrimitive(text))
    }
}

/**
 * Anthropic SSE 事件 → [LlmDelta]（纯函数，可单测）。
 *
 * 覆盖 JS `parseAnthropicSseChunk` 的正文/思考路径，并补齐传输层需要的
 * 工具分片（`content_block_start` / `input_json_delta`）、`usage`、`stop_reason` 与错误帧。
 */
internal fun parseAnthropicFrame(payload: String): LlmDelta? {
    val root = parseJsonObjectOrNull(payload) ?: return null
    val rawUsage = root.objOrNull("usage")
    return when (root.strOrNull("type")) {
        "content_block_delta" -> {
            val delta = root.objOrNull("delta") ?: return null
            when (delta.strOrNull("type")) {
                "text_delta" -> LlmDelta(content = delta.strOrNull("text"))
                "thinking_delta" -> LlmDelta(reasoning = delta.strOrNull("thinking"))
                "input_json_delta" -> LlmDelta(
                    toolCalls = listOf(
                        ToolCallDelta(
                            index = root.strOrNull("index")?.toIntOrNull() ?: 0,
                            argumentsChunk = delta.strOrNull("partial_json"),
                        )
                    )
                )

                else -> null
            }
        }

        "content_block_start" -> {
            val block = root.objOrNull("content_block") ?: return null
            if (block.strOrNull("type") == "tool_use") {
                LlmDelta(
                    toolCalls = listOf(
                        ToolCallDelta(
                            index = root.strOrNull("index")?.toIntOrNull() ?: 0,
                            id = block.strOrNull("id"),
                            name = block.strOrNull("name"),
                        )
                    )
                )
            } else null
        }

        "message_delta" -> {
            val stopReason = root.objOrNull("delta")?.strOrNull("stop_reason")
            LlmDelta(
                usage = usageOf(rawUsage),
                rawUsage = rawUsage,
                finishReason = stopReason,
            )
        }

        "message_start" -> LlmDelta(
            usage = usageOf(rawUsage ?: root.objOrNull("message")?.objOrNull("usage")),
            rawUsage = rawUsage ?: root.objOrNull("message")?.objOrNull("usage"),
        )

        // 非流式整包（少数网关忽略 stream=true）与 JS `data.type === 'message'` 分支同构
        "message" -> {
            val content = StringBuilder()
            val reasoning = StringBuilder()
            root.arrayOrNull("content")?.objects()?.forEach { block ->
                when (block.strOrNull("type")) {
                    "text" -> content.append(block.strOrNull("text").orEmpty())
                    "thinking" -> reasoning.append(block.strOrNull("thinking").orEmpty())
                }
            }
            LlmDelta(
                content = content.toString().ifEmpty { null },
                reasoning = reasoning.toString().ifEmpty { null },
                usage = usageOf(rawUsage),
                rawUsage = rawUsage,
                finishReason = root.strOrNull("stop_reason"),
            )
        }

        // 流结束标记：**不带** finishReason——真正的停止原因在 message_delta.stop_reason，
        // 若这里补一个 "stop" 会把 `tool_use` / `end_turn` 顶掉（抗截断判断要用它）。
        "message_stop" -> LlmDelta()

        "error" -> {
            val error = root.objOrNull("error")
            LlmDelta(
                error = LlmError(
                    message = redactSecrets(error?.strOrNull("message") ?: "Anthropic 返回错误"),
                    retryable = error?.strOrNull("type") == "overloaded_error",
                ),
                finishReason = "error",
            )
        }

        else -> null
    }
}

/** `usage` → 归一用量（Anthropic 用 `input_tokens` / `output_tokens`）。 */
private fun usageOf(usage: JsonObject?): LlmDelta.Usage? {
    if (usage == null) return null
    val input = usage.usageCount("input_tokens", "prompt_tokens")
    val output = usage.usageCount("output_tokens", "completion_tokens")
    if (input == 0 && output == 0) return null
    return LlmDelta.Usage(input = input, output = output)
}

package com.luzzymeow.luzzyrp.core.ai.provider.anthropic

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.params.ToolChoice
import com.luzzymeow.luzzyrp.core.ai.provider.Provider
import com.luzzymeow.luzzyrp.core.ai.util.AiJson
import com.luzzymeow.luzzyrp.core.ai.util.buildApiErrorMessage
import com.luzzymeow.luzzyrp.core.ai.util.jsonArrayOrNull
import com.luzzymeow.luzzyrp.core.ai.util.jsonObjectOrNull
import com.luzzymeow.luzzyrp.core.ai.util.jsonPrimitiveOrNull
import com.luzzymeow.luzzyrp.core.ai.util.mergeCustomBody
import com.luzzymeow.luzzyrp.core.ai.util.parseErrorDetail
import com.luzzymeow.luzzyrp.core.ai.util.stringSafe
import com.luzzymeow.luzzyrp.core.ai.util.toHeaders
import com.luzzymeow.luzzyrp.core.model.MessageChunk
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.TokenUsage
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessageChoice
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Anthropic messages API 实现。
 *
 * [INVARIANT-STREAMING] 流式结构与 OpenAICompatibleProvider 相同铁律：
 *   callbackFlow + EventSources + 逐 event trySend + awaitClose cancel。
 *   Anthropic 事件流（message_start / content_block_start / content_block_delta /
 *   content_block_stop / message_delta / message_stop）逐 event 解析合并。
 *
 * [INVARIANT-KV] 缓存：system 块与 tools 末位挂 cache_control: ephemeral 断点，
 * 与「稳定前缀」层对应，保护角色卡/世界书/工具说明的 KV 命中。
 *
 * [INVARIANT-AGENTIC] 不发送 tool_choice="required"（工具结果经 user 消息的
 * tool_result 块回传，模型自主决定是否继续调用）。
 */
class AnthropicProvider(
    private val client: OkHttpClient,
) : Provider<ProviderSetting> {

    override suspend fun listModels(setting: ProviderSetting): List<Model> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(setting.baseUrl.trimEnd('/') + "/v1/models")
            .addHeader("x-api-key", setting.apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val data = AiJson.parseToJsonElement(body).jsonObject["data"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            data.mapNotNull { el ->
                val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                Model(id = id, displayName = id, capabilities = ModelDefaultCaps)
            }
        }
    }

    override suspend fun generateText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildMessageRequest(messages, params)
        val request = buildRequest(setting, requestBody, stream = false)
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val json = AiJson.parseToJsonElement(body).jsonObject
            val content = json["content"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            MessageChunk(
                id = json.str("id").orEmpty(),
                model = json.str("model").orEmpty(),
                choices = listOf(
                    UIMessageChoice(
                        index = 0,
                        message = parseContentBlocks(content),
                        finishReason = json.str("stop_reason"),
                    )
                ),
                usage = parseUsage(json),
            )
        }
    }

    override fun streamText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildMessageRequest(messages, params, stream = true)
        val request = buildRequest(setting, requestBody, stream = true)

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                val dataJson = runCatching { AiJson.parseToJsonElement(data).jsonObject }
                    .getOrElse { return }

                when (type) {
                    "message_stop" -> {
                        close()
                        return
                    }
                    "error" -> {
                        close(dataJson["error"]?.parseErrorDetail())
                        return
                    }
                }

                // content_block_start 的完整块 + content_block_delta 的增量块统一解析
                val blocks = buildJsonArray {
                    dataJson["content_block"]?.jsonObjectOrNull()?.let { add(it) }
                    dataJson["delta"]?.jsonObjectOrNull()?.let { add(it) }
                }
                val deltaMessage = if (blocks.isEmpty()) null else parseContentBlocks(blocks)
                val usage = parseUsage(dataJson)

                if (deltaMessage != null || usage != null) {
                    trySend(
                        MessageChunk(
                            id = id.orEmpty(),
                            choices = if (deltaMessage != null) listOf(
                                UIMessageChoice(index = 0, delta = deltaMessage)
                            ) else emptyList(),
                            usage = usage,
                        )
                    )
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                var exception: Throwable? = t
                val bodyRaw = response?.stringSafe()
                if (!bodyRaw.isNullOrBlank()) {
                    exception = runCatching {
                        AiJson.parseToJsonElement(bodyRaw).parseErrorDetail()
                    }.getOrElse { exception ?: it }
                }
                close(exception)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        // [INVARIANT-STREAMING] 核心：标准 EventSource 工厂 + 逐 event trySend
        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }

    override suspend fun generateEmbedding(setting: ProviderSetting, texts: List<String>): List<FloatArray> =
        error("Anthropic 不提供嵌入接口；请使用其他供应商的嵌入模型")

    // ------------------------------------------------------------------
    // 请求组装
    // ------------------------------------------------------------------

    private fun buildRequest(setting: ProviderSetting, body: JsonObject, stream: Boolean): Request =
        Request.Builder()
            .url(setting.baseUrl.trimEnd('/') + "/v1/messages")
            .headers(setting.customHeaders.toHeaders())
            .post(AiJson.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", setting.apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .apply { if (stream) addHeader("Accept", "text/event-stream") }
            .build()

    private fun buildMessageRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("model", params.model.id)
        put("max_tokens", (params.maxTokens ?: 64_000L).toInt())
        put("messages", buildProtocolMessages(messages))

        // system 提示（稳定前缀）+ 缓存断点
        val systemMessage = messages.firstOrNull { it.role == Role.SYSTEM }
        val systemText = systemMessage?.textContent().orEmpty()
        if (systemText.isNotBlank()) {
            put("system", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", systemText)
                    put("cache_control", buildJsonObject { put("type", "ephemeral") })
                })
            })
        }

        if (params.temperature != null) put("temperature", params.temperature)
        if (params.topP != null) put("top_p", params.topP)
        put("stream", stream)

        // thinking：adaptive 模式（新 API）；关闭时显式 disabled
        if (params.model.capabilities.reasoning) {
            if (params.thinking.enabled == false) {
                put("thinking", buildJsonObject { put("type", "disabled") })
            } else {
                put("thinking", buildJsonObject {
                    put("type", "adaptive")
                    put("display", "summarized")
                })
            }
        }

        // 工具定义 + 末位缓存断点
        if (params.model.capabilities.tool && params.tools.isNotEmpty()) {
            putJsonArray("tools") {
                params.tools.forEachIndexed { index, tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put(
                            "input_schema",
                            runCatching { AiJson.parseToJsonElement(tool.parametersJson) }
                                .getOrElse { buildJsonObject { } },
                        )
                        if (index == params.tools.lastIndex) {
                            put("cache_control", buildJsonObject { put("type", "ephemeral") })
                        }
                    })
                }
            }
            // [INVARIANT-AGENTIC] toolChoice：Anthropic 协议不发送 required
            when (params.toolChoice) {
                ToolChoice.NONE -> put("tool_choice", buildJsonObject { put("type", "none") })
                ToolChoice.AUTO -> {
                    // 默认 auto
                }
            }
        }
    }.mergeCustomBody(params.customBody)

    private fun buildProtocolMessages(messages: List<UIMessage>) = buildJsonArray {
        messages.filter { it.role != Role.SYSTEM }.forEach { message ->
            when (message.role) {
                Role.USER -> add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        message.parts.forEach { part ->
                            when (part) {
                                is UIMessagePart.Text -> if (part.text.isNotBlank()) add(textBlock(part.text))
                                is UIMessagePart.Image -> add(imageBlock(part))
                                else -> {}
                            }
                        }
                    }
                })

                Role.ASSISTANT -> {
                    val groups = com.luzzymeow.luzzyrp.core.ai.util.groupPartsByToolBoundary(message.parts)
                    val contentBuffer = mutableListOf<JsonObject>()
                    fun flush(tools: List<UIMessagePart.Tool>) {
                        if (contentBuffer.isNotEmpty() || tools.isNotEmpty()) {
                            add(buildJsonObject {
                                put("role", "assistant")
                                putJsonArray("content") { contentBuffer.forEach { add(it) } }
                            })
                            contentBuffer.clear()
                        }
                        if (tools.isNotEmpty()) {
                            add(buildJsonObject {
                                put("role", "user")
                                putJsonArray("content") {
                                    tools.forEach { tool ->
                                        add(buildJsonObject {
                                            put("type", "tool_result")
                                            put("tool_use_id", tool.toolCallId)
                                            putJsonArray("content") {
                                                tool.output?.forEach { out ->
                                                    if (out is UIMessagePart.Text) add(textBlock(out.text))
                                                } ?: add(textBlock(""))
                                            }
                                        })
                                    }
                                }
                            })
                        }
                    }

                    groups.forEach { group ->
                        when (group) {
                            is com.luzzymeow.luzzyrp.core.ai.util.PartGroup.Content -> {
                                group.parts.forEach { part ->
                                    when (part) {
                                        is UIMessagePart.Text -> if (part.text.isNotBlank()) contentBuffer.add(textBlock(part.text))
                                        is UIMessagePart.Reasoning -> {
                                            if (part.thinking.isNotBlank()) {
                                                contentBuffer.add(buildJsonObject {
                                                    put("type", "thinking")
                                                    put("thinking", part.thinking)
                                                    if (part.signature != null) put("signature", part.signature)
                                                })
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                            is com.luzzymeow.luzzyrp.core.ai.util.PartGroup.Tools -> flush(group.tools)
                        }
                    }
                    flush(emptyList())
                }

                else -> {}
            }
        }
    }

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun imageBlock(image: UIMessagePart.Image): JsonObject = buildJsonObject {
        put("type", "image")
        put("source", buildJsonObject {
            put("type", "base64")
            put("media_type", image.mime)
            put("data", image.data.removePrefix("data:${image.mime};base64,"))
        })
    }

    // ------------------------------------------------------------------
    // 响应解析
    // ------------------------------------------------------------------

    private fun parseContentBlocks(content: JsonArray): UIMessage {
        val parts = mutableListOf<UIMessagePart>()
        content.forEach { blockEl ->
            val block = blockEl.jsonObjectOrNull() ?: return@forEach
            when (block.str("type")) {
                "text", "text_delta" -> {
                    val text = block.str("text").orEmpty()
                    if (text.isNotEmpty()) parts.add(UIMessagePart.Text(text))
                }

                "thinking", "thinking_delta" -> {
                    val thinking = block.str("thinking").orEmpty()
                    val signature = block.str("signature")
                    if (thinking.isNotEmpty() || signature != null) {
                        parts.add(UIMessagePart.Reasoning(thinking = thinking, signature = signature))
                    }
                }

                "signature_delta" -> {
                    val signature = block.str("signature")
                    if (signature != null) {
                        parts.add(UIMessagePart.Reasoning(thinking = "", signature = signature))
                    }
                }

                "tool_use" -> {
                    val input = block["input"]?.jsonObjectOrNull()
                    parts.add(
                        UIMessagePart.Tool(
                            toolCallId = block.str("id").orEmpty(),
                            toolName = block.str("name").orEmpty(),
                            input = if (input == null || input.isEmpty()) JsonPrimitive("{}")
                            else JsonPrimitive(Json.encodeToString(JsonObject.serializer(), input)),
                            output = null,
                        )
                    )
                }

                "input_json_delta" -> {
                    // 空 toolCallId：由 appendChunk 合并到最后一个 Tool part
                    parts.add(
                        UIMessagePart.Tool(
                            toolCallId = "",
                            toolName = "",
                            input = JsonPrimitive(block.str("partial_json").orEmpty()),
                            output = null,
                        )
                    )
                }
            }
        }
        return UIMessage(role = Role.ASSISTANT, parts = parts)
    }

    private fun parseUsage(json: JsonObject): TokenUsage? {
        val usageJson = json["usage"]?.jsonObjectOrNull()
            ?: json["message"]?.jsonObjectOrNull()?.get("usage")?.jsonObjectOrNull()
            ?: return null
        val inputTokens = usageJson["input_tokens"]?.jsonPrimitiveOrNull()?.content?.toLongOrNull() ?: 0L
        val cacheRead = usageJson["cache_read_input_tokens"]?.jsonPrimitiveOrNull()?.content?.toLongOrNull() ?: 0L
        val cacheCreation = usageJson["cache_creation_input_tokens"]?.jsonPrimitiveOrNull()?.content?.toLongOrNull() ?: 0L
        val outputTokens = usageJson["output_tokens"]?.jsonPrimitiveOrNull()?.content?.toLongOrNull() ?: 0L
        val promptTokens = inputTokens + cacheRead + cacheCreation
        return TokenUsage(
            promptTokens = promptTokens,
            completionTokens = outputTokens,
            totalTokens = promptTokens + outputTokens,
            cachedTokens = cacheRead,
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitiveOrNull()?.contentOrNull

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val ModelDefaultCaps = com.luzzymeow.luzzyrp.core.model.ModelCapability(
            reasoning = true, tool = true,
        )
    }
}

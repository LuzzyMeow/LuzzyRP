package com.luzzymeow.luzzyrp.core.ai.provider.google

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.params.ToolChoice
import com.luzzymeow.luzzyrp.core.ai.provider.Provider
import com.luzzymeow.luzzyrp.core.ai.util.parseErrorDetail
import com.luzzymeow.luzzyrp.core.ai.util.AiJson
import com.luzzymeow.luzzyrp.core.ai.util.buildApiErrorMessage
import com.luzzymeow.luzzyrp.core.ai.util.jsonArrayOrNull
import com.luzzymeow.luzzyrp.core.ai.util.jsonObjectOrNull
import com.luzzymeow.luzzyrp.core.ai.util.jsonPrimitiveOrNull
import com.luzzymeow.luzzyrp.core.ai.util.mergeCustomBody
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
import com.luzzymeow.luzzyrp.core.model.parsedInput
import kotlinx.serialization.json.contentOrNull
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * Google Gemini 协议实现（streamGenerateContent?alt=sse）。
 *
 * [INVARIANT-STREAMING] 流式结构与 OpenAICompatibleProvider 相同铁律：
 *   callbackFlow + EventSources + 逐 event trySend + awaitClose cancel。
 *   每个 SSE event 的 data 为一个完整 GenerateContentResponse JSON。
 *
 * [INVARIANT-AGENTIC] functionCall/functionResponse 往返；不发送强制工具模式。
 */
class GoogleProvider(
    private val client: OkHttpClient,
) : Provider<ProviderSetting> {

    override suspend fun listModels(setting: ProviderSetting): List<Model> = withContext(Dispatchers.IO) {
        val url = setting.baseUrl.trimEnd('/') + "/v1beta/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", setting.apiKey)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val models = AiJson.parseToJsonElement(body).jsonObject["models"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            models.mapNotNull { el ->
                val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                // name 形如 "models/gemini-2.0-flash"
                val name = obj.str("name")?.substringAfter("models/") ?: return@mapNotNull null
                val supported = obj["supportedGenerationMethods"]?.jsonArrayOrNull()
                Model(
                    id = name,
                    displayName = obj.str("displayName") ?: name,
                    capabilities = com.luzzymeow.luzzyrp.core.model.ModelCapability(
                        reasoning = supported?.any { it.jsonPrimitiveOrNull()?.contentOrNull == "generateContent" } == true,
                        tool = true,
                    ),
                )
            }
        }
    }

    override suspend fun generateText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildGenerateContentRequest(messages, params)
        val request = buildRequest(setting, params.model.id, requestBody, stream = false)
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val json = AiJson.parseToJsonElement(body).jsonObject
            val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            MessageChunk(
                id = json.str("responseId").orEmpty(),
                model = params.model.id,
                choices = listOf(
                    UIMessageChoice(
                        index = 0,
                        message = parseParts(candidate?.get("content")?.jsonObjectOrNull()?.get("parts")?.jsonArrayOrNull()),
                        finishReason = candidate?.str("finishReason"),
                    )
                ),
                usage = parseUsage(json["usageMetadata"]?.jsonObjectOrNull()),
            )
        }
    }

    override fun streamText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildGenerateContentRequest(messages, params)
        val request = buildRequest(setting, params.model.id, requestBody, stream = true)

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                val obj = runCatching { AiJson.parseToJsonElement(data).jsonObject }
                    .getOrElse { return }

                if (obj["error"] != null) {
                    close(obj["error"]!!.parseErrorDetail())
                    return
                }

                val candidate = obj["candidates"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                val deltaMessage = parseParts(candidate?.get("content")?.jsonObjectOrNull()?.get("parts")?.jsonArrayOrNull())
                val usage = parseUsage(obj["usageMetadata"]?.jsonObjectOrNull())

                if (deltaMessage != null || usage != null) {
                    trySend(
                        MessageChunk(
                            id = obj.str("responseId").orEmpty(),
                            model = obj.str("modelVersion").orEmpty(),
                            choices = if (deltaMessage != null) listOf(
                                UIMessageChoice(
                                    index = 0,
                                    delta = deltaMessage,
                                    finishReason = candidate?.str("finishReason"),
                                )
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
        withContext(Dispatchers.IO) {
            val embeddingModel = setting.models.firstOrNull { it.capabilities.embedding }?.id
                ?: setting.models.firstOrNull()?.id
                ?: error("供应商未配置嵌入模型")
            val url = setting.baseUrl.trimEnd('/') + "/v1beta/models/$embeddingModel:batchEmbedContents"
            val body = buildJsonObject {
                put("requests", buildJsonArray {
                    texts.forEach { text ->
                        add(buildJsonObject {
                            put("model", "models/$embeddingModel")
                            put("content", buildJsonObject {
                                put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
                            })
                        })
                    }
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(AiJson.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
                .addHeader("x-goog-api-key", setting.apiKey)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.stringSafe()
                check(response.isSuccessful && raw != null) { buildApiErrorMessage(response.code, raw) }
                val embeddings = AiJson.parseToJsonElement(raw).jsonObject["embeddings"]?.jsonArrayOrNull()
                    ?: error("嵌入响应缺少 embeddings")
                embeddings.mapNotNull { el ->
                    val values = el.jsonObjectOrNull()?.get("values")?.jsonArrayOrNull() ?: return@mapNotNull null
                    FloatArray(values.size) { i -> values[i].jsonPrimitive.content.toFloat() }
                }
            }
        }

    // ------------------------------------------------------------------
    // 请求组装
    // ------------------------------------------------------------------

    private fun buildRequest(setting: ProviderSetting, modelId: String, body: JsonObject, stream: Boolean): Request {
        val method = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return Request.Builder()
            .url("${setting.baseUrl.trimEnd('/')}/v1beta/models/$modelId:$method")
            .headers(setting.customHeaders.toHeaders())
            .post(AiJson.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .addHeader("x-goog-api-key", setting.apiKey)
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun buildGenerateContentRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): JsonObject = buildJsonObject {
        // systemInstruction 对应稳定前缀（KV 命中位）
        val systemMessage = messages.firstOrNull { it.role == Role.SYSTEM }
        val systemText = systemMessage?.textContent().orEmpty()
        if (systemText.isNotBlank()) {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemText) }) })
            })
        }

        put("contents", buildContents(messages))

        put("generationConfig", buildJsonObject {
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("topP", params.topP)
            if (params.maxTokens != null) put("maxOutputTokens", params.maxTokens)
        })

        // thinking：thinkingConfig.thinkingBudget（Gemini 2.5+）
        if (params.model.capabilities.reasoning) {
            if (params.thinking.enabled == false) {
                put("generationConfig", buildJsonObject {
                    put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
                })
            }
        }

        if (params.model.capabilities.tool && params.tools.isNotEmpty()) {
            putJsonArray("tools") {
                add(buildJsonObject {
                    putJsonArray("functionDeclarations") {
                        params.tools.forEach { tool ->
                            add(buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put(
                                    "parameters",
                                    runCatching { AiJson.parseToJsonElement(tool.parametersJson) }
                                        .getOrElse { buildJsonObject { } },
                                )
                            })
                        }
                    }
                })
            }
            // [INVARIANT-AGENTIC] toolChoice：Gemini 不发送强制模式
            when (params.toolChoice) {
                ToolChoice.NONE -> put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject { put("mode", "NONE") })
                })
                ToolChoice.AUTO -> {
                    // 默认 AUTO
                }
            }
        }
    }.mergeCustomBody(params.customBody)

    private fun buildContents(messages: List<UIMessage>) = buildJsonArray {
        messages.filter { it.role != Role.SYSTEM }.forEach { message ->
            when (message.role) {
                Role.USER -> add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        message.parts.forEach { part ->
                            when (part) {
                                is UIMessagePart.Text -> if (part.text.isNotBlank()) {
                                    add(buildJsonObject { put("text", part.text) })
                                }
                                is UIMessagePart.Image -> {
                                    add(buildJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", part.mime)
                                            put("data", part.data.removePrefix("data:${part.mime};base64,"))
                                        })
                                    })
                                }
                                else -> {}
                            }
                        }
                    })
                })

                Role.ASSISTANT -> {
                    val groups = com.luzzymeow.luzzyrp.core.ai.util.groupPartsByToolBoundary(message.parts)
                    var textBuffer = StringBuilder()
                    var thoughtBuffer = StringBuilder()

                    fun flush(tools: List<UIMessagePart.Tool>) {
                        val parts = buildJsonArray {
                            if (thoughtBuffer.isNotBlank()) {
                                add(buildJsonObject { put("text", thoughtBuffer.toString()) })
                            }
                            if (textBuffer.isNotBlank()) {
                                add(buildJsonObject { put("text", textBuffer.toString()) })
                            }
                            tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", tool.toolName)
                                        put("args", tool.parsedInput())
                                    })
                                })
                            }
                        }
                        if (parts.isNotEmpty()) {
                            add(buildJsonObject {
                                put("role", "model")
                                put("parts", parts)
                            })
                        }
                        if (tools.isNotEmpty()) {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    tools.forEach { tool ->
                                        add(buildJsonObject {
                                            put("functionResponse", buildJsonObject {
                                                put("name", tool.toolName)
                                                put("response", buildJsonObject {
                                                    put(
                                                        "result",
                                                        tool.output?.filterIsInstance<UIMessagePart.Text>()
                                                            ?.joinToString("\n") { it.text }
                                                            .orEmpty(),
                                                    )
                                                })
                                            })
                                        })
                                    }
                                })
                            })
                        }
                        textBuffer = StringBuilder()
                        thoughtBuffer = StringBuilder()
                    }

                    groups.forEach { group ->
                        when (group) {
                            is com.luzzymeow.luzzyrp.core.ai.util.PartGroup.Content -> group.parts.forEach { part ->
                                when (part) {
                                    is UIMessagePart.Text -> textBuffer.append(part.text)
                                    is UIMessagePart.Reasoning -> thoughtBuffer.append(part.thinking)
                                    else -> {}
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

    // ------------------------------------------------------------------
    // 响应解析
    // ------------------------------------------------------------------

    private fun parseParts(parts: JsonArray?): UIMessage? {
        parts ?: return null
        val messageParts = mutableListOf<UIMessagePart>()
        parts.forEach { partEl ->
            val part = partEl.jsonObjectOrNull() ?: return@forEach
            if (part.containsKey("functionCall")) {
                val call = part["functionCall"]!!.jsonObject
                messageParts.add(
                    UIMessagePart.Tool(
                        toolCallId = "google_${System.nanoTime()}",
                        toolName = call.str("name").orEmpty(),
                        input = JsonPrimitive(Json.encodeToString(JsonObject.serializer(), call["args"]?.jsonObjectOrNull() ?: buildJsonObject { })),
                        output = null,
                    )
                )
            } else {
                val text = part.str("text").orEmpty()
                if (text.isNotEmpty()) {
                    val thought = part["thought"]?.jsonPrimitiveOrNull()?.contentOrNull == "true"
                    messageParts.add(
                        if (thought) UIMessagePart.Reasoning(thinking = text)
                        else UIMessagePart.Text(text)
                    )
                }
            }
        }
        return if (messageParts.isEmpty()) null else UIMessage(role = Role.ASSISTANT, parts = messageParts)
    }

    private fun parseUsage(obj: JsonObject?): TokenUsage? {
        obj ?: return null
        val promptTokens = obj["promptTokenCount"]?.jsonPrimitive?.longOrNull ?: 0
        val completionTokens = obj["candidatesTokenCount"]?.jsonPrimitive?.longOrNull ?: 0
        val cachedTokens = obj["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull ?: 0
        val thoughtTokens = obj["thoughtsTokenCount"]?.jsonPrimitive?.longOrNull ?: 0
        return TokenUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens + thoughtTokens,
            totalTokens = obj["totalTokenCount"]?.jsonPrimitive?.longOrNull ?: (promptTokens + completionTokens),
            cachedTokens = cachedTokens,
            reasoningTokens = thoughtTokens,
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitiveOrNull()?.contentOrNull
}

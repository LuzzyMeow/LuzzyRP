package com.luzzymeow.luzzyrp.core.ai.provider.openai

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
import com.luzzymeow.luzzyrp.core.model.parsedInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * OpenAI 兼容协议实现（chat/completions + embeddings）。
 * 一等公民协议：DeepSeek / 火山方舟 / GLM / OpenRouter / 任意兼容端点。
 *
 * [INVARIANT-STREAMING] streamText 实现链（HARD_REQUIREMENTS 规定 1 / 不变性 S3）：
 *   1. callbackFlow + EventSources.createFactory(client).newEventSource(request, listener)
 *   2. onEvent 内 trySend(messageChunk) —— 每个 SSE event 一次发射，零节流零缓冲
 *   3. awaitClose { eventSource.cancel() } —— 取消收集即取消连接（Stop 按钮语义）
 *   4. [DONE] 或 onClosed → close()；onFailure → 解析错误体后 close(error)
 *   禁止移除上述结构；禁止用轮询/分段请求模拟流式。
 *
 * [INVARIANT-AGENTIC] 工具协议（Agentic 不变性 A5）：
 *   toolChoice 仅 AUTO/NONE，本实现永不发送 tool_choice="required"。
 */
class OpenAICompatibleProvider(
    private val client: OkHttpClient,
) : Provider<ProviderSetting> {

    override suspend fun listModels(setting: ProviderSetting): List<Model> = withContext(Dispatchers.IO) {
        val url = setting.baseUrl.trimEnd('/') + "/models"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${setting.apiKey}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val data = AiJson.parseToJsonElement(body).jsonObject["data"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
            data.mapNotNull { el ->
                val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                Model(id = id, displayName = id)
            }
        }
    }

    override suspend fun generateText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildChatCompletionRequest(setting, messages, params, stream = false)
        val request = buildRequest(setting, requestBody, stream = false)
        client.newCall(request).execute().use { response ->
            val body = response.stringSafe()
            check(response.isSuccessful && body != null) { buildApiErrorMessage(response.code, body) }
            val json = AiJson.parseToJsonElement(body).jsonObject
            val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("响应缺少 choices")
            MessageChunk(
                id = json.str("id").orEmpty(),
                model = json.str("model").orEmpty(),
                choices = listOf(
                    UIMessageChoice(
                        index = 0,
                        message = parseAssistantMessage(choice["message"]?.jsonObjectOrNull() ?: buildJsonObject { }),
                        finishReason = choice.str("finish_reason"),
                    )
                ),
                usage = parseUsage(json["usage"]?.jsonObjectOrNull()),
            )
        }
    }

    override fun streamText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildChatCompletionRequest(setting, messages, params, stream = true)
        val request = buildRequest(setting, requestBody, stream = true)

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                // 部分端点把多条 JSON 塞进一个 event：逐行解析；心跳等不可解析行跳过
                data.trim().split("\n")
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        val obj = runCatching { AiJson.parseToJsonElement(line).jsonObject }
                            .getOrElse { return@forEach }
                        if (obj["error"] != null) {
                            throw obj["error"]!!.parseErrorDetail()
                        }
                        trySend(parseStreamChunk(obj))
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

        // [INVARIANT-STREAMING] 核心：标准 EventSource 工厂 + 逐 event trySend + awaitClose cancel
        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose {
            eventSource.cancel()
        }
    }

    override suspend fun generateEmbedding(setting: ProviderSetting, texts: List<String>): List<FloatArray> =
        withContext(Dispatchers.IO) {
            val url = setting.baseUrl.trimEnd('/') + "/embeddings"
            val embeddingModel = setting.models.firstOrNull { it.capabilities.embedding }?.id
                ?: setting.models.firstOrNull()?.id
                ?: error("供应商未配置嵌入模型")
            val body = buildJsonObject {
                put("model", embeddingModel)
                put("input", buildJsonArray { texts.forEach { add(it) } })
            }
            val request = Request.Builder()
                .url(url)
                .post(AiJson.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${setting.apiKey}")
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.stringSafe()
                check(response.isSuccessful && raw != null) { buildApiErrorMessage(response.code, raw) }
                val json = AiJson.parseToJsonElement(raw).jsonObject
                val data = json["data"]?.jsonArrayOrNull() ?: error("嵌入响应缺少 data")
                data.mapNotNull { el ->
                    val embedding = el.jsonObjectOrNull()?.get("embedding")?.jsonArrayOrNull()
                        ?: return@mapNotNull null
                    FloatArray(embedding.size) { i -> embedding[i].jsonPrimitive.content.toFloat() }
                }
            }
        }

    // ------------------------------------------------------------------
    // 请求组装
    // ------------------------------------------------------------------

    private fun buildRequest(setting: ProviderSetting, body: JsonObject, stream: Boolean): Request =
        Request.Builder()
            .url(setting.baseUrl.trimEnd('/') + "/chat/completions")
            .headers(setting.customHeaders.toHeaders())
            .post(AiJson.encodeToString(JsonObject.serializer(), body).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${setting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .apply { if (stream) addHeader("Accept", "text/event-stream") }
            .build()

    private fun buildChatCompletionRequest(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean,
    ): JsonObject {
        val host = setting.baseUrl.toHttpUrl().host
        return buildJsonObject {
            put("model", params.model.id)
            put("messages", buildProtocolMessages(messages))

            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("top_p", params.topP)
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)

            put("stream", stream)
            if (stream) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }

            applyThinkingParams(this, host, params)

            // 工具定义 —— [INVARIANT-AGENTIC] toolChoice 仅 AUTO/NONE
            if (params.model.capabilities.tool && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put(
                                    "parameters",
                                    runCatching { AiJson.parseToJsonElement(tool.parametersJson) }
                                        .getOrElse { buildJsonObject { } },
                                )
                            })
                        })
                    }
                }
                when (params.toolChoice) {
                    ToolChoice.AUTO -> {
                        // 协议默认即 auto：不发送字段（部分端点不接受显式 auto）
                    }
                    ToolChoice.NONE -> put("tool_choice", "none")
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    /**
     * 思考参数主机适配表（与 rikkahub 对齐）：
     *   火山方舟/智谱/月之暗面：thinking.type；DeepSeek：thinking + reasoning_effort；
     *   OpenRouter：reasoning.{enabled,effort}；其余：reasoning_effort（low/medium/high）。
     */
    private fun applyThinkingParams(builder: JsonObjectBuilder, host: String, params: TextGenerationParams) {
        if (!params.model.capabilities.reasoning) return
        val thinking = params.thinking
        when (host) {
            "ark.cn-beijing.volces.com", "open.bigmodel.cn", "api.moonshot.cn" -> builder.put("thinking", buildJsonObject {
                put("type", if (thinking.enabled == false) "disabled" else "enabled")
            })

            "api.deepseek.com" -> {
                builder.put("thinking", buildJsonObject {
                    put("type", if (thinking.enabled == false) "disabled" else "enabled")
                })
                if (thinking.enabled != false && thinking.effort != null) {
                    builder.put("reasoning_effort", thinking.effort)
                }
            }

            "openrouter.ai" -> builder.put("reasoning", buildJsonObject {
                if (thinking.enabled == false) put("effort", "none")
                else if (thinking.effort != null) put("effort", thinking.effort)
                else put("enabled", true)
            })

            else -> {
                if (thinking.enabled != false && thinking.effort != null) {
                    builder.put("reasoning_effort", thinking.effort)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 消息序列化（存储模型 → 协议格式）
    // ------------------------------------------------------------------

    /**
     * 存储消息 → OpenAI 协议消息数组。
     * [INVARIANT-KV] 字段构造次序固定：同一历史两次组装请求体逐字节一致。
     */
    private fun buildProtocolMessages(messages: List<UIMessage>) = buildJsonArray {
        messages.forEach { message ->
            when (message.role) {
                Role.SYSTEM -> add(buildJsonObject {
                    put("role", "system")
                    put("content", message.textContent())
                })

                Role.USER -> add(buildJsonObject {
                    put("role", "user")
                    val texts = message.parts.filterIsInstance<UIMessagePart.Text>()
                    val images = message.parts.filterIsInstance<UIMessagePart.Image>()
                    if (images.isEmpty()) {
                        put("content", texts.joinToString("\n") { it.text })
                    } else {
                        putJsonArray("content") {
                            texts.forEach { t ->
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", t.text)
                                })
                            }
                            images.forEach { img ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put(
                                            "url",
                                            if (img.data.startsWith("http")) img.data
                                            else "data:${img.mime};base64,${img.data}",
                                        )
                                    })
                                })
                            }
                        }
                    }
                })

                Role.ASSISTANT -> addAssistantMessage(message)
            }
        }
    }

    /**
     * assistant 消息按工具边界分组还原：
     *   …content… → {assistant(content+tool_calls)} + {tool 结果} × N → …content…
     * 存储层的原地回填结果在这里展开为协议要求的 role:"tool" 消息。
     */
    private fun kotlinx.serialization.json.JsonArrayBuilder.addAssistantMessage(message: UIMessage) {
        val groups = com.luzzymeow.luzzyrp.core.ai.util.groupPartsByToolBoundary(message.parts)
        var pendingReasoning: String? = null
        var pendingSignature: String? = null
        val contentBuffer = StringBuilder()

        fun flush(tools: List<UIMessagePart.Tool>) {
            val text = contentBuffer.toString()
            val reasoning = pendingReasoning
            if (text.isBlank() && tools.isEmpty() && reasoning.isNullOrBlank()) {
                contentBuffer.clear()
                return
            }
            add(buildJsonObject {
                put("role", "assistant")
                if (!reasoning.isNullOrBlank()) put("reasoning_content", reasoning)
                put("content", text)
                if (tools.isNotEmpty()) {
                    putJsonArray("tool_calls") {
                        tools.forEach { tool ->
                            add(buildJsonObject {
                                put("id", tool.toolCallId)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", tool.toolName)
                                    put("arguments", tool.parsedInput())
                                })
                            })
                        }
                    }
                }
            })
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", tool.toolCallId)
                    put("name", tool.toolName)
                    put(
                        "content",
                        tool.output?.filterIsInstance<UIMessagePart.Text>()
                            ?.joinToString("\n") { it.text }
                            .orEmpty(),
                    )
                })
            }
            contentBuffer.clear()
            pendingReasoning = null
            pendingSignature = null
        }

        groups.forEach { group ->
            when (group) {
                is com.luzzymeow.luzzyrp.core.ai.util.PartGroup.Content -> {
                    group.parts.filterIsInstance<UIMessagePart.Reasoning>().firstOrNull()?.let {
                        pendingReasoning = it.thinking
                        pendingSignature = it.signature
                    }
                    group.parts.filter { it is UIMessagePart.Text || it is UIMessagePart.Image }
                        .filterIsInstance<UIMessagePart.Text>()
                        .forEach { contentBuffer.append(it.text) }
                }

                is com.luzzymeow.luzzyrp.core.ai.util.PartGroup.Tools -> flush(group.tools)
            }
        }
        flush(emptyList())
    }

    // ------------------------------------------------------------------
    // 响应解析
    // ------------------------------------------------------------------

    private fun parseStreamChunk(obj: JsonObject): MessageChunk = MessageChunk(
        id = obj.str("id").orEmpty(),
        model = obj.str("model").orEmpty(),
        choices = buildList {
            val choices = obj["choices"]?.jsonArrayOrNull()
            if (!choices.isNullOrEmpty()) {
                val choice = choices[0].jsonObject
                val delta = choice["delta"]?.jsonObjectOrNull() ?: choice["message"]?.jsonObjectOrNull()
                if (delta != null) {
                    add(
                        UIMessageChoice(
                            index = 0,
                            delta = parseAssistantMessage(delta),
                            finishReason = choice.str("finish_reason"),
                        )
                    )
                }
            }
        },
        usage = parseUsage(obj["usage"]?.jsonObjectOrNull()),
    )

    /** 解析 assistant delta/message → UIMessage（兼容 GLM/DeepSeek reasoning_content、Mistral thinking 数组）。 */
    private fun parseAssistantMessage(obj: JsonObject): UIMessage {
        val role = when (obj.str("role")?.uppercase()) {
            "USER" -> Role.USER
            "SYSTEM" -> Role.SYSTEM
            else -> Role.ASSISTANT
        }
        val reasoning = obj.str("reasoning_content")
            ?: obj.str("reasoning")
            ?: obj["content"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                ?.get("thinking")?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                ?.get("text")?.jsonPrimitiveOrNull()?.contentOrNull
        val content = obj.str("content").orEmpty()
        val toolCalls = obj["tool_calls"]?.jsonArrayOrNull() ?: JsonArray(emptyList())

        return UIMessage(
            role = role,
            parts = buildList {
                if (!reasoning.isNullOrBlank()) {
                    add(UIMessagePart.Reasoning(thinking = reasoning))
                }
                toolCalls.forEach { callEl ->
                    val call = callEl.jsonObjectOrNull() ?: return@forEach
                    val function = call["function"]?.jsonObjectOrNull() ?: return@forEach
                    add(
                        UIMessagePart.Tool(
                            toolCallId = call.str("id").orEmpty(),
                            toolName = function.str("name").orEmpty(),
                            input = JsonPrimitive(function.str("arguments") ?: "{}"),
                            output = null,
                        )
                    )
                }
                if (content.isNotEmpty()) {
                    add(UIMessagePart.Text(content))
                }
            },
        )
    }

    private fun parseUsage(obj: JsonObject?): TokenUsage? {
        obj ?: return null
        return TokenUsage(
            promptTokens = obj["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
            completionTokens = obj["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
            totalTokens = obj["total_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
            cachedTokens = obj["prompt_tokens_details"]?.jsonObjectOrNull()
                ?.get("cached_tokens")?.jsonPrimitive?.longOrNull ?: 0,
            reasoningTokens = obj["completion_tokens_details"]?.jsonObjectOrNull()
                ?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitiveOrNull()?.contentOrNull
}

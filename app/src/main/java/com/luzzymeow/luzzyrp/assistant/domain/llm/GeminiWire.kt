package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gemini `streamGenerateContent` 的**纯函数**线协议层（PLAN §5.3）。
 *
 * 与 OpenAI 的关键差异：
 * - 端点形如 `/v1beta/models/{model}:streamGenerateContent?alt=sse`；
 * - 角色只有 `user` / `model`（无 system 角色 → `systemInstruction` 顶层字段）；
 * - 工具调用是 `parts[].functionCall`，**没有 id** → 本层合成 `call_<index>_<name>`；
 * - 工具结果走 `parts[].functionResponse`（按函数名回填）；
 * - 采样参数在 `generationConfig` 下。
 */
object GeminiWire {

    const val DEFAULT_MAX_TOKENS = 4096

    /** Base URL + 模型 → 流式端点（自动补 `/v1beta`）。 */
    fun streamUrl(baseUrl: String, model: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty() || model.isBlank()) return ""
        val base = when {
            trimmed.endsWith("/v1beta") || trimmed.endsWith("/v1") -> trimmed
            trimmed.contains("/v1beta/") || trimmed.contains("/v1/") -> trimmed.substringBefore("/models")
            else -> "$trimmed/v1beta"
        }
        return "$base/models/$model:streamGenerateContent?alt=sse"
    }

    /** 构造请求体（密钥不进 body，由传输层加 `x-goog-api-key` 头）。 */
    fun requestBody(request: LlmRequest, onConflict: (String) -> Unit = {}): JsonObject {
        val systemText = request.messages
            .filter { it.role == LlmRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .trim()

        val generationConfig = buildJsonObject {
            request.temperature?.let { put("temperature", JsonPrimitive(it)) }
            request.topP?.let { put("topP", JsonPrimitive(it)) }
            put("maxOutputTokens", JsonPrimitive(request.maxTokens ?: DEFAULT_MAX_TOKENS))
            request.stop?.takeIf { it.isNotEmpty() }?.let { stop ->
                put("stopSequences", JsonArray(stop.map { JsonPrimitive(it) }))
            }
        }

        val base = buildJsonObject {
            put("contents", contentsJson(request.messages))
            if (systemText.isNotEmpty()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive(systemText)) })))
                    },
                )
            }
            if (request.tools.isNotEmpty()) put("tools", functionDeclarations(request.tools))
            put("generationConfig", generationConfig)
        }

        val extra = request.extraBody ?: return base
        if (extra.isEmpty()) return base
        val protectedKeys = setOf("contents", "systemInstruction", "tools", "generationConfig")
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

    /** OpenAI 风格工具 schema → `tools[0].functionDeclarations`。 */
    fun functionDeclarations(tools: List<JsonObject>): JsonArray = JsonArray(
        listOf(
            buildJsonObject {
                put(
                    "functionDeclarations",
                    JsonArray(
                        tools.mapNotNull { tool ->
                            val function = (tool["function"] as? JsonObject) ?: tool
                            val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            buildJsonObject {
                                put("name", JsonPrimitive(name))
                                function["description"]?.jsonPrimitive?.contentOrNull?.let {
                                    put("description", JsonPrimitive(it))
                                }
                                put(
                                    "parameters",
                                    function["parameters"] ?: buildJsonObject { put("type", JsonPrimitive("object")) },
                                )
                            }
                        }
                    ),
                )
            }
        )
    )
}

/**
 * [LlmMessage] → Gemini `contents`。
 *
 * - SYSTEM 提到 `systemInstruction`，此处跳过；
 * - ASSISTANT → role=model，文本进 `text` part，工具调用进 `functionCall` part；
 * - TOOL → role=user 的 `functionResponse` part（按函数名回填）。
 */
internal fun contentsJson(messages: List<LlmMessage>): JsonArray =
    JsonArray(
        messages.mapNotNull { message ->
            when (message.role) {
                LlmRole.SYSTEM -> null

                LlmRole.USER -> buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", JsonArray(listOf(buildJsonObject { put("text", JsonPrimitive(message.content)) })))
                }

                LlmRole.ASSISTANT -> {
                    val parts = mutableListOf<JsonElement>()
                    if (message.content.isNotBlank()) {
                        parts += buildJsonObject { put("text", JsonPrimitive(message.content)) }
                    }
                    message.toolCalls.forEach { call ->
                        parts += buildJsonObject {
                            put(
                                "functionCall",
                                buildJsonObject {
                                    put("name", JsonPrimitive(call.name))
                                    put("args", call.arguments)
                                },
                            )
                        }
                    }
                    if (parts.isEmpty()) null
                    else buildJsonObject {
                        put("role", JsonPrimitive("model"))
                        put("parts", JsonArray(parts))
                    }
                }

                LlmRole.TOOL -> buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put(
                        "parts",
                        JsonArray(listOf(buildJsonObject {
                            put(
                                "functionResponse",
                                buildJsonObject {
                                    put("name", JsonPrimitive(message.name ?: ""))
                                    put(
                                        "response",
                                        buildJsonObject { put("result", JsonPrimitive(message.content)) },
                                    )
                                },
                            )
                        })),
                    )
                }
            }
        }
    )

/** Gemini SSE 帧 → [LlmDelta]（纯函数，可单测）。 */
internal fun parseGeminiFrame(payload: String, callIndexBase: Int = 0): LlmDelta? {
    val root = runCatching { JsonLenient.json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null

    (root["error"] as? JsonObject)?.let { error ->
        return LlmDelta(
            error = LlmError(
                message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Gemini 返回错误",
                retryable = error["status"]?.jsonPrimitive?.contentOrNull == "UNAVAILABLE",
            )
        )
    }

    val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
    val parts = (candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray

    val content = StringBuilder()
    val reasoning = StringBuilder()
    val toolCalls = mutableListOf<ToolCallDelta>()
    parts?.forEachIndexed { index, element ->
        val part = element as? JsonObject ?: return@forEachIndexed
        part["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
            if (part["thought"]?.jsonPrimitive?.contentOrNull == "true") reasoning.append(text)
            else content.append(text)
        }
        (part["functionCall"] as? JsonObject)?.let { call ->
            val name = call["name"]?.jsonPrimitive?.contentOrNull ?: return@let
            val args = call["args"]?.toString() ?: "{}"
            toolCalls += ToolCallDelta(
                index = callIndexBase + index,
                id = "call_${callIndexBase + index}_$name",
                name = name,
                argumentsChunk = args,
            )
        }
    }

    val usage = root["usageMetadata"] as? JsonObject
    return LlmDelta(
        content = content.toString().ifEmpty { null },
        reasoning = reasoning.toString().ifEmpty { null },
        toolCalls = toolCalls,
        usage = usage?.let {
            LlmDelta.Usage(
                input = it["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                output = it["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        },
        finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull,
    )
}

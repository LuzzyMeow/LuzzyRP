package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Google Gemini `generateContent` / `streamGenerateContent` 的**纯函数**线协议层。
 *
 * 与 JS（`assets/rphub/assets/js/api-utils.js` 的 `requestGeminiCompletionInternal`
 * 与 `GEMINI_THINKING_BUDGETS`）逐键对应：
 * - 端点由本层拼接：`{base}/v1beta/models/{encodeURIComponent(model)}:` +
 *   `streamGenerateContent?alt=sse&` / `generateContent?` + `key={encodeURIComponent(apiKey)}`；
 * - 头**只有** `Content-Type: application/json`（密钥在查询串里，与 JS 同）；
 * - 键序固定：`contents, [systemInstruction], generationConfig, <extraBody 展开>`；
 * - `generationConfig` 恒在（无采样参数时是空对象，与 JS 同）。
 */
object GeminiWire {

    /** reasoningEffort → thinkingBudget（与 JS `GEMINI_THINKING_BUDGETS` 同值）。 */
    val THINKING_BUDGETS: Map<String, Int> = mapOf(
        "low" to 1024,
        "medium" to 8192,
        "high" to 24576,
        "max" to 32768,
    )

    /**
     * 端点拼接（与 JS 同式）。base 的尾部斜杠先剥掉；空 base 或空模型返回 ""。
     */
    fun endpoint(baseUrl: String, model: String, apiKey: String, stream: Boolean): String {
        val base = baseUrl.trim().replace(Regex("/+$"), "")
        if (base.isEmpty() || model.isBlank()) return ""
        val method = if (stream) "streamGenerateContent?alt=sse&" else "generateContent?"
        return "$base/v1beta/models/${encodeUriComponent(model)}:$method" +
            "key=${encodeUriComponent(apiKey)}"
    }

    /** 构造请求体（键序与 extraBody 展开语义见类注释）。 */
    fun requestBody(request: LlmRequest): JsonObject {
        val (contents, systemInstruction) = toGeminiContents(request.messages)
        val thinking = request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { THINKING_BUDGETS[it] }
        return buildJsonObject {
            put("contents", contents)
            systemInstruction?.let { put("systemInstruction", it) }
            put(
                "generationConfig",
                buildJsonObject {
                    request.temperature?.takeIf { it.isFinite() }?.let { put("temperature", JsonPrimitive(it)) }
                    request.maxTokens?.takeIf { it > 0 }?.let { put("maxOutputTokens", JsonPrimitive(it)) }
                    thinking?.let {
                        put(
                            "thinkingConfig",
                            buildJsonObject { put("thinkingBudget", JsonPrimitive(it)) },
                        )
                    }
                },
            )
            request.extraBody?.forEach { (key, value) -> put(key, value) }
        }
    }

    /**
     * 消息转换（与 JS `requestGeminiCompletionInternal` 内联转换同构）。
     *
     * 规则：
     * 1. **首条 `system` 消息**（v2.0 归一形态）→ 顶层 `systemInstruction`；
     * 2. 上游形态兜底：**首条纯文本 user 消息**（且总消息数 > 1）→ `systemInstruction`（JS 原规则）；
     * 3. 角色映射：`assistant` → `model`，**其它一律 → `user`**（含 `tool`）；
     * 4. content 数组 → parts：`text` → `{text}`；`image_url` 的 data URL →
     *    `{inlineData:{mimeType,data}}`（非 data URL 丢弃）；其它 → `[{text: String(content||'')}]`；
     * 5. parts 为空的条目整条丢弃；相邻同角色合并 parts。
     */
    fun toGeminiContents(messages: List<LlmMessage>): Pair<JsonArray, JsonObject?> {
        val source = messages.map { it.toOpenAiJson() }
        val contents = ArrayList<JsonObject>()
        var systemInstruction: JsonObject? = null

        source.forEachIndexed { index, message ->
            val rawRole = message.strOrNull("role")
            val content = message["content"]
            if (index == 0 && rawRole == "system") {
                systemInstruction = systemInstructionOf(content.contentText())
                return@forEachIndexed
            }
            val role = if (rawRole == "assistant") "model" else "user"
            if (role == "user" && content.isJsonString() && index == 0 && source.size > 1) {
                systemInstruction = systemInstructionOf(content.contentText())
                return@forEachIndexed
            }
            val parts = if (content is JsonArray) {
                content.objects().mapNotNull { part ->
                    when (part.strOrNull("type")) {
                        "text" -> geminiTextPart(part.strOrNull("text") ?: "")
                        "image_url" -> {
                            val url = part.objOrNull("image_url")?.strOrNull("url").orEmpty()
                            val parsed = parseBase64DataUrl(url) ?: return@mapNotNull null
                            buildJsonObject {
                                put(
                                    "inlineData",
                                    buildJsonObject {
                                        put("mimeType", JsonPrimitive(parsed.first))
                                        put("data", JsonPrimitive(parsed.second))
                                    },
                                )
                            }
                        }

                        else -> null
                    }
                }
            } else {
                listOf(geminiTextPart(content.contentText()))
            }
            if (parts.isEmpty()) return@forEachIndexed

            val last = contents.lastOrNull()
            if (last != null && last.strOrNull("role") == role) {
                contents[contents.size - 1] = last.withParts(
                    JsonArray(last.arrayOrNull("parts").orEmpty() + parts),
                )
            } else {
                contents += buildJsonObject {
                    put("role", JsonPrimitive(role))
                    put("parts", JsonArray(parts))
                }
            }
        }
        return JsonArray(contents) to systemInstruction
    }

    private fun systemInstructionOf(text: String): JsonObject = buildJsonObject {
        put("parts", JsonArray(listOf(geminiTextPart(text))))
    }

    private fun geminiTextPart(text: String): JsonObject = buildJsonObject {
        put("text", JsonPrimitive(text))
    }

    /** 原位替换 `parts`（保留其余键与键序），对应 JS 的 `last.parts = [...]`。 */
    private fun JsonObject.withParts(value: JsonArray): JsonObject {
        val merged = LinkedHashMap<String, JsonElement>(this)
        merged["parts"] = value
        return JsonObject(merged)
    }
}

/** `encodeURIComponent` 的未转义字符集（与 JS 规范一字不差）。 */
private const val URI_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * 等价 JS `encodeURIComponent`：UTF-8 逐字节百分号编码，未保留字符集与 JS 一致。
 *
 * 与 `java.net.URLEncoder` 的差别（必须自己实现的原因）：后者把空格编成 `+`、
 * 且会把 `~` `!` `*` `'` `(` `)` 也编码——模型 ID 与密钥里出现这些字符时，
 * 会与 JS 路径产生不同 URL。
 */
internal fun encodeUriComponent(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val index = byte.toInt() and 0xFF
        val ch = index.toChar()
        if (index < 128 && URI_UNRESERVED.indexOf(ch) >= 0) {
            out.append(ch)
        } else {
            out.append('%').append(HEX_DIGITS[index shr 4]).append(HEX_DIGITS[index and 0xF])
        }
    }
    return out.toString()
}

/**
 * Gemini SSE 帧 → [LlmDelta]（纯函数，可单测）。
 *
 * 覆盖 JS `parseGeminiChunk` 的正文/思考路径（`part.thought === true` 走思考），
 * 并补齐工具调用（`parts[].functionCall`，无 id → 合成 `call_<n>_<name>`）、
 * `usageMetadata`、`finishReason` 与错误帧。
 */
internal fun parseGeminiFrame(payload: String, callIndexBase: Int = 0): LlmDelta? {
    val root = parseJsonObjectOrNull(payload) ?: return null

    root.objOrNull("error")?.let { error ->
        return LlmDelta(
            error = LlmError(
                message = redactSecrets(error.strOrNull("message") ?: "Gemini 返回错误"),
                retryable = error.strOrNull("status") == "UNAVAILABLE",
            ),
            finishReason = "error",
        )
    }

    val candidate = root.arrayOrNull("candidates")?.firstObjectOrNull()
    val parts = candidate?.objOrNull("content")?.arrayOrNull("parts")

    val content = StringBuilder()
    val reasoning = StringBuilder()
    val toolCalls = mutableListOf<ToolCallDelta>()
    parts?.forEachIndexed { index, element ->
        val part = element as? JsonObject ?: return@forEachIndexed
        part.strOrNull("text")?.let { text ->
            if (part.strOrNull("thought") == "true") reasoning.append(text) else content.append(text)
        }
        part.objOrNull("functionCall")?.let { call ->
            val name = call.strOrNull("name") ?: return@let
            toolCalls += ToolCallDelta(
                index = callIndexBase + index,
                id = "call_${callIndexBase + index}_$name",
                name = name,
                argumentsChunk = call["args"]?.toString() ?: "{}",
            )
        }
    }

    val rawUsage = root.objOrNull("usageMetadata")
    return LlmDelta(
        content = content.toString().ifEmpty { null },
        reasoning = reasoning.toString().ifEmpty { null },
        toolCalls = toolCalls,
        usage = rawUsage?.let {
            LlmDelta.Usage(
                input = it.usageCount("promptTokenCount"),
                output = it.usageCount("candidatesTokenCount"),
            )
        },
        rawUsage = rawUsage,
        finishReason = candidate?.strOrNull("finishReason"),
    )
}

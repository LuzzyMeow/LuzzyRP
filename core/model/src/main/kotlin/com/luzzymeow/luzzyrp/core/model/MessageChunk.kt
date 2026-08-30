package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [INVARIANT-STREAMING] 流式分块与合并代数
 *
 * MessageChunk 是 SSE 单个 event 的解析产物；handleMessageChunk 将其合并进消息列表。
 * 真流式链路：SSE event → trySend(MessageChunk) → handleMessageChunk → StateFlow 更新，
 * 每 event 一次更新（1 字 = 1 次更新），本文件是逐字流式的数学核心。
 */

/** Token 用量（token 统计行数据源：↑in(cache·%) ↓out · tok/s）。 */
@Serializable
data class TokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    /** 命中缓存的 prompt tokens（OpenAI prompt_tokens_details.cached_tokens 等）。 */
    val cachedTokens: Long = 0,
    /** 推理（思考）token 数。 */
    val reasoningTokens: Long = 0,
)

/** 流式选择项：delta 为增量，message 为整段（非流式回退）。 */
@Serializable
data class UIMessageChoice(
    val index: Int = 0,
    val delta: UIMessage? = null,
    val message: UIMessage? = null,
    val finishReason: String? = null,
)

/** 单个 SSE event 解析产物。 */
@Serializable
data class MessageChunk(
    val id: String = "",
    val model: String = "",
    val choices: List<UIMessageChoice> = emptyList(),
    val usage: TokenUsage? = null,
)

/**
 * 将 chunk 合并进消息列表（纯函数）。
 *
 * [INVARIANT-STREAMING] 本函数是流式合并代数的唯一实现（参考 rikkahub 同名函数）：
 *   - delta.role 与末消息一致 → 在末消息上合并；
 *   - 否则新建消息追加；
 *   - chunk.usage 到达 → 写入末 assistant 消息的 usage 字段（token 统计行数据源）。
 * 禁止在此引入节流/缓冲/批量合并。
 */
fun List<UIMessage>.handleMessageChunk(chunk: MessageChunk): List<UIMessage> {
    if (chunk.choices.isEmpty()) {
        // 仅 usage 的尾包：补记到最后一条 assistant 消息
        if (chunk.usage != null) return attachUsageToLast(chunk.usage)
        return this
    }
    var messages = this
    for (choice in chunk.choices) {
        val delta = choice.delta
        val whole = choice.message
        if (whole != null) {
            messages = messages + whole
            continue
        }
        if (delta == null) continue

        val last = messages.lastOrNull()
        // 合并语义与 rikkahub 对齐：OpenAI 流式 delta 仅在首个 chunk 带 role，
        // 后续 delta 由协议层补默认角色；同一轮生成内 delta 角色一致即合并，
        // role 变更（user→assistant）即开新消息。禁止按消息 id 判定（delta 无稳定 id）。
        val canMerge = last != null && last.role == delta.role
        messages = if (canMerge && last != null) {
            messages.dropLast(1) + last.appendChunk(delta)
        } else {
            messages + delta.copy(
                id = delta.id.ifBlank { last?.id ?: delta.id },
                modelId = chunk.model.ifBlank { null },
            )
        }
    }
    if (chunk.usage != null) messages = messages.attachUsageToLast(chunk.usage)
    return messages
}

private fun List<UIMessage>.attachUsageToLast(usage: TokenUsage): List<UIMessage> {
    val last = lastOrNull() ?: return this
    return dropLast(1) + last.copy(usage = usage)
}

/**
 * 将增量 chunk 合并到单条消息（纯函数，返回新消息）。
 *
 * 合并规则（OpenAI 增量语义）：
 *   - Text 增量 → 追加到末尾 Text part（无则新建）——保证 1 字 = 1 次更新；
 *   - Reasoning 增量 → 追加到末尾 Reasoning part（思考卡片逐字）；
 *   - Tool 增量 → 按 toolCallId 定位既有 Tool part，toolName/input 字符串拼接；
 *   - Image 增量 → 整体追加（图片为整块到达）。
 */
fun UIMessage.appendChunk(delta: UIMessage): UIMessage {
    var parts = this.parts
    for (part in delta.parts) {
        when (part) {
            is UIMessagePart.Text -> {
                if (part.text.isEmpty()) continue
                val lastTextIndex = parts.indexOfLast { it is UIMessagePart.Text }
                parts = if (lastTextIndex >= 0) {
                    val lastText = parts[lastTextIndex] as UIMessagePart.Text
                    parts.toMutableList().apply {
                        set(lastTextIndex, lastText.copy(text = lastText.text + part.text))
                    }.toList()
                } else {
                    parts + UIMessagePart.Text(part.text)
                }
            }

            is UIMessagePart.Reasoning -> {
                if (part.thinking.isEmpty() && part.signature == null) continue
                val lastReasoningIndex = parts.indexOfLast { it is UIMessagePart.Reasoning }
                parts = if (lastReasoningIndex >= 0) {
                    val lastReasoning = parts[lastReasoningIndex] as UIMessagePart.Reasoning
                    parts.toMutableList().apply {
                        set(
                            lastReasoningIndex,
                            lastReasoning.copy(
                                thinking = lastReasoning.thinking + part.thinking,
                                signature = part.signature ?: lastReasoning.signature,
                            ),
                        )
                    }.toList()
                } else {
                    parts + part
                }
            }

            is UIMessagePart.Tool -> {
                // Anthropic 的 input_json_delta 以空 toolCallId 流式下发参数：
                // 空 id 增量合并到最后一个 Tool part；有 id 时按 id 定位（OpenAI 语义）
                val existingIndex = parts.indexOfLast {
                    it is UIMessagePart.Tool &&
                        (if (part.toolCallId.isBlank()) true else it.toolCallId == part.toolCallId) &&
                        !it.isExecuted
                }
                parts = if (existingIndex >= 0) {
                    val existing = parts[existingIndex] as UIMessagePart.Tool
                    parts.toMutableList().apply {
                        set(
                            existingIndex,
                            existing.copy(
                                toolName = if (part.toolName.isBlank()) existing.toolName
                                else existing.toolName + part.toolName,
                                input = mergeJsonStringInput(existing.input, part.input),
                            ),
                        )
                    }.toList()
                } else {
                    parts + part
                }
            }

            is UIMessagePart.Image -> parts + part
        }
    }
    return copy(parts = parts)
}

/**
 * input 增量拼接：协议将 input 以「字符串化 JSON」流式下发，
 * 两侧均为字符串原语时做字符串级拼接（OpenAI arguments 增量语义）。
 */
private fun mergeJsonStringInput(existing: JsonElement, delta: JsonElement): JsonElement {
    val existingStr = (existing as? JsonPrimitive)?.takeIf { it.isString }?.content
    val deltaStr = (delta as? JsonPrimitive)?.takeIf { it.isString }?.content
    return when {
        existingStr != null && deltaStr != null -> JsonPrimitive(existingStr + deltaStr)
        existingStr != null && delta is JsonNull -> JsonPrimitive(existingStr)
        (existingStr == null || existing is JsonNull) && deltaStr != null -> JsonPrimitive(deltaStr)
        else -> existing
    }
}

/**
 * 从字符串化的 JSON 参数解析工具输入（增量拼接完成后由工具执行器调用）。
 * 解析失败时返回空对象，执行层据此产出错误输出。
 */
fun UIMessagePart.Tool.parsedInput(): JsonElement = try {
    val raw = (input as? JsonPrimitive)?.takeIf { it.isString }?.content ?: input.toString()
    if (raw.isNullOrBlank()) {
        buildJsonObject {}
    } else {
        Json.parseToJsonElement(raw)
    }
} catch (_: Exception) {
    buildJsonObject { }
}

/** finish reason 常量别名。 */
object FinishReason {
    const val STOP = "stop"
    const val TOOL_CALLS = "tool_calls"
    const val LENGTH = "length"
    const val CONTENT_FILTER = "content_filter"
}

/** 便捷扩展：缓存命中率（token 统计行用）。 */
fun TokenUsage.cacheHitRate(): Float = if (promptTokens <= 0) 0f else cachedTokens.toFloat() / promptTokens

/** 便捷扩展：JsonElement 安全读字符串。 */
fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: runCatching { this?.jsonPrimitive?.content }.getOrNull()

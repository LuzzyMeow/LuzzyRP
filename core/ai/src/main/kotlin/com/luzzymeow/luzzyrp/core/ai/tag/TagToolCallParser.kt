package com.luzzymeow.luzzyrp.core.ai.tag

/**
 * [INVARIANT-AGENTIC] 文本标签工具调用兜底解析器
 *
 * 背景：GLM-5.2 等模型不支持原生 function calling，其工具调用以文本标签形式输出：
 *   <tool_calls>
 *   tool_name:{"query": "鹿溪"}
 *   tool_name2:{"key": 1}
 *   </tool_calls>
 * 或 JSON 数组形态：
 *   <tool_calls>[{"name": "tool_name", "arguments": {"query": "鹿溪"}}]</tool_calls>
 *
 * 流式行为（配合真流式不变性）：
 *   - 开标签之前的正文增量**立即**透传（逐字上屏）；
 *   - 开标签（允许跨 delta 切分）之后的正文进入缓冲，**不再上屏**
 *     （防止工具 JSON 闪现正文气泡）；
 *   - 闭合标签到达后一次性解析出全部工具调用；
 *   - 流意外结束（maxTokens 截断）时对未闭合缓冲做尽力解析。
 *
 * 实现：单一尾部缓冲 + 「完整分隔符命中即切分、尾部保留最长可能前缀」的标准
 * 流式分隔符算法，任意切分粒度（含逐字符）下结果一致。
 */
class TagToolCallParser {

    /** 单个解析出的工具调用。 */
    data class ParsedToolCall(
        val name: String,
        val argumentsJson: String,
    )

    /** 一次 feed 的产物：可透传的正文增量 + 已闭合解析完成的工具调用。 */
    data class ParseResult(
        val visibleText: String,
        val toolCalls: List<ParsedToolCall>,
    )

    private var insideTag = false
    private val buf = StringBuilder()

    /** 喂入一个流式文本增量。 */
    fun feed(delta: String): ParseResult {
        if (delta.isEmpty()) return ParseResult("", emptyList())
        buf.append(delta)

        val visible = StringBuilder()
        val toolCalls = mutableListOf<ParsedToolCall>()

        while (true) {
            if (!insideTag) {
                val openIdx = buf.indexOf(OPEN_TAG)
                if (openIdx >= 0) {
                    visible.append(buf, 0, openIdx)
                    buf.delete(0, openIdx + OPEN_TAG.length)
                    insideTag = true
                    continue
                }
                // 无完整开标签：安全透传 = 缓冲长度 −（可能是开标签前缀的尾部窗口）
                val safe = safeEmitLength(buf)
                if (safe > 0) {
                    visible.append(buf, 0, safe)
                    buf.delete(0, safe)
                }
                break
            } else {
                val closeIdx = buf.indexOf(CLOSE_TAG)
                if (closeIdx >= 0) {
                    toolCalls += parseBody(buf.substring(0, closeIdx))
                    buf.delete(0, closeIdx + CLOSE_TAG.length)
                    insideTag = false
                    continue
                }
                // 标签内：内容不上屏，全部留在缓冲等待闭合
                break
            }
        }
        return ParseResult(visible.toString(), toolCalls)
    }

    /**
     * 流结束时调用：未闭合标签做尽力解析。
     * 残留缓冲若为开标签的部分前缀（流被 maxTokens 截断），直接吞掉不上屏。
     */
    fun finish(): ParseResult {
        val leftover = buf.toString()
        buf.setLength(0)
        return if (insideTag) {
            insideTag = false
            ParseResult("", parseBody(leftover))
        } else {
            // 剥离尾部半截开标签前缀
            val stripped = leftover.dropLast(trailingPrefixLength(leftover))
            ParseResult(stripped, emptyList())
        }
    }

    /** 尾部与 OPEN_TAG 前缀吻合的最大窗口长度。 */
    private fun trailingPrefixLength(text: String): Int {
        val maxWindow = minOf(text.length, OPEN_TAG.length - 1)
        for (window in maxWindow downTo 1) {
            if (text.endsWith(OPEN_TAG.substring(0, window))) return window
        }
        return 0
    }

    // —— 内部实现 ——

    /** 可安全透传的长度：扣除尾部可能是 OPEN_TAG 前缀的窗口。 */
    private fun safeEmitLength(text: CharSequence): Int {
        val maxWindow = minOf(text.length, OPEN_TAG.length - 1)
        for (window in maxWindow downTo 1) {
            val i = text.length - window
            var isPrefix = true
            for (j in 0 until window) {
                if (text[i + j] != OPEN_TAG[j]) {
                    isPrefix = false
                    break
                }
            }
            if (isPrefix) return i
        }
        return text.length
    }

    /**
     * 解析标签体：JSON 数组形态或行式形态（tool_name:json）。
     * 行式解析容错：参数 JSON 跨行时按花括号配对续行合并。
     */
    private fun parseBody(body: String): List<ParsedToolCall> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[")) {
            return runCatching {
                val arr = AiTagJson.parseToJsonElement(trimmed) as? kotlinx.serialization.json.JsonArray
                arr?.mapNotNull { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: return@mapNotNull null
                    val args = obj["arguments"] ?: obj["args"] ?: obj["parameters"]
                    ParsedToolCall(name, (args ?: kotlinx.serialization.json.buildJsonObject { }).toString())
                }.orEmpty()
            }.getOrDefault(emptyList())
        }

        // 行式：tool_name:{json}（JSON 支持跨行花括号配对）
        val calls = mutableListOf<ParsedToolCall>()
        val lines = trimmed.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            i++
            if (line.isEmpty()) continue
            val sep = line.indexOf(':')
            if (sep <= 0) continue
            val name = line.substring(0, sep).trim().trim('"', '`', '\'').trim()
            if (name.isEmpty()) continue
            var args = line.substring(sep + 1).trim()
            // 花括号不配对时向下续行
            while (!balancedBraces(args) && i < lines.size) {
                args += "\n" + lines[i]
                i++
            }
            calls.add(ParsedToolCall(name, normalizeArgs(args)))
        }
        return calls
    }

    private fun balancedBraces(s: String): Boolean {
        var depth = 0
        var inString = false
        var escape = false
        for (c in s) {
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
        }
        return depth <= 0
    }

    /** 参数规整：非法 JSON 退化为 {"_raw": args}；空参数为 {}。 */
    private fun normalizeArgs(args: String): String {
        val trimmed = args.trim()
        if (trimmed.isEmpty()) return "{}"
        return try {
            AiTagJson.parseToJsonElement(trimmed).toString()
        } catch (_: Exception) {
            kotlinx.serialization.json.buildJsonObject {
                put("_raw", kotlinx.serialization.json.JsonPrimitive(trimmed))
            }.toString()
        }
    }

    private companion object {
        const val OPEN_TAG = "<tool_calls>"
        const val CLOSE_TAG = "</tool_calls>"
        val AiTagJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

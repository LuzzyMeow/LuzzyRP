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
 *   - 检测到开标签（允许跨 delta 切分，如 "…<tool_" + "calls>"）后，
 *     后续内容进入 pending 缓冲，**不再上屏**（防止工具 JSON 闪现正文气泡）；
 *   - 闭合标签到达后一次性解析出全部工具调用；
 *   - 流意外结束（maxTokens 截断）时对未闭合缓冲做尽力解析。
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
    private val pending = StringBuilder()

    /** 喂入一个流式文本增量。 */
    fun feed(delta: String): ParseResult {
        if (delta.isEmpty()) return ParseResult("", emptyList())
        val visible = StringBuilder()
        val toolCalls = mutableListOf<ParsedToolCall>()
        var rest = delta

        while (rest.isNotEmpty()) {
            if (!insideTag) {
                val tagStart = findTagStart(rest)
                when {
                    tagStart != null -> {
                        // 标签前正文透传；进入标签模式
                        visible.append(rest, 0, tagStart.index)
                        insideTag = true
                        pending.clear()
                        pending.append(rest.substring(tagStart.index + tagStart.matchedPrefixLength))
                        rest = ""
                        val closed = tryCloseTag()
                        if (closed != null) {
                            toolCalls += closed.first
                            rest = closed.second + rest
                            insideTag = false
                        }
                    }

                    tagStart == null && mightBeTagPrefix(rest) -> {
                        // 尾部可能是被切分的标签前缀：扣留尾部，其余透传
                        visible.append(rest.substring(0, safePassLength(rest)))
                        pending.append(rest.substring(safePassLength(rest)))
                        rest = ""
                    }

                    else -> {
                        visible.append(rest)
                        rest = ""
                    }
                }
            } else {
                // 标签内：继续缓冲
                pending.append(rest)
                rest = ""
                val closed = tryCloseTag()
                if (closed != null) {
                    toolCalls += closed.first
                    rest = closed.second
                    insideTag = false
                }
            }
        }
        return ParseResult(visible.toString(), toolCalls)
    }

    /** 流结束时调用：未闭合标签做尽力解析。 */
    fun finish(): ParseResult {
        if (!insideTag || pending.isBlank()) {
            val leftover = pending.toString()
            pending.clear()
            insideTag = false
            return ParseResult(if (insideTag) "" else leftover, emptyList())
        }
        val calls = parseBody(pending.toString())
        pending.clear()
        insideTag = false
        return ParseResult("", calls)
    }

    // —— 内部实现 ——

    private data class TagHit(val index: Int, val matchedPrefixLength: Int)

    /** 在文本中查找开标签 <tool_calls>（或其部分前缀匹配点）。 */
    private fun findTagStart(text: String): TagHit? {
        val direct = text.indexOf(OPEN_TAG)
        if (direct >= 0) return TagHit(direct, OPEN_TAG.length)
        // 处理跨 delta 切分的开标签
        for (len in (OPEN_TAG.length - 1) downTo 1) {
            if (text.length >= len && text.endsWith(OPEN_TAG.substring(0, len)) && text.length - len >= 0) {
                // 仅当该尾部不是更长普通文本的一部分时才需扣留——endsWith 前缀判断即可
                return TagHit(text.length - len, len)
            }
        }
        return null
    }

    /** rest 尾部是否可能构成开标签前缀（用于决定扣留多少尾部）。 */
    private fun mightBeTagPrefix(text: String): Boolean {
        for (len in (OPEN_TAG.length - 1) downTo 1) {
            if (text.length >= len && text.endsWith(OPEN_TAG.substring(0, len))) {
                return true
            }
        }
        return false
    }

    /** 可安全透传的长度（尾部留出标签前缀探测窗口）。 */
    private fun safePassLength(text: String): Int {
        for (len in (OPEN_TAG.length - 1) downTo 1) {
            if (text.length >= len && text.endsWith(OPEN_TAG.substring(0, len))) {
                return (text.length - len).coerceAtLeast(0)
            }
        }
        return text.length
    }

    /** 尝试在 pending 中找到闭合标签；命中则解析并返回（工具调用, 闭合标签之后的残余文本）。 */
    private fun tryCloseTag(): Pair<List<ParsedToolCall>, String>? {
        val text = pending.toString()
        val closeIndex = text.indexOf(CLOSE_TAG)
        if (closeIndex < 0) return null
        val body = text.substring(0, closeIndex)
        val after = text.substring(closeIndex + CLOSE_TAG.length)
        val calls = parseBody(body)
        return Pair(calls, after)
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
                val arr = AiTagJson.parseToJsonElement(trimmed).let { it as? kotlinx.serialization.json.JsonArray }
                arr?.mapNotNull { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                    val args = obj["arguments"] ?: obj["args"] ?: obj["parameters"] ?: kotlinx.serialization.json.buildJsonObject { }
                    ParsedToolCall(name, args.toString())
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
            val name = line.substring(0, sep).trim().trim('"', '`', '\'')
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

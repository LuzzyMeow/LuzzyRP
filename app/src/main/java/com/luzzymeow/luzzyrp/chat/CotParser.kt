package com.luzzymeow.luzzyrp.chat

/**
 * 上游 CoT 标记解析（`core-utils.js` 的 `parseCot` 的 Kotlin 移植）。
 *
 * ## 为什么需要它（这是一个真实缺陷的根因）
 *
 * **上游把思维链存在正文里**：模型输出的 `<thinking>…</thinking>`（或 `<think>` / `<cot>`）
 * 会**原样并入 message.content**，渲染时再靠 `parseCot()` 把这段剥出来单独展示。
 * 证据在真实迁移数据里：某条 assistant 消息 `content` 1847 字符、其中整段是
 * `<thinking>[情景意图分析]…</thinking>`，而 `reasoning` 字段是空的。
 *
 * 我们的 Compose 侧当初只认「独立字段」（自己生成时从 SSE 的 `reasoning_content` 拿到，
 * 存进 `reasoning` 列），于是迁移进来的消息出现两个症状：
 * 1. **思维链被当正文渲染**（用户看到满屏「[情景意图分析]…」）；
 * 2. **思考节点是空的**（本该折叠收起的内容跑到了正文里）。
 *
 * ## 移植时保留的关键行为（都不许简化）
 *
 * - **成对栈 + 嵌套**：`<thinking><cot>…</cot></thinking>` 要整体算 CoT；
 *   只有栈空时才回到正文；
 * - **代码围栏/行内码/HTML 块/注释里的标签不算 CoT**：模型经常在正文里贴一段含 `<thinking>`
 *   的示例代码，那段必须留在正文（否则正文被吃掉一半）；
 * - **流式期的半截标签不展示**：末尾的 `</thi` 这类残片先剔除，等下一段到达后重新解析整串；
 * - **未闭合的 CoT 延伸到文末**（`isFinished = false`）——流式未完时就是这样。
 */
object CotParser {

    /** 解析结果。[main] 是去掉了 CoT 的正文，[cot] 是思维链原文。 */
    data class Parsed(
        val main: String,
        val cot: String,
        /** 出现过 CoT 标记（即使内容为空）。 */
        val hadCot: Boolean,
        /** CoT 已闭合（流式结束或本来就有闭合标签）。 */
        val isFinished: Boolean,
    ) {
        companion object {
            val EMPTY = Parsed("", "", false, false)
        }
    }

    /**
     * 分词器（与上游同一条正则、同样的分组含义）：
     * - 组 1：整块跳过的标签名（`html/script/style/ui_template_updates`）→ 整块算普通文本；
     * - 组 2：`/` 表示闭合标签；
     * - 组 3：CoT 标签名（`thinking/think/cot`，大小写不敏感）。
     *
     * 顺序有意义：成对结构（`<!--…-->`、``` ```…``` ```、`` `…` ``）必须排在裸标签之前，
     * 否则代码块里的 `<thinking>` 会被当成真标记。
     */
    private val TOKENS = Regex(
        """<(html|script|style|ui_template_updates)\b[^>]*>[\s\S]*?(?:</\1\s*>|${'$'})""" +
            """|<!--[\s\S]*?(?:-->|${'$'})""" +
            """|```[\s\S]*?(?:```|${'$'})""" +
            """|`[^`\r\n]*`""" +
            """|<\s*(/?)\s*(thinking|think|cot)\s*>""" +
            """|</?[a-zA-Z][\w:-]*(?:[^"'<>]|"[^"]*"|'[^']*')*>""",
        RegexOption.IGNORE_CASE,
    )

    /** 末尾的半截标签（流式拆开）：`<`、`</thi`、`<thinking` 等，暂不展示。 */
    private val TRAILING_PARTIAL_TAG = Regex(
        """<\s*/?\s*(?:t|th|thi|thin|think|thinki|thinkin|thinking|c|co|cot)?\s*${'$'}""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 有界解析缓存（对应上游的 `parseCotCache`，同样有上限）。
     *
     * 为什么必须有：解析结果在**同一段文本**上会被问很多次——正文渲染、字数统计、
     * 会话总览预览、以及**重组期间的重复取值**。没有缓存就是每问一次把 1~2KB 文本重新正则扫一遍。
     * 上限 64 条：一次只看得见屏上几条消息，多出来的命中率极低，不占内存。
     */
    private const val CACHE_LIMIT = 64
    private val cache = object : LinkedHashMap<String, Parsed>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Parsed>?): Boolean =
            size > CACHE_LIMIT
    }

    fun parse(text: String): Parsed {
        if (text.isEmpty()) return Parsed.EMPTY
        synchronized(cache) { cache[text]?.let { return it } }
        val parsed = parseUncached(text)
        synchronized(cache) { cache[text] = parsed }
        return parsed
    }

    private fun parseUncached(text: String): Parsed {

        val openTags = ArrayDeque<String>()
        val cot = StringBuilder()
        val main = StringBuilder()
        var cursor = 0
        var hadCot = false

        fun append(part: CharSequence) {
            if (part.isEmpty()) return
            if (openTags.isNotEmpty()) cot.append(part) else main.append(part)
        }

        for (match in TOKENS.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val cotTag = match.groupValues[3]
            if (cotTag.isEmpty()) {
                // 成对结构 / 普通标签 / 注释：原样留在当前桶里
                append(match.value)
            } else {
                val closing = match.groupValues[2] == "/"
                val tag = cotTag.lowercase()
                if (!closing) {
                    hadCot = true
                    if (openTags.isEmpty()) cot.append("") // 起块；不写入标记本身
                    if (!openTags.contains(tag)) openTags.addLast(tag)
                    if (cot.isNotEmpty()) cot.append("\n")
                } else if (openTags.lastOrNull() == tag) {
                    openTags.removeLast()
                }
            }
            cursor = match.range.last + 1
        }
        // 尾部残片（可能是半截标签）先剔除，下一段到达后会重新解析整串
        append(TRAILING_PARTIAL_TAG.replace(text.substring(cursor), ""))

        return Parsed(
            main = main.toString().trim(),
            cot = cot.toString().trim(),
            hadCot = hadCot,
            isFinished = hadCot && openTags.isEmpty(),
        )
    }

    /** 只要正文（会话总览的预览、字数统计用）。 */
    fun mainOf(text: String): String = parse(text).main

    /**
     * 把（原文有 CoT 的）消息按「新正文 + 原思维链」重新拼回去。
     *
     * 用在**编辑**上：用户改的是正文，思维链是那次生成的历史、不该因为改字就丢掉。
     * 原文没有 CoT 时原样返回新正文（不做任何包装）。
     */
    fun rewrap(originalText: String, newMain: String): String {
        val parsed = parse(originalText)
        if (!parsed.hadCot || parsed.cot.isEmpty()) return newMain
        return "$newMain\n\n<thinking>\n${parsed.cot}\n</thinking>"
    }
}

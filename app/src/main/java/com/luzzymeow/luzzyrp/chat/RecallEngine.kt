package com.luzzymeow.luzzyrp.chat

/**
 * 会话记忆检索（真实本地实现）。
 *
 * 语义对应 WebView 版「向量召回」（patch 016/031 的记忆召回块）：生成前检索历史轮次，
 * 命中则以 `<memory_recall>` 注入 system prompt，并在思考时间线上留一个真实节点。
 *
 * 与向量召回的区别（如实记录）：本实现是**词面重叠**打分（CJK 二元组 + 拉丁词），
 * 不需要嵌入模型、可离线、可单测；P4 数据层建成后换真实嵌入检索时，本类替换实现即可。
 */
object RecallEngine {

    /** 一条命中（回填给思考节点展示的真实数据）。 */
    data class Hit(
        /** 轮次（从 1 开始的用户轮序号）。 */
        val turn: Int,
        /** 相关度 0~1（词面覆盖率）。 */
        val score: Double,
        /** 命中片段（截断后的可读文本）。 */
        val text: String,
    )

    /** 检索：返回相关度降序、不超过 [topK] 条、且不低于 [minScore] 的命中。 */
    fun search(
        history: List<Pair<Int, String>>,
        query: String,
        topK: Int = 2,
        minScore: Double = 0.08,
        snippetLength: Int = 48,
    ): List<Hit> {
        val q = tokens(query)
        if (q.isEmpty()) return emptyList()
        return history
            .mapNotNull { (turn, text) ->
                val d = tokens(text)
                if (d.isEmpty()) return@mapNotNull null
                val overlap = q.count { it in d }
                val score = overlap.toDouble() / q.size
                if (score < minScore) null
                else Hit(turn = turn, score = score, text = snippet(text, snippetLength))
            }
            .sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.turn })
            .take(topK)
    }

    /** 命中集合 → 注入 system 的召回块（空命中返回空串）。 */
    fun renderForPrompt(hits: List<Hit>): String {
        if (hits.isEmpty()) return ""
        val body = hits.joinToString("\n") { "- （第 ${it.turn} 轮，相关度 ${percent(it.score)}）${it.text}" }
        return "<memory_recall>\n$body\n</memory_recall>"
    }

    /** 相关度区间文本（思考节点右上角展示，如 `0.87~0.91`）。 */
    fun rangeLabel(hits: List<Hit>): String {
        if (hits.isEmpty()) return ""
        val lo = hits.minOf { it.score }
        val hi = hits.maxOf { it.score }
        return "%.2f~%.2f".format(lo, hi)
    }

    fun percent(score: Double): String = "${(score * 100).toInt()}%"

    /**
     * 分词：CJK 连续段取二元组（单字段落补一元），拉丁/数字取整词小写。
     * 过滤高频虚词，避免「的/了/是」之类造成假命中。
     */
    fun tokens(text: String): Set<String> {
        val out = mutableSetOf<String>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isEmpty()) return
            val s = buffer.toString()
            buffer.clear()
            if (s.length == 1) {
                if (s !in CJK_STOP) out += s
            } else {
                for (i in 0 until s.length - 1) {
                    val bigram = s.substring(i, i + 2)
                    if (bigram !in CJK_STOP) out += bigram
                }
            }
        }
        // CJK 段与拉丁段不能混在一个 buffer 里（否则会跨边界产出「果a」这类噪声二元组）
        var mode = 0
        for (c in text) {
            val m = when {
                isCjk(c) -> 1
                c.isLetterOrDigit() && c.code < 128 -> 2
                else -> 0
            }
            if (m == 0) {
                flush(); mode = 0; continue
            }
            if (m != mode) {
                flush(); mode = m
            }
            buffer.append(if (m == 2) c.lowercaseChar() else c)
        }
        flush()
        return out
    }

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF

    private fun snippet(text: String, length: Int): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= length) flat else flat.take(length) + "…"
    }

    /** 停用词（两字及以上，避免与二元组撞车；单字另列）。 */
    private val CJK_STOP: Set<String> = setOf(
        "的", "了", "是", "在", "我", "你", "他", "她", "它", "们", "个", "和", "就", "都",
        "不", "有", "也", "很", "还", "把", "被", "给", "对", "从", "到", "这", "那", "什么",
        "怎么", "这个", "那个", "一下", "一个", "因为", "所以", "但是", "如果", "而且", "然后",
    )
}

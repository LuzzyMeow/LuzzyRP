package com.luzzymeow.luzzyrp.assistant.data.db.dao

/**
 * CJK bigram 分词器（纯 Kotlin，可单测）。
 *
 * 背景：SQLite FTS4 的 unicode61 分词器把一整段中文当成**一个 token**（实测：
 * 「请帮我整理 2024 年度报告」中 "报告*" 命中 0 次），中文检索几乎不可用。
 * 方案（派发者 2026-09-09 拍板）：message_fts 增加 search_text 列 =
 * **原文 + 空格分隔的 bigram/小写拉丁 token**，检索侧同样展开 bigram，用 AND 串联。
 *
 * 规则：
 * - CJK 连续段（汉字 / 假名 / 谚文）→ 相邻两字 bigram；单字段则保留该字本身；
 * - 拉丁字母 / 数字连续段 → 原样转小写（FTS4 unicode61 本身大小写不敏感，转小写只为对齐）；
 * - 其它字符（标点 / 空格 / emoji / 符号）→ 分段符，不参与 token；
 * - 按 code point 迭代，emoji / 代理对 / 扩展 B 汉字不会崩、也不会被拆成半个字符；
 * - 单字中文查询在索引里没有对应 token（bigram 只覆盖相邻两字），由 MessageFtsDao.searchFallback
 *   的 LIKE 回落兜底——这是「bigram 为主 + LIKE 兜底」方案的组成部分。
 */
object CjkBigram {

    /** 单条消息最多写入多少 token（防超长正文把 FTS 索引撑爆；原文始终完整保留）。 */
    const val MAX_TOKENS: Int = 4096

    /**
     * 写入 message_fts.search_text 的值：原文 + 空格 + token 串。
     * 纯函数、幂等（同一输入永远得到同一输出），重建索引不会产生漂移。
     */
    fun searchText(text: String): String {
        val tokens = tokenize(text)
        return if (tokens.isEmpty()) text else text + " " + tokens
    }

    /**
     * 生成 token 串（空格分隔）：CJK 段 → bigram（单字段 → 该字），拉丁/数字段 → 小写。
     */
    fun tokenize(text: String): String {
        if (text.isEmpty()) return ""
        val out = ArrayList<String>(16)
        val run = ArrayList<Int>(8)
        var runIsCjk = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            when {
                isCjkCodePoint(codePoint) -> {
                    if (!runIsCjk) {
                        appendLatinRun(out, run)
                        run.clear()
                        runIsCjk = true
                    }
                    run.add(codePoint)
                }
                isTokenCodePoint(codePoint) -> {
                    if (runIsCjk) {
                        appendCjkRun(out, run)
                        run.clear()
                        runIsCjk = false
                    }
                    run.add(codePoint)
                }
                else -> {
                    if (runIsCjk) appendCjkRun(out, run) else appendLatinRun(out, run)
                    run.clear()
                    runIsCjk = false
                }
            }
        }
        if (runIsCjk) appendCjkRun(out, run) else appendLatinRun(out, run)
        return out.joinToString(separator = " ")
    }

    /** 是否含 CJK 字符（查询侧据此决定加不加前缀通配 *）。 */
    fun containsCjk(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isCjkCodePoint(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    /** CJK：汉字（含扩展区 / 兼容区）+ 假名 + 谚文。 */
    fun isCjkCodePoint(codePoint: Int): Boolean = when (codePoint) {
        in 0x4E00..0x9FFF -> true
        in 0x3400..0x4DBF -> true
        in 0xF900..0xFAFF -> true
        in 0x20000..0x2FA1F -> true
        in 0x3040..0x30FF -> true
        in 0x31F0..0x31FF -> true
        in 0xAC00..0xD7AF -> true
        in 0x1100..0x11FF -> true
        in 0x3130..0x318F -> true
        else -> false
    }

    /** FTS4 unicode61 的 token 字符 = Unicode 字母 / 数字（下划线、撇号均为分隔符）。 */
    private fun isTokenCodePoint(codePoint: Int): Boolean = Character.isLetterOrDigit(codePoint)

    private fun appendCjkRun(out: MutableList<String>, run: List<Int>) {
        if (run.isEmpty()) return
        if (run.size == 1) {
            addToken(out, String(Character.toChars(run[0])))
            return
        }
        for (i in 0 until run.size - 1) {
            val pair = String(Character.toChars(run[i])) + String(Character.toChars(run[i + 1]))
            addToken(out, pair)
        }
    }

    private fun appendLatinRun(out: MutableList<String>, run: List<Int>) {
        if (run.isEmpty()) return
        val token = buildString(run.size) { for (codePoint in run) appendCodePoint(codePoint) }
        addToken(out, token.lowercase())
    }

    private fun addToken(out: MutableList<String>, token: String) {
        if (token.isEmpty() || out.size >= MAX_TOKENS) return
        out.add(token)
    }
}

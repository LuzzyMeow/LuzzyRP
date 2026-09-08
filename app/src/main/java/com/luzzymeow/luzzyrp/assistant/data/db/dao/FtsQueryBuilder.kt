package com.luzzymeow.luzzyrp.assistant.data.db.dao

/**
 * FTS4 MATCH 表达式构造器（纯 Kotlin，可单测）。
 *
 * 用户输入不能直接进 MATCH：引号、*、^、-、AND/OR/NOT/NEAR 都会改变语法甚至抛 SQLException。
 *
 * 做法：用与索引侧**同一个** CjkBigram.tokenize 切分（保证查询词与索引 token 完全对齐），
 * - CJK token（已是 bigram / 单字）→ 原样作为精确 token；
 * - 拉丁/数字 token → 小写 + 前缀通配 token*；
 * - 各 token 用 AND 串联（FTS4 的 AND 运算符，大写），最多 [MAX_MATCH_TERMS] 项；
 * - 无有效 token → 返回 null，调用方应跳过 FTS 并走 LIKE 回落。
 *
 * 例：toMatchExpression("年度报告") -> "年度 AND 度报 AND 报告"
 *     toMatchExpression("报告 2024") -> "报告 AND 2024*"
 *     toMatchExpression("   ")        -> null
 */
object FtsQueryBuilder {

    private const val MAX_MATCH_TERMS: Int = 64

    fun toMatchExpression(rawQuery: String): String? {
        if (rawQuery.isBlank()) return null
        val tokens = CjkBigram.tokenize(rawQuery).split(' ').filter { token -> token.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val terms = LinkedHashSet<String>(tokens.size)
        for (token in tokens) {
            if (terms.size >= MAX_MATCH_TERMS) break
            terms.add(if (CjkBigram.containsCjk(token)) token else token + "*")
        }
        if (terms.isEmpty()) return null
        return terms.joinToString(separator = " AND ")
    }
}

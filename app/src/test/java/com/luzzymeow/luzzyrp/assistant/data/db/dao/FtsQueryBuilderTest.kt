package com.luzzymeow.luzzyrp.assistant.data.db.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FtsQueryBuilder 单测：查询侧必须与索引侧（CjkBigram）用同一套切分。
 * 中文 → bigram 精确 token + AND 串联；拉丁/数字 → 小写 + 前缀通配。
 */
class FtsQueryBuilderTest {

    @Test
    fun blankOrSymbolOnlyInputReturnsNull() {
        assertNull(FtsQueryBuilder.toMatchExpression(""))
        assertNull(FtsQueryBuilder.toMatchExpression("   "))
        assertNull(FtsQueryBuilder.toMatchExpression("!!! -- **"))
        assertNull(FtsQueryBuilder.toMatchExpression("\"\"\""))
    }

    @Test
    fun chineseExpandsToBigramsJoinedByAnd() {
        assertEquals("年度 AND 度报 AND 报告", FtsQueryBuilder.toMatchExpression("年度报告"))
        assertEquals("报告", FtsQueryBuilder.toMatchExpression("报告"))
        assertEquals("中", FtsQueryBuilder.toMatchExpression("中"))
    }

    @Test
    fun latinAndDigitsKeepPrefixWildcard() {
        assertEquals("report*", FtsQueryBuilder.toMatchExpression("Report"))
        assertEquals("report* AND 2024*", FtsQueryBuilder.toMatchExpression("Report 2024"))
    }

    @Test
    fun mixedChineseAndLatin() {
        assertEquals("报告 AND 2024*", FtsQueryBuilder.toMatchExpression("报告 2024"))
        assertEquals("报告 AND v2*", FtsQueryBuilder.toMatchExpression("报告 V2"))
        assertEquals("请帮 AND 帮我 AND 我整 AND 整理 AND 2024*", FtsQueryBuilder.toMatchExpression("请帮我整理 2024"))
    }

    @Test
    fun duplicateTermsAreDeduplicated() {
        assertEquals("报告", FtsQueryBuilder.toMatchExpression("报告 报告"))
        assertEquals("report*", FtsQueryBuilder.toMatchExpression("Report report"))
    }

    @Test
    fun ftsOperatorsBecomePlainLowercaseWords() {
        // 小写化后 AND / OR 不再是 FTS 运算符，作为普通词检索，不会造成语法注入
        assertEquals("and* AND or*", FtsQueryBuilder.toMatchExpression("AND OR"))
        assertEquals("not*", FtsQueryBuilder.toMatchExpression("NOT"))
    }

    @Test
    fun termCountIsCapped() {
        val raw = (1..200).joinToString(" ") { index -> "t" + index }
        val expression = FtsQueryBuilder.toMatchExpression(raw)
        assertEquals(64, expression!!.split(" AND ").size)
    }

    /** 关键性质：查询词必须出现在索引 token 里，否则 bigram 方案不成立。 */
    @Test
    fun queryTermsExistInIndexTokens() {
        val content = "请帮我整理 2024 年度报告"
        val indexTokens = CjkBigram.searchText(content).split(' ').toSet()
        val terms = FtsQueryBuilder.toMatchExpression("年度报告")!!.split(" AND ")
        assertTrue(terms.isNotEmpty())
        for (term in terms) {
            assertTrue("索引 token 应包含查询词: " + term, indexTokens.contains(term))
        }
    }
}

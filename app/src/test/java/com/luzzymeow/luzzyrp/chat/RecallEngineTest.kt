package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 会话记忆检索（真实本地实现）的确定性单测。 */
class RecallEngineTest {

    @Test
    fun `分词按 CJK 二元组切分且不跨拉丁边界产出噪声`() {
        val t = RecallEngine.tokens("苹果abc")
        assertTrue("苹果" in t)
        assertTrue("ab" in t)
        assertTrue("bc" in t)
        // 跨边界二元组（果a / 果ab）不得出现
        assertFalse(t.contains("果a"))
        assertFalse(t.contains("果ab"))
    }

    @Test
    fun `单字与停用词被过滤，双字以上实词保留`() {
        val t = RecallEngine.tokens("钟楼的苹果")
        assertTrue("钟楼" in t)
        assertTrue("苹果" in t)
        assertFalse("单字 token 不应出现", t.any { it.length == 1 })
    }

    @Test
    fun `检索命中相关轮次且排序在前`() {
        val history = listOf(
            1 to "钟楼顶上长着一整树的红苹果，只有恶魔找得到。",
            2 to "今天早上天气不错，街上人很多。",
        )
        val hits = RecallEngine.search(history, "苹果树在哪里？")
        assertEquals(1, hits.size)
        assertEquals(1, hits.first().turn)
        assertTrue(hits.first().score > 0.0)
    }

    @Test
    fun `低于阈值不命中，空查询无命中`() {
        val history = listOf(1 to "钟楼顶上的红苹果树")
        assertTrue(RecallEngine.search(history, "完全不相干的内容啊").isEmpty())
        assertTrue(RecallEngine.search(history, "").isEmpty())
    }

    @Test
    fun `topK 限制返回条数`() {
        val history = listOf(
            1 to "苹果苹果苹果",
            2 to "苹果苹果苹果",
            3 to "苹果苹果苹果",
        )
        assertEquals(2, RecallEngine.search(history, "苹果", topK = 2).size)
    }

    @Test
    fun `相关度区间标签与召回块渲染`() {
        val hits = RecallEngine.search(listOf(1 to "钟楼红苹果树"), "钟楼苹果")
        assertTrue(hits.isNotEmpty())
        val range = RecallEngine.rangeLabel(hits)
        assertTrue(range.matches(Regex("""\d\.\d\d~\d\.\d\d""")))
        val block = RecallEngine.renderForPrompt(hits)
        assertTrue(block.startsWith("<memory_recall>"))
        assertTrue(block.contains("第 1 轮"))
        // 空命中不产出空块（避免把空标签注入 system）
        assertEquals("", RecallEngine.renderForPrompt(emptyList()))
    }
}

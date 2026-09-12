package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆化缓存单测 —— 它是「滚动回看时气泡突然弹出」修复的承重件：
 * 静态消息必须能在**首次组合那一帧**就拿到解析结果，且不因反复滚动重复付费。
 */
class MarkdownMemoTest {

    @Test
    fun `同一内容返回同一实例（缓存命中，避免重复解析）`() {
        MarkdownMemo.clear()
        val text = "他蹲在光斑里，*尾巴翘起*。"
        val first = MarkdownMemo.blocksOf(text)
        val second = MarkdownMemo.blocksOf(text)
        assertSame("同内容必须命中缓存（同一实例）", first, second)
        assertEquals(1, MarkdownMemo.size())
    }

    @Test
    fun `不同内容分别缓存且结果与直接解析一致`() {
        MarkdownMemo.clear()
        val a = MarkdownMemo.blocksOf("第一段")
        val b = MarkdownMemo.blocksOf("第二段")
        assertEquals(MarkdownParser.parse("第一段"), a)
        assertEquals(MarkdownParser.parse("第二段"), b)
        assertEquals(2, MarkdownMemo.size())
    }

    @Test
    fun `容量有界：超出上限后旧条目被淘汰`() {
        MarkdownMemo.clear()
        repeat(40) { MarkdownMemo.blocksOf("第 $it 段内容") }
        assertTrue("缓存必须受容量约束（实际 ${MarkdownMemo.size()}）", MarkdownMemo.size() <= 24)
    }

    @Test
    fun `空串与纯文本等边界结果一致`() {
        MarkdownMemo.clear()
        assertEquals(MarkdownParser.parse(""), MarkdownMemo.blocksOf(""))
        assertEquals(MarkdownParser.parse("   "), MarkdownMemo.blocksOf("   "))
    }
}

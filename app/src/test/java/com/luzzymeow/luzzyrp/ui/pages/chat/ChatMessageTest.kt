package com.luzzymeow.luzzyrp.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 消息模型的多结果状态机单测（「重新生成=累积候选」的纯逻辑，UI 只负责调它）。
 *
 * 纪律：候选必须是**多次真实生成的产出**，这里钉住「追加并切到新结果」「越界切换被忽略」
 * 「就地编辑只动当前候选」三条不变式。
 */
class ChatMessageTest {

    private fun ai(vararg results: String) = ChatMessage.Ai(
        results = results.map { AiResult(raw = it) },
    )

    @Test
    fun `追加候选后自动切到新结果，序号与数量同步`() {
        val first = ai("第一版")
        assertEquals(1, first.resultCount)
        assertEquals(0, first.index)

        val second = first.withResult(AiResult("第二版"))
        assertEquals(2, second.resultCount)
        assertEquals(1, second.index)
        assertEquals("第二版", second.raw)
        // 原候选仍在（可切回去看）
        assertEquals("第一版", second.selectResult(0).raw)
    }

    @Test
    fun `越界切换被忽略而不是崩溃`() {
        val message = ai("唯一一版")
        assertSame(message, message.selectResult(3))
        assertSame(message, message.selectResult(-1))
    }

    @Test
    fun `直接构造多候选时默认展示第一个，显式指定下标才切`() {
        val message = ai("第一版", "第二版")
        assertEquals(0, message.index)
        assertEquals("第一版", message.raw)
        assertEquals("第二版", message.selectResult(1).raw)
    }

    @Test
    fun `就地编辑只改当前候选，其它候选不受影响`() {
        // 走真实路径：追加候选后下标指向新结果
        val second = ai("第一版").withResult(AiResult("第二版"))
        assertEquals(1, second.index)
        val edited = second.editCurrent("改过的第二版")
        assertEquals("改过的第二版", edited.raw)
        assertEquals("第一版", edited.selectResult(0).raw)
        assertEquals(2, edited.resultCount)
    }

    @Test
    fun `空候选列表不抛异常（防御空消息）`() {
        val empty = ChatMessage.Ai(results = emptyList())
        assertEquals("", empty.raw)
        assertEquals(0, empty.resultCount)
        assertEquals("x", empty.editCurrent("x").raw)
    }

    @Test
    fun `用户消息可就地改写`() {
        assertEquals("改后", ChatMessage.User("原话").edited("改后").text)
    }
}

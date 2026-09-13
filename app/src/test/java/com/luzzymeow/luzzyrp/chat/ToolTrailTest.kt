package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具轨迹的**展开**与**配对修复**（B3）——纯函数，JVM 单测。
 *
 * 这里测的是两件在真实故障里才会暴露的事：
 * 1. **展开顺序**：落库时「正文 + 轨迹」合成一行，展开必须还原成真实发生顺序
 *    （先 `assistant(tool_calls)` → `tool(结果)` → 再 `assistant(正文)`）；
 * 2. **悬空配对**：进程在工具执行中途被杀时，存储里会留下「有调用没结果」，
 *    复位后必须补上「结果未知」，而且**不能**补成「执行失败」——那会诱导模型放心重试
 *    一个可能已经产生副作用的调用。
 */
class ToolTrailTest {

    private fun step(name: String, result: String? = "ok") = ToolStep(name, """{"q":"x"}""", result)

    // ---------------------------------------------------------------- 展开

    @Test
    fun `没有轨迹时展开成一条普通的 assistant 正文`() {
        val expanded = ToolTrail.expand(messageIndex = 3, text = "他说了句话", trail = emptyList())
        assertEquals(1, expanded.size)
        assertEquals(LlmRole.ASSISTANT, expanded[0].role)
        assertEquals("他说了句话", expanded[0].content)
    }

    @Test
    fun `有轨迹时按真实发生顺序展开：调用 → 结果 → 正文`() {
        val expanded = ToolTrail.expand(
            messageIndex = 1,
            text = "查完了，钟楼上确实有苹果树。",
            trail = listOf(step("world_info_lookup", "两条设定")),
        )

        assertEquals(3, expanded.size)
        assertEquals("第一条是 assistant(tool_calls)", LlmRole.ASSISTANT, expanded[0].role)
        assertEquals(1, expanded[0].toolCalls.size)
        assertEquals("world_info_lookup", expanded[0].toolCalls[0].name)
        assertEquals("第二条是 tool 结果", LlmRole.TOOL, expanded[1].role)
        assertEquals("两条设定", expanded[1].content)
        assertEquals("第三条才是正文（正文发生在工具之后）", LlmRole.ASSISTANT, expanded[2].role)
        assertTrue(expanded[2].content.startsWith("查完了"))
    }

    @Test
    fun `配对 id 在 assistant 与 tool 两侧一致（否则供应商直接报协议错误）`() {
        val expanded = ToolTrail.expand(2, "正文", listOf(step("a"), step("b")))
        val callIds = expanded[0].toolCalls.map { it.id }
        val resultIds = expanded.filter { it.role == LlmRole.TOOL }.map { it.toolCallId }
        assertEquals("两条调用两条结果", 2, resultIds.size)
        assertEquals("id 必须一一对应", callIds, resultIds)
    }

    @Test
    fun `悬空的那条展开时补「结果未知」，不留空`() {
        val expanded = ToolTrail.expand(0, "正文", listOf(step("a", result = null)))
        val tool = expanded.single { it.role == LlmRole.TOOL }
        assertEquals("模型永远不该看到「有调用没结果」", ToolPairing.UNKNOWN, tool.content)
        assertTrue("文案要说清是「未知」而不是「失败」", tool.content.contains("unknown"))
        assertTrue("并提示只有只读/幂等才可重试", tool.content.contains("read-only"))
    }

    @Test
    fun `展开是决定性的——同一份历史两次展开逐字节相同`() {
        // 这是批 A 的前缀纯追加性质能在批 B 之后继续成立的前提：
        // 展开里若掺进时间戳 / 随机 id / 迭代顺序，每一轮的历史字节都会变 → 缓存全废。
        val trail = listOf(step("a"), step("b"), step("c", result = null))
        val first = ToolTrail.expand(5, "正文", trail).map { it.toOpenAiJson().toString() }
        val second = ToolTrail.expand(5, "正文", trail).map { it.toOpenAiJson().toString() }
        assertEquals(first, second)
    }

    @Test
    fun `不同消息的合成 id 不冲突`() {
        val a = ToolTrail.expand(0, "甲", listOf(step("t")))[0].toolCalls[0].id
        val b = ToolTrail.expand(1, "乙", listOf(step("t")))[0].toolCalls[0].id
        assertTrue("同一段历史里两条消息的调用 id 必须能区分", a != b)
    }

    @Test
    fun `hasDangling 只认没结果的那些`() {
        assertFalse(ToolTrail.hasDangling(listOf(step("a"), step("b"))))
        assertTrue(ToolTrail.hasDangling(listOf(step("a"), step("b", result = null))))
        assertFalse("空轨迹不算悬空", ToolTrail.hasDangling(emptyList()))
    }

    // ---------------------------------------------------------------- 修复点就在展开处

    @Test
    fun `每条悬空调用都会被补结果——扫描式修复由展开承担`() {
        // 说明：本仓库的存储把「一次生成的正文 + 工具轨迹」放在**同一行**，
        // 所以「有调用没结果」= 轨迹里 result == null，永远进不了请求。
        // 修复因此发生在**展开那一刻**（比「启动时扫日志」更早、也更难绕过），
        // 不需要额外的 wire 层扫描器。
        val expanded = ToolTrail.expand(
            messageIndex = 0,
            text = "正文",
            trail = listOf(step("a"), step("b", result = null), step("c"), step("d", result = null)),
        )
        val tools = expanded.filter { it.role == LlmRole.TOOL }
        assertEquals("每个调用都要有结果", 4, tools.size)
        assertEquals(
            "只补悬空的那两条",
            listOf("ok", ToolPairing.UNKNOWN, "ok", ToolPairing.UNKNOWN),
            tools.map { it.content },
        )
    }

    @Test
    fun `空历史与普通历史不受影响`() {
        assertTrue(ToolPairing.abortTimes(emptyList()).isEmpty())
    }
}

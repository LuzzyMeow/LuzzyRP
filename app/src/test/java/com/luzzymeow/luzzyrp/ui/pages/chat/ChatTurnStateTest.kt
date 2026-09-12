package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.ChatEngine
import com.luzzymeow.luzzyrp.chat.RecallEngine
import com.luzzymeow.luzzyrp.chat.UsageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一次真实生成的状态机（[LiveTurn.apply]）单测。
 *
 * 这是 Stage 0 补的第一块缺口：此前 `LiveTurn` **零覆盖**，而它正是「引擎事件 → 界面」
 * 的唯一状态入口 —— 逐字纪律、节点时序、用量落点全在这里，回归风险最高。
 */
class ChatTurnStateTest {

    private fun recallEvent(scores: List<Double> = listOf(0.9, 0.8)) = ChatEngine.Event.Recall(
        hits = scores.mapIndexed { i, s ->
            RecallEngine.Hit(turn = i + 1, score = s, text = "第${i + 1}轮片段")
        },
        range = "0.80~0.90",
    )

    @Test
    fun `逐字追加不失序也不丢字`() {
        val turn = LiveTurn()
        listOf("你", "好", "呀").forEach { turn.apply(ChatEngine.Event.Content(it)) }
        assertEquals("你好呀", turn.body)
    }

    @Test
    fun `思考内容逐片累加，正文首片到达即标记思考结束（节点自动收起的前提）`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.Reasoning("先想"))
        turn.apply(ChatEngine.Event.Reasoning("一下"))
        assertEquals("先想一下", turn.reasoning)
        assertFalse("思考流未结束时不应标记完成", turn.reasoningDone)

        turn.apply(ChatEngine.Event.Content("正"))
        assertTrue(turn.reasoningDone)
        assertEquals("正", turn.body)
    }

    @Test
    fun `工具参数按分片累积到同一槽位，结果到达后标记完成`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.ToolCallStarted("world_info_lookup"))
        turn.apply(ChatEngine.Event.ToolCallArgs("""{"keywords":"""))
        turn.apply(ChatEngine.Event.ToolCallArgs("""["钟楼"]}"""))
        assertEquals(1, turn.tools.size)
        assertEquals("""{"keywords":["钟楼"]}""", turn.tools.single().args)
        assertNull(turn.tools.single().result)

        turn.apply(ChatEngine.Event.ToolCallFinished("world_info_lookup", """{"keywords":["钟楼"]}""", """{"entries":1}"""))
        assertEquals("""{"entries":1}""", turn.tools.single().result)
        assertTrue(turn.nodes.filterIsInstance<ThinkNode.Tool>().single().done)
    }

    @Test
    fun `节点顺序恒为召回 → 工具 → 思考`() {
        val turn = LiveTurn()
        turn.apply(recallEvent())
        turn.apply(ChatEngine.Event.ToolCallStarted("t"))
        turn.apply(ChatEngine.Event.ToolCallArgs("{}"))
        turn.apply(ChatEngine.Event.ToolCallFinished("t", "{}", "{}"))
        turn.apply(ChatEngine.Event.Reasoning("想"))
        assertEquals(
            listOf("MemoryRecall", "Tool", "Brainstorm"),
            turn.nodes.map { it::class.simpleName },
        )
    }

    @Test
    fun `activeNode 时序：召回→工具→思考→正文时全部收起`() {
        val turn = LiveTurn()
        assertEquals(-1, turn.activeNode)

        turn.apply(recallEvent())
        assertEquals("召回节点应自动展开", 0, turn.activeNode)

        turn.apply(ChatEngine.Event.ToolCallStarted("t"))
        assertEquals("工具节点接管", 1, turn.activeNode)

        turn.apply(ChatEngine.Event.Reasoning("想"))
        assertEquals("思考节点接管（最后一个）", 2, turn.activeNode)

        turn.apply(ChatEngine.Event.Content("正"))
        assertEquals("正文开始 → 全部自动收起", -1, turn.activeNode)
    }

    @Test
    fun `无召回时工具节点下标从 0 起（下标计算不依赖召回是否存在）`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.ToolCallStarted("t"))
        assertEquals(0, turn.activeNode)
    }

    @Test
    fun `用量与耗时在收尾时落到状态上（脚注数据源）`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.Content("x"))
        turn.apply(ChatEngine.Event.Usage(UsageInfo(input = 10, output = 20, cached = 8)))
        turn.apply(ChatEngine.Event.Finished("stop"))

        assertEquals(UsageInfo(10, 20, 8), turn.usage)
        assertNotNull("收尾必须打点耗时", turn.elapsedMs)
        assertTrue(turn.elapsedMs!! >= 0)
        assertFalse(turn.generating)
        assertEquals("stop", turn.finishReason)
    }

    @Test
    fun `失败态：记录原因、停止生成、节点全收起，已到达的正文保留`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.Content("半句"))
        turn.apply(ChatEngine.Event.Failed("HTTP 401"))

        assertEquals("HTTP 401", turn.error)
        assertFalse(turn.generating)
        assertEquals(-1, turn.activeNode)
        assertEquals("失败也不能吞掉已到达的正文", "半句", turn.body)
    }

    @Test
    fun `召回节点携带真实分片与相关度区间`() {
        val turn = LiveTurn()
        turn.apply(recallEvent(listOf(0.87, 0.91)))
        val node = turn.recall!!
        assertEquals("0.80~0.90", node.range)
        assertEquals(2, node.shards.size)
        assertEquals("第 1 轮", node.shards.first().turn)
        assertEquals("相关度 87%", node.shards.first().score)
    }

    @Test
    fun `无思考内容时不产出思考节点（不留空节点）`() {
        val turn = LiveTurn()
        turn.apply(ChatEngine.Event.Content("只有正文"))
        turn.apply(ChatEngine.Event.Finished("stop"))
        assertTrue(turn.nodes.isEmpty())
    }
}

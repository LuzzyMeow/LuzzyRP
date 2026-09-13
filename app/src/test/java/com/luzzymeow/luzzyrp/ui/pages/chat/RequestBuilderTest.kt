package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.RuntimeSnapshots
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **请求 = 会话状态的纯函数**（A6）——批 A 最关键性质的可执行证明。
 *
 * 这里不测「某段文本对不对」，测的是**缓存赖以成立的两条结构性质**：
 *
 * 1. **落盘顺序 = 请求顺序**：本轮发出的快照必须先于用户消息落盘。
 *    会话 73 的第一次回退就是这条错了（快照挂在列表末尾），而它**不报错、不崩溃**，
 *    只在下一轮悄悄把前缀截断——所以必须有一条测试钉死它。
 * 2. **纯追加**：`turn(n)` 的每一条消息在 `turn(n+1)` 里**逐字节**相同且位置不变。
 *    断言用的是 `toOpenAiJson()`（就是 OpenAI 协议的线上字节），不是「内容差不多」。
 *
 * 为什么这些能是 JVM 单测：组装层（[PromptAssembler] / [RuntimeSnapshots]）与
 * [RequestBuilder] 都是纯 Kotlin。改造前它藏在 `ChatEngine` 里，只能靠起模拟器发请求来看。
 */
class RequestBuilderTest {

    private val character = PromptAssembler.CharacterView(
        name = "谢昭",
        description = "旧书店的老板，说话很慢。",
        firstMes = "「来了。」",
    )

    /**
     * 一条**漂移型**世界书条目（`at_depth`）——它按设计走尾部快照，
     * 于是每一轮都能稳定地产生一条快照，纯追加性质才有东西可测。
     */
    private val dynamicEntry = WorldEntry(
        comment = "雨夜",
        content = "今晚下着雨，街上没什么人。",
        constant = true,
        position = WorldPosition.AtDepth,
    )

    /** 一条**稳定型**条目（`system_top`）——它进 system，用来验证「system 逐字不变」。 */
    private val stableEntry = WorldEntry(
        comment = "世界观",
        content = "这里是一座靠海的小城。",
        constant = true,
        position = WorldPosition.SystemTop,
    )

    private fun input(retained: String?) = PromptAssembler.Input(
        character = character,
        worldEntries = listOf(stableEntry, dynamicEntry),
        toolHint = "涉及设定时可先检索世界书。",
        retainedSnapshot = retained,
    )

    /** 一轮的产物（把「落盘 → 模型回复」也演出来，下一轮才有真实的历史）。 */
    private data class Turn(
        val plan: RequestBuilder.Plan,
        val state: List<ChatMessage>,
        val retained: String?,
    )

    /** 跑一轮：算计划 → **按计划顺序落盘** → 追加一条模型回复。 */
    private fun turn(state: List<ChatMessage>, userText: String, retained: String?): Turn {
        val plan = RequestBuilder.plan(state, userText, input(retained))
        val next = state + plan.appends + ChatMessage.Ai(results = listOf(AiResult(raw = "回复：$userText")))
        return Turn(plan, next, plan.snapshotText ?: retained)
    }

    /** 协议线上字节（OpenAI 形态就是我们要发出去的东西）。 */
    private fun wire(message: LlmMessage): String = message.toOpenAiJson().toString()

    /**
     * **纯追加的判据**：下一轮的每一条消息与上一轮**逐字节相同、位置相同**。
     *
     * 这就是 PLAN §11 要求的「`turn1 请求 ⊂ turn2 请求`」的字节级版本；
     * 用 `startsWith` 比 JSON 数组字面量是不行的（末尾 `]` 会挡住），所以逐条比。
     */
    private fun assertPureAppend(previous: List<LlmMessage>, next: List<LlmMessage>, label: String) {
        assertTrue("$label：下一轮不得比上一轮短", next.size >= previous.size)
        previous.forEachIndexed { index, message ->
            assertEquals(
                "$label：第 $index 条在两轮之间必须逐字节相同（前缀不得被改写）",
                wire(message),
                wire(next[index]),
            )
        }
    }

    // ---------------------------------------------------------------- 落盘顺序（回退缺陷的守卫）

    @Test
    fun `落盘顺序等于请求顺序——快照必须先于用户消息`() {
        val result = turn(emptyList(), "第一句", null)

        assertNotNull("有漂移型世界书时本轮应发快照", result.plan.snapshotText)
        assertEquals("本轮恰好落盘两条", 2, result.plan.appends.size)
        assertTrue(
            "第一条落盘的必须是快照，实际=${result.plan.appends.map { it::class.simpleName }}",
            result.plan.appends[0] is ChatMessage.Snapshot,
        )
        assertEquals(ChatMessage.User("第一句"), result.plan.appends[1])
    }

    @Test
    fun `快照在请求里的位置也在用户输入之前——与落盘位置一致`() {
        val result = turn(emptyList(), "第一句", null)
        val messages = result.plan.messages
        val snapshotIndex = messages.indexOfFirst { it.content.startsWith(RuntimeSnapshots.HEADER) }
        val userIndex = messages.indexOfLast { it.content == "第一句" }

        assertTrue("快照必须出现", snapshotIndex >= 0)
        assertTrue("快照必须在用户输入之前（实际 $snapshotIndex vs $userIndex）", snapshotIndex < userIndex)
    }

    // ---------------------------------------------------------------- 纯追加（本批的验收指标）

    @Test
    fun `连续五轮的请求是纯追加——每一轮都逐字节包含上一轮`() {
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        val requests = mutableListOf<List<LlmMessage>>()

        listOf("钟楼上的红苹果树", "苹果树在哪", "嬷嬷是谁", "她为什么生气", "后来呢").forEach { text ->
            val result = turn(state, text, retained)
            requests += result.plan.messages
            state = result.state
            retained = result.retained
        }

        assertEquals("五轮都要有请求", 5, requests.size)
        for (i in 0 until requests.size - 1) {
            assertPureAppend(requests[i], requests[i + 1], "第 ${i + 1}→${i + 2} 轮")
        }
    }

    @Test
    fun `召回内容每轮都变时也仍是纯追加（新快照只往尾部追加）`() {
        // 这些句子共享同一个显著词面（红苹果）→ 从第二轮起每轮都召回命中。
        // 这是最坏情况：快照内容逐轮在变。若实现把「变化的快照」写在历史中间，这里立刻红。
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        val plans = mutableListOf<RequestBuilder.Plan>()
        val requests = mutableListOf<List<LlmMessage>>()

        listOf(
            "钟楼顶上长着红苹果树",
            "红苹果是谁种的",
            "红苹果树有多高",
            "红苹果好吃吗",
        ).forEach { text ->
            val result = turn(state, text, retained)
            plans += result.plan
            requests += result.plan.messages
            state = result.state
            retained = result.retained
        }

        // 前提自证：第一轮没有历史可召回（结构上不可能命中），第 2 轮起必须命中，
        // 否则这条用例退化成上一条「内容不变」的情形、测不到该测的东西。
        assertTrue("第 2 轮起应产生召回命中", plans.drop(1).all { it.recallHits.isNotEmpty() })
        assertTrue("召回块确实进过快照", plans.any { it.snapshotText?.contains("<memory_recall>") == true })

        for (i in 0 until requests.size - 1) {
            assertPureAppend(requests[i], requests[i + 1], "第 ${i + 1}→${i + 2} 轮")
        }
    }

    // ---------------------------------------------------------------- 去重（收益来源）

    @Test
    fun `内容没变就不发第二条快照`() {
        // 第一轮：动态条目 + 无召回 → 发一条快照
        val first = turn(emptyList(), "第一句", null)
        assertNotNull(first.plan.snapshotText)

        // 第二轮：动态条目没变、且本轮输入与历史无词面重叠 → 召回仍为空 → 快照内容逐字相同
        val second = turn(first.state, "换个话题", first.retained)
        assertNull("内容没变 → 一条都不发（这正是多数轮次前缀完全不变的原因）", second.plan.snapshotText)
        assertEquals("只落盘用户消息一条", 1, second.plan.appends.size)
        assertTrue(second.plan.appends.single() is ChatMessage.User)
    }

    @Test
    fun `上一轮的快照原样留在历史里（位置与内容都不动）`() {
        val first = turn(emptyList(), "第一句", null)
        val snapshotText = first.plan.snapshotText!!
        val second = turn(first.state, "换个话题", first.retained)

        val carried = second.plan.messages.filter { it.content == snapshotText }
        assertEquals("旧快照必须原样在第二轮请求里出现一次", 1, carried.size)
        assertTrue("并且要带 runtimeSnapshot 标记（召回轮号据此跳过它）", carried.single().runtimeSnapshot)
    }

    // ---------------------------------------------------------------- 召回轮号与块位置

    @Test
    fun `快照不计入召回轮号——轮号只数真实用户发言`() {
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        repeat(3) { index ->
            val result = turn(state, "第 ${index + 1} 句关于苹果树的话", retained)
            state = result.state
            retained = result.retained
        }

        val history = RequestBuilder.historyOf(state)
        assertTrue("历史里应当有快照", history.any { it.runtimeSnapshot })
        val turns = RequestBuilder.turnsOf(history)
        val maxTurn = turns.maxOf { it.first }
        assertEquals("三轮真实发言 → 最大轮号只能是 3（快照不算轮）", 3, maxTurn)
        assertTrue(
            "快照正文不得出现在轮次序列里",
            turns.none { it.second.startsWith(RuntimeSnapshots.HEADER) },
        )
    }

    @Test
    fun `召回块在尾部快照里而不在 system`() {
        val state = turn(emptyList(), "钟楼顶上的红苹果树", null).state
        val plan = RequestBuilder.plan(state, "苹果树在哪", input(null))

        val system = plan.messages.first()
        assertEquals(LlmRole.SYSTEM, system.role)
        assertTrue("system 里不该有召回块", !system.content.contains("<memory_recall>"))
        assertTrue(
            "召回块必须在尾部快照里",
            plan.snapshotText?.contains("<memory_recall>") == true,
        )
    }

    @Test
    fun `上一轮的召回块不会被改写进 system`() {
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        repeat(3) { index ->
            val result = turn(state, "第 ${index + 1} 句关于钟楼苹果树的话", retained)
            state = result.state
            retained = result.retained
        }
        val plan = RequestBuilder.plan(state, "再说说苹果树", input(retained))

        val system = plan.messages.first()
        assertTrue("历史再长，system 也一个字都不许含召回块", !system.content.contains("<memory_recall>"))
        assertTrue("历史里的旧快照也仍然带着当年的召回块（原文不改）",
            plan.messages.any { it.runtimeSnapshot && it.content.contains("<memory_recall>") })
    }

    // ---------------------------------------------------------------- system 稳定性

    @Test
    fun `system 逐字不变——历史增长不会改写稳定块`() {
        val first = turn(emptyList(), "第一句", null)
        val second = turn(first.state, "第二句", first.retained)
        val third = turn(second.state, "第三句", second.retained)

        assertEquals(
            "system 是缓存的最大稳定前缀，绝不允许随轮次变化",
            wire(first.plan.messages.first()),
            wire(third.plan.messages.first()),
        )
    }

    @Test
    fun `最后一条消息是本轮用户输入`() {
        val result = turn(emptyList(), "第一句", null)
        val last = result.plan.messages.last()
        assertEquals(LlmRole.USER, last.role)
        assertEquals("第一句", last.content)
    }
}

package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.Compaction
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.RegexScript
import com.luzzymeow.luzzyrp.chat.RuntimeSnapshots
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * [RequestBuilder] 都是纯 Kotlin。改造前它藏在 `AgentLoop` 里，只能靠起模拟器发请求来看。
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

    /** 同上，但带**提示词侧正则**（A1）——用来钉住它改了什么、没改什么。 */
    private fun turnWithScripts(
        state: List<ChatMessage>,
        userText: String,
        retained: String?,
        scripts: List<RegexScript>,
        userName: String = "",
    ): Turn {
        val plan = RequestBuilder.plan(
            state = state,
            userText = userText,
            input = input(retained),
            regexScripts = scripts,
            promptUserName = userName,
        )
        val next = state + plan.appends + ChatMessage.Ai(results = listOf(AiResult(raw = "回复：$userText")))
        return Turn(plan, next, plan.snapshotText ?: retained)
    }

    /**
     * 一条**仅提示词**脚本（`promptOnly = true`）：只有这种才会进请求。
     *
     * 默认什么都不勾 = 「仅用户可见」→ 提示词侧一律跳过（上游 `app.js:4039` 的 `userOnly`）。
     * 所以这里的默认值与显示期的脚本**正好相反**，不是笔误。
     */
    private fun promptScript(
        pattern: String,
        replacement: String,
        minDepth: Int? = null,
        maxDepth: Int? = null,
        enabled: Boolean = true,
    ) = RegexScript(
        name = "提示词侧",
        pattern = pattern,
        flags = "g",
        replacement = replacement,
        placement = setOf(1, 2),
        scope = "global",
        markdownOnly = false,
        promptOnly = true,
        minDepth = minDepth,
        maxDepth = maxDepth,
        enabled = enabled,
    )

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

    // ---------------------------------------------------------------- B3：工具轨迹进请求

    private fun aiWithTrail(
        text: String,
        trail: List<com.luzzymeow.luzzyrp.chat.ToolStep>,
    ) = ChatMessage.Ai(name = "谢昭", results = listOf(AiResult(raw = text, toolTrail = trail)))

    @Test
    fun `历史里的工具轨迹会展开进请求：调用 → 结果 → 正文`() {
        // 落库时「正文 + 轨迹」合成一行；请求里必须还原成真实发生顺序，
        // 否则模型看到的是「先说话、后查资料」——它会以为工具结果是说完话才拿到的。
        val state = listOf(
            ChatMessage.User("钟楼上有苹果树吗"),
            aiWithTrail(
                "查到了：钟楼顶上确实有一整树红苹果。",
                listOf(
                    com.luzzymeow.luzzyrp.chat.ToolStep(
                        "world_info_lookup",
                        """{"keywords":["钟楼"]}""",
                        "设定：钟楼有红苹果树",
                    ),
                ),
            ),
        )
        val plan = RequestBuilder.plan(state, "那棵树是谁种的", input(null))
        val history = plan.messages

        val callIndex = history.indexOfFirst { it.role == LlmRole.ASSISTANT && it.toolCalls.isNotEmpty() }
        val toolIndex = history.indexOfFirst { it.role == LlmRole.TOOL }
        val textIndex = history.indexOfFirst { it.role == LlmRole.ASSISTANT && it.content.contains("查到了") }

        assertTrue("调用必须在请求里", callIndex >= 0)
        assertTrue("结果必须在请求里", toolIndex >= 0)
        assertTrue(
            "顺序必须是 调用 → 结果 → 正文（实际 $callIndex/$toolIndex/$textIndex）",
            callIndex < toolIndex && toolIndex < textIndex,
        )
        assertEquals("设定：钟楼有红苹果树", history[toolIndex].content)
    }

    @Test
    fun `悬空的工具轨迹在请求里已被补成「结果未知」`() {
        // 场景：上一轮进程在工具执行中途被杀 → 库里留下「有调用没结果」。
        // 进请求之前必须补平，否则严格协议直接报配对错误，或者模型以为「工具没返回」而反复重试。
        val state = listOf(
            ChatMessage.User("钟楼上有苹果树吗"),
            aiWithTrail(
                "我查一下……",
                listOf(com.luzzymeow.luzzyrp.chat.ToolStep("world_info_lookup", "{}", result = null)),
            ),
        )
        val plan = RequestBuilder.plan(state, "还在吗", input(null))

        val tool = plan.messages.single { it.role == LlmRole.TOOL }
        assertEquals(com.luzzymeow.luzzyrp.chat.ToolPairing.UNKNOWN, tool.content)
    }

    @Test
    fun `展开是决定性的——两次组装出同样的字节（批 A 的前缀性质不被批 B 破坏）`() {
        val state = listOf(
            ChatMessage.User("问"),
            aiWithTrail("答", listOf(com.luzzymeow.luzzyrp.chat.ToolStep("t", "{}", "结果"))),
        )
        val first = RequestBuilder.plan(state, "第二问", input(null)).messages.map { wire(it) }
        val second = RequestBuilder.plan(state, "第二问", input(null)).messages.map { wire(it) }
        assertEquals(first, second)
    }

    // ---------------------------------------------------------------- 压缩水位线（B5）

    /** 一段「被压缩过」的会话：水位线之前的历史不再进请求，简报取而代之。 */
    private fun compactedState(): List<ChatMessage> = listOf(
        ChatMessage.User("很早以前说的话"),
        ChatMessage.Ai(results = listOf(AiResult(raw = "很早以前的回复"))),
        ChatMessage.Compacted("角色是谢昭，用户在找钟楼上的红苹果树。"),
        ChatMessage.User("那棵树是谁种的"),
        ChatMessage.Ai(results = listOf(AiResult(raw = "没人记得了。"))),
    )

    @Test
    fun `水位线之前的历史不进请求`() {
        val plan = RequestBuilder.plan(compactedState(), "还在吗", input(null))
        val contents = plan.messages.map { it.content }

        assertTrue(
            "被取代的历史必须消失（否则等于没压缩，白付一次摘要调用）",
            contents.none { it.contains("很早以前") },
        )
        assertTrue("水位线之后的对话必须还在", contents.any { it == "那棵树是谁种的" })
    }

    @Test
    fun `简报以运行时上下文的形态进请求且排在保留段之前`() {
        val plan = RequestBuilder.plan(compactedState(), "还在吗", input(null))
        val summaryIndex = plan.messages.indexOfFirst { it.content.contains("对话简报") }
        val keptIndex = plan.messages.indexOfFirst { it.content == "那棵树是谁种的" }

        assertTrue("简报必须在请求里", summaryIndex >= 0)
        assertTrue("简报必须排在保留的对话之前（实际 $summaryIndex vs $keptIndex）", summaryIndex < keptIndex)
        val summary = plan.messages[summaryIndex]
        assertTrue("它不是用户发言（召回轮号据此跳过）", summary.runtimeSnapshot)
        assertEquals("wire 上仍是 user 消息", LlmRole.USER, summary.role)
    }

    @Test
    fun `水位线之前的快照不算「已发过」——压缩后要能重发运行时上下文`() {
        // 场景：整段历史（含最后一条快照）都被压缩取代。若仍拿那条快照当「已发过」，
        // 模型就永久丢失运行时上下文，而且不报错——这类静默失效必须被钉住。
        val state = listOf(
            ChatMessage.User("很久以前"),
            ChatMessage.Snapshot(RuntimeSnapshots.HEADER + "\n\n旧的运行时上下文"),
            ChatMessage.Compacted("简报正文"),
            ChatMessage.User("刚才说的"),
        )
        assertNull("被取代的快照不得算作已发出", RequestBuilder.lastSnapshotText(state))
        val plan = RequestBuilder.plan(state, "还在吗", input(null))
        assertNotNull("于是本轮应当重发一条新快照", plan.snapshotText)
    }

    @Test
    fun `水位线之后的快照照旧算「已发过」（不重复发）`() {
        val state = listOf(
            ChatMessage.Compacted("简报正文"),
            ChatMessage.User("刚才说的"),
            ChatMessage.Snapshot(RuntimeSnapshots.HEADER + "\n\n现在的运行时上下文"),
        )
        assertEquals(
            RuntimeSnapshots.HEADER + "\n\n现在的运行时上下文",
            RequestBuilder.lastSnapshotText(state),
        )
    }

    @Test
    fun `每条历史消息都带上存储下标——压缩后要靠它把水位线写回库`() {
        val state = compactedState()
        val plan = RequestBuilder.plan(state, "还在吗", input(null))
        val history = RequestBuilder.historyOf(state)

        assertEquals("水位线之前的 2 条被摘掉，其余（含水位线自己）都在", 3, history.size)
        assertEquals("第一条就是水位线那一行", 2, history.first().sourceIndex)
        assertEquals(
            "水位线本身以简报形态进请求（正文由 Compaction.summaryMessage 单点定义）",
            Compaction.summaryMessage("角色是谢昭，用户在找钟楼上的红苹果树。").content,
            history.first().content,
        )
        assertEquals("末条是最后一行", state.lastIndex, history.last().sourceIndex)
        assertTrue(
            "展开出的多条共享同一个存储下标",
            RequestBuilder.historyOf(
                listOf(aiWithTrail("答", listOf(com.luzzymeow.luzzyrp.chat.ToolStep("t", "{}", "r")))),
            ).all { it.sourceIndex == 0 },
        )
        // 请求消息里的下标必须原样带到引擎（压缩事件据此算落库位置）
        assertEquals(history.map { it.sourceIndex }, plan.messages.filter { it.fromHistory }.map { it.sourceIndex })
    }

    // ---------------------------------------------------------------- A1：提示词侧正则

    /**
     * 「仅提示词」的脚本**进请求**，且改的是模型看到的字节——这是 A1 存在的全部理由：
     * 上游模型看到的上下文与用户看到的不同，这正是「模型为什么不听话」的常见真因。
     */
    @Test
    fun `勾了仅提示词的脚本会改请求内容`() {
        val state = turn(emptyList(), "钟楼上有苹果树吗", null).state
        val plain = RequestBuilder.plan(state, "那棵树是谁种的", input(null))
        val withScript = RequestBuilder.plan(
            state = state,
            userText = "那棵树是谁种的",
            input = input(null),
            regexScripts = listOf(promptScript("苹果", "梨")),
        )

        assertTrue(
            "提示词侧脚本必须真的改到请求字节",
            withScript.messages.any { it.content.contains("梨") },
        )
        assertTrue(
            "原来的字面（被替换掉的那个）应在该条消息里消失",
            plain.messages.none { it.content.contains("梨") },
        )
        // 只改内容、不改结构：条数与角色序列必须逐条对齐
        assertEquals(plain.messages.size, withScript.messages.size)
        assertEquals(plain.messages.map { it.role }, withScript.messages.map { it.role })
    }

    /**
     * **仅用户可见的脚本（默认形态）一个字节都不许改请求。**
     *
     * 上游 `app.js:4039` 的 `userOnly = markdownOnly || (!markdownOnly && !promptOnly)`：
     * 两项都不勾时按「仅用户可见」处理，提示词侧跳过。这条用例是
     * 「A1 会不会误伤普通用户」的守卫——绝大多数用户的脚本都是这个形态。
     */
    @Test
    fun `没勾仅提示词的脚本不进请求（默认形态零影响）`() {
        val state = turn(emptyList(), "钟楼上有苹果树吗", null).state
        val plain = RequestBuilder.plan(state, "那棵树是谁种的", input(null))
        val userOnly = RegexScript(
            name = "显示用高亮",
            pattern = "苹果",
            flags = "g",
            replacement = "<span>梨</span>",
            placement = setOf(1, 2),
            scope = "global",
            markdownOnly = false,
            promptOnly = false, // ← 两项都没勾 = 仅用户可见
            minDepth = null,
            maxDepth = null,
            enabled = true,
        )
        val withScript = RequestBuilder.plan(
            state = state,
            userText = "那棵树是谁种的",
            input = input(null),
            regexScripts = listOf(userOnly),
        )

        assertEquals(
            "仅用户可见的脚本不该改请求——一个字节都不行",
            plain.messages.map { wire(it) },
            withScript.messages.map { wire(it) },
        )
    }

    /** 勾了「仅 Markdown（用户可见）」同理跳过（上游两条判据取或）。 */
    @Test
    fun `勾了仅 Markdown 的脚本也不进请求`() {
        val state = turn(emptyList(), "钟楼上有苹果树吗", null).state
        val plain = RequestBuilder.plan(state, "那棵树是谁种的", input(null))
        val markdownOnly = promptScript("苹果", "梨").copy(markdownOnly = true, promptOnly = false)
        val withScript = RequestBuilder.plan(
            state = state,
            userText = "那棵树是谁种的",
            input = input(null),
            regexScripts = listOf(markdownOnly),
        )

        assertEquals(plain.messages.map { wire(it) }, withScript.messages.map { wire(it) })
    }

    /** 显式 `enabled = false` 一律跳过（上游 `app.js:4028`）。 */
    @Test
    fun `停用的脚本不进请求`() {
        val state = turn(emptyList(), "钟楼上有苹果树吗", null).state
        val plain = RequestBuilder.plan(state, "那棵树是谁种的", input(null))
        val disabled = RequestBuilder.plan(
            state = state,
            userText = "那棵树是谁种的",
            input = input(null),
            regexScripts = listOf(promptScript("苹果", "梨", enabled = false)),
        )

        assertEquals(plain.messages.map { wire(it) }, disabled.messages.map { wire(it) })
    }

    /**
     * **depth 区间外跳过**，且深度口径 = `条数 - 1 - 下标`（上游 `app.js:6516`）。
     *
     * 这里同时钉住两件事，缺一条这个用例就证明不了「接线正确」：
     * ① `depth >= minDepth` 的末条消息被改、更早的没被改；
     * ② 同一条**历史**消息在第 2 轮 depth 是 2、第 3 轮 depth 是 4 —— 每轮都在增长。
     *    这是深度语义的固有后果（见 [RegexScripts.applyToPromptMessages] 的说明），
     *    所以断言写的是「按当前轮的实际 depth 命中」，不是「永远命中同几条」。
     */
    @Test
    fun `depth 区间外的消息不被改写`() {
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        // 两轮真实历史（每轮：用户发言 + 模型回复），第三轮来断言
        repeat(2) { index ->
            val result = turn(state, "老话 $index", retained)
            state = result.state
            retained = result.retained
        }
        val plan = RequestBuilder.plan(
            state = state,
            userText = "这一轮的问题",
            input = retained?.let { input(it) } ?: input(null),
            regexScripts = listOf(promptScript("话", "语", minDepth = 1)),
        )

        val last = plan.messages.last()
        assertEquals("末条必然是本轮输入", LlmRole.USER, last.role)
        assertEquals("depth=0 < minDepth=1 → 不改", "这一轮的问题", last.content)
        // 往前的每一条：depth 就是「它后面还有几条」。用逐条下标重算一遍，与实现同源。
        // ⚠️ system 必须排除：它**不套脚本**（上游 `app.js:4019`），而本条夹具的角色描述里
        // 正好含「说话很慢」的「话」字——第一版断言没排除 system，于是被自己的夹具抓到，
        // 报的是「depth≥1 的消息没被改写」，实际是「system 本来就不该被改写」。
        plan.messages.forEachIndexed { index, message ->
            if (message.role == LlmRole.SYSTEM) return@forEachIndexed
            val depth = plan.messages.size - 1 - index
            if (depth >= 1 && message.content.contains("话")) {
                assertTrue(
                    "depth=$depth 已在区间内，应被改写（实际=${message.content}）",
                    message.content.contains("语"),
                )
            }
        }
        // 至少有一条真的被改了，否则上面那个 forEach 是空转
        assertTrue("区间内应至少有一条被实际改写", plan.messages.any { it.content.contains("语") })
        // 反向：minDepth 大于任何消息的 depth → 整条请求一个字节都不变
        val untouched = RequestBuilder.plan(
            state = state,
            userText = "这一轮的问题",
            input = retained?.let { input(it) } ?: input(null),
            regexScripts = listOf(promptScript("话", "语", minDepth = 999)),
        )
        assertTrue(
            "minDepth 超出所有消息的 depth → 不该改任何一条",
            untouched.messages.none { it.content.contains("语") },
        )
    }

    /** 角色口径：`system` 不套脚本（上游 `app.js:4019`）；user/assistant 各自按 `placement` 判。 */
    @Test
    fun `system 消息不会被提示词脚本改写`() {
        val plan = RequestBuilder.plan(
            state = emptyList(),
            userText = "苹果",
            input = input(null),
            regexScripts = listOf(promptScript("谢昭", "某某")),
        )
        val system = plan.messages.first { it.role == LlmRole.SYSTEM }
        assertTrue("system 里应含角色名（前置条件自证）", system.content.contains("谢昭"))
        assertTrue("system 不套脚本，角色名必须原样", !system.content.contains("某某"))
        assertTrue("但同一条脚本对 user 消息生效", plan.messages.any { it.content.contains("某某") })
    }

    /**
     * `{{user}}` 在**提示词侧**也替换（上游 `app.js:4018`，`apply` 里占位符先于一切判据）。
     * 这条同时钉住「传给提示词侧的是档案原名、不是显示期的『你』兜底」。
     */
    @Test
    fun `提示词侧的 user 占位符按档案名替换`() {
        val plan = RequestBuilder.plan(
            state = emptyList(),
            userText = "{{user}}在吗",
            input = input(null),
            promptUserName = "鹿溪",
        )
        assertTrue(plan.messages.any { it.content == "鹿溪在吗" })

        // 空名字 → 占位符原样保留（宁可留着，也不要替换成空白）
        val noName = RequestBuilder.plan(
            state = emptyList(),
            userText = "{{user}}在吗",
            input = input(null),
            promptUserName = "",
        )
        assertTrue(noName.messages.last().content == "{{user}}在吗")
    }

    /** 没配脚本、也没名字 → 连 map 都不做，逐字节等于原序列（零成本的守卫）。 */
    @Test
    fun `没有脚本时请求逐字节不变`() {
        val state = turn(emptyList(), "第一句", null).state
        val plain = RequestBuilder.plan(state, "第二句", input(null))
        val explicit = RequestBuilder.plan(
            state = state,
            userText = "第二句",
            input = input(null),
            regexScripts = emptyList(),
            promptUserName = "",
        )
        assertEquals(plain.messages.map { wire(it) }, explicit.messages.map { wire(it) })
    }

    // ---------------------------------------------------------------- A1 × 批 A 的不变量

    /**
     * **提示词侧脚本不得破坏纯追加**（PLAN §7.1 的指标在 A1 之后仍要成立）。
     *
     * 用「无深度区间的仅提示词脚本」——脚本是确定性的，于是同一条历史消息每一轮都套用
     * **同一份改写**，前缀必须逐字节保持。这正是接线正确性的最强证据：
     * 若哪天有人把脚本套到了「已发出的上一轮字节」之外的地方（例如改写快照或重排消息），
     * 这条立刻红。
     */
    @Test
    fun `有提示词脚本时连续五轮仍是纯追加`() {
        val scripts = listOf(promptScript("苹果", "梨"), promptScript("钟楼", "塔楼"))
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        val requests = mutableListOf<List<LlmMessage>>()

        listOf("钟楼上的红苹果树", "苹果树在哪", "嬷嬷是谁", "她为什么生气", "后来呢").forEach { text ->
            val result = turnWithScripts(state, text, retained, scripts)
            requests += result.plan.messages
            state = result.state
            retained = result.retained
        }

        // 前提自证：脚本确实改到了请求（否则这条退化成「没有脚本」的情形，测不到东西）
        assertTrue("脚本应已改写请求内容", requests.any { list -> list.any { it.content.contains("梨") } })
        for (i in 0 until requests.size - 1) {
            assertPureAppend(requests[i], requests[i + 1], "第 ${i + 1}→${i + 2} 轮（有提示词脚本）")
        }
    }

    /**
     * **反过来把代价钉住**：带深度区间的提示词脚本会让前缀在**同一位置**逐轮漂移。
     *
     * 这不是实现缺陷，是深度语义的固有后果（上游亦然）：一条老消息的 depth 每轮都在增长，
     * 于是「它这一轮该不该被改写」与上一轮可能不同 → 上一轮发出去的字节无法成为这一轮的前缀。
     *
     * 为什么要把一条负面结论写成绿灯用例：**它是可预知的**。写成断言之后，
     * 将来真机复核缓存命中率若掉下来，第一现场就在这里——省掉一轮「是不是我们接线错了」的排查。
     */
    @Test
    fun `带深度区间的提示词脚本会让前缀在同一位置漂移（已知代价，真机复核命中率）`() {
        // minDepth=2：历史越老越可能落在区间外，于是它在不同轮次里被改写 / 不被改写
        val scripts = listOf(promptScript("苹果", "梨", minDepth = 2))
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        val requests = mutableListOf<List<LlmMessage>>()

        listOf("钟楼上的红苹果树", "苹果树在哪", "嬷嬷是谁", "后来呢").forEach { text ->
            val result = turnWithScripts(state, text, retained, scripts)
            requests += result.plan.messages
            state = result.state
            retained = result.retained
        }

        val drifted = requests.zipWithNext().count { (previous, next) -> !isPureAppend(previous, next) }
        println("[A1·深度区间] 4 轮里有 $drifted 轮的前缀发生漂移（上游同此语义，真机复核命中率）")
        assertTrue(
            "深度区间的脚本会逐轮改变老消息的改写结果 → 前缀必然漂移（若有朝一日为 0，说明判据变了，请重读本用例）",
            drifted > 0,
        )
    }

    /** 纯追加判据的可复用版本（[assertPureAppend] 的布尔形态）。 */
    private fun isPureAppend(previous: List<LlmMessage>, next: List<LlmMessage>): Boolean {
        if (next.size < previous.size) return false
        return previous.indices.all { wire(previous[it]) == wire(next[it]) }
    }

    // ---------------------------------------------------------------- ② 提示词侧文风过滤

    /**
     * **assistant 的历史在发给模型前会被过滤**（②，上游 `processRegex` 出口 `app.js:4090`）。
     *
     * 为什么这条属于「照上游」而不是我们的发明：上游的提示词侧走的就是 `processRegex`，
     * 而它的出口对 `role === 'assistant'` 调 `filterBlockedStyleText`。
     * 于是模型看到的自己的历史**也是过滤后的**。
     */
    @Test
    fun `提示词侧的 assistant 历史会被文风过滤`() {
        val state = listOf(
            ChatMessage.User("问第一句"),
            ChatMessage.Ai(results = listOf(AiResult(raw = "他极其平静地说，然后走开了。"))),
        )
        val plan = RequestBuilder.plan(state, "第二句", input(null))

        val assistant = plan.messages.single { it.role == LlmRole.ASSISTANT && it.content.contains("走开了") }
        assertFalse("命中短语不该进请求", assistant.content.contains("极其"))
        assertTrue("其余正文必须留着", assistant.content.contains("走开了"))
    }

    /** **user 的历史一个字都不许动**（上游只对 assistant 筛）。 */
    @Test
    fun `提示词侧的 user 历史不被文风过滤`() {
        val state = listOf(ChatMessage.User("他极其平静地说了这句话"))
        val plan = RequestBuilder.plan(state, "第二句", input(null))

        assertTrue(
            "用户自己的话不该被筛",
            plan.messages.any { it.role == LlmRole.USER && it.content.contains("极其") },
        )
    }

    /** system 也不筛（它不是 assistant；上游同）。 */
    @Test
    fun `提示词侧的 system 不被文风过滤`() {
        val plan = RequestBuilder.plan(
            state = emptyList(),
            userText = "问",
            input = input(null),
        )
        val system = plan.messages.first { it.role == LlmRole.SYSTEM }
        // 角色描述里含「说话很慢」，这里用一个必然含黑名单词的预设来验证
        val planWithHit = RequestBuilder.plan(
            state = emptyList(),
            userText = "问",
            input = PromptAssembler.Input(
                character = character,
                presets = listOf(
                    com.luzzymeow.luzzyrp.data.preset.PresetEntry(name = "语气", content = "语气极其克制。"),
                ),
            ),
        )
        val sys = planWithHit.messages.first { it.role == LlmRole.SYSTEM }
        assertTrue("system 不是 assistant → 不筛（上游同）", sys.content.contains("极其"))
        assertTrue("前提自证：system 确实装配出来了", system.content.isNotEmpty())
    }

    /**
     * **过滤开关是活的**：关掉之后同一条历史逐字节不变。
     * 这条与上上条构成负控对（否则「永远返回同一结果」的实现也能过）。
     */
    @Test
    fun `关掉文风过滤后提示词侧逐字节不变`() {
        val state = listOf(
            ChatMessage.User("问第一句"),
            ChatMessage.Ai(results = listOf(AiResult(raw = "他极其平静地说，然后走开了。"))),
        )
        val on = RequestBuilder.plan(state, "第二句", input(null), styleFilterEnabled = true)
        val off = RequestBuilder.plan(state, "第二句", input(null), styleFilterEnabled = false)

        assertTrue("开着时该短语被删", on.messages.none { it.content.contains("极其") })
        assertTrue("关着时该短语必须在", off.messages.any { it.content.contains("极其") })
        assertTrue(
            "两条请求必须真的不同（否则说明开关没接上）",
            on.messages.map { wire(it) } != off.messages.map { wire(it) },
        )
    }

    /**
     * **过滤不破坏纯追加**：过滤是确定性的（同一条历史每轮得到同一份改写），
     * 所以批 A 的前缀性质必须继续成立。
     */
    @Test
    fun `有文风过滤时连续五轮仍是纯追加`() {
        var state: List<ChatMessage> = emptyList()
        var retained: String? = null
        val requests = mutableListOf<List<LlmMessage>>()

        listOf("钟楼上的红苹果树", "苹果树在哪", "嬷嬷是谁", "她为什么生气", "后来呢").forEach { text ->
            val result = turn(state, text, retained)
            requests += result.plan.messages
            state = result.state
            retained = result.retained
        }
        for (i in 0 until requests.size - 1) {
            assertPureAppend(requests[i], requests[i + 1], "第 ${i + 1}→${i + 2} 轮（有文风过滤）")
        }
    }
}

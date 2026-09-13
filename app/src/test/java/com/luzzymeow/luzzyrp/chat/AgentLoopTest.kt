package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmError
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.ToolCallDelta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **完整 Agent Loop**（批 B）的行为门禁：两级循环、终止条件、工具配对、纯追加不变量。
 *
 * 全部用**可控的假传输**（零网络）：真实网络路径由 `chat/llm/` 的既有测试覆盖，
 * 这里只测编排语义——而编排恰恰是批 B 唯一新增的东西。
 *
 * 「组装」不在本测试职责内：引擎只接受装配好的 [ChatRequest]，
 * 组装语义由 `RequestBuilderTest` / `PromptSectionsTest` 覆盖。
 */
class AgentLoopTest {

    /** 让假传输能在请求到达时读到循环状态（构造顺序所致，故用可空引用）。 */
    private var loop: AgentLoop? = null

    /** 按轮次回放的假传输；记录收到的请求，并可在**请求到达的那一刻**采样循环状态。 */
    private inner class FakeTransport(private val rounds: List<List<LlmDelta>>) : LlmTransport {
        val requests = mutableListOf<LlmRequest>()
        val stateAtRequest = mutableListOf<AgentLoop.State>()

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            loop?.let { stateAtRequest += it.state.value }
            rounds.getOrElse(requests.size - 1) { emptyList() }.forEach { emit(it) }
        }
    }

    private val config = TransportConfig(
        baseUrl = "https://example.invalid/v1",
        apiKey = "test-key",
        model = "test-model",
    )

    private fun request(
        history: List<LlmMessage> = emptyList(),
        userText: String = "",
        promptInput: PromptAssembler.Input = PromptAssembler.Input(),
        recallHits: List<RecallEngine.Hit> = emptyList(),
    ): ChatRequest = ChatRequest(
        messages = PromptAssembler.assemble(promptInput.copy(history = history, userText = userText)),
        recallHits = recallHits,
    )

    /** 造一个「模型请求 N 个工具」的轮次。 */
    private fun toolRound(vararg names: String, finish: String = "tool_calls") = buildList {
        names.forEachIndexed { index, name ->
            add(LlmDelta(toolCalls = listOf(ToolCallDelta(index = index, id = "call_$index", name = name))))
            add(
                LlmDelta(
                    toolCalls = listOf(
                        ToolCallDelta(index = index, argumentsChunk = """{"keywords":["钟楼"]}"""),
                    ),
                ),
            )
        }
        add(LlmDelta(finishReason = finish))
    }

    // ---------------------------------------------------------------- 基本编排

    @Test
    fun `未配置供应商时立刻失败且不发请求`() = runTest {
        val transport = FakeTransport(emptyList())
        val events = AgentLoop(transport).run(TransportConfig(), request(userText = "你好")).toList()
        assertEquals(1, events.size)
        assertTrue(events.single() is AgentLoop.Event.Failed)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `端点拼接补 chat completions`() {
        assertEquals("https://example.invalid/v1/chat/completions", config.chatEndpoint())
        assertEquals(
            "https://a.example/v1/chat/completions",
            TransportConfig(baseUrl = "https://a.example/v1/chat/completions").chatEndpoint(),
        )
        assertEquals(
            "https://a.example/v1/chat/completions",
            TransportConfig(baseUrl = "https://a.example/v1/").chatEndpoint(),
        )
        assertEquals("", TransportConfig(baseUrl = "  ").chatEndpoint())
    }

    @Test
    fun `召回命中由请求带入并作为事件回放（且先于正文）`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(content = "嗯"), LlmDelta(finishReason = "stop"))))
        val hits = listOf(RecallEngine.Hit(turn = 1, score = 0.5, text = "钟楼顶上长着红苹果树"))
        val events = AgentLoop(transport).run(config, request(userText = "苹果树在哪", recallHits = hits)).toList()

        val recall = events.filterIsInstance<AgentLoop.Event.Recall>().single()
        assertEquals(hits, recall.hits)
        assertTrue(events.indexOf(recall) < events.indexOfFirst { it is AgentLoop.Event.Content })
    }

    @Test
    fun `没有召回命中时不发召回事件`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        val events = AgentLoop(transport).run(config, request(userText = "你好呀")).toList()
        assertTrue(events.none { it is AgentLoop.Event.Recall })
    }

    @Test
    fun `引擎原样发送请求里的消息序列——一条都不改写、不追加`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        val history = listOf(
            LlmMessage(role = LlmRole.USER, content = "第一句", fromHistory = true),
            LlmMessage(role = LlmRole.ASSISTANT, content = "回复", fromHistory = true),
        )
        val sent = request(history = history, userText = "第二句")
        AgentLoop(transport).run(config, sent).toList()

        assertEquals(
            "引擎不得自己拼一遍——前缀缓存的前提是「发出去的就是装配出来的」",
            sent.messages.map { it.role to it.content },
            transport.requests.first().messages.map { it.role to it.content },
        )
    }

    @Test
    fun `reasoning 与 content 增量按到达顺序原样转成事件`() = runTest {
        val transport = FakeTransport(
            listOf(
                listOf(
                    LlmDelta(reasoning = "先想"),
                    LlmDelta(reasoning = "一下"),
                    LlmDelta(content = "你"),
                    LlmDelta(content = "好"),
                    LlmDelta(finishReason = "stop"),
                ),
            ),
        )
        val events = AgentLoop(transport).run(config, request(userText = "你好")).toList()
        val kinds = events.mapNotNull {
            when (it) {
                is AgentLoop.Event.Reasoning -> "R:${it.chunk}"
                is AgentLoop.Event.Content -> "C:${it.chunk}"
                else -> null
            }
        }
        assertEquals(listOf("R:先想", "R:一下", "C:你", "C:好"), kinds)
    }

    // ---------------------------------------------------------------- B1：两级循环 + 状态机

    @Test
    fun `状态机：每个 step 开始时处于 Running 且 step 号递增，收尾回到 Idle`() = runTest {
        val transport = FakeTransport(
            listOf(
                toolRound(WorldBookTool.Name),
                listOf(LlmDelta(content = "答"), LlmDelta(finishReason = "stop")),
            ),
        )
        val agent = AgentLoop(transport, toolRunner = { _, _ -> "{}" })
        loop = agent
        val events = agent.run(config, request(userText = "钟楼")).toList()

        assertEquals(
            "两次请求分别应处在 Running(step=1) 与 Running(step=2)",
            listOf(AgentLoop.State.Running(1, 1), AgentLoop.State.Running(1, 2)),
            transport.stateAtRequest,
        )
        assertEquals("跑完必须回到 Idle（否则界面永远停在「生成中」）", AgentLoop.State.Idle, agent.state.value)
        assertEquals(
            "每个 step 都要发 StepStarted",
            listOf(1, 2),
            events.filterIsInstance<AgentLoop.Event.StepStarted>().map { it.step },
        )
    }

    // ---------------------------------------------------------------- B2：终止条件

    @Test
    fun `没有工具调用即停——这是主终止条件`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(content = "答"), LlmDelta(finishReason = "stop"))))
        val events = AgentLoop(transport).run(config, request(userText = "你好")).toList()

        assertEquals("只发一次请求", 1, transport.requests.size)
        val finished = events.filterIsInstance<AgentLoop.Event.Finished>().single()
        assertEquals(AgentLoop.FinishReason.Completed, finished.reason)
        assertEquals("供应商原始结束原因要原样带出来（截断提示用它）", "stop", finished.wire)
    }

    @Test
    fun `撞输出上限是粘性的——即使同时请求了工具也不再续跑`() = runTest {
        // 关键：finish_reason=length 与 tool_calls 同时出现时**不能**继续跑工具——
        // 输出已经断了，续跑只会拿到半截工具参数（DSH agent.ts:305-310 同）。
        val transport = FakeTransport(listOf(toolRound(WorldBookTool.Name, finish = "length")))
        val events = AgentLoop(transport, toolRunner = { _, _ -> "{}" }).run(config, request(userText = "钟楼")).toList()

        assertEquals("不得再发第二次请求", 1, transport.requests.size)
        val finished = events.filterIsInstance<AgentLoop.Event.Finished>().single()
        assertEquals(AgentLoop.FinishReason.MaxTokens, finished.reason)
        assertEquals("length", finished.wire)
        assertTrue("粘性终止时不该执行任何工具", events.none { it is AgentLoop.Event.ToolCallFinished })
    }

    @Test
    fun `step 上限触发 stepLimit 且不再发下一次请求`() = runTest {
        val endless = toolRound(WorldBookTool.Name)
        val transport = FakeTransport(listOf(endless, endless, endless, endless, endless))
        val events = AgentLoop(transport, toolRunner = { _, _ -> "{}" }, maxSteps = 3)
            .run(config, request(userText = "钟楼")).toList()

        assertEquals("恰好跑满上限次请求", 3, transport.requests.size)
        val finished = events.filterIsInstance<AgentLoop.Event.Finished>().single()
        assertEquals(AgentLoop.FinishReason.StepLimit, finished.reason)
    }

    @Test
    fun `默认 step 上限是 50——这是我们的加法，必须写在明面上`() {
        assertEquals("DSH 无上限靠外部监督；我们没有外部刹车，所以要有硬界", 50, AgentLoop.MAX_STEPS)
    }

    @Test
    fun `传输错误转成 Failed 事件且不抛异常`() = runTest {
        val transport = FakeTransport(
            listOf(listOf(LlmDelta(error = LlmError("HTTP 401", httpStatus = 401), finishReason = "error"))),
        )
        val events = AgentLoop(transport).run(config, request(userText = "你好")).toList()
        assertEquals("HTTP 401", events.filterIsInstance<AgentLoop.Event.Failed>().single().message)
        assertNotNull(events)
    }

    @Test
    fun `工具开关是真实请求差异：关闭后请求体不带 tools`() = runTest {
        val on = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        AgentLoop(on).run(config, request(userText = "你好")).toList()
        assertTrue("开启时请求应带工具定义", on.requests.first().tools.isNotEmpty())

        val off = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        AgentLoop(off).run(config.copy(toolsEnabled = false), request(userText = "你好")).toList()
        assertTrue("关闭时请求不应带任何工具定义", off.requests.first().tools.isEmpty())
    }

    // ---------------------------------------------------------------- B3：工具配对

    @Test
    fun `工具结果按模型给出的顺序回填（多调用一次给全）`() = runTest {
        val transport = FakeTransport(
            listOf(
                toolRound("first_tool", "second_tool", "third_tool"),
                listOf(LlmDelta(content = "好"), LlmDelta(finishReason = "stop")),
            ),
        )
        val seen = mutableListOf<String>()
        val agent = AgentLoop(transport, toolRunner = { name, _ -> seen += name; "result-of-$name" })
        agent.run(config, request(userText = "钟楼")).toList()

        assertEquals(listOf("first_tool", "second_tool", "third_tool"), seen)

        val second = transport.requests[1].messages
        val toolRows = second.filter { it.role == LlmRole.TOOL }
        assertEquals("三条调用三条结果", 3, toolRows.size)
        assertEquals(
            "回填顺序必须与模型给出的顺序一致",
            listOf("first_tool", "second_tool", "third_tool"),
            toolRows.map { it.name },
        )
        assertEquals(listOf("call_0", "call_1", "call_2"), toolRows.map { it.toolCallId })
    }

    @Test
    fun `中止时未启动的调用补合成错误结果——配对不留缺口`() = runTest {
        val transport = FakeTransport(listOf(toolRound("first_tool", "second_tool")))
        // 第一个调用执行完后要求中止
        val executedCount = intArrayOf(0)
        val agent = AgentLoop(transport, toolRunner = { name, _ -> executedCount[0]++; "result-of-$name" })
        val events = agent
            .run(config, request(userText = "钟楼"), shouldAbort = { executedCount[0] >= 1 })
            .toList()

        assertEquals("已中止 → 不得再发第二次请求", 1, transport.requests.size)
        val finished = events.filterIsInstance<AgentLoop.Event.Finished>().single()
        assertEquals(AgentLoop.FinishReason.Aborted, finished.reason)

        val results = events.filterIsInstance<AgentLoop.Event.ToolCallFinished>()
        assertEquals("每个调用都要有一条结果（含合成的那条）", 2, results.size)
        assertEquals("first_tool", results[0].name)
        assertEquals("second_tool", results[1].name)
        assertEquals(
            "未启动的那条要写明是被中止的，而不是「执行失败」",
            ToolPairing.ABORTED,
            results[1].result,
        )
        assertEquals("已执行的那条结果不变", "result-of-first_tool", results[0].result)
    }

    @Test
    fun `中止发生在第一个调用之前时全部都是合成结果`() = runTest {
        val transport = FakeTransport(listOf(toolRound("a_tool", "b_tool")))
        val agent = AgentLoop(transport, toolRunner = { _, _ -> error("不该被执行") })
        val events = agent.run(config, request(userText = "钟楼"), shouldAbort = { true }).toList()

        val results = events.filterIsInstance<AgentLoop.Event.ToolCallFinished>()
        assertEquals(2, results.size)
        assertTrue("全部为合成结果", results.all { it.result == ToolPairing.ABORTED })
    }

    @Test
    fun `工具抛异常不许掀翻整个 turn——异常本身作为结果回填，配对仍然完整`() = runTest {
        // 工具是本地代码（世界书检索、将来的文件/网络操作），它抛异常不该等于「应用崩了」；
        // 而且「每个 tool_call 必有结果」是硬约束，所以异常要被转成结果本身。
        val transport = FakeTransport(
            listOf(
                toolRound("boom_tool", "ok_tool"),
                listOf(LlmDelta(content = "答"), LlmDelta(finishReason = "stop")),
            ),
        )
        val agent = AgentLoop(transport, toolRunner = { name, _ ->
            if (name == "boom_tool") error("工具内部炸了") else "fine"
        })
        val events = agent.run(config, request(userText = "钟楼")).toList()

        val results = events.filterIsInstance<AgentLoop.Event.ToolCallFinished>()
        assertEquals("两条调用两条结果", 2, results.size)
        assertTrue("炸掉那条要如实写明失败", results[0].result.contains("tool execution failed"))
        assertEquals("另一条不受影响", "fine", results[1].result)
        assertEquals(
            "turn 照常跑完",
            AgentLoop.FinishReason.Completed,
            events.filterIsInstance<AgentLoop.Event.Finished>().single().reason,
        )
    }

    // ---------------------------------------------------------------- B7：纯追加不变量

    @Test
    fun `工具回填是纯追加——第二轮请求以第一轮为前缀`() = runTest {
        val transport = FakeTransport(
            listOf(toolRound(WorldBookTool.Name), listOf(LlmDelta(content = "好"), LlmDelta(finishReason = "stop"))),
        )
        AgentLoop(transport, toolRunner = { _, _ -> "{}" }).run(config, request(userText = "钟楼")).toList()

        assertPureAppend(transport.requests[0].messages, transport.requests[1].messages, "第 1→2 轮")
    }

    @Test
    fun `多 step 的工具续跑每一轮都是上一轮的逐字节延伸`() = runTest {
        val transport = FakeTransport(
            listOf(
                toolRound(WorldBookTool.Name),
                toolRound(WorldBookTool.Name),
                toolRound(WorldBookTool.Name),
                listOf(LlmDelta(content = "答"), LlmDelta(finishReason = "stop")),
            ),
        )
        AgentLoop(transport, toolRunner = { _, _ -> "{}" }).run(config, request(userText = "钟楼")).toList()

        assertEquals(4, transport.requests.size)
        for (i in 0 until transport.requests.size - 1) {
            assertPureAppend(
                transport.requests[i].messages,
                transport.requests[i + 1].messages,
                "第 ${i + 1}→${i + 2} 轮",
            )
        }
    }

    /** 逐条比协议字节：下一轮必须以上一轮为**严格前缀**（批 A 的性质不能被批 B 破坏）。 */
    private fun assertPureAppend(previous: List<LlmMessage>, next: List<LlmMessage>, label: String) {
        assertTrue("$label：下一轮不得比上一轮短", next.size >= previous.size)
        previous.forEachIndexed { index, message ->
            assertEquals(
                "$label：第 $index 条必须逐字节相同（前缀不得被改写）",
                message.toOpenAiJson().toString(),
                next[index].toOpenAiJson().toString(),
            )
        }
    }
}

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
 * 聊天引擎单测：用**可控的假传输**验证真实链路的编排逻辑
 * （真实网络路径本身已由 `chat/llm/` 既有测试覆盖，此处只测引擎的事件编排与工具循环）。
 *
 * **组装不在本测试的职责内**（A6 起）：引擎只接受一个装配好的 [ChatRequest]，
 * 组装语义（system / 预设 / 历史 / 快照 / 召回块放哪）由 `PromptAssemblerTest` /
 * `PromptSectionsTest` / `ui.pages.chat.RequestBuilderTest` 覆盖。
 * 这样切开的好处是：本文件里任何一条红灯都只可能是**编排**错了，不会与「拼错了」混在一起。
 */
class ChatEngineTest {

    /** 按轮次回放的假传输；记录收到的请求以便断言回填内容。 */
    private class FakeTransport(private val rounds: List<List<LlmDelta>>) : LlmTransport {
        val requests = mutableListOf<LlmRequest>()

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            rounds.getOrElse(requests.size - 1) { emptyList() }.forEach { emit(it) }
        }
    }

    private val config = TransportConfig(
        baseUrl = "https://example.invalid/v1",
        apiKey = "test-key",
        model = "test-model",
    )

    /** 装配一个请求：走真实装配函数（避免测试自造消息序列而与生产漂移）。 */
    private fun request(
        history: List<LlmMessage> = emptyList(),
        userText: String = "",
        promptInput: PromptAssembler.Input = PromptAssembler.Input(),
        recallHits: List<RecallEngine.Hit> = emptyList(),
    ): ChatRequest = ChatRequest(
        messages = PromptAssembler.assemble(promptInput.copy(history = history, userText = userText)),
        recallHits = recallHits,
    )

    @Test
    fun `未配置供应商时立刻失败且不发请求`() = runTest {
        val transport = FakeTransport(emptyList())
        val events = ChatEngine(transport).run(TransportConfig(), request(userText = "你好")).toList()
        assertEquals(1, events.size)
        assertTrue(events.single() is ChatEngine.Event.Failed)
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
        val events = ChatEngine(transport).run(config, request(userText = "苹果树在哪", recallHits = hits)).toList()

        val recall = events.filterIsInstance<ChatEngine.Event.Recall>().single()
        assertEquals(hits, recall.hits)
        // 召回事件必须发生在第一次请求的正文之前（界面上的检索节点先出现）
        assertTrue(events.indexOf(recall) < events.indexOfFirst { it is ChatEngine.Event.Content })
    }

    @Test
    fun `没有召回命中时不发召回事件`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        val events = ChatEngine(transport).run(config, request(userText = "你好呀")).toList()
        assertTrue(events.none { it is ChatEngine.Event.Recall })
    }

    @Test
    fun `引擎原样发送请求里的消息序列——一条都不改写、不追加`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        val history = listOf(
            LlmMessage(role = LlmRole.USER, content = "第一句", fromHistory = true),
            LlmMessage(role = LlmRole.ASSISTANT, content = "回复", fromHistory = true),
        )
        val sent = request(history = history, userText = "第二句")
        ChatEngine(transport).run(config, sent).toList()

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
        val events = ChatEngine(transport).run(config, request(userText = "你好")).toList()
        val kinds = events.mapNotNull {
            when (it) {
                is ChatEngine.Event.Reasoning -> "R:${it.chunk}"
                is ChatEngine.Event.Content -> "C:${it.chunk}"
                else -> null
            }
        }
        assertEquals(listOf("R:先想", "R:一下", "C:你", "C:好"), kinds)
        assertEquals("stop", events.filterIsInstance<ChatEngine.Event.Finished>().single().finishReason)
    }

    @Test
    fun `工具循环：执行真实工具并把结果回填后再发一次请求`() = runTest {
        val transport = FakeTransport(
            listOf(
                // 第 1 轮：模型请求工具（参数分片到达，模拟真实线格式）
                listOf(
                    LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, id = "call_1", name = WorldBookTool.Name))),
                    LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, argumentsChunk = """{"keywords":"""))),
                    LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, argumentsChunk = """["钟楼"]}"""))),
                    LlmDelta(finishReason = "tool_calls"),
                ),
                // 第 2 轮：拿到工具结果后继续生成
                listOf(LlmDelta(content = "钟楼上的苹果树"), LlmDelta(finishReason = "stop")),
            ),
        )
        val events = ChatEngine(transport).run(config, request(userText = "钟楼在哪")).toList()

        assertEquals(2, transport.requests.size)
        assertEquals(listOf(WorldBookTool.Name), events.filterIsInstance<ChatEngine.Event.ToolCallStarted>().map { it.name })

        // 参数分片按到达顺序累积（逐片可见）
        val chunks = events.filterIsInstance<ChatEngine.Event.ToolCallArgs>().map { it.chunk }
        assertEquals(listOf("""{"keywords":""", """["钟楼"]}"""), chunks)

        // 工具结果是**本机真实执行**的结果（含世界书条目）
        val finished = events.filterIsInstance<ChatEngine.Event.ToolCallFinished>().single()
        assertEquals(WorldBookTool.Name, finished.name)
        assertTrue(finished.result.contains("钟楼红苹果树"))

        // 第二次请求的消息里必须有 assistant(tool_calls) + tool(结果)
        val second = transport.requests[1].messages
        val assistant = second.single { it.role == LlmRole.ASSISTANT }
        assertEquals(1, assistant.toolCalls.size)
        val tool = second.single { it.role == LlmRole.TOOL }
        assertEquals("call_1", tool.toolCallId)
        assertEquals(WorldBookTool.Name, tool.name)
        assertTrue(tool.content.contains("钟楼红苹果树"))

        // 工具事件先于第二轮正文
        val idxTool = events.indexOfFirst { it is ChatEngine.Event.ToolCallFinished }
        val idxContent = events.indexOfFirst { it is ChatEngine.Event.Content }
        assertTrue(idxTool < idxContent)
    }

    @Test
    fun `工具回填是纯追加——第二轮请求以第一轮为前缀`() = runTest {
        val toolRound = listOf(
            LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, id = "c", name = WorldBookTool.Name, argumentsChunk = "{}"))),
            LlmDelta(finishReason = "tool_calls"),
        )
        val transport = FakeTransport(
            listOf(toolRound, listOf(LlmDelta(content = "好"), LlmDelta(finishReason = "stop"))),
        )
        ChatEngine(transport).run(config, request(userText = "钟楼")).toList()

        val first = transport.requests[0].messages
        val second = transport.requests[1].messages
        assertTrue(second.size > first.size)
        first.forEachIndexed { index, message ->
            assertEquals(
                "工具续跑也必须保持前缀（第 $index 条）",
                message.toOpenAiJson().toString(),
                second[index].toOpenAiJson().toString(),
            )
        }
    }

    @Test
    fun `工具循环有上限：连续请求工具不会无限循环`() = runTest {
        val toolRound = listOf(
            LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, id = "c", name = WorldBookTool.Name, argumentsChunk = "{}"))),
            LlmDelta(finishReason = "tool_calls"),
        )
        val transport = FakeTransport(listOf(toolRound, toolRound, toolRound, toolRound, toolRound))
        val events = ChatEngine(transport).run(config, request(userText = "钟楼")).toList()
        assertEquals(3, transport.requests.size)
        assertEquals("max_rounds", events.filterIsInstance<ChatEngine.Event.Finished>().single().finishReason)
    }

    @Test
    fun `传输错误转成 Failed 事件且不抛异常`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(error = LlmError("HTTP 401", httpStatus = 401), finishReason = "error"))))
        val events = ChatEngine(transport).run(config, request(userText = "你好")).toList()
        assertEquals("HTTP 401", events.filterIsInstance<ChatEngine.Event.Failed>().single().message)
        assertNotNull(events)
    }

    @Test
    fun `工具开关是真实请求差异：关闭后请求体不带 tools`() = runTest {
        val on = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        ChatEngine(on).run(config, request(userText = "你好")).toList()
        assertTrue("开启时请求应带工具定义", on.requests.first().tools.isNotEmpty())

        val off = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        ChatEngine(off).run(config.copy(toolsEnabled = false), request(userText = "你好")).toList()
        assertTrue("关闭时请求不应带任何工具定义", off.requests.first().tools.isEmpty())
    }
}

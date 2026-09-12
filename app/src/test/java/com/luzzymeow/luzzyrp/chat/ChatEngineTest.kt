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

    @Test
    fun `未配置供应商时立刻失败且不发请求`() = runTest {
        val transport = FakeTransport(emptyList())
        val events = ChatEngine(transport).run(TransportConfig(), emptyList(), "你好").toList()
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
    fun `命中历史时先发召回事件并把召回块注入 system`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(content = "嗯"), LlmDelta(finishReason = "stop"))))
        val history = listOf(
            LlmMessage(role = LlmRole.USER, content = "钟楼顶上长着红苹果树"),
            LlmMessage(role = LlmRole.ASSISTANT, content = "别告诉嬷嬷"),
        )
        val events = ChatEngine(transport).run(config, history, "苹果树在哪").toList()

        val recall = events.filterIsInstance<ChatEngine.Event.Recall>().single()
        assertEquals(1, recall.hits.size)
        assertEquals(1, recall.hits.first().turn)
        // 召回事件必须发生在第一次请求之前
        assertTrue(events.indexOf(recall) < events.indexOfFirst { it is ChatEngine.Event.Content })

        val system = transport.requests.first().messages.first()
        assertEquals(LlmRole.SYSTEM, system.role)
        assertTrue(system.content.contains("<memory_recall>"))
        assertTrue(system.content.contains(VanioCard.persona.take(10)))
    }

    @Test
    fun `未命中历史时不发召回事件`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(finishReason = "stop"))))
        val events = ChatEngine(transport).run(config, emptyList(), "你好呀").toList()
        assertTrue(events.none { it is ChatEngine.Event.Recall })
        assertTrue(transport.requests.first().messages.first().content.contains("<world_info>").not())
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
        val events = ChatEngine(transport).run(config, emptyList(), "你好").toList()
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
        val events = ChatEngine(transport).run(config, emptyList(), "钟楼在哪").toList()

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
    fun `工具循环有上限：连续请求工具不会无限循环`() = runTest {
        val toolRound = listOf(
            LlmDelta(toolCalls = listOf(ToolCallDelta(index = 0, id = "c", name = WorldBookTool.Name, argumentsChunk = "{}"))),
            LlmDelta(finishReason = "tool_calls"),
        )
        val transport = FakeTransport(listOf(toolRound, toolRound, toolRound, toolRound, toolRound))
        val events = ChatEngine(transport).run(config, emptyList(), "钟楼").toList()
        assertEquals(3, transport.requests.size)
        assertEquals("max_rounds", events.filterIsInstance<ChatEngine.Event.Finished>().single().finishReason)
    }

    @Test
    fun `传输错误转成 Failed 事件且不抛异常`() = runTest {
        val transport = FakeTransport(listOf(listOf(LlmDelta(error = LlmError("HTTP 401", httpStatus = 401), finishReason = "error"))))
        val events = ChatEngine(transport).run(config, emptyList(), "你好").toList()
        assertEquals("HTTP 401", events.filterIsInstance<ChatEngine.Event.Failed>().single().message)
        assertNotNull(events)
    }
}

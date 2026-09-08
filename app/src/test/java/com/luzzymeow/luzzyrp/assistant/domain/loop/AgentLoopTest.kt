package com.luzzymeow.luzzyrp.assistant.domain.loop

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmDelta
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmError
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmTransport
import com.luzzymeow.luzzyrp.assistant.domain.llm.ToolCallDelta
import com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate
import com.luzzymeow.luzzyrp.assistant.domain.tool.AskUserOption
import com.luzzymeow.luzzyrp.assistant.domain.tool.AskUserPrompt
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AgentLoop] 单测（FakeLlmTransport 驱动，无网络）。
 *
 * 覆盖 PLAN §5.2 的循环语义：纯文本收尾、工具调用事件顺序、工具异常转 Error 继续、
 * `ask_user` 暂停、轮次/预算上限、取消、审批（拒绝 / 放行）、传输错误、上下文回灌。
 */
class AgentLoopTest {

    private class FakeTool(
        override val name: String,
        override val tier: ToolTier = ToolTier.T0_READ,
        private val behavior: suspend (ToolContext) -> ToolResult = { ToolResult.Ok("ok") },
    ) : Tool {
        override val description: String = "fake tool"
        override val parameters: JsonObject = Schema.empty()
        var executions: Int = 0
        var lastContext: ToolContext? = null

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            executions++
            lastContext = ctx
            return behavior(ctx)
        }
    }

    private class FakeLlmTransport(private val turns: List<List<LlmDelta>>) : LlmTransport {
        val requests: MutableList<LlmRequest> = mutableListOf()
        private var index = 0

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            val deltas = turns.getOrElse(index) { emptyList() }
            index++
            deltas.forEach { emit(it) }
        }
    }

    private fun textTurn(vararg text: String): List<LlmDelta> = text.map { LlmDelta(content = it) }

    private fun toolTurn(
        id: String,
        name: String,
        arguments: String = "{}",
        index: Int = 0,
    ): List<LlmDelta> = listOf(
        LlmDelta(
            toolCalls = listOf(ToolCallDelta(index = index, id = id, name = name, argumentsChunk = arguments)),
            finishReason = "tool_calls",
        )
    )

    private fun config(
        cancelled: () -> Boolean = { false },
        approvalProvider: ApprovalProvider = ApprovalProvider { _, _ -> false },
        log: (String) -> Unit = {},
    ) = AgentRunConfig(
        request = LlmRequest(
            messages = emptyList(),
            protocol = "openai",
            baseUrl = "http://127.0.0.1:1/v1",
            apiKey = "sk-test-not-real",
            model = "gpt-test",
        ),
        assistantId = "a1",
        conversationId = "c1",
        assistantName = "测试助手",
        systemPrompt = "你是 {{assistant_name}}",
        workspacePath = "/ws/a1",
        nowMillis = 1_700_000_000_000L,
        zoneId = "UTC",
        cancelled = cancelled,
        log = log,
        approvalProvider = approvalProvider,
    )

    private fun run(
        loop: AgentLoop,
        config: AgentRunConfig,
        history: List<LlmMessage> = emptyList(),
        input: String = "你好",
    ): List<AgentEvent> = runBlocking { withTimeout(10_000) { loop.run(config, history, input).toList() } }

    private fun names(events: List<AgentEvent>): List<String> = events.map { it::class.simpleName ?: "" }

    // ---------- 基本路径 ----------

    @Test
    fun `纯文本一轮以 stop 收尾`() {
        val transport = FakeLlmTransport(listOf(textTurn("你", "好")))
        val events = run(AgentLoop(transport, ToolRegistry()), config())

        assertEquals(listOf("TurnStarted", "TextDelta", "TextDelta", "TurnFinished"), names(events))
        assertEquals(0, (events.first() as AgentEvent.TurnStarted).turn)
        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `一轮工具调用的事件顺序与回灌`() {
        val tool = FakeTool("get_time") { ToolResult.Ok("12:00") }
        val transport = FakeLlmTransport(
            listOf(toolTurn("call_1", "get_time"), textTurn("现在是 12:00"))
        )
        val events = run(AgentLoop(transport, ToolRegistry(listOf(tool))), config())

        assertEquals(
            listOf("TurnStarted", "ToolCallStarted", "ToolCallFinished", "TurnStarted", "TextDelta", "TurnFinished"),
            names(events),
        )
        assertEquals("call_1", (events[1] as AgentEvent.ToolCallStarted).call.id)
        assertEquals(ToolResult.Ok("12:00"), (events[2] as AgentEvent.ToolCallFinished).result)
        assertEquals(1, tool.executions)

        // 第二个请求 = system + user + assistant(tool_calls) + tool(结果)
        val second = transport.requests[1]
        assertEquals(listOf(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.TOOL), second.messages.map { it.role })
        assertEquals("get_time", second.messages[2].toolCalls.single().name)
        assertEquals("call_1", second.messages[3].toolCallId)
        assertEquals("12:00", second.messages[3].content)

        // ToolContext 注入了助手 / 会话
        assertEquals("a1", tool.lastContext!!.assistantId)
        assertEquals("c1", tool.lastContext!!.conversationId)
    }

    @Test
    fun `历史与本轮输入装配进首个请求且带工具 schema`() {
        val transport = FakeLlmTransport(listOf(textTurn("ok")))
        val tool = FakeTool("get_time")
        val events = run(
            AgentLoop(transport, ToolRegistry(listOf(tool))),
            config(),
            history = listOf(
                LlmMessage(role = LlmRole.USER, content = "之前的问题"),
                LlmMessage(role = LlmRole.ASSISTANT, content = "之前的回答"),
            ),
            input = "新问题",
        )

        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
        val request = transport.requests.single()
        assertEquals(
            listOf(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER),
            request.messages.map { it.role },
        )
        assertEquals("新问题", request.messages.last().content)
        assertTrue(request.messages.first().content.contains("你是 测试助手"))
        assertEquals(1, request.tools.size)
    }

    // ---------- 工具失败与超时 ----------

    @Test
    fun `工具抛异常转 Error 且循环继续`() {
        val tool = FakeTool("boom") { throw IllegalStateException("炸了") }
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "boom"), textTurn("换个办法")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(tool))), config())

        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertTrue(finished.result is ToolResult.Error)
        assertTrue((finished.result as ToolResult.Error).message.contains("炸了"))
        assertEquals(2, events.filterIsInstance<AgentEvent.TurnStarted>().size)
        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `工具超时转可重试 Error 且循环继续`() {
        val tool = FakeTool("slow") {
            delay(1_000)
            ToolResult.Ok("太晚了")
        }
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "slow"), textTurn("算了")))
        val loop = AgentLoop(
            transport = transport,
            tools = ToolRegistry(listOf(tool)),
            budget = BudgetGuard(toolTimeoutMs = 50L),
        )
        val events = run(loop, config())

        val result = events.filterIsInstance<AgentEvent.ToolCallFinished>().single().result
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).retryable)
        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `工具日志作为 ToolCallProgress 推送`() {
        val tool = FakeTool("chatty") { ctx ->
            ctx.log("进度 1/2")
            ToolResult.Ok("done")
        }
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "chatty"), textTurn("好")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(tool))), config())

        val progress = events.filterIsInstance<AgentEvent.ToolCallProgress>().single()
        assertEquals("c1", progress.id)
        assertEquals("进度 1/2", progress.chunk)
    }

    // ---------- ask_user ----------

    @Test
    fun `ask_user 发 AwaitingUserInput 并结束本轮`() {
        val askUser = FakeTool("ask_user") {
            ToolResult.NeedUserInput(
                AskUserPrompt(
                    question = "选哪个",
                    options = listOf(AskUserOption("A"), AskUserOption("B")),
                    allowMultiple = true,
                )
            )
        }
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "ask_user"), textTurn("不应到达")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(askUser))), config())

        val awaiting = events.filterIsInstance<AgentEvent.AwaitingUserInput>().single()
        assertEquals("c1", awaiting.callId)
        assertEquals("选哪个", awaiting.question)
        assertEquals(listOf("A", "B"), awaiting.options)
        assertTrue(awaiting.allowMultiple)
        assertEquals(1, events.filterIsInstance<AgentEvent.TurnStarted>().size)
        assertEquals(1, transport.requests.size)
        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
    }

    // ---------- 预算 / 取消 ----------

    @Test
    fun `超过 maxRounds 以 max_rounds 收尾`() {
        val tool = FakeTool("get_time")
        val transport = FakeLlmTransport(List(10) { toolTurn("c$it", "get_time") })
        val loop = AgentLoop(transport, ToolRegistry(listOf(tool)), budget = BudgetGuard(maxRounds = 2))
        val events = run(loop, config())

        assertEquals(2, events.filterIsInstance<AgentEvent.TurnStarted>().size)
        assertEquals(2, transport.requests.size)
        assertEquals("max_rounds", (events.last() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `token 预算超限以 max_rounds 收尾`() {
        val tool = FakeTool("get_time")
        val transport = FakeLlmTransport(
            listOf(
                toolTurn("c1", "get_time") + LlmDelta(usage = LlmDelta.Usage(input = 100, output = 100)),
                textTurn("不该到第二轮"),
            )
        )
        val loop = AgentLoop(
            transport = transport,
            tools = ToolRegistry(listOf(tool)),
            budget = BudgetGuard(maxRounds = 10, tokenBudget = 150L),
        )
        val events = run(loop, config())

        assertEquals(1, events.filterIsInstance<AgentEvent.TurnStarted>().size)
        assertEquals("max_rounds", (events.last() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `取消信号在开始前即生效`() {
        val events = run(AgentLoop(FakeLlmTransport(emptyList()), ToolRegistry()), config(cancelled = { true }))
        assertEquals(listOf("TurnFinished"), names(events))
        assertEquals("cancelled", (events.single() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `工具执行后置取消信号以 cancelled 收尾`() {
        var cancelled = false
        val tool = FakeTool("get_time") {
            cancelled = true
            ToolResult.Ok("ok")
        }
        val transport = FakeLlmTransport(List(5) { toolTurn("c1", "get_time") })
        val events = run(AgentLoop(transport, ToolRegistry(listOf(tool))), config(cancelled = { cancelled }))

        assertEquals("cancelled", (events.last() as AgentEvent.TurnFinished).reason)
        assertEquals(1, transport.requests.size)
    }

    // ---------- 审批 ----------

    @Test
    fun `需审批工具默认被拒绝且循环继续`() {
        val writeTool = FakeTool("memory_write", tier = ToolTier.T1_WRITE_APP)
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "memory_write"), textTurn("好的")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(writeTool))), config())

        assertTrue(events.any { it is AgentEvent.ToolCallApproval })
        assertEquals(0, writeTool.executions)
        val result = events.filterIsInstance<AgentEvent.ToolCallFinished>().single().result
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("未批准"))
        assertEquals("stop", (events.last() as AgentEvent.TurnFinished).reason)
    }

    @Test
    fun `审批端口放行后执行`() {
        val writeTool = FakeTool("memory_write", tier = ToolTier.T1_WRITE_APP) { ToolResult.Ok("已写入") }
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "memory_write"), textTurn("好的")))
        val events = run(
            AgentLoop(transport, ToolRegistry(listOf(writeTool))),
            config(approvalProvider = ApprovalProvider { _, _ -> true }),
        )

        assertEquals(1, writeTool.executions)
        assertEquals(ToolResult.Ok("已写入"), events.filterIsInstance<AgentEvent.ToolCallFinished>().single().result)
    }

    @Test
    fun `会话级许可不再弹审批`() {
        val gate = ApprovalGate().apply { allowForSession("memory_write") }
        val writeTool = FakeTool("memory_write", tier = ToolTier.T1_WRITE_APP)
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "memory_write"), textTurn("好的")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(writeTool), gate)), config())

        assertEquals(0, events.count { it is AgentEvent.ToolCallApproval })
        assertEquals(1, writeTool.executions)
    }

    @Test
    fun `一次性许可被消费后不再询问`() {
        val gate = ApprovalGate().apply { allowOnce("memory_write") }
        val writeTool = FakeTool("memory_write", tier = ToolTier.T1_WRITE_APP)
        val transport = FakeLlmTransport(listOf(toolTurn("c1", "memory_write"), textTurn("好的")))
        val events = run(AgentLoop(transport, ToolRegistry(listOf(writeTool), gate)), config())

        assertEquals(0, events.count { it is AgentEvent.ToolCallApproval })
        assertEquals(1, writeTool.executions)
    }

    // ---------- 传输错误 ----------

    @Test
    fun `传输错误发 Error 与 error 终止`() {
        val transport = FakeLlmTransport(
            listOf(listOf(LlmDelta(error = LlmError("HTTP 500", retryable = true))))
        )
        val events = run(AgentLoop(transport, ToolRegistry()), config())

        assertEquals(listOf("TurnStarted", "Error", "TurnFinished"), names(events))
        val error = events[1] as AgentEvent.Error
        assertEquals("HTTP 500", error.message)
        assertTrue(error.retryable)
        assertEquals("error", (events.last() as AgentEvent.TurnFinished).reason)
    }
}

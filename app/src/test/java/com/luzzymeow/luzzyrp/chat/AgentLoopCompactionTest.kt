package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmError
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **压缩（B5）与重试（B6）的引擎行为门禁。**
 *
 * 全部走**可控假传输**：编排语义（什么时候压、压完发什么、什么时候重发）是这两批唯一新增的东西，
 * 而它恰恰是「只看代码看不出错」的部分——真实网络路径由 `chat/llm/` 的既有测试覆盖。
 *
 * 判据纪律：断言的是**请求内容与事件序列**（确定性），不是耗时或次数以外的概率量。
 */
class AgentLoopCompactionTest {

    private val config = TransportConfig(
        baseUrl = "https://example.invalid/v1",
        apiKey = "test-key",
        model = "test-model",
        // 门槛压到 1000 tokens（触发线 800 / 保留预算 160），让用例不用造几万字的语料
        contextWindow = 1_000,
    )

    /** 一段足够长的历史（8 条 × ~200 CJK 字 ≈ 1650 tokens > 800）。 */
    private fun longHistory(): List<LlmMessage> = buildList {
        for (i in 1..4) {
            add(LlmMessage(role = LlmRole.USER, content = "第 $i 问" + "甲".repeat(199), fromHistory = true))
            add(LlmMessage(role = LlmRole.ASSISTANT, content = "第 $i 答" + "乙".repeat(199), fromHistory = true))
        }
    }

    private fun request(history: List<LlmMessage>) = ChatRequest(
        messages = PromptAssembler.assemble(
            PromptAssembler.Input(history = history, userText = "本轮问题", emitSnapshot = false),
        ),
    )

    /** 按请求内容分派的假传输：能区分「摘要请求」与「正式请求」。 */
    private inner class ScriptedTransport(
        private val respond: (LlmRequest, Int) -> List<LlmDelta>,
    ) : LlmTransport {
        val requests = mutableListOf<LlmRequest>()

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            respond(request, requests.size - 1).forEach { emit(it) }
        }

        fun isSummary(request: LlmRequest): Boolean =
            request.messages.lastOrNull()?.content == Compaction.Instruction

        fun summaries(): List<LlmRequest> = requests.filter { isSummary(it) }
        fun conversations(): List<LlmRequest> = requests.filterNot { isSummary(it) }
    }

    private fun answer(text: String = "回复") = listOf(
        LlmDelta(content = text),
        LlmDelta(finishReason = "stop"),
    )

    private fun summaryAnswer(text: String = "这是简报") = listOf(
        LlmDelta(usage = LlmDelta.Usage(input = 900, output = 40)),
        LlmDelta(content = text),
        LlmDelta(finishReason = "stop"),
    )

    /** 摘要请求的判据：最后一条就是我们的摘要指令（正式请求的最后一条是本轮用户输入）。 */
    private fun isSummaryRequest(request: LlmRequest): Boolean =
        request.messages.lastOrNull()?.content == Compaction.Instruction

    // ---------------------------------------------------------------- B5 阈值压缩

    @Test
    fun `达阈值时先压缩再发正式请求并记账`() = runTest {
        val transport = ScriptedTransport { request, _ ->
            if (isSummaryRequest(request)) summaryAnswer() else answer()
        }
        val events = AgentLoop(transport).run(config, request(longHistory())).toList()

        val compact = events.filterIsInstance<AgentLoop.Event.Compacted>().single()
        assertEquals(AgentLoop.REASON_THRESHOLD, compact.reason)
        assertTrue("必须真的裁掉了历史", compact.dropped > 0)
        assertTrue("压缩后必须更小", compact.tokensAfter < compact.tokensBefore)
        assertEquals("摘要只发一次", 1, transport.summaries().size)
        assertEquals("压缩只发生一次", 1, events.count { it is AgentLoop.Event.Compacted })
        assertTrue("收尾仍是正常完成", events.last() is AgentLoop.Event.Finished)

        // 正式请求用的是**压缩后**的序列：简报在，且消息条数变少
        val firstConversation = transport.conversations().first()
        assertTrue(
            "简报必须进正式请求",
            firstConversation.messages.any { it.content.contains(Compaction.SummaryHeader) },
        )
        assertTrue(
            "压缩后的请求必须更短",
            firstConversation.messages.size < request(longHistory()).messages.size,
        )
    }

    /** 摘要请求的判据（与 [ScriptedTransport.isSummary] 同一口径，供 lambda 里用）。 */
    private fun transport_isSummaryPlaceholder(request: LlmRequest): Boolean =
        request.messages.lastOrNull()?.content == Compaction.Instruction

    @Test
    fun `摘要请求按原前缀逐字节重放——这是它吃一次缓存的前提`() = runTest {
        val transport = ScriptedTransport { request, _ ->
            if (isSummaryRequest(request)) summaryAnswer() else answer()
        }
        AgentLoop(transport).run(config, request(longHistory())).toList()

        val summary = transport.summaries().single()
        val original = request(longHistory()).messages
        val prefix = summary.messages.dropLast(1)
        assertEquals("前缀必须是原请求的前 N 条，一条不改", original.take(prefix.size), prefix)
        assertEquals("指令只能追加在最后一条", Compaction.Instruction, summary.messages.last().content)

        // 「吃缓存」还要求请求头完全一致（协议/端点/模型/采样值/工具集）
        val conversation = transport.conversations().first()
        assertEquals(conversation.protocol, summary.protocol)
        assertEquals(conversation.baseUrl, summary.baseUrl)
        assertEquals(conversation.model, summary.model)
        assertEquals(conversation.temperature, summary.temperature)
        assertEquals(conversation.maxTokens, summary.maxTokens)
        assertEquals(conversation.tools, summary.tools)
    }

    @Test
    fun `contextWindow 为 0 时完全不压缩（用户关掉了自动压缩）`() = runTest {
        val transport = ScriptedTransport { _, _ -> answer() }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertTrue(events.none { it is AgentLoop.Event.Compacted })
        assertTrue(transport.summaries().isEmpty())
    }

    @Test
    fun `上下文没到阈值时不压缩`() = runTest {
        val transport = ScriptedTransport { _, _ -> answer() }
        val events = AgentLoop(transport)
            .run(config.copy(contextWindow = 1_000_000), request(longHistory()))
            .toList()
        assertTrue(events.none { it is AgentLoop.Event.Compacted })
        assertTrue(transport.summaries().isEmpty())
    }

    @Test
    fun `摘要失败时不压缩也不丢上下文——照原样继续`() = runTest {
        val transport = ScriptedTransport { request, _ ->
            if (isSummaryRequest(request)) {
                listOf(LlmDelta(error = LlmError("HTTP 500：boom", retryable = false, httpStatus = 500)))
            } else {
                answer()
            }
        }
        val events = AgentLoop(transport).run(config, request(longHistory())).toList()

        assertTrue(events.none { it is AgentLoop.Event.Compacted })
        val failed = events.filterIsInstance<AgentLoop.Event.CompactionFailed>().single()
        assertEquals(AgentLoop.REASON_THRESHOLD, failed.reason)
        // 上下文一个字都没少：正式请求 = 原序列（没有被裁、也没有假简报）
        assertEquals(request(longHistory()).messages, transport.conversations().first().messages)
        assertTrue("对话本身照常完成", events.last() is AgentLoop.Event.Finished)
    }

    @Test
    fun `摘要为空也算失败（不写空简报进上下文）`() = runTest {
        val transport = ScriptedTransport { request, _ ->
            if (isSummaryRequest(request)) listOf(LlmDelta(finishReason = "stop")) else answer()
        }
        val events = AgentLoop(transport).run(config, request(longHistory())).toList()
        assertTrue(events.none { it is AgentLoop.Event.Compacted })
        assertEquals(1, events.filterIsInstance<AgentLoop.Event.CompactionFailed>().size)
    }

    // ---------------------------------------------------------------- B6 重试

    @Test
    fun `上下文溢出先压缩再重发一次`() = runTest {
        var overflowed = false
        val transport = ScriptedTransport { request, _ ->
            when {
                isSummaryRequest(request) -> summaryAnswer()
                !overflowed -> {
                    overflowed = true
                    listOf(
                        LlmDelta(
                            error = LlmError(
                                message = "HTTP 400：This model's maximum context length is 65536 tokens.",
                                retryable = false,
                                httpStatus = 400,
                            ),
                            finishReason = "error",
                        ),
                    )
                }

                else -> answer("压缩后的回复")
            }
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()

        val compact = events.filterIsInstance<AgentLoop.Event.Compacted>().single()
        assertEquals(AgentLoop.REASON_OVERFLOW, compact.reason)
        assertEquals("溢出后必须重发", 2, transport.conversations().size)
        assertTrue(
            "重发那次必须带着简报",
            transport.conversations()[1].messages.any { it.content.contains(Compaction.SummaryHeader) },
        )
        assertTrue(events.none { it is AgentLoop.Event.Failed })
        assertEquals(1, events.filterIsInstance<AgentLoop.Event.Finished>().size)
    }

    @Test
    fun `溢出重试只做一次——再失败就如实报错`() = runTest {
        val transport = ScriptedTransport { request, _ ->
            if (isSummaryRequest(request)) {
                summaryAnswer()
            } else {
                listOf(
                    LlmDelta(
                        error = LlmError("HTTP 400：maximum context length exceeded", httpStatus = 400),
                        finishReason = "error",
                    ),
                )
            }
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertEquals("正式请求只发两次（原 + 压缩后）", 2, transport.conversations().size)
        assertTrue(events.last() is AgentLoop.Event.Failed)
    }

    @Test
    fun `网络失败在什么都没上屏时重发一次`() = runTest {
        var failed = false
        val transport = ScriptedTransport { _, _ ->
            if (!failed) {
                failed = true
                listOf(LlmDelta(error = LlmError("网络错误（UnknownHostException）", retryable = true)))
            } else {
                answer("第二次成功")
            }
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertEquals(2, transport.requests.size)
        assertTrue(events.none { it is AgentLoop.Event.Failed })
        assertEquals("重发成功后正文照常上屏", 1, events.filterIsInstance<AgentLoop.Event.Content>().size)
    }

    @Test
    fun `已经上屏过的失败绝不重发（会重复输出）`() = runTest {
        val transport = ScriptedTransport { _, _ ->
            listOf(
                LlmDelta(content = "说到一半"),
                LlmDelta(error = LlmError("网络错误（SocketTimeoutException）", retryable = true)),
            )
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertEquals("只有一次请求：宁可半截话，也不重复半截话", 1, transport.requests.size)
        assertTrue(events.last() is AgentLoop.Event.Failed)
    }

    @Test
    fun `网络重试也只有一次`() = runTest {
        val transport = ScriptedTransport { _, _ ->
            listOf(LlmDelta(error = LlmError("网络错误（UnknownHostException）", retryable = true)))
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertEquals(2, transport.requests.size)
        assertTrue(events.last() is AgentLoop.Event.Failed)
    }

    @Test
    fun `配置类错误不重试`() = runTest {
        val transport = ScriptedTransport { _, _ ->
            listOf(LlmDelta(error = LlmError("请求地址非法（请检查供应商 Base URL）", retryable = false)))
        }
        val events = AgentLoop(transport).run(config.copy(contextWindow = 0), request(longHistory())).toList()
        assertEquals(1, transport.requests.size)
        assertNotNull(events.filterIsInstance<AgentLoop.Event.Failed>().single())
    }
}

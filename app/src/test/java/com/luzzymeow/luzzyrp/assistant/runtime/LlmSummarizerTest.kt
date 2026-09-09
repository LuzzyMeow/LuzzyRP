package com.luzzymeow.luzzyrp.assistant.runtime

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmDelta
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LlmSummarizer] 单测（PLAN §5.4：摘要失败必须退化为截断，不得打断对话）。 */
class LlmSummarizerTest {

    private val template = LlmRequest(
        messages = emptyList(),
        protocol = "openai",
        baseUrl = "https://api.example.com",
        apiKey = "sk-secret",
        model = "gpt-x",
    )

    private fun fakeTransport(reply: String): LlmTransport = object : LlmTransport {
        var lastRequest: LlmRequest? = null
        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            lastRequest = request
            reply.chunked(8).forEach { emit(LlmDelta(content = it)) }
        }
    }

    private fun history() = listOf(
        LlmMessage(LlmRole.USER, "帮我整理这周的会议"),
        LlmMessage(LlmRole.ASSISTANT, "好的，先看会议记录"),
    )

    @Test
    fun `摘要拼接流式分片`() = runBlocking {
        val summarizer = LlmSummarizer(fakeTransport("本周 5 场会议。"), templateProvider = { template })
        assertEquals("本周 5 场会议。", summarizer.summarize(history()))
    }

    @Test
    fun `摘要请求不带工具且温度为零`() = runBlocking {
        val capturing = object : LlmTransport {
            var captured: LlmRequest? = null
            override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
                captured = request
                emit(LlmDelta(content = "ok"))
            }
        }
        LlmSummarizer(capturing, templateProvider = { template }).summarize(history())
        val captured = capturing.captured!!
        assertTrue(captured.tools.isEmpty())
        assertEquals(false, captured.requireTool)
        assertEquals(0f, captured.temperature!!, 0f)
        assertTrue(captured.messages.first().content.contains("要点摘要"))
        assertTrue(captured.messages.last().content.contains("帮我整理这周的会议"))
    }

    @Test
    fun `无模板时返回 null（退化截断）`() = runBlocking {
        val summarizer = LlmSummarizer(fakeTransport("x"), templateProvider = { null })
        assertNull(summarizer.summarize(history()))
    }

    @Test
    fun `空历史返回 null`() = runBlocking {
        val summarizer = LlmSummarizer(fakeTransport("x"), templateProvider = { template })
        assertNull(summarizer.summarize(emptyList()))
    }

    @Test
    fun `传输抛异常时返回 null 不向上抛`() = runBlocking {
        val failing = object : LlmTransport {
            override fun stream(request: LlmRequest): Flow<LlmDelta> = flow { error("boom") }
        }
        val summarizer = LlmSummarizer(failing, templateProvider = { template })
        // ContextBuilder 侧也会兜底；这里断言不抛出
        val result = runCatching { summarizer.summarize(history()) }
        assertTrue("不应向上抛异常", result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `空白摘要返回 null`() = runBlocking {
        val summarizer = LlmSummarizer(fakeTransport("   "), templateProvider = { template })
        assertNull(summarizer.summarize(history()))
    }
}

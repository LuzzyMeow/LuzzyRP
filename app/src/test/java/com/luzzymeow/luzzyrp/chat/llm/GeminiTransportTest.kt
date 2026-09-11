package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GeminiTransport] 端到端单测（真 socket + 真 OkHttp）。
 *
 * 重点：端点在传输层拼接（含 `?key=` 查询串）、头集**只有** Content-Type、
 * 以及无 id 的 functionCall 合成稳定 id。
 */
class GeminiTransportTest {

    private fun request(baseUrl: String) = LlmRequest(
        messages = listOf(
            LlmMessage(role = LlmRole.SYSTEM, content = "你是助手"),
            LlmMessage(role = LlmRole.USER, content = "几点了"),
        ),
        protocol = "gemini",
        baseUrl = baseUrl,
        apiKey = "AIza-test",
        model = "gemini-2.0-flash",
        temperature = 0.7,
        maxTokens = 2048,
        reasoningEffort = "high",
    )

    private fun transport(logs: MutableList<String> = mutableListOf()) =
        GeminiTransport(log = { logs += it }, sleep = { })

    @Test
    fun `端点由传输层拼接且只有 Content-Type 头`() {
        RawSseServer(
            listOf(RawSseServer.Script(body = RawSseServer.sseNoDone("""{"candidates":[{"finishReason":"STOP"}]}""")))
        ).use { server ->
            runBlocking {
                withTimeout(10_000) { transport().stream(request(server.origin)).toList() }
            }
            val raw = server.requests.single()
            assertTrue(
                raw.startsWith(
                    "POST /v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=AIza-test HTTP/1.1"
                )
            )
            assertTrue(raw.contains("Content-Type: application/json"))
            assertFalse(raw.contains("Authorization:"))
            assertFalse(raw.contains("x-api-key:"))
            assertFalse(raw.contains("x-goog-api-key:"))
        }
    }

    @Test
    fun `请求体键序保真`() {
        RawSseServer(
            listOf(RawSseServer.Script(body = RawSseServer.sseNoDone("""{"candidates":[{"finishReason":"STOP"}]}""")))
        ).use { server ->
            runBlocking { withTimeout(10_000) { transport().stream(request(server.origin)).toList() } }
            val body = server.requests.single().substringAfter("\r\n\r\n")
            assertEquals(
                """{"contents":[{"role":"user","parts":[{"text":"几点了"}]}],""" +
                    """"systemInstruction":{"parts":[{"text":"你是助手"}]},""" +
                    """"generationConfig":{"temperature":0.7,"maxOutputTokens":2048,""" +
                    """"thinkingConfig":{"thinkingBudget":24576}}}""",
                body,
            )
        }
    }

    @Test
    fun `流式正文 思考 工具调用与用量端到端`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = RawSseServer.sseNoDone(
                        """{"candidates":[{"content":{"parts":[{"text":"想想","thought":true}]}}]}""",
                        """{"candidates":[{"content":{"parts":[{"text":"你"},{"text":"好"}]}}]}""",
                        """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"get_time","args":{"tz":"UTC"}}}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":12}}""",
                    )
                )
            )
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.origin)).toList() } }
            assertEquals("你好", deltas.mapNotNull { it.content }.joinToString(""))
            assertEquals("想想", deltas.mapNotNull { it.reasoning }.joinToString(""))
            assertEquals("STOP", deltas.mapNotNull { it.finishReason }.last())
            val calls = ToolCallAccumulator().apply { deltas.forEach { accept(it.toolCalls) } }.build()
            assertEquals(1, calls.size)
            assertEquals("get_time", calls[0].name)
            assertEquals("call_0_get_time", calls[0].id)
            assertEquals(5, deltas.mapNotNull { it.usage }.last().input)
        }
    }

    @Test
    fun `多帧 functionCall 合成 id 递增`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = RawSseServer.sseNoDone(
                        """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"a","args":{}}}]}}]}""",
                        """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"b","args":{}}}]}}]}""",
                    )
                )
            )
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.origin)).toList() } }
            val calls = ToolCallAccumulator().apply { deltas.forEach { accept(it.toolCalls) } }.build()
            assertEquals(listOf("call_0_a", "call_1_b"), calls.map { it.id })
        }
    }

    @Test
    fun `400 不重试且 URL 中的密钥不进日志也不进错误消息`() {
        RawSseServer(listOf(RawSseServer.Script(status = 400, body = """{"error":{"message":"bad key AIza-test-0123456789"}}"""))).use { server ->
            val logs = mutableListOf<String>()
            val deltas = runBlocking {
                withTimeout(10_000) { transport(logs).stream(request(server.origin)).toList() }
            }
            assertEquals(1, server.requests.size)
            val error = deltas.single().error!!
            assertFalse(error.retryable)
            assertFalse(error.message.contains("AIza-test-0123456789"))
            assertTrue(error.message.contains("AIza***"))
            assertTrue(logs.none { it.contains("AIza-test") })
            assertTrue(logs.none { it.contains("key=") })
        }
    }

    @Test
    fun `未配置 API Key 或模型时不发请求`() {
        RawSseServer(listOf(RawSseServer.Script(body = RawSseServer.sseNoDone()))).use { server ->
            val noKey = runBlocking { transport().stream(request(server.origin).copy(apiKey = "")).toList() }
            assertTrue(noKey.single().error!!.message.contains("API Key"))

            val noModel = runBlocking { transport().stream(request(server.origin).copy(model = "  ")).toList() }
            assertTrue(noModel.single().error!!.message.contains("模型"))

            assertTrue(server.requests.isEmpty())
        }
    }
}

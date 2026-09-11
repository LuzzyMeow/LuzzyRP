package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AnthropicTransport] 端到端单测（真 socket + 真 OkHttp）。
 *
 * 重点在于**头集与 URL 形态**——这两处与 OpenAI 路径不同，且是 wire fidelity 的
 * 硬要求（URL 原样、不追加 `/v1/messages`）。
 */
class AnthropicTransportTest {

    private fun request(baseUrl: String) = LlmRequest(
        messages = listOf(
            LlmMessage(role = LlmRole.SYSTEM, content = "你是助手"),
            LlmMessage(role = LlmRole.USER, content = "几点了"),
        ),
        protocol = "anthropic",
        baseUrl = baseUrl,
        apiKey = "sk-ant-test",
        model = "claude-3-5-sonnet",
        temperature = 0.8,
        maxTokens = 8192,
        reasoningEffort = "medium",
    )

    private fun transport(logs: MutableList<String> = mutableListOf()) =
        AnthropicTransport(log = { logs += it }, sleep = { })

    @Test
    fun `头集与 JS fetch 一致且 URL 原样`() {
        RawSseServer(listOf(RawSseServer.Script(body = RawSseServer.sseNoDone("""{"type":"message_stop"}""")))).use { server ->
            runBlocking {
                withTimeout(10_000) {
                    transport().stream(request(server.url("/proxy/anthropic"))).toList()
                }
            }
            val raw = server.requests.single()
            assertTrue(raw.startsWith("POST /proxy/anthropic HTTP/1.1"))
            assertTrue(raw.contains("Content-Type: application/json"))
            assertTrue(raw.contains("x-api-key: sk-ant-test"))
            assertTrue(raw.contains("anthropic-version: 2023-06-01"))
            assertTrue(raw.contains("anthropic-dangerous-direct-browser-access: true"))
            // 不追加路径：不该出现 /v1/messages
            assertFalse(raw.startsWith("POST /proxy/anthropic/v1/messages"))
        }
    }

    @Test
    fun `请求体键序与 thinking 预算保真`() {
        RawSseServer(listOf(RawSseServer.Script(body = RawSseServer.sseNoDone("""{"type":"message_stop"}""")))).use { server ->
            runBlocking {
                withTimeout(10_000) { transport().stream(request(server.url())).toList() }
            }
            val body = server.requests.single().substringAfter("\r\n\r\n")
            assertEquals(
                """{"model":"claude-3-5-sonnet","max_tokens":8192,"system":"你是助手",""" +
                    """"messages":[{"role":"user","content":"几点了"}],"temperature":0.8,""" +
                    """"thinking":{"type":"enabled","budget_tokens":6144},"stream":true}""",
                body,
            )
        }
    }

    @Test
    fun `流式正文 思考 工具分片与用量端到端`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = RawSseServer.sseNoDone(
                        """{"type":"message_start","message":{"usage":{"input_tokens":9,"output_tokens":1}}}""",
                        """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"想想"}}""",
                        """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你"}}""",
                        """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"好"}}""",
                        """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu_1","name":"get_time"}}""",
                        """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"tz\":"}}""",
                        """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"UTC\"}"}}""",
                        """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"input_tokens":9,"output_tokens":21}}""",
                        """{"type":"message_stop"}""",
                    )
                )
            )
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.url())).toList() } }
            assertEquals("你好", deltas.mapNotNull { it.content }.joinToString(""))
            assertEquals("想想", deltas.mapNotNull { it.reasoning }.joinToString(""))
            assertEquals("tool_use", deltas.mapNotNull { it.finishReason }.last())
            val calls = ToolCallAccumulator().apply { deltas.forEach { accept(it.toolCalls) } }.build()
            assertEquals(1, calls.size)
            assertEquals("get_time", calls[0].name)
            assertEquals("UTC", calls[0].arguments["tz"]!!.jsonPrimitive.content)
            assertEquals(21, deltas.mapNotNull { it.usage }.last().output)
        }
    }

    @Test
    fun `错误帧转 error 且 overloaded 可重试`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    status = 200,
                    body = RawSseServer.sseNoDone("""{"type":"error","error":{"type":"overloaded_error","message":"忙"}}"""),
                )
            )
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.url())).toList() } }
            val error = deltas.single().error!!
            assertTrue(error.retryable)
            assertEquals("忙", error.message)
        }
    }

    @Test
    fun `400 不重试且密钥不进日志`() {
        RawSseServer(listOf(RawSseServer.Script(status = 400, body = """{"error":{"message":"bad key sk-ant-test"}}"""))).use { server ->
            val logs = mutableListOf<String>()
            val deltas = runBlocking { withTimeout(10_000) { transport(logs).stream(request(server.url())).toList() } }
            assertEquals(1, server.requests.size)
            val error = deltas.single().error!!
            assertFalse(error.retryable)
            assertFalse(error.message.contains("sk-ant-test"))
            assertTrue(logs.none { it.contains("sk-ant-test") })
        }
    }

    @Test
    fun `未配置 API Key 不发请求`() {
        RawSseServer(listOf(RawSseServer.Script(body = RawSseServer.sseNoDone()))).use { server ->
            val deltas = runBlocking { transport().stream(request(server.url()).copy(apiKey = "")).toList() }
            assertTrue(server.requests.isEmpty())
            assertTrue(deltas.single().error!!.message.contains("API Key"))
        }
    }

    @Test
    fun `未配置 Base URL 不发请求`() {
        val deltas = runBlocking { transport().stream(request("  ")).toList() }
        assertTrue(deltas.single().error!!.message.contains("Base URL"))
    }
}

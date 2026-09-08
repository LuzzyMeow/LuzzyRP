package com.luzzymeow.luzzyrp.assistant.domain.llm

import java.util.concurrent.TimeUnit
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OpenAiTransport] + [SseClient] 端到端单测（真 socket + 真 OkHttp）。
 *
 * 覆盖：流式正文 / 思考 / 工具分片 / usage、重试与退避边界、HTTP 错误不重试、
 * 取消关闭连接、空闲超时、以及「密钥只进 Authorization 头、不进日志」。
 */
class OpenAiTransportTest {

    private fun request(baseUrl: String) = LlmRequest(
        messages = listOf(
            LlmMessage(role = LlmRole.SYSTEM, content = "系统"),
            LlmMessage(role = LlmRole.USER, content = "现在几点"),
        ),
        protocol = "openai",
        baseUrl = baseUrl,
        apiKey = "sk-test-not-real",
        model = "gpt-test",
        tools = listOf(ToolRegistry.toolSchema(FakeSchemaTool)),
    )

    private fun transport(logs: MutableList<String> = mutableListOf()) =
        OpenAiTransport(log = { logs += it }, sleep = { /* 单测不真等 */ })

    @Test
    fun `流式正文 思考 工具分片与 usage 端到端`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = RawSseServer.sse(
                        """{"choices":[{"index":0,"delta":{"content":"你"},"finish_reason":null}]}""",
                        """{"choices":[{"index":0,"delta":{"reasoning_content":"先想一下"}}]}""",
                        """{"choices":[{"index":0,"delta":{"content":"好"},"finish_reason":null}]}""",
                        """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"get_time","arguments":"{\"tz\":"}}]}}]}""",
                        """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"UTC\"}"}}]},"finish_reason":"tool_calls"}]}""",
                        """{"choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7}}""",
                    )
                )
            )
        ).use { server ->
            val logs = mutableListOf<String>()
            val deltas = runBlocking {
                withTimeout(10_000) { transport(logs).stream(request(server.url)).toList() }
            }

            assertEquals("你好", deltas.mapNotNull { it.content }.joinToString(""))
            assertEquals("先想一下", deltas.mapNotNull { it.reasoning }.joinToString(""))
            val accumulator = ToolCallAccumulator().apply { deltas.forEach { accept(it.toolCalls) } }
            val calls = accumulator.build()
            assertEquals(1, calls.size)
            assertEquals("get_time", calls[0].name)
            assertEquals("UTC", calls[0].arguments["tz"]!!.jsonPrimitive.content)
            assertEquals("tool_calls", deltas.mapNotNull { it.finishReason }.lastOrNull())
            val usage = deltas.mapNotNull { it.usage }.single()
            assertEquals(11, usage.input)
            assertEquals(7, usage.output)

            // 密钥只进 Authorization 头
            assertTrue(server.requests.single().contains("Authorization: Bearer sk-test-not-real"))
            // …且不进任何日志
            assertTrue(logs.none { it.contains("sk-test-not-real") })
        }
    }

    @Test
    fun `429 重试一次后成功`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(status = 429, contentType = "application/json", body = """{"error":{"message":"rate limited"}}"""),
                RawSseServer.Script(body = RawSseServer.sse("""{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""")),
            )
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.url)).toList() } }
            assertEquals(2, server.requests.size)
            assertEquals("ok", deltas.mapNotNull { it.content }.joinToString(""))
            assertTrue(deltas.all { it.error == null })
        }
    }

    @Test
    fun `5xx 最多重试 2 次后报错`() {
        RawSseServer(List(3) { RawSseServer.Script(status = 500, body = "boom") }).use { server ->
            val logs = mutableListOf<String>()
            val deltas = runBlocking { withTimeout(10_000) { transport(logs).stream(request(server.url)).toList() } }
            assertEquals(3, server.requests.size)
            val error = deltas.last().error
            assertNotNull(error)
            assertTrue(error!!.retryable)
            assertEquals(500, error.httpStatus)
            assertTrue(logs.any { it.contains("重试") })
        }
    }

    @Test
    fun `4xx 不重试`() {
        RawSseServer(listOf(RawSseServer.Script(status = 400, body = "bad request"))).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.url)).toList() } }
            assertEquals(1, server.requests.size)
            val error = deltas.single().error!!
            assertFalse(error.retryable)
            assertEquals(400, error.httpStatus)
        }
    }

    @Test
    fun `未配置 API Key 不发请求`() {
        RawSseServer(listOf(RawSseServer.Script(body = RawSseServer.sse()))).use { server ->
            val deltas = runBlocking {
                transport().stream(request(server.url).copy(apiKey = "")).toList()
            }
            assertTrue(server.requests.isEmpty())
            assertTrue(deltas.single().error!!.message.contains("API Key"))
            assertFalse(deltas.single().error!!.retryable)
        }
    }

    @Test
    fun `连接失败转可重试错误且不抛异常`() {
        // 127.0.0.1:1 通常无人监听
        val deltas = runBlocking {
            withTimeout(10_000) { transport().stream(request("http://127.0.0.1:1/v1")).toList() }
        }
        val error = deltas.single().error!!
        assertTrue(error.retryable)
        assertFalse(error.message.contains("sk-test-not-real"))
    }

    @Test
    fun `取消收集后不等待读取超时`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n\n",
                    keepOpenMs = 3_000,
                )
            )
        ).use { server ->
            val deltas = runBlocking {
                // 只取第一帧即取消上游；若取消没有关闭连接，这里会卡到读超时
                withTimeout(5_000) { transport().stream(request(server.url)).take(1).toList() }
            }
            assertEquals("a", deltas.single().content)
        }
    }

    @Test
    fun `空闲超时转可重试错误`() {
        RawSseServer(
            listOf(
                RawSseServer.Script(
                    body = "data: {\"choices\":[]}\n\n",
                    keepOpenMs = 2_000,
                )
            )
        ).use { server ->
            val client = SseClient(
                OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.MILLISECONDS)
                    .build()
            )
            val chunks = runBlocking {
                withTimeout(5_000) { client.post(server.url, mapOf("Authorization" to "Bearer sk-test-not-real"), "{}").toList() }
            }
            assertTrue(chunks.first() is SseChunk.Data)
            val failure = chunks.last() as SseChunk.Failed
            assertTrue(failure.error.retryable)
            assertFalse(failure.error.message.contains("sk-test-not-real"))
        }
    }

    @Test
    fun `HTTP 错误响应体中的密钥被脱敏`() {
        RawSseServer(
            listOf(RawSseServer.Script(status = 401, body = """{"error":{"message":"bad key sk-test-not-real"}}"""))
        ).use { server ->
            val deltas = runBlocking { withTimeout(10_000) { transport().stream(request(server.url)).toList() } }
            val message = deltas.single().error!!.message
            assertFalse(message.contains("sk-test-not-real"))
            assertTrue(message.contains("***"))
        }
    }

    /** 仅用于给请求体塞一个真实的工具声明（服务器不解析它）。 */
    private object FakeSchemaTool : Tool {
        override val name: String = "get_time"
        override val description: String = "返回当前时间"
        override val tier: ToolTier = ToolTier.T0_READ
        override val parameters: JsonObject = Schema.empty()
        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = ToolResult.Ok("12:00")
    }
}

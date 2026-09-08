package com.luzzymeow.luzzyrp.assistant.domain.llm

import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OpenAiWire] 单测：请求体映射、extraBody 覆盖保护、流帧解析。 */
class OpenAiWireTest {

    private fun request(
        tools: List<JsonObject> = emptyList(),
        extraBody: JsonObject? = null,
    ) = LlmRequest(
        messages = listOf(
            LlmMessage(role = LlmRole.SYSTEM, content = "系统"),
            LlmMessage(role = LlmRole.USER, content = "你好"),
        ),
        protocol = "openai",
        baseUrl = "https://api.example.com/v1",
        apiKey = "sk-test-not-real",
        model = "gpt-test",
        tools = tools,
        extraBody = extraBody,
    )

    // ---------- 请求体 ----------

    @Test
    fun `请求体包含 model messages tools stream 与 stream_options`() {
        val body = OpenAiWire.requestBody(
            request(
                tools = listOf(
                    buildJsonObject {
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject { put("name", JsonPrimitive("get_time")) })
                    }
                )
            )
        )
        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertEquals(2, body["messages"]!!.jsonArray.size)
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `采样参数按存在性映射`() {
        val body = OpenAiWire.requestBody(
            request().copy(
                temperature = 0.7f,
                topP = 0.9f,
                maxTokens = 2048,
                stop = listOf("\n\n"),
                reasoningEffort = "high",
            )
        )
        assertEquals(0.7f, body["temperature"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(0.9f, body["top_p"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals(2048, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(1, body["stop"]!!.jsonArray.size)
        assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `未设置的可选参数不出现在请求体`() {
        val body = OpenAiWire.requestBody(request())
        assertNull(body["temperature"])
        assertNull(body["top_p"])
        assertNull(body["max_tokens"])
        assertNull(body["stop"])
        assertNull(body["reasoning_effort"])
        assertNull(body["tools"])
    }

    @Test
    fun `requireTool 映射为 tool_choice required`() {
        val tool = buildJsonObject { put("type", JsonPrimitive("function")) }
        val body = OpenAiWire.requestBody(request(tools = listOf(tool)).copy(requireTool = true))
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
    }

    // ---------- extraBody 覆盖保护（PLAN §11.1） ----------

    @Test
    fun `extraBody 不得覆盖 model messages tools stream`() {
        val conflicts = mutableListOf<String>()
        val extra = buildJsonObject {
            put("model", JsonPrimitive("hacked-model"))
            put("messages", JsonArray(emptyList()))
            put("tools", JsonArray(emptyList()))
            put("stream", JsonPrimitive(false))
            put("temperature", JsonPrimitive(0.1))
            put("custom_field", JsonPrimitive("keep-me"))
        }
        val body = OpenAiWire.requestBody(request(extraBody = extra)) { conflicts += it }

        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertEquals(2, body["messages"]!!.jsonArray.size)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertEquals(setOf("model", "messages", "tools", "stream"), conflicts.toSet())
        // 非保护字段照常生效
        assertEquals(0.1f, body["temperature"]!!.jsonPrimitive.float, 0.0001f)
        assertEquals("keep-me", body["custom_field"]!!.jsonPrimitive.content)
    }

    @Test
    fun `受保护字段清单与实现一致`() {
        assertEquals(setOf("model", "messages", "tools", "stream"), OpenAiWire.PROTECTED_BODY_KEYS)
    }

    // ---------- 消息映射 ----------

    @Test
    fun `assistant 带 tool_calls 与原始参数串`() {
        val call = ToolCall(id = "call_1", name = "get_time", arguments = Schema.empty(), rawArguments = """{"tz":"UTC"}""")
        val json = OpenAiWire.messagesJson(listOf(LlmMessage(role = LlmRole.ASSISTANT, content = "", toolCalls = listOf(call))))
        val message = json.first().jsonObject
        assertEquals("assistant", message["role"]!!.jsonPrimitive.content)
        val toolCall = message["tool_calls"]!!.jsonArray.first().jsonObject
        assertEquals("call_1", toolCall["id"]!!.jsonPrimitive.content)
        assertEquals("function", toolCall["type"]!!.jsonPrimitive.content)
        assertEquals("get_time", toolCall["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("""{"tz":"UTC"}""", toolCall["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool 消息带 tool_call_id`() {
        val message = OpenAiWire.messagesJson(
            listOf(LlmMessage(role = LlmRole.TOOL, content = "12:00", toolCallId = "call_1"))
        ).first().jsonObject
        assertEquals("tool", message["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", message["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("12:00", message["content"]!!.jsonPrimitive.content)
        assertNull(message["name"])
    }

    @Test
    fun `system 与 user 映射为标准角色`() {
        val json = OpenAiWire.messagesJson(
            listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = "s"),
                LlmMessage(role = LlmRole.USER, content = "u"),
            )
        )
        assertEquals(listOf("system", "user"), json.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    // ---------- 流帧解析 ----------

    @Test
    fun `解析正文与结束原因`() {
        val delta = OpenAiWire.parseFrame(
            """{"choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":"stop"}]}"""
        )!!
        assertEquals("你好", delta.content)
        assertEquals("stop", delta.finishReason)
        assertNull(delta.reasoning)
        assertTrue(delta.toolCalls.isEmpty())
    }

    @Test
    fun `reasoning_content 与 reasoning 两种字段都识别`() {
        assertEquals(
            "思考A",
            OpenAiWire.parseFrame("""{"choices":[{"delta":{"reasoning_content":"思考A"}}]}""")!!.reasoning
        )
        assertEquals(
            "思考B",
            OpenAiWire.parseFrame("""{"choices":[{"delta":{"reasoning":"思考B"}}]}""")!!.reasoning
        )
    }

    @Test
    fun `工具调用增量分片按 index 透出`() {
        val first = OpenAiWire.parseFrame(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","function":{"name":"web_fetch","arguments":"{\"url\":"}}]}}]}"""
        )!!
        assertEquals(1, first.toolCalls.size)
        assertEquals(0, first.toolCalls[0].index)
        assertEquals("call_a", first.toolCalls[0].id)
        assertEquals("web_fetch", first.toolCalls[0].name)
        assertEquals("""{"url":""", first.toolCalls[0].argumentsChunk)

        val second = OpenAiWire.parseFrame(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"https://a.b\"}"}}]}}]}"""
        )!!
        assertNull(second.toolCalls[0].id)
        assertNull(second.toolCalls[0].name)
        assertEquals(""""https://a.b"}""", second.toolCalls[0].argumentsChunk)
    }

    @Test
    fun `usage 帧解析 prompt 与 completion tokens`() {
        val delta = OpenAiWire.parseFrame("""{"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":34}}""")!!
        assertEquals(12, delta.usage!!.input)
        assertEquals(34, delta.usage!!.output)
        assertNull(delta.content)
    }

    @Test
    fun `顶层 error 对象转为 LlmError 且限流可重试`() {
        val delta = OpenAiWire.parseFrame(
            """{"error":{"message":"Rate limit reached for gpt-test","type":"rate_limit_error","code":"rate_limit_exceeded"}}"""
        )!!
        assertEquals("error", delta.finishReason)
        assertTrue(delta.error!!.retryable)
        assertTrue(delta.error!!.message.contains("Rate limit"))
    }

    @Test
    fun `error 消息中的密钥被脱敏`() {
        val delta = OpenAiWire.parseFrame(
            """{"error":{"message":"Incorrect API key provided: sk-abcdef0123456789","type":"invalid_request_error"}}"""
        )!!
        assertFalse(delta.error!!.message.contains("sk-abcdef0123456789"))
        assertTrue(delta.error!!.message.contains("sk-***"))
        assertFalse(delta.error!!.retryable)
    }

    @Test
    fun `非 JSON 或非对象帧返回 null 不抛异常`() {
        assertNull(OpenAiWire.parseFrame(""))
        assertNull(OpenAiWire.parseFrame("   "))
        assertNull(OpenAiWire.parseFrame("not json"))
        assertNull(OpenAiWire.parseFrame("[1,2,3]"))
    }

    @Test
    fun `空 choices 帧安全`() {
        val delta = OpenAiWire.parseFrame("""{"choices":[]}""")!!
        assertNull(delta.content)
        assertNull(delta.finishReason)
        assertNull(delta.usage)
    }

    // ---------- URL ----------

    @Test
    fun `BaseUrl 拼接 chat completions`() {
        assertEquals("https://api.example.com/v1/chat/completions", OpenAiWire.chatCompletionsUrl("https://api.example.com/v1"))
        assertEquals("https://api.example.com/v1/chat/completions", OpenAiWire.chatCompletionsUrl("https://api.example.com/v1/"))
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiWire.chatCompletionsUrl("https://api.example.com/v1/chat/completions")
        )
        assertEquals("", OpenAiWire.chatCompletionsUrl("   "))
    }

    @Test
    fun `密钥不出现在请求体`() {
        val body = OpenAiWire.requestBody(request()).toString()
        assertFalse(body.contains("sk-test-not-real"))
    }
}

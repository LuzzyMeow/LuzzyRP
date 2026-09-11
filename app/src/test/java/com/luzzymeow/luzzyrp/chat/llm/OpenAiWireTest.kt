package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OpenAiWire] 单测：请求体映射、键序、extraBody 展开语义、流帧解析。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改写：
 * - 原「受保护字段被忽略」的断言改为 **JS 对象展开语义**（后写覆盖先写）；
 * - 原「BaseUrl 拼接 /chat/completions」的断言**删除**（v2.0 契约要求原样 POST）；
 * - 新增 messages 原样转发（保键序、保未知字段）与 replyInTool / tool_choice 断言。
 */
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
        baseUrl = "https://api.example.com/v1/chat/completions",
        apiKey = "sk-test-not-real",
        model = "gpt-test",
        tools = tools,
        extraBody = extraBody,
    )

    private fun tool(name: String) = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put("function", buildJsonObject { put("name", JsonPrimitive(name)) })
    }

    // ---------- 请求体 ----------

    @Test
    fun `请求体包含 model messages tools stream 与 stream_options`() {
        val body = OpenAiWire.requestBody(request(tools = listOf(tool("get_time"))))
        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertEquals(2, body["messages"]!!.jsonArray.size)
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `采样参数按存在性映射`() {
        val body = OpenAiWire.requestBody(
            request().copy(temperature = 0.7, maxTokens = 2048, reasoningEffort = "high")
        )
        assertEquals("0.7", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("2048", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `未设置的可选参数不出现在请求体`() {
        val body = OpenAiWire.requestBody(request())
        assertNull(body["temperature"])
        assertNull(body["max_tokens"])
        assertNull(body["reasoning_effort"])
        assertNull(body["tools"])
        assertNull(body["tool_choice"])
        assertNull(body["parallel_tool_calls"])
    }

    @Test
    fun `非流式不带 stream_options`() {
        val body = OpenAiWire.requestBody(request().copy(stream = false))
        assertEquals("false", body["stream"]!!.jsonPrimitive.content)
        assertNull(body["stream_options"])
    }

    // ---------- 键序（wire fidelity 的核心） ----------

    @Test
    fun `请求体键序固定`() {
        val body = OpenAiWire.requestBody(
            request(tools = listOf(tool("get_time")), extraBody = buildJsonObject { put("top_k", 40) })
                .copy(temperature = 0.8, maxTokens = 8192, reasoningEffort = "medium")
        )
        assertEquals(
            listOf(
                "model", "messages", "temperature", "reasoning_effort", "max_tokens",
                "top_k", "tools", "tool_choice", "parallel_tool_calls", "stream", "stream_options",
            ),
            body.keys.toList(),
        )
    }

    @Test
    fun `extraBody 用对象展开语义：覆盖前面的键、被后面的键覆盖`() {
        val extra = buildJsonObject {
            put("temperature", JsonPrimitive(0.1))   // 覆盖前面的 temperature
            put("top_k", JsonPrimitive(40))          // 新增
            put("tools", JsonArray(emptyList()))     // 被后面的 tools 簇覆盖
            put("stream", JsonPrimitive(false))      // 被后面的 stream 覆盖
            put("stream_options", JsonPrimitive("nope")) // 被后面的 stream_options 覆盖
        }
        val body = OpenAiWire.requestBody(
            request(tools = listOf(tool("get_time")), extraBody = extra).copy(temperature = 0.8)
        )
        assertEquals("0.1", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("40", body["top_k"]!!.jsonPrimitive.content)
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean())
        // 覆盖**不改变键位**（JS 展开与 kotlinx put 的语义一致：都是原地赋值）
        assertEquals(
            listOf(
                "model", "messages", "temperature", "top_k", "tools",
                "stream", "stream_options", "tool_choice", "parallel_tool_calls",
            ),
            body.keys.toList(),
        )
    }

    @Test
    fun `messages 原样转发：键序与未知字段都不丢`() {
        val raw = """{"role":"assistant","content":"","reasoning_content":"想过","reasoning_details":[{"x":1}],"tool_calls":[{"id":"c1","type":"function","function":{"name":"t","arguments":"{}"},"extra_content":{"g":1}}]}"""
        val message = JsonLenient.json.parseToJsonElement(raw) as JsonObject
        val json = OpenAiWire.messagesJson(listOf(LlmMessage(role = LlmRole.ASSISTANT, raw = message)))
        assertEquals(
            """[{"role":"assistant","content":"","reasoning_content":"想过","reasoning_details":[{"x":1}],"tool_calls":[{"id":"c1","type":"function","function":{"name":"t","arguments":"{}"},"extra_content":{"g":1}}]}]""",
            json.toString(),
        )
    }

    @Test
    fun `合成消息：assistant 带 tool_calls 且参数用原始串`() {
        val call = ToolCall(id = "call_1", name = "get_time", rawArguments = """{"tz":"UTC"}""")
        val json = OpenAiWire.messagesJson(
            listOf(LlmMessage(role = LlmRole.ASSISTANT, content = "", toolCalls = listOf(call)))
        )
        val message = json.first().jsonObject
        assertEquals("assistant", message["role"]!!.jsonPrimitive.content)
        val toolCall = message["tool_calls"]!!.jsonArray.first().jsonObject
        assertEquals(
            """{"id":"call_1","type":"function","function":{"name":"get_time","arguments":"{\"tz\":\"UTC\"}"}}""",
            toolCall.toString(),
        )
    }

    @Test
    fun `合成消息：tool 带 tool_call_id、system 与 user 映射标准角色`() {
        val toolMessage = OpenAiWire.messagesJson(
            listOf(LlmMessage(role = LlmRole.TOOL, content = "12:00", toolCallId = "call_1"))
        ).first().jsonObject
        assertEquals("tool", toolMessage["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", toolMessage["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("12:00", toolMessage["content"]!!.jsonPrimitive.content)
        assertNull(toolMessage["name"])

        val roles = OpenAiWire.messagesJson(
            listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = "s"),
                LlmMessage(role = LlmRole.USER, content = "u"),
            )
        ).map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user"), roles)
    }

    // ---------- 抗截断工具与 tool_choice ----------

    @Test
    fun `replyInTool 且用户已带工具时 tool_choice 为 required（同 JS 三元）`() {
        val body = OpenAiWire.requestBody(request(tools = listOf(tool("web_search"))).copy(replyInTool = true))
        val tools = body["tools"]!!.jsonArray
        assertEquals(2, tools.size)
        assertEquals("web_search", tools[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("output_reply", tools[1].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("false", body["parallel_tool_calls"]!!.jsonPrimitive.content)
    }

    @Test
    fun `requireTool 时不再追加 output_reply 且 tool_choice 为 required`() {
        val body = OpenAiWire.requestBody(
            request(tools = listOf(tool("web_search"))).copy(replyInTool = true, requireTool = true)
        )
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun `有工具但既不 require 也不 reply 时 tool_choice 为 auto`() {
        val body = OpenAiWire.requestBody(request(tools = listOf(tool("web_search"))))
        assertEquals("auto", body["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("false", body["parallel_tool_calls"]!!.jsonPrimitive.content)
    }

    @Test
    fun `无工具时完全不出现 tools 簇`() {
        val body = OpenAiWire.requestBody(request().copy(replyInTool = false, requireTool = true))
        assertNull(body["tools"])
        assertNull(body["tool_choice"])
    }

    @Test
    fun `replyInTool 单独也能触发 tools 簇（只有 output_reply）`() {
        val body = OpenAiWire.requestBody(request().copy(replyInTool = true))
        assertEquals(1, body["tools"]!!.jsonArray.size)
        assertEquals(
            """{"type":"function","function":{"name":"output_reply"}}""",
            body["tool_choice"]!!.toString(),
        )
    }

    @Test
    fun `output_reply 定义与 JS replyTool 同构`() {
        val function = ReplyTool.definition["function"]!!.jsonObject
        assertEquals("output_reply", function["name"]!!.jsonPrimitive.content)
        val parameters = function["parameters"]!!.jsonObject
        assertEquals("object", parameters["type"]!!.jsonPrimitive.content)
        assertEquals("string", parameters["properties"]!!.jsonObject["content"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("content", parameters["required"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("false", parameters["additionalProperties"]!!.jsonPrimitive.content)
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
    fun `usage 帧解析并保留原始对象`() {
        val delta = OpenAiWire.parseFrame(
            """{"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":34,"prompt_tokens_details":{"cached_tokens":5}}}"""
        )!!
        assertEquals(12, delta.usage!!.input)
        assertEquals(34, delta.usage!!.output)
        assertNull(delta.content)
        assertEquals(
            """{"prompt_tokens":12,"completion_tokens":34,"prompt_tokens_details":{"cached_tokens":5}}""",
            delta.rawUsage.toString(),
        )
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

    @Test
    fun `忽略 stream 的网关返回整包 message 也能解析`() {
        val delta = OpenAiWire.parseFrame(
            """{"choices":[{"message":{"content":"整包回答","tool_calls":[{"id":"c1","function":{"name":"get_time","arguments":"{}"}}]},"finish_reason":"stop"}]}"""
        )!!
        assertEquals("整包回答", delta.content)
        assertEquals("stop", delta.finishReason)
        assertEquals("get_time", delta.toolCalls.single().name)
        assertEquals(0, delta.toolCalls.single().index)
    }

    // ---------- URL 与密钥 ----------

    @Test
    fun `端点原样使用 baseUrl（不再拼接路径）`() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiWire.messagesEndpoint(request()),
        )
        assertEquals(
            "https://gateway.local/anything/else",
            OpenAiWire.messagesEndpoint(request().copy(baseUrl = "  https://gateway.local/anything/else  ")),
        )
        assertEquals("", OpenAiWire.messagesEndpoint(request().copy(baseUrl = "   ")))
    }

    @Test
    fun `密钥不出现在请求体`() {
        val body = OpenAiWire.requestBody(request()).toString()
        assertFalse(body.contains("sk-test-not-real"))
        assertNotNull(body)
    }
}

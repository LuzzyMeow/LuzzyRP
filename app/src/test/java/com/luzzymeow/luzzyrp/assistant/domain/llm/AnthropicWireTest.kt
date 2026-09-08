package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anthropic Messages 线协议单测（PLAN §5.3）。 */
class AnthropicWireTest {

    private fun request(
        messages: List<LlmMessage> = listOf(LlmMessage(LlmRole.SYSTEM, "你是助手")),
        tools: List<JsonObject> = emptyList(),
        maxTokens: Int? = null,
        extraBody: JsonObject? = null,
        requireTool: Boolean = false,
    ) = LlmRequest(
        messages = messages,
        protocol = "anthropic",
        baseUrl = "https://api.anthropic.com",
        apiKey = "sk-secret",
        model = "claude-3-5-sonnet",
        maxTokens = maxTokens,
        tools = tools,
        extraBody = extraBody,
        requireTool = requireTool,
    )

    @Test
    fun `端点补全 v1 messages`() {
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicWire.messagesUrl("https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicWire.messagesUrl("https://api.anthropic.com/"))
        assertEquals(
            "https://proxy/v1/messages",
            AnthropicWire.messagesUrl("https://proxy/v1/messages"),
        )
        assertEquals("", AnthropicWire.messagesUrl("  "))
    }

    @Test
    fun `system 提到顶层且不进 messages`() {
        val body = AnthropicWire.requestBody(request())
        assertEquals("你是助手", body["system"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertTrue(messages.none { (it as JsonObject)["role"]!!.jsonPrimitive.content == "system" })
    }

    @Test
    fun `max_tokens 缺省补 4096`() {
        val body = AnthropicWire.requestBody(request())
        assertEquals(AnthropicWire.DEFAULT_MAX_TOKENS, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        val explicit = AnthropicWire.requestBody(request(maxTokens = 1024))
        assertEquals(1024, explicit["max_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `工具 schema 转为 input_schema 形态`() {
        val tool = buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "get_time")
                put("description", "取时间")
                put("parameters", buildJsonObject { put("type", "object") })
            })
        }
        val body = AnthropicWire.requestBody(request(tools = listOf(tool)))
        val tools = body["tools"]!!.jsonArray
        val first = tools[0].jsonObject
        assertEquals("get_time", first["name"]!!.jsonPrimitive.content)
        assertEquals("取时间", first["description"]!!.jsonPrimitive.content)
        assertEquals("object", first["input_schema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(first["type"])
    }

    @Test
    fun `requireTool 生成 tool_choice any`() {
        val tool = buildJsonObject { put("function", buildJsonObject { put("name", "t") }) }
        val body = AnthropicWire.requestBody(request(tools = listOf(tool), requireTool = true))
        assertEquals("any", body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `extraBody 覆盖受保护字段被忽略并回调`() {
        val conflicts = mutableListOf<String>()
        val body = AnthropicWire.requestBody(
            request(extraBody = buildJsonObject {
                put("model", "hacked")
                put("max_tokens", 1)
                put("top_k", 40)
            }),
        ) { conflicts += it }
        assertEquals("claude-3-5-sonnet", body["model"]!!.jsonPrimitive.content)
        assertEquals(AnthropicWire.DEFAULT_MAX_TOKENS, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(40, body["top_k"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf("model", "max_tokens"), conflicts)
    }

    @Test
    fun `assistant 工具调用映射为 tool_use block`() {
        val messages = listOf(
            LlmMessage(LlmRole.USER, "几点了"),
            LlmMessage(
                LlmRole.ASSISTANT,
                content = "我查一下",
                toolCalls = listOf(
                    com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall(
                        id = "tu_1",
                        name = "get_time",
                        arguments = buildJsonObject { put("timezone", "Asia/Shanghai") },
                    )
                ),
            ),
        )
        val json = AnthropicWire.messagesJsonForTest(messages)
        val assistant = json.first { it.jsonObject["role"]!!.jsonPrimitive.content == "assistant" }.jsonObject
        val blocks = assistant["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        val toolUse = blocks[1].jsonObject
        assertEquals("tool_use", toolUse["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", toolUse["id"]!!.jsonPrimitive.content)
        assertEquals("Asia/Shanghai", toolUse["input"]!!.jsonObject["timezone"]!!.jsonPrimitive.content)
    }

    @Test
    fun `工具结果映射为 user 消息里的 tool_result`() {
        val messages = listOf(
            LlmMessage(LlmRole.TOOL, content = "2026-09-09 10:00", toolCallId = "tu_1", name = "get_time"),
        )
        val json = AnthropicWire.messagesJsonForTest(messages)
        val user = json[0].jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        val block = user["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", block["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", block["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `文本与思考与工具参数增量解析`() {
        val text = parseAnthropicFrame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}""")
        assertEquals("你好", text!!.content)
        val thinking = parseAnthropicFrame(
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"想想"}}"""
        )
        assertEquals("想想", thinking!!.reasoning)
        val args = parseAnthropicFrame(
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"a\":"}}"""
        )
        assertEquals("{\"a\":", args!!.toolCalls[0].argumentsChunk)
        assertEquals(1, args.toolCalls[0].index)
    }

    @Test
    fun `tool_use 起始帧带 id 与名字`() {
        val delta = parseAnthropicFrame(
            """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"tu_9","name":"web_fetch"}}"""
        )
        val call = delta!!.toolCalls[0]
        assertEquals("tu_9", call.id)
        assertEquals("web_fetch", call.name)
        assertEquals(2, call.index)
    }

    @Test
    fun `用量与结束原因解析`() {
        val delta = parseAnthropicFrame(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":12,"output_tokens":34}}"""
        )
        assertEquals(12, delta!!.usage!!.input)
        assertEquals(34, delta.usage!!.output)
        assertEquals("end_turn", delta.finishReason)
    }

    @Test
    fun `错误帧转 LlmDelta error 且 overloaded 可重试`() {
        val delta = parseAnthropicFrame("""{"type":"error","error":{"type":"overloaded_error","message":"忙"}}""")
        assertTrue(delta!!.error!!.retryable)
        assertEquals("忙", delta.error!!.message)
        val fatal = parseAnthropicFrame("""{"type":"error","error":{"type":"invalid_request_error","message":"坏请求"}}""")
        assertEquals(false, fatal!!.error!!.retryable)
    }

    @Test
    fun `无关帧返回 null`() {
        assertNull(parseAnthropicFrame("""{"type":"ping"}"""))
        assertNull(parseAnthropicFrame("not json"))
    }
}

/** 暴露 internal messagesJson 供测试（测试类与 Wire 同包，可直接调用）。 */
internal fun AnthropicWire.messagesJsonForTest(messages: List<LlmMessage>): JsonArray = messagesJson(messages)

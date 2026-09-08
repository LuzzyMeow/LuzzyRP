package com.luzzymeow.luzzyrp.assistant.domain.llm

import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
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

/** Gemini streamGenerateContent 线协议单测（PLAN §5.3）。 */
class GeminiWireTest {

    private fun request(
        messages: List<LlmMessage> = listOf(LlmMessage(LlmRole.SYSTEM, "你是助手")),
        tools: List<JsonObject> = emptyList(),
        extraBody: JsonObject? = null,
    ) = LlmRequest(
        messages = messages,
        protocol = "gemini",
        baseUrl = "https://generativelanguage.googleapis.com",
        apiKey = "AIza-secret",
        model = "gemini-2.0-flash",
        temperature = 0.7f,
        maxTokens = 2048,
        tools = tools,
        extraBody = extraBody,
    )

    @Test
    fun `端点补 v1beta 与 alt=sse`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse",
            GeminiWire.streamUrl("https://generativelanguage.googleapis.com", "gemini-2.0-flash"),
        )
        assertEquals(
            "https://proxy/v1beta/models/m:streamGenerateContent?alt=sse",
            GeminiWire.streamUrl("https://proxy/v1beta", "m"),
        )
        assertEquals("", GeminiWire.streamUrl("", "m"))
        assertEquals("", GeminiWire.streamUrl("https://x", " "))
    }

    @Test
    fun `system 进 systemInstruction 且不在 contents`() {
        val body = GeminiWire.requestBody(request())
        val text = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!
        assertEquals("你是助手", text.jsonPrimitive.content)
        val contents = body["contents"]!!.jsonArray
        assertTrue(contents.none { (it as JsonObject)["role"]?.jsonPrimitive?.content == "system" })
    }

    @Test
    fun `采样参数进 generationConfig`() {
        val config = GeminiWire.requestBody(request())["generationConfig"]!!.jsonObject
        assertEquals("0.7", config["temperature"]!!.jsonPrimitive.content)
        assertEquals("2048", config["maxOutputTokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `工具 schema 转 functionDeclarations`() {
        val tool = buildJsonObject {
            put("function", buildJsonObject {
                put("name", "memory_search")
                put("description", "查记忆")
                put("parameters", buildJsonObject { put("type", "object") })
            })
        }
        val body = GeminiWire.requestBody(request(tools = listOf(tool)))
        val decl = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("memory_search", decl["name"]!!.jsonPrimitive.content)
        assertEquals("查记忆", decl["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `extraBody 覆盖受保护字段被忽略`() {
        val conflicts = mutableListOf<String>()
        val body = GeminiWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, "你是助手"),
                    LlmMessage(LlmRole.USER, "你好"),
                ),
                extraBody = buildJsonObject { put("contents", "hack"); put("safetySettings", "x") },
            ),
        ) { conflicts += it }
        assertEquals(listOf("contents"), conflicts)
        // contents 仍是原样映射（user 一条），未被 extraBody 覆盖
        assertEquals(1, body["contents"]!!.jsonArray.size)
        assertEquals("你好", body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant 工具调用与工具结果映射`() {
        val messages = listOf(
            LlmMessage(
                LlmRole.ASSISTANT,
                content = "查询中",
                toolCalls = listOf(ToolCall("call_0_get_time", "get_time", buildJsonObject { put("timezone", "UTC") })),
            ),
            LlmMessage(LlmRole.TOOL, content = "10:00", name = "get_time"),
        )
        val contents = contentsJson(messages)
        val model = contents[0].jsonObject
        assertEquals("model", model["role"]!!.jsonPrimitive.content)
        val callPart = model["parts"]!!.jsonArray[1].jsonObject["functionCall"]!!.jsonObject
        assertEquals("get_time", callPart["name"]!!.jsonPrimitive.content)
        assertEquals("UTC", callPart["args"]!!.jsonObject["timezone"]!!.jsonPrimitive.content)

        val user = contents[1].jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        val response = user["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("get_time", response["name"]!!.jsonPrimitive.content)
        assertEquals("10:00", response["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
    }

    @Test
    fun `文本帧解析`() {
        val delta = parseGeminiFrame(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"你好"}]},"finishReason":"STOP"}]}"""
        )
        assertEquals("你好", delta!!.content)
        assertEquals("STOP", delta.finishReason)
    }

    @Test
    fun `思考 part 走 reasoning 通道`() {
        val delta = parseGeminiFrame(
            """{"candidates":[{"content":{"parts":[{"text":"想想","thought":true},{"text":"答案"}]}}]}"""
        )
        assertEquals("想想", delta!!.reasoning)
        assertEquals("答案", delta.content)
    }

    @Test
    fun `functionCall 合成稳定 id 并带参数`() {
        val delta = parseGeminiFrame(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"web_fetch","args":{"url":"https://a"}}}]}}]}""",
            callIndexBase = 3,
        )
        val call = delta!!.toolCalls[0]
        assertEquals("call_3_web_fetch", call.id)
        assertEquals("web_fetch", call.name)
        assertEquals("{\"url\":\"https://a\"}", call.argumentsChunk)
    }

    @Test
    fun `用量元数据解析`() {
        val delta = parseGeminiFrame(
            """{"candidates":[{"content":{"parts":[{"text":"x"}]}}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":22}}"""
        )
        assertEquals(11, delta!!.usage!!.input)
        assertEquals(22, delta.usage!!.output)
    }

    @Test
    fun `错误帧与非法帧处理`() {
        val delta = parseGeminiFrame("""{"error":{"status":"UNAVAILABLE","message":"过载"}}""")
        assertTrue(delta!!.error!!.retryable)
        assertNull(parseGeminiFrame("not json"))
    }
}

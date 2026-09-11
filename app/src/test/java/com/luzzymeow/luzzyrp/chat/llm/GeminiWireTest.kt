package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemini 线协议单测。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改写：端点形态改为
 * `?key=` 查询串（JS 同式）、断言对象改为 OpenAI 形态消息、补齐
 * encodeUriComponent 与 thinking 预算映射。
 */
class GeminiWireTest {

    private fun request(
        messages: List<LlmMessage> = listOf(LlmMessage(LlmRole.SYSTEM, "你是助手")),
        tools: List<JsonObject> = emptyList(),
        extraBody: JsonObject? = null,
        temperature: Double? = 0.7,
        maxTokens: Int? = 2048,
        stream: Boolean = true,
        reasoningEffort: String? = null,
    ) = LlmRequest(
        messages = messages,
        protocol = "gemini",
        baseUrl = "https://generativelanguage.googleapis.com",
        apiKey = "AIza-secret",
        model = "gemini-2.0-flash",
        temperature = temperature,
        maxTokens = maxTokens,
        stream = stream,
        tools = tools,
        extraBody = extraBody,
        reasoningEffort = reasoningEffort,
    )

    private fun openAi(raw: String): LlmMessage =
        LlmMessage(role = LlmRole.USER, raw = JsonLenient.json.parseToJsonElement(raw) as JsonObject)

    // ---------- 端点 ----------

    @Test
    fun `端点形态与 JS 一致`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=AIza-secret",
            GeminiWire.endpoint("https://generativelanguage.googleapis.com", "gemini-2.0-flash", "AIza-secret", true),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIza-secret",
            GeminiWire.endpoint("https://generativelanguage.googleapis.com", "gemini-2.0-flash", "AIza-secret", false),
        )
    }

    @Test
    fun `base 尾部斜杠被剥掉且空值返回空串`() {
        assertEquals(
            "https://proxy/v1beta/models/m:streamGenerateContent?alt=sse&key=k",
            GeminiWire.endpoint("https://proxy///", "m", "k", true),
        )
        assertEquals("", GeminiWire.endpoint("", "m", "k", true))
        assertEquals("", GeminiWire.endpoint("https://x", " ", "k", true))
    }

    @Test
    fun `encodeUriComponent 与 JS 语义一致`() {
        // 未保留字符集：A-Za-z0-9 - _ . ! ~ * ' ( )
        assertEquals("aZ0-_.!~*'()", encodeUriComponent("aZ0-_.!~*'()"))
        // 空格编成 %20（不是 URLEncoder 的 +）
        assertEquals("a%20b", encodeUriComponent("a b"))
        assertEquals("%2F", encodeUriComponent("/"))
        assertEquals("%26", encodeUriComponent("&"))
        assertEquals("%E4%B8%AD", encodeUriComponent("中"))
    }

    @Test
    fun `模型名与密钥被正确转义`() {
        assertEquals(
            "https://x/v1beta/models/a%2Fb:streamGenerateContent?alt=sse&key=k%26v",
            GeminiWire.endpoint("https://x", "a/b", "k&v", true),
        )
    }

    // ---------- 请求体 ----------

    @Test
    fun `system 进 systemInstruction 且不在 contents`() {
        val body = GeminiWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, "你是助手"),
                    LlmMessage(LlmRole.USER, "你好"),
                )
            )
        )
        assertEquals(
            "你是助手",
            body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(1, body["contents"]!!.jsonArray.size)
    }

    @Test
    fun `无系统提示时不出现 systemInstruction`() {
        val body = GeminiWire.requestBody(request(messages = listOf(LlmMessage(LlmRole.USER, "你好"))))
        assertNull(body["systemInstruction"])
    }

    @Test
    fun `采样参数进 generationConfig 且缺省键不出现`() {
        val config = GeminiWire.requestBody(request())["generationConfig"]!!.jsonObject
        assertEquals("0.7", config["temperature"]!!.jsonPrimitive.content)
        assertEquals("2048", config["maxOutputTokens"]!!.jsonPrimitive.content)
        assertNull(config["thinkingConfig"])

        val bare = GeminiWire.requestBody(
            request(messages = listOf(LlmMessage(LlmRole.USER, "hi")), temperature = null, maxTokens = null)
        )["generationConfig"]!!.jsonObject
        assertTrue(bare.isEmpty())
    }

    @Test
    fun `reasoningEffort 映射 thinkingBudget（与 JS 表同值）`() {
        fun budget(effort: String) = GeminiWire.requestBody(request(reasoningEffort = effort))["generationConfig"]!!
            .jsonObject["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.content.toInt()

        assertEquals(1024, budget("low"))
        assertEquals(8192, budget("medium"))
        assertEquals(24576, budget("high"))
        assertEquals(32768, budget("max"))
    }

    @Test
    fun `未知 reasoningEffort 不注入 thinkingConfig`() {
        val config = GeminiWire.requestBody(request(reasoningEffort = "very-high"))["generationConfig"]!!.jsonObject
        assertNull(config["thinkingConfig"])
        assertEquals(GeminiWire.THINKING_BUDGETS.keys, setOf("low", "medium", "high", "max"))
    }

    @Test
    fun `请求体键序固定且 extraBody 展开在最后`() {
        val body = GeminiWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, "s"),
                    LlmMessage(LlmRole.USER, "u"),
                ),
                extraBody = buildJsonObject { put("safetySettings", "x") },
            )
        )
        assertEquals(listOf("contents", "systemInstruction", "generationConfig", "safetySettings"), body.keys.toList())
    }

    @Test
    fun `extraBody 展开在最后可覆盖 generationConfig`() {
        val body = GeminiWire.requestBody(
            request(
                messages = listOf(LlmMessage(LlmRole.USER, "u")),
                extraBody = buildJsonObject { put("generationConfig", buildJsonObject { put("topK", 20) }) },
            )
        )
        assertEquals("20", body["generationConfig"]!!.jsonObject["topK"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Gemini 不发送 tools（与 JS 适配器一致）`() {
        val body = GeminiWire.requestBody(
            request(tools = listOf(buildJsonObject { put("type", "function") }))
        )
        assertNull(body["tools"])
    }

    // ---------- 消息转换 ----------

    @Test
    fun `assistant 角色映射为 model`() {
        val messages = listOf(
            openAi("""{"role":"system","content":"s"}"""),
            openAi("""{"role":"user","content":"问"}"""),
            openAi("""{"role":"assistant","content":"答"}"""),
        )
        val (contents, system) = GeminiWire.toGeminiContents(messages)
        assertEquals("s", system!!["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(listOf("user", "model"), contents.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun `工具结果消息降级为 user 的纯文本 part`() {
        val (contents, _) = GeminiWire.toGeminiContents(
            listOf(openAi("""{"role":"tool","tool_call_id":"c1","content":"10:00"}"""))
        )
        val entry = contents[0].jsonObject
        assertEquals("user", entry["role"]!!.jsonPrimitive.content)
        assertEquals("10:00", entry["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `上游形态兜底：首条纯文本 user 视为 systemInstruction`() {
        val (contents, system) = GeminiWire.toGeminiContents(
            listOf(
                openAi("""{"role":"user","content":"（系统提示）"}"""),
                openAi("""{"role":"user","content":"你好"}"""),
            )
        )
        assertEquals("（系统提示）", system!!["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(1, contents.size)
    }

    @Test
    fun `content 数组转 text 与 inlineData，非 data URL 图片丢弃`() {
        val image = "data:image/jpeg;base64,QUJD"
        val (contents, _) = GeminiWire.toGeminiContents(
            listOf(
                openAi("""{"role":"user","content":[{"type":"text","text":"看图"},{"type":"image_url","image_url":{"url":"$image"}},{"type":"image_url","image_url":{"url":"https://x/y.png"}}]}""")
            )
        )
        val parts = contents[0].jsonObject["parts"]!!.jsonArray
        assertEquals(2, parts.size)
        assertEquals("看图", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val inline = parts[1].jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/jpeg", inline["mimeType"]!!.jsonPrimitive.content)
        assertEquals("QUJD", inline["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parts 为空的条目被整条丢弃`() {
        val (contents, _) = GeminiWire.toGeminiContents(
            listOf(
                openAi("""{"role":"user","content":[{"type":"image_url","image_url":{"url":"https://x/y.png"}}]}"""),
                openAi("""{"role":"user","content":"有内容"}"""),
            )
        )
        assertEquals(1, contents.size)
        assertEquals("有内容", contents[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `相邻同角色合并 parts`() {
        val (contents, _) = GeminiWire.toGeminiContents(
            listOf(
                openAi("""{"role":"assistant","content":"半句"}"""),
                openAi("""{"role":"assistant","content":"另半句"}"""),
            )
        )
        assertEquals(1, contents.size)
        assertEquals(
            listOf("半句", "另半句"),
            contents[0].jsonObject["parts"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `空消息列表产出空 contents 无系统提示`() {
        val (contents, system) = GeminiWire.toGeminiContents(emptyList())
        assertEquals(0, contents.size)
        assertNull(system)
    }

    // ---------- 流帧解析 ----------

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
    fun `用量元数据解析并保留原始对象`() {
        val delta = parseGeminiFrame(
            """{"candidates":[{"content":{"parts":[{"text":"x"}]}}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":22,"cachedContentTokenCount":3}}"""
        )
        assertEquals(11, delta!!.usage!!.input)
        assertEquals(22, delta.usage!!.output)
        assertTrue(delta.rawUsage.toString().contains("cachedContentTokenCount"))
    }

    @Test
    fun `错误帧与非法帧处理`() {
        val delta = parseGeminiFrame("""{"error":{"status":"UNAVAILABLE","message":"过载"}}""")
        assertTrue(delta!!.error!!.retryable)
        assertEquals("error", delta.finishReason)
        val fatal = parseGeminiFrame("""{"error":{"status":"INVALID_ARGUMENT","message":"参数错"}}""")
        assertFalse(fatal!!.error!!.retryable)
        assertNull(parseGeminiFrame("not json"))
        assertNull(parseGeminiFrame(""))
    }

    @Test
    fun `空 candidates 帧安全`() {
        val delta = parseGeminiFrame("""{"candidates":[]}""")!!
        assertNull(delta.content)
        assertNull(delta.finishReason)
    }
}

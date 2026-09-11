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
 * Anthropic Messages 线协议单测。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改写：断言对象从
 * [LlmMessage] 的合成键改为 **JS 同构的 OpenAI 形态消息**，并补齐
 * thinking 预算守卫边界、messages 转换（占位 / 合并 / 图片 / tool 角色降级）。
 */
class AnthropicWireTest {

    private fun request(
        messages: List<LlmMessage> = listOf(LlmMessage(LlmRole.SYSTEM, "你是助手")),
        tools: List<JsonObject> = emptyList(),
        maxTokens: Int? = null,
        stream: Boolean = true,
        temperature: Double? = null,
        reasoningEffort: String? = null,
        extraBody: JsonObject? = null,
        requireTool: Boolean = false,
    ) = LlmRequest(
        messages = messages,
        protocol = "anthropic",
        baseUrl = "https://api.anthropic.com",
        apiKey = "sk-secret",
        model = "claude-3-5-sonnet",
        temperature = temperature,
        maxTokens = maxTokens,
        stream = stream,
        tools = tools,
        extraBody = extraBody,
        reasoningEffort = reasoningEffort,
        requireTool = requireTool,
    )

    private fun openAi(raw: String): LlmMessage =
        LlmMessage(role = LlmRole.USER, raw = JsonLenient.json.parseToJsonElement(raw) as JsonObject)

    // ---------- 常量与顶层系统提示 ----------

    @Test
    fun `缺省 max_tokens 为 8192`() {
        assertEquals(8192, AnthropicWire.DEFAULT_MAX_TOKENS)
        val body = AnthropicWire.requestBody(request())
        assertEquals(8192, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(1024, AnthropicWire.requestBody(request(maxTokens = 1024))["max_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `system 提到顶层且不进 messages`() {
        val body = AnthropicWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, "你是助手"),
                    LlmMessage(LlmRole.USER, "你好"),
                )
            )
        )
        assertEquals("你是助手", body["system"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertTrue(messages.none { (it as JsonObject)["role"]!!.jsonPrimitive.content == "system" })
        assertEquals(1, messages.size)
    }

    @Test
    fun `无系统提示时不出现 system 键`() {
        val body = AnthropicWire.requestBody(request(messages = listOf(LlmMessage(LlmRole.USER, "你好"))))
        assertNull(body["system"])
    }

    @Test
    fun `上游形态兜底：首条纯文本 user 视为 system（总条数大于 1）`() {
        val body = AnthropicWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.USER, "（系统提示）你是助手"),
                    LlmMessage(LlmRole.USER, "你好"),
                )
            )
        )
        assertEquals("（系统提示）你是助手", body["system"]!!.jsonPrimitive.content)
        // 首条被吃掉后，剩余两条 user 相邻 → 合并成一条
        assertEquals(1, body["messages"]!!.jsonArray.size)
    }

    @Test
    fun `只有一条 user 消息时不当成 system`() {
        val body = AnthropicWire.requestBody(request(messages = listOf(LlmMessage(LlmRole.USER, "只有一句"))))
        assertNull(body["system"])
        assertEquals("只有一句", body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    // ---------- 键序 / extraBody ----------

    @Test
    fun `请求体键序固定`() {
        val body = AnthropicWire.requestBody(
            request(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, "你是助手"),
                    LlmMessage(LlmRole.USER, "你好"),
                ),
                temperature = 0.8,
                reasoningEffort = "medium",
                maxTokens = 8192,
                extraBody = buildJsonObject { put("top_k", 40) },
            )
        )
        assertEquals(
            listOf("model", "max_tokens", "system", "messages", "temperature", "thinking", "top_k", "stream"),
            body.keys.toList(),
        )
    }

    @Test
    fun `extraBody 展开在 stream 之前且 stream 恒胜出`() {
        val body = AnthropicWire.requestBody(
            request(
                messages = listOf(LlmMessage(LlmRole.USER, "你好")),
                stream = true,
                extraBody = buildJsonObject {
                    put("stream", false)
                    put("max_tokens", 1)
                    put("top_k", 40)
                },
            )
        )
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(40, body["top_k"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `非流式时 stream 为 false 且仍带该键`() {
        val body = AnthropicWire.requestBody(request(stream = false))
        assertEquals("false", body["stream"]!!.jsonPrimitive.content)
    }

    // ---------- thinking 预算守卫（与 JS anthropicThinkingConfig 同式） ----------

    @Test
    fun `thinking 仅在给了 reasoningEffort 时出现`() {
        assertNull(AnthropicWire.requestBody(request(reasoningEffort = null))["thinking"])
        assertNull(AnthropicWire.requestBody(request(reasoningEffort = ""))["thinking"])
        assertEquals(
            """{"type":"enabled","budget_tokens":6144}""",
            AnthropicWire.requestBody(request(reasoningEffort = "medium"))["thinking"].toString(),
        )
    }

    @Test
    fun `max_tokens 小于 2048 时不启用 thinking`() {
        assertNull(AnthropicWire.thinkingConfig(2047))
        assertNull(AnthropicWire.thinkingConfig(1024))
        assertEquals(1536, AnthropicWire.thinkingConfig(2048)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `max_tokens 为 0 或缺失都按 JS 真值语义回落到 8192`() {
        // JS 是 `Number(maxTokens) || 8192`：0 / undefined 都属 falsy → 用缺省值继续算预算
        assertEquals(6144, AnthropicWire.thinkingConfig(0)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(6144, AnthropicWire.thinkingConfig(null)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `预算上限 64000 只对大 max_tokens 生效`() {
        // 可达区间（total ≥ 2048）里 budget 恒小于 total，故守卫不会拒绝配置
        assertEquals(6144, AnthropicWire.thinkingConfig(8192)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(48000, AnthropicWire.thinkingConfig(64000)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(64000, AnthropicWire.thinkingConfig(100_000)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(64000, AnthropicWire.thinkingConfig(200_000)!!["budget_tokens"]!!.jsonPrimitive.content.toInt())
    }

    // ---------- 消息转换 ----------

    @Test
    fun `工具结果消息降级为 user 且 content 为纯文本（镜像 JS 角色映射）`() {
        val messages = listOf(
            openAi("""{"role":"tool","tool_call_id":"tu_1","content":"12:00"}"""),
        )
        val (_, json) = AnthropicWire.toAnthropicMessages(messages)
        val user = json[0].jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        assertEquals("12:00", user["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant 消息角色保持不变`() {
        val messages = listOf(
            openAi("""{"role":"system","content":"s"}"""),
            openAi("""{"role":"user","content":"几点了"}"""),
            openAi("""{"role":"assistant","content":"我查一下"}"""),
        )
        val (system, json) = AnthropicWire.toAnthropicMessages(messages)
        assertEquals("s", system)
        assertEquals(listOf("user", "assistant"), json.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun `content 数组转 text 与 image parts，非 data URL 图片丢弃`() {
        val image = "data:image/png;base64,QUJD"
        val messages = listOf(
            openAi("""{"role":"user","content":[{"type":"text","text":"看图"},{"type":"image_url","image_url":{"url":"$image"}},{"type":"image_url","image_url":{"url":"https://x/y.png"}}]}"""),
        )
        val (_, json) = AnthropicWire.toAnthropicMessages(messages)
        val parts = json[0].jsonObject["content"]!!.jsonArray
        assertEquals(2, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("看图", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val source = parts[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("QUJD", source["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `空 content 数组补一个空 text part`() {
        val messages = listOf(openAi("""{"role":"user","content":[]}"""))
        val (_, json) = AnthropicWire.toAnthropicMessages(messages)
        val parts = json[0].jsonObject["content"]!!.jsonArray
        assertEquals(1, parts.size)
        assertEquals("", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `相邻同角色按 parts 合并成一条`() {
        // 首条故意用 parts 形态：纯文本首条 user 会被「上游形态兜底」当成 system（下一条用例专测该规则）
        val messages = listOf(
            openAi("""{"role":"user","content":[{"type":"text","text":"第一句"}]}"""),
            openAi("""{"role":"user","content":"第二句"}"""),
            openAi("""{"role":"assistant","content":"答"}"""),
        )
        val (system, json) = AnthropicWire.toAnthropicMessages(messages)
        assertEquals("", system)
        assertEquals(2, json.size)
        val parts = json[0].jsonObject["content"]!!.jsonArray
        assertEquals(listOf("第一句", "第二句"), parts.map { it.jsonObject["text"]!!.jsonPrimitive.content })
    }

    @Test
    fun `首条为 assistant 时前置 begin 占位`() {
        val messages = listOf(openAi("""{"role":"assistant","content":"我先说"}"""))
        val (_, json) = AnthropicWire.toAnthropicMessages(messages)
        assertEquals(2, json.size)
        assertEquals("user", json[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(
            "(begin)",
            json[0].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals("assistant", json[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `空消息列表也会前置占位（Anthropic 不接受空 messages）`() {
        val (_, json) = AnthropicWire.toAnthropicMessages(emptyList())
        assertEquals(1, json.size)
        assertEquals("user", json[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    // ---------- 流帧解析 ----------

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
    fun `用量与结束原因解析并保留原始 usage`() {
        val delta = parseAnthropicFrame(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":12,"output_tokens":34,"cache_read_input_tokens":5}}"""
        )
        assertEquals(12, delta!!.usage!!.input)
        assertEquals(34, delta.usage!!.output)
        assertEquals("end_turn", delta.finishReason)
        assertTrue(delta.rawUsage.toString().contains("cache_read_input_tokens"))
    }

    @Test
    fun `message_start 帧带输入用量但不产生正文`() {
        val delta = parseAnthropicFrame(
            """{"type":"message_start","message":{"usage":{"input_tokens":9,"output_tokens":1}}}"""
        )!!
        assertEquals(9, delta.usage!!.input)
        assertNull(delta.content)
    }

    @Test
    fun `非流式整包 message 帧也能拼出正文与思考`() {
        val delta = parseAnthropicFrame(
            """{"type":"message","stop_reason":"end_turn","content":[{"type":"thinking","thinking":"想过"},{"type":"text","text":"答案"}]}"""
        )!!
        assertEquals("答案", delta.content)
        assertEquals("想过", delta.reasoning)
        assertEquals("end_turn", delta.finishReason)
    }

    @Test
    fun `message_stop 不产生 finishReason（避免顶掉 stop_reason）`() {
        val delta = parseAnthropicFrame("""{"type":"message_stop"}""")!!
        assertNull(delta.finishReason)
        assertNull(delta.content)
        assertNull(delta.reasoning)
        assertTrue(delta.toolCalls.isEmpty())
        assertNull(delta.error)
    }

    @Test
    fun `错误帧转 LlmDelta error 且 overloaded 可重试`() {
        val delta = parseAnthropicFrame("""{"type":"error","error":{"type":"overloaded_error","message":"忙"}}""")
        assertTrue(delta!!.error!!.retryable)
        assertEquals("忙", delta.error!!.message)
        assertEquals("error", delta.finishReason)
        val fatal = parseAnthropicFrame("""{"type":"error","error":{"type":"invalid_request_error","message":"坏请求"}}""")
        assertFalse(fatal!!.error!!.retryable)
    }

    @Test
    fun `无关帧返回 null`() {
        assertNull(parseAnthropicFrame("""{"type":"ping"}"""))
        assertNull(parseAnthropicFrame("not json"))
        assertNull(parseAnthropicFrame(""))
    }

    @Test
    fun `Anthropic 不发送 tools（与 JS 适配器一致）`() {
        val body = AnthropicWire.requestBody(
            request(tools = listOf(buildJsonObject { put("type", "function") }))
        )
        assertNull(body["tools"])
        assertNull(body["tool_choice"])
    }
}

package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChatPlan] 单测：JS 产出的 plan JSON → [com.luzzymeow.luzzyrp.chat.llm.LlmRequest]。
 *
 * 关注点是**只做索引、不做裁剪**：messages 原对象必须留在 [com.luzzymeow.luzzyrp.chat.llm.LlmMessage.raw]
 * 里（OpenAI 路径逐字节转发靠它），tools 原样收下，未知协议 / 缺 jobId 才拒绝。
 */
class ChatPlanTest {

    private fun ok(json: String) = ChatPlan.parse(json) as ChatPlan.Result.Ok

    private fun invalid(json: String) = ChatPlan.parse(json) as ChatPlan.Result.Invalid

    private val minimal = """
        {"jobId":"j-1","protocol":"openai","baseUrl":"https://a/v1/chat/completions",
         "apiKey":"sk-k","model":"m","messages":[{"role":"user","content":"hi"}]}
    """.trimIndent()

    @Test
    fun `最小 plan 可解析且布尔与可选字段走缺省`() {
        val request = ok(minimal).request
        assertEquals("j-1", ok(minimal).jobId)
        assertEquals("openai", request.protocol)
        assertEquals("https://a/v1/chat/completions", request.baseUrl)
        assertEquals("sk-k", request.apiKey)
        assertEquals("m", request.model)
        assertTrue("stream 缺省为 true", request.stream)
        assertNull(request.temperature)
        assertNull(request.maxTokens)
        assertNull(request.reasoningEffort)
        assertNull(request.extraBody)
        assertFalse(request.replyInTool)
        assertFalse(request.requireTool)
        assertTrue(request.tools.isEmpty())
    }

    @Test
    fun `数值与布尔字段按 JSON 原语读取`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"gemini","baseUrl":"https://g","apiKey":"k","model":"m",
             "temperature":0.85,"maxTokens":4096,"stream":false,"reasoningEffort":"max",
             "replyInTool":true,"requireTool":true,"messages":[]}
            """.trimIndent()
        ).request
        assertEquals(0.85, request.temperature!!, 1e-9)
        assertEquals(4096, request.maxTokens)
        assertFalse(request.stream)
        assertEquals("max", request.reasoningEffort)
        assertTrue(request.replyInTool)
        assertTrue(request.requireTool)
    }

    @Test
    fun `messages 原对象保留在 raw 上（保键序与未知字段）`() {
        val raw = """{"role":"assistant","content":"答","reasoning_details":[{"type":"x"}],"unknown":7}"""
        val request = ok(
            """{"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[$raw]}"""
        ).request
        assertEquals(raw, request.messages.single().raw.toString())
    }

    @Test
    fun `角色映射覆盖四种标准角色且未知回退 user`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[
              {"role":"system","content":"s"},
              {"role":"user","content":"u"},
              {"role":"assistant","content":"a"},
              {"role":"tool","content":"t"},
              {"role":"weird","content":"w"}
            ]}
            """.trimIndent()
        ).request
        assertEquals(
            listOf(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.TOOL, LlmRole.USER),
            request.messages.map { it.role },
        )
    }

    @Test
    fun `tool_calls 被解析成 ToolCall（参数串保留原文）`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[
              {"role":"assistant","content":"","tool_calls":[
                {"id":"call_1","type":"function","function":{"name":"get_time","arguments":"{\"tz\": \"UTC\"}"}},
                {"id":"call_2","type":"function","function":{"arguments":"{}"}}
              ]}
            ]}
            """.trimIndent()
        ).request
        val calls = request.messages.single().toolCalls
        assertEquals(listOf("get_time"), calls.map { it.name })
        assertEquals("""{"tz": "UTC"}""", calls[0].rawArguments)
        assertEquals("UTC", calls[0].arguments["tz"]!!.jsonPrimitive.content)
        // 缺 name 的条目被丢弃（发出去只会让服务端 400）
        assertEquals(1, calls.size)
    }

    @Test
    fun `tool 消息的 tool_call_id 与 name 被读取`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[
              {"role":"tool","tool_call_id":"c1","name":"get_time","content":"12:00"}
            ]}
            """.trimIndent()
        ).request
        val message = request.messages.single()
        assertEquals("c1", message.toolCallId)
        assertEquals("get_time", message.name)
    }

    @Test
    fun `数组 content 的纯文本视图被抽出`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[
              {"role":"user","content":[{"type":"text","text":"看"},{"type":"image_url","image_url":{"url":"data:image/png;base64,QUJD"}},{"type":"text","text":"图"}]}
            ]}
            """.trimIndent()
        ).request
        assertEquals("看图", request.messages.single().content)
    }

    @Test
    fun `tools 原样收下`() {
        val request = ok(
            """
            {"jobId":"j","protocol":"openai","baseUrl":"https://a","apiKey":"k","model":"m","messages":[],
             "tools":[{"type":"function","function":{"name":"t"},"自定义":[1,2]}]}
            """.trimIndent()
        ).request
        assertEquals(1, request.tools.size)
        assertEquals("""{"type":"function","function":{"name":"t"},"自定义":[1,2]}""", request.tools[0].toString())
    }

    @Test
    fun `extraBody 原样收下且空对象不被当成缺失`() {
        val withEmpty = """
            {"jobId":"j-1","protocol":"openai","baseUrl":"https://a","apiKey":"sk-k","model":"m",
             "extraBody":{},"messages":[{"role":"user","content":"hi"}]}
        """.trimIndent()
        assertTrue(ok(withEmpty).request.extraBody!!.isEmpty())

        val withValues = withEmpty.replace(""""extraBody":{}""", """"extraBody":{"top_k":40,"嵌套":{"a":[1]}}""")
        val extra = ok(withValues).request.extraBody!!
        assertEquals("40", extra["top_k"]!!.jsonPrimitive.content)
        assertEquals("""{"top_k":40,"嵌套":{"a":[1]}}""", extra.toString())

        assertNull(ok(minimal).request.extraBody)
    }

    @Test
    fun `extraBody 非对象时视为缺失`() {
        val json = """{"jobId":"j","protocol":"openai","messages":[],"extraBody":"nope"}"""
        assertNull(ok(json).request.extraBody)
    }

    // ---------- 拒绝路径 ----------

    @Test
    fun `非法 JSON 被拒`() {
        assertTrue(invalid("not json").reason.contains("合法 JSON"))
        assertTrue(invalid("").reason.contains("合法 JSON"))
        assertTrue(invalid("[1,2,3]").reason.contains("合法 JSON"))
    }

    @Test
    fun `缺 jobId 或 jobId 为空被拒`() {
        assertTrue(invalid("""{"protocol":"openai"}""").reason.contains("jobId"))
        assertTrue(invalid("""{"jobId":"   ","protocol":"openai"}""").reason.contains("jobId"))
    }

    @Test
    fun `未知协议被拒且原因列出支持项`() {
        val reason = invalid("""{"jobId":"j","protocol":"cohere"}""").reason
        assertTrue(reason.contains("cohere"))
        assertTrue(reason.contains("openai"))
        assertTrue(reason.contains("anthropic"))
        assertTrue(reason.contains("gemini"))
    }

    @Test
    fun `协议大小写与空白被归一`() {
        assertEquals("anthropic", ok("""{"jobId":"j","protocol":" Anthropic "}""").request.protocol)
    }

    @Test
    fun `baseUrl 为空不算参数非法（交给传输层产出可读错误事件）`() {
        val request = ok("""{"jobId":"j","protocol":"openai","messages":[]}""").request
        assertEquals("", request.baseUrl)
        assertEquals("", request.apiKey)
        assertEquals("", request.model)
    }

    @Test
    fun `支持的协议清单与路由层同源`() {
        assertEquals(listOf("openai", "anthropic", "gemini"), ChatPlan.SUPPORTED_PROTOCOLS)
    }
}

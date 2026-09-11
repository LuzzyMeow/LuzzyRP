package com.luzzymeow.luzzyrp.chat.llm

import com.luzzymeow.luzzyrp.chat.ChatPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **线协议保真门禁**（v2.0 交付物 C）。
 *
 * 与其它线协议单测的分工：那些测「字段有没有被正确映射」，本文件测
 * **「序列化出来的那串字节是不是逐字符符合预期」**——键序、数字形态、转义、
 * extraBody 插入位置、tools 簇的出现与否，一处不对就红。
 *
 * 期望值一律是**手写常量**（不是从实现回显出来的），且与 JS 参考实现
 * `assets/rphub/assets/js/api-utils.js` 的三段 body 对象字面量逐键对齐：
 * - OpenAI：`requestChatCompletionOnce`；
 * - Anthropic：`requestAnthropicCompletionInternal`；
 * - Gemini：`requestGeminiCompletionInternal`。
 *
 * 输入统一经 [ChatPlan.parse] —— 也就是**从 JS 真正会发来的那份 plan JSON 起步**，
 * 因此这里同时覆盖了「plan 解析 → 请求体构造」整条链。
 */
class WireFidelityTest {

    private val timeTool =
        """{"type":"function","function":{"name":"get_time","description":"取时间","parameters":{"type":"object","properties":{}}}}"""

    private fun plan(
        protocol: String,
        baseUrl: String,
        tools: String = "[$timeTool]",
        extraBody: String = "{}",
        replyInTool: Boolean = false,
        requireTool: Boolean = false,
        reasoningEffort: String? = "medium",
        messages: String = """[{"role":"system","content":"你是助手"},{"role":"user","content":"现在几点"}]""",
    ): String = """
        {
          "jobId": "j-0001",
          "protocol": "$protocol",
          "baseUrl": "$baseUrl",
          "apiKey": "sk-test-key",
          "model": "model-x",
          "temperature": 0.8,
          "reasoningEffort": ${reasoningEffort?.let { "\"$it\"" } ?: "null"},
          "maxTokens": 8192,
          "stream": true,
          "extraBody": $extraBody,
          "replyInTool": $replyInTool,
          "requireTool": $requireTool,
          "tools": $tools,
          "messages": $messages
        }
    """.trimIndent()

    /** 走完整链路：plan JSON → [ChatPlan] → 对应协议的请求体序列化结果。 */
    private fun bodyOf(planJson: String): String {
        val parsed = ChatPlan.parse(planJson)
        assertTrue("plan 应可解析，实际：$parsed", parsed is ChatPlan.Result.Ok)
        val request = (parsed as ChatPlan.Result.Ok).request
        return when (request.protocol) {
            "openai" -> OpenAiWire.requestBody(request).toString()
            "anthropic" -> AnthropicWire.requestBody(request).toString()
            "gemini" -> GeminiWire.requestBody(request).toString()
            else -> error("未知协议")
        }
    }

    // ---------- OpenAI ----------

    @Test
    fun `OpenAI 请求体逐字符符合预期`() {
        assertEquals(
            """{"model":"model-x",""" +
                """"messages":[{"role":"system","content":"你是助手"},{"role":"user","content":"现在几点"}],""" +
                """"temperature":0.8,""" +
                """"reasoning_effort":"medium",""" +
                """"max_tokens":8192,""" +
                """"tools":[$timeTool],""" +
                """"tool_choice":"auto",""" +
                """"parallel_tool_calls":false,""" +
                """"stream":true,""" +
                """"stream_options":{"include_usage":true}}""",
            bodyOf(plan("openai", "https://api.example.com/v1/chat/completions")),
        )
    }

    @Test
    fun `OpenAI extraBody 精确落在 max_tokens 与 tools 之间`() {
        assertEquals(
            """{"model":"model-x",""" +
                """"messages":[{"role":"system","content":"你是助手"},{"role":"user","content":"现在几点"}],""" +
                """"temperature":0.8,""" +
                """"reasoning_effort":"medium",""" +
                """"max_tokens":8192,""" +
                """"top_k":40,""" +
                """"chat_template_kwargs":{"thinking":true},""" +
                """"tools":[$timeTool],""" +
                """"tool_choice":"auto",""" +
                """"parallel_tool_calls":false,""" +
                """"stream":true,""" +
                """"stream_options":{"include_usage":true}}""",
            bodyOf(
                plan(
                    "openai",
                    "https://api.example.com/v1/chat/completions",
                    extraBody = """{"top_k": 40, "chat_template_kwargs": {"thinking": true}}""",
                )
            ),
        )
    }

    @Test
    fun `OpenAI 抗截断：追加 output_reply 且 tool_choice 为 required`() {
        val body = bodyOf(
            plan("openai", "https://api.example.com/v1/chat/completions", replyInTool = true)
        )
        assertEquals("required", toolChoiceOf(body))
        // output_reply 追加在用户工具**之后**
        assertTrue(body.indexOf(""""name":"get_time"""") < body.indexOf(""""name":"output_reply""""))
        assertEquals(
            listOf("get_time", "output_reply"),
            Regex(""""name":"([a-z_]+)"""").findAll(body).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun `OpenAI 抗截断但无用户工具时点名 output_reply`() {
        val body = bodyOf(
            plan("openai", "https://api.example.com/v1/chat/completions", tools = "[]", replyInTool = true)
        )
        assertEquals("""{"type":"function","function":{"name":"output_reply"}}""", toolChoiceOf(body))
        // 工具数组里只有 output_reply 一份定义；另一次出现是 tool_choice 的点名
        assertEquals(2, occurrences(body, """"name":"output_reply""""))
        assertTrue(body.indexOf(""""tools":[""") < body.indexOf(""""name":"output_reply""""))
        assertTrue(body.indexOf(""""tool_choice":""") < body.lastIndexOf(""""name":"output_reply""""))
    }

    @Test
    fun `OpenAI 无工具时不带 tools 簇`() {
        val body = bodyOf(plan("openai", "https://api.example.com/v1/chat/completions", tools = "[]"))
        assertTrue(!body.contains(""""tools":"""))
        assertTrue(!body.contains(""""tool_choice":"""))
        assertTrue(!body.contains(""""parallel_tool_calls":"""))
        assertTrue(body.endsWith(""""stream":true,"stream_options":{"include_usage":true}}"""))
    }

    @Test
    fun `OpenAI requireTool 时 tool_choice 为 required 且不追加 output_reply`() {
        val body = bodyOf(
            plan(
                "openai",
                "https://api.example.com/v1/chat/completions",
                replyInTool = true,
                requireTool = true,
            )
        )
        assertEquals("required", toolChoiceOf(body))
        assertEquals(1, occurrences(body, """"name":"get_time""""))
        assertEquals(0, occurrences(body, """"name":"output_reply""""))
    }

    @Test
    fun `OpenAI messages 逐字节透传（含未知字段与键序）`() {
        val messages = """[{"role":"assistant","content":"","reasoning_content":"想过","reasoning_details":[{"type":"x"}],""" +
            """"tool_calls":[{"id":"c1","type":"function","function":{"name":"t","arguments":"{\"a\": 1}"},"extra_content":{"g":1}}]},""" +
            """{"role":"tool","tool_call_id":"c1","content":"结果"}]"""
        val body = bodyOf(
            plan("openai", "https://x/v1/chat/completions", tools = "[]", messages = messages)
        )
        assertTrue(body.contains(""""messages":$messages"""))
    }

    // ---------- Anthropic ----------

    @Test
    fun `Anthropic 请求体逐字符符合预期`() {
        assertEquals(
            """{"model":"model-x",""" +
                """"max_tokens":8192,""" +
                """"system":"你是助手",""" +
                """"messages":[{"role":"user","content":"现在几点"}],""" +
                """"temperature":0.8,""" +
                """"thinking":{"type":"enabled","budget_tokens":6144},""" +
                """"stream":true}""",
            bodyOf(plan("anthropic", "https://api.anthropic.com/v1/messages")),
        )
    }

    @Test
    fun `Anthropic 不开思考时无 thinking 键`() {
        assertEquals(
            """{"model":"model-x","max_tokens":8192,"system":"你是助手",""" +
                """"messages":[{"role":"user","content":"现在几点"}],"temperature":0.8,"stream":true}""",
            bodyOf(plan("anthropic", "https://api.anthropic.com/v1/messages", reasoningEffort = null)),
        )
    }

    @Test
    fun `Anthropic extraBody 落在 thinking 与 stream 之间`() {
        assertEquals(
            """{"model":"model-x","max_tokens":8192,"system":"你是助手",""" +
                """"messages":[{"role":"user","content":"现在几点"}],"temperature":0.8,""" +
                """"thinking":{"type":"enabled","budget_tokens":6144},"top_k":40,"stream":true}""",
            bodyOf(
                plan(
                    "anthropic",
                    "https://api.anthropic.com/v1/messages",
                    extraBody = """{"top_k": 40}""",
                )
            ),
        )
    }

    @Test
    fun `Anthropic 不发 tools（与 JS 适配器一致）`() {
        assertTrue(!bodyOf(plan("anthropic", "https://api.anthropic.com/v1/messages")).contains(""""tools":"""))
    }

    @Test
    fun `Anthropic 无 system 消息时不出现 system 键`() {
        val single = """[{"role":"user","content":"只有一句"}]"""
        assertEquals(
            """{"model":"model-x","max_tokens":8192,"messages":[{"role":"user","content":"只有一句"}],""" +
                """"temperature":0.8,"thinking":{"type":"enabled","budget_tokens":6144},"stream":true}""",
            bodyOf(plan("anthropic", "https://api.anthropic.com/v1/messages", messages = single)),
        )
    }

    // ---------- Gemini ----------

    @Test
    fun `Gemini 请求体逐字符符合预期`() {
        assertEquals(
            """{"contents":[{"role":"user","parts":[{"text":"现在几点"}]}],""" +
                """"systemInstruction":{"parts":[{"text":"你是助手"}]},""" +
                """"generationConfig":{"temperature":0.8,"maxOutputTokens":8192,""" +
                """"thinkingConfig":{"thinkingBudget":8192}}}""",
            bodyOf(plan("gemini", "https://generativelanguage.googleapis.com")),
        )
    }

    @Test
    fun `Gemini extraBody 展开在最外层末尾`() {
        val body = bodyOf(
            plan(
                "gemini",
                "https://generativelanguage.googleapis.com",
                extraBody = """{"safetySettings": [{"category": "HARM"}]}""",
            )
        )
        assertEquals(
            """{"contents":[{"role":"user","parts":[{"text":"现在几点"}]}],""" +
                """"systemInstruction":{"parts":[{"text":"你是助手"}]},""" +
                """"generationConfig":{"temperature":0.8,"maxOutputTokens":8192,"thinkingConfig":{"thinkingBudget":8192}},""" +
                """"safetySettings":[{"category":"HARM"}]}""",
            body,
        )
        assertTrue(body.endsWith("""{"category":"HARM"}]}"""))
    }

    @Test
    fun `Gemini 端点为逐字符预期`() {
        val request = (ChatPlan.parse(plan("gemini", "https://generativelanguage.googleapis.com")) as ChatPlan.Result.Ok).request
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/model-x:streamGenerateContent?alt=sse&key=sk-test-key",
            GeminiWire.endpoint(request.baseUrl, request.model, request.apiKey, request.stream),
        )
    }

    // ---------- 跨协议一致性 ----------

    @Test
    fun `同一 plan 在三种协议下都不把密钥写进 body`() {
        listOf(
            "openai" to "https://api.example.com/v1/chat/completions",
            "anthropic" to "https://api.anthropic.com/v1/messages",
            "gemini" to "https://generativelanguage.googleapis.com",
        ).forEach { (protocol, baseUrl) ->
            val body = bodyOf(plan(protocol, baseUrl))
            assertTrue("$protocol 的请求体不该含密钥", !body.contains("sk-test-key"))
        }
    }

    @Test
    fun `OpenAI 与 Anthropic 从同一份 plan 出发：一个转发、一个抽 system`() {
        val openAi = bodyOf(plan("openai", "https://a/v1/chat/completions"))
        val anthropic = bodyOf(plan("anthropic", "https://a/v1/messages"))
        assertTrue(openAi.contains(""""role":"system","content":"你是助手""""))
        assertTrue(anthropic.contains(""""system":"你是助手""""))
        assertTrue(anthropic.contains(""""role":"user","content":"现在几点""""))
        assertTrue(!anthropic.contains(""""role":"system""""))
    }

    // ---------- 断言助手 ----------

    /** 取 `tool_choice` 的 JSON 值文本（字符串去掉引号；对象保持字面量）。 */
    private fun toolChoiceOf(body: String): String {
        val marker = """"tool_choice":"""
        val start = body.indexOf(marker)
        assertTrue("请求体应含 tool_choice", start >= 0)
        val valueStart = start + marker.length
        if (body[valueStart] == '{') {
            var depth = 0
            var index = valueStart
            while (index < body.length) {
                when (body[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return body.substring(valueStart, index + 1)
                    }
                }
                index++
            }
            error("tool_choice 对象未闭合")
        }
        val end = body.indexOf(',', valueStart)
        return body.substring(valueStart, end).trim('"')
    }

    private fun occurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }
}

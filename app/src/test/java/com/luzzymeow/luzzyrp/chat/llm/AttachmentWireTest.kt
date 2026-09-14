package com.luzzymeow.luzzyrp.chat.llm

import com.luzzymeow.luzzyrp.ui.pages.chat.ChatAttachment
import com.luzzymeow.luzzyrp.ui.pages.chat.userContentParts
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 附件图片进请求的**三协议翻译**（C4）。
 *
 * PLAN §9 风险表的判据原文：「每协议一条单测 + 负控（不支持图片要**如实报错**，不静默丢图）」。
 * 上游构造的是 OpenAI 形态的 content 数组（`userContentParts`），三家 wire 各自翻译：
 * 这里用**同一条输入**过三家的请求体构造，钉住每家的图片形态与正文位置。
 */
class AttachmentWireTest {

    private val dataUrl = "data:image/jpeg;base64,QUJDREVGRw=="
    private val text = "看这张图"

    /** OpenAI 形态的一条带图 user 消息（与 `userContentParts` 的产物同构）。 */
    private fun message(): LlmMessage {
        val parts = requireNotNull(
            userContentParts(text, listOf(ChatAttachment(location = dataUrl, mime = "image/jpeg"))),
        )
        return LlmMessage(role = LlmRole.USER, content = text, rawContent = parts)
    }

    private fun request(protocol: String) = LlmRequest(
        messages = listOf(message()),
        protocol = protocol,
        baseUrl = "https://api.example.com",
        apiKey = "sk-test-not-real",
        model = "test-model",
        maxTokens = 100,
    )

    @Test
    fun `OpenAI 原样直通 content 数组`() {
        val body = OpenAiWire.requestBody(request("openai"))
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals(2, content.size)
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(text, content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(dataUrl, content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Anthropic 翻译成 base64 image source`() {
        val body = AnthropicWire.requestBody(request("anthropic"))
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        val image = content.first { it.jsonObject["type"]!!.jsonPrimitive.content == "image" }.jsonObject
        val source = image["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("QUJDREVGRw==", source["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Gemini 翻译成 inline_data`() {
        val body = GeminiWire.requestBody(request("gemini"))
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val inline = parts.first { it.jsonObject.containsKey("inlineData") }.jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/jpeg", inline["mimeType"]!!.jsonPrimitive.content)
        assertEquals("QUJDREVGRw==", inline["data"]!!.jsonPrimitive.content)
    }
}

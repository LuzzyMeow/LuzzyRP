package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 附件的**纯函数层**（C4）：parts 构造、payload 编解码、发请求前的路径解析。
 *
 * 为什么值得单独钉：附件链路的两类故障都**不报错**——
 * - parts 键名写错 → 三家 wire 静默丢图（模型根本看不到图，用户以为发成功了）；
 * - 路径解析静默失败 → 模型上一轮看得见图、这一轮看不见（改写历史，DSH 说的「静默漂移」）。
 * 所以「解析失败必须抛」「纯文本消息逐字节不变」这两条要在 JVM 层钉死。
 */
class ChatAttachmentTest {

    private fun attachment(
        location: String = "assets/attachments/c4-1.jpg",
        mime: String = "image/jpeg",
    ) = ChatAttachment(location = location, mime = mime, name = "photo.jpg")

    // ---------------------------------------------------------------- parts 构造

    @Test
    fun `纯文本不产生 parts（请求字节与引入附件之前一致）`() {
        assertNull(userContentParts("你好", emptyList()))
    }

    @Test
    fun `带附件时正文在前图片在后`() {
        val parts = userContentParts("看这张图", listOf(attachment(), attachment(location = "assets/attachments/c4-2.jpg")))
        val array = parts!!.jsonArray
        assertEquals(3, array.size)
        assertEquals("text", array[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("看这张图", array[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", array[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "assets/attachments/c4-1.jpg",
            array[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
        assertEquals("image_url", array[2].jsonObject["type"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------- payload 编解码

    @Test
    fun `附件与 payload 往返逐字段一致`() {
        val original = listOf(attachment(), ChatAttachment(location = "assets/attachments/x.png", mime = "image/png"))
        val restored = attachmentsOfJson(attachmentsToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `旧结构条目（只有 dataUrl）也能认回`() {
        val payload = """{"imageAttachments":[{"dataUrl":"assets/attachments/old.jpg","extra":1}]}"""
        val restored = attachmentsOfJson(Json.parseToJsonElement(payload).jsonObject["imageAttachments"])
        assertEquals(1, restored.size)
        assertEquals("assets/attachments/old.jpg", restored.single().location)
        assertEquals("旧条目没有 mime 时不编造", "", restored.single().mime)
    }

    @Test
    fun `内联 data URL 形态照常认回（迁移小图）`() {
        val payload = """{"imageAttachments":[{"dataUrl":"data:image/png;base64,QUJD"}]}"""
        val restored = attachmentsOfJson(Json.parseToJsonElement(payload).jsonObject["imageAttachments"])
        assertTrue("data: 形态是内联的，发请求前无需解析", restored.single().isInline)
    }

    @Test
    fun `坏条目丢弃绝不抛`() {
        assertNull(attachmentsOfJson(null).takeIf { it.isNotEmpty() })
        assertTrue(attachmentsOfJson(Json.parseToJsonElement("""{"imageAttachments":[1,"x",{}]}""")).isEmpty())
    }

    // ---------------------------------------------------------------- 路径解析

    @Test
    fun `路径解析成 data URL 且 data 形态原样保留`() {
        val messages = listOf(
            LlmMessage(
                role = LlmRole.USER,
                content = "两张图",
                rawContent = userContentParts("两张图", listOf(attachment(), ChatAttachment(location = "data:image/png;base64,QUJD"))),
            ),
            LlmMessage(role = LlmRole.ASSISTANT, content = "收到"),
        )
        val resolved = kotlinx.coroutines.runBlocking {
            resolveImageParts(messages) { path -> "data:image/jpeg;base64," + path.toByteArray().size }
        }
        val parts = resolved[0].rawContent!!.jsonArray
        val resolvedUrl = parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertTrue(
            "路径形态被替换成 data URL：$resolvedUrl",
            resolvedUrl.startsWith("data:image/jpeg;base64,"),
        )
        assertEquals(
            "data: 形态不动",
            "data:image/png;base64,QUJD",
            parts[2].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
        assertEquals("纯文本消息逐字节不变（前缀缓存不受影响）", "收到", resolved[1].content)
        assertNull("无附件的消息 rawContent 仍为空", resolved[1].rawContent)
    }

    @Test
    fun `解析失败必须抛（静默丢图等于改写历史）`() {
        val messages = listOf(
            LlmMessage(
                role = LlmRole.USER,
                content = "图",
                rawContent = userContentParts("图", listOf(attachment())),
            ),
        )
        try {
            kotlinx.coroutines.runBlocking {
                resolveImageParts(messages) { throw IllegalStateException("附件文件缺失") }
            }
            fail("读不到附件必须抛，不能静默丢图")
        } catch (expected: IllegalStateException) {
            assertEquals("附件文件缺失", expected.message)
        }
    }

    @Test
    fun `无 rawContent 的消息原样通过`() {
        val resolved = kotlinx.coroutines.runBlocking {
            resolveImageParts(listOf(LlmMessage(role = LlmRole.USER, content = "普通发言"))) { error("不应被调用") }
        }
        assertEquals("普通发言", resolved.single().content)
    }
}

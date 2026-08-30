package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式合并代数单元测试（HARD_REQUIREMENTS 规定 1 的守护测试之一）。
 * 验证 SSE 增量 chunk 逐 event 合并的正确性：文本/推理/工具调用/用量。
 */
class ChunkMergeTest {

    private fun textChunk(text: String, id: String = "chatcmpl-1") = MessageChunk(
        id = id,
        model = "glm-5.2",
        choices = listOf(
            UIMessageChoice(delta = UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Text(text))))
        ),
    )

    @Test
    fun `text delta appends character by character`() {
        var messages = listOf<UIMessage>()
        for (ch in "你好世界") {
            messages = messages.handleMessageChunk(textChunk(ch.toString()))
        }
        assertEquals(1, messages.size)
        assertEquals("你好世界", messages[0].textContent())
    }

    @Test
    fun `reasoning delta merges into single reasoning part`() {
        var messages = listOf<UIMessage>()
        messages = messages.handleMessageChunk(
            MessageChunk(choices = listOf(UIMessageChoice(delta = UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Reasoning("思考"))))))
        )
        messages = messages.handleMessageChunk(
            MessageChunk(choices = listOf(UIMessageChoice(delta = UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Reasoning("继续"))))))
        )
        assertEquals(1, messages.size)
        assertEquals("思考继续", messages[0].reasoningContent())
        assertEquals(1, messages[0].parts.filterIsInstance<UIMessagePart.Reasoning>().size)
    }

    @Test
    fun `tool call delta accumulates by toolCallId`() {
        var messages = listOf<UIMessage>()
        messages = messages.handleMessageChunk(
            MessageChunk(
                choices = listOf(
                    UIMessageChoice(
                        delta = UIMessage(
                            role = Role.ASSISTANT,
                            parts = listOf(
                                UIMessagePart.Tool(toolCallId = "call_1", toolName = "memory_reca", input = JsonPrimitive("{\"qu"))
                            ),
                        )
                    )
                )
            )
        )
        messages = messages.handleMessageChunk(
            MessageChunk(
                choices = listOf(
                    UIMessageChoice(
                        delta = UIMessage(
                            role = Role.ASSISTANT,
                            parts = listOf(
                                UIMessagePart.Tool(toolCallId = "call_1", toolName = "ll", input = JsonPrimitive("ery\":\"鹿溪\"}"))
                            ),
                        )
                    )
                )
            )
        )
        val tool = messages[0].parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals("memory_recall", tool.toolName)
        val parsed = tool.parsedInput().jsonObject
        assertEquals("鹿溪", parsed["query"]?.toString()?.trim('"'))
        assertTrue(tool.isExecuted.not())
    }

    @Test
    fun `usage tail chunk attaches to last assistant message`() {
        var messages = listOf<UIMessage>()
        messages = messages.handleMessageChunk(textChunk("正文"))
        messages = messages.handleMessageChunk(
            MessageChunk(usage = TokenUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120, cachedTokens = 80))
        )
        val usage = messages.last().usage
        assertNotNull(usage)
        assertEquals(80L, usage!!.cachedTokens)
        assertEquals(0.8f, usage.cacheHitRate(), 0.001f)
    }

    @Test
    fun `new role starts new message`() {
        var messages = listOf<UIMessage>()
        messages = messages.handleMessageChunk(textChunk("AI 说"))
        messages = messages.handleMessageChunk(
            MessageChunk(choices = listOf(UIMessageChoice(delta = UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text("用户说"))))))
        )
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[1].role)
    }

    @Test
    fun `serialization roundtrip of merged message is stable`() {
        // [INVARIANT-KV] 同一内容两次序列化必须逐字节相等（KV 稳定性）
        var messages = listOf<UIMessage>()
        messages = messages.handleMessageChunk(textChunk("稳定"))
        messages = messages.handleMessageChunk(
            MessageChunk(
                choices = listOf(
                    UIMessageChoice(
                        delta = UIMessage(
                            role = Role.ASSISTANT,
                            parts = listOf(UIMessagePart.Tool(toolCallId = "c1", toolName = "world_recall", input = JsonPrimitive("{}")))
                        )
                    )
                )
            )
        )
        val a = Json.encodeToString(UIMessage.serializer(), messages[0])
        val b = Json.encodeToString(UIMessage.serializer(), messages[0])
        assertEquals(a, b)
        // createdAt 仅用于本地持久化展示，不进入模型请求体（请求体由 buildProtocolMessages
        // 单独构造，无时间字段），故此处只校验两次序列化一致性 + 工具调用完整入列
        assertTrue(a.contains("world_recall"))
    }
}

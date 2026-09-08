package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ToolCallAccumulator] 单测：三协议的工具调用分片拼装语义。
 *
 * 覆盖：OpenAI 增量分片（id 只在首片、arguments 跨片）、多工具并发（按 index 隔离）、
 * 非法 JSON 容错、空参调用。
 */
class ToolCallAccumulatorTest {

    @Test
    fun `单工具参数跨片拼装后解析`() {
        val acc = ToolCallAccumulator()
        acc.accept(
            listOf(
                ToolCallDelta(index = 0, id = "call_a", name = "workspace_write", argumentsChunk = "{\"path\":\"a."),
                ToolCallDelta(index = 0, argumentsChunk = "txt\",\"content\":\"hi\"}"),
            )
        )
        val calls = acc.build()
        assertEquals(1, calls.size)
        assertEquals("call_a", calls[0].id)
        assertEquals("workspace_write", calls[0].name)
        assertEquals("a.txt", calls[0].arguments["path"]?.jsonPrimitive?.content)
        assertEquals("hi", calls[0].arguments["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `多工具按 index 隔离且保持顺序`() {
        val acc = ToolCallAccumulator()
        acc.accept(
            listOf(
                ToolCallDelta(index = 0, id = "call_0", name = "get_time", argumentsChunk = "{}"),
                ToolCallDelta(index = 1, id = "call_1", name = "web_fetch", argumentsChunk = "{\"url\":\"https://a.b\"}"),
            )
        )
        val calls = acc.build()
        assertEquals(listOf("get_time", "web_fetch"), calls.map { it.name })
        assertEquals("https://a.b", calls[1].arguments["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun `非法 JSON 不抛异常并保留原文`() {
        val acc = ToolCallAccumulator()
        acc.accept(listOf(ToolCallDelta(index = 0, id = "call_x", name = "memory_write", argumentsChunk = "{not json")))
        val calls = acc.build()
        assertEquals(1, calls.size)
        assertTrue("非法参数应回退为空对象", calls[0].arguments.isEmpty())
        assertEquals("{not json", calls[0].rawArguments)
    }

    @Test
    fun `空参调用产出空对象`() {
        val acc = ToolCallAccumulator()
        acc.accept(listOf(ToolCallDelta(index = 0, id = "c", name = "get_device_info")))
        val calls = acc.build()
        assertTrue(calls[0].arguments.isEmpty())
    }

    @Test
    fun `无分片时 isEmpty 为真`() {
        assertTrue(ToolCallAccumulator().isEmpty())
    }
}

package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ToolCallAccumulator] 单测：三协议的工具调用分片拼装语义 + 累积快照形状。
 *
 * 覆盖：OpenAI 增量分片（id 只在首片、arguments 跨片）、多工具并发（按 index 隔离）、
 * 非法 JSON 容错、空参调用、快照键序（JS 事件契约）。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复（新增快照断言）。
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

    // ---------- 累积快照（JS 事件契约） ----------

    @Test
    fun `快照是 OpenAI tool_calls 形状且键序固定`() {
        val acc = ToolCallAccumulator()
        acc.accept(
            listOf(
                ToolCallDelta(index = 0, id = "call_a", name = "get_time", argumentsChunk = "{\"tz\":"),
                ToolCallDelta(index = 0, argumentsChunk = "\"UTC\"}"),
            )
        )
        val payload = acc.snapshotString()
        assertEquals(
            """[{"id":"call_a","type":"function","function":{"name":"get_time","arguments":"{\"tz\":\"UTC\"}"}}]""",
            payload,
        )
    }

    @Test
    fun `快照随分片增长（每帧给全量而不是增量）`() {
        val acc = ToolCallAccumulator()
        acc.accept(listOf(ToolCallDelta(index = 0, id = "call_a", name = "web_fetch", argumentsChunk = "{\"url\":")))
        assertEquals(1, acc.snapshotJson().size)
        acc.accept(listOf(ToolCallDelta(index = 1, id = "call_b", name = "get_time", argumentsChunk = "{}")))
        assertEquals(2, acc.snapshotJson().size)
    }

    @Test
    fun `快照参数串保留原始分片拼装结果（未二次序列化）`() {
        val acc = ToolCallAccumulator()
        acc.accept(listOf(ToolCallDelta(index = 0, id = "c", name = "t", argumentsChunk = """{"a": 1, "b":2}""")))
        assertTrue(acc.snapshotString().contains(""""arguments":"{\"a\": 1, \"b\":2}""""))
    }

    @Test
    fun `缺失 id 时用 index 合成稳定 id`() {
        val acc = ToolCallAccumulator()
        acc.accept(listOf(ToolCallDelta(index = 2, name = "t")))
        assertEquals("call_2", acc.build().single().id)
    }
}

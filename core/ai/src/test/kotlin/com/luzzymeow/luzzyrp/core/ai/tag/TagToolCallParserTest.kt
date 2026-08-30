package com.luzzymeow.luzzyrp.core.ai.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [INVARIANT-AGENTIC] 标签工具调用兜底解析器测试（不变性：GLM-5.2 等无原生 FC 模型兜底）。
 */
class TagToolCallParserTest {

    private fun feedAll(parser: TagToolCallParser, text: String): List<TagToolCallParser.ParseResult> {
        // 模拟流式：按字符喂入（最严苛的切分场景）
        return text.map { parser.feed(it.toString()) } + parser.finish()
    }

    @Test
    fun `visible text streams through before tag`() {
        val parser = TagToolCallParser()
        val results = feedAll(parser, "她抬起头。<tool_calls>memory_recall:{\"query\":\"鹿溪\"}</tool_calls>")
        val visible = results.joinToString("") { it.visibleText }
        assertEquals("她抬起头。", visible)
    }

    @Test
    fun `line format single call parsed`() {
        val parser = TagToolCallParser()
        val results = feedAll(parser, "<tool_calls>memory_recall:{\"query\":\"鹿溪\"}</tool_calls>")
        val calls = results.flatMap { it.toolCalls }
        assertEquals(1, calls.size)
        assertEquals("memory_recall", calls[0].name)
        assertEquals("{\"query\":\"鹿溪\"}", calls[0].argumentsJson.replace(" ", ""))
    }

    @Test
    fun `line format multiple calls parsed`() {
        val parser = TagToolCallParser()
        val text = "<tool_calls>\nworld_search:{\"keywords\":[\"森林\",\"夜晚\"]}\nmemory_recall:{\"query\":\"约定\"}\n</tool_calls>"
        val results = feedAll(parser, text)
        val calls = results.flatMap { it.toolCalls }
        assertEquals(2, calls.size)
        assertEquals("world_search", calls[0].name)
        assertEquals("memory_recall", calls[1].name)
    }

    @Test
    fun `json array format parsed`() {
        val parser = TagToolCallParser()
        val text = "<tool_calls>[{\"name\":\"time\",\"arguments\":{}},{\"name\":\"memory_recall\",\"arguments\":{\"query\":\"名字\"}}]</tool_calls>"
        val results = feedAll(parser, text)
        val calls = results.flatMap { it.toolCalls }
        assertEquals(2, calls.size)
        assertEquals("time", calls[0].name)
        assertEquals("memory_recall", calls[1].name)
    }

    @Test
    fun `tag split across deltas does not leak into visible text`() {
        val parser = TagToolCallParser()
        val results = feedAll(parser, "正文<tool_ca")
        val visible = results.joinToString("") { it.visibleText }
        assertEquals("正文", visible)  // 半个标签不得上屏
    }

    @Test
    fun `unclosed tag at stream end best-effort parsed`() {
        val parser = TagToolCallParser()
        val results = feedAll(parser, "<tool_calls>time:{}</tool_calls><tool_calls>memory_recall:{\"query\":\"故乡\"}")
        val calls = results.flatMap { it.toolCalls }
        assertEquals(2, calls.size)
        assertEquals("time", calls[0].name)
        assertEquals("memory_recall", calls[1].name)
    }

    @Test
    fun `multiline json args merged by brace pairing`() {
        val parser = TagToolCallParser()
        val text = "<tool_calls>\nmemory_recall:{\n\"query\": \"鹿溪的发饰\",\n\"top_k\": 5\n}\n</tool_calls>"
        val results = feedAll(parser, text)
        val calls = results.flatMap { it.toolCalls }
        assertEquals(1, calls.size)
        assertEquals("memory_recall", calls[0].name)
        assertTrue(calls[0].argumentsJson.contains("\"top_k\""))
    }

    @Test
    fun `plain text without tags passes through untouched`() {
        val parser = TagToolCallParser()
        val results = feedAll(parser, "普通正文，没有任何标签。1 < 2 且 a:b 不是工具。")
        val visible = results.joinToString("") { it.visibleText }
        assertEquals("普通正文，没有任何标签。1 < 2 且 a:b 不是工具。", visible)
        assertTrue(results.flatMap { it.toolCalls }.isEmpty())
    }
}

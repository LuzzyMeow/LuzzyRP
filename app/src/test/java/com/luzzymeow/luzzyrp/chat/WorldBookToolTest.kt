package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 世界书工具（模型可自主调用、应用侧真实执行）的确定性单测。 */
class WorldBookToolTest {

    @Test
    fun `schema 是 OpenAI 函数形态且名字一致`() {
        val fn = WorldBookTool.schema["function"]!!.jsonObject
        assertEquals(WorldBookTool.Name, fn["name"]!!.jsonPrimitive.content)
        assertEquals("function", WorldBookTool.schema["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `解析模型参数：合法 JSON 取关键词`() {
        val kw = WorldBookTool.parseKeywords("""{"keywords":["钟楼","苹果树"]}""")
        assertEquals(listOf("钟楼", "苹果树"), kw)
    }

    @Test
    fun `解析模型参数：非法 JSON 或字段缺失一律空列表（不抛异常）`() {
        assertTrue(WorldBookTool.parseKeywords("").isEmpty())
        assertTrue(WorldBookTool.parseKeywords("not json at all").isEmpty())
        assertTrue(WorldBookTool.parseKeywords("""{"keywords":"钟楼"}""").isEmpty())
        assertTrue(WorldBookTool.parseKeywords("""{"other":1}""").isEmpty())
        assertTrue(WorldBookTool.parseKeywords("""["钟楼"]""").isEmpty())
    }

    @Test
    fun `检索命中任一关键词即激活条目`() {
        assertTrue(WorldBookTool.lookup(listOf("钟楼")).any { it.title == "钟楼红苹果树" })
        assertTrue(WorldBookTool.lookup(listOf("嬷嬷")).any { it.title == "嬷嬷的巡视路线" })
        // 包含关系也算命中（「钟楼顶」含「钟楼」）
        assertTrue(WorldBookTool.lookup(listOf("钟楼顶")).any { it.title == "钟楼红苹果树" })
        assertTrue(WorldBookTool.lookup(listOf("毫不相干的词")).isEmpty())
        assertTrue(WorldBookTool.lookup(emptyList()).isEmpty())
    }

    @Test
    fun `执行结果 JSON 携带命中条目标题与正文`() {
        val json = WorldBookTool.execute(WorldBookTool.Name, """{"keywords":["嬷嬷"]}""")
        assertTrue(json.contains("\"entries\":1"))
        assertTrue(json.contains("嬷嬷的巡视路线"))
        assertTrue(json.contains("清晨与黄昏"))
    }

    @Test
    fun `未知工具返回错误 JSON 而不是异常`() {
        val json = WorldBookTool.execute("no_such_tool", "{}")
        assertTrue(json.contains("error"))
        assertTrue(json.contains("no_such_tool"))
    }

    @Test
    fun `注入 system 的世界书块含标题与正文，空命中为空串`() {
        val hits = WorldBookTool.lookup(listOf("苹果树"))
        val block = WorldBookTool.renderForPrompt(hits)
        assertTrue(block.startsWith("<world_info>"))
        assertTrue(block.contains("钟楼红苹果树"))
        assertEquals("", WorldBookTool.renderForPrompt(emptyList()))
        assertFalse(block.contains("<memory_recall>"))
    }
}

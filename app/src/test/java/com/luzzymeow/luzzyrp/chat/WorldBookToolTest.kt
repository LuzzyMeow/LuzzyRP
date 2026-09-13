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
        assertTrue(WorldBookTool.lookup(listOf("钟楼")).any { it.displayName == "钟楼红苹果树" })
        assertTrue(WorldBookTool.lookup(listOf("嬷嬷")).any { it.displayName == "嬷嬷的巡视路线" })
        // 包含关系也算命中（「钟楼顶」含「钟楼」）
        assertTrue(WorldBookTool.lookup(listOf("钟楼顶")).any { it.displayName == "钟楼红苹果树" })
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

    // ---------------------------------------------------------------- A4 缓存：schema 字节稳定

    @Test
    fun `工具的 schema 序列化逐字节稳定`() {
        // 服务端前缀缓存把 `tools` 也算进前缀：schema 只要有一处键序/空白变化，
        // 整个请求从 tools 段起就失效。所以钉住它的**字节稳定**（不是「语义等价」）。
        val first = WorldBookTool.schemas.map { it.toString() }
        val second = WorldBookTool.schemas.map { it.toString() }
        assertEquals("同一进程内两次取必须逐字节相同", first, second)

        // 键序必须固定（buildJsonObject 的插入顺序 = 序列化顺序）
        val fn = WorldBookTool.schema["function"]!!.jsonObject
        assertEquals(
            "function 的键序固定为 name/description/parameters",
            listOf("name", "description", "parameters"),
            fn.keys.toList(),
        )
        assertEquals(
            "parameters 的键序固定为 type/properties/required",
            listOf("type", "properties", "required"),
            fn["parameters"]!!.jsonObject.keys.toList(),
        )
    }

    @Test
    fun `工具描述里不含会随轮次变的内容`() {
        // 若描述里塞了「当前有 3 条世界书」这类动态信息，tools 就会逐轮变形 → 缓存全断。
        val text = WorldBookTool.schema.toString()
        listOf("本轮", "条已启用").forEach {
            assertTrue("schema 里不该出现随时间变的内容：$it", !text.contains(it))
        }
    }
}

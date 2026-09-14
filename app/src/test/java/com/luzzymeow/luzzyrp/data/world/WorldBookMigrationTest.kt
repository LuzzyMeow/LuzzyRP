package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 世界书 v3.1：**新字段读写** + **旧两桶 → 多书迁移** 的纯逻辑单测。
 *
 * 全部 JVM（无 Android / 无 Room）：迁移计划与字段解析都是纯函数。
 */
class WorldBookMigrationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun entry(comment: String, content: String, keys: String = ""): JsonObject = JsonObject(
        mapOf(
            "comment" to JsonPrimitive(comment),
            "content" to JsonPrimitive(content),
            "keys" to JsonArray(keys.takeIf { it.isNotBlank() }?.split(',')?.map { JsonPrimitive(it.trim()) } ?: emptyList()),
        ),
    )

    // ---------------------------------------------------------------- 迁移计划

    @Test
    fun `已迁移过就跳过（幂等）`() {
        val plan = WorldBookMigration.plan(
            alreadyMigrated = true,
            globalWorldInfo = listOf(entry("A", "a")),
            characterWorldInfos = listOf(Triple("u1", "Vanio", listOf(entry("B", "b")))),
        )
        assertTrue("已迁移过应跳过", plan.skipped)
        assertEquals(0, plan.books.size)
    }

    @Test
    fun `全局桶建默认世界书，角色内嵌建角色名下的书`() {
        val plan = WorldBookMigration.plan(
            alreadyMigrated = false,
            globalWorldInfo = listOf(entry("全球1", "c1"), entry("全球2", "c2")),
            characterWorldInfos = listOf(
                Triple("u1", "Vanio", listOf(entry("角色1", "x"))),
                Triple("u2", "Luna", emptyList()),
            ),
        )
        assertEquals("全局 1 本 + 有内容的角色 1 本", 2, plan.books.size)

        val global = plan.books[0]
        assertEquals(WorldBookMigration.DEFAULT_BOOK_NAME, global.name)
        assertEquals(2, global.entries.size)
        assertNull("全局书不绑角色", global.bindToCharacterUuid)

        val character = plan.books[1]
        assertEquals("Vanio 的世界书", character.name)
        assertEquals("u1", character.bindToCharacterUuid)

        assertFalse("空数组的角色不该建书", plan.books.any { it.name.contains("Luna") })
    }

    @Test
    fun `什么都没有时也返回空计划（调用方据此落标记）`() {
        val plan = WorldBookMigration.plan(false, emptyList(), emptyList())
        assertFalse(plan.skipped)
        assertEquals(0, plan.books.size)
    }

    @Test
    fun `条目顺序原样保留（不重排）`() {
        val plan = WorldBookMigration.plan(
            false,
            listOf(entry("第一条", "1"), entry("第二条", "2"), entry("第三条", "3")),
            emptyList(),
        )
        val names = plan.books[0].entries.map { (it as JsonObject)["comment"]!!.let { c -> (c as JsonPrimitive).content } }
        assertEquals(listOf("第一条", "第二条", "第三条"), names)
    }

    // ---------------------------------------------------------------- 角色卡 worldInfo 读取

    @Test
    fun `worldInfoOf 认顶层与 data 外壳两种形态`() {
        val top = """{"worldInfo":[{"comment":"A","content":"a"}]}"""
        assertEquals(1, WorldBookMigration.worldInfoOf(top).size)

        val wrapped = """{"data":{"name":"X","worldInfo":[{"comment":"B","content":"b"},{"comment":"C","content":"c"}]}}"""
        assertEquals(2, WorldBookMigration.worldInfoOf(wrapped).size)

        assertEquals(0, WorldBookMigration.worldInfoOf("""{"name":"没有世界书"}""").size)
        assertEquals(0, WorldBookMigration.worldInfoOf(null).size)
        assertEquals("坏 JSON 不该抛", 0, WorldBookMigration.worldInfoOf("{不是JSON").size)
    }

    // ---------------------------------------------------------------- 导入格式识别

    @Test
    fun `导入认三种形态——裸数组、ST entries 映射、CCv3 角色书`() {
        val bare = """[{"comment":"A"},{"comment":"B"}]"""
        assertEquals(2, WorldBookFormats.parseEntries(bare)!!.size)

        // ST：entries 是 uid → 条目的对象映射（按数值序展开，不是字典序）
        val st = """{"entries":{"10":{"comment":"十"},"2":{"comment":"二"},"0":{"comment":"零"}}}"""
        val stEntries = WorldBookFormats.parseEntries(st)!!
        assertEquals(3, stEntries.size)
        assertEquals(
            "按 uid 数值序，不是字典序",
            listOf("零", "二", "十"),
            stEntries.map { ((it as JsonObject)["comment"] as JsonPrimitive).content },
        )

        val ccv3 = """{"character_book":{"name":"书","entries":[{"comment":"A"}]}}"""
        assertEquals(1, WorldBookFormats.parseEntries(ccv3)!!.size)

        val nested = """{"data":{"character_book":{"entries":[{"comment":"A"},{"comment":"B"}]}}}"""
        assertEquals(2, WorldBookFormats.parseEntries(nested)!!.size)
    }

    @Test
    fun `导入解析不出时返回 null 而不是空列表（不许静默成功）`() {
        assertNull(WorldBookFormats.parseEntries("{不是 JSON"))
        assertNull(WorldBookFormats.parseEntries("""{"name":"有名字但没条目"}"""))
        assertNull(WorldBookFormats.parseEntries(""""就是一句话""""))
    }

    @Test
    fun `parseName 认我们的导出与 CCv3，ST 无名字时回 null`() {
        assertEquals("我的书", WorldBookFormats.parseName("""{"name":"我的书","entries":[]}"""))
        assertEquals("角色书", WorldBookFormats.parseName("""{"character_book":{"name":"角色书","entries":[]}}"""))
        assertNull("ST 文件本身不存书名", WorldBookFormats.parseName("""{"entries":{"0":{"comment":"A"}}}"""))
    }

    // ---------------------------------------------------------------- 新字段（v3.1）

    @Test
    fun `新字段读写往返——次级关键词 四逻辑 大小写 全词 角色 定时效果`() {
        val entry = WorldEntry(
            comment = "测试",
            content = "内容",
            keys = listOf("主键"),
            secondaryKeys = listOf("次键1", "次键2"),
            secondaryLogic = SecondaryLogic.AndAll,
            caseSensitive = true,
            matchWholeWords = true,
            depthRole = DepthRole.Assistant,
            sticky = 3,
            cooldown = 2,
            delay = 1,
        )
        val payload = entry.mergeInto(JsonObject(emptyMap()))
        val read = WorldEntry.from(payload)

        assertEquals(listOf("次键1", "次键2"), read.secondaryKeys)
        assertEquals(SecondaryLogic.AndAll, read.secondaryLogic)
        assertTrue(read.caseSensitive)
        assertTrue(read.matchWholeWords)
        assertEquals(DepthRole.Assistant, read.depthRole)
        assertEquals(3, read.sticky)
        assertEquals(2, read.cooldown)
        assertEquals(1, read.delay)
    }

    @Test
    fun `旧数据缺新字段时给安全默认（全词默认关=中文友好）`() {
        val legacy = """{"comment":"老条目","content":"旧内容","keys":["k"]}"""
        val entry = WorldEntry.from(json.parseToJsonElement(legacy))

        assertEquals(emptyList<String>(), entry.secondaryKeys)
        assertEquals(SecondaryLogic.AndAny, entry.secondaryLogic)
        assertFalse(entry.caseSensitive)
        assertFalse("全词默认关（有意偏离 ST，中文友好）", entry.matchWholeWords)
        assertEquals(DepthRole.System, entry.depthRole)
        assertEquals(0, entry.sticky)
        assertEquals(0, entry.cooldown)
        assertEquals(0, entry.delay)
    }

    @Test
    fun `认 ST 原生键名——keysecondary  selectiveLogic  role`() {
        val stEntry = """
            {"comment":"ST 条目","content":"c","key":["主"],"keysecondary":["次"],
             "selectiveLogic":1,"caseSensitive":true,"matchWholeWords":true,"role":"assistant"}
        """.trimIndent()
        val entry = WorldEntry.from(json.parseToJsonElement(stEntry))

        assertEquals(listOf("主"), entry.keys)
        assertEquals(listOf("次"), entry.secondaryKeys)
        assertEquals("selectiveLogic=1 → NOT_ALL", SecondaryLogic.NotAll, entry.secondaryLogic)
        assertTrue(entry.caseSensitive)
        assertTrue(entry.matchWholeWords)
        assertEquals("role=assistant → Assistant", DepthRole.Assistant, entry.depthRole)
    }

    @Test
    fun `写回用规范名并清掉 ST 别名（避免别名优先导致改了没生效）`() {
        val stEntry = json.parseToJsonElement("""{"comment":"x","keysecondary":["旧"],"selectiveLogic":2}""")
        val payload = WorldEntry.from(stEntry).copy(secondaryKeys = listOf("新")).mergeInto(stEntry)

        assertFalse("keysecondary 别名必须删掉", payload.containsKey("keysecondary"))
        assertFalse("selectiveLogic 别名必须删掉", payload.containsKey("selectiveLogic"))
        assertEquals(listOf("新"), WorldEntry.from(payload).secondaryKeys)
        assertEquals(SecondaryLogic.NotAny, WorldEntry.from(payload).secondaryLogic)
    }

    @Test
    fun `次级逻辑数字映射与非法值回落`() {
        assertEquals(SecondaryLogic.AndAny, SecondaryLogic.fromRaw(JsonPrimitive(0)))
        assertEquals(SecondaryLogic.NotAll, SecondaryLogic.fromRaw(JsonPrimitive(1)))
        assertEquals(SecondaryLogic.NotAny, SecondaryLogic.fromRaw(JsonPrimitive(2)))
        assertEquals(SecondaryLogic.AndAll, SecondaryLogic.fromRaw(JsonPrimitive(3)))
        assertEquals("越界数字回落 AND_ANY", SecondaryLogic.AndAny, SecondaryLogic.fromRaw(JsonPrimitive(99)))
        assertEquals("认不出的字符串回落", SecondaryLogic.AndAny, SecondaryLogic.fromRaw(JsonPrimitive("wat")))
    }

    // ---------------------------------------------------------------- 导出

    @Test
    fun `导出形态是 ST 可读的 name + entries`() {
        val book = LoreBook(
            id = "b1",
            name = "我的书",
            entries = listOf(WorldEntry(comment = "A", content = "a", keys = listOf("k"))),
        )
        val exported = LoreBook.toExportJson(book)
        assertEquals("我的书", (exported["name"] as JsonPrimitive).content)
        val entries = exported["entries"] as JsonArray
        assertEquals(1, entries.size)
        assertEquals("A", ((entries[0] as JsonObject)["comment"] as JsonPrimitive).content)

        // 导出的东西要能被自己读回来（round-trip）
        assertNotNull(WorldBookFormats.entriesOf(exported))
    }

    @Test
    fun `新书默认名与 id 生成`() {
        assertEquals("未命名世界书", LoreBook.DEFAULT_NAME)
        val a = LoreBook.newId()
        val b = LoreBook.newId()
        assertTrue(a.startsWith("book-"))
        assertTrue("两次生成的 id 不该相同", a != b)
        assertEquals("缺 payload 时回落默认名", "未命名世界书", LoreBook.from("x", null).name)
    }
}

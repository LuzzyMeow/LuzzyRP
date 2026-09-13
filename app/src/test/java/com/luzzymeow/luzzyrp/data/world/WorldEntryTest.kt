package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 世界书类型化层单测（W1）。**喂真实夹具的 payload**，不是手写样本——
 * 于是「上游那套别名/默认值/组合视图规则我们真的认」是可回归的。
 */
class WorldEntryTest {

    private val fixture: JsonObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    private fun fixtureArray(rawKey: String): JsonArray =
        fixture["databases"]!!.jsonObject["RPHubDB"]!!.jsonObject["entries"]!!.jsonObject[rawKey]!!.jsonArray

    /** 真实全局条目（夹具里 2 条：自动生图 / 全局：语言风格）。 */
    private val realGlobal: List<JsonElement> get() = fixtureArray("rp_hub_global_worldinfo")

    /** 真实角色绑定条目（Vanio 的「旧书店的规矩」）。 */
    private val realCharacter: List<JsonElement>
        get() = fixtureArray("rp_hub_characters")[0].jsonObject["worldInfo"]!!.jsonArray

    // ---------------------------------------------------------------- 读取

    @Test
    fun `空对象回落到上游默认值`() {
        val entry = WorldEntry.from(JsonObject(emptyMap()))
        assertEquals("", entry.comment)
        assertEquals("", entry.content)
        assertTrue(entry.keys.isEmpty())
        assertTrue(entry.enabled)
        assertEquals(WorldScope.Character, entry.scope)
        assertEquals(WorldPosition.AtDepth, entry.position)
        assertEquals(0, entry.order)
        assertEquals(4, entry.depth)
        assertNull("缺省 = 继承全局扫描深度", entry.scanDepth)
        assertEquals(100, entry.probability)
        assertTrue(entry.useProbability)
        assertFalse(entry.useRegex)
        assertFalse(entry.constant)
    }

    @Test
    fun `真实全局条目逐字段读出`() {
        val first = WorldEntry.from(realGlobal[0])
        assertEquals("自动生图", first.comment)
        assertEquals(WorldScope.Global, first.scope)
        assertFalse("夹具里这条是关掉的", first.enabled)
        assertTrue("正文很长，不能截断", first.content.length > 3000)

        val second = WorldEntry.from(realGlobal[1])
        assertEquals("全局：语言风格", second.comment)
        assertTrue(second.enabled)
    }

    @Test
    fun `真实角色绑定条目读出 scope=character`() {
        val entry = WorldEntry.from(realCharacter[0])
        assertEquals("旧书店的规矩", entry.comment)
        assertEquals(WorldScope.Character, entry.scope)
    }

    @Test
    fun `keys 认两种逗号并去掉空项`() {
        val entry = WorldEntry.from(
            JsonObject(mapOf("keys" to JsonPrimitive("苹果, 钟楼， 恶魔果子 ,, "))),
        )
        assertEquals(listOf("苹果", "钟楼", "恶魔果子"), entry.keys)
    }

    @Test
    fun `disable 别名取反`() {
        val disabled = WorldEntry.from(JsonObject(mapOf("disable" to JsonPrimitive(true))))
        assertFalse(disabled.enabled)
        val enabledExplicitly = WorldEntry.from(
            JsonObject(mapOf("enabled" to JsonPrimitive(true), "disabled" to JsonPrimitive(true))),
        )
        assertFalse("enabled=true 但 disabled=true → 关（上游同）", enabledExplicitly.enabled)
    }

    @Test
    fun `extensions 摊平且覆盖同名顶层键`() {
        val entry = WorldEntry.from(
            JsonObject(
                mapOf(
                    "depth" to JsonPrimitive(9),
                    "extensions" to JsonObject(
                        mapOf("depth" to JsonPrimitive(2), "customFlag" to JsonPrimitive("keep-me")),
                    ),
                ),
            ),
        )
        assertEquals("extensions 的值覆盖顶层（上游顺序如此）", 2, entry.depth)
    }

    @Test
    fun `position 认别名与数字映射`() {
        assertEquals(WorldPosition.BeforeChar, WorldPosition.fromRaw(JsonPrimitive("before_character")))
        assertEquals(WorldPosition.GlobalNote, WorldPosition.fromRaw(JsonPrimitive("an_top")))
        assertEquals(WorldPosition.GlobalNote, WorldPosition.fromRaw(JsonPrimitive("Author Note")))
        assertEquals(WorldPosition.GlobalNote, WorldPosition.fromRaw(JsonPrimitive(2)))
        assertEquals(WorldPosition.AtDepth, WorldPosition.fromRaw(JsonPrimitive(4)))
        assertEquals(WorldPosition.AtDepth, WorldPosition.fromRaw(JsonPrimitive("nonsense")))
        assertEquals(WorldPosition.AtDepth, WorldPosition.fromRaw(JsonNull))
        assertEquals(WorldPosition.UserTop, WorldPosition.fromRaw(JsonPrimitive("user_top")))
    }

    // ---------------------------------------------------------------- 写入

    @Test
    fun `mergeInto 保留未暴露的键`() {
        val original = JsonObject(
            mapOf(
                "comment" to JsonPrimitive("旧名"),
                "unknownField" to JsonPrimitive("不要丢我"),
                "customNumber" to JsonPrimitive(42),
                "nested" to JsonObject(mapOf("a" to JsonPrimitive(1))),
            ),
        )
        val merged = WorldEntry.from(original).copy(comment = "新名").mergeInto(original)
        assertEquals("新名", (merged["comment"] as JsonPrimitive).content)
        assertEquals("不要丢我", (merged["unknownField"] as JsonPrimitive).content)
        assertEquals("42", (merged["customNumber"] as JsonPrimitive).content)
        assertEquals("1", ((merged["nested"] as JsonObject)["a"] as JsonPrimitive).content)
    }

    @Test
    fun `mergeInto 把别名收成规范名并删掉别名`() {
        val original = JsonObject(
            mapOf(
                "use_regex" to JsonPrimitive(true),
                "insertion_order" to JsonPrimitive(7),
                "scan_depth" to JsonPrimitive(3),
                "disable" to JsonPrimitive(true),
                "extensions" to JsonObject(mapOf("foo" to JsonPrimitive("bar"))),
            ),
        )
        val entry = WorldEntry.from(original).copy(useRegex = false, order = 1, scanDepth = null, enabled = true)
        val merged = entry.mergeInto(original)

        assertFalse("别名必须删掉：否则上游读值优先命中旧别名 → 改了没生效", merged.containsKey("use_regex"))
        assertFalse(merged.containsKey("insertion_order"))
        assertFalse(merged.containsKey("scan_depth"))
        assertFalse(merged.containsKey("disable"))
        assertFalse(merged.containsKey("extensions"))
        assertEquals("false", (merged["useRegex"] as JsonPrimitive).content)
        assertEquals("1", (merged["order"] as JsonPrimitive).content)
        assertTrue("scanDepth=null 要写成 JSON null（=继承全局）", merged["scanDepth"] is JsonNull)
        assertEquals("true", (merged["enabled"] as JsonPrimitive).content)
        assertEquals("extensions 摊平后仍保留", "bar", (merged["foo"] as JsonPrimitive).content)
    }

    @Test
    fun `setEnabled 只改一个键且清掉 disable 别名`() {
        val buckets = WorldBookOps.Buckets(global = realGlobal)
        val once = WorldBookOps.setEnabled(buckets, EntryRef(WorldScope.Global, 0), true)
        val entry = WorldEntry.from(once.global[0])
        assertTrue(entry.enabled)
        assertFalse("别名要清掉，否则读回还是关", (once.global[0] as JsonObject).containsKey("disable"))

        // 其它键不许被顺手重排：整体只在 enabled 上有差
        val before = realGlobal[0] as JsonObject
        val after = once.global[0] as JsonObject
        assertEquals(before.keys, after.keys)
    }

    // ---------------------------------------------------------------- 数组操作

    @Test
    fun `有效归属：角色卡里 scope=global 的条目按全局展示`() {
        val inCardButGlobal = JsonObject(
            mapOf("comment" to JsonPrimitive("住错桶的条目"), "scope" to JsonPrimitive("global")),
        )
        val systemNamed = JsonObject(mapOf("comment" to JsonPrimitive("自动生图")))
        assertEquals(WorldScope.Global, WorldBookOps.effectiveScope(inCardButGlobal))
        assertEquals(WorldScope.Global, WorldBookOps.effectiveScope(systemNamed))
        assertEquals(WorldScope.Character, WorldBookOps.effectiveScope(realCharacter[0]))

        val grouped = WorldBookOps.groupByEffectiveScope(
            WorldBookOps.Buckets(global = realGlobal, character = realCharacter + inCardButGlobal),
        )
        assertEquals("全局 2 条 + 住错桶的 1 条", 3, grouped[WorldScope.Global]!!.size)
        assertEquals("角色桶里只剩真绑定那条", 1, grouped[WorldScope.Character]!!.size)
    }

    @Test
    fun `upsert 新增追加到目标桶末尾`() {
        val buckets = WorldBookOps.Buckets(global = realGlobal, character = realCharacter)
        val added = WorldBookOps.upsert(
            buckets,
            ref = null,
            entry = WorldEntry(comment = "新条目", keys = listOf("雾"), scope = WorldScope.Character),
        )
        assertEquals("新增到角色桶，全局不受影响", 2, added.global.size)
        assertEquals(2, added.character.size)
        assertEquals("新条目", WorldEntry.from(added.character.last()).comment)
        assertEquals(listOf("雾"), WorldEntry.from(added.character.last()).keys)
    }

    @Test
    fun `upsert 改归属 = 跨桶移动且落到目标末尾`() {
        val buckets = WorldBookOps.Buckets(global = realGlobal, character = realCharacter)
        val moved = WorldBookOps.upsert(
            buckets,
            ref = EntryRef(WorldScope.Character, 0),
            entry = WorldEntry(comment = "旧书店的规矩", scope = WorldScope.Global),
        )
        assertEquals("角色桶清空", 0, moved.character.size)
        assertEquals(3, moved.global.size)
        assertEquals("追加到全局末尾", "旧书店的规矩", WorldEntry.from(moved.global.last()).comment)
    }

    @Test
    fun `upsert 原地保存不动其它条目`() {
        val buckets = WorldBookOps.Buckets(global = realGlobal)
        val edited = WorldBookOps.upsert(
            buckets,
            ref = EntryRef(WorldScope.Global, 1),
            entry = WorldEntry.from(realGlobal[1]).copy(comment = "全局：语言风格（改过）", order = 5),
        )
        assertEquals("0 号条目的 payload 必须逐字未动", realGlobal[0], edited.global[0])
        assertEquals("全局：语言风格（改过）", WorldEntry.from(edited.global[1]).comment)
        assertEquals(5, WorldEntry.from(edited.global[1]).order)
    }

    @Test
    fun `remove 删除指定下标`() {
        val buckets = WorldBookOps.Buckets(global = realGlobal)
        val after = WorldBookOps.remove(buckets, EntryRef(WorldScope.Global, 0))
        assertEquals(1, after.global.size)
        assertEquals("全局：语言风格", WorldEntry.from(after.global[0]).comment)
    }

    @Test
    fun `move 交换相邻项且越界安全`() {
        val buckets = WorldBookOps.Buckets(character = realCharacter)
        val down = WorldBookOps.move(buckets, EntryRef(WorldScope.Character, 0), +1)
        assertEquals("越界时原样返回（按钮本就不该越界）", buckets, down)

        val two = WorldBookOps.Buckets(
            character = listOf(
                JsonObject(mapOf("comment" to JsonPrimitive("A"))),
                JsonObject(mapOf("comment" to JsonPrimitive("B"))),
            ),
        )
        val swapped = WorldBookOps.move(two, EntryRef(WorldScope.Character, 0), +1)
        assertEquals(listOf("B", "A"), swapped.character.map { WorldEntry.from(it).comment })
        assertEquals(listOf("A", "B"), WorldBookOps.move(swapped, EntryRef(WorldScope.Character, 1), -1)
            .character.map { WorldEntry.from(it).comment })
    }

    @Test
    fun `triggerSummary 说清怎么触发`() {
        assertEquals("常驻：不匹配关键词", WorldEntry(constant = true).triggerSummary)
        assertEquals("无关键词", WorldEntry().triggerSummary)
        assertEquals("正则：雾.*灯", WorldEntry(keys = listOf("雾.*灯"), useRegex = true).triggerSummary)
        assertEquals("关键词：苹果 / 钟楼", WorldEntry(keys = listOf("苹果", "钟楼")).triggerSummary)
        assertTrue(WorldEntry(keys = emptyList()).neverTriggers)
        assertFalse(WorldEntry(keys = emptyList(), constant = true).neverTriggers)
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
    }
}

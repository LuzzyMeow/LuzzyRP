package com.luzzymeow.luzzyrp.data.preset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预设类型化层单测（W1）。夹具里的 19 条是**真实**预设（含 3.7KB 的「破限」），
 * 所以「字段读对了吗、写入有没有伤到别的键」是可回归的，不必起模拟器。
 */
class PresetEntryTest {

    private val fixture: JsonObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    /** 真实预设数组（夹具里 19 条）。 */
    private val real: JsonArray
        get() = fixture["databases"]!!.jsonObject["RPHubDB"]!!.jsonObject["entries"]!!.jsonObject["rp_hub_presets"]!!.jsonArray

    @Test
    fun `真实预设逐字段读出`() {
        assertEquals(19, real.size)
        val first = PresetEntry.from(real[0])
        assertEquals("破限", first.name)
        assertEquals(PresetRole.System, first.role)
        assertTrue(first.enabled)
        assertEquals("真实长度 1063 字；断言只钉「没被截断」", 1063, first.content.length)
        assertFalse(first.ignoredByAssembly)
    }

    @Test
    fun `真实预设里三种 role 与关掉的条目都在`() {
        val all = real.map { PresetEntry.from(it) }
        assertTrue("夹具里确有 user 消息型预设（破限预注入）", all.any { it.role == PresetRole.User })
        assertTrue("夹具里确有 assistant 消息型预设", all.any { it.role == PresetRole.Assistant })
        assertEquals("第三人称 这条是关着的", listOf("第三人称"), all.filter { !it.enabled }.map { it.name })
    }

    @Test
    fun `role 白名单外回落 system`() {
        val odd = PresetEntry.from(JsonObject(mapOf("role" to JsonPrimitive("hacker"))))
        assertEquals(PresetRole.System, odd.role)
        assertEquals(PresetRole.User, PresetEntry.from(JsonObject(mapOf("role" to JsonPrimitive("user")))).role)
        assertEquals(PresetRole.Assistant, PresetEntry.from(JsonObject(mapOf("role" to JsonPrimitive("assistant")))).role)
    }

    @Test
    fun `role 认 presetRole 与 type 别名`() {
        assertEquals(PresetRole.User, PresetEntry.from(JsonObject(mapOf("presetRole" to JsonPrimitive("user")))).role)
        assertEquals(PresetRole.Assistant, PresetEntry.from(JsonObject(mapOf("type" to JsonPrimitive("assistant")))).role)
    }

    @Test
    fun `enabled 只在显式 false 时算关`() {
        assertTrue(PresetEntry.from(JsonObject(emptyMap())).enabled)
        assertTrue(PresetEntry.from(JsonObject(mapOf("enabled" to JsonPrimitive("true")))).enabled)
        assertFalse(PresetEntry.from(JsonObject(mapOf("enabled" to JsonPrimitive(false)))).enabled)
        assertFalse(PresetEntry.from(JsonObject(mapOf("enabled" to JsonPrimitive("false")))).enabled)
    }

    @Test
    fun `空名字给出可读显示名而不是英文默认`() {
        val blank = PresetEntry(role = PresetRole.User)
        assertEquals("未命名条目", blank.displayName)
        assertEquals("（正文为空）", blank.summary)
        assertTrue(blank.ignoredByAssembly)

        assertEquals("第一行", PresetEntry(content = "第一行\n第二行").summary)
        assertEquals("跳过空行", PresetEntry(content = "\n\n  跳过空行  \n").summary)
    }

    @Test
    fun `mergeInto 保留未暴露的键`() {
        val original = JsonObject(
            mapOf(
                "name" to JsonPrimitive("旧名"),
                "someFutureField" to JsonPrimitive("keep"),
                "nested" to JsonObject(mapOf("k" to JsonPrimitive(1))),
            ),
        )
        val merged = PresetEntry.from(original).copy(name = "新名", role = PresetRole.User).mergeInto(original)
        assertEquals("新名", (merged["name"] as JsonPrimitive).content)
        assertEquals("user", (merged["role"] as JsonPrimitive).content)
        assertEquals("keep", (merged["someFutureField"] as JsonPrimitive).content)
        assertEquals("1", ((merged["nested"] as JsonObject)["k"] as JsonPrimitive).content)
    }

    @Test
    fun `upsert 新增追加到末尾、覆盖只动那一条`() {
        val appended = PresetOps.upsert(real, ref = null, entry = PresetEntry(name = "新预设", role = PresetRole.User))
        assertEquals(20, appended.size)
        assertEquals("新预设", PresetEntry.from(appended.last()).name)
        assertEquals("前面 19 条逐字未动", real.toList(), appended.dropLast(1))

        val replaced = PresetOps.upsert(real, ref = 0, entry = PresetEntry.from(real[0]).copy(name = "破限（改）"))
        assertEquals(19, replaced.size)
        assertEquals("破限（改）", PresetEntry.from(replaced[0]).name)
        assertEquals("第二条没被动", real[1], replaced[1])
    }

    @Test
    fun `setEnabled 只改一个键`() {
        val base = real[0] as JsonObject
        val off = PresetOps.setEnabled(real, 0, false)
        val after = off[0] as JsonObject
        assertEquals("false", (after["enabled"] as JsonPrimitive).content)
        assertEquals("键集合不变", base.keys, after.keys)
        assertTrue(PresetOps.setEnabled(off, 0, true).let { PresetEntry.from(it[0]).enabled })
    }

    @Test
    fun `remove 与 move 与越界安全`() {
        val removed = PresetOps.remove(real, 0)
        assertEquals(18, removed.size)
        assertEquals("破限被删掉后第一条是原来的第二条", real[1], removed[0])
        assertEquals("越界删除原样返回", real, PresetOps.remove(real, 99))

        val swapped = PresetOps.move(real, 0, +1)
        assertEquals(real[0], swapped[1])
        assertEquals(real[1], swapped[0])
        assertEquals("上越界原样返回", real, PresetOps.move(real, 0, -1))
        assertEquals("下越界原样返回", real, PresetOps.move(real, real.size - 1, +1))
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
    }
}

package com.luzzymeow.luzzyrp.data.transfer

import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2 导入导出的 round-trip 判据（纯函数层）：导出→导入→语义相等。
 * 覆盖三种文件形态：records 原样数组（预设/世界书）、带 data 外壳的 V2 卡、顶层平铺的旧形态卡。
 */
class TransferFormatTest {

    // ---------------- 预设 / 世界书 ----------------

    @Test
    fun `records 导出导入往返逐元素相等`() {
        val records = listOf(
            JsonObject(
                mapOf(
                    "identifier" to JsonPrimitive("破限预设"),
                    "prompts" to JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("主要：中文"))))),
                    "temperature" to JsonPrimitive(0.9),
                ),
            ),
            JsonObject(mapOf("identifier" to JsonPrimitive("含引号\"与反斜杠\\的内容"))),
        )
        val text = TransferFormat.exportRecords(records)
        assertEquals(records, TransferFormat.importRecords(text))
    }

    @Test
    fun `导入顶层不是数组时明确报错`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TransferFormat.importRecords("""{"a":1}""")
        }
        assertTrue(ex.message!!.contains("数组"))
    }

    @Test
    fun `导入坏 JSON 时明确报错不静默`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferFormat.importRecords("这不是 JSON")
        }
    }

    // ---------------- 角色卡 ----------------

    private fun v2Card(uuid: String?) = CharacterEntity(
        uuid = uuid ?: "hash-derived-uuid",
        name = "谢昭",
        avatarPath = "avatars/abc.jpg",
        createdAt = 1726000000000L,
        payload = """{"spec":"chara_card_v2","data":{"name":"谢昭","description":"旧书店的老板","first_mes":"「来了？」"}}""",
    )

    private fun flatCard() = CharacterEntity(
        uuid = "flat-uuid",
        name = "夏梧",
        avatarPath = null,
        createdAt = 1726000000001L,
        payload = """{"name":"夏梧","description":"顶层平铺形态"}""",
    )

    @Test
    fun `导出注入 uuid——迁移器补的身份必须随卡走`() {
        // payload 里没有 uuid 键（迁移器用内容哈希补在表主键上）
        val text = TransferFormat.exportCharacters(listOf(v2Card(null)))
        val parsed = TransferFormat.importCharacters(text).single()
        assertEquals("hash-derived-uuid", parsed.uuid)
    }

    @Test
    fun `角色卡导出导入往返字段相等`() {
        val rows = listOf(v2Card(null), flatCard())
        val round = TransferFormat.importCharacters(TransferFormat.exportCharacters(rows))
        assertEquals(rows.map { it.uuid }, round.map { it.uuid })
        assertEquals(rows.map { it.name }, round.map { it.name })
        // payload 语义相等 = 原字段逐键保持 + 注入 uuid（uuid 是设计意图：身份随卡走）
        round.zip(rows).forEach { (got, want) ->
            val obj = Json.parseToJsonElement(got.payload).jsonObject
            val original = Json.parseToJsonElement(want.payload).jsonObject
            val gotMap = obj.entries.associate { it.key to it.value }
            val wantMap = original.entries.associate { it.key to it.value }
            assertEquals(wantMap + ("uuid" to JsonPrimitive(want.uuid)), gotMap)
        }
    }

    @Test
    fun `单对象导入也被接受（上游单卡导出形态）`() {
        val text = TransferFormat.exportCharacters(listOf(v2Card("one")))
            .removePrefix("[").removeSuffix("]")
        assertEquals("one", TransferFormat.importCharacters(text).single().uuid)
    }

    @Test
    fun `缺 name 的条目明确报错`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TransferFormat.importCharacters("""{"description":"没有名字的卡"}""")
        }
        assertTrue(ex.message!!.contains("name"))
    }

    @Test
    fun `缺 uuid 的外部卡生成新身份`() {
        val a = TransferFormat.importCharacters("""{"data":{"name":"外来卡"}}""").single()
        val b = TransferFormat.importCharacters("""{"data":{"name":"外来卡"}}""").single()
        assertTrue(a.uuid.isNotBlank())
        assertNotEquals("两次导入是两次新身份，绝不静默共用", a.uuid, b.uuid)
    }

    @Test
    fun `data 外壳与顶层平铺的 name 都认（与 characterViewOf 同一判据）`() {
        assertEquals("谢昭", TransferFormat.importCharacters(v2Card(null).payload).single().name)
        assertEquals("夏梧", TransferFormat.importCharacters(flatCard().payload).single().name)
    }
}

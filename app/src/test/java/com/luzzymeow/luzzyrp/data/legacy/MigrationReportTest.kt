package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3 迁移报告的解析判据（纯函数）：键集照 `MigrationWriter.markMigrated` 的写入形状；
 * 缺字段按 0 兜底（旧版本写入的键集可能更小），无记录返回 null（界面呈现「未迁移」）。
 */
class MigrationReportTest {

    private val counts = JsonObject(
        mapOf(
            "characters" to JsonPrimitive(2),
            "branches" to JsonPrimitive(3),
            "messages" to JsonPrimitive(6),
            "vectorMemories" to JsonPrimitive(4),
            "classicMemories" to JsonPrimitive(4),
            "worldEntries" to JsonPrimitive(2),
            "legacyWorldEntriesDropped" to JsonPrimitive(2),
            "regexes" to JsonPrimitive(1),
            "presets" to JsonPrimitive(18),
            "usage" to JsonPrimitive(9),
            "profiles" to JsonPrimitive(1),
            "assets" to JsonPrimitive(7),
        ),
    )

    @Test
    fun `完整 kv 解析出 12 项计数`() {
        val report = MigrationReport.parse(counts, "1726000000000")!!
        assertEquals(2, report.characters)
        assertEquals(3, report.branches)
        assertEquals(6, report.messages)
        assertEquals(4, report.vectorMemories)
        assertEquals(4, report.classicMemories)
        assertEquals(2, report.worldEntries)
        assertEquals(2, report.legacyWorldEntriesDropped)
        assertEquals(1, report.regexes)
        assertEquals(18, report.presets)
        assertEquals(9, report.usage)
        assertEquals(1, report.profiles)
        assertEquals(7, report.assets)
        assertEquals(12, report.rows().size)
    }

    @Test
    fun `无记录或非对象返回 null（不许渲染成空表）`() {
        assertNull(MigrationReport.parse(null, null))
        assertNull(MigrationReport.parse(JsonPrimitive(1), null))
    }

    @Test
    fun `缺字段按 0 兜底且时间缺失有占位`() {
        val report = MigrationReport.parse(JsonObject(mapOf("characters" to JsonPrimitive(5))), null)!!
        assertEquals(5, report.characters)
        assertEquals(0, report.presets)
        assertEquals("时间未知", report.timeText)
    }

    @Test
    fun `时间解析为本地可读文本`() {
        val report = MigrationReport.parse(counts, "1726000000000")!!
        assertTrue("1726000000000ms 在 2024 年（任何时区）", report.timeText.contains("2024"))
    }
}

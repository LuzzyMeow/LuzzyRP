package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEvent
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEventDraft
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPermissionException
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.ClipboardPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 日历工具单测（PLAN §12.2：T2 档、权限被拒的可操作提示、时间解析）。 */
class CalendarToolsTest {

    private class FakeCalendar(
        private val denied: Boolean = false,
    ) : CalendarPort {
        val inserted = mutableListOf<CalendarEventDraft>()
        val deleted = mutableListOf<String>()
        override suspend fun read(fromMillis: Long, toMillis: Long, query: String?, limit: Int): List<CalendarEvent> {
            if (denied) throw CalendarPermissionException("未授予「读取日历」权限")
            return listOf(
                CalendarEvent("1", "站会", fromMillis + 3_600_000, fromMillis + 7_200_000, "会议室", "每日站会", false)
            )
        }

        override suspend fun insert(event: CalendarEventDraft): String {
            if (denied) throw CalendarPermissionException("未授予「写入日历」权限")
            inserted += event
            return "42"
        }

        override suspend fun update(eventId: String, event: CalendarEventDraft): Boolean = !denied
        override suspend fun delete(eventId: String): Boolean {
            if (denied) throw CalendarPermissionException("未授予「写入日历」权限")
            deleted += eventId
            return true
        }
    }

    private class FakeCtx : ToolContext {
        override val assistantId = "a1"
        override val conversationId = "c1"
        override val workspace = object : WorkspaceAccess {
            override suspend fun list(relativeDir: String): List<WorkspaceEntry> = emptyList()
            override suspend fun read(relativePath: String): ByteArray = ByteArray(0)
            override suspend fun write(relativePath: String, bytes: ByteArray) {}
            override suspend fun delete(relativePath: String) {}
            override suspend fun move(fromRelative: String, toRelative: String) {}
            override suspend fun mkdir(relativeDir: String) {}
            override suspend fun exists(relativePath: String): Boolean = false
        }
        override val cancelled = { false }
        override val log: (String) -> Unit = {}
    }

    private fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    @Test
    fun `日历工具分级为 T2 且默认关闭`() {
        val tool = CalendarReadTool(FakeCalendar())
        assertEquals(ToolTier.T2_WRITE_DEVICE, tool.tier)
        assertTrue(!tool.tier.defaultEnabled)
        assertTrue(tool.tier.requiresApproval)
    }

    @Test
    fun `读取返回事件且带 id 与地点`() = runBlocking {
        val result = CalendarReadTool(FakeCalendar()).execute(
            args("from" to "2026-09-09T00:00", "to" to "2026-09-10T00:00"),
            FakeCtx(),
        )
        val text = (result as ToolResult.Ok).text
        assertTrue(text.contains("站会"))
        assertTrue(text.contains("会议室"))
        assertTrue(text.contains("id=1"))
    }

    @Test
    fun `权限被拒给出可操作提示`() = runBlocking {
        val result = CalendarReadTool(FakeCalendar(denied = true)).execute(
            args("from" to "2026-09-09T00:00", "to" to "2026-09-10T00:00"),
            FakeCtx(),
        )
        val error = result as ToolResult.Error
        assertTrue(error.message.contains("权限"))
        assertTrue(error.message.contains("系统设置"))
    }

    @Test
    fun `时间格式非法明确报错`() = runBlocking {
        val result = CalendarReadTool(FakeCalendar()).execute(
            args("from" to "昨天", "to" to "2026-09-10"),
            FakeCtx(),
        )
        assertTrue((result as ToolResult.Error).message.contains("无法解析"))
    }

    @Test
    fun `创建事件补默认时长与提醒`() = runBlocking {
        val calendar = FakeCalendar()
        val result = CalendarWriteTool(calendar).execute(
            args(
                "action" to "create",
                "title" to "评审",
                "start" to "2026-09-09T14:00",
                "location" to "线上",
            ),
            FakeCtx(),
        )
        assertTrue(result is ToolResult.Ok)
        assertEquals(1, calendar.inserted.size)
        val draft = calendar.inserted[0]
        assertEquals("评审", draft.title)
        assertEquals(3_600_000L, draft.endMillis - draft.startMillis) // 默认 1 小时
        assertEquals("线上", draft.location)
    }

    @Test
    fun `全天事件用日期与默认一天时长`() = runBlocking {
        val calendar = FakeCalendar()
        CalendarWriteTool(calendar).execute(
            args("action" to "create", "title" to "团建", "start" to "2026-09-09"),
            FakeCtx(),
        )
        val draft = calendar.inserted[0]
        assertTrue(draft.allDay)
        assertEquals(24L * 60 * 60 * 1000, draft.endMillis - draft.startMillis)
    }

    @Test
    fun `结束早于开始被拒绝`() = runBlocking {
        val result = CalendarWriteTool(FakeCalendar()).execute(
            args("action" to "create", "title" to "x", "start" to "2026-09-09T15:00", "end" to "2026-09-09T14:00"),
            FakeCtx(),
        )
        assertTrue((result as ToolResult.Error).message.contains("晚于"))
    }

    @Test
    fun `删除需 event_id`() = runBlocking {
        val calendar = FakeCalendar()
        val missing = CalendarWriteTool(calendar).execute(args("action" to "delete"), FakeCtx())
        assertTrue(missing is ToolResult.Error)
        val ok = CalendarWriteTool(calendar).execute(args("action" to "delete", "event_id" to "7"), FakeCtx())
        assertTrue(ok is ToolResult.Ok)
        assertEquals(listOf("7"), calendar.deleted)
    }

    @Test
    fun `不支持的 action 报错`() = runBlocking {
        val result = CalendarWriteTool(FakeCalendar()).execute(args("action" to "wipe"), FakeCtx())
        assertTrue((result as ToolResult.Error).message.contains("不支持的 action"))
    }

    @Test
    fun `时间解析覆盖三种格式`() {
        val zone = ZoneId.of("Asia/Shanghai")
        assertNotNull(parseTime("2026-09-09", zone))
        assertNotNull(parseTime("2026-09-09T14:00", zone))
        assertNotNull(parseTime("2026-09-09 14:00", zone))
        assertEquals(null, parseTime("2026/09/09", zone))
    }
}

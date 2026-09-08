package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEventDraft
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPermissionException
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 日历工具（PLAN §12.2，T2 档：**默认关闭 + 逐调用审批**）。
 *
 * 时间输入统一用 **ISO-8601 本地时间**（如 `2026-09-09T14:00`，缺时区按设备时区），
 * 也接受纯日期 `2026-09-09`（视为全天）。解析失败**明确报错**，不猜测。
 */
class CalendarReadTool(private val calendar: CalendarPort) : Tool {
    override val name = "calendar_read"
    override val description = "查询日历事件（标题/时间/地点/描述）。需要日历读取权限。"
    override val tier = ToolTier.T2_WRITE_DEVICE
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "from" to Schema.string("起始时间，ISO-8601（如 2026-09-09T00:00）或日期 2026-09-09"),
            "to" to Schema.string("结束时间，同上"),
            "query" to Schema.string("标题/描述关键词过滤（可选）"),
            "limit" to Schema.integer("返回条数上限，默认 50"),
        ),
        required = listOf("from", "to"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val zone = ZoneId.systemDefault()
        val from = parseTime(args.str("from") ?: return ToolResult.Error("缺少 from 参数"), zone)
            ?: return ToolResult.Error("from 时间格式无法解析（用 2026-09-09T14:00 或 2026-09-09）")
        val to = parseTime(args.str("to") ?: return ToolResult.Error("缺少 to 参数"), zone)
            ?: return ToolResult.Error("to 时间格式无法解析")
        val query = args.str("query")
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 50
        return try {
            val events = calendar.read(from, to, query, limit.coerceIn(1, 200))
            if (events.isEmpty()) ToolResult.Ok("（该时间段没有事件）")
            else ToolResult.Ok(events.joinToString("\n") { format(it, zone) })
        } catch (e: CalendarPermissionException) {
            ToolResult.Error("${e.message}（请到系统设置授予日历读取权限后重试）")
        } catch (e: Exception) {
            ToolResult.Error("查询日历失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun format(event: com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEvent, zone: ZoneId): String {
        val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(event.endMillis).atZone(zone)
        val time = if (event.allDay) start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "（全天）"
        else start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "–" + end.format(DateTimeFormatter.ofPattern("HH:mm"))
        val extra = listOfNotNull(event.location?.takeIf { it.isNotBlank() }, event.description?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        return "- [$time] ${event.title}${if (extra.isNotEmpty()) "（$extra）" else ""}（id=${event.id}）"
    }
}

/** 写日历（插入 / 更新 / 删除）。 */
class CalendarWriteTool(private val calendar: CalendarPort) : Tool {
    override val name = "calendar_write"
    override val description =
        "创建、更新或删除日历事件。action=create|update|delete；update/delete 需 event_id。删除需用户审批。"
    override val tier = ToolTier.T2_WRITE_DEVICE
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "action" to Schema.string("操作", enumValues = listOf("create", "update", "delete")),
            "event_id" to Schema.string("事件 id（update/delete 必填）"),
            "title" to Schema.string("标题（create/update 必填）"),
            "start" to Schema.string("开始时间，ISO-8601 或日期"),
            "end" to Schema.string("结束时间，ISO-8601 或日期"),
            "location" to Schema.string("地点（可选）"),
            "description" to Schema.string("描述（可选）"),
            "reminder_minutes" to Schema.integer("提前提醒分钟数（可选）"),
            "all_day" to Schema.boolean("是否全天，默认 false"),
        ),
        required = listOf("action"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val zone = ZoneId.systemDefault()
        val action = args.str("action") ?: return ToolResult.Error("缺少 action 参数")
        return try {
            when (action) {
                "delete" -> {
                    val id = args.str("event_id") ?: return ToolResult.Error("delete 需要 event_id")
                    if (calendar.delete(id)) ToolResult.Ok("已删除事件 $id") else ToolResult.Error("未找到事件 $id")
                }

                "create", "update" -> {
                    val title = args.str("title") ?: return ToolResult.Error("$action 需要 title")
                    val startRaw = args.str("start") ?: return ToolResult.Error("$action 需要 start")
                    val start = parseTime(startRaw, zone) ?: return ToolResult.Error("start 时间格式无法解析")
                    val allDay = args.bool("all_day") ?: startRaw.length == 10
                    val end = args.str("end")?.let { parseTime(it, zone) }
                        ?: start + if (allDay) DAY_MS else HOUR_MS
                    if (end <= start) return ToolResult.Error("end 必须晚于 start")
                    val draft = CalendarEventDraft(
                        title = title,
                        startMillis = start,
                        endMillis = end,
                        location = args.str("location"),
                        description = args.str("description"),
                        reminderMinutes = args["reminder_minutes"]?.jsonPrimitive?.intOrNull,
                        allDay = allDay,
                    )
                    if (action == "create") {
                        ToolResult.Ok("已创建事件（id=${calendar.insert(draft)}）：$title")
                    } else {
                        val id = args.str("event_id") ?: return ToolResult.Error("update 需要 event_id")
                        if (calendar.update(id, draft)) ToolResult.Ok("已更新事件 $id")
                        else ToolResult.Error("未找到事件 $id")
                    }
                }

                else -> ToolResult.Error("不支持的 action：$action（仅 create / update / delete）")
            }
        } catch (e: CalendarPermissionException) {
            ToolResult.Error("${e.message}（请到系统设置授予日历写入权限后重试）")
        } catch (e: Exception) {
            ToolResult.Error("日历操作失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val HOUR_MS = 60L * 60 * 1000
    }
}

/**
 * 解析时间：支持 `2026-09-09T14:00[:ss]` / `2026-09-09 14:00` / `2026-09-09`。
 * 无法解析返回 null（**不猜测**，由调用方报错）。
 */
internal fun parseTime(raw: String, zone: ZoneId): Long? {
    val text = raw.trim().replace(' ', 'T')
    return runCatching {
        when {
            text.length == 10 -> java.time.LocalDate.parse(text)
                .atStartOfDay(zone).toInstant().toEpochMilli()

            text.contains('T') -> java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

            else -> null
        }
    }.getOrNull()
}

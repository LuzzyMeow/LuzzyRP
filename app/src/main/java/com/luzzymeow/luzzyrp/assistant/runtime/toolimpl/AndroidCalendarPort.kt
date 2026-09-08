package com.luzzymeow.luzzyrp.assistant.runtime.toolimpl

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEvent
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarEventDraft
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPermissionException
import com.luzzymeow.luzzyrp.assistant.domain.tool.CalendarPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 日历读写（PLAN §12.2 `calendar_read` / `calendar_write`）。
 *
 * **权限**：`READ_CALENDAR` / `WRITE_CALENDAR` 为运行时权限，未授权时抛
 * [CalendarPermissionException]（工具层转成「请去系统设置授权」的可操作提示，不静默返回空）。
 *
 * **删除需审批**：审批由 [com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate]（T2 逐调用）保证，
 * 本层只做数据操作。
 *
 * 写入目标：**用户的主日历**（`CalendarContract.Calendars.IS_PRIMARY`），找不到时用第一个可写日历；
 * 提醒通过 `CalendarContract.Reminders` 单独插入。
 */
class AndroidCalendarPort(private val context: Context) : CalendarPort {

    override suspend fun read(
        fromMillis: Long,
        toMillis: Long,
        query: String?,
        limit: Int,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        requirePermission(Manifest.permission.READ_CALENDAR, "读取日历")
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection = buildString {
            append("${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.END} <= ?")
            if (!query.isNullOrBlank()) {
                append(" AND (${CalendarContract.Instances.TITLE} LIKE ? OR ${CalendarContract.Instances.DESCRIPTION} LIKE ?)")
            }
        }
        val args = buildList {
            add(fromMillis.toString())
            add(toMillis.toString())
            if (!query.isNullOrBlank()) {
                add("%$query%")
                add("%$query%")
            }
        }.toTypedArray()

        val out = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(fromMillis.toString())
                .appendPath(toMillis.toString())
                .build(),
            projection,
            selection,
            args,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (cursor.moveToNext() && out.size < limit) {
                out += CalendarEvent(
                    id = cursor.getLong(idIndex).toString(),
                    title = cursor.getString(titleIndex).orEmpty(),
                    startMillis = cursor.getLong(beginIndex),
                    endMillis = cursor.getLong(endIndex),
                    location = cursor.getString(locationIndex),
                    description = cursor.getString(descriptionIndex),
                    allDay = cursor.getInt(allDayIndex) == 1,
                )
            }
        }
        out
    }

    override suspend fun insert(event: CalendarEventDraft): String = withContext(Dispatchers.IO) {
        requirePermission(Manifest.permission.WRITE_CALENDAR, "写入日历")
        val calendarId = writableCalendarId()
            ?: throw IllegalStateException("设备上没有可写日历账户")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            event.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IllegalStateException("插入日历事件失败")
        val eventId = ContentUris.parseId(uri)
        event.reminderMinutes?.let { minutes ->
            runCatching {
                context.contentResolver.insert(
                    CalendarContract.Reminders.CONTENT_URI,
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, minutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    },
                )
            }
        }
        eventId.toString()
    }

    override suspend fun update(eventId: String, event: CalendarEventDraft): Boolean = withContext(Dispatchers.IO) {
        requirePermission(Manifest.permission.WRITE_CALENDAR, "写入日历")
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DESCRIPTION, event.description)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLongOrNull() ?: return@withContext false)
        context.contentResolver.update(uri, values, null, null) > 0
    }

    override suspend fun delete(eventId: String): Boolean = withContext(Dispatchers.IO) {
        requirePermission(Manifest.permission.WRITE_CALENDAR, "写入日历")
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLongOrNull() ?: return@withContext false)
        context.contentResolver.delete(uri, null, null) > 0
    }

    private fun requirePermission(permission: String, label: String) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            throw CalendarPermissionException("未授予「$label」权限")
        }
    }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        var fallback: Long? = null
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val primaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                if (fallback == null) fallback = id
                if (cursor.getInt(primaryIndex) == 1) return id
            }
        }
        return fallback
    }
}

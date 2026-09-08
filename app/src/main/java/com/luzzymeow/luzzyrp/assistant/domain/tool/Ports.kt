package com.luzzymeow.luzzyrp.assistant.domain.tool

/**
 * 需要 Android / 运行时的能力端口（domain 层只声明，实现放 `assistant/runtime/`）。
 *
 * 这样工具实现保持纯 Kotlin 可单测，Android 依赖集中在一处，也便于沙盒/宿主两种模式切换
 * （PLAN §10.2）。
 */

/** 设备信息（`get_device_info`）。 */
interface DeviceInfoProvider {
    /** 机型 / 系统版本 / API 级别。 */
    fun summary(): String

    /** 可用存储字节数（可为 -1 表示未知）。 */
    fun freeStorageBytes(): Long
}

/** 剪贴板（`clipboard_read` / `clipboard_write`）。 */
interface ClipboardPort {
    fun read(): String?
    fun write(text: String): Boolean
}

/**
 * 一次性命令执行（`terminal_run` / `run_code`）。
 *
 * 实现分沙盒（proot）与宿主（`/system/bin/sh`）两种，见 PLAN §10.2。
 * **实现方必须自行再跑一遍 [HardlineGuard]**（双保险），并把输出截断到上限。
 */
interface ShellRunner {
    /** 执行一条命令；返回 stdout+stderr 合并文本。 */
    suspend fun run(command: String, timeoutMs: Long): ShellResult

    /** 当前模式：`sandbox` | `host`。 */
    val mode: String
}

data class ShellResult(
    val exitCode: Int,
    val output: String,
    /** 输出被截断时给出落盘后的相对路径（PLAN §10.3：单次回传上限 200KB）。 */
    val truncatedToPath: String? = null,
)

/** 代码执行（`run_code`）：语言 → 解释器由实现决定（沙盒内 node/python3）。 */
interface CodeRunner {
    suspend fun run(language: String, code: String, timeoutMs: Long): ShellResult
}

/** 记忆存取端口（`memory_*` 工具与 ContextBuilder 共用）。 */
interface MemoryStore {
    suspend fun write(
        content: String,
        type: String,
        scope: String,
        assistantId: String,
        conversationId: String?,
    ): String

    suspend fun search(query: String, assistantId: String, topK: Int): List<MemoryHit>

    suspend fun update(id: String, content: String): Boolean

    suspend fun delete(id: String): Boolean

    suspend fun list(assistantId: String, scope: String?, limit: Int): List<MemoryHit>
}

data class MemoryHit(
    val id: String,
    val content: String,
    val type: String,
    val scope: String,
    val createdAtMillis: Long,
    /** 向量相似度（全文模式为 null）。 */
    val similarity: Float?,
)

/**
 * 日历读写（`calendar_read` / `calendar_write`，PLAN §12.2 T2 档）。
 *
 * 权限被拒时实现须抛 [CalendarPermissionException]（工具层转成可操作提示，
 * 引导用户去系统设置授权），**不得静默返回空**。
 */
interface CalendarPort {
    /** 查询区间内的事件（`from`/`to` 为 epoch millis；`query` 可选关键词）。 */
    suspend fun read(fromMillis: Long, toMillis: Long, query: String?, limit: Int): List<CalendarEvent>

    /** 插入事件，返回事件 id。 */
    suspend fun insert(event: CalendarEventDraft): String

    /** 更新事件；返回是否命中。 */
    suspend fun update(eventId: String, event: CalendarEventDraft): Boolean

    /** 删除事件；返回是否命中（需用户审批，由审批门保证）。 */
    suspend fun delete(eventId: String): Boolean
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
    val description: String?,
    val allDay: Boolean,
)

data class CalendarEventDraft(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String? = null,
    val description: String? = null,
    val reminderMinutes: Int? = null,
    val allDay: Boolean = false,
)

/** 日历权限被拒（工具层据此提示用户授权）。 */
class CalendarPermissionException(message: String) : SecurityException(message)

/** 时间/时区（`get_time`）——抽出来便于单测注入固定时钟。 */
interface ClockProvider {
    /** epoch millis。 */
    fun nowMillis(): Long

    /** IANA 时区 id（如 `Asia/Shanghai`）。 */
    fun zoneId(): String
}

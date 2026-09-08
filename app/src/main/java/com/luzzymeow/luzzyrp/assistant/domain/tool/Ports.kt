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

/** 时间/时区（`get_time`）——抽出来便于单测注入固定时钟。 */
interface ClockProvider {
    /** epoch millis。 */
    fun nowMillis(): Long

    /** IANA 时区 id（如 `Asia/Shanghai`）。 */
    fun zoneId(): String
}

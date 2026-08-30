package com.luzzymeow.luzzyrp.data.logger

import android.content.Context
import android.net.Uri
import com.luzzymeow.luzzyrp.core.common.JsonInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用日志系统（6.6）。
 *
 * 记录范围（越详细越好，受 [enabled] 开关控制）：
 *   - 用户步骤：发送/停止/审批/切换会话/设置变更
 *   - 模型步骤：请求组装（模型/参数/消息数）、流式开始/结束、chunk 批次计数、finishReason
 *   - 工具调用：名称/参数摘要/结果摘要/耗时
 *   - 后处理：三级摘要调度、ACE 反思/提取
 *   - 错误与崩溃上下文
 *
 * 存储：内存环形缓冲（最近 500 条，日志页实时展示）+ `filesDir/logs/luzzy-YYYYMMDD.jsonl`
 * 异步落盘；保留 3 天（超出自动清理）。
 * 导出：合并全部日志为单个 JSON 数组写入用户选择的 SAF Uri，或经系统分享面板发出。
 *
 * [INVARIANT-STREAMING] 日志对生成链路零阻塞：所有写盘经 Channel + 独立协程；
 * chunk 级日志按批次聚合（每 20 个 chunk 记一条），不影响 1 字 = 1 次更新。
 */
class AppLogger(private val context: Context, private val enabledProvider: () -> Boolean) {

    @Serializable
    data class LogEntry(
        val ts: Long,
        val level: String,      // DEBUG / INFO / WARN / ERROR
        val category: String,   // USER / MODEL / TOOL / PIPELINE / SETTINGS / SYSTEM
        val message: String,
        val detail: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seq = AtomicLong(0)
    private val writeChannel = Channel<LogEntry>(Channel.BUFFERED)

    private val _recent = MutableStateFlow<List<LogEntry>>(emptyList())
    val recent: StateFlow<List<LogEntry>> = _recent.asStateFlow()

    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneId.systemDefault())

    init {
        scope.launch { cleanupOldLogs() }
        scope.launch {
            val entries = mutableListOf<LogEntry>()
            for (entry in writeChannel) {
                entries.add(entry)
                if (entries.size > BATCH) {
                    flush(entries.toList())
                    entries.clear()
                }
                _recent.value = (_recent.value + entry).takeLast(MEMORY_LIMIT)
            }
        }
    }

    fun log(level: String, category: String, message: String, detail: String? = null) {
        if (!enabledProvider()) return
        val entry = LogEntry(
            ts = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message.take(2000),
            detail = detail?.take(8000),
        )
        // 非阻塞投递（缓冲满则丢弃最旧语义由 Channel BUFFERED + trySend 保证不卡调用方）
        writeChannel.trySend(entry)
        _recent.value = (_recent.value + entry).takeLast(MEMORY_LIMIT)
    }

    fun info(category: String, message: String, detail: String? = null) = log("INFO", category, message, detail)
    fun warn(category: String, message: String, detail: String? = null) = log("WARN", category, message, detail)
    fun error(category: String, message: String, detail: String? = null) = log("ERROR", category, message, detail)
    fun debug(category: String, message: String, detail: String? = null) = log("DEBUG", category, message, detail)

    /** 导出全部日志（合并为 JSON 数组）到用户选择的 Uri。 */
    suspend fun exportTo(uri: Uri): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val all = readAllEntries()
            val json = JsonInstant.encodeToString(ListSerializer(LogEntry.serializer()), all)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    /** 生成分享用的临时导出文件。 */
    suspend fun exportToCache(): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val all = readAllEntries()
            val json = JsonInstant.encodeToString(ListSerializer(LogEntry.serializer()), all)
            val out = File(context.cacheDir, "export/luzzy-logs-${System.currentTimeMillis()}.json")
            out.parentFile?.mkdirs()
            out.writeText(json, Charsets.UTF_8)
            out
        }.getOrNull()
    }

    fun clearInMemory() { _recent.value = emptyList() }

    // —— 内部 ——

    private fun logDir(): File = File(context.filesDir, "logs").apply { mkdirs() }

    private suspend fun flush(entries: List<LogEntry>) {
        runCatching {
            val byDay = entries.groupBy { dateFormat.format(Instant.ofEpochMilli(it.ts)) }
            byDay.forEach { (day, list) ->
                val file = File(logDir(), "luzzy-$day.jsonl")
                file.appendText(list.joinToString("\n") {
                    JsonInstant.encodeToString(LogEntry.serializer(), it)
                } + "\n")
            }
        }
    }

    private fun readAllEntries(): List<LogEntry> {
        val serializer = ListSerializer(LogEntry.serializer())
        return logDir().listFiles { f -> f.name.startsWith("luzzy-") && f.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            ?.flatMap { file ->
                runCatching {
                    JsonInstant.decodeFromString(serializer, "[" + file.readLines().filter { it.isNotBlank() }.joinToString(",") + "]")
                }.getOrDefault(emptyList())
            }
            .orEmpty()
    }

    private fun cleanupOldLogs() {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 3600 * 1000
        logDir().listFiles { f -> f.name.startsWith("luzzy-") && f.name.endsWith(".jsonl") }
            ?.forEach { file ->
                val day = file.name.removePrefix("luzzy-").removeSuffix(".jsonl")
                runCatching {
                    val instant = java.time.LocalDate.parse(day, DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (instant < cutoff) file.delete()
                }
            }
    }

    companion object {
        const val CAT_USER = "USER"
        const val CAT_MODEL = "MODEL"
        const val CAT_TOOL = "TOOL"
        const val CAT_PIPELINE = "PIPELINE"
        const val CAT_SETTINGS = "SETTINGS"
        const val CAT_SYSTEM = "SYSTEM"
        private const val MEMORY_LIMIT = 500
        private const val BATCH = 20
        const val RETENTION_DAYS = 3
    }
}

package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 子进程执行内核（宿主 shell 与 proot 沙盒共用）。
 *
 * 关键点（会话 29 实踩后固化）：
 * - **非阻塞 `available()` 轮询 + 有界排空**：`sh -c "sleep 30"` 的孙进程会持有管道，
 *   用阻塞 `readText()` 排空会一直等到孙进程结束（超时形同失效）；
 * - 输出上限 + 溢出落盘（PLAN §10.3 单次 200KB）；
 * - 超时强杀 shell 本体（Android 无 `ProcessHandle`，无法枚举孙进程——已记录在 AGENTS 坑表）。
 */
internal object ProcessRunner {

    suspend fun run(
        command: List<String>,
        workingDir: File,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long,
        maxOutputChars: Int = 200 * 1024,
        overflowDir: File? = null,
        overflowLabel: String = "",
    ): ShellResult = withContext(Dispatchers.IO) {
        workingDir.mkdirs()
        val process = try {
            ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
        } catch (e: Exception) {
            return@withContext ShellResult(-1, "无法启动进程：${e.message ?: e.javaClass.simpleName}")
        }

        val buffer = StringBuilder()
        var truncated = false
        var timedOut = false
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

        try {
            process.inputStream.use { stream ->
                val chunk = ByteArray(4096)
                while (true) {
                    val available = stream.available()
                    if (available > 0) {
                        val read = stream.read(chunk, 0, minOf(chunk.size, available))
                        if (read > 0) {
                            val remaining = maxOutputChars - buffer.length
                            if (remaining <= 0) truncated = true
                            else {
                                val take = minOf(read, remaining)
                                buffer.append(String(chunk, 0, take, Charsets.UTF_8))
                                if (take < read) truncated = true
                            }
                        }
                        continue
                    }
                    if (!process.isAlive) break
                    if (System.nanoTime() > deadline) {
                        runCatching { process.destroyForcibly() }
                        timedOut = true
                        break
                    }
                    Thread.sleep(15)
                }
                val drainDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
                while (stream.available() > 0 && System.nanoTime() < drainDeadline) {
                    val read = stream.read(chunk, 0, minOf(chunk.size, stream.available()))
                    if (read <= 0) break
                    val remaining = maxOutputChars - buffer.length
                    if (remaining <= 0) { truncated = true; continue }
                    val take = minOf(read, remaining)
                    buffer.append(String(chunk, 0, take, Charsets.UTF_8))
                    if (take < read) truncated = true
                }
            }
            val exit = if (process.isAlive) -1 else runCatching { process.exitValue() }.getOrDefault(-1)
            if (timedOut) buffer.append("\n[超时 ${timeoutMs}ms，已终止进程]")
            val overflowPath = if (truncated) spill(buffer.toString(), overflowDir, overflowLabel) else null
            ShellResult(exit, buffer.toString(), overflowPath)
        } finally {
            if (process.isAlive) runCatching { process.destroyForcibly() }
        }
    }

    private fun spill(full: String, dir: File?, label: String): String? {
        if (dir == null) return null
        return runCatching {
            dir.mkdirs()
            val name = "output-${System.currentTimeMillis()}.log"
            File(dir, name).writeText("# $label\n\n$full", Charsets.UTF_8)
            name
        }.getOrNull()
    }
}

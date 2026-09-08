package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import com.luzzymeow.luzzyrp.assistant.domain.tool.HardlineGuard
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全局（宿主）模式 Shell（PLAN §10.2 第二行）。
 *
 * `ProcessBuilder("/system/bin/sh", "-c", cmd)`，以 **App 权限**运行：
 * 可读 App 私有目录与 SAF 授权目录，**不能** apk/pip、无 root。
 *
 * **安全（硬性要求 11，双层）**：进入前再跑一遍 [HardlineGuard]（Agent 层已跑一次，
 * 此处防绕过）；输出上限 [maxOutputChars]（默认 200KB，PLAN §10.3），超出部分落工作区文件
 * 并把相对路径回传，避免一次回灌爆上下文。
 *
 * 注：`ProcessBuilder` 不支持 PTY，交互式全屏程序（vim/top）不可用——按 PLAN §10.2
 * 「共同能力」的降级条款处理。
 */
class GlobalShellRunner(
    private val workingDir: File,
    private val overflowDir: File? = null,
    private val maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS,
    /** 解释器路径；真机固定 `/system/bin/sh`，单测可注入（如 Git Bash 的 sh）。 */
    private val shellPath: String = DEFAULT_SHELL,
) : ShellRunner {

    override val mode: String = MODE_HOST

    override suspend fun run(command: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        HardlineGuard.reasonOf(command)?.let { reason ->
            return@withContext ShellResult(exitCode = 126, output = "已拦截：$reason")
        }
        workingDir.mkdirs()
        val process = try {
            ProcessBuilder(shellPath, "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return@withContext ShellResult(-1, "无法启动 shell：${e.message ?: e.javaClass.simpleName}")
        }

        val buffer = StringBuilder()
        var truncated = false
        var timedOut = false
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

        try {
            process.inputStream.use { stream ->
                val chunk = ByteArray(4096)
                // 非阻塞轮询：只在 available()>0 时读，避免 readText() 因孙进程持有管道而永久阻塞
                while (true) {
                    val available = stream.available()
                    if (available > 0) {
                        val read = stream.read(chunk, 0, minOf(chunk.size, available))
                        if (read > 0) {
                            val remaining = maxOutputChars - buffer.length
                            if (remaining <= 0) {
                                truncated = true
                            } else {
                                val take = minOf(read, remaining)
                                buffer.append(String(chunk, 0, take, Charsets.UTF_8))
                                if (take < read) truncated = true
                            }
                        }
                        continue
                    }
                    if (!process.isAlive) break
                    if (System.nanoTime() > deadline) {
                        killTree(process)
                        timedOut = true
                        break
                    }
                    Thread.sleep(15)
                }
                // 终止后的有界排空（500ms 上限，防止孙进程继续持有管道时卡死）
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
            val overflowPath = if (truncated) spill(buffer.toString(), command) else null
            ShellResult(exit, buffer.toString(), overflowPath)
        } finally {
            if (process.isAlive) killTree(process)
        }
    }

    /**
     * 终止进程。
     *
     * 说明：Android 的 `java.lang.Process` **没有** `toHandle()/ProcessHandle`（Java 9 API 未下沉），
     * 因此无法枚举孙进程。这里强杀 shell 本体，并靠**非阻塞排空 + 有界等待**保证调用及时返回
     * （`sh -c "sleep 30"` 的孙进程会自行结束，不会拖住本次调用——超时语义已由单测锁定）。
     */
    private fun killTree(process: Process) {
        runCatching { process.destroyForcibly() }
    }

    /** 超长输出落盘（工作区 `exports/` 或注入目录），返回相对路径。 */
    private fun spill(full: String, command: String): String? {
        val dir = overflowDir ?: return null
        return runCatching {
            dir.mkdirs()
            val name = "shell-${System.currentTimeMillis()}.log"
            val file = File(dir, name)
            file.writeText("# command: ${command.take(200)}\n\n$full", Charsets.UTF_8)
            name
        }.getOrNull()
    }

    companion object {
        const val MODE_HOST = "host"
        const val DEFAULT_MAX_OUTPUT_CHARS = 200 * 1024
        const val DEFAULT_SHELL = "/system/bin/sh"
    }
}

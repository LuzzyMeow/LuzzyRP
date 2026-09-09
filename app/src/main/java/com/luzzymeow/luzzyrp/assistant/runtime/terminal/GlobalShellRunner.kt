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

    override suspend fun run(command: String, timeoutMs: Long): ShellResult {
        HardlineGuard.reasonOf(command)?.let { reason ->
            return ShellResult(exitCode = 126, output = "已拦截：$reason")
        }
        return ProcessRunner.run(
            command = listOf(shellPath, "-c", command),
            workingDir = workingDir,
            timeoutMs = timeoutMs,
            maxOutputChars = maxOutputChars,
            overflowDir = overflowDir,
            overflowLabel = "command: ${command.take(200)}",
        )
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

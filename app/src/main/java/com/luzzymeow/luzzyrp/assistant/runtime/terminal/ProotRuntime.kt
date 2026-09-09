package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import android.content.Context
import com.luzzymeow.luzzyrp.assistant.domain.tool.HardlineGuard
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * proot 沙盒运行时（PLAN §10.2/§10.3，用户 2026-09-09 拍板方案 A：**随包内置**）。
 *
 * 资产（`assets/assistant/sandbox/`，来源与 GPL 合规见同目录 `SOURCES.md`）：
 * `proot` + `proot-loader` + `libtalloc.so.2` + `libandroid-shmem.so` + `rootfs.tar.gz`（Alpine 3.20.3）。
 *
 * 首次使用释放到 `filesDir/assistant/sandbox/`（幂等：版本标记匹配则跳过），并 `chmod +x`；
 * 启动命令：`proot -0 -r rootfs -b /dev -b /proc -b /sys -b <workspace>:/workspace -w /workspace /bin/sh -c <cmd>`。
 *
 * **安全**：进入前再跑一遍 [HardlineGuard]（Agent 层已跑一次，此处防绕过）；
 * 输出上限 200KB、溢出落工作区（PLAN §10.3）。
 */
class ProotRuntime(
    private val context: Context,
    private val sandboxDir: File = File(context.filesDir, SANDBOX_DIR),
) : ShellRunner {

    override val mode: String = MODE_SANDBOX

    private val installMutex = Mutex()

    @Volatile
    private var installError: String? = null

    /** 是否已就绪（无需重新释放）。 */
    fun isInstalled(): Boolean = prootBin.isFile && rootfsDir.isDirectory && markerFile.isFile &&
        markerFile.readText().trim() == ASSET_VERSION

    /** 释放资产（幂等、并发安全）。返回 null 表示成功，否则错误信息。 */
    suspend fun ensureInstalled(onProgress: (String) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        installMutex.withLock {
            if (isInstalled()) return@withLock null
            installError = null
            try {
                sandboxDir.mkdirs()
                onProgress("释放 proot 与运行库…")
                copyAsset("proot", prootBin, executable = true)
                copyAsset("proot-loader", loaderBin, executable = true)
                copyAsset("libtalloc.so.2", File(sandboxDir, "libtalloc.so.2"), executable = false)
                copyAsset("libandroid-shmem.so", File(sandboxDir, "libandroid-shmem.so"), executable = false)

                onProgress("解压 Alpine rootfs…")
                rootfsDir.deleteRecursively()
                rootfsDir.mkdirs()
                extractRootfs()
                // Alpine 需要 /etc/resolv.conf 才能联网 apk add
                runCatching {
                    File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
                }
                File(sandboxDir, "tmp").mkdirs()
                markerFile.writeText(ASSET_VERSION)
                null
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                installError = message
                message
            }
        }
    }

    /** 一次性命令执行（沙盒内）。 */
    override suspend fun run(command: String, timeoutMs: Long): ShellResult {
        HardlineGuard.reasonOf(command)?.let { reason ->
            return ShellResult(126, "已拦截：$reason")
        }
        return runInWorkspace(File(sandboxDir, "tmp"), command, timeoutMs)
    }

    /** 在指定工作区执行（工作区被 bind 到容器内 `/workspace`）。 */
    suspend fun runInWorkspace(workspaceDir: File, command: String, timeoutMs: Long): ShellResult {
        ensureInstalled()?.let { return ShellResult(-1, "沙盒不可用：$it") }
        workspaceDir.mkdirs()
        return ProcessRunner.run(
            command = buildCommand(workspaceDir, command),
            workingDir = workspaceDir,
            environment = environment(),
            timeoutMs = timeoutMs,
            overflowDir = File(workspaceDir, "exports"),
            overflowLabel = "sandbox: ${command.take(200)}",
        )
    }

    /**
     * 启动一个**交互式**沙盒进程（stdin/stdout 管道可用），供 MCP stdio 客户端使用。
     *
     * 与 [runInWorkspace] 的区别：不收集输出、不设超时——生命周期由调用方管理（[close]）。
     * 返回 null 表示沙盒未就绪或启动失败。
     */
    fun spawnInteractive(
        command: String,
        args: List<String>,
        env: Map<String, String> = emptyMap(),
        workspaceDir: File = workspaceRootHint().let(::File),
    ): Process? {
        if (!isInstalled()) return null
        workspaceDir.mkdirs()
        val full = buildCommand(workspaceDir, (listOf(command) + args).joinToString(" "))
        return runCatching {
            ProcessBuilder(full)
                .directory(workspaceDir)
                .redirectErrorStream(false)
                .apply { environment().putAll(environment()); environment().putAll(env) }
                .start()
        }.getOrNull()
    }

    /** 供单测/诊断查看启动命令（不含任何密钥）。 */
    fun buildCommand(workspaceDir: File, command: String): List<String> =
        ProotCommand.build(prootBin, rootfsDir, workspaceDir, command)

    private fun environment(): Map<String, String> = mapOf(
        // Termux proot 需要显式指定 loader 与可写临时目录
        "PROOT_LOADER" to loaderBin.absolutePath,
        "PROOT_TMP_DIR" to File(sandboxDir, "tmp").absolutePath,
        "LD_LIBRARY_PATH" to sandboxDir.absolutePath,
        "HOME" to "/root",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM" to "xterm-256color",
        "LANG" to "C.UTF-8",
        "TMPDIR" to "/tmp",
    )

    private fun copyAsset(name: String, target: File, executable: Boolean) {
        target.parentFile?.mkdirs()
        context.assets.open("$ASSET_DIR/$name").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (executable) target.setExecutable(true, true)
    }

    private fun extractRootfs() {
        context.assets.open("$ASSET_DIR/rootfs.tar.gz").use { raw ->
            GZIPInputStream(raw, 64 * 1024).use { gz ->
                TarExtractor.extract(gz, rootfsDir)
            }
        }
    }

    /** 诊断用（不含敏感信息）。 */
    fun status(): String = when {
        isInstalled() -> "已安装（$ASSET_VERSION）"
        installError != null -> "安装失败：$installError"
        else -> "未安装"
    }

    val workspaceMountPoint: String get() = "/workspace"

    /** 工作区根提示（沙盒默认工作区 = sandbox/workspace，绑定到容器 /workspace）。 */
    fun workspaceRootHint(): String = File(sandboxDir, "workspace").also { it.mkdirs() }.absolutePath

    private val prootBin: File get() = File(sandboxDir, "proot")
    private val loaderBin: File get() = File(sandboxDir, "proot-loader")
    private val rootfsDir: File get() = File(sandboxDir, "rootfs")
    private val markerFile: File get() = File(sandboxDir, ".installed")

    companion object {
        const val MODE_SANDBOX: String = "sandbox"
        const val ASSET_DIR: String = "assistant/sandbox"
        const val SANDBOX_DIR: String = "assistant/sandbox"

        /** 资产版本（与 `assets/assistant/sandbox/manifest.json` 保持一致；变更即触发重装）。 */
        const val ASSET_VERSION: String = "proot-5.1.107.92+alpine-3.20.3"
    }
}

/**
 * proot 启动命令构造（纯函数，可单测）。
 *
 * 说明：`--link2symlink` 让容器内的符号链接兼容 Android 文件系统；
 * `-0` 伪造 root（仅容器内视角，**不是提权**）；工作区 bind 到 `/workspace` 并设为 cwd。
 */
internal object ProotCommand {
    fun build(prootBin: File, rootfsDir: File, workspaceDir: File, command: String): List<String> = listOf(
        prootBin.absolutePath,
        "--link2symlink",
        "-0",
        "-r", rootfsDir.absolutePath,
        "-b", "/dev",
        "-b", "/proc",
        "-b", "/sys",
        "-b", "${workspaceDir.absolutePath}:/workspace",
        "-w", "/workspace",
        "/bin/sh", "-lc", command,
    )
}

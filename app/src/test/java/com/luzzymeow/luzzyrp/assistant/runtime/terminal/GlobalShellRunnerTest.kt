package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * [GlobalShellRunner] 单测（宿主模式）。
 *
 * 用本机 `sh`（Git Bash）代替 `/system/bin/sh`——行为一致（`sh -c`），
 * 故逻辑可在开发机验证；真机差异仅解释器路径。
 */
class GlobalShellRunnerTest {

    private val sh = findSh()

    private fun findSh(): String? = listOf(
        "/usr/bin/sh", "/bin/sh", "C:/Program Files/Git/usr/bin/sh.exe", "sh",
    ).firstOrNull { candidate ->
        runCatching {
            ProcessBuilder(candidate, "-c", "echo ok").start().waitFor() == 0
        }.getOrDefault(false)
    }

    private fun runner(maxChars: Int = GlobalShellRunner.DEFAULT_MAX_OUTPUT_CHARS, overflow: File? = null) =
        GlobalShellRunner(
            workingDir = File(System.getProperty("java.io.tmpdir"), "luzzy-shell-test"),
            overflowDir = overflow,
            maxOutputChars = maxChars,
            shellPath = sh ?: "sh",
        )

    @Test
    fun `正常命令返回退出码与输出`() = runBlocking {
        assumeTrue("本机无 sh，跳过", sh != null)
        val result = runner().run("echo hello", 10_000)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("hello"))
    }

    @Test
    fun `非零退出码透传`() = runBlocking {
        assumeTrue(sh != null)
        val result = runner().run("exit 3", 10_000)
        assertEquals(3, result.exitCode)
    }

    @Test
    fun `危险命令被 HARDLINE 拦截且不执行`() = runBlocking {
        assumeTrue(sh != null)
        val result = runner().run("rm -rf /", 10_000)
        assertEquals(126, result.exitCode)
        assertTrue(result.output.contains("已拦截"))
    }

    @Test
    fun `超时终止进程`() = runBlocking {
        assumeTrue(sh != null)
        val started = System.currentTimeMillis()
        val result = runner().run("sleep 30", 800)
        val elapsed = System.currentTimeMillis() - started
        assertTrue("应在超时后很快返回，实际 ${elapsed}ms", elapsed < 15_000)
        assertTrue(result.output.contains("超时") || result.exitCode != 0)
    }

    @Test
    fun `超长输出截断并落盘`() = runBlocking {
        assumeTrue(sh != null)
        val overflowDir = File(System.getProperty("java.io.tmpdir"), "luzzy-shell-overflow")
        overflowDir.deleteRecursively()
        val result = runner(maxChars = 200, overflow = overflowDir)
            .run("for i in 1 2 3 4 5 6 7 8 9 10; do echo 0123456789012345678901234567890123456789; done", 10_000)
        assertTrue("输出应被截断", result.output.length <= 300)
        assertNotNull("截断时应给出落盘路径", result.truncatedToPath)
        assertTrue(overflowDir.listFiles()?.isNotEmpty() == true)
    }
}

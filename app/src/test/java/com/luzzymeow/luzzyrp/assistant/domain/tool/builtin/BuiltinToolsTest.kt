package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.ClipboardPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.CodeRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.DeviceInfoProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryHit
import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryStore
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置工具单测（PLAN §12）：澄清提问、工作区越界、HARDLINE 拦截、SSRF 拦截、记忆工具。
 *
 * 用假端口（[FakeCtx] / [FakeWorkspace] / [FakeShell]），不依赖 Android。
 */
class BuiltinToolsTest {

    // ---------- 假端口 ----------

    private class FakeWorkspace : WorkspaceAccess {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun list(relativeDir: String): List<WorkspaceEntry> =
            files.filterKeys { it.startsWith(relativeDir) }
                .map { WorkspaceEntry(it.key, false, it.value.size.toLong()) }

        override suspend fun read(relativePath: String): ByteArray =
            files[relativePath] ?: throw IllegalArgumentException("路径越界或不存在: $relativePath")

        override suspend fun write(relativePath: String, bytes: ByteArray) {
            if (relativePath.startsWith("../") || relativePath.startsWith("/")) {
                throw IllegalArgumentException("路径越界: $relativePath")
            }
            files[relativePath] = bytes
        }

        override suspend fun delete(relativePath: String) { files.remove(relativePath) }
        override suspend fun move(fromRelative: String, toRelative: String) {
            files[toRelative] = read(fromRelative); files.remove(fromRelative)
        }
        override suspend fun mkdir(relativeDir: String) {}
        override suspend fun exists(relativePath: String): Boolean = files.containsKey(relativePath)
    }

    private class FakeShell(private val exit: Int = 0, private val output: String = "ok") : ShellRunner {
        var lastCommand: String? = null
        override val mode = "sandbox"
        override suspend fun run(command: String, timeoutMs: Long): ShellResult {
            lastCommand = command
            return ShellResult(exit, output)
        }
    }

    private class FakeCodeRunner : CodeRunner {
        var called = false
        override suspend fun run(language: String, code: String, timeoutMs: Long): ShellResult {
            called = true
            return ShellResult(0, "ran $language")
        }
    }

    private class FakeMemory : MemoryStore {
        val items = linkedMapOf<String, Triple<String, String, String>>()
        var seq = 0
        override suspend fun write(content: String, type: String, scope: String, assistantId: String, conversationId: String?): String {
            val id = "m${++seq}"; items[id] = Triple(content, type, scope); return id
        }
        override suspend fun search(query: String, assistantId: String, topK: Int): List<MemoryHit> =
            items.filter { it.value.first.contains(query) }
                .map { MemoryHit(it.key, it.value.first, it.value.second, it.value.third, 0L, null) }
        override suspend fun update(id: String, content: String): Boolean =
            items[id]?.let { items[id] = Triple(content, it.second, it.third); true } ?: false
        override suspend fun delete(id: String): Boolean = items.remove(id) != null
        override suspend fun list(assistantId: String, scope: String?, limit: Int): List<MemoryHit> =
            items.map { MemoryHit(it.key, it.value.first, it.value.second, it.value.third, 0L, null) }
    }

    private class FakeCtx(val ws: WorkspaceAccess = FakeWorkspace()) : ToolContext {
        val logs = mutableListOf<String>()
        override val assistantId = "a1"
        override val conversationId = "c1"
        override val workspace = ws
        override val cancelled = { false }
        override val log = { msg: String -> logs += msg }
    }

    private fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    // ---------- 测试 ----------

    @Test
    fun `ask_user 返回 NeedUserInput 并带选项`() = runBlocking {
        val json = buildJsonObject {
            put("question", "用哪种格式？")
            put("options", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject { put("label", "三段式") })
                add(buildJsonObject { put("label", "表格") })
            })
            put("allow_multiple", true)
        }
        val result = AskUserTool().execute(json, FakeCtx())
        assertTrue(result is ToolResult.NeedUserInput)
        val prompt = (result as ToolResult.NeedUserInput).prompt
        assertEquals("用哪种格式？", prompt.question)
        assertEquals(listOf("三段式", "表格"), prompt.options.map { it.label })
        assertTrue(prompt.allowMultiple)
    }

    @Test
    fun `ask_user 缺 question 报错`() = runBlocking {
        val result = AskUserTool().execute(buildJsonObject { }, FakeCtx())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `workspace_write 与 read 往返`() = runBlocking {
        val ctx = FakeCtx()
        val w = WorkspaceWriteTool().execute(args("path" to "a/b.txt", "content" to "你好"), ctx)
        assertTrue(w is ToolResult.Ok)
        val r = WorkspaceReadTool().execute(args("path" to "a/b.txt"), ctx)
        assertEquals("你好", (r as ToolResult.Ok).text)
    }

    @Test
    fun `workspace 越界异常转 ToolResult Error 不抛`() = runBlocking {
        val ctx = FakeCtx()
        val r = WorkspaceReadTool().execute(args("path" to "../etc/passwd"), ctx)
        assertTrue(r is ToolResult.Error)
        val w = WorkspaceWriteTool().execute(args("path" to "../x", "content" to "x"), ctx)
        assertTrue(w is ToolResult.Error)
    }

    @Test
    fun `workspace_patch 要求唯一命中`() = runBlocking {
        val ctx = FakeCtx()
        WorkspaceWriteTool().execute(args("path" to "f.txt", "content" to "aa aa"), ctx)
        val dup = WorkspacePatchTool().execute(args("path" to "f.txt", "find" to "aa", "replace" to "b"), ctx)
        assertTrue("重复命中应拒绝", dup is ToolResult.Error)
        val miss = WorkspacePatchTool().execute(args("path" to "f.txt", "find" to "zz", "replace" to "b"), ctx)
        assertTrue(miss is ToolResult.Error)
        WorkspaceWriteTool().execute(args("path" to "g.txt", "content" to "hello world"), ctx)
        val ok = WorkspacePatchTool().execute(args("path" to "g.txt", "find" to "world", "replace" to "there"), ctx)
        assertTrue(ok is ToolResult.Ok)
        assertEquals("hello there", (WorkspaceReadTool().execute(args("path" to "g.txt"), ctx) as ToolResult.Ok).text)
    }

    @Test
    fun `terminal_run 拦截危险命令且不触达执行器`() = runBlocking {
        val shell = FakeShell()
        val ctx = FakeCtx()
        val result = TerminalRunTool(shell).execute(args("command" to "rm -rf /"), ctx)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("拦截"))
        assertEquals(null, shell.lastCommand)
        assertTrue(ctx.logs.any { it.contains("HARDLINE") })
    }

    @Test
    fun `terminal_run 正常命令透传并返回输出`() = runBlocking {
        val shell = FakeShell(exit = 0, output = "hello")
        val result = TerminalRunTool(shell).execute(args("command" to "echo hello"), FakeCtx())
        assertEquals("hello", (result as ToolResult.Ok).text)
        assertEquals("echo hello", shell.lastCommand)
    }

    @Test
    fun `terminal_run 非零退出码转 Error 并带输出`() = runBlocking {
        val result = TerminalRunTool(FakeShell(exit = 2, output = "boom"))
            .execute(args("command" to "false"), FakeCtx())
        val error = result as ToolResult.Error
        assertTrue(error.message.contains("退出码 2"))
        assertTrue(error.message.contains("boom"))
    }

    @Test
    fun `run_code 拦截危险代码且不触达执行器`() = runBlocking {
        val runner = FakeCodeRunner()
        val result = RunCodeTool(runner).execute(
            args("language" to "python", "code" to "import os; os.system('rm -rf /')"), FakeCtx()
        )
        assertTrue(result is ToolResult.Error)
        assertTrue(!runner.called)
    }

    @Test
    fun `run_code 不支持的语言被拒绝`() = runBlocking {
        val result = RunCodeTool(FakeCodeRunner()).execute(args("language" to "ruby", "code" to "puts 1"), FakeCtx())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `web_fetch 拒绝内网地址`() = runBlocking {
        val result = WebFetchTool(resolver = { listOf(java.net.InetAddress.getByName("127.0.0.1")) })
            .execute(args("url" to "http://internal.local/secret"), FakeCtx())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("内网"))
    }

    @Test
    fun `web_fetch 拒绝非 http 协议`() = runBlocking {
        val result = WebFetchTool().execute(args("url" to "file:///etc/passwd"), FakeCtx())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `html 正文提取去掉脚本样式并反转义`() {
        val html = """
            <html><head><style>p{color:red}</style><script>alert(1)</script></head>
            <body><h1>标题</h1><p>第一段 &amp; 第二段</p><div>块</div>
            <!-- 注释 --><noscript>n</noscript></body></html>
        """.trimIndent()
        val text = HtmlText.extract(html)
        assertTrue(text.contains("标题"))
        assertTrue(text.contains("第一段 & 第二段"))
        assertTrue(!text.contains("alert(1)"))
        assertTrue(!text.contains("color:red"))
        assertTrue(!text.contains("注释"))
    }

    @Test
    fun `memory_write 与 search 闭环`() = runBlocking {
        val store = FakeMemory()
        val ctx = FakeCtx()
        val w = MemoryWriteTool(store).execute(args("content" to "用户喜欢三段式周报", "type" to "preference"), ctx)
        assertTrue(w is ToolResult.Ok)
        val s = MemorySearchTool(store).execute(args("query" to "三段式"), ctx)
        assertTrue((s as ToolResult.Ok).text.contains("三段式周报"))
    }

    @Test
    fun `memory_delete 未命中报错`() = runBlocking {
        val result = MemoryDeleteTool(FakeMemory()).execute(args("id" to "nope"), FakeCtx())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `device_info 与 clipboard 走端口`() = runBlocking {
        val provider = object : DeviceInfoProvider {
            override fun summary() = "Xiaomi Test · Android 16"
            override fun freeStorageBytes() = 2048L * 1024 * 1024
        }
        val info = GetDeviceInfoTool(provider).execute(buildJsonObject { }, FakeCtx())
        assertTrue((info as ToolResult.Ok).text.contains("2048 MB"))

        val clipboard = object : ClipboardPort {
            var text: String? = null
            override fun read() = text
            override fun write(t: String): Boolean { text = t; return true }
        }
        ClipboardWriteTool(clipboard).execute(args("text" to "hi"), FakeCtx())
        val read = ClipboardReadTool(clipboard).execute(buildJsonObject { }, FakeCtx())
        assertEquals("hi", (read as ToolResult.Ok).text)
    }
}

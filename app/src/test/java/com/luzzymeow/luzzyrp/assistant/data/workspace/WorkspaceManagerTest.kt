package com.luzzymeow.luzzyrp.assistant.data.workspace

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * WorkspaceManager 单测（PLAN §10.1 路径安全 + 配额）。
 *
 * 纯 JVM：WorkspaceManager 主构造只依赖 File，不触碰 Android API，故无需 Robolectric。
 * Room DAO 需要 Robolectric/Instrumentation，本阶段不做（见交付报告）。
 */
class WorkspaceManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var manager: WorkspaceManager

    private val assistantId = "asst-0001"

    @Before
    fun setUp() {
        manager = WorkspaceManager(File(tempFolder.root, "workspaces"))
    }

    // ---------------- 目录与元数据 ----------------

    @Test
    fun ensureWorkspaceCreatesLayoutAndMeta() = runBlocking {
        val root = manager.ensureWorkspace(assistantId)
        assertTrue(File(root, "files").isDirectory)
        assertTrue(File(root, "attachments").isDirectory)
        assertTrue(File(root, "exports").isDirectory)
        val meta = File(root, ".meta/workspace.json")
        assertTrue(meta.isFile)
        val json = meta.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"assistantId\": \"asst-0001\""))
        assertTrue(json.contains("\"quotaBytes\""))
        assertTrue(json.contains("\"createdAt\""))
        assertTrue(manager.existsWorkspace(assistantId))
    }

    // ---------------- 越界防护 ----------------

    @Test
    fun parentTraversalIsRejected() = runBlocking {
        manager.ensureWorkspace(assistantId)
        val illegal = listOf(
            "../evil.txt",
            "files/../../evil.txt",
            "..",
            "files/./../../evil.txt",
            "files/../..",
            "attachments/../../../etc/hosts",
        )
        for (path in illegal) assertSecurityRejected(path)
    }

    @Test
    fun absoluteAndDriveLetterPathsAreRejected() = runBlocking {
        manager.ensureWorkspace(assistantId)
        val illegal = listOf(
            "/etc/passwd",
            "//server/share/file.txt",
            "\\windows\\system32\\drivers\\etc\\hosts",
            "C:/Windows/win.ini",
            "C:\\Windows\\win.ini",
            "~/.ssh/id_rsa",
        )
        for (path in illegal) assertSecurityRejected(path)
    }

    @Test
    fun illegalAssistantIdIsRejected() = runBlocking {
        val illegal = listOf("../x", "a/b", "..", "", ".hidden", "a b", "x\\y")
        for (id in illegal) {
            try {
                manager.resolve(id, "files")
                fail("应拒绝助手 id: " + id)
            } catch (expected: WorkspaceSecurityException) {
                // 预期
            }
        }
    }

    @Test
    fun symlinkTraversalIsRejected() = runBlocking {
        val root = manager.ensureWorkspace(assistantId)
        val outside = File(tempFolder.root, "outside")
        assertTrue(outside.mkdirs())
        val secret = File(outside, "secret.txt")
        secret.writeText("top-secret", Charsets.UTF_8)

        val link = File(root, "files/escape")
        val linkCreated = createDirectoryLink(link, outside)
        assumeTrue("本机既不能建符号链接也不能建目录联接，跳过该用例", linkCreated)

        assertSecurityRejected("files/escape/secret.txt")
        assertSecurityRejected("files/escape")

        try {
            manager.readText(assistantId, "files/escape/secret.txt")
            fail("读操作不应穿透链接")
        } catch (expected: WorkspaceSecurityException) {
        }
        try {
            manager.writeText(assistantId, "files/escape/pwn.txt", "pwn")
            fail("写操作不应穿透链接")
        } catch (expected: WorkspaceSecurityException) {
        }
        try {
            manager.delete(assistantId, "files/escape", recursive = true)
            fail("删除操作不应穿透链接")
        } catch (expected: WorkspaceSecurityException) {
        }

        // 根外文件必须原封不动
        assertEquals("top-secret", secret.readText(Charsets.UTF_8))
        assertFalse(File(outside, "pwn.txt").exists())
    }

    @Test
    fun symlinkedWorkspaceRootIsRejected() = runBlocking {
        val base = File(tempFolder.root, "workspaces")
        assertTrue(base.mkdirs())
        val outside = File(tempFolder.root, "outside-root")
        assertTrue(outside.mkdirs())
        val linkRoot = File(base, assistantId)
        val created = createDirectoryLink(linkRoot, outside)
        assumeTrue("本机无法创建链接，跳过该用例", created)
        try {
            manager.ensureWorkspace(assistantId)
            fail("工作区根为链接时应拒绝")
        } catch (expected: WorkspaceSecurityException) {
        }
    }

    // ---------------- 配额 ----------------

    @Test
    fun workspaceQuotaExceededIsRejected() = runBlocking {
        val small = WorkspaceManager(
            workspacesRoot = File(tempFolder.root, "quota-ws"),
            quotaBytesPerWorkspace = 2048L,
            maxFileBytes = 4096L,
        )
        val root = small.ensureWorkspace(assistantId)
        small.writeText(assistantId, "files/ok.txt", "x".repeat(1024))
        try {
            small.writeText(assistantId, "files/big.txt", "y".repeat(2048))
            fail("应抛 WorkspaceQuotaExceededException")
        } catch (expected: WorkspaceQuotaExceededException) {
            assertEquals(2048L, expected.quotaBytes)
            assertTrue(expected.projectedBytes > expected.quotaBytes)
        }
        assertFalse(File(root, "files/big.txt").exists())
    }

    @Test
    fun singleFileLimitIsRejected() = runBlocking {
        val small = WorkspaceManager(
            workspacesRoot = File(tempFolder.root, "file-limit"),
            quotaBytesPerWorkspace = 1L shl 20,
            maxFileBytes = 16L,
        )
        small.ensureWorkspace(assistantId)
        try {
            small.writeText(assistantId, "files/big.txt", "0123456789ABCDEFG")
            fail("应抛 WorkspaceFileTooLargeException")
        } catch (expected: WorkspaceFileTooLargeException) {
            assertEquals(17L, expected.sizeBytes)
            assertEquals(16L, expected.maxFileBytes)
        }
        // 追加也不能突破单文件上限
        small.writeText(assistantId, "files/small.txt", "12345678")
        try {
            small.writeText(assistantId, "files/small.txt", "123456789", append = true)
            fail("追加后超限应被拒绝")
        } catch (expected: WorkspaceFileTooLargeException) {
            assertEquals(17L, expected.sizeBytes)
        }
    }

    // ---------------- 归一后正常读写 ----------------

    @Test
    fun normalizedLegalWriteSucceeds() = runBlocking {
        val root = manager.ensureWorkspace(assistantId)
        val written = manager.writeText(assistantId, "files/./docs/../docs/note.txt", "hello 工作区")
        assertEquals(File(root, "files/docs/note.txt").canonicalPath, written.canonicalPath)
        assertTrue(written.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertEquals("hello 工作区", manager.readText(assistantId, "files/docs/note.txt"))
        assertEquals("hello 工作区", manager.readText(assistantId, "files//docs//./note.txt"))

        val entries = manager.list(assistantId, "files/docs")
        assertEquals(1, entries.size)
        assertEquals("note.txt", entries[0].name)
        assertEquals("files/docs/note.txt", entries[0].relativePath)
        assertFalse(entries[0].isDirectory)

        manager.writeText(assistantId, "files/docs/note.txt", " + more", append = true)
        assertEquals("hello 工作区 + more", manager.readText(assistantId, "files/docs/note.txt"))

        manager.mkdir(assistantId, "files/out")
        val moved = manager.move(assistantId, "files/docs/note.txt", "files/out/moved.txt")
        assertFalse(File(root, "files/docs/note.txt").exists())
        assertEquals(File(root, "files/out/moved.txt").canonicalPath, moved.canonicalPath)
        assertTrue(manager.exists(assistantId, "files/out/moved.txt"))
        assertTrue(manager.size(assistantId, "files/out/moved.txt") > 0L)

        val stat = manager.stat(assistantId, "files/out/moved.txt")
        assertEquals("files/out/moved.txt", stat.relativePath)
        assertFalse(stat.isDirectory)

        assertTrue(manager.delete(assistantId, "files/out", recursive = true))
        assertFalse(File(root, "files/out").exists())
        assertFalse(manager.delete(assistantId, "files/out"))
    }

    @Test
    fun deleteRefusesWorkspaceRoot() = runBlocking {
        manager.ensureWorkspace(assistantId)
        for (path in listOf("", ".", "files/..", "./")) {
            try {
                manager.delete(assistantId, path)
                fail("应拒绝删除工作区根: " + path)
            } catch (expected: WorkspaceSecurityException) {
            }
        }
    }

    @Test
    fun quotaUsageTracksFiles() = runBlocking {
        val root = manager.ensureWorkspace(assistantId)
        val before = manager.quotaUsageBytes(assistantId)
        manager.writeText(assistantId, "files/a.txt", "12345")
        val after = manager.quotaUsageBytes(assistantId)
        assertTrue(after >= before + 5L)
        val expected = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertEquals(expected, after)
        assertEquals(2L * 1024L * 1024L * 1024L, manager.quotaBytes())
        assertEquals(64L * 1024L * 1024L, manager.maxFileSizeBytes())
    }

    @Test
    fun deleteWorkspaceRemovesEverything() = runBlocking {
        val root = manager.ensureWorkspace(assistantId)
        manager.writeText(assistantId, "files/a.txt", "data")
        assertTrue(manager.deleteWorkspace(assistantId))
        assertFalse(root.exists())
        assertFalse(manager.deleteWorkspace(assistantId))
    }

    // ---------------- helpers ----------------

    private suspend fun assertSecurityRejected(relativePath: String) {
        try {
            manager.resolve(assistantId, relativePath)
            fail("应拒绝越界路径: " + relativePath)
        } catch (expected: WorkspaceSecurityException) {
            // 预期
        }
    }

    /** 优先建符号链接，Windows 无权限时退回目录联接（junction，canonicalPath 同样解析到根外）。 */
    private fun createDirectoryLink(link: File, target: File): Boolean {
        return try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (ignored: Exception) {
            try {
                val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.absolutePath, target.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                process.waitFor() == 0 && link.exists()
            } catch (ignoredToo: Exception) {
                false
            }
        }
    }
}

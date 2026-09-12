package com.luzzymeow.luzzyrp.data.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 迁移收件箱单测：分块协议的**顺序语义**与拼装完整性。
 *
 * 为什么这些断言重要：缺块拼出来的 JSON 有可能**恰好能被解析**，然后静默少掉一部分数据
 * （比如正在保存的那一段会话）。那是最坏的一类迁移缺陷——不报错、能启动、数据缺一块。
 * 所以「序号必须连续」不是洁癖，是数据完整性判据。
 */
class MigrationInboxTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun inbox() = MigrationInbox(temp.newFolder("migration"))

    @Test
    fun `顺序追加后再收尾得到完整内容`() {
        val box = inbox()
        box.start("s1")
        assertTrue(box.appendChunk(0, "{\"a\":"))
        assertTrue(box.appendChunk(1, "1}"))
        val result = box.finish(MigrationInbox.Summary(2, 0, 1, null))
        assertNotNull(result)
        assertEquals(2, result!!.chunkCount)
        assertEquals(7, result.charCount) // "{\"a\":" 5 字符 + "1}" 2 字符
        assertEquals("{\"a\":1}", box.readExport())
    }

    @Test
    fun `缺块被拒：跳号不会被静默接受`() {
        val box = inbox()
        box.start("s1")
        assertTrue(box.appendChunk(0, "aaa"))
        assertFalse("序号 2 跳过了 1，必须拒绝", box.appendChunk(2, "ccc"))
        assertFalse("重复序号也必须拒绝", box.appendChunk(0, "aaa"))
        assertTrue(box.appendChunk(1, "bbb"))
        assertEquals("aaabbb", box.readExport())
    }

    @Test
    fun `没有会话时拒收（防上次中断的半成品被续写）`() {
        val box = inbox()
        assertFalse(box.appendChunk(0, "xxx"))
        assertNull(box.finish(null))
    }

    @Test
    fun `start 会清掉上次残留`() {
        val box = inbox()
        box.start("s1")
        box.appendChunk(0, "旧的一半")
        box.start("s2")
        assertTrue(box.appendChunk(0, "新的"))
        assertEquals("新的", box.readExport())
        assertEquals(1, box.finish(null)!!.chunkCount)
    }

    @Test
    fun `abandon 清掉半成品，避免被当成完整数据读走`() {
        val box = inbox()
        box.start("s1")
        box.appendChunk(0, "半截")
        box.abandon()
        assertNull(box.readExport())
        assertNull(box.finish(null))
    }

    @Test
    fun `空导出不收尾（读不到数据时宁可为 null，也不要写出空文件）`() {
        val box = inbox()
        box.start("s1")
        assertNull(box.finish(null))
    }

    @Test
    fun `sha256 与内容一致且可复算`() {
        val box = inbox()
        box.start("s1")
        box.appendChunk(0, "hello")
        val result = box.finish(null)!!
        // 与已知值比对：拒绝「算了个别的什么哈希」
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            result.sha256,
        )
        assertEquals(result.sha256, MigrationInbox.sha256Of(result.file))
        assertTrue(result.file.name == MigrationInbox.FILE_EXPORT)
    }

    @Test
    fun `manifest 写出块数与完成标记（中断续跑的判据）`() {
        val box = inbox()
        box.start("s1")
        box.appendChunk(0, "{}")
        box.finish(MigrationInbox.Summary(3, 1, 1, 2))
        val manifest = temp.root.walkTopDown().first { it.name == MigrationInbox.FILE_MANIFEST }.readText()
        assertTrue(manifest.contains("\"chunks\": 1"))
        assertTrue(manifest.contains("\"completed\": true"))
        assertTrue(manifest.contains("\"mainKeys\": 3"))
        assertTrue(manifest.contains("\"legacyKeys\": 1"))
    }

    @Test
    fun `分块尺寸常量留在 Binder 安全区`() {
        // Binder 事务上限 1MB；块内容是 JSON，中文按 UTF-8 占 3 字节/字符。
        // 180k 字符最坏约 540KB —— 留了足够余量，这条断言防止日后有人随手调大。
        assertTrue(MigrationInbox.CHUNK_CHARS * 3 < 1_000_000)
    }
}

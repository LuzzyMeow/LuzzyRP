package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [TarExtractor] 单测：普通文件 / 目录 / 符号链接 / tar-slip 防护 / 设备节点跳过。
 *
 * 用内存构造 tar 流（512 字节块），不依赖外部 tar 工具。
 */
class TarExtractorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun block(): ByteArray = ByteArray(512)

    private fun putAscii(target: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.UTF_8).copyInto(target, offset)
    }

    private fun octal(value: Long, width: Int): String = value.toString(8).padStart(width - 1, '0') + "\u0000"

    private fun header(name: String, size: Long, type: Char, link: String = ""): ByteArray {
        val h = block()
        putAscii(h, 0, name)
        putAscii(h, 100, octal(0b111_101_101, 8)) // 0755
        putAscii(h, 108, octal(0, 8))
        putAscii(h, 116, octal(0, 8))
        putAscii(h, 124, octal(size, 12))
        putAscii(h, 136, octal(0, 12))
        putAscii(h, 148, "        ") // 校验和占位（本实现不校验）
        h[156] = type.code.toByte()
        if (link.isNotEmpty()) putAscii(h, 157, link)
        putAscii(h, 257, "ustar\u000000")
        return h
    }

    private fun tarOf(vararg entries: Triple<ByteArray, ByteArray, Long>): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { (header, body, size) ->
            out.write(header)
            if (size > 0) {
                out.write(body)
                val padding = ((size + 511) / 512) * 512 - size
                if (padding > 0) out.write(ByteArray(padding.toInt()))
            }
        }
        out.write(ByteArray(1024)) // 结束块
        return out.toByteArray()
    }

    @Test
    fun `普通文件与目录被解出`() {
        val content = "hello alpine".toByteArray()
        val tar = tarOf(
            Triple(header("etc/", 0, '5'), ByteArray(0), 0L),
            Triple(header("etc/hostname", content.size.toLong(), '0'), content, content.size.toLong()),
        )
        val target = folder.newFolder("rootfs")
        TarExtractor.extract(ByteArrayInputStream(tar), target)
        val file = File(target, "etc/hostname")
        assertTrue(file.isFile)
        assertEquals("hello alpine", file.readText())
    }

    @Test
    fun `符号链接被创建`() {
        val tar = tarOf(
            Triple(header("bin/sh", 0, '2', link = "busybox"), ByteArray(0), 0L),
        )
        val target = folder.newFolder("rootfs-symlink")
        TarExtractor.extract(ByteArrayInputStream(tar), target)
        val link = File(target, "bin/sh")
        assertTrue("符号链接应存在", link.exists() || java.nio.file.Files.isSymbolicLink(link.toPath()))
    }

    @Test
    fun `tar-slip 路径被拒绝`() {
        val content = "evil".toByteArray()
        val tar = tarOf(
            Triple(header("../escaped.txt", content.size.toLong(), '0'), content, content.size.toLong()),
        )
        val target = folder.newFolder("rootfs-slip")
        TarExtractor.extract(ByteArrayInputStream(tar), target)
        assertFalse("越界文件不得写出", File(target.parentFile, "escaped.txt").exists())
    }

    @Test
    fun `设备节点被跳过且不中断解包`() {
        val content = "after".toByteArray()
        val tar = tarOf(
            Triple(header("dev/null", 0, '3'), ByteArray(0), 0L), // 字符设备
            Triple(header("ok.txt", content.size.toLong(), '0'), content, content.size.toLong()),
        )
        val target = folder.newFolder("rootfs-dev")
        TarExtractor.extract(ByteArrayInputStream(tar), target)
        assertEquals("after", File(target, "ok.txt").readText())
    }

    @Test
    fun `多块文件跨 512 边界正确`() {
        val content = ByteArray(1300) { (it % 251).toByte() }
        val tar = tarOf(Triple(header("big.bin", content.size.toLong(), '0'), content, content.size.toLong()))
        val target = folder.newFolder("rootfs-big")
        TarExtractor.extract(ByteArrayInputStream(tar), target)
        val read = File(target, "big.bin").readBytes()
        assertEquals(content.size, read.size)
        assertTrue(content.contentEquals(read))
    }

    @Test
    fun `gzip 包装的 tar 也能解出（APK 内 rootfs 走这条路径）`() {
        val content = "root:x:0:0".toByteArray()
        val tar = tarOf(Triple(header("etc/passwd", content.size.toLong(), '0'), content, content.size.toLong()))
        val gz = ByteArrayOutputStream().apply {
            java.util.zip.GZIPOutputStream(this).use { it.write(tar) }
        }.toByteArray()
        // magic bytes 1f 8b 判定为 gzip
        assertEquals(0x1f, gz[0].toInt() and 0xFF)
        assertEquals(0x8b, gz[1].toInt() and 0xFF)
        val target = folder.newFolder("rootfs-gz")
        java.util.zip.GZIPInputStream(ByteArrayInputStream(gz)).use { TarExtractor.extract(it, target) }
        assertEquals("root:x:0:0", File(target, "etc/passwd").readText())
    }

    @Test
    fun `proot 启动命令包含 bind 与工作区 cwd`() {
        val cmd = ProotCommand.build(
            prootBin = File("/data/app/proot"),
            rootfsDir = File("/data/app/rootfs"),
            workspaceDir = File("/data/ws"),
            command = "apk add python3",
        )
        // 路径用 endsWith 断言（Windows 开发机上 File.absolutePath 会加盘符）
        assertTrue(cmd[0].endsWith("proot"))
        assertTrue(cmd.contains("--link2symlink"))
        assertTrue(cmd.contains("-0"))
        assertTrue(cmd.windowed(2).any { it[0] == "-r" && it[1].endsWith("rootfs") })
        assertTrue(cmd.windowed(2).any { it[0] == "-b" && it[1].endsWith(":/workspace") })
        assertTrue(cmd.windowed(2).any { it[0] == "-w" && it[1] == "/workspace" })
        assertEquals("apk add python3", cmd.last())
    }
}

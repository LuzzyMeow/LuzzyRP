package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import java.io.File
import java.io.InputStream
import java.nio.file.Files

/**
 * 极简 tar 解包器（零依赖，供 Alpine rootfs 释放使用）。
 *
 * 支持：普通文件、目录、符号链接、硬链接（尽力）、GNU 长名（`L`）、PAX 头（`x`/`g` 跳过）。
 * **不支持**：字符/块设备节点（Android 无权限创建，跳过并继续）。
 *
 * **安全**：逐条做 tar-slip 防护——路径归一后必须落在目标目录内，否则跳过该条目。
 */
internal object TarExtractor {

    private const val BLOCK = 512

    fun extract(input: InputStream, targetDir: File) {
        targetDir.mkdirs()
        val canonicalRoot = targetDir.canonicalFile
        var longName: String? = null

        while (true) {
            val header = readBlock(input) ?: break
            if (header.all { it == 0.toByte() }) break

            val rawName = readString(header, 0, 100)
            val prefix = readString(header, 345, 155)
            val sizeField = readString(header, 124, 12).trim().trimEnd('\u0000')
            val size = if (sizeField.isEmpty()) 0L else sizeField.toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()
            val linkName = readString(header, 157, 100)

            val name = longName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            longName = null

            val padded = ((size + BLOCK - 1) / BLOCK) * BLOCK

            when (typeFlag) {
                'L' -> {
                    // GNU 长文件名：内容即真实名字
                    val bytes = readExactly(input, size.toInt())
                    longName = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                    skipPadding(input, padded - size)
                }

                'x', 'g' -> {
                    readExactly(input, size.toInt())
                    skipPadding(input, padded - size)
                }

                '0', '\u0000', '7' -> {
                    val target = safeTarget(canonicalRoot, name) ?: run {
                        skipBytes(input, padded); null
                    }
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> copyExactly(input, out, size) }
                        skipPadding(input, padded - size)
                        applyMode(target, header)
                    }
                }

                '5' -> {
                    safeTarget(canonicalRoot, name)?.mkdirs()
                    skipBytes(input, padded)
                }

                '2' -> {
                    val target = safeTarget(canonicalRoot, name)
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        runCatching { Files.createSymbolicLink(target.toPath(), File(linkName).toPath()) }
                    }
                    skipBytes(input, padded)
                }

                '1' -> {
                    val target = safeTarget(canonicalRoot, name)
                    val source = safeTarget(canonicalRoot, linkName)
                    if (target != null && source != null && source.isFile) {
                        target.parentFile?.mkdirs()
                        runCatching { source.copyTo(target, overwrite = true) }
                    }
                    skipBytes(input, padded)
                }

                else -> skipBytes(input, padded) // 设备节点等：跳过
            }
        }
    }

    /** 归一化并做 tar-slip 防护；越界返回 null。 */
    private fun safeTarget(canonicalRoot: File, rawName: String): File? {
        val cleaned = rawName.trimStart('/', '.').replace('\\', '/')
        if (cleaned.isEmpty() || cleaned.contains("../") || cleaned == "..") return null
        val file = File(canonicalRoot, cleaned)
        return runCatching {
            if (file.canonicalFile.path == canonicalRoot.path ||
                file.canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
            ) file else null
        }.getOrNull()
    }

    private fun applyMode(target: File, header: ByteArray) {
        val mode = readString(header, 100, 8).trim().trimEnd('\u0000').toIntOrNull(8) ?: return
        runCatching {
            target.setReadable(true, true)
            target.setWritable(mode and 0b010_000_000 != 0, true)
            if (mode and 0b001_000_000 != 0) target.setExecutable(true, true)
        }
    }

    private fun readBlock(input: InputStream): ByteArray? {
        val block = ByteArray(BLOCK)
        var read = 0
        while (read < BLOCK) {
            val n = input.read(block, read, BLOCK - read)
            if (n < 0) return if (read == 0) null else block
            read += n
        }
        return block
    }

    private fun readString(block: ByteArray, offset: Int, length: Int): String {
        val end = (offset until minOf(offset + length, block.size)).firstOrNull { block[it] == 0.toByte() }
            ?: minOf(offset + length, block.size)
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return bytes
    }

    private fun copyExactly(input: InputStream, out: java.io.OutputStream, length: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun skipPadding(input: InputStream, count: Long) {
        if (count > 0) skipBytes(input, count)
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) return
            remaining -= n
        }
    }
}

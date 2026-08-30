package com.luzzymeow.luzzyrp.core.common

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.util.zip.CRC32

/**
 * PNG tEXt chunk 手工读写器。
 *
 * 用途：SillyTavern 角色卡将卡片 JSON 以 base64 存入 PNG 的 tEXt chunk
 * （关键字 `chara`，v3 卡另用 `ccv3`）。本类同时支持读取（导入）与写入（导出）。
 * 手工实现 chunk 级读写以避免引入重量级图像元数据库。
 *
 * PNG 结构：8 字节签名 + 若干 chunk（length(4) type(4) data(length) crc32(4)）。
 */
object PngTextChunk {

    /** 单条 tEXt 记录：keyword 与 Latin-1 文本值。 */
    data class TextEntry(val keyword: String, val value: String)

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val TYPE_IHDR = "IHDR"
    private const val TYPE_IEND = "IEND"
    private const val TYPE_TEXT = "tEXt"
    private const val MAX_CHUNK_LENGTH = 0x7FFFFFF7

    /** 读取 PNG 全部 tEXt 记录。 */
    fun readAll(png: ByteArray): List<TextEntry> {
        val input = DataInputStream(png.inputStream().buffered())
        val signature = ByteArray(8)
        input.readFully(signature)
        require(signature.contentEquals(PNG_SIGNATURE)) { "不是有效的 PNG 文件" }

        val entries = mutableListOf<TextEntry>()
        while (true) {
            val length = try {
                input.readInt()
            } catch (_: EOFException) {
                break
            }
            require(length in 0..MAX_CHUNK_LENGTH) { "PNG chunk 长度非法：$length" }
            val type = String(ByteArray(4).also { input.readFully(it) }, Charsets.US_ASCII)
            val data = ByteArray(length).also { input.readFully(it) }
            val crc = input.readInt()
            // CRC 校验：type + data
            val crc32 = CRC32().apply {
                update(type.toByteArray(Charsets.US_ASCII))
                update(data)
            }
            require(crc.toInt() == crc32.value.toInt()) { "PNG chunk CRC 校验失败（$type）" }

            if (type == TYPE_TEXT) {
                entries += parseTextChunk(data)
            }
            if (type == TYPE_IEND) break
        }
        return entries
    }

    /** 解析 tEXt 数据段：keyword\0text（均为 Latin-1）。 */
    private fun parseTextChunk(data: ByteArray): TextEntry {
        val zeroIndex = data.indexOf(0)
        require(zeroIndex >= 0) { "tEXt chunk 缺少 keyword 分隔符" }
        val keyword = String(data, 0, zeroIndex, Charsets.ISO_8859_1)
        val value = String(data, zeroIndex + 1, data.size - zeroIndex - 1, Charsets.ISO_8859_1)
        return TextEntry(keyword, value)
    }

    /**
     * 写入/替换一条 tEXt 记录，返回新的 PNG 字节。
     * 已存在同名 keyword 时原地替换；否则插到 IEND 之前。
     */
    fun writeEntry(png: ByteArray, keyword: String, value: String): ByteArray {
        val entry = encodeTextChunk(keyword, value)

        val input = DataInputStream(png.inputStream().buffered())
        val signature = ByteArray(8)
        input.readFully(signature)
        require(signature.contentEquals(PNG_SIGNATURE)) { "不是有效的 PNG 文件" }

        val out = ByteArrayOutputStream(png.size + entry.size + 12)
        val writer = DataOutputStream(out)
        writer.write(signature)

        var replaced = false
        while (true) {
            val length = try {
                input.readInt()
            } catch (_: EOFException) {
                break
            }
            val type = ByteArray(4).also { input.readFully(it) }
            val data = ByteArray(length).also { input.readFully(it) }
            val crc = input.readInt()

            val isTargetText = String(type, Charsets.US_ASCII) == TYPE_TEXT &&
                !replaced && parseKeyword(data) == keyword
            val isIend = String(type, Charsets.US_ASCII) == TYPE_IEND

            when {
                isTargetText -> {
                    // 原地替换同名 tEXt
                    writer.writeInt(entry.size - 8)
                    writer.write(entry)
                    replaced = true
                }

                isIend -> {
                    // 新增条目必须插在 IEND 之前（PNG 读取止于 IEND）
                    if (!replaced) {
                        writer.writeInt(entry.size - 8)
                        writer.write(entry)
                        replaced = true
                    }
                    writer.writeInt(length)
                    writer.write(type)
                    writer.write(data)
                    writer.writeInt(crc)
                    break
                }

                else -> {
                    writer.writeInt(length)
                    writer.write(type)
                    writer.write(data)
                    writer.writeInt(crc)
                }
            }
        }
        return out.toByteArray()
    }

    private fun parseKeyword(data: ByteArray): String {
        val zeroIndex = data.indexOf(0)
        return if (zeroIndex >= 0) String(data, 0, zeroIndex, Charsets.ISO_8859_1) else ""
    }

    /** 构造完整 chunk 字节：type(4) + data + crc32(4)，此处返回 data + crc，长度由调用方写入。 */
    private fun encodeTextChunk(keyword: String, value: String): ByteArray {
        val kw = keyword.toByteArray(Charsets.ISO_8859_1)
        val text = value.toByteArray(Charsets.ISO_8859_1)
        val data = ByteArray(kw.size + 1 + text.size)
        kw.copyInto(data)
        data[kw.size] = 0
        text.copyInto(data, kw.size + 1)

        val crc32 = CRC32().apply {
            update(TYPE_TEXT.toByteArray(Charsets.US_ASCII))
            update(data)
        }
        val chunk = ByteArray(4 + data.size + 4)
        val typeBytes = TYPE_TEXT.toByteArray(Charsets.US_ASCII)
        typeBytes.copyInto(chunk)
        data.copyInto(chunk, 4)
        val crcInt = crc32.value.toInt()
        for (i in 0 until 4) {
            chunk[4 + data.size + i] = (crcInt ushr ((3 - i) * 8)).toByte()
        }
        return chunk
    }

    /** 便捷方法：读取 SillyTavern 角色卡 JSON（chara / ccv3 字段，base64）。 */
    fun readCharacterCard(png: ByteArray): String? {
        val entries = readAll(png)
        val v3 = entries.firstOrNull { it.keyword == "ccv3" }
        val v2 = entries.firstOrNull { it.keyword == "chara" }
        return (v3 ?: v2)?.value
    }
}

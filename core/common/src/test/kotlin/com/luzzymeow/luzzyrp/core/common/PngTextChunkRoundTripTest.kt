package com.luzzymeow.luzzyrp.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * PNG tEXt chunk 读写往返测试（SillyTavern 角色卡导入/导出的数据基础）。
 */
class PngTextChunkRoundTripTest {

    /** 手工构造一个最小合法 PNG：签名 + IHDR(13B) + tEXt + IEND。 */
    private fun minimalPng(textEntries: List<PngTextChunk.TextEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        fun chunk(type: String, data: ByteArray) {
            val crc = CRC32().apply {
                update(type.toByteArray(Charsets.US_ASCII))
                update(data)
            }
            out.write((data.size ushr 24) and 0xFF)
            out.write((data.size ushr 16) and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write(data.size and 0xFF)
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(data)
            val c = crc.value.toInt()
            out.write((c ushr 24) and 0xFF)
            out.write((c ushr 16) and 0xFF)
            out.write((c ushr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        val ihdr = ByteArray(13)
        chunk("IHDR", ihdr)
        for (e in textEntries) {
            val kw = e.keyword.toByteArray(Charsets.ISO_8859_1)
            val value = e.value.toByteArray(Charsets.ISO_8859_1)
            chunk("tEXt", kw + 0 + value)
        }
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test
    fun `read and write roundtrip preserves entries`() {
        val original = minimalPng(listOf(PngTextChunk.TextEntry("chara", "eyJzcGVjIjoiY2hhcmFfY2FyZF92MiJ9")))
        val entries = PngTextChunk.readAll(original)
        assertEquals(1, entries.size)
        assertEquals("chara", entries[0].keyword)

        val updated = PngTextChunk.writeEntry(original, "chara", "NEW_PAYLOAD")
        val reRead = PngTextChunk.readAll(updated)
        assertEquals("NEW_PAYLOAD", reRead.first { it.keyword == "chara" }.value)
    }

    @Test
    fun `write adds new entry before IEND`() {
        val original = minimalPng(emptyList())
        val updated = PngTextChunk.writeEntry(original, "ccv3", "V3DATA")
        val entries = PngTextChunk.readAll(updated)
        assertEquals("V3DATA", entries.first { it.keyword == "ccv3" }.value)
    }

    @Test
    fun `replacing same keyword does not duplicate`() {
        var png = minimalPng(listOf(PngTextChunk.TextEntry("chara", "A")))
        png = PngTextChunk.writeEntry(png, "chara", "B")
        png = PngTextChunk.writeEntry(png, "chara", "C")
        val entries = PngTextChunk.readAll(png)
        assertEquals(1, entries.count { it.keyword == "chara" })
        assertEquals("C", entries.first { it.keyword == "chara" }.value)
    }

    @Test
    fun `readCharacterCard prefers ccv3 over chara`() {
        val png = minimalPng(
            listOf(
                PngTextChunk.TextEntry("chara", "V2BASE64"),
                PngTextChunk.TextEntry("ccv3", "V3BASE64"),
            )
        )
        assertEquals("V3BASE64", PngTextChunk.readCharacterCard(png))
    }

    @Test
    fun `readCharacterCard returns null when absent`() {
        assertNull(PngTextChunk.readCharacterCard(minimalPng(emptyList())))
    }

    @Test
    fun `rejects non png bytes`() {
        val result = runCatching { PngTextChunk.readAll("not a png".toByteArray()) }
        assertTrue(result.isFailure)
    }
}

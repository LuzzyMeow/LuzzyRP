package com.luzzymeow.luzzyrp.ui.pages.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 附件**设备侧 IO** 的仪器化用例（C4）。
 *
 * JVM 层已钉住纯函数（parts 构造 / 解析 / payload 编解码）；这里证的是**只有设备上才成立**的
 * 那一段：真解码 → 真降采样 → 真落盘 → 真读回。`BitmapFactory` / `ImageDecoder` 在 JVM 上
 * 全是 stub，这些判据在单测里写不出来。
 */
@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 造一张 2048×1024 的真实 PNG（大于 MAX_DIMENSION，验证降采样真的发生）。 */
    private fun writeSourceImage(): File {
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        val file = File(context.cacheDir, "attach-src-${System.nanoTime()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return file
    }

    @Test
    fun importDownscalesWritesFileAndReturnsReference() = runBlocking {
        val source = writeSourceImage()
        val attachment = AttachmentStore.import(context, Uri.fromFile(source))

        assertEquals("location 在约定的附件目录下", "assets/attachments/", attachment.location.take("assets/attachments/".length))
        assertEquals("image/jpeg", attachment.mime)
        val stored = AttachmentStore.fileFor(context, attachment.location)
        assertTrue("落盘文件应存在：${stored.absolutePath}", stored.isFile)
        assertTrue("JPEG 压缩应显著小于 PNG 原图", stored.length() in 1 until source.length())

        val decoded = android.graphics.BitmapFactory.decodeFile(stored.absolutePath)
        assertNotNull("落盘的应是可解码的 JPEG", decoded)
        val (w, h) = decoded!!.let { it.width to it.height }
        assertTrue("最长边应压到 ${AttachmentStore.MAX_DIMENSION}：${w}x$h", maxOf(w, h) <= AttachmentStore.MAX_DIMENSION)
        assertTrue("只缩不放大：短边保留比例", maxOf(w, h) == AttachmentStore.MAX_DIMENSION)
    }

    @Test
    fun readDataUrlRoundTripsTheStoredBytes() = runBlocking {
        val source = writeSourceImage()
        val attachment = AttachmentStore.import(context, Uri.fromFile(source))

        val dataUrl = AttachmentStore.readDataUrl(context, attachment.location)
        assertTrue("应是 data URL：${dataUrl.take(40)}", dataUrl.startsWith("data:image/jpeg;base64,"))

        // base64 解回的**字节数**与落盘文件一致（不逐字节比：JPEG 重编码不可逆，比大小即可）
        val base64 = dataUrl.substringAfter("base64,")
        assertEquals(AttachmentStore.fileFor(context, attachment.location).length(), java.util.Base64.getDecoder().decode(base64).size.toLong())
    }

    @Test
    fun missingFileThrowsHonestly() = runBlocking {
        try {
            AttachmentStore.readDataUrl(context, "assets/attachments/definitely-missing.jpg")
            fail("附件文件缺失必须抛（静默丢图 = 改写历史）")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("缺失"))
        }
    }

}

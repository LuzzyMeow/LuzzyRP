package com.luzzymeow.luzzyrp.ui.pages.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 附件的**设备侧 IO**（C4）：选图 → 降采样压缩 → 落盘 → 读回 data URL。
 *
 * ## 为什么选图那一刻就落盘（而不是等到发送）
 *
 * SAF 给的 `Uri` 是**一次性的**（授权随进程/重启失效），而附件是**消息的历史事实**——
 * 用户三天后回看这条消息，图必须还在。所以拿到 Uri 立刻导入成自有文件
 * （`filesDir/assets/attachments/`，与迁移附件同一目录约定），此后只认自己的路径。
 *
 * ## 压缩口径
 *
 * 最长边压到 [MAX_DIMENSION]（1024px）+ JPEG 85：聊天图看的是内容不是像素，
 * 一张 4000px 原图 base64 后是好几 MB 的请求体——那会让**每一轮**请求都背着它走
 * （模型要持续看得见它看过的图，见 `resolveImageParts` 的说明），必须压。
 *
 * ## 与纯函数层的边界
 *
 * 本文件只做 Android IO；尺寸判定（[targetDimension]）等纯逻辑放伴生对象里给 JVM 单测，
 * 编解码与协议翻译（三家 wire）另有各自的纯函数层。
 */
object AttachmentStore {

    /** 附件目录：**相对 `filesDir`**，与迁移写附件的约定逐字一致（`MigrationWriter.writeAssets`）。 */
    const val DIR = "assets/attachments"

    /** 导入图片的最长边（px）。 */
    const val MAX_DIMENSION = 1024

    /** 单条消息最多带几张：请求体与 payload 都会随张数线性膨胀，得有个诚实的上限。 */
    const val MAX_PER_MESSAGE = 4

    /** JPEG 压缩质量。 */
    private const val JPEG_QUALITY = 85

    /**
     * 目标尺寸（纯函数，可 JVM 单测）：最长边超过 [max] 才缩，**只缩不放大**；
     * 短边按比例取整且至少 1px。
     */
    fun targetDimension(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= max || longest <= 0) return maxOf(width, 1) to maxOf(height, 1)
        val scale = max.toFloat() / longest
        return maxOf((width * scale).toInt(), 1) to maxOf((height * scale).toInt(), 1)
    }

    /**
     * 导入一张图（SAF 选图结果）：解码 → 降采样 → JPEG → 落盘。
     *
     * 解不出位图（损坏文件 / 非图片）**抛异常**——调用方转成错误提示；
     * 「选了但没进来的图」必须让用户知道，不能静默吞掉。
     */
    suspend fun import(context: Context, uri: Uri): ChatAttachment = withContext(Dispatchers.IO) {
        val source = decode(context, uri) ?: throw IllegalArgumentException("无法读取所选图片（$uri）")
        val (w, h) = targetDimension(source.width, source.height, MAX_DIMENSION)
        val scaled = if (w == source.width && h == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, w, h, true)
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "c4-${System.currentTimeMillis()}-${(0..999).random()}.jpg")
        file.writeBytes(bytes)
        ChatAttachment(
            location = "$DIR/${file.name}",
            mime = "image/jpeg",
            name = queryDisplayName(context, uri),
        )
    }

    /**
     * 路径 → 可直发的 data URL（`resolveImageParts` 的读端）。
     *
     * `data:` 形态原样返回（旧数据的内联小图）；文件读不到**抛异常**——
     * 一张模型看过的图静默消失等于改写历史，如实报错好过让模型「失忆」。
     */
    fun readDataUrl(context: Context, location: String): String {
        if (location.startsWith("data:")) return location
        val file = fileFor(context, location)
        val bytes = file.takeIf { it.isFile }?.readBytes()
            ?: throw IllegalStateException("附件文件缺失：$location（消息里引用的图已不存在）")
        val mime = guessMime(file) { BitmapFactory.decodeFile(file.absolutePath) }
        return "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    fun fileFor(context: Context, location: String): File = File(context.filesDir, location)

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri),
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private inline fun guessMime(file: File, fallbackDecode: () -> Bitmap?): String =
        when {
            file.extension.equals("png", ignoreCase = true) -> "image/png"
            file.extension.equals("webp", ignoreCase = true) -> "image/webp"
            file.extension.equals("gif", ignoreCase = true) -> "image/gif"
            else -> "image/jpeg"
        }
}

package com.luzzymeow.luzzyrp.ui.pages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * **角色头像**（批 C C2）：真图 + 真降级。
 *
 * ## 为什么值得单独成文件
 *
 * 迁移进来 3 张卡，头像形态**三种都有**（真实夹具实测）：
 * 1. `data:image/jpeg;base64,…` **内联图**（Vanio 那张是真照片，几十 KB）；
 * 2. `data:image/svg+xml;base64,…` **内联 SVG**（谢昭/夏梧是默认占位图）——
 *    `BitmapFactory` **解不了 SVG**，会返回 null；
 * 3. 无头像 / 文件路径（迁移时被抽成了 `assets/avatars/…`）。
 *
 * 所以「有图显示图、无图显示首字 monogram」这条降级路径**必须真的存在**，
 * 而且要能被判据钉住——这正是本仓库栽过的那类坑（`DOM 里有 ≠ 用户看得见`）：
 * 只写「能解码就显示」而不测 SVG 与文件路径这两条路，真机上就会白块一片。
 *
 * ## 解码纪律（照抄 `DESIGN-compose §21.1` 的既有约定）
 *
 * - **按目标尺寸采样**（`inSampleSize`）：头像只有 22–40dp，解码成原图尺寸是纯浪费，
 *   真机上就是滚动掉帧；
 * - **有界 LRU**：列表滚动会反复请求同一张图，没有缓存就是反复解码；
 * - **只在后台线程解码**（`Dispatchers.IO`），主线程只负责画。
 */
object AvatarLoader {

    /** 解码目标尺寸（像素上限）：比实际显示尺寸大一点以适配高 DPI，但远小于原图。 */
    private const val MAX_PIXELS = 192

    /** 有界 LRU（按 key 缓存**已解码**的位图；键含目标尺寸）。 */
    private val cache = object : LruCache<String, Bitmap>(CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private const val CACHE_ENTRIES = 32

    /**
     * 解码一个头像。
     *
     * @param source 三种形态：`data:` URI / 绝对文件路径 / 其它（一律失败返回 null）
     * @return 解码成功给位图；**任何失败都返回 null**（调用方据此走 monogram 降级）——
     *         这里绝不抛异常：一张坏图不该让整个列表崩掉
     */
    suspend fun load(source: String?): Bitmap? {
        val key = source?.takeIf { it.isNotBlank() } ?: return null
        cache.get(key)?.let { return it }
        val decoded = withContext(Dispatchers.IO) { decode(key) }
        if (decoded != null) cache.put(key, decoded)
        return decoded
    }

    private fun decode(source: String): Bitmap? = runCatching {
        when {
            source.startsWith("data:") -> decodeDataUri(source)
            // 迁移把内联图抽成文件后，avatar 字段就是文件路径（`assets/avatars/…`）
            else -> decodeFile(source)
        }
    }.getOrNull()

    /** `data:image/…;base64,<payload>` → 位图。**SVG 会解码失败并返回 null**（预期行为）。 */
    private fun decodeDataUri(source: String): Bitmap? {
        val comma = source.indexOf(',')
        if (comma < 0) return null
        val meta = source.substring(0, comma)
        val payload = source.substring(comma + 1)
        // 只认 base64；`data:image/svg+xml;utf8,<svg…>` 这类非 base64 形态直接降级
        if (!meta.contains("base64", ignoreCase = true)) return null
        val bytes = runCatching { android.util.Base64.decode(payload, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return null
        return decodeBytes(bytes)
    }

    private fun decodeFile(path: String): Bitmap? {
        val file = File(path.ifBlank { return null })
        if (!file.isFile) return null
        return decodeBytes(file.readBytes())
    }

    /**
     * 两趟解码：先只读边界算 [inSampleSize]，再真解码。
     *
     * 一趟解码原图再缩放在小图上「看起来也能用」，但一张 2000×2000 的卡面图
     * 会白白占几十 MB 内存——列表里几张就是 OOM 的种子。
     */
    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / 2 >= MAX_PIXELS && height / 2 >= MAX_PIXELS) {
            width /= 2
            height /= 2
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** 测试与调试用：清缓存（真机上不需要调用）。 */
    fun clearCache() = cache.evictAll()
}

/**
 * 头像组件：**能解码就显示图，否则显示首字**。
 *
 * 判据（真机回归清单 B2 会量这一条）：图与 monogram 都是 `size × size` 的圆形，
 * `width > 0`——不能出现「有图但看不见」（尺寸为 0）或「占位图把真图盖住」。
 */
@Composable
fun AvatarImage(
    name: String,
    avatarPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, avatarPath) {
        value = AvatarLoader.load(avatarPath)
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "$name 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            // 降级：首字 monogram。**这条路径必须真的走得到**——夹具里 2/3 的头像是
            // SVG（BitmapFactory 解不了），所以它不是「理论上存在的兜底」。
            Text(
                text = name.take(1),
                fontFamily = LuzzyFonts.Lora,
                fontSize = (size.value * 0.5f).sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

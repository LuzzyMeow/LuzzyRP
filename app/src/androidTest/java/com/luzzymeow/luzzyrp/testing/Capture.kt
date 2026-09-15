package com.luzzymeow.luzzyrp.testing

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **仪器化视觉留证的统一落点**（记忆页 / 用量页共用，2026-09-15）。
 *
 * ## 为什么写文件而不是「跑起来自己看」
 *
 * 模拟器的应用库是空的，有数据的状态（条目行、图表曲线、弹层）在空库上**根本到不了**；
 * 而直接往 `/data/data/...` 塞 SQLite 会被 Room 的 schema 校验挡下（identity hash 对不上）。
 * 所以视觉审查走「测试里种数据 → 渲染 → 截图 → 人工 `read_image`」。
 *
 * ## 落点为什么是 `/sdcard/Download/luzzy-captures/`（三条都实测过）
 *
 * | 落点 | 结果 |
 * |---|---|
 * | `Android/media/<pkg>` | **随卸载被删**——AGP 跑完会卸载测试包，事后取不到 |
 * | `Android/data/<pkg>` | 对 `adb shell` 受限（`ls` 直接报「不存在」） |
 * | `getExternalFilesDir` | 这台 AVD 上压根没被创建 |
 * | **`Download/`（经 MediaStore）** | 活过卸载 + adb 直接可读 → 唯一同时满足的 |
 *
 * ## 文件名带时间戳的原因
 *
 * MediaStore 的 `files` 表对 `_data`（完整路径）有 UNIQUE 约束，而在 MediaStore 背后直接删文件
 * 会留下悬空索引行 → 同名再插必报 `UNIQUE constraint failed: files._data`。
 * 换名之后索引不再冲突；拉回本地时由脚本去掉 `HHmmss-` 前缀。
 */
object Capture {

    private val stampFormat = SimpleDateFormat("HHmmss", Locale.US)

    /** 本次运行的统一前缀（同一个测试类的所有图用同一个前缀，便于一次拉取）。 */
    fun newStamp(): String = stampFormat.format(Date())

    /**
     * 截当前界面。
     *
     * @param name 文件名（会加上时间戳前缀）
     * @param allRoots `true` 时把**每个 root 都存一份**：弹层是独立窗口，而它是否被计入
     *   `isRoot()` 与时机有关（同一段代码两次运行实测到 2 与 1），猜错的表现是「截到弹层背后那一页」。
     *   存全量后由人工挑，判据不依赖时序假设。
     */
    fun shot(
        context: Context,
        rule: ComposeTestRule,
        name: String,
        stamp: String,
        allRoots: Boolean = false,
    ) {
        val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        val indices = if (allRoots) (0 until roots).toList() else listOf(0)
        indices.forEach { index ->
            val safe = index.coerceIn(0, (roots - 1).coerceAtLeast(0))
            val bitmap = rule.onAllNodes(isRoot())[safe].captureToImage().asAndroidBitmap()
            val fileName = if (allRoots && roots > 1) "$stamp-$name.root$safe.png" else "$stamp-$name.png"
            write(context, fileName, bitmap)
        }
    }

    private fun write(context: Context, fileName: String, bitmap: android.graphics.Bitmap) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/luzzy-captures")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            android.util.Log.e("LuzzyCapture", "MediaStore 插入失败：$fileName")
            return
        }
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        android.util.Log.i("LuzzyCapture", "已写入 Download/luzzy-captures/$fileName")
    }
}

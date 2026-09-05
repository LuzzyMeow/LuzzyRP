package com.luzzymeow.luzzyrp.util

import android.content.Context
import com.luzzymeow.luzzyrp.BuildConfig
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * assets 解压器（rphub 上游 + ext 扩展层）。
 *
 * [HARD-REQ-GUIDE] 壳工程关键决策（AGENTS.md §7）：
 * 必须加载 filesDir 而非 android_asset —— WebView 的 localStorage 在
 * `file:///android_asset` 路径下不可靠（部分系统 WebView 不持久化），
 * 解压到应用私有目录 (filesDir) 是标准做法，数据持久化才有保障。
 *
 * 解压两个根目录：
 * - `rphub/` → filesDir/rphub/（RP-Hub 上游文件，index.html 入口）
 * - `ext/`   → filesDir/ext/（二创扩展层，index.html 尾部挂载 ../ext/ 引用）
 *
 * 幂等：目标目录已存在且标记文件版本匹配时跳过；首次启动执行完整解压。
 */
object AssetExtractor {

    private const val TAG = "AssetExtractor"

    /** 需要解压的 assets 根目录（assets 内）→ filesDir 目标子目录名 */
    private val ROOTS = listOf(
        "rphub" to "rphub",
        "ext" to "ext"
    )

    /** 解压标记文件名（内容存构建期资产签名） */
    private const val MARKER_NAME = ".extracted_sig"

    /**
     * 返回 WebView 实际加载的入口 HTML 的绝对路径。
     * 首次调用会执行解压（若未解压或版本不符）。
     */
    fun ensureExtracted(context: Context): File {
        for ((assetRoot, targetName) in ROOTS) {
            val target = File(context.filesDir, targetName)
            if (needsExtract(target)) {
                extract(context, assetRoot, target)
            }
        }
        return File(context.filesDir, "rphub")
    }

    private fun needsExtract(target: File): Boolean {
        val marker = File(target, MARKER_NAME)
        if (!marker.exists()) return true
        // 标记内容与构建期签名不一致（资产或构建变更）→ 重新解压
        return runCatching { marker.readText().trim() != BuildConfig.ASSET_SIGNATURE }.getOrDefault(true)
    }

    private fun extract(context: Context, assetRoot: String, target: File) {
        try {
            // 全新解压：先清掉旧目录（避免残留旧版本文件）
            if (target.exists()) {
                target.deleteRecursively()
            }
            if (!target.mkdirs()) {
                throw IOException("无法创建目标目录: ${target.absolutePath}")
            }

            copyAssetDir(context, assetRoot, target)

            // 写解压标记（内容=构建期资产签名；资产变更时由 needsExtract 判定重解压）
            File(target, MARKER_NAME).writeText(BuildConfig.ASSET_SIGNATURE)

            // 保险：清理旧格式标记（.extracted_v*）
            target.listFiles()
                ?.filter { it.name.startsWith(".extracted_v") }
                ?.forEach { it.delete() }

            Log.i(TAG, "解压完成: $assetRoot -> ${target.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "解压失败 ($assetRoot): ${e.message}", e)
            // 失败不崩溃：WebView 加载时再次尝试；目录不完整时前台可见
        }
    }

    /** 递归复制 assets 子目录。 */
    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val entries = assetManager.list(assetPath) ?: return
        for (entry in entries) {
            if (entry.startsWith(".extracted")) continue // 目录内永不复写解压标记（.extracted_sig/.extracted_v*）
            val childAssetPath = if (assetPath.isEmpty()) entry else "$assetPath/$entry"
            val childDest = File(destDir, entry)

            val hasChildren = !assetManager.list(childAssetPath).isNullOrEmpty()
            if (hasChildren) {
                if (!childDest.exists() && !childDest.mkdirs()) {
                    throw IOException("无法创建目录: ${childDest.absolutePath}")
                }
                copyAssetDir(context, childAssetPath, childDest)
            } else {
                assetManager.open(childAssetPath).use { input ->
                    FileOutputStream(childDest).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

package com.luzzymeow.luzzyrp.util

import android.content.Context
import com.luzzymeow.luzzyrp.BuildConfig
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * assets 解压器（v3.0 P6 起**只剩 ext 扩展层**——rphub 上游资产已随 WebView 路径退役删除）。
 *
 * [HARD-REQ-GUIDE] 解压到 filesDir 的历史决策（AGENTS.md §7）：WebView 的 file:// origin
 * 存储（localStorage / IndexedDB）依赖可写路径；老用户的旧数据仍在设备侧 filesDir 下，
 * 迁移通道（MigrationRunner 的隐藏 WebView）按同 origin 读取。
 *
 * 解压根目录：
 * - `ext/` → filesDir/ext/（迁移页 luzzy-migrate.html 在其中；其余 v2.x 扩展文件
 *   不再被执行但无害，不清理——见 docs/HANDOFF-p6-static.md §二）
 *
 * 幂等：目标目录已存在且标记文件版本匹配时跳过；首次启动执行完整解压。
 */
object AssetExtractor {

    private const val TAG = "AssetExtractor"

    /** 需要解压的 assets 根目录（assets 内）→ filesDir 目标子目录名（P6 起只剩 ext） */
    private val ROOTS = listOf(
        "ext" to "ext"
    )

    /** 解压标记文件名（内容存构建期资产签名） */
    private const val MARKER_NAME = ".extracted_sig"

    /**
     * 确保 ext/ 已解压到 filesDir 并返回该目录。
     * 首次调用会执行解压（若未解压或版本不符）。
     * 必须在迁移（MigrationCoordinator.ensureMigrated）**之前**调用：
     * 迁移页 files/ext/luzzy-migrate.html 由这里解压出来，全新安装也要先有它。
     */
    fun ensureExtExtracted(context: Context): File {
        for ((assetRoot, targetName) in ROOTS) {
            val target = File(context.filesDir, targetName)
            if (needsExtract(target)) {
                extract(context, assetRoot, target)
            }
        }
        return File(context.filesDir, "ext")
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

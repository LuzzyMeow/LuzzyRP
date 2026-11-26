package com.luzzymeow.luzzyrp.util

import android.content.Context
import android.util.Log
import com.luzzymeow.luzzyrp.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetExtractor {

    private const val TAG = "AssetExtractor"
    private const val ASSET_ROOT = "rphub"
    private const val TARGET_NAME = "rphub"
    private const val MARKER_NAME = ".extracted_sig"

    fun ensureExtracted(context: Context): File {
        val target = File(context.filesDir, TARGET_NAME)
        if (needsExtract(target)) {
            extract(context, ASSET_ROOT, target)
        }
        return target
    }

    private fun needsExtract(target: File): Boolean {
        val marker = File(target, MARKER_NAME)
        if (!marker.exists()) return true
        return runCatching { marker.readText().trim() != BuildConfig.ASSET_SIGNATURE }.getOrDefault(true)
    }

    private fun extract(context: Context, assetRoot: String, target: File) {
        try {
            if (target.exists()) {
                target.deleteRecursively()
            }
            if (!target.mkdirs()) {
                throw IOException("无法创建目标目录: ${target.absolutePath}")
            }
            copyAssetDir(context, assetRoot, target)
            File(target, MARKER_NAME).writeText(BuildConfig.ASSET_SIGNATURE)
            Log.i(TAG, "解压完成: $assetRoot -> ${target.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "解压失败 ($assetRoot): ${e.message}", e)
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val entries = assetManager.list(assetPath) ?: return
        for (entry in entries) {
            if (entry.startsWith(".extracted")) continue
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

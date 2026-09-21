package com.luzzymeow.luzzyrp.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast

/**
 * 下载桥（角色卡/世界书/分支导出等 `a[download]` 场景）。
 *
 * RP-Hub 的导出在浏览器是「触发下载」；WebView 中默认无下载行为，
 * 通过 DownloadListener 接管，落盘到外部下载目录并提示。
 *
 * 注意：RP-Hub 多数导出走 blob: URL（前端生成内容），系统 DownloadManager
 * 无法消费 blob: ——此场景由扩展层 luzzy-bridge.js 走 FileChooser/SAF 导出；
 * 此处仅接管常规 http(s) 下载。
 */
object DownloadHandler {

    /** MainActivity 调用：给 WebView 挂 DownloadListener。 */
    fun attach(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.isNullOrEmpty()) return@setDownloadListener
            val context = webView.context

            // blob: URL 交给扩展层桥接处理（JS 侧 FileChooser/SAF 导出）
            if (url.startsWith("blob:")) {
                return@setDownloadListener
            }

            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDescription("LuzzyRP 导出文件")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "LuzzyRP/$fileName"
                    )
                CookieManager.getInstance().apply {
                    val cookie = getCookie(url)
                    if (!cookie.isNullOrEmpty()) request.addRequestHeader("Cookie", cookie)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "导出已开始：$fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

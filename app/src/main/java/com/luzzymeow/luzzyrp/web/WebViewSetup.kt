package com.luzzymeow.luzzyrp.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView 统一配置（AGENTS.md §1.2）。
 *
 * 关键决策：
 * - 加载 filesDir（AssetExtractor 解压后）而非 android_asset —— localStorage 持久化依赖可写路径；
 * - DOM storage 必须开启（RP-Hub 全部数据存 localStorage）；
 * - 允许 file:// 页面访问本地资源（RP-Hub 为纯本地应用）；
 * - 混合内容：RP-Hub 的 API 请求为 https，无需全局明文放行（按需在 Manifest 处理）。
 */
object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        val settings: WebSettings = webView.settings

        // JS 与存储
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // 本地文件访问（RP-Hub 依赖 file:// 相对路径资源）
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.setAllowFileAccessFromFileURLs(true)
        @Suppress("DEPRECATION")
        settings.setAllowUniversalAccessFromFileURLs(true)

        // 缓存：启用缓存但保证资源更新可见（版本升级后目录已重新解压）
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 渲染
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false

        // UA 标注（供站点识别；RP-Hub 本地应用无实际影响）
        settings.userAgentString = "${settings.userAgentString} LuzzyRP/1.0"
    }
}

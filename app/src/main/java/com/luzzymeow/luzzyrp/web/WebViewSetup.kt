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
        // 真机调试开关（用户 2026-09-09 决策）：release 包同样开启 WebView 内容调试。
        // 缘由：用户日常真机改为**直接体验 release 签名包**（与最终分发件同物），
        // release 不可调试会导致 CDP 排查通道整体失效（帧率/布局/脚本耗时只能靠猜）。
        // 代价：任何能连 adb 的电脑都可检查页面内容——本应用仅侧载分发，接受该代价。
        // 必须早于任何内容加载调用（本方法在 WebView 创建后、loadUrl 之前执行）。
        WebView.setWebContentsDebuggingEnabled(true)

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

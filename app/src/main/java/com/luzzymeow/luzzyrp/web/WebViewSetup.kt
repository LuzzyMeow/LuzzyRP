package com.luzzymeow.luzzyrp.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.setAllowFileAccessFromFileURLs(true)
        @Suppress("DEPRECATION")
        settings.setAllowUniversalAccessFromFileURLs(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = "${settings.userAgentString} LuzzyRP/2.0.6.2"
    }
}

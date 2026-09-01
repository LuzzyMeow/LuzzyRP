package com.luzzymeow.luzzyrp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.luzzymeow.luzzyrp.util.AssetExtractor
import com.luzzymeow.luzzyrp.web.DownloadHandler
import com.luzzymeow.luzzyrp.web.FileChooserHandler
import com.luzzymeow.luzzyrp.web.LuzzyBridge
import com.luzzymeow.luzzyrp.web.WebViewSetup
import java.io.File

/**
 * LuzzyRP 单 Activity 壳（v1.0.0 重建）。
 *
 * 职责（AGENTS.md §1.2）：
 * - 承载 WebView，加载 AssetExtractor 解压后的 RP-Hub 入口 HTML；
 * - 注入 LuzzyBridge（JSBridge 原生实现）；
 * - 委托文件选择（角色卡导入）与下载（导出）处理器。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val fileChooserHandler = FileChooserHandler()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸：不显示系统 ActionBar（壳无原生标题栏，标题由 RP-Hub 前端渲染）
        // API 29+ 去掉导航栏对比度 scrim（Android 10-11 手势条区域的深色遮罩），
        // 让导航栏区域透出 windowBackground 白色（与页面浅色背景连续）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false)
        }

        // 1) 确保 RP-Hub 资源已解压到 filesDir
        val rphubDir: File = AssetExtractor.ensureExtracted(this)
        val entryFile = File(rphubDir, "index.html")

        // 2) 创建 WebView 并配置
        webView = WebView(this)
        WebViewSetup.configure(webView)
        webView.setBackgroundColor(Color.WHITE)

        // 3) JSBridge（扩展层桥接，AGENTS.md §5）
        webView.addJavascriptInterface(LuzzyBridge(this), "LuzzyBridge")

        // 4) 客户端：同源导航留在 WebView 内（RP-Hub 纯本地）
        webView.webViewClient = WebViewClient()
        DownloadHandler.attach(webView)

        // 5) 文件选择/导出代理（角色卡 PNG/JSON 导入导出）
        webView.webChromeClient = fileChooserHandler.webChromeClient()

        // 6) 加载入口
        webView.loadUrl("file://" + entryFile.absolutePath)

        // 7) 布局：全屏 WebView + 系统栏 insets 适配
        //    Android 15+ 强制 edge-to-edge：内容会绘制到状态栏/导航栏之后，
        //    不处理 insets 则顶栏与系统时间重叠、底部输入栏贴边影响点击。
        val root = FrameLayout(this).apply {
            addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 8) 返回键：WebView 可回退时回退，否则默认退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileChooserHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

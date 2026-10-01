package com.luzzymeow.luzzyrp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
 *
 * [v1.5.0 移除] 原「助手」原生覆盖层（ComposeView 兄弟视图 + 返回键三级优先级 +
 * 覆盖层进/出过渡 + WebView 停绘/暂停定时器）已按用户指示于 2026-09-11 彻底移除。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var bridge: LuzzyBridge
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
        // debug 构建开启 WebView 远程调试（chrome://inspect / CDP；release 不受影响）
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // 3) JSBridge（扩展层桥接，AGENTS.md §5）
        bridge = LuzzyBridge(this)
        webView.addJavascriptInterface(bridge, "LuzzyBridge")

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
        root = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
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

        // 8) 返回键：先问页面是否消费（弹窗 → 抽屉 → 非对话页回对话页），
        //    页面无事可做时才走 WebView 历史，最后才退出。
        //
        //    为何需要「问页面」这一步（2026-10-01 静态检查发现）：
        //    上游 RP-Hub 是**不用 History API 的单页应用**（全仓无 pushState/popstate/hashchange），
        //    页面切换只改响应式 currentView —— 于是 webView.canGoBack() 恒为 false，
        //    旧实现下「在设置/关于/外观等任意页面按返回键」都会直接退出应用。
        //    接管实现见扩展层 ext/luzzy-back.js（零上游改动）。
        //    异步回调中若用户已离开本 Activity，直接丢弃结果，不触碰已销毁的 WebView。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                askPageToHandleBack { handled ->
                    if (isFinishing || isDestroyed) return@askPageToHandleBack
                    if (handled) return@askPageToHandleBack
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    /**
     * 询问 WebView 内的页面是否要消费本次返回。
     * 扩展层提供 `window.__luzzyHandleBack()`；不可用时回调 false（等价于改前行为）。
     */
    private fun askPageToHandleBack(onResult: (Boolean) -> Unit) {
        val js = "(function(){try{return typeof window.__luzzyHandleBack==='function'?!!window.__luzzyHandleBack():false;}catch(e){return false;}})()"
        try {
            webView.evaluateJavascript(js) { value ->
                onResult(value != null && value.trim().trim('"') == "true")
            }
        } catch (e: Exception) {
            // 扩展层缺失或 WebView 状态异常：退回原行为，绝不阻断返回键
            onResult(false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileChooserHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}

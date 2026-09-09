package com.luzzymeow.luzzyrp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.luzzymeow.luzzyrp.assistant.AssistantController
import com.luzzymeow.luzzyrp.assistant.ui.AssistantApp
import com.luzzymeow.luzzyrp.util.AssetExtractor
import com.luzzymeow.luzzyrp.web.DownloadHandler
import com.luzzymeow.luzzyrp.web.FileChooserHandler
import com.luzzymeow.luzzyrp.web.LuzzyBridge
import com.luzzymeow.luzzyrp.web.WebViewSetup
import java.io.File

/**
 * LuzzyRP 单 Activity 壳（v1.0.0 重建；v1.5.0 新增助手覆盖层）。
 *
 * 职责（AGENTS.md §1.2 / PLAN §2.3）：
 * - 承载 WebView，加载 AssetExtractor 解压后的 RP-Hub 入口 HTML；
 * - 注入 LuzzyBridge（JSBridge 原生实现）；
 * - 委托文件选择（角色卡导入）与下载（导出）处理器；
 * - **v1.5.0**：承载「助手」原生覆盖层（ComposeView 懒创建，WebView 保持存活）。
 */
class MainActivity : ComponentActivity(), AssistantController {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private val fileChooserHandler = FileChooserHandler()

    // ------------------------------------------------------------------
    // [v1.5.0 助手] 覆盖层状态（PLAN §3.1/§3.2：同 Activity 原生覆盖层，懒创建）
    // ------------------------------------------------------------------
    /** 懒创建：首次点击侧栏「助手」才构建 ComposeView，避免冷启动成本。 */
    private var assistantView: ComposeView? = null
    private var assistantVisible = false
    private var assistantDarkTheme = false

    /** 主题模式变化需触发重组——用 Compose 的 MutableState 桥接。 */
    private var darkThemeState by mutableStateOf(false)

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

        // 3) JSBridge（扩展层桥接，AGENTS.md §5；v1.5.0 起接线助手控制）
        webView.addJavascriptInterface(LuzzyBridge(this, this), "LuzzyBridge")

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
        //    助手覆盖层作为兄弟视图叠在 WebView 之上（GONE，懒创建）。
        root = FrameLayout(this).apply {
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

        // 8) 返回键优先级（PLAN §2.3）：助手可见 → 先关助手；否则 WebView 回退；再否则退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (assistantVisible) {
                    hideAssistant()
                } else if (webView.canGoBack()) {
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

    // ------------------------------------------------------------------
    // [v1.5.0 助手] AssistantController 实现
    // ------------------------------------------------------------------

    /**
     * 显示助手覆盖层。首次调用懒创建 ComposeView（PLAN §3.2）。
     * WebView 不销毁、不暂停（状态不丢）。
     * 注意：桥接方法从 WebView 的 JS 线程调用，视图操作必须切主线程。
     */
    override fun showAssistant() {
        runOnUiThread {
            val view = assistantView ?: ComposeView(this).also { created ->
                created.setContent {
                    val dark = darkThemeState
                    AssistantApp(darkTheme = dark, onExit = ::hideAssistant)
                }
                root.addView(
                    created,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                created.visibility = View.GONE
                assistantView = created
            }
            if (!assistantVisible) {
                view.visibility = View.VISIBLE
                assistantVisible = true
                // 助手覆盖层全屏显示时，WebView 仍在后台重绘（Vue 应用有动画/定时器），
                // 与 Compose 争抢合成器 → 帧率明显下降。这里把它停绘+暂停定时器，
                // 关闭时原样恢复（**不销毁、状态不丢**）。
                webView.visibility = View.INVISIBLE
                webView.onPause()
                webView.pauseTimers()
                notifyAssistantVisibility(true)
            }
        }
    }

    override fun hideAssistant() {
        runOnUiThread {
            if (!assistantVisible) return@runOnUiThread
            assistantView?.visibility = View.GONE
            assistantVisible = false
            webView.resumeTimers()
            webView.onResume()
            webView.visibility = View.VISIBLE
            notifyAssistantVisibility(false)
        }
    }

    override fun isAssistantVisible(): Boolean = assistantVisible

    override fun setThemeMode(dark: Boolean) {
        if (assistantDarkTheme == dark) return
        assistantDarkTheme = dark
        runOnUiThread { darkThemeState = dark }
    }

    /** Kotlin → JS：助手显隐通知（前端可据此暂停/恢复轮询等）。 */
    private fun notifyAssistantVisibility(visible: Boolean) {
        val js = "window.Luzzy && window.Luzzy.onAssistantVisibilityChanged " +
            "&& window.Luzzy.onAssistantVisibilityChanged($visible);"
        runOnUiThread {
            runCatching { webView.evaluateJavascript(js, null) }
        }
    }
}

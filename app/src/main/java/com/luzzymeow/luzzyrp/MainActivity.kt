package com.luzzymeow.luzzyrp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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

    /** 助手覆盖层过渡时长（DESIGN.md Motion 令牌：进 200ms / 退 140ms）。 */
    private companion object {
        const val ENTER_DURATION_MS = 200L
        const val EXIT_DURATION_MS = 140L

        /** 呼出前端侧栏（返回 "true" 表示汉堡按钮已点到、侧栏已开始滑入）。 */
        const val JS_OPEN_RP_SIDEBAR =
            "!!(window.Luzzy && window.Luzzy.openRpSidebar && window.Luzzy.openRpSidebar())"
    }

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

    /** 助手初始页面（RP 侧栏「助手」子项传入；空串 = 首页聊天页版式）。 */
    private var assistantRouteState by mutableStateOf("")

    /**
     * 侧栏子项「进入助手」的导航请求序号。
     *
     * 每次 `showAssistant` 自增：`initialRoute` 是字符串，同一路由（尤其是「对话」的空串）
     * 连续进入时 key 不变，Compose 的 `LaunchedEffect` 不会重跑 → 页面切不过去（会话 57 真机实测）。
     */
    private var assistantNavSeq by mutableStateOf(0)

    /**
     * 覆盖层正在做进入交叉淡化（侧栏子项进入 → 200ms 后转 false）。
     *
     * 传给助手层用于**抑制页内动画**：这一次切换的「页内容交叉淡化」由覆盖层 alpha 与 WebView
     * 页完成（DESIGN.md 页面交接令牌），页内若再淡入淡出，新页会在覆盖层还半透明时就换完，
     * 观感即「硬切」（会话 57 真机实测）。
     */
    private var assistantEntering by mutableStateOf(false)

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
    override fun showAssistant(route: String) {
        // 路由与序号必须在主线程一次性写入：Compose 状态、且要在首次 setContent 之前生效
        runOnUiThread {
            assistantRouteState = route
            assistantNavSeq++
            val view = assistantView ?: ComposeView(this).also { created ->
                created.setContent {
                    val dark = darkThemeState
                    AssistantApp(
                        darkTheme = dark,
                        initialRoute = assistantRouteState,
                        navSeq = assistantNavSeq,
                        entering = assistantEntering,
                        onExit = ::hideAssistant,
                        onOpenRpSidebar = ::openRpSidebar,
                    )
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
                assistantEntering = true
                // [用户 2026-09-10] 进/出助手原为硬切（visibility 直翻）体感生硬 → 加过渡。
                // 令牌取自 DESIGN.md Motion：进 200ms / 退 140ms / cubic-bezier(0.23,1,0.32,1)，
                // 自 scale(0.96)+alpha 0 起步（**禁 scale(0) 起步**）；系统关动画时直接呈现。
                //
                // [用户 2026-09-11「页面交接」统一编排] 覆盖层淡入期间**保留 WebView 绘制**，
                // 淡入结束才停绘：原实现紧接着置 INVISIBLE，等于把「旧页」瞬间抽走——覆盖层只能
                // 淡入到窗口底色上（不是交叉淡化），WebView 侧的侧栏左收动画也一并看不见。
                // 停绘策略本身不变，只是推迟到过渡收尾（200ms，可忽略）。
                animateAssistantOverlay(view, entering = true) {
                    assistantEntering = false
                    suspendWebViewForAssistant()
                }
                notifyAssistantVisibility(true)
            }
        }
    }

    override fun hideAssistant() = closeAssistant(handoffToSidebar = false)

    /**
     * 隐藏助手覆盖层。
     *
     * @param handoffToSidebar true = 这次关闭是「呼出 LuzzyRP 侧栏」的**页面交接**：覆盖层淡出改用
     *   200ms 交接令牌，与侧栏滑入同帧起跑、同时结束；false = 普通退出（140ms 退出令牌）。
     */
    private fun closeAssistant(handoffToSidebar: Boolean) {
        runOnUiThread {
            if (!assistantVisible) return@runOnUiThread
            assistantEntering = false
            val duration = if (handoffToSidebar) ENTER_DURATION_MS else EXIT_DURATION_MS
            // 退出过渡（令牌）；收尾再置 GONE，避免退场动画被立刻掐断
            assistantView?.let { view ->
                animateAssistantOverlay(view, entering = false, durationMs = duration) {
                    view.visibility = View.GONE
                }
            } ?: run { assistantView?.visibility = View.GONE }
            assistantVisible = false
            webView.resumeTimers()
            webView.onResume()
            webView.visibility = View.VISIBLE
            notifyAssistantVisibility(false)
        }
    }

    /**
     * 助手覆盖层完全盖住之后停绘 WebView。
     *
     * 助手覆盖层全屏显示时，WebView 若继续重绘（Vue 应用有动画/定时器），会与 Compose 争抢
     * 合成器 → 帧率明显下降。故停绘 + 暂停定时器，关闭时原样恢复（**不销毁、状态不丢**）。
     * **调用时机**：覆盖层淡入过渡结束（见 [showAssistant]）——过渡期间必须让旧页可见，
     * 交叉淡化才有「旧页」可淡。
     */
    private fun suspendWebViewForAssistant() {
        runOnUiThread {
            if (!assistantVisible) return@runOnUiThread
            webView.visibility = View.INVISIBLE
            webView.onPause()
            webView.pauseTimers()
        }
    }

    /**
     * 助手覆盖层进/出过渡（DESIGN.md Motion 令牌）。
     *
     * 进：200ms / 退：140ms / `cubic-bezier(0.23,1,0.32,1)`；自 `scale(0.96)+alpha 0` 起步
     * （令牌禁止 `scale(0)` 起步）。系统「移除动画」（ANIMATOR_DURATION_SCALE=0）时直接呈现。
     * 不改变既有的 WebView 停绘策略——过渡只负责视觉衔接。
     */
    private fun animateAssistantOverlay(
        view: View,
        entering: Boolean,
        durationMs: Long = if (entering) ENTER_DURATION_MS else EXIT_DURATION_MS,
        onEnd: (() -> Unit)? = null,
    ) {
        val reduced = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
        }.getOrDefault(false)
        if (reduced) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            onEnd?.invoke()
            return
        }
        if (entering) {
            // 进入＝纯交叉淡化（只动 alpha）：位移交给 WebView 侧的侧栏左收
            // （ext/luzzy-theme.css 的 .lsp-handoff），两边同时位移会在视觉上互相打架。
            // 旧页（WebView）在淡化期间保持绘制，见 showAssistant 的停绘时机。
            view.alpha = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
        view.animate()
            .alpha(if (entering) 1f else 0f)
            .scaleX(1f)
            .scaleY(if (entering) 1f else 0.96f)
            .setDuration(durationMs)
            .setInterpolator(PathInterpolator(0.23f, 1f, 0.32f, 1f))
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * 助手页左上角汉堡 → 隐藏助手 + 呼出 LuzzyRP 原侧栏（用户 2026-09-09 指定）。
     *
     * **一次页面交接**（DESIGN.md 页面交接令牌 200ms）：覆盖层淡出与侧栏滑入**同帧起跑、同时结束**。
     * 改前是「淡出 140ms → 盲等 250ms 才点汉堡 → 侧栏再滑入 200ms」，中间约 110ms 空档，
     * 观感是两段（会话 57 真机）。
     *
     * 前端调用改为**按返回值重试**（`Luzzy.openRpSidebar()` 返回 false = 汉堡没找到）：
     * 不再盲等固定时长，点空时按 100ms 退避补两次。
     */
    override fun openRpSidebar() {
        runOnUiThread {
            closeAssistant(handoffToSidebar = true)
            requestRpSidebar(attempt = 0)
        }
    }

    /** 调前端封装打开侧栏；返回 false（汉堡按钮没找到）时按 100ms 退避重试，最多 3 次。 */
    private fun requestRpSidebar(attempt: Int) {
        runCatching {
            webView.evaluateJavascript(JS_OPEN_RP_SIDEBAR) { value ->
                val opened = value?.trim()?.trim('"') == "true"
                if (!opened && attempt < 2) {
                    webView.postDelayed({ requestRpSidebar(attempt + 1) }, 100)
                }
            }
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

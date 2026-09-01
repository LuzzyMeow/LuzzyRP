package com.luzzymeow.luzzyrp.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.luzzymeow.luzzyrp.BuildConfig

/**
 * JSBridge 原生实现（AGENTS.md §5.4 新增桥接方法流程的落点）。
 *
 * 所有 `@JavascriptInterface` 方法集中于此；前端通过
 * `assets/ext/luzzy-bridge.js` 的封装调用（含存在性检测与降级）。
 *
 * [HARD-REQ-3] 扩展层隔离：本类属于原生层，与上游 RP-Hub 文件无关；
 * 方法名被前端 JS 直接引用，R8 规则已保留（proguard-rules.pro）。
 */
@Suppress("unused")
class LuzzyBridge(private val context: Context) {

    /** 剪贴板写入。返回是否成功（后端实现总是返回 true）。 */
    @JavascriptInterface
    fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("LuzzyRP", text))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 轻提示。 */
    @JavascriptInterface
    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 应用版本信息（versionName / versionCode / 上游基线版本）。 */
    @JavascriptInterface
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    @JavascriptInterface
    fun getAppVersionCode(): Int {
        return BuildConfig.VERSION_CODE
    }

    /** 上游基线版本（RP-Hub）。同步时更新（AGENTS.md §4）。 */
    @JavascriptInterface
    fun getUpstreamVersion(): String {
        return UPSTREAM_VERSION
    }

    /**
     * 系统栏样式联动（主题切换配套）。
     * mode: "light" / "dark" —— 控制状态栏/导航栏图标深浅与底色。
     * 亮色主题 → 深色图标 + 浅色系统栏；暗色主题 → 浅色图标 + 深色系统栏。
     */
    @JavascriptInterface
    fun setSystemBarStyle(mode: String) {
        val activity = context as? android.app.Activity ?: return
        activity.runOnUiThread {
            val isDark = mode == "dark"
            val window = activity.window
            val decorView = window.decorView
            // 状态栏区域在亮/暗两主题下都覆盖顶栏深色渐隐层 → 图标恒为浅色（白）
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            // 导航栏区域透出页面画布：亮主题（米纸浅底）→ 深图标；暗主题（暗纸深底）→ 浅图标
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = if (isDark) {
                    decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        }
    }

    /** SDK / 设备信息（供诊断用）。 */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    companion object {
        /** 上游基线版本；每次同步上游后更新。 */
        const val UPSTREAM_VERSION = "1.8.9"
    }
}

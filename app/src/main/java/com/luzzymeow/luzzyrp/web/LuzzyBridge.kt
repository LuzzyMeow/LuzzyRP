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
class LuzzyBridge(
    private val context: Context,
    /** 助手原生页宿主控制（可空：未接线时相关桥接方法降级为 no-op）。 */
    private val assistantController: com.luzzymeow.luzzyrp.assistant.AssistantController? = null,
) {

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

    /**
     * 用系统浏览器打开外部链接（关于页仓库入口等）。
     * WebView 内 window.open 无 onCreateWindow 支持时是 no-op，外链必须走 Intent。
     */
    @JavascriptInterface
    fun openUrl(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 无可处理该 scheme 的应用时静默失败（前端侧有降级提示路径）
        }
    }

    companion object {
        /** 上游基线版本；每次同步上游后更新（v1.5.0 同步至 1.9.3 / commit 4aef0bb）。 */
        const val UPSTREAM_VERSION = "1.9.3"
    }

    // ------------------------------------------------------------------
    // [LuzzyRP v1.5.0 助手] 桥接契约（PLAN §14）
    // 前端封装见 assets/ext/luzzy-bridge.js（存在性检测 + 降级），
    // 调用点见 assets/ext/luzzy-assistant.js。新增方法按 AGENTS §5.4 流程登记 CHANGELOG。
    // ------------------------------------------------------------------

    /** 侧栏「助手」入口：显示原生助手覆盖层首页。未接线时静默（前端有降级提示）。 */
    @JavascriptInterface
    fun openAssistant() {
        assistantController?.showAssistant("")
    }

    /**
     * 侧栏「助手」子项入口：按指定页面打开助手（PLAN §3.3）。
     *
     * @param route `conversations` / `memory` / `skills` / `mcp` / `workspace` / `terminal` /
     *   `settings`；未知值回退首页。
     */
    @JavascriptInterface
    fun openAssistantAt(route: String) {
        assistantController?.showAssistant(route)
    }

    /**
     * 助手页左上角汉堡 → 回到 **LuzzyRP 原侧栏**（用户 2026-09-09 指定）。
     * 原生侧：隐藏助手覆盖层后调用 `window.Luzzy.openRpSidebar()`。
     */
    @JavascriptInterface
    fun openRpSidebar() {
        assistantController?.openRpSidebar()
    }

    /** 助手覆盖层当前是否可见（前端可据此暂停/恢复轮询等）。 */
    @JavascriptInterface
    fun isAssistantVisible(): Boolean = assistantController?.isAssistantVisible() ?: false

    /**
     * Web 端供应商配置**只读**推送（PLAN §14）：由 `luzzy-assistant.js` 在页面就绪与
     * 设置变更时调用。内容仅驻留内存（[com.luzzymeow.luzzyrp.assistant.AssistantConfigHolder]），
     * **不落盘、不进日志**。
     */
    @JavascriptInterface
    fun setAssistantConfig(json: String) {
        com.luzzymeow.luzzyrp.assistant.AssistantConfigHolder.update(json)
    }

    /** 读取最近一次推送的配置（未推送时返回空串）。 */
    @JavascriptInterface
    fun getAssistantConfig(): String = com.luzzymeow.luzzyrp.assistant.AssistantConfigHolder.get()

    /**
     * 主题模式联动：`"light"` / `"dark"`。助手覆盖层与 Web 端主题保持一致
     * （DESIGN.md：恒定「暖幕手记」，亮/暗双模式）。
     */
    @JavascriptInterface
    fun setAssistantThemeMode(mode: String) {
        assistantController?.setThemeMode(mode == "dark")
    }
}

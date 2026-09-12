package com.luzzymeow.luzzyrp.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.luzzymeow.luzzyrp.BuildConfig
import com.luzzymeow.luzzyrp.chat.ChatEventSink
import com.luzzymeow.luzzyrp.chat.ChatJobs
import com.luzzymeow.luzzyrp.chat.ChatJsCall
import com.luzzymeow.luzzyrp.data.legacy.MigrationInbox

/**
 * JSBridge 原生实现（AGENTS.md §5.4 新增桥接方法流程的落点）。
 *
 * 所有 `@JavascriptInterface` 方法集中于此；前端通过
 * `assets/ext/luzzy-bridge.js`（以及 v2.0 的 `luzzy-chat-native.js`）的封装调用
 * （含存在性检测与降级）。
 *
 * [HARD-REQ-3] 扩展层隔离：本类属于原生层，与上游 RP-Hub 文件无关；
 * 方法名被前端 JS 直接引用，R8 规则已保留（proguard-rules.pro）。
 */
@Suppress("unused")
class LuzzyBridge(private val context: Context) {

    /**
     * 原生 → JS 事件出口（v2.0）。
     *
     * 由 [com.luzzymeow.luzzyrp.MainActivity] 在创建 WebView 后设置，契约：
     * 收到的字符串是**完整的 JS 表达式**，宿主须在 **UI 线程** 调
     * `webView.evaluateJavascript(js, null)`；WebView 销毁前须置回 null。
     */
    @Volatile
    var eventSink: ((String) -> Unit)? = null

    /** 事件出口的稳定适配器（读 [eventSink] 的最新值，避免每任务重新分配）。 */
    private val chatSink = ChatEventSink { jobId, eventJson ->
        eventSink?.invoke(ChatJsCall.onEvent(jobId, eventJson))
    }

    /** 原生聊天传输任务管理器（懒建：不用原生传输时不占资源）。 */
    private val chatJobs: ChatJobs by lazy {
        ChatJobs(
            sink = { chatSink },
            log = { message -> android.util.Log.d(TAG, message) },
        )
    }

    // ---------- v2.0 原生聊天传输桥接契约（JS 侧：assets/ext/luzzy-chat-native.js） ----------

    /** 启动一次原生聊天传输；返回 jobId（成功）或空串（原生不可用/参数非法）。必须立即返回，不得阻塞。 */
    @JavascriptInterface
    fun chatStart(planJson: String): String = try {
        chatJobs.start(planJson)
    } catch (e: Throwable) {
        android.util.Log.w(TAG, "chatStart 失败：${e.javaClass.simpleName}")
        ""
    }

    /** 中止指定 job；返回是否真的中止了。 */
    @JavascriptInterface
    fun chatAbort(jobId: String): Boolean = try {
        chatJobs.abort(jobId)
    } catch (e: Throwable) {
        false
    }

    /** 原生传输能力探测；返回 JSON 字符串 {"available":true,"protocols":[...]} 或 {"available":false,"reason":"..."}。 */
    @JavascriptInterface
    fun chatCapabilities(): String = try {
        ChatJobs.capabilitiesJson(available = true)
    } catch (e: Throwable) {
        ChatJobs.capabilitiesJson(available = false, reason = e.javaClass.simpleName)
    }

    /** WebView 销毁时收尾：停止全部在跑的传输任务，并断开事件出口。 */
    fun shutdownChatJobs() {
        eventSink = null
        chatJobs.shutdown()
    }

    /** 剪贴板写入。返回是否成功（后端实现总是返回 true）。 */
    @JavascriptInterface
    fun copyToClipboard(text: String): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LuzzyRP", text))
        true
    } catch (e: Exception) {
        false
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

    // ---------- v3.0 数据迁移通道（JS 侧：assets/ext/luzzy-migrate.html + luzzy-bridge.js 封装） ----------
    //
    // 旧数据活在 WebView 的 IndexedDB 里（LevelDB 形态），Kotlin 直接解析不现实 → 迁移必须由
    // 跑在 WebView 里的 JS 把数据「读出来」。通道形状：开始 → 若干块 → 结束。
    // 分块是**硬要求**：真实用户的全量导出有几 MB，一次传会撞 Binder 事务上限（1 MB），
    // 而这类失败在数据量小时看不出来（详见 docs/DESIGN-migration.md §6）。
    //
    // 本组方法只往 `filesDir/migration/incoming/` 写文件，**不碰旧库**（迁移不破坏源，G5）。

    private val migrationInbox: MigrationInbox by lazy {
        MigrationInbox(java.io.File(context.filesDir, "migration"))
    }

    /** 开始一次导出，返回会话 id（页面后续回传）。 */
    @JavascriptInterface
    fun migrateStart(sessionId: String): String = try {
        migrationInbox.start(sessionId)
    } catch (e: Throwable) {
        android.util.Log.w(TAG, "migrateStart 失败：${e.javaClass.simpleName}")
        ""
    }

    /**
     * 追加一块。返回 false = 被拒（顺序错乱/无会话），页面必须停下并调 [migrateError]。
     * 顺序校验存在的理由：缺块拼出来的 JSON 可能**恰好能被解析**，然后悄悄少掉一部分数据。
     */
    @JavascriptInterface
    fun migrateChunk(seq: Int, payload: String): Boolean = try {
        migrationInbox.appendChunk(seq, payload)
    } catch (e: Throwable) {
        false
    }

    /** 收尾。返回 JSON 报告（块数/字符数/sha256），失败返回空串。 */
    @JavascriptInterface
    fun migrateDone(summaryJson: String): String = try {
        val summary = parseMigrationSummary(summaryJson)
        val result = migrationInbox.finish(summary)
        if (result == null) {
            ""
        } else {
            """{"chunks":${result.chunkCount},"chars":${result.charCount},"sha256":"${result.sha256}",""" +
                """"path":"${result.file.name}"}"""
        }
    } catch (e: Throwable) {
        android.util.Log.w(TAG, "migrateDone 失败：${e.javaClass.simpleName}")
        ""
    }

    /** 页面侧异常出口（不吞错）：清掉半成品并记录。 */
    @JavascriptInterface
    fun migrateError(message: String) {
        android.util.Log.w(TAG, "迁移导出失败：$message")
        try {
            migrationInbox.abandon()
        } catch (_: Throwable) {
            // 清理失败不影响「已经失败」这个结论
        }
    }

    /** 迁移收件箱的上次结果（原生侧读报告用）。 */
    fun migrationResult(): MigrationInbox.Result? = migrationInbox.last

    private fun parseMigrationSummary(json: String): MigrationInbox.Summary? = try {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json)
            as? kotlinx.serialization.json.JsonObject ?: return null
        fun intOf(key: String): Int? =
            (obj[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.let { runCatching { it.content.toInt() }.getOrNull() }
        MigrationInbox.Summary(
            mainKeys = intOf("mainKeys") ?: -1,
            legacyKeys = intOf("legacyKeys") ?: -1,
            mainVersion = intOf("mainVersion"),
            legacyVersion = intOf("legacyVersion"),
        )
    } catch (e: Throwable) {
        null
    }

    companion object {
        private const val TAG = "LuzzyBridge"

        /** 上游基线版本；每次同步上游后更新（v1.5.0 同步至 1.9.3 / commit 4aef0bb）。 */
        const val UPSTREAM_VERSION = "1.9.3"
    }

    // [v1.5.0 移除] 原「助手」桥接契约（openAssistant / openAssistantAt / openRpSidebar /
    // isAssistantVisible / set-getAssistantConfig / setAssistantThemeMode）已随助手功能
    // 按用户指示于 2026-09-11 一并移除。
}

package com.luzzymeow.luzzyrp.data.legacy

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.store.MigrationWriter
import com.luzzymeow.luzzyrp.web.LuzzyBridge
import com.luzzymeow.luzzyrp.web.WebViewSetup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 迁移入口：**在需要时自动把旧 WebView 数据搬进新数据层**。
 *
 * 这是 P4 的收口动作——没有它，迁移器写得再对也不会被执行，老用户升级后依然看到空应用。
 *
 * ## 流程
 *
 * ```
 * alreadyMigrated? ──是──> 跳过
 *        │否
 *        ▼
 * 隐藏 WebView 打开 files/ext/luzzy-migrate.html（复用 WebViewSetup.configure！）
 *        │  页面读两库 → 分块经桥回传 → 原生落盘
 *        ▼
 * 读回落盘文件 → LegacyDb.parse → LegacyMigrator.migrate → MigrationWriter.import
 *        │
 *        ▼
 * 写迁移完成标记 + 报告（kv）
 * ```
 *
 * ## 三条硬约束（都有具体理由，不是洁癖）
 *
 * 1. **必须复用 [WebViewSetup.configure]**：能读到旧库靠的是
 *    `setAllowFileAccessFromFileURLs` + `setAllowUniversalAccessFromFileURLs`
 *    （见 `docs/DESIGN-migration.md` §2.2）。换了配置就会**静默读不到数据**，
 *    所以导出失败时**不写完成标记**，下次启动重来。
 * 2. **先把数据落库、成功了才写标记**（G4）：中断可续的前提是「标记只代表真的完成」。
 * 3. **只在没有标记时跑**：迁移是幂等的，但没必要每次启动都开一个 WebView。
 *
 * ## 为什么不用真实 Activity
 *
 * 迁移是一次性的后台动作，用户不需要看着它（有报告可查）。用一个不挂到视图树的 WebView
 * 就够了，代价是必须自己管生命周期（`destroy()` 要在主线程）。
 */
class MigrationRunner(
    private val context: Context,
    private val store: LuzzyStore,
    private val webViewTimeoutMs: Long = 120_000,
) {

    sealed interface Outcome {
        /** 已迁移过（或没有旧数据），本次什么都没做。 */
        data object Skipped : Outcome

        data class Migrated(
            val outcome: MigrationWriter.Outcome,
            val report: String,
        ) : Outcome

        data class Failed(val reason: String) : Outcome
    }

    private val filesDir: File get() = context.filesDir

    /** 导出页地址：**与 ext/ 同目录约定一致**（[MigrationInbox] 只负责收，不负责找页）。 */
    private fun exporterUrl(): String =
        "file://" + File(File(filesDir, "ext"), EXPORTER_PAGE).absolutePath

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun runIfNeeded(): Outcome {
        val writer = MigrationWriter(store, filesDir)
        if (writer.alreadyMigrated()) return Outcome.Skipped
        if (store.characterCount() > 0) {
            // 新库里已经有数据（用户已经用过新版）→ 不能拿旧数据去覆盖它。
            // 记下标记，避免每次启动都重试。
            writer.markMigrated(MigrationWriter.Outcome(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), System.currentTimeMillis())
            Log.i(TAG, "新库已有数据，跳过旧数据迁移")
            return Outcome.Skipped
        }
        val page = File(File(filesDir, "ext"), EXPORTER_PAGE)
        if (!page.isFile) return Outcome.Failed("导出页缺失：${page.absolutePath}")

        val exported = exportViaHiddenWebView() ?: return Outcome.Failed("旧数据导出失败（未取到导出文件）")
        val text = exported.readText(Charsets.UTF_8)

        val migrated = runCatching { LegacyMigrator.migrate(LegacyDb.parse(text)) }
            .getOrElse { return Outcome.Failed("迁移器解析失败：${it.javaClass.simpleName} ${it.message}") }

        val outcome = runCatching { writer.import(migrated) }
            .getOrElse { return Outcome.Failed("写入新库失败：${it.javaClass.simpleName} ${it.message}") }

        writer.markMigrated(outcome, System.currentTimeMillis())
        val report = buildReport(outcome)
        store.putString(KEY_MIGRATION_REPORT, report)
        Log.i(TAG, "迁移完成：$report")
        return Outcome.Migrated(outcome, report)
    }

    /** 跑一次导出页并等它收尾；成功返回落盘好的导出文件。 */
    private suspend fun exportViaHiddenWebView(): File? = withContext(Dispatchers.Main) {
        val bridge = LuzzyBridge(context)
        val done = CompletableDeferred<Boolean>()
        bridge.migrationDoneSink = { ok, detail ->
            Log.i(TAG, "导出页回调 ok=$ok $detail")
            done.complete(ok)
        }
        val webView = WebView(context)
        // 与主壳同一套配置 —— 见类注释第 1 条
        WebViewSetup.configure(webView)
        webView.addJavascriptInterface(bridge, "LuzzyBridge")
        try {
            webView.loadUrl(exporterUrl())
            withTimeoutOrNull(webViewTimeoutMs) { done.await() }?.let { if (it) return@withContext exportFile() }
            return@withContext null
        } finally {
            bridge.migrationDoneSink = null
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun exportFile(): File? =
        File(File(File(filesDir, "migration"), MigrationInbox.DIR_INCOMING), MigrationInbox.FILE_EXPORT)
            .takeIf { it.isFile && it.length() > 0L }

    /**
     * 报告文案：给用户看的一行摘要。
     *
     * 刻意**只讲事实**（搬了多少条），不讲过程；跳过与失败的明细留在日志与 kv 里，
     * 等有了设计过的报告界面再展开（见 `docs/DESIGN-migration.md` §11）。
     */
    private fun buildReport(outcome: MigrationWriter.Outcome): String = buildString {
        append("已从旧版迁移：")
        append("角色 ${outcome.characters} · 会话 ${outcome.messages} 条 · 记忆 ${outcome.vectorMemories + outcome.classicMemories}")
        if (outcome.worldEntries > 0) append(" · 世界书 ${outcome.worldEntries}")
        if (outcome.presets > 0) append(" · 预设 ${outcome.presets}")
        if (outcome.skipped > 0) append(" · 跳过 ${outcome.skipped}")
    }

    companion object {
        private const val TAG = "LuzzyMigrate"

        /** 导出页文件名（assets 解压后在 `filesDir/ext/`）。 */
        const val EXPORTER_PAGE = "luzzy-migrate.html"

        /** kv 键：给界面读的一行迁移报告。 */
        const val KEY_MIGRATION_REPORT = "legacy.migrationReport"
    }
}

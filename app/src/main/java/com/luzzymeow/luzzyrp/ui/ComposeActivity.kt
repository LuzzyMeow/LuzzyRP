package com.luzzymeow.luzzyrp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.legacy.MigrationCoordinator
import com.luzzymeow.luzzyrp.data.settings.SettingsBootstrap
import com.luzzymeow.luzzyrp.data.settings.SettingsStore
import com.luzzymeow.luzzyrp.data.settings.ThemeMode
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.transfer.TransferStore
import com.luzzymeow.luzzyrp.ui.nav.LuzzyNavShell
import com.luzzymeow.luzzyrp.ui.nav.LuzzyRoute
import com.luzzymeow.luzzyrp.ui.pages.AboutPage
import com.luzzymeow.luzzyrp.ui.pages.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.SessionsPage
import com.luzzymeow.luzzyrp.ui.pages.SettingsPage
import com.luzzymeow.luzzyrp.ui.pages.UsagePage
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.chat.MeshGradientBackground
import com.luzzymeow.luzzyrp.ui.pages.preset.PresetsPage
import com.luzzymeow.luzzyrp.ui.pages.world.WorldInfoPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.launch

/**
 * v3.0 Compose 界面宿主（P1 静态稿验证）。
 *
 * launcher 仍为 [com.luzzymeow.luzzyrp.MainActivity]（WebView 壳，v2.x 体验零影响）；
 * 本 Activity 经 `adb shell am start` 显式启动做开发验证，P6 切换时才接任 launcher。
 *
 * 路由：LuzzyNavShell（抽屉壳 + AnimatedContent 页面转场，DESIGN-compose §13.2）；
 * 聊天页沉浸形态（§12），其余页静态稿（§13.1）。
 */
class ComposeActivity : ComponentActivity() {

    /**
     * 亮暗模式（**持久化**）。
     *
     * [ThemeMode.System] = 跟随系统（首次安装的默认）。用户手动切过就是显式 Light/Dark，
     * 重启后仍在 —— 这就是 DESIGN-compose 里「持久化在 P4 接」的那一项。
     */
    private var themeMode by mutableStateOf(ThemeMode.System)
    /** 用户字号缩放（D1；null 存储语义 → 运行态用 1f）。 */
    private var fontScale by mutableStateOf(1f)
    private var route by mutableStateOf<LuzzyRoute>(LuzzyRoute.Chat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LuzzyFonts.appContext = applicationContext
        // 旧数据迁移：进界面**之前**发起（协调器保证只跑一次），界面会等它出结论再读库
        // （见 MigrationCoordinator 的类注释：否则会先按空库渲染成演示数据）。
        // 旧设置搬运作为「启动准备」挂在迁移之后、状态离开 Running 之前 ——
        // 否则聊天页会用「未配置」渲染首帧（真机实测）。
        lifecycleScope.launch {
            MigrationCoordinator.ensureMigrated(applicationContext) {
                SettingsBootstrap.importOnce(
                    applicationContext,
                    LuzzyStore(DatabaseProvider.luzzy(applicationContext)),
                )
            }
            // 搬运完再读主题/字号：否则会把「刚搬来的旧值」覆盖回默认
            SettingsStore(applicationContext).load().let {
                themeMode = it.themeMode ?: ThemeMode.System
                fontScale = it.fontScale ?: 1f
            }
        }
        setContent {
            val currentDark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // 手动切换落盘成显式值（不再是「跟随系统」）
            val toggleDark: () -> Unit = {
                val next = if (currentDark) ThemeMode.Light else ThemeMode.Dark
                themeMode = next
                SettingsStore(applicationContext).save(
                    SettingsStore(applicationContext).load().copy(themeMode = next),
                )
            }
            // ── D2 导入导出：SAF 接线（文本格式在 TransferFormat/TransferStore；运行时验证留真机）──
            val scope = rememberCoroutineScope()
            val transfer = remember { TransferStore(LuzzyStore(DatabaseProvider.luzzy(applicationContext))) }
            val notify: (String) -> Unit = { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
            var pendingExport by remember { mutableStateOf<(suspend () -> String)?>(null) }
            var pendingImport by remember { mutableStateOf<(suspend (String) -> String)?>(null) }
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                val producer = pendingExport
                if (uri == null || producer == null) return@rememberLauncherForActivityResult
                scope.launch {
                    runCatching {
                        val text = producer()
                        val stream = applicationContext.contentResolver.openOutputStream(uri)
                            ?: error("无法打开写入流")
                        stream.bufferedWriter().use { it.write(text) }
                    }.onSuccess { notify("导出完成") }
                        .onFailure { notify("导出失败：${it.message}") }
                }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val consumer = pendingImport
                if (uri == null || consumer == null) return@rememberLauncherForActivityResult
                scope.launch {
                    runCatching {
                        val stream = applicationContext.contentResolver.openInputStream(uri)
                            ?: error("无法打开读取流")
                        val text = stream.bufferedReader().use { it.readText() }
                        consumer(text)
                    }.onSuccess { notify(it) }
                        .onFailure { notify("导入失败：${it.message}") }
                }
            }
            val transferActions = com.luzzymeow.luzzyrp.ui.pages.TransferActions(                exportPresets = { pendingExport = { transfer.exportPresets() }; exportLauncher.launch("presets.json") },
                exportWorldInfo = { pendingExport = { transfer.exportWorldInfo() }; exportLauncher.launch("world_info.json") },
                exportCharacters = { pendingExport = { transfer.exportCharacters() }; exportLauncher.launch("characters.json") },
                importPresets = {
                    pendingImport = { text -> "导入预设 ${transfer.importPresets(text)} 条" }
                    importLauncher.launch(arrayOf("application/json"))
                },
                importWorldInfo = {
                    pendingImport = { text -> "导入世界书 ${transfer.importWorldInfo(text)} 条" }
                    importLauncher.launch(arrayOf("application/json"))
                },
                importCharacters = {
                    pendingImport = { text -> transfer.importCharacters(text).let { (created, replaced) -> "导入角色卡：新增 $created · 覆盖 $replaced" } }
                    importLauncher.launch(arrayOf("application/json"))
                },
            )

            // 字号实时预览（state 立即生效于两条 token 体系），松手才落盘
            val pageData = remember { PageDataSource(LuzzyStore(DatabaseProvider.luzzy(applicationContext))) }
            val changeFontScale: (Float) -> Unit = { fontScale = it }
            val commitFontScale: () -> Unit = {
                SettingsStore(applicationContext).save(
                    SettingsStore(applicationContext).load().copy(fontScale = fontScale),
                )
            }
            LuzzyTheme(darkTheme = currentDark, fontScale = fontScale) {
                LuzzyNavShell(
                    route = route,
                    onNavigate = { route = it },
                    darkMode = currentDark,
                    onToggleDarkMode = toggleDark,
                ) { r, onOpenDrawer ->
                    when (r) {
                        LuzzyRoute.Chat -> ChatPage(
                            darkMode = currentDark,
                            onToggleDarkMode = toggleDark,
                            onOpenDrawer = onOpenDrawer,
                            onOpenSessions = { route = LuzzyRoute.Sessions },
                            onOpenWorldInfo = { route = LuzzyRoute.WorldInfo },
                            onOpenPresets = { route = LuzzyRoute.Presets },
                        )
                        LuzzyRoute.Sessions -> SessionsPage(
                            onOpenDrawer = onOpenDrawer,
                            // 选中即切到该角色该分支：写进 kv 再回聊天页——聊天页启动时按 kv 装载，
                            // 于是「点一行 = 回到那一段」不需要跨页传状态
                            onOpenSession = { _, _ -> route = LuzzyRoute.Chat },
                        )
                        LuzzyRoute.Characters -> CharactersPage(onOpenDrawer)
                        LuzzyRoute.WorldInfo -> WorldInfoPage(onOpenDrawer)
                        LuzzyRoute.Presets -> PresetsPage(onOpenDrawer)
                        LuzzyRoute.Memory -> MemoryPage(onOpenDrawer)
                        LuzzyRoute.Usage -> UsagePage(onOpenDrawer)
                        LuzzyRoute.Settings -> SettingsPage(
                            onOpenDrawer,
                            fontScale = fontScale,
                            onFontScaleChange = changeFontScale,
                            onFontScaleFinished = commitFontScale,
                            transfer = transferActions,
                            migrationReportProvider = { pageData.migrationReport() },
                        )
                        LuzzyRoute.About -> AboutPage(onOpenDrawer)
                    }
                }
            }
        }
    }
}
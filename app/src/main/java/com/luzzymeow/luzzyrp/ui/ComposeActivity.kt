package com.luzzymeow.luzzyrp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.luzzymeow.luzzyrp.data.legacy.MigrationCoordinator
import com.luzzymeow.luzzyrp.data.settings.SettingsBootstrap
import com.luzzymeow.luzzyrp.data.settings.SettingsStore
import com.luzzymeow.luzzyrp.data.settings.ThemeMode
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.nav.LuzzyNavShell
import com.luzzymeow.luzzyrp.ui.nav.LuzzyRoute
import com.luzzymeow.luzzyrp.ui.pages.AboutPage
import com.luzzymeow.luzzyrp.ui.pages.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.PresetsPage
import com.luzzymeow.luzzyrp.ui.pages.SessionsPage
import com.luzzymeow.luzzyrp.ui.pages.SettingsPage
import com.luzzymeow.luzzyrp.ui.pages.UsagePage
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.chat.MeshGradientBackground
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
    private var route by mutableStateOf<LuzzyRoute>(LuzzyRoute.Chat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LuzzyFonts.appContext = applicationContext
        // 旧数据迁移：进界面**之前**发起（协调器保证只跑一次），界面会等它出结论再读库
        // （见 MigrationCoordinator 的类注释：否则会先按空库渲染成演示数据）。
        lifecycleScope.launch {
            MigrationCoordinator.ensureMigrated(applicationContext)
            // 旧设置一次性搬运（在迁移之后：旧设置就存在迁移进来的 kv 里）
            SettingsBootstrap.importOnce(applicationContext, LuzzyStore(DatabaseProvider.luzzy(applicationContext)))
            // 搬运完再读主题：否则会把「刚搬来的旧主题」覆盖回系统默认
            themeMode = SettingsStore(applicationContext).load().themeMode ?: ThemeMode.System
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
            LuzzyTheme(darkTheme = currentDark) {
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
                        LuzzyRoute.Settings -> SettingsPage(onOpenDrawer)
                        LuzzyRoute.About -> AboutPage(onOpenDrawer)
                    }
                }
            }
        }
    }
}
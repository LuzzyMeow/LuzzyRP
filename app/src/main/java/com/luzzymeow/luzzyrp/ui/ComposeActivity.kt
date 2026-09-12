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
import com.luzzymeow.luzzyrp.ui.nav.LuzzyNavShell
import com.luzzymeow.luzzyrp.ui.nav.LuzzyRoute
import com.luzzymeow.luzzyrp.ui.pages.AboutPage
import com.luzzymeow.luzzyrp.ui.pages.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.PresetsPage
import com.luzzymeow.luzzyrp.ui.pages.SettingsPage
import com.luzzymeow.luzzyrp.ui.pages.UsagePage
import com.luzzymeow.luzzyrp.ui.pages.WorldInfoPage
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.chat.MeshGradientBackground
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

    private var darkMode by mutableStateOf<Boolean?>(null)
    private var route by mutableStateOf<LuzzyRoute>(LuzzyRoute.Chat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LuzzyFonts.appContext = applicationContext
        // 旧数据迁移：进界面**之前**发起（协调器保证只跑一次），界面会等它出结论再读库
        // （见 MigrationCoordinator 的类注释：否则会先按空库渲染成演示数据）。
        lifecycleScope.launch { MigrationCoordinator.ensureMigrated(applicationContext) }
        setContent {
            val currentDark = darkMode ?: isSystemInDarkTheme()
            val toggleDark: () -> Unit = { darkMode = !currentDark }
            LuzzyTheme(darkTheme = darkMode) {
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
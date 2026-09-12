package com.luzzymeow.luzzyrp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.chat.MeshGradientBackground
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme

/**
 * v3.0 Compose 界面宿主（P1 空壳验证）。
 *
 * launcher 仍为 [com.luzzymeow.luzzyrp.MainActivity]（WebView 壳，v2.x 体验零影响）；
 * 本 Activity 仅经 `adb shell am start -n com.luzzymeow.luzzyrp(.debug)/.ui.ComposeActivity`
 * 显式启动做 P1-P5 开发验证，P6 切换时才接任 launcher 并移除 WebView 路径
 * （届时回归「单 Activity」终态）。
 *
 * 亮暗模式：P1 为内存态 + 手动切换（验证「主题切换正常」）；持久化随 P4 数据层接 DataStore。
 */
class ComposeActivity : ComponentActivity() {

    private var darkMode by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LuzzyFonts.appContext = applicationContext
        setContent {
            LuzzyTheme(darkTheme = darkMode) {
                MeshGradientBackground(Modifier.fillMaxSize()) {
                    val currentDark = darkMode ?: isSystemInDarkTheme()
                    ChatPage(
                        darkMode = currentDark,
                        onToggleDarkMode = { darkMode = !currentDark },
                    )
                }
            }
        }
    }
}
package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography

/** 动效令牌（DESIGN-compose §7；open-design 动效哲学，硬约束）。 */
object Motion {
    /** 进入 200ms（open-design 令牌）。 */
    const val EnterMs = 200

    /** 退出 140ms（退出读作「果断」，因用户已选择关闭）。 */
    const val ExitMs = 140

    /** ease-out 贝塞尔（UI 过渡禁 ease-in）。 */
    val Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

/**
 * v3.0 Compose 主题入口。所有 Compose UI 必须包在本主题内。
 *
 * @param darkTheme 亮暗（null = 跟随系统）；由宿主持有状态以支持手动切换
 *   （P1 验证项「主题切换正常」；持久化在 P4 接 DataStore）。
 */
@Composable
fun LuzzyTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = remember(dark) { luzzyColorScheme(dark = dark) }
    val extendColors = remember(dark) { extendFor(dark = dark) }
    CompositionLocalProvider(
        LocalDarkMode provides dark,
        LocalExtendColors provides extendColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LuzzyTypography,
            content = content,
        )
    }
}

/** 便捷访问：`MaterialTheme.colorScheme` 之外的品牌扩展。 */
object LuzzyThemeColors {
    val extend: ExtendColors
        @Composable get() = LocalExtendColors.current

    val isDark: Boolean
        @Composable get() = LocalDarkMode.current
}

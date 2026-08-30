package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * [INVARIANT-THEME] LuzzyRP 全局主题（Aurora Dual）
 *
 * - 颜色唯一来源为 [AuroraColor]；本文件只做「令牌 → Material3 方案」的装配。
 * - 使用 MaterialExpressiveTheme + MotionScheme.expressive()（规定 6 的动画基线）。
 * - 扩展语义色经 [LocalExtendColors] 注入，UI 层禁止绕过主题直接写色值。
 */

private fun luzzyLightScheme(): ColorScheme = lightColorScheme(
    primary = AuroraColor.PrimaryLight,
    onPrimary = AuroraColor.OnPrimaryLight,
    primaryContainer = AuroraColor.PrimaryContainerLight,
    onPrimaryContainer = AuroraColor.OnPrimaryContainerLight,
    secondary = AuroraColor.SecondaryLight,
    onSecondary = AuroraColor.OnSecondaryLight,
    secondaryContainer = AuroraColor.SecondaryContainerLight,
    onSecondaryContainer = AuroraColor.OnSecondaryContainerLight,
    tertiary = AuroraColor.TertiaryLight,
    surface = AuroraColor.SurfaceLight,
    onSurface = AuroraColor.OnSurfaceLight,
    surfaceVariant = AuroraColor.SurfaceVariantLight,
    onSurfaceVariant = AuroraColor.OnSurfaceVariantLight,
    outline = AuroraColor.OutlineLight,
    error = AuroraColor.ErrorLight,
    background = AuroraColor.CanvasLight,
    onBackground = AuroraColor.OnSurfaceLight,
)

private fun luzzyDarkScheme(amoled: Boolean = false): ColorScheme = darkColorScheme(
    primary = AuroraColor.PrimaryDark,
    onPrimary = AuroraColor.OnPrimaryDark,
    primaryContainer = AuroraColor.PrimaryContainerDark,
    onPrimaryContainer = AuroraColor.OnPrimaryContainerDark,
    secondary = AuroraColor.SecondaryDark,
    onSecondary = AuroraColor.OnSecondaryDark,
    secondaryContainer = AuroraColor.SecondaryContainerDark,
    onSecondaryContainer = AuroraColor.OnSecondaryContainerDark,
    tertiary = AuroraColor.TertiaryDark,
    surface = if (amoled) AuroraColor.SurfaceAmoled else AuroraColor.SurfaceDark,
    onSurface = AuroraColor.OnSurfaceDark,
    surfaceVariant = AuroraColor.SurfaceVariantDark,
    onSurfaceVariant = AuroraColor.OnSurfaceVariantDark,
    outline = AuroraColor.OutlineDark,
    error = AuroraColor.ErrorDark,
    background = if (amoled) AuroraColor.CanvasAmoled else AuroraColor.CanvasDark,
    onBackground = AuroraColor.OnSurfaceDark,
)

/**
 * 扩展语义色：Material3 色彩体系之外、LuzzyRP 特有的语义色。
 * 通过 CompositionLocal 注入（见 [LocalExtendColors]），UI 层统一取值。
 */
@Immutable
data class ExtendColors(
    // 用户气泡底色（暖粉调）
    val userBubble: Color,
    // AI 气泡底色（纸面调）
    val aiBubble: Color,
    // 思考卡片底色
    val thinkingCard: Color,
    // 思考卡片进行中脉冲色
    val thinkingPulse: Color,
    // 工具卡片底色
    val toolCard: Color,
    // 引用高亮底色（会话中 "…" 引用文本）
    val quoteHighlight: Color,
    // 代码块底色
    val codeBlock: Color,
    // 成功 / 警示
    val success: Color,
    val warning: Color,
)

private val extendLight = ExtendColors(
    userBubble = Color(0xFFFFF0F8),
    aiBubble = Color(0xFFFFFFFF),
    thinkingCard = Color(0xFFF6EFFF),
    thinkingPulse = AuroraColor.AuroraViolet,
    toolCard = Color(0xFFEFF5FF),
    quoteHighlight = Color(0xFFFFF3C7),
    codeBlock = Color(0xFF1E1B18),
    success = Color(0xFF2E9E63),
    warning = Color(0xFFC77700),
)

private val extendDark = ExtendColors(
    userBubble = Color(0xFF3D2033),
    aiBubble = Color(0xFF1C1F26),
    thinkingCard = Color(0xFF261B38),
    thinkingPulse = AuroraColor.AuroraViolet,
    toolCard = Color(0xFF18253B),
    quoteHighlight = Color(0xFF3A3214),
    codeBlock = Color(0xFF101214),
    success = Color(0xFF7BD8A5),
    warning = Color(0xFFFFC46B),
)

/** 扩展语义色的 CompositionLocal（UI 层取语义色的唯一通道）。 */
val LocalExtendColors = staticCompositionLocalOf { extendLight }

/**
 * LuzzyRP 主题入口。
 *
 * @param mode 主题模式偏好（系统/亮/暗/AMOLED）
 */
@Composable
fun LuzzyTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val amoled = mode == ThemeMode.AMOLED
    val colorScheme = if (dark) luzzyDarkScheme(amoled) else luzzyLightScheme()

    CompositionLocalProvider(LocalExtendColors provides if (dark) extendDark else extendLight) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = luzzyTypography(),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

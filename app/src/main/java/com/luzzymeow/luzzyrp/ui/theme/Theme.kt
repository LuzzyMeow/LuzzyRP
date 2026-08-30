package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * [INVARIANT-THEME] LuzzyRP 全局主题 v2（Aurora Dual · DESIGN.md 为真源）
 *
 * v2 重制点：
 *   - 完整 Material3 方案：surfaceContainer 全族 / outline / outlineVariant /
 *     inverseSurface / scrim / surfaceDim / surfaceBright 全部落位；
 *   - Shapes 令牌（LuzzyCorner）接入主题；
 *   - 扩展语义色经 [LocalExtendColors] 注入；
 *   - AMOLED 独立方案（纯黑，非暗色微调）。
 */

private val LightScheme: ColorScheme = lightColorScheme(
    primary = AuroraColor.PrimaryLight,
    onPrimary = AuroraColor.OnPrimaryLight,
    primaryContainer = AuroraColor.PrimaryContainerLight,
    onPrimaryContainer = AuroraColor.OnPrimaryContainerLight,
    secondary = AuroraColor.SecondaryLight,
    onSecondary = AuroraColor.OnSecondaryLight,
    secondaryContainer = AuroraColor.SecondaryContainerLight,
    onSecondaryContainer = AuroraColor.OnSecondaryContainerLight,
    tertiary = AuroraColor.TertiaryLight,
    tertiaryContainer = Color(0xFFC4F1EC),
    onTertiaryContainer = Color(0xFF003733),
    surface = AuroraColor.SurfaceLight,
    onSurface = AuroraColor.OnSurfaceLight,
    surfaceVariant = AuroraColor.SurfaceVariantLight,
    onSurfaceVariant = AuroraColor.OnSurfaceVariantLight,
    // surfaceContainer 全族（Layer 色阶：Layer1→3 逐级抬升）
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFAF6),
    surfaceContainer = Color(0xFFF9F4F0),
    surfaceContainerHigh = Color(0xFFF3EEEA),
    surfaceContainerHighest = Color(0xFFEDE8E4),
    surfaceDim = Color(0xFFE2D9D4),
    surfaceBright = Color(0xFFFFF8F3),
    outline = AuroraColor.OutlineLight,
    outlineVariant = Color(0xFFE9DCD7),
    inverseSurface = Color(0xFF362F2C),
    inverseOnSurface = Color(0xFFFBEFEA),
    inversePrimary = AuroraColor.PrimaryDark,
    error = AuroraColor.ErrorLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
    background = AuroraColor.CanvasLight,
    onBackground = AuroraColor.OnSurfaceLight,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = AuroraColor.PrimaryDark,
    onPrimary = AuroraColor.OnPrimaryDark,
    primaryContainer = AuroraColor.PrimaryContainerDark,
    onPrimaryContainer = AuroraColor.OnPrimaryContainerDark,
    secondary = AuroraColor.SecondaryDark,
    onSecondary = AuroraColor.OnSecondaryDark,
    secondaryContainer = AuroraColor.SecondaryContainerDark,
    onSecondaryContainer = AuroraColor.OnSecondaryContainerDark,
    tertiary = AuroraColor.TertiaryDark,
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFF9CF1E7),
    surface = AuroraColor.SurfaceDark,
    onSurface = AuroraColor.OnSurfaceDark,
    surfaceVariant = AuroraColor.SurfaceVariantDark,
    onSurfaceVariant = AuroraColor.OnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF110D0A),
    surfaceContainerLow = Color(0xFF1B1613),
    surfaceContainer = Color(0xFF201A17),
    surfaceContainerHigh = Color(0xFF2B2421),
    surfaceContainerHighest = Color(0xFF362F2C),
    surfaceDim = Color(0xFF0E1116),
    surfaceBright = Color(0xFF3B3431),
    outline = AuroraColor.OutlineDark,
    outlineVariant = Color(0xFF3E3230),
    inverseSurface = Color(0xFFEBE0DB),
    inverseOnSurface = Color(0xFF362F2C),
    inversePrimary = AuroraColor.PrimaryLight,
    error = AuroraColor.ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    background = AuroraColor.CanvasDark,
    onBackground = AuroraColor.OnSurfaceDark,
)

private val AmoledScheme: ColorScheme = DarkScheme.copy(
    surface = AuroraColor.SurfaceAmoled,
    background = AuroraColor.CanvasAmoled,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    surfaceDim = AuroraColor.CanvasAmoled,
    surfaceBright = Color(0xFF1E1E1E),
)

/** v2 形状令牌（LuzzyCorner → Material Shapes）。 */
private val LuzzyShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(LuzzyCorner.ExtraSmall),
    small = RoundedCornerShape(LuzzyCorner.Small),
    medium = RoundedCornerShape(LuzzyCorner.Medium),
    large = RoundedCornerShape(LuzzyCorner.Large),
    extraLarge = RoundedCornerShape(LuzzyCorner.Large),
)

/** 扩展语义色：Material3 体系之外、LuzzyRP 特有的语义色。 */
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
    // 引用高亮底色
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
 * LuzzyRP 主题入口 v2。
 *
 * @param mode 主题模式偏好（系统/亮/暗/AMOLED）
 */
@Composable
fun LuzzyTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val amoled = mode == ThemeMode.AMOLED
    val colorScheme = when {
        amoled -> AmoledScheme
        dark -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(LocalExtendColors provides if (dark) extendDark else extendLight) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = luzzyTypography(),
            motionScheme = MotionScheme.expressive(),
            shapes = LuzzyShapes,
            content = content,
        )
    }
}

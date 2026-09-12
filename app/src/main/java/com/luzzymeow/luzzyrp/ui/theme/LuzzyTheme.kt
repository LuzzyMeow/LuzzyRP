package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dynamiccolor.ColorSpecs
import dynamiccolor.DynamicScheme
import hct.Hct
import palettes.TonalPalette

/**
 * v3.0 主题（设计真源 `docs/DESIGN-compose.md`；方向「A · 织机 Loom」，用户 2026-09-12 选定）。
 *
 * 机制参照 rikkahub `ui/theme/CustomTheme.kt`（AGPL-3.0，结构复用；本文件为独立实现，
 * 数值为 LuzzyRP 自有推导）：**HCT seed = 珊瑚陶土 #CC785C → TONAL_SPOT → 亮暗双 ColorScheme**。
 * 零照抄 rikkahub Claude 预设色值（DESIGN-compose Do's & Don'ts）。
 *
 * MCU 源码 vendor 于 `ui/theme/mcu/`（Apache-2.0，见该目录 NOTICE.md）。
 */
object LuzzySeed {
    /** 品牌种子：珊瑚陶土（现行 DESIGN.md primary-500）。 */
    const val CORAL = 0xFFCC785C.toInt()

    /** 荧光笔记号 amber（现行 DESIGN.md highlight #F5D9A8），ExtendColors/tertiary 参照。 */
    const val HIGHLIGHT = 0xFFF5D9A8.toInt()
}

/**
 * 由 seed 生成 M3 ColorScheme（亮/暗）。
 * spec 2021 + PHONE + contrast 0（与 rikkahub CustomTheme 默认一致）。
 */
fun luzzyColorScheme(dark: Boolean, seedArgb: Int = LuzzySeed.CORAL): ColorScheme {
    val sourceHct = Hct.fromInt(seedArgb)
    val spec = ColorSpecs.get(DynamicScheme.DEFAULT_SPEC_VERSION)
    val platform = DynamicScheme.DEFAULT_PLATFORM
    val contrastLevel = 0.0

    val scheme = DynamicScheme(
        sourceHct,
        dynamiccolor.Variant.TONAL_SPOT,
        dark,
        contrastLevel,
        platform,
        DynamicScheme.DEFAULT_SPEC_VERSION,
        spec.getPrimaryPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
        spec.getSecondaryPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
        spec.getTertiaryPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
        spec.getNeutralPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
        spec.getNeutralVariantPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
        spec.getErrorPalette(dynamiccolor.Variant.TONAL_SPOT, sourceHct, dark, platform, contrastLevel),
    )
    return scheme.toMaterial3()
}

/** DynamicScheme → M3 ColorScheme（逐 role 取色；MCU 2021 spec 与 M3 role 一一对应）。 */
private fun DynamicScheme.toMaterial3(): ColorScheme {
    fun p(t: TonalPalette, tone: Int) = Color(t.tone(tone))
    val primary = primaryPalette
    val secondary = secondaryPalette
    val tertiary = tertiaryPalette
    val neutral = neutralPalette
    val neutralVariant = neutralVariantPalette
    val error = errorPalette
    return if (isDark) {
        darkColorScheme(
            primary = p(primary, 80), onPrimary = p(primary, 20),
            primaryContainer = p(primary, 30), onPrimaryContainer = p(primary, 90),
            secondary = p(secondary, 80), onSecondary = p(secondary, 20),
            secondaryContainer = p(secondary, 30), onSecondaryContainer = p(secondary, 90),
            tertiary = p(tertiary, 80), onTertiary = p(tertiary, 20),
            tertiaryContainer = p(tertiary, 30), onTertiaryContainer = p(tertiary, 90),
            error = p(error, 80), onError = p(error, 20),
            errorContainer = p(error, 30), onErrorContainer = p(error, 90),
            background = p(neutral, 10), onBackground = p(neutral, 90),
            surface = p(neutral, 10), onSurface = p(neutral, 90),
            surfaceVariant = p(neutralVariant, 30), onSurfaceVariant = p(neutralVariant, 80),
            outline = p(neutralVariant, 60), outlineVariant = p(neutralVariant, 30),
            scrim = Color(0xFF000000),
            inverseSurface = p(neutral, 90), inverseOnSurface = p(neutral, 20),
            inversePrimary = p(primary, 40),
            surfaceDim = p(neutral, 10), surfaceBright = p(neutral, 24),
            surfaceContainerLowest = p(neutral, 4),
            surfaceContainerLow = p(neutral, 10),
            surfaceContainer = p(neutral, 12),
            surfaceContainerHigh = p(neutral, 17),
            surfaceContainerHighest = p(neutral, 22),
        )
    } else {
        lightColorScheme(
            primary = p(primary, 40), onPrimary = p(primary, 100),
            primaryContainer = p(primary, 90), onPrimaryContainer = p(primary, 10),
            secondary = p(secondary, 40), onSecondary = p(secondary, 100),
            secondaryContainer = p(secondary, 90), onSecondaryContainer = p(secondary, 10),
            tertiary = p(tertiary, 40), onTertiary = p(tertiary, 100),
            tertiaryContainer = p(tertiary, 90), onTertiaryContainer = p(tertiary, 10),
            error = p(error, 40), onError = p(error, 100),
            errorContainer = p(error, 90), onErrorContainer = p(error, 10),
            background = p(neutral, 97), onBackground = p(neutral, 10),
            surface = p(neutral, 97), onSurface = p(neutral, 10),
            surfaceVariant = p(neutralVariant, 90), onSurfaceVariant = p(neutralVariant, 30),
            outline = p(neutralVariant, 50), outlineVariant = p(neutralVariant, 80),
            scrim = Color(0xFF000000),
            inverseSurface = p(neutral, 20), inverseOnSurface = p(neutral, 95),
            inversePrimary = p(primary, 80),
            surfaceDim = p(neutral, 87), surfaceBright = p(neutral, 98),
            surfaceContainerLowest = p(neutral, 100),
            surfaceContainerLow = p(neutral, 96),
            surfaceContainer = p(neutral, 94),
            surfaceContainerHigh = p(neutral, 92),
            surfaceContainerHighest = p(neutral, 90),
        )
    }
}

/**
 * ExtendColors：五色 × 十阶扩展色（结构参照 rikkahub `Color.kt` 语义；
 * 数值以 HCT 从品牌种子与固定 hue 派生——**非** rikkahub 的写死 RGB 表）。
 * 用途：用量图表分类色、状态徽标等 M3 色板装不下的语义色。
 */
data class ExtendColors(
    val red: List<Color>, val orange: List<Color>, val green: List<Color>,
    val blue: List<Color>, val gray: List<Color>,
) {
    operator fun get(family: String, shade: Int): Color = when (family) {
        "red" -> red[shade - 1]; "orange" -> orange[shade - 1]
        "green" -> green[shade - 1]; "blue" -> blue[shade - 1]
        else -> gray[shade - 1]
    }
}

/** 由固定 hue 生成 10 阶 ramp（tone 99→20，1-10 阶；暗色反转由 [extendFor] 处理）。 */
private fun ramp(hue: Double, chroma: Double): List<Color> {
    val palette = TonalPalette.fromHueAndChroma(hue, chroma)
    val tones = listOf(99, 95, 90, 80, 70, 60, 50, 40, 30, 20)
    return tones.map { Color(palette.tone(it)) }
}

/** ExtendColors 亮暗两套：暗色把 ramp 反转（1=最深），同 rikkahub 语义。 */
fun extendFor(dark: Boolean): ExtendColors {
    val seedHue = Hct.fromInt(LuzzySeed.CORAL).hue
    val base = ExtendColors(
        red = ramp(25.0, 32.0),
        orange = ramp(55.0, 40.0),
        green = ramp(145.0, 32.0),
        blue = ramp(260.0, 24.0),
        gray = ramp(seedHue, 8.0),
    )
    if (!dark) return base
    return ExtendColors(
        red = base.red.reversed(), orange = base.orange.reversed(),
        green = base.green.reversed(), blue = base.blue.reversed(),
        gray = base.gray.reversed(),
    )
}

/** 语义色（承袭现行 DESIGN.md，不随主题推导）：success / warning / error。 */
object LuzzySemantic {
    val Success = Color(0xFF5DB872)
    val Warning = Color(0xFFD4A017)
    val Error = Color(0xFFC64545)
}

val LocalExtendColors = staticCompositionLocalOf { extendFor(false) }
val LocalDarkMode = compositionLocalOf { false }

/** 便捷访问：`Extend.current["gray", 5]`。 */
object Extend {
    val current: ExtendColors
        @Composable get() = LocalExtendColors.current
}
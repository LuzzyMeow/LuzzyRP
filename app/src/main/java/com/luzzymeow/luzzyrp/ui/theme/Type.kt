package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.R

/**
 * [INVARIANT-FONT] 内置字体体系（HARD_REQUIREMENTS 规定 6）
 *
 * 规定：系统内置字体仅限 AlibabaPuHuiTi-3（中文）与 AlibabaSans（西文/数字）。
 *
 * 实现：利用 Compose FontFamily 的逐字符回退链——
 *   链首放 AlibabaSans（仅覆盖拉丁字母/数字/半角标点），
 *   链尾放 AlibabaPuHuiTi（覆盖 CJK 与全角标点）。
 * 逐字符解析时：拉丁/数字 → 命中 Sans；中文 → Sans 不覆盖，落到 PuHuiTi；
 * 其他文字（韩文等）→ 两者皆不覆盖 → 自动回退系统默认字体。
 * 这正好满足「只有非简体中文和英语时回退系统默认字体」的规定。
 *
 * [INVARIANT-FONT] 禁止向 res/font 添加这两个家族之外的字体文件；
 * 禁止调换链序（链序决定中西文分流）。
 */
object LuzzyFonts {

    // —— 混排字族：Regular ——
    val MixedRegular: FontFamily = FontFamily(
        Font(R.font.alibaba_sans_regular),
        Font(R.font.puhuiti_regular),
    )

    // —— 混排字族：Medium ——
    val MixedMedium: FontFamily = FontFamily(
        Font(R.font.alibaba_sans_medium),
        Font(R.font.puhuiti_medium),
    )

    // —— 混排字族：Bold ——
    val MixedBold: FontFamily = FontFamily(
        Font(R.font.alibaba_sans_bold),
        Font(R.font.puhuiti_bold),
    )

    /** 按字重取对应混排字族（UI 层唯一入口）。 */
    fun family(weight: FontWeight): FontFamily = when (weight) {
        FontWeight.Black, FontWeight.ExtraBold, FontWeight.Bold, FontWeight.SemiBold -> MixedBold
        FontWeight.Medium -> MixedMedium
        else -> MixedRegular
    }
}

/**
 * Luzzy Typography：全局字族固定为混排体系，尺寸/字距按 Material3 Expressive 基线调校。
 */
fun luzzyTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = LuzzyFonts.MixedBold, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = LuzzyFonts.MixedRegular, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LuzzyFonts.MixedRegular, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = LuzzyFonts.MixedRegular, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = LuzzyFonts.MixedMedium, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * [INVARIANT-THEME] Aurora Dual 唯一色源（HARD_REQUIREMENTS 规定 6）
 *
 * 全项目禁止出现硬编码色值：任何新颜色必须定义在本文档并经扩展色通道注入主题。
 * 品牌双色：AuroraPink（主）与 AuroraViolet（辅）构成「极光双生」体系。
 */
object AuroraColor {
    // —— 品牌色 ——
    val AuroraPink = Color(0xFFFF6EC7)
    val AuroraViolet = Color(0xFFB57BFF)

    // —— 画布 ——
    val CanvasLight = Color(0xFFFAF7F2)   // 暖纸感亮底
    val CanvasDark = Color(0xFF0E1116)    // 极夜暗底

    // —— 亮色方案派生（基于品牌双色手工调校，保证对比度） ——
    val PrimaryLight = AuroraPink
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFFFD9EF)
    val OnPrimaryContainerLight = Color(0xFF3A0A2A)
    val SecondaryLight = AuroraViolet
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFEBDCFF)
    val OnSecondaryContainerLight = Color(0xFF291243)
    val TertiaryLight = Color(0xFF00B8A9)      // 辅助点缀：极光青
    val SurfaceLight = Color(0xFFFFF8F3)
    val OnSurfaceLight = Color(0xFF201B19)
    val SurfaceVariantLight = Color(0xFFF2E3E0)
    val OnSurfaceVariantLight = Color(0xFF51443F)
    val OutlineLight = Color(0xFF83736D)
    val ErrorLight = Color(0xFFBA1A1A)

    // —— 暗色方案派生 ——
    val PrimaryDark = Color(0xFFFFB1DE)
    val OnPrimaryDark = Color(0xFF5E1048)
    val PrimaryContainerDark = Color(0xFF7D2C64)
    val OnPrimaryContainerDark = Color(0xFFFFD9EF)
    val SecondaryDark = Color(0xFFD3BAFF)
    val OnSecondaryDark = Color(0xFF44266A)
    val SecondaryContainerDark = Color(0xFF5C3D86)
    val OnSecondaryContainerDark = Color(0xFFEBDCFF)
    val TertiaryDark = Color(0xFF66DAD0)
    val SurfaceDark = Color(0xFF17130F)
    val OnSurfaceDark = Color(0xFFEBE0DB)
    val SurfaceVariantDark = Color(0xFF51443F)
    val OnSurfaceVariantDark = Color(0xFFD5C3BD)
    val OutlineDark = Color(0xFF9E8D87)
    val ErrorDark = Color(0xFFFFB4AB)

    // AMOLED 纯黑模式覆盖（暗底直接压到纯黑）
    val CanvasAmoled = Color(0xFF000000)
    val SurfaceAmoled = Color(0xFF0A0A0A)
}

/**
 * 主题模式偏好。
 * system 跟随系统；light/dark 固定；amoled 为纯黑变体。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

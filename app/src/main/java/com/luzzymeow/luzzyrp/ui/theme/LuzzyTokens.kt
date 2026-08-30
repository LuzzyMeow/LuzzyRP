package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [INVARIANT-THEME] 图层令牌（DESIGN.md「Elevation & Depth」唯一真源）
 *
 * 五层深度体系（open-design 图层契约）：
 *   Layer0 画布 → Layer1 卡片 → Layer2 输入栏/悬浮 → Layer3 弹窗/Sheet → Layer4 Toast
 * 任何新 UI 必须声明所属层级并取用对应阴影/色阶；禁止随手写 dp。
 */
object LuzzyElevation {
    /** Layer0：画布（无阴影）。 */
    val Canvas: Dp = 0.dp

    /** Layer1：卡片/列表项。 */
    val Card: Dp = 2.dp

    /** Layer2：输入栏/悬浮按钮。 */
    val Floating: Dp = 6.dp

    /** Layer3：弹窗/BottomSheet。 */
    val Dialog: Dp = 12.dp

    /** Layer4：Toast。 */
    val Toast: Dp = 16.dp

    /** Layer3 弹窗遮罩透明度（0-1）。 */
    const val SCRIM_ALPHA: Float = 0.45f
}

/**
 * [INVARIANT-THEME] 图标语义尺寸令牌（pro-rules「Consistent Icon Sizing」）。
 * 禁止在业务代码中直接写 17.dp / 22.dp 之类的随机图标值。
 */
object LuzzyIconSize {
    /** 行内小图（标签旁、时间线内）。 */
    val Inline: Dp = 16.dp

    /** 默认操作图标（列表行、次级按钮）。 */
    val Default: Dp = 20.dp

    /** 强调图标（顶栏、主操作）。 */
    val Emphasis: Dp = 24.dp

    /** 展示级图标（空态、Hero 区）。 */
    val Hero: Dp = 32.dp
}

/**
 * [INVARIANT-THEME] 圆角令牌（DESIGN.md「Shapes」）。
 */
object LuzzyCorner {
    val ExtraSmall: Dp = 8.dp
    val Small: Dp = 12.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 20.dp
    val Full: Dp = 999.dp
}

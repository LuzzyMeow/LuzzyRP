package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [INVARIANT-THEME] 4pt 栅格间距令牌（HARD_REQUIREMENTS 规定 6）
 * UI 布局间距一律从本表取值，禁止魔法数字。
 */
object LuzzySpacing {
    val XS: Dp = 4.dp     // 图标与文字微间距
    val SM: Dp = 8.dp     // 行内元素间距
    val MD: Dp = 12.dp    // 卡片内边距（紧凑）
    val LG: Dp = 16.dp    // 页面水平边距 / 卡片内边距
    val XL: Dp = 24.dp    // 区块间距
    val XXL: Dp = 32.dp   // 大区块间距
    val XXXL: Dp = 48.dp  // 页面级留白
}

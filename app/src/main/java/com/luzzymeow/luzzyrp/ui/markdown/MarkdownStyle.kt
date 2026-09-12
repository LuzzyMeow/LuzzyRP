package com.luzzymeow.luzzyrp.ui.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown 排版 token（**唯一取值处**；DESIGN-compose §16 的落地）。
 *
 * 纪律（ui-ux-pro-max pro-rules）：字号/间距一律走 token，禁止在渲染函数里散落硬编码；
 * 提供 CompositionLocal 以便将来按用户字号设置（上游 `settings.fontSize` 同语义）整体缩放。
 *
 * 层级取值说明：rikkahub 的头号缩放是 24/22/20/18（其正文 16sp），换算到我们
 * **气泡内 13.5sp 正文**且气泡宽仅 336dp，直接套用会把 h1 撑到 20sp+ 而溢出换行，
 * 故按 1.33 / 1.19 / 1.11 的比例收窄为 18 / 16 / 15sp（h4+ 与正文同号，仅加粗）。
 */
@Immutable
data class MarkdownTokens(
    val bodySize: TextUnit = 13.5.sp,
    val bodyLineHeight: TextUnit = 23.sp,
    val h1Size: TextUnit = 18.sp,
    val h2Size: TextUnit = 16.sp,
    val h3Size: TextUnit = 15.sp,
    val h4Size: TextUnit = 13.5.sp,
    val headingLineHeight: TextUnit = 24.sp,
    val codeSize: TextUnit = 11.5.sp,
    val codeLineHeight: TextUnit = 17.sp,
    /** 块之间的垂直间距（8dp 节奏，pro-rules）。 */
    val blockGap: androidx.compose.ui.unit.Dp = 8.dp,
    /** 列表每级缩进。 */
    val listIndent: androidx.compose.ui.unit.Dp = 14.dp,
    /** 代码块折叠阈值（超过则折叠，对齐 rikkahub 的 10 行）。 */
    val codeCollapseLines: Int = 10,
) {
    fun headingSize(level: Int): TextUnit = when (level) {
        1 -> h1Size
        2 -> h2Size
        3 -> h3Size
        else -> h4Size
    }
}

val LocalMarkdownTokens = staticCompositionLocalOf { MarkdownTokens() }

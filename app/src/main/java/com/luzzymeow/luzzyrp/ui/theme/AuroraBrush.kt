package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * [INVARIANT-THEME] 极光渐变系统（DESIGN.md v2「Aurora Brush」唯一来源）。
 *
 * 用于：发送/主行动按钮、激活态芯片、区块头强调、品牌区。
 * 渐变是品牌资产——禁止在业务代码里自拼 Pink+Violet 渐变，一律取用本表。
 */
object AuroraBrush {

    /** 主渐变：Pink → Violet（45°，主行动/强调）。 */
    val primary: Brush = Brush.linearGradient(
        colors = listOf(AuroraColor.AuroraPink, AuroraColor.AuroraViolet),
        tileMode = TileMode.Clamp,
    )

    /** 反向渐变：Violet → Pink（次级行动/装饰条）。 */
    val reverse: Brush = Brush.linearGradient(
        colors = listOf(AuroraColor.AuroraViolet, AuroraColor.AuroraPink),
        tileMode = TileMode.Clamp,
    )

    /** 画布顶部微渐变（Layer0 氛围层：极淡品牌色自顶部渗入）。 */
    fun canvasScrim(isDark: Boolean): Brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0x14B57BFF), Color.Transparent)   // Violet 8%
        } else {
            listOf(Color(0x1AFF6EC7), Color.Transparent)   // Pink 10%
        },
    )

    /** 软性高光渐变（卡片顶部微光）。 */
    val highlight: Brush = Brush.verticalGradient(
        colors = listOf(Color(0x0DFFFFFF), Color.Transparent),
    )
}

/** 本地标记当前是否暗色（渐变/氛围层使用）。 */
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 包装器：为子树注入 LocalIsDark（LuzzyTheme 内部已调用；页面一般无需直接使用）。
 */
@Composable
fun AuroraDarknessProvider(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalIsDark provides dark) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

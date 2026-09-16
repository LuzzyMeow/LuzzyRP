package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loom v4 设计令牌（设计真源 `docs/DESIGN-ui-v4.md`）。
 *
 * ## 色彩纪律
 * 全部从 M3 role 取（`MaterialTheme.colorScheme`），**零硬编码色相**——亮暗两套
 * 自动跟随（种子 = 珊瑚陶土 #CC785C，TONAL_SPOT）。
 * 「织层」阶梯：canvas → card → raised → overlay，对应 M3 surface 容器四阶，
 * 这正是 M3 的 tonal depth（material-3 skill：tonal surface 替代阴影）。
 *
 * ## 形状
 * 卡 18 / hero 24 / 控件 12 / 药丸 999（M3 corner medium/large/xl 语义对齐）。
 */
@Immutable
data class LoomColors(
    /** 画布阶梯（tonal depth，不是阴影）。 */
    val canvas: Color,
    val card: Color,
    val raised: Color,
    val overlay: Color,

    /** 卡片顶缘高光（亮 5% / 暗 3%）：一层微光替代重边框。 */
    val topHighlight: Color,
    /** 发丝线。 */
    val hairline: Color,
    /** 织纹点（onSurface 极低透明度，暗色再降）。 */
    val weave: Color,

    /** 语义（承袭 LuzzySemantic，不随主题推导）。 */
    val success: Color,
    val warning: Color,
    val danger: Color,
)

/**
 * 动效令牌（open-design：进入 200 / 退出 140；page 用仓库实测 420ms）。
 * 「减弱动效」由 [com.luzzymeow.luzzyrp.ui.scaledDuration] 在调用点折算——
 * 令牌只存基准值，不读系统设置（保持纯数据可测）。
 */
object LoomMotion {
    /** 按下反馈。 */
    const val PressMs = 90
    /** 退出 / 收起（快于进入：退出是用户已做的决定）。 */
    const val QuickMs = 140
    /** 进入 / 淡入 / scrim。 */
    const val StandardMs = 200
    /** hero、底部表、图表绘制。 */
    const val ExpressiveMs = 320
    /** 跨页交叉淡化（与抽屉收起等长，2026-09-12 实测值，勿改）。 */
    const val PageMs = 420
    /** 详情推进（二级页）。 */
    const val PushMs = 240
    /** 列表入场步进。 */
    const val StaggerMs = 40
    /** 列表入场上限（超过部分同帧出现，防长列表拖沓）。 */
    const val StaggerCap = 6
}

/** 圆角令牌。 */
object LoomShape {
    val Card: Dp = 18.dp
    val Hero: Dp = 24.dp
    val Control: Dp = 12.dp
    val Pill: Dp = 999.dp
}

/**
 * Loom 环境提供者。
 * 亮暗在 [com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme] 装配一次，页面只读。
 */
val LocalLoom = staticCompositionLocalOf { loomColors(dark = false) }

/** 便捷访问：`Loom.current.card`。 */
object Loom {
    val current: LoomColors
        @Composable get() = LocalLoom.current
}

/** 由 M3 role 推导织层色（亮暗各一套；语义色取对应变体——见 LuzzySemantic 注释）。 */
fun loomColors(dark: Boolean): LoomColors {
    val scheme = luzzyColorScheme(dark = dark)
    val semantic = if (dark) LuzzySemantic.Dark else LuzzySemantic.Light
    return LoomColors(
        canvas = scheme.surface,
        card = scheme.surfaceContainerLow,
        raised = scheme.surfaceContainerHigh,
        overlay = scheme.surfaceContainerHighest,
        topHighlight = Color.White.copy(alpha = if (dark) 0.03f else 0.05f),
        hairline = scheme.outlineVariant.copy(alpha = 0.4f),
        weave = scheme.onSurface.copy(alpha = if (dark) 0.025f else 0.035f),
        success = semantic.Success,
        warning = semantic.Warning,
        danger = semantic.Error,
    )
}

// ───────────────────────── 画布与织纹 ─────────────────────────

/**
 * 页面画布：`surface` 底 + 顶部珊瑚 wash（primary 渐入，向下收敛）。
 *
 * 返回竖向渐变 Brush；画布织纹由 [WeaveSpec] 绘制（点阵，12dp 间距）。
 * 每页一个 accent（primary/secondary/tertiary 之一），由调用方传入。
 * wash 强度：亮 8% / 暗 6%（暗色下过强会泛灰）。
 */
@Composable
fun loomCanvasBrush(accent: Color): Brush {
    val dark = LocalDarkMode.current
    val surface = MaterialTheme.colorScheme.surface
    return Brush.verticalGradient(
        0f to lerp(surface, accent, if (dark) 0.06f else 0.08f),
        0.42f to lerp(surface, accent, 0.015f),
        1f to surface,
    )
}

/**
 * 织纹点阵画布参数（供 `drawBehind` / Canvas 使用）：
 * 12dp 间距、点半径 0.8dp、颜色 [LoomColors.weave]。
 */
object WeaveSpec {
    val Pitch: Dp = 12.dp
    val Dot: Dp = 0.8.dp
}
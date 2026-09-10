package com.luzzymeow.luzzyrp.assistant.ui.component.ledger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 「卷宗」组件库的尺寸与字级（DESIGN.md §管理页组件规范，v1.5.0）。
 *
 * **全部取自上游 Tailwind 类**（1 CSS px = 1 dp = 1 sp），**不得临场改动**；
 * 新增规格必须先登记到 DESIGN.md 再实现（硬性规定 9 第 3 步）。
 */
object Ledger {

    // ---- 页面骨架 ----
    /** `.management-view` `p-4`。 */
    val PagePadding = 16.dp
    /** 页面头 `h-12`（48dp，含 24dp 图标与右侧动作）。 */
    val PageHeaderHeight = 48.dp
    /** 页面头 `mb-4`。 */
    val PageHeaderGap = 16.dp
    /** 卡片间距 `space-y-4`。 */
    val CardGap = 16.dp
    /** 卡片内边距 `p-4`。 */
    val CardPadding = 16.dp

    // ---- 圆角（rounded-lg / xl / 2xl）----
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusPill = 999.dp

    // ---- 控件 ----
    /** 图标按钮 `p-2.5` + 20dp 图标。 */
    val IconButtonSize = 40.dp
    /** 次级按钮 `px-3 py-1.5` + `text-xs`。 */
    val ButtonHeight = 32.dp
    val ButtonPaddingH = 12.dp
    /** `.settings-toggle` 2.75rem × 1.5rem，滑块 1.25rem，位移 0.125rem。 */
    val ToggleWidth = 44.dp
    val ToggleHeight = 24.dp
    val ToggleThumb = 20.dp
    val ToggleInset = 2.dp
    /** 输入框 `px-4 py-3`。 */
    val InputMinHeight = 44.dp
    /** 搜索框 `py-2.5` + `pl-10`。 */
    val SearchHeight = 40.dp
    val SearchIconInset = 12.dp
    /** 折叠行 `px-4 py-3`。 */
    val CollapseRowHeight = 48.dp
    /**
     * 折叠面板时长（DESIGN.md §Motion 令牌）：**展开 200ms / 收起 140ms**。
     *
     * 2026-09-10 修订：原为上游 `.settings-collapse` 的 360ms——与会话 48 已收敛到令牌的
     * Web 侧栏折叠（200/140）分裂成两种节奏，故追认为令牌值（审查档 A3）。
     */
    const val CollapseExpandMs = 200
    const val CollapseCollapseMs = 140
    /** 列表行最小高度。 */
    val ListRowMinHeight = 48.dp
    /** 分段选择器外框 `p-1`。 */
    val SegmentPadding = 4.dp

    // ---- 图标（w-6 / md:w-7 / w-5 / w-4）----
    val IconSize = 24.dp
    val IconSizeLg = 28.dp
    val IconSizeMd = 20.dp
    val IconSizeSm = 16.dp
    /** 线性图标描边（stroke-width 2）。 */
    val IconStroke = 2.dp
}

/** 字级（上游 `text-*` 类，1px = 1sp）。 */
object LedgerType {
    /** 页面标题 `text-xl font-bold`。 */
    val pageTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

    /** 卡片/折叠行标题 `font-bold` `text-sm`。 */
    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

    /** 正文 `text-sm`。 */
    val body: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)

    /** 次级文字 `text-xs`。 */
    val caption: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)

    /** 分组标题 `.settings-section-heading`（12sp / 700 / 0.05em / uppercase）。 */
    val sectionHeading: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05f.em,
        )

    /** 按钮文字 `text-xs font-medium`。 */
    val button: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

    /** 标签 `text-sm font-semibold`。 */
    val label: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
}

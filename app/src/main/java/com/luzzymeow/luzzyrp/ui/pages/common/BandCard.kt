package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * **带纹理头带的设置卡**（v3.2 起为 [LoomHero] 的 API 兼容薄壳）。
 *
 * 织带绘制曾在此处与 LoomKit 各存一份（契约 KDoc 承诺「旧文件本轮改造后删除」，
 * 拖欠至 v3.2 兑现）：现在全部视觉走 [LoomHero]（含顶缘高光），本文件只保留
 * 参数映射与 [bandTone]。4 个既有调用点（设置/关于页）**零改动**。
 *
 * ## 色相纪律（原文保留）
 *
 * 头带颜色**只从 M3 role 取**（调用方传 `colorScheme.primary` / `secondary` / `tertiary`…），
 * 第二色由 [bandTone] 从同一色相与画布混出 —— **不再有硬编码色值**，
 * 亮暗两套自动跟随。带上的文字色同理必须传 **on 色**（`onPrimary` …）：
 * 暗色下 `primary` 是浅桃色，白字压上去会读不清（历史真机截图抓到的对比度缺陷）。
 */
@Composable
fun BandCard(
    title: String,
    iconRes: Int,
    bandFirst: Color,
    bandSecond: Color,
    /** 头带上的文字/图标色：**必须传对应 role 的 on 色**（`onPrimary` / `onSecondary` / `onTertiary`）。 */
    bandContent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LoomHero(
        title = title,
        iconRes = iconRes,
        first = bandFirst,
        second = bandSecond,
        onColor = bandContent,
        modifier = modifier,
        trailing = trailing,
        overlap = avatar,
        content = content,
    )
}

/**
 * 头带第二色：**同色相的 T 阶**——与画布按 34% 混合。
 *
 * 为什么不用 `color.copy(alpha)`：alpha 叠加在背景上会透出画布，暗色下滑向灰；
 * 用 [lerp] 得到的是**不透明的同色相深浅**，亮暗两套都稳。
 * 34% 是实测取值：18% 时两色差过小，头带看着像纯色块、纹理也显不出来。
 */
fun bandTone(color: Color, surface: Color): Color = lerp(color, surface, 0.34f)
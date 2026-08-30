package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * [INVARIANT-MOTION] 三态动效令牌 v2（huashu-design「动效=物理学」纪律 + DESIGN.md v2）
 *
 * 分类规格（每类场景固定搭配，禁止随手组合）：
 *   页面转场  → slide¼ + fade，Enter 300 / Exit 195（快出慢入不对称）
 *   弹窗      → scale 0.92→1 spring(Enter) / 195(Exit) + scrim fade
 *   Sheet     → slideUp 300 / 195
 *   列表进入  → stagger 20ms 波次（fade+scale 0.92）
 *   按压交互  → scale 0.98 + 色彩过渡 150ms（布局不跳动）
 *   展开/收起 → expand/shrinkVertically 195
 *   流式正文  → 永不动画（真流式不变性优先于一切动效）
 *
 * [INVARIANT-STREAMING] 所有文本流式渲染直出，本文件不提供打字机规格。
 */
object MotionTokens {

    // —— 时长（ms）——
    const val DURATION_ENTER = 300
    const val DURATION_INTERACT = 150
    const val DURATION_EXIT = 195
    const val LIST_STAGGER_MS = 20

    // —— 缓动（物理隐喻：Enter=物体入场带惯性衰减；Exit=退场加速离开）——
    val EasingEnter: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingExit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val EasingInteract: Easing = LinearEasing
    val EasingDecelerate: Easing = LinearOutSlowInEasing

    // —— 通用规格 ——
    fun <T> enter(): TweenSpec<T> = tween(DURATION_ENTER, easing = EasingEnter)
    fun <T> interact(): TweenSpec<T> = tween(DURATION_INTERACT, easing = EasingInteract)
    fun <T> exit(): TweenSpec<T> = tween(DURATION_EXIT, easing = EasingExit)

    /** 弹性进场（弹窗/卡片/Hero）：轻微过冲，阻尼 0.8。 */
    fun <T> springEnter(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 按压回弹（快、小过冲）。 */
    fun <T> springPress(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // —— 场景预设 ——

    /** 弹窗 scale：0.92 → 1。 */
    const val DIALOG_SCALE_FROM = 0.92f

    /** 列表进入 scale：0.92 → 1（配 fade + stagger）。 */
    const val LIST_SCALE_FROM = 0.92f

    /** 页面转场位移比（¼ 屏）。 */
    const val PAGE_SLIDE_FACTOR = 4

    /** 系统减弱动效时的全局替代规格（直接淡入淡出、无位移/过冲）。 */
    fun <T> reduced(): TweenSpec<T> = tween(DURATION_ENTER, easing = EasingInteract)
}

package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.ui.scaledDuration
import com.luzzymeow.luzzyrp.ui.rememberReduceMotion

/**
 * Loom v4 动效系统（open-design + material-3 skill 的折衷，全部经
 * [scaledDuration] 折算「减弱动效」）。
 *
 * ## 曲线
 * - **页面/抽屉级**（420ms）：`FastOutSlowInEasing`——仓库 2026-09-12 实测定档，
 *   强 ease-out 会让 400ms 动画感知只有 ~100ms（对称曲线铺满全程）。
 * - **元素级进入**（200/320ms）：`LoomEasing.Enter`（`cubic-bezier(0.23,1,0.32,1)`）——
 *   元素级动画时间短，ease-out 的快启动是「响应感」，不会像整页转场那样被压缩感知。
 * - **交互开关**：spring（dampingRatio 0.75 / stiffness MediumLow）。
 * - 禁 scale(0) 起步（open-design 红线）；缩放类下限 0.92。
 *
 * ## 「减弱动效」
 * 一切一次性时长都过 [scaledDuration]：系统开了「移除动画」→ 归零 → 跳到终态。
 * 无限循环动效（呼吸点、shimmer）用 `reduce` 分支停帧，不用时长归零。
 */
object LoomEasing {
    /** 页面/抽屉/大结构：对称（感知时长 = 实际时长）。 */
    val Page: androidx.compose.animation.core.Easing = FastOutSlowInEasing
    /** 元素进入：快启动 ease-out。 */
    val Enter: CubicBezierEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** 退出：加速离场（退出是用户已做的决定，更快）。 */
    val Exit: CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/** spring（交互态：开关 / chip / 指示器）。 */
fun <T> loomSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMediumLow,
)

/** tween 快捷方式（自动折算减弱动效）。 */
@Composable
fun loomTween(ms: Int, easing: androidx.compose.animation.core.Easing = LoomEasing.Enter) =
    tween<Any>(scaledDuration(ms, rememberReduceMotion()), easing = easing)

/**
 * 元素进入动效（fade + 上移 10dp + 列表 stagger，封顶 [LoomMotion.StaggerCap]）。
 *
 * 用法（LazyColumn 的 item 内容外）：
 * ```
 * LoomAppear(index = index) { LoomRow(...) }
 * ```
 * 减弱动效时直接显示。
 */
@Composable
fun LoomAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = rememberReduceMotion()
    if (scaledDuration(LoomMotion.StandardMs, reduce) == 0) {
        content()
        return
    }
    val step = index.coerceIn(0, LoomMotion.StaggerCap)
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(LoomMotion.StandardMs, delayMillis = LoomMotion.StaggerMs * step, easing = LoomEasing.Enter),
        label = "loom-appear-$step",
    )
    val shift = (10 * (1f - progress)).dp
    Box(
        modifier.graphicsLayer {
            alpha = if (progress >= 0.999f) 1f else progress
            translationY = shift.toPx()
        },
    ) { content() }
}

/**
 * 按压缩放反馈（90ms，快速回弹）。
 *
 * ```kotlin
 * val pressed by interactionSource.collectIsPressedAsState()
 * Modifier.loomPress(pressed)
 * ```
 * 减弱动效时返回原样（无缩放）。
 */
fun Modifier.loomPress(pressed: Boolean, reduce: Boolean): Modifier {
    if (reduce || !pressed) return this
    return graphicsLayer(scaleX = 0.985f, scaleY = 0.985f)
}

/**
 * 底部表 / 对话框 / 二级页的进出 spec 集。
 * `ms` 传基准时长（内部折算退出 0.7×、下限 QuickMs）。
 */
object LoomTransitions {
    /** 详情推进：右侧 1/8 滑入 + fade。 */
    fun enterPush(ms: Int): Pair<EnterTransition, ExitTransition> =
        (
            slideInHorizontally(tween(ms, easing = LoomEasing.Enter)) { it / 8 } +
                fadeIn(tween(ms, easing = LoomEasing.Enter))
            ) to (
            slideOutHorizontally(tween(exitMs(ms), easing = LoomEasing.Exit)) { it / 8 } +
                fadeOut(tween(exitMs(ms), easing = LoomEasing.Exit))
            )

    /** 底部升起（表/对话框/批量栏）。 */
    fun enterRise(ms: Int): Pair<EnterTransition, ExitTransition> =
        (
            slideInVertically(tween(ms, easing = LoomEasing.Enter)) { it / 8 } +
                fadeIn(tween(ms, easing = LoomEasing.Enter))
            ) to (
            slideOutVertically(tween(exitMs(ms), easing = LoomEasing.Exit)) { it / 8 } +
                fadeOut(tween(exitMs(ms), easing = LoomEasing.Exit))
            )

    /** 纯交叉淡化。 */
    fun enterFade(ms: Int): Pair<EnterTransition, ExitTransition> =
        fadeIn(tween(ms, easing = LoomEasing.Enter)) to
            fadeOut(tween(exitMs(ms), easing = LoomEasing.Exit))
}

private fun exitMs(ms: Int): Int = (ms * 0.7f).toInt().coerceAtLeast(LoomMotion.QuickMs)

/** 减弱动效下的线性时长（供呼吸点等循环动效判断）。 */
@Composable
fun loomLoopOk(): Boolean = !rememberReduceMotion()
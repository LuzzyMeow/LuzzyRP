package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * [INVARIANT-MOTION] 三态动效令牌（HARD_REQUIREMENTS 规定 6）
 *
 * 令牌体系（与 Aurora Dual 设计基线一致）：
 *   - 进入（Enter）= 300ms：页面/容器/弹窗的进场
 *   - 交互（Interact）= 150ms：按压/切换/展开等即时反馈
 *   - 退出（Exit）= 195ms：离场
 * 所有新增 UI 的过渡动画必须从本文件取令牌，禁止在业务代码中随手写时长。
 * 流式正文输出不使用打字机动画（真流式不变性：1 字 = 1 次更新直出）。
 */
object MotionTokens {
    // —— 时长 ——
    const val DURATION_ENTER = 300
    const val DURATION_INTERACT = 150
    const val DURATION_EXIT = 195

    // —— 缓动 ——
    val EasingEnter = CubicBezierEasing(0.2f, 0f, 0f, 1f)      // 减速进场
    val EasingExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)       // 平滑离场
    val EasingInteract = LinearEasing

    // —— 预制动画规格 ——
    fun <T> enter() = tween<T>(DURATION_ENTER, easing = EasingEnter)
    fun <T> interact() = tween<T>(DURATION_INTERACT, easing = EasingInteract)
    fun <T> exit() = tween<T>(DURATION_EXIT, easing = EasingExit)

    // 弹性进场（弹窗/卡片 scale 0.92→1 的推荐规格）
    fun <T> springEnter() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow * 1.9f,   // ≈380，与 300ms 观感对齐
        visibilityThreshold = null,
    )

    // 列表项错峰间隔（stagger）
    const val LIST_STAGGER_MS = 20
}

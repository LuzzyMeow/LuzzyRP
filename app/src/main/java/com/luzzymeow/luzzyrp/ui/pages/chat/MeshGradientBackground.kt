package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.luzzymeow.luzzyrp.ui.rememberReduceMotion
import com.luzzymeow.luzzyrp.ui.theme.LuzzyThemeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MeshGradient 背景（DESIGN-compose §7 动效令牌；手法参照 rikkahub
 * `MeshGradientBackground` 的「线性底 + 光斑漂移」——不用 Modifier.blur，全 API 级可用）。
 *
 * 亮色：暖纸白底 + 珊瑚/amber 光斑；暗色：暖黑底 + 深珊瑚光斑。
 * 光斑沿正弦/余弦轨迹缓慢漂移，周期错落（5.5s/7s/8.5s/6.2s）避免整齐划一。
 *
 * ## 减弱动效（C7）
 *
 * 系统「移除动画」开启时**冻结漂移**：光斑停在各自的一个静止相位上，暖底与构图不变。
 * 为什么是冻结而不是「去掉光斑」：光斑是这个设计语言的一部分（暖幕的物质感），
 * 去掉会让背景变得平坦、像未加载完成；而「一直在动」才是前庭敏感人群要避开的。
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LuzzyThemeColors.isDark
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "luzzyMesh")

    @Composable
    fun phase(durationMillis: Int, loops: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI * loops).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis * loops, easing = LinearEasing)),
        label = label,
    )
    // 减弱动效时不读动画值：四路相位都取 0（各光斑停在**各自的初始位置**，
    // 错落感仍在——因为它们的基准坐标本就不同），而动画本身也无需再跑
    val p1 by phase(5_500, loops = 20, label = "p1")
    val p2 by phase(7_000, loops = 1, label = "p2")
    val p3 by phase(8_500, loops = 10, label = "p3")
    val p4 by phase(6_200, loops = 10, label = "p4")
    val a1 = if (reduceMotion) 0f else p1
    val a2 = if (reduceMotion) 1.2f else p2
    val a3 = if (reduceMotion) 2.4f else p3
    val a4 = if (reduceMotion) 3.6f else p4

    val baseStops: List<Pair<Float, Color>> = if (dark) {
        listOf(
            0.0f to Color(0xFF231917),
            0.35f to Color(0xFF1C1412),
            0.7f to Color(0xFF171110),
            1.0f to Color(0xFF140E0D),
        )
    } else {
        listOf(
            0.0f to Color(0xFFFFF4F1),
            0.4f to Color(0xFFFDEFEA),
            0.75f to Color(0xFFFFF4F1),
            1.0f to Color(0xFFFFF8F6),
        )
    }
    val blobCoral = if (dark) Color(0xFF723520).copy(alpha = 0.34f) else Color(0xFFFFDBD0).copy(alpha = 0.55f)
    val blobAmber = if (dark) Color(0xFF51461A).copy(alpha = 0.22f) else Color(0xFFF4E2A7).copy(alpha = 0.42f)
    val blobSoft = if (dark) Color(0xFF5D4036).copy(alpha = 0.20f) else Color(0xFFF7E4DF).copy(alpha = 0.5f)

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(colorStops = baseStops.toTypedArray()))
            fun blob(fractionX: Float, fractionY: Float, radius: Float, color: Color, angle: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(
                            x = w * (fractionX + 0.06f * cos(angle)),
                            y = h * (fractionY + 0.05f * sin(angle)),
                        ),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(
                        x = w * (fractionX + 0.06f * cos(angle)),
                        y = h * (fractionY + 0.05f * sin(angle)),
                    ),
                )
            }
            blob(0.82f, 0.16f, w * 0.55f, blobCoral, a1)
            blob(0.12f, 0.44f, w * 0.62f, blobSoft, a2)
            blob(0.72f, 0.78f, w * 0.7f, blobAmber, a3)
            blob(0.28f, 0.9f, w * 0.5f, blobCoral.copy(alpha = blobCoral.alpha * 0.6f), a4)
        }
        content()
    }
}

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
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LuzzyThemeColors.isDark
    val transition = rememberInfiniteTransition(label = "luzzyMesh")

    @Composable
    fun phase(durationMillis: Int, loops: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI * loops).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis * loops, easing = LinearEasing)),
        label = label,
    )
    val p1 by phase(5_500, loops = 20, label = "p1")
    val p2 by phase(7_000, loops = 1, label = "p2")
    val p3 by phase(8_500, loops = 10, label = "p3")
    val p4 by phase(6_200, loops = 10, label = "p4")

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
            blob(0.82f, 0.16f, w * 0.55f, blobCoral, p1)
            blob(0.12f, 0.44f, w * 0.62f, blobSoft, p2)
            blob(0.72f, 0.78f, w * 0.7f, blobAmber, p3)
            blob(0.28f, 0.9f, w * 0.5f, blobCoral.copy(alpha = blobCoral.alpha * 0.6f), p4)
        }
        content()
    }
}

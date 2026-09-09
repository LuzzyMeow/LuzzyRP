package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 顶栏图标（Canvas 手绘，零依赖）。
 *
 * 项目未引入 `material-icons`（本地依赖缓存无该构件），故按上游聊天页 `#icon-menu` /
 * `#icon-delete` 的视觉重量手绘等价图形：1.6dp 圆头线，24dp 视觉盒。
 */
@Composable
fun MenuIcon(
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier.size(iconSize)) {
        val stroke = strokeWidth.toPx()
        val w = this.size.width
        val h = this.size.height
        val left = w * 0.14f
        val right = w * 0.86f
        listOf(0.3f, 0.5f, 0.7f).forEach { fraction ->
            drawLine(
                color = color,
                start = Offset(left, h * fraction),
                end = Offset(right, h * fraction),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 设置图标（齿轮：外环 + 8 根齿 + 中心小环）。
 *
 * 用户指定「右上角删除按钮 → 助手专属设置按钮」，故用齿轮而非垃圾桶。
 */
@Composable
fun SettingsIcon(
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    strokeWidth: Dp = 1.5.dp,
) {
    Canvas(modifier.size(iconSize)) {
        val stroke = strokeWidth.toPx()
        val center = this.center
        val radius = this.size.minDimension / 2f * 0.58f
        drawCircle(color, radius, center, style = Stroke(stroke))
        drawCircle(color, radius * 0.34f, center, style = Stroke(stroke))
        repeat(8) { index ->
            val angle = (index * 45f) * PI.toFloat() / 180f
            val inner = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
            val outer = Offset(
                center.x + cos(angle) * radius * 1.38f,
                center.y + sin(angle) * radius * 1.38f,
            )
            drawLine(color, inner, outer, stroke, StrokeCap.Round)
        }
    }
}

/** 会话标题右侧的下拉箭头（与上游 `desc-panel-chevron` 同形）。 */
@Composable
fun ChevronIcon(
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier.size(iconSize)) {
        val stroke = strokeWidth.toPx()
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.2f, h * 0.38f), Offset(w * 0.5f, h * 0.66f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.8f, h * 0.38f), stroke, StrokeCap.Round)
    }
}

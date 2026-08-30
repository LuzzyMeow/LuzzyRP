package com.luzzymeow.luzzyrp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import com.luzzymeow.luzzyrp.ui.theme.MotionTokens

/**
 * [INVARIANT-THEME] Aurora 表面引擎：统一卡片/容器视觉与按压交互（规定 6）。
 *
 * - 三态反馈：默认 / 按压（色彩过渡 + 阴影抬升）/ 恢复，动画取 MotionTokens（交互 150ms）；
 * - 禁止 Modifier.blur（设计基线），毛玻璃质感以层叠透明度模拟；
 * - 所有卡片类 UI 统一经由此组件，保证一致性。
 */
@Composable
fun AuroraSurface(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LuzzySpacing.LG),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(LuzzySpacing.MD),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val animatedColor by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceVariant else containerColor,
        animationSpec = MotionTokens.interact(),
        label = "auroraSurfaceColor",
    )
    val animatedShadow by animateDpAsState(
        targetValue = if (pressed) shadowElevation * 1.4f else shadowElevation,
        animationSpec = MotionTokens.interact(),
        label = "auroraSurfaceShadow",
    )

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.shadow(animatedShadow, shape),
        shape = shape,
        color = animatedColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        interactionSource = interaction,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

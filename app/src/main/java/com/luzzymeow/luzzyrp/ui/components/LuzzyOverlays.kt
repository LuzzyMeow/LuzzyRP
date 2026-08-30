package com.luzzymeow.luzzyrp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import com.luzzymeow.luzzyrp.ui.theme.LuzzyCorner
import com.luzzymeow.luzzyrp.ui.theme.LuzzyElevation
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import com.luzzymeow.luzzyrp.ui.theme.MotionTokens

/**
 * LuzzyDialog v2：统一确认/提示弹窗（DESIGN.md「Components」契约）。
 *
 * 动效（MotionTokens v2 弹窗规格）：scale 0.92→1 spring 进场 / 195ms 退场 + fade；
 * Layer3（12dp 阴影）；正文左对齐；按钮右置主行动。
 */
@Composable
fun LuzzyDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    onConfirm: () -> Unit,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(
                initialScale = MotionTokens.DIALOG_SCALE_FROM,
                animationSpec = MotionTokens.springEnter(),
            ) + fadeIn(MotionTokens.enter()),
            exit = scaleOut(targetScale = MotionTokens.DIALOG_SCALE_FROM, animationSpec = MotionTokens.exit()) +
                fadeOut(MotionTokens.exit()),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(LuzzyCorner.Medium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = LuzzyElevation.Dialog,
                shadowElevation = LuzzyElevation.Dialog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(LuzzySpacing.LG)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = LuzzySpacing.SM))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    content()
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = LuzzySpacing.MD))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        if (dismissText != null) {
                            TextButton(onClick = onDismiss) {
                                Text(dismissText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            visible.targetState = false
                            onConfirm()
                        }) {
                            Text(
                                confirmText,
                                color = if (danger) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * EmptyState v2：统一空态（图标 Hero 尺寸 + 标题 + 引导文案），带入场动效。
 */
@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(MotionTokens.enter()) + scaleIn(
            initialScale = MotionTokens.LIST_SCALE_FROM,
            animationSpec = MotionTokens.springEnter(),
        ),
        exit = fadeOut(MotionTokens.exit()),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LuzzySpacing.XXXL),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Icon(
                painterResource(iconRes),
                contentDescription = null, // 装饰性
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = LuzzySpacing.MD),
            )
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = LuzzySpacing.XS))
            Text(hint, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG))
        }
    }
}

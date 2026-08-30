package com.luzzymeow.luzzyrp.ui.components

import androidx.compose.animation.AnimatedVisibility as TopAnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LocalExtendColors
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import com.luzzymeow.luzzyrp.ui.theme.MotionTokens

/**
 * 思考卡片（CoT 时间线节点）。
 *
 * 三态视觉（规定 6 动效令牌）：
 *   - 进行中：脉冲圆点 + 「思考中…」；
 *   - 完成：静态圆点，可展开推理全文；
 *   - 错误/中断：警示色圆点。
 * 展开动画 expandVertically（195ms 退出令牌）；流式期间逐字直出（无打字动画）。
 */
@Composable
fun ThinkingCard(
    reasoning: String,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val extend = LocalExtendColors.current
    val accent = if (inProgress) extend.thinkingPulse else MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LuzzySpacing.MD))
            .background(extend.thinkingCard.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
            .padding(LuzzySpacing.MD),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(LuzzySpacing.SM))
            Text(
                text = when {
                    inProgress -> "思考中…"
                    expanded -> "收起思考"
                    else -> "思考过程"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(if (expanded) LuzzyIcons.Collapse.res else LuzzyIcons.Expand.res),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        TopAnimatedVisibility(
            visible = expanded || inProgress,
            enter = expandVertically(MotionTokens.enter()) + fadeIn(MotionTokens.enter()),
            exit = shrinkVertically(MotionTokens.exit()) + fadeOut(MotionTokens.exit()),
        ) {
            Text(
                text = reasoning.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LuzzySpacing.SM),
            )
        }
    }
}

/**
 * 工具调用卡片：工具名 + 参数摘要 + 输出（内嵌 CoT 列，不独立于思考卡片列）。
 * 三态：等待审批（Pending）/ 执行中 / 已完成（含错误）。
 */
@Composable
fun ToolCard(
    tool: UIMessagePart.Tool,
    onApprove: () -> Unit,
    onDeny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extend = LocalExtendColors.current
    val pendingApproval = tool.approvalState is com.luzzymeow.luzzyrp.core.model.ToolApprovalState.Pending
    val executed = tool.isExecuted

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LuzzySpacing.MD))
            .background(extend.toolCard.copy(alpha = 0.6f))
            .padding(LuzzySpacing.MD),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(LuzzyIcons.Wand.res),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(LuzzySpacing.SM))
            Text(
                text = tool.toolName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    pendingApproval -> "待批准"
                    !executed -> "执行中…"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TopAnimatedVisibility(visible = tool.input.toString().length < 200) {
            Text(
                text = tool.input.toString().take(200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LuzzySpacing.XS),
            )
        }

        tool.output?.let { output ->
            Spacer(Modifier.height(LuzzySpacing.XS))
            Text(
                text = output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }.take(600),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pendingApproval) {
            Spacer(Modifier.height(LuzzySpacing.SM))
            Row {
                androidx.compose.material3.TextButton(onClick = onApprove) { Text("批准") }
                androidx.compose.material3.TextButton(onClick = { onDeny("用户拒绝该工具调用") }) { Text("拒绝") }
            }
        }
    }
}

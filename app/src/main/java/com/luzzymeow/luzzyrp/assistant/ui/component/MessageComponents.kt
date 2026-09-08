package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageRoleUi
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageUi
import com.luzzymeow.luzzyrp.assistant.ui.model.StepGroupUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ThinkingUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ToolCardUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ToolStatusUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyMotion
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 消息区组件（方向 A §1.5）。
 *
 * 三类渲染通道分开：正文气泡 / 思考卡 / 工具卡与步骤组——
 * 与 PLAN §11.2「文本、思考、工具进度三类事件分别走不同渲染通道」对应。
 */
@Composable
fun MessageItem(message: MessageUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        when (message.role) {
            MessageRoleUi.USER -> UserBubble(message.content)
            MessageRoleUi.ASSISTANT -> AssistantMessage(message)
            MessageRoleUi.SYSTEM -> SystemNote(message.content)
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = LuzzyTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(LuzzyShapes.bubble)
                .background(colors.surfaceCard)
                .border(1.dp, colors.accentGraphic.copy(alpha = 0.35f), LuzzyShapes.bubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.body)
        }
    }
}

@Composable
private fun AssistantMessage(message: MessageUi) {
    val colors = LuzzyTheme.colors
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message.thinking?.let { ThinkingCard(it) }
        message.stepGroup?.let { StepGroupCard(it) }
        if (message.content.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(LuzzyShapes.bubble)
                    .background(colors.surfaceSoft)
                    .border(1.dp, colors.hairline, LuzzyShapes.bubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.body,
                )
            }
        }
        message.tools.forEach { ToolCard(it) }
    }
}

@Composable
private fun SystemNote(text: String) {
    val colors = LuzzyTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.mutedSoft,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** 思考卡：整卡 surface-card + hairline；头部 44dp；默认折叠为一行摘要。 */
@Composable
fun ThinkingCard(thinking: ThinkingUi, modifier: Modifier = Modifier) {
    val colors = LuzzyTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceCard)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "THINKING",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = colors.accentGraphic,
            )
            Text(
                text = thinking.summary,
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = thinking.durationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
            )
            Text(text = if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelMedium, color = colors.mutedSoft)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut)) +
                fadeIn(tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut)),
            exit = shrinkVertically(tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut)) +
                fadeOut(tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut)),
        ) {
            Text(
                text = thinking.fullText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.bodyStrongMid,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

/** 工具卡：单行 52dp——等宽工具名 + 参数摘要 + 状态 pill + 耗时。 */
@Composable
fun ToolCard(tool: ToolCardUi, modifier: Modifier = Modifier) {
    val colors = LuzzyTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(enabled = tool.resultPreview != null) { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = colors.body,
            )
            Text(
                text = tool.argsSummary,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ToolStatusPill(tool.status)
            tool.durationLabel?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.mutedSoft)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = tool.resultPreview.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = colors.muted,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun ToolStatusPill(status: ToolStatusUi) {
    val colors = LuzzyTheme.colors
    val (label, tint) = when (status) {
        ToolStatusUi.WAITING_APPROVAL -> "待审批" to colors.warning
        ToolStatusUi.RUNNING -> "执行中" to colors.accentGraphic
        ToolStatusUi.SUCCESS -> "成功" to colors.success
        ToolStatusUi.FAILED -> "失败" to colors.error
    }
    Box(
        modifier = Modifier
            .clip(LuzzyShapes.pill)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** 步骤组：折叠头 56dp「N 步 · 总耗时」，展开为缩进步骤行（12dp 圆点，不用彩边）。 */
@Composable
fun StepGroupCard(group: StepGroupUi, modifier: Modifier = Modifier) {
    val colors = LuzzyTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${group.stepCount} 步 · ${group.totalDurationLabel}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.body,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut)) + fadeIn(),
            exit = shrinkVertically(tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.steps.forEach { step ->
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (step.ok) colors.success else colors.error)
                        )
                        Text(
                            text = step.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colors.body,
                        )
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedSoft,
                        )
                    }
                }
            }
        }
    }
}

/** 审批弹窗内容（白纸面 + 12dp 圆角；主按钮 coral，次按钮描边，危险动作 error 文字）。 */
@Composable
fun ApprovalDialogContent(
    toolName: String,
    argsJson: String,
    onAllowOnce: () -> Unit,
    onAllowSession: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.canvas)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("需要授权", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Text(
            text = toolName,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = colors.accentDeep,
        )
        Text(
            text = argsJson,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(LuzzyShapes.button)
                    .background(colors.accentButton)
                    .clickable(onClick = onAllowOnce)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("允许一次", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(LuzzyShapes.button)
                    .border(1.dp, colors.hairlineStrong, LuzzyShapes.button)
                    .clickable(onClick = onAllowSession)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("本会话始终允许", style = MaterialTheme.typography.labelMedium, color = colors.body)
            }
            Box(
                modifier = Modifier
                    .clip(LuzzyShapes.button)
                    .clickable(onClick = onDeny)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("拒绝", style = MaterialTheme.typography.labelMedium, color = colors.error)
            }
        }
    }
}

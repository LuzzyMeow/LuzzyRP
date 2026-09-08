package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.chat.PendingApproval
import com.luzzymeow.luzzyrp.assistant.ui.chat.PendingQuestion
import com.luzzymeow.luzzyrp.assistant.ui.component.ApprovalDialogContent
import com.luzzymeow.luzzyrp.assistant.ui.component.InputIsland
import com.luzzymeow.luzzyrp.assistant.ui.component.MessageItem
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 会话页（方向 A §1.2：顶栏 56dp → 消息流 → 输入岛 60dp）。
 *
 * 消息流三类渲染通道分列（正文 / 思考卡 / 工具卡与步骤组），见 [MessageItem]。
 */
@Composable
fun ChatScreen(
    assistant: AssistantUi?,
    conversation: ConversationUi?,
    messages: List<MessageUi>,
    streaming: Boolean = false,
    pendingApproval: PendingApproval? = null,
    pendingQuestion: PendingQuestion? = null,
    error: String? = null,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    onApprove: (Boolean) -> Unit = {},
    onDeny: () -> Unit = {},
    onAnswer: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        // 顶栏：助手名 + 会话标题 + ⋯
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSoft)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = MaterialTheme.typography.titleLarge, color = colors.body)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant?.name ?: "助手",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = conversation?.title ?: "新会话",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (streaming) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceSoft)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("■", style = MaterialTheme.typography.labelMedium, color = colors.error)
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                Text("⋯", style = MaterialTheme.typography.titleLarge, color = colors.muted)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { message -> MessageItem(message) }
        }

        // 错误条（可关闭）
        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.error.copy(alpha = 0.08f))
                    .clickable(onClick = onDismissError)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
                Text("关闭", style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }
            Spacer(Modifier.size(8.dp))
        }

        // ask_user 澄清卡（暂停循环，等用户选择）
        if (pendingQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(pendingQuestion.question, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                pendingQuestion.options.forEach { option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(LuzzyShapes.button)
                            .background(colors.surfaceSoft)
                            .clickable { onAnswer(option) }
                            .padding(12.dp),
                    ) {
                        Text(option, style = MaterialTheme.typography.bodyMedium, color = colors.body)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }

        // 审批弹窗（写类工具逐调用审批）
        if (pendingApproval != null) {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                ApprovalDialogContent(
                    toolName = pendingApproval.toolName,
                    argsJson = pendingApproval.argsJson,
                    onAllowOnce = { onApprove(false) },
                    onAllowSession = { onApprove(true) },
                    onDeny = onDeny,
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            InputIsland(
                value = input,
                onValueChange = { input = it },
                enabled = !streaming,
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        input = ""
                    }
                },
                onAttach = { /* P1：SAF 选附件 */ },
            )
        }
    }
}

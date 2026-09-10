package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.chat.PendingApproval
import com.luzzymeow.luzzyrp.assistant.ui.chat.PendingQuestion
import com.luzzymeow.luzzyrp.assistant.ui.component.ApprovalDialogContent
import com.luzzymeow.luzzyrp.assistant.ui.component.CHAT_CONTENT_BOTTOM_PADDING
import com.luzzymeow.luzzyrp.assistant.ui.component.CHAT_CONTENT_HORIZONTAL_PADDING
import com.luzzymeow.luzzyrp.assistant.ui.component.CHAT_CONTENT_TOP_PADDING
import com.luzzymeow.luzzyrp.assistant.ui.component.CHAT_MESSAGE_SPACING
import com.luzzymeow.luzzyrp.assistant.ui.component.ChatTopBar
import com.luzzymeow.luzzyrp.assistant.ui.component.InputIsland
import com.luzzymeow.luzzyrp.assistant.ui.component.MessageItem
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 助手首页 = **LuzzyRP 聊天页版式**（用户 2026-09-09 指定，方向 A 改稿）。
 *
 * 结构对齐上游 `index.html` 的聊天视图：
 * `h-28` 黑色渐隐顶栏（汉堡 + 头像 + 名称 + chevron）→ 消息流（`px-2 pt-14 space-y-12`）
 * → 底部输入岛；**唯一差异点**：顶栏右上角按钮 = 助手专属设置（上游是「清空聊天」）。
 *
 * 会话列表与助手切换收进左侧抽屉（汉堡），管理页入口同在抽屉底部——见 `AssistantSidebar`。
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
    onOpenSidebar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConversationInfo: () -> Unit = {},
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
        // 消息区（顶栏以覆盖层形式压在滚动内容之上，与上游一致）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = CHAT_CONTENT_HORIZONTAL_PADDING,
                    end = CHAT_CONTENT_HORIZONTAL_PADDING,
                    top = CHAT_CONTENT_TOP_PADDING,
                    bottom = CHAT_CONTENT_BOTTOM_PADDING,
                ),
                verticalArrangement = Arrangement.spacedBy(CHAT_MESSAGE_SPACING),
            ) {
                items(messages, key = { it.id }) { message -> MessageItem(message) }
            }

            if (messages.isEmpty() && !streaming) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = assistant?.name ?: "助手",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                    )
                    Text(
                        text = "在下方输入消息开始对话",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }

            ChatTopBar(
                title = assistant?.name ?: "助手",
                subtitle = conversation?.title?.takeIf { it.isNotBlank() && it != "新会话" },
                avatarText = (assistant?.name ?: "助").take(1),
                onMenu = onOpenSidebar,
                onTitleClick = onOpenConversationInfo,
                onSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // 底部：错误条 / 澄清卡 / 审批卡 / 输入岛
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
            }

            if (pendingQuestion != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
            }

            if (pendingApproval != null) {
                ApprovalDialogContent(
                    toolName = pendingApproval.toolName,
                    argsJson = pendingApproval.argsJson,
                    onAllowOnce = { onApprove(false) },
                    onAllowSession = { onApprove(true) },
                    onDeny = onDeny,
                )
            }

            if (streaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(LuzzyShapes.pill)
                            .background(colors.surfaceCard)
                            .clickable(onClick = onStop)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.error),
                        )
                        Text("停止", style = MaterialTheme.typography.labelMedium, color = colors.error)
                    }
                }
            }

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

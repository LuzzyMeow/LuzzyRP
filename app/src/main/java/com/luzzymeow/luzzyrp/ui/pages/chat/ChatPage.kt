package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility as TopAnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luzzymeow.luzzyrp.ui.components.MarkdownText
import com.luzzymeow.luzzyrp.ui.components.ThinkingCard
import com.luzzymeow.luzzyrp.ui.components.ToolCard
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LocalExtendColors
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import com.luzzymeow.luzzyrp.ui.theme.MotionTokens
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 聊天页（应用主场景）。
 *
 * [INVARIANT-STREAMING] 消息列表直连 ChatService 单一真源 StateFlow：
 * 模型每个增量到达即重组渲染（1 字 = 1 次更新）；自动跟随为纯滚动控制，
 * 不参与也不延迟内容更新。
 */
@Composable
fun ChatPage(
    conversationId: String,
    onBack: () -> Unit,
    onSwitchChat: (String) -> Unit = {},
) {
    val viewModel: ChatViewModel = koinViewModel { parametersOf(conversationId) }
    val conversation by viewModel.conversation.collectAsState()
    val generating by viewModel.generating.collectAsState()
    val extend = LocalExtendColors.current

    val visuals = remember(conversation) {
        conversation?.currentMessages().orEmpty().map { MessageVisual.from(it) }
    }
    val listState = rememberLazyListState()
    val lastVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val atBottom = remember { derivedStateOf { lastVisible >= visuals.lastIndex.coerceAtLeast(0) } }
    var showHistory by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    // 流式跟随：内容增长且用户在底部 → 平滑滚动到底
    LaunchedEffect(visuals.size, visuals.lastOrNull()?.textContent?.length) {
        if (atBottom.value && visuals.isNotEmpty()) {
            listState.animateScrollToItem(visuals.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // —— 顶栏 ——
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = conversation?.title?.ifBlank { "对话" } ?: "对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { viewModel.regenerate() }) {
                    Icon(painterResource(LuzzyIcons.Refresh.res), contentDescription = "重新生成",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showHistory = true }) {
                    Icon(painterResource(LuzzyIcons.History.res), contentDescription = "历史会话",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSearch = true }) {
                    Icon(painterResource(LuzzyIcons.Search.res), contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // —— 历史会话面板（4.1）——
        if (showHistory) {
            val allConversations by viewModel.allConversations.collectAsState()
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                Column(Modifier.padding(horizontal = LuzzySpacing.LG)) {
                    Text("历史会话", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(LuzzySpacing.MD))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(LuzzySpacing.XS),
                        modifier = Modifier.heightIn(max = 480.dp),
                    ) {
                        items(allConversations, key = { it.id }) { c ->
                            Surface(
                                onClick = { showHistory = false; onSwitchChat(c.id) },
                                color = if (c.id == conversationId) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(LuzzySpacing.MD),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    c.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(LuzzySpacing.MD),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(LuzzySpacing.XL))
                }
            }
        }

        // —— 搜索面板（4.4）：会话标题 + 当前会话消息内容 ——
        if (showSearch) {
            var query by remember { mutableStateOf("") }
            val hits = remember(query, visuals) {
                if (query.isBlank()) emptyList()
                else visuals.filter { it.textContent.contains(query, ignoreCase = true) }
                    .take(20)
            }
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSearch = false }) {
                Column(Modifier.padding(horizontal = LuzzySpacing.LG)) {
                    Text("在本会话中搜索", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(LuzzySpacing.MD))
                    com.luzzymeow.luzzyrp.ui.components.LuzzyTextField(
                        query, { query = it },
                        placeholder = "输入关键词…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(LuzzySpacing.MD))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(LuzzySpacing.XS),
                        modifier = Modifier.heightIn(max = 420.dp),
                    ) {
                        items(hits.size) { idx ->
                            val hit = hits[idx]
                            Surface(
                                onClick = { showSearch = false },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(LuzzySpacing.MD),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    (if (hit.isUser) "你：" else "角色：") + hit.textContent.take(120),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    modifier = Modifier.padding(LuzzySpacing.MD),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(LuzzySpacing.XL))
                }
            }
        }

        // —— 消息列表 ——
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = LuzzySpacing.LG, vertical = LuzzySpacing.MD,
                ),
                verticalArrangement = Arrangement.spacedBy(LuzzySpacing.MD),
            ) {
                items(visuals, key = { it.message.id }) { visual ->
                    MessageItem(
                        visual = visual,
                        generating = generating,
                        onApprove = viewModel::approveTool,
                        onDeny = { id -> viewModel.denyTool(id) },
                    )
                }
            }

            // 回到底部箭头（离底时出现）
            // FQ 调用顶层函数，避免 ColumnScope.AnimatedVisibility 扩展的隐式接收器冲突
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom.value,
                enter = fadeIn(MotionTokens.interact()) + slideInVertically(MotionTokens.interact()) { it / 2 },
                exit = fadeOut(MotionTokens.interact()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = LuzzySpacing.MD),
            ) {
                val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
                Surface(
                    onClick = {
                        if (visuals.isNotEmpty()) scrollScope.launch { listState.animateScrollToItem(visuals.lastIndex) }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(LuzzyIcons.Expand.res), contentDescription = "回到底部",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // —— 错误条 ——
        val errors by viewModel.errors.collectAsState()
        errors.lastOrNull()?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LuzzySpacing.LG, vertical = LuzzySpacing.XS),
                shape = RoundedCornerShape(LuzzySpacing.SM),
                onClick = { viewModel.dismissError(error) },
            ) {
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(LuzzySpacing.SM),
                )
            }
        }

        // —— 输入栏（两行布局：输入行 / 操作行）——
        ChatInputBar(
            generating = generating,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stop,
        )
    }
}

@Composable
private fun MessageItem(
    visual: MessageVisual,
    generating: Boolean,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
) {
    val extend = LocalExtendColors.current
    val isUser = visual.isUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // 思考卡片（assistant 且有推理内容）
        if (!isUser && visual.reasoningParts.isNotEmpty()) {
            ThinkingCard(
                reasoning = visual.reasoningParts.joinToString("\n") { it.thinking },
                inProgress = generating && visual.textContent.isBlank() && visual.toolParts.none { !it.isExecuted },
            )
            Spacer(Modifier.size(LuzzySpacing.XS))
        }

        // 工具卡片（内嵌 CoT 列）
        visual.toolParts.forEach { tool ->
            ToolCard(
                tool = tool,
                onApprove = { onApprove(tool.toolCallId) },
                onDeny = { reason -> onDeny(tool.toolCallId) },
            )
            Spacer(Modifier.size(LuzzySpacing.XS))
        }

        // 正文气泡
        if (visual.textContent.isNotBlank()) {
            val bubbleColor = if (isUser) extend.userBubble else extend.aiBubble
            val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = if (isUser) LuzzySpacing.MD else LuzzySpacing.XS,
                    topEnd = LuzzySpacing.MD,
                    bottomStart = LuzzySpacing.MD,
                    bottomEnd = if (isUser) LuzzySpacing.XS else LuzzySpacing.MD,
                ),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                MarkdownText(
                    text = visual.textContent,
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    modifier = Modifier.padding(horizontal = LuzzySpacing.LG, vertical = LuzzySpacing.SM),
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    generating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LuzzySpacing.MD, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("写下你的故事…", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp),
                shape = RoundedCornerShape(LuzzySpacing.LG),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                maxLines = 4,
            )
            Spacer(Modifier.size(LuzzySpacing.SM))
            // 发送 / 停止（同一按钮位，三态过渡）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (generating) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
            ) {
                IconButton(onClick = { if (generating) onStop() else { onSend(input); input = "" } }) {
                    Icon(
                        painter = painterResource(if (generating) LuzzyIcons.Stop.res else LuzzyIcons.Send.res),
                        contentDescription = if (generating) "停止" else "发送",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

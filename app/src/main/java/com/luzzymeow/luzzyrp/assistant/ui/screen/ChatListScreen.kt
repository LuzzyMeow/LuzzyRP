package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.AssistantAvatarStrip
import com.luzzymeow.luzzyrp.assistant.ui.component.ConversationGroupHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ConversationRow
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.SearchField
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 家 · 会话列表（方向 A §1.1：会话即家，无一级导航）。
 *
 * 三段：顶栏（助手名 + ⋯ 开抽屉）→ 头像条 + 检索 → 按日期分组的会话流。
 */
@Composable
fun ChatListScreen(
    assistants: List<AssistantUi>,
    selectedAssistant: AssistantUi?,
    conversations: List<ConversationUi>,
    onSelectAssistant: (String) -> Unit,
    onOpenManager: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, conversations) {
        if (query.isBlank()) conversations
        else conversations.filter { it.title.contains(query, true) || it.summary.contains(query, true) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group } }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedAssistant?.name ?: "助手",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
                Text(
                    text = "${assistants.size} 位 · ${filtered.size} 条会话",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNewConversation),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = colors.accentButton)
            }
            Spacer(Modifier.size(4.dp))
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

        AssistantAvatarStrip(
            assistants = assistants,
            selectedId = selectedAssistant?.id.orEmpty(),
            onSelect = onSelectAssistant,
            onOpenManager = onOpenManager,
        )

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索会话…",
            )
        }
        Spacer(Modifier.size(8.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "还没有会话" else "没有匹配的会话",
                hint = if (query.isBlank()) "写下第一句，助手会把过程记成手记。" else "换个关键词试试。",
                actionLabel = if (query.isBlank()) "开始新会话" else null,
                onAction = if (query.isBlank()) onNewConversation else null,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val order = listOf("今天", "昨天", "7 天内", "本月", "更早")
                order.forEach { group ->
                    val items = grouped[group] ?: return@forEach
                    item(key = "h-$group") { ConversationGroupHeader(group) }
                    items(items, key = { it.id }) { conversation ->
                        ConversationRow(conversation, onClick = { onOpenConversation(conversation.id) })
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** 全部助手管理页（方向 A：顶栏头像条 `+` 进入）。 */
@Composable
fun AssistantManagerScreen(
    assistants: List<AssistantUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.body,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text("全部助手", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(assistants, key = { it.id }) { assistant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(LuzzyShapes.card)
                        .background(colors.surfaceSoft)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(assistant.initial, style = MaterialTheme.typography.titleMedium, color = colors.body)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(assistant.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                        Text(
                            text = assistant.lastTitle ?: "暂无会话",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted,
                        )
                    }
                    Text(
                        text = assistant.modelLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedSoft,
                    )
                }
            }
        }
    }
}

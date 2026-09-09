package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.AssistantAvatarStrip
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerEmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIconButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerListRow
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerSearchField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 会话列表（DESIGN.md §管理页组件规范）。
 *
 * 入口：LuzzyRP 侧栏「助手 → 会话」。结构：页面头（会话图标 + 新建图标按钮）→ 助手切换条
 * → 检索框 → 会话卡列（按日期分组）。
 */
@Composable
fun ConversationsScreen(
    assistants: List<AssistantUi>,
    selectedAssistant: AssistantUi?,
    conversations: List<ConversationUi>,
    onSelectAssistant: (String) -> Unit,
    onOpenManager: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, conversations) {
        if (query.isBlank()) conversations
        else conversations.filter { it.title.contains(query, true) || it.summary.contains(query, true) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Conversation,
            title = "会话",
            onBack = onBack,
            actions = {
                LedgerStatusPill("${conversations.size} 条")
                LedgerIconButton(
                    icon = LedgerIcons.Plus,
                    contentDescription = "新建会话",
                    onClick = onNewConversation,
                    tint = colors.accentButton,
                )
            },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        LedgerCard {
            AssistantAvatarStrip(
                assistants = assistants,
                selectedId = selectedAssistant?.id.orEmpty(),
                onSelect = onSelectAssistant,
                onOpenManager = onOpenManager,
            )
        }
        Spacer(Modifier.height(Ledger.CardGap))

        LedgerSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索会话…",
        )
        Spacer(Modifier.height(Ledger.CardGap))

        if (filtered.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Conversation,
                title = if (query.isBlank()) "还没有会话" else "没有匹配的会话",
                hint = if (query.isBlank()) "写下第一句，助手会把过程记成手记。" else "换个关键词试试。",
                actionLabel = if (query.isBlank()) "开始新会话" else null,
                onAction = if (query.isBlank()) onNewConversation else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val order = listOf("今天", "昨天", "7 天内", "本月", "更早")
                order.forEach { group ->
                    val rows = grouped[group] ?: return@forEach
                    item(key = "h-$group") {
                        Text(
                            text = group,
                            style = LedgerType.sectionHeading,
                            color = colors.hairlineStrong,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.id }) { conversation ->
                        LedgerCard(padded = false) {
                            LedgerListRow(
                                title = conversation.title,
                                subtitle = conversation.summary,
                                onClick = { onOpenConversation(conversation.id) },
                                trailing = {
                                    Text(
                                        text = conversation.updatedAtLabel,
                                        style = LedgerType.caption,
                                        color = colors.mutedSoft,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

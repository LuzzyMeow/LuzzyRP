package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerEmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 助手管理页（DESIGN.md §管理页组件规范）。
 *
 * 入口：侧栏「助手 → 会话 → 头像条 +」。列出全部助手及其模型/最近会话。
 */
@Composable
fun AssistantManagerScreen(
    assistants: List<AssistantUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Assistants,
            title = "助手管理",
            onBack = onBack,
            actions = { LedgerStatusPill("${assistants.size} 位") },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        if (assistants.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Assistants,
                title = "还没有助手",
                hint = "助手在首次打开时自动创建。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Ledger.CardGap),
            ) {
                items(assistants, key = { it.id }) { assistant ->
                    LedgerCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentSoft),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = assistant.initial,
                                    style = LedgerType.label,
                                    color = colors.accentDeep,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(assistant.name, style = LedgerType.cardTitle, color = colors.ink)
                                Text(
                                    text = assistant.lastTitle ?: "暂无会话",
                                    style = LedgerType.caption,
                                    color = colors.mutedSoft,
                                )
                            }
                            LedgerStatusPill(assistant.modelLabel)
                        }
                    }
                }
            }
        }
    }
}

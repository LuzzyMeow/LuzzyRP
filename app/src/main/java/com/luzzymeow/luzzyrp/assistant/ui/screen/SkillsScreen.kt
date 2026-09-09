package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButtonTone
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerEmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIconButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerSearchField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerToggleRow
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.skill.SkillRow
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 技能页（DESIGN.md §管理页组件规范）。
 *
 * 每项两个开关：**全局**（所有助手注入）与**本助手**（仅当前助手注入）；
 * 正文进系统提示词，`tools:` 仅作提示，不绕过工具开关（PLAN §8.3）。
 */
@Composable
fun SkillsScreen(
    skills: List<SkillRow>,
    message: String?,
    onToggleGlobal: (String, Boolean) -> Unit,
    onToggleBinding: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onImportUrl: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var showUrlDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Skills,
            title = "技能",
            onBack = onBack,
            actions = {
                LedgerButton(
                    text = "链接导入",
                    icon = LedgerIcons.ExternalLink,
                    onClick = { showUrlDialog = true },
                )
            },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        if (message != null) {
            LedgerCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        style = LedgerType.body,
                        color = colors.body,
                        modifier = Modifier.weight(1f),
                    )
                    LedgerButton(text = "关闭", onClick = onDismissMessage)
                }
            }
            Spacer(Modifier.height(Ledger.CardGap))
        }

        if (skills.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Skills,
                title = "还没有技能",
                hint = "技能是给模型的流程说明（Markdown + front-matter），导入后按需启用。",
                actionLabel = "链接导入",
                onAction = { showUrlDialog = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Ledger.CardGap),
            ) {
                items(skills, key = { it.id }) { skill ->
                    SkillCard(skill, onToggleGlobal, onToggleBinding, onDelete)
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = colors.card,
            confirmButton = {
                LedgerButton(
                    text = "导入",
                    tone = LedgerButtonTone.Primary,
                    onClick = {
                        onImportUrl(url)
                        showUrlDialog = false
                    },
                )
            },
            dismissButton = { LedgerButton(text = "取消", onClick = { showUrlDialog = false }) },
            title = { Text("从链接导入技能", style = LedgerType.cardTitle, color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "仅支持 http/https；需为 Markdown（可含 front-matter）",
                        style = LedgerType.caption,
                        color = colors.mutedSoft,
                    )
                    LedgerSearchField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = "https://example.com/skill.md",
                    )
                }
            },
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillRow,
    onToggleGlobal: (String, Boolean) -> Unit,
    onToggleBinding: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LuzzyTheme.colors
    LedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = skill.name,
                style = LedgerType.cardTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            LedgerStatusPill(
                text = skill.sourceLabel,
                tone = LedgerButtonTone.Primary,
            )
            Spacer(Modifier.size(8.dp))
            LedgerIconButton(
                icon = LedgerIcons.Trash,
                contentDescription = "删除技能",
                onClick = { onDelete(skill.id) },
                tint = colors.error,
            )
        }
        Text(text = skill.description, style = LedgerType.body, color = colors.muted)
        Spacer(Modifier.height(4.dp))
        LedgerToggleRow(
            label = "全局启用",
            hint = "所有助手都会注入这条技能",
            checked = skill.enabledGlobal,
            onCheckedChange = { onToggleGlobal(skill.id, it) },
        )
        LedgerToggleRow(
            label = "本助手启用",
            hint = "仅当前助手注入",
            checked = skill.boundToAssistant,
            onCheckedChange = { onToggleBinding(skill.id, it) },
        )
    }
}

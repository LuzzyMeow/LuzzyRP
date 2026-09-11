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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButtonTone
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerEmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIconButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme
import com.luzzymeow.luzzyrp.assistant.ui.workspace.FileRow
import com.luzzymeow.luzzyrp.assistant.ui.workspace.Preview

/**
 * 工作区页（DESIGN.md §管理页组件规范）。
 *
 * 路径条 + 配额徽标 → 文件列表（`LedgerListRow`）→ 预览卡（等宽正文 + 关闭）。
 */
@Composable
fun WorkspaceScreen(
    currentPath: String,
    entries: List<FileRow>,
    usageLabel: String,
    preview: Preview?,
    message: String?,
    onEnter: (String) -> Unit,
    onUp: () -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClosePreview: () -> Unit,
    onDismissMessage: () -> Unit,
    onMenu: () -> Unit,
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
            icon = LedgerIcons.Workspace,
            title = "工作区",
            onMenu = onMenu,
            actions = {
                LedgerStatusPill(text = usageLabel)
                LedgerIconButton(
                    icon = LedgerIcons.ChevronLeft,
                    contentDescription = "返回上级",
                    onClick = onUp,
                )
            },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        LedgerCard {
            Text(
                text = currentPath.ifBlank { "files/" },
                style = LedgerType.caption,
                fontFamily = FontFamily.Monospace,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Ledger.CardGap))

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

        preview?.let { current ->
            LedgerCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = current.relativePath,
                        style = LedgerType.cardTitle,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LedgerButton(text = "关闭预览", onClick = onClosePreview)
                }
                Text(
                    text = current.text,
                    style = LedgerType.caption,
                    fontFamily = FontFamily.Monospace,
                    color = colors.body,
                )
            }
            Spacer(Modifier.height(Ledger.CardGap))
        }

        if (entries.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Workspace,
                title = "这里是空的",
                hint = "助手写入的文件会出现在这里（工作区 = 该助手的 files/ 目录）。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.relativePath }) { entry ->
                    LedgerCard(padded = false) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerListRow(
                                title = entry.name,
                                subtitle = if (entry.isDirectory) "目录" else entry.sizeLabel,
                                onClick = { if (entry.isDirectory) onEnter(entry.relativePath) else onPreview(entry.relativePath) },
                                modifier = Modifier.weight(1f),
                                leading = {
                                    LedgerStatusPill(
                                        text = if (entry.isDirectory) "目录" else "文件",
                                        tone = if (entry.isDirectory) LedgerButtonTone.Primary else LedgerButtonTone.Secondary,
                                    )
                                },
                            )
                            LedgerIconButton(
                                icon = LedgerIcons.Trash,
                                contentDescription = "删除",
                                onClick = { onDelete(entry.relativePath) },
                                tint = colors.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

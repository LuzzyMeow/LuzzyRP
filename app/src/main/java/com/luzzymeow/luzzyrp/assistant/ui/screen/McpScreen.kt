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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerTextField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerToggleRow
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.mcp.McpRow
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * MCP 页（DESIGN.md §管理页组件规范）。
 *
 * 导入区（JSON 文本域 + 导入按钮）→ 服务器卡列（名称/传输/地址 + 全局开关 + 连接/删除）。
 */
@Composable
fun McpScreen(
    servers: List<McpRow>,
    message: String?,
    onImport: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onConnect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Mcp,
            title = "MCP",
            onBack = onBack,
            actions = { LedgerStatusPill("${servers.size} 个服务器") },
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

        LedgerCard {
            Text(
                text = "粘贴 MCP 配置 JSON（支持 mcpServers 映射或单服务器对象）",
                style = LedgerType.caption,
                color = colors.mutedSoft,
            )
            LedgerTextField(
                value = draft,
                onValueChange = { draft = it },
                minHeight = 96.dp,
                placeholder = "{\"mcpServers\": {\"demo\": {\"url\": \"https://…\"}}}",
            )
            Row {
                LedgerButton(
                    text = "导入",
                    icon = LedgerIcons.Plus,
                    tone = LedgerButtonTone.Primary,
                    enabled = draft.isNotBlank(),
                    onClick = {
                        onImport(draft)
                        draft = ""
                    },
                )
            }
        }
        Spacer(Modifier.height(Ledger.CardGap))

        if (servers.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Mcp,
                title = "还没有 MCP 服务器",
                hint = "导入 JSON 后可连接远端工具（HTTP / Streamable HTTP / SSE）。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Ledger.CardGap),
            ) {
                items(servers, key = { it.id }) { server ->
                    ServerCard(server, onToggle, onConnect, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: McpRow,
    onToggle: (String, Boolean) -> Unit,
    onConnect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LuzzyTheme.colors
    LedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = server.name,
                style = LedgerType.cardTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LedgerStatusPill(
                text = if (server.connected) "已连接" else "未连接",
                tone = if (server.connected) LedgerButtonTone.Primary else LedgerButtonTone.Secondary,
            )
            Spacer(Modifier.size(8.dp))
            LedgerIconButton(
                icon = LedgerIcons.Trash,
                contentDescription = "删除服务器",
                onClick = { onDelete(server.id) },
                tint = colors.error,
            )
        }
        Text(
            text = "${server.transportLabel} · ${server.url}",
            style = LedgerType.caption,
            fontFamily = FontFamily.Monospace,
            color = colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        server.lastError?.let { error ->
            Text(text = error, style = LedgerType.caption, color = colors.error)
        }
        LedgerToggleRow(
            label = "全局启用",
            hint = "所有助手可用（T2：逐调用审批）",
            checked = server.enabledGlobal,
            onCheckedChange = { onToggle(server.id, it) },
        )
        Row { LedgerButton(text = "连接", icon = LedgerIcons.Refresh, onClick = { onConnect(server.id) }) }
    }
}

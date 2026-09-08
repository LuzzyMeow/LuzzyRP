package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.mcp.McpRow
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * MCP 页（方向 A 抽屉二级）。
 *
 * 导入：粘贴 JSON（两种格式自动识别）→ 列表出现 → 开启全局启用即握手连接并注册工具。
 * 工具按 **T2 外部工具**处理：默认关闭 + 逐调用审批（PLAN §9.4）。
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

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(title = "MCP", subtitle = "共 ${servers.size} 个服务器", onBack = onBack)

        if (message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceCard)
                    .clickable(onClick = onDismissMessage)
                    .padding(12.dp),
            ) {
                Text(message, style = MaterialTheme.typography.labelMedium, color = colors.body)
            }
            Spacer(Modifier.size(8.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceSoft)
                    .border(1.dp, colors.hairline, LuzzyShapes.card)
                    .padding(12.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "粘贴 MCP 配置 JSON（支持 mcpServers 映射或单服务器对象）",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedSoft,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(
                        color = colors.body,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    ),
                    cursorBrush = SolidColor(colors.accentButton),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .clip(LuzzyShapes.button)
                    .background(colors.accentButton)
                    .clickable {
                        onImport(draft)
                        draft = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("导入", style = MaterialTheme.typography.labelMedium, color = colors.canvas)
            }
            Spacer(Modifier.size(12.dp))
        }

        if (servers.isEmpty()) {
            EmptyState(
                title = "还没有 MCP 服务器",
                hint = "导入 JSON 后可连接远端工具（HTTP / Streamable HTTP / SSE）。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(servers, key = { it.id }) { row -> McpCard(row, onToggle, onConnect, onDelete) }
            }
        }
    }
}

@Composable
private fun McpCard(
    row: McpRow,
    onToggle: (String, Boolean) -> Unit,
    onConnect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceCard)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(row.transportLabel, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }
        }
        Text(
            text = row.url,
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.lastError != null) {
            Text(row.lastError, style = MaterialTheme.typography.labelMedium, color = colors.error, maxLines = 2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (row.connected) "已连接" else "未连接",
                style = MaterialTheme.typography.labelMedium,
                color = if (row.connected) colors.success else colors.mutedSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "重连",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentButton,
                modifier = Modifier.clickable { onConnect(row.id) }.padding(end = 12.dp),
            )
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = colors.error,
                modifier = Modifier.clickable { onDelete(row.id) }.padding(end = 12.dp),
            )
            Switch(
                checked = row.enabledGlobal,
                onCheckedChange = { onToggle(row.id, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.canvas,
                    checkedTrackColor = colors.accentButton,
                    uncheckedThumbColor = colors.mutedSoft,
                    uncheckedTrackColor = colors.hairline,
                ),
            )
        }
    }
}

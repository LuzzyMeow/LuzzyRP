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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme
import com.luzzymeow.luzzyrp.assistant.ui.workspace.FileRow
import com.luzzymeow.luzzyrp.assistant.ui.workspace.Preview

/**
 * 工作区页（方向 A 抽屉二级，PLAN §10.1）。
 *
 * 只展示 `files/`（Agent 读写区）；面包屑可回上级；点文件看预览（截断 8KB）；
 * 右上角显示配额用量（默认 2GB）。
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(
            title = "工作区",
            subtitle = "$currentPath · $usageLabel",
            onBack = onBack,
            action = {
                if (currentPath != "files") {
                    Text(
                        text = "↑ 上级",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentButton,
                        modifier = Modifier.clickable(onClick = onUp).padding(8.dp),
                    )
                }
            },
        )

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

        if (preview != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceSoft)
                    .border(1.dp, colors.hairline, LuzzyShapes.card)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preview.relativePath,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentDeep,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                        modifier = Modifier.clickable(onClick = onClosePreview),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = preview.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.body,
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = "工作区是空的",
                hint = "助手写入的文件（报告、导出、脚本）会出现在这里。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.relativePath }) { row -> FileCard(row, onEnter, onPreview, onDelete) }
            }
        }
    }
}

@Composable
private fun FileCard(
    row: FileRow,
    onEnter: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
            .clickable { if (row.isDirectory) onEnter(row.relativePath) else onPreview(row.relativePath) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (row.isDirectory) "▸" else "·",
            style = MaterialTheme.typography.titleMedium,
            color = colors.accentGraphic,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.relativePath,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(row.sizeLabel, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Text(
            text = "删除",
            style = MaterialTheme.typography.labelMedium,
            color = colors.error,
            modifier = Modifier.clickable { onDelete(row.relativePath) }.padding(start = 8.dp),
        )
    }
}

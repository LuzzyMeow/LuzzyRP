package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.MemoryCard
import com.luzzymeow.luzzyrp.assistant.ui.component.MemoryModeBar
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.SearchField
import com.luzzymeow.luzzyrp.assistant.ui.model.MemoryUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 记忆页（方向 A：抽屉二级）。
 *
 * 模式条 + 检索框 + 记忆卡列；单屏约 4 条（含模式条与检索框）。
 */
@Composable
fun MemoryScreen(
    memories: List<MemoryUi>,
    modeLabel: String,
    topK: Int,
    threshold: Float,
    recent: Int,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MemoryUi?>(null) }
    val filtered = remember(query, memories) {
        if (query.isBlank()) memories else memories.filter { it.content.contains(query, true) }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(
            title = "记忆",
            subtitle = "共 ${memories.size} 条",
            onBack = onBack,
            action = {
                Text(
                    text = "＋ 添加",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentButton,
                    modifier = Modifier.clickable { showAdd = true }.padding(8.dp),
                )
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemoryModeBar(modeLabel = modeLabel, topK = topK, threshold = threshold, recent = recent)
            SearchField(value = query, onValueChange = { query = it }, placeholder = "搜索记忆…")
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "还没有记忆" else "没有匹配的记忆",
                hint = if (query.isBlank()) "助手在对话中写下的要点会出现在这里。" else "换个关键词试试。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { memory ->
                    Box {
                        MemoryCard(memory)
                        // 长按删除（用点击代理简化：右下角「删除」文字按钮）
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable { pendingDelete = memory }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        var draft by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("fact") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                Text(
                    text = "保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentButton,
                    modifier = Modifier.clickable {
                        onAdd(draft, type)
                        showAdd = false
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.clickable { showAdd = false }.padding(8.dp),
                )
            },
            title = { Text("添加记忆", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(value = draft, onValueChange = { draft = it }, placeholder = "一句话说清这条记忆…")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("fact" to "事实", "preference" to "偏好", "task" to "任务", "note" to "笔记")
                            .forEach { (id, label) ->
                                val selected = type == id
                                Box(
                                    modifier = Modifier
                                        .clip(LuzzyShapes.pill)
                                        .background(if (selected) colors.accentSoft else colors.surfaceSoft)
                                        .clickable { type = id }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) colors.accentDeep else colors.muted,
                                    )
                                }
                            }
                    }
                }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.error,
                    modifier = Modifier.clickable {
                        onDelete(target.id)
                        pendingDelete = null
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.clickable { pendingDelete = null }.padding(8.dp),
                )
            },
            title = { Text("删除这条记忆？", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
            text = { Text(target.content, style = MaterialTheme.typography.bodyMedium, color = colors.body) },
        )
    }
}

/**
 * 管理页占位（技能 / MCP / 工作区 / 终端 / 设置）。
 *
 * 方向 A 的抽屉六项在 P0 全部可达；未实现项给「规划中」占位而非空白页
 * （避免用户以为功能坏了）。P2/P3 按 PLAN §8/§9/§10/§11 逐项替换。
 */
@Composable
fun PlaceholderScreen(
    title: String,
    note: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(title = title, onBack = onBack)
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        EmptyState(title = "规划中", hint = "该页将在 v1.5.0 后续阶段接入。")
    }
}

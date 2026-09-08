package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.MemoryCard
import com.luzzymeow.luzzyrp.assistant.ui.component.MemoryModeBar
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.SearchField
import com.luzzymeow.luzzyrp.assistant.ui.model.MemoryUi
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
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, memories) {
        if (query.isBlank()) memories else memories.filter { it.content.contains(query, true) }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(title = "记忆", subtitle = "共 ${memories.size} 条", onBack = onBack)
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
                items(filtered, key = { it.id }) { memory -> MemoryCard(memory) }
            }
        }
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

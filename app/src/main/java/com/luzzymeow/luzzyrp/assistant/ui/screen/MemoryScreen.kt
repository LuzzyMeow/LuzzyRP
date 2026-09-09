package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButtonTone
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCollapseCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerEmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIconButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import androidx.compose.foundation.layout.RowScope
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerSearchField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.model.MemoryUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 记忆页（DESIGN.md §管理页组件规范）。
 *
 * 结构：`LedgerPageHeader`（灯泡图标 + 「记忆」+ 右侧添加图标按钮）→ 检索框 →
 * `LedgerCollapseCard`「记忆引擎设置」→ 记忆卡列。
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
    var showEngine by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MemoryUi?>(null) }
    val filtered = remember(query, memories) {
        if (query.isBlank()) memories else memories.filter { it.content.contains(query, true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Memory,
            title = "记忆",
            onBack = onBack,
            actions = {
                LedgerIconButton(
                    icon = LedgerIcons.Plus,
                    contentDescription = "添加记忆",
                    onClick = { showAdd = true },
                    tint = colors.accentButton,
                )
            },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        LedgerSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索记忆…",
        )
        Spacer(Modifier.height(Ledger.CardGap))

        LedgerCollapseCard(
            icon = LedgerIcons.Settings,
            title = "记忆引擎设置",
            expanded = showEngine,
            onToggle = { showEngine = !showEngine },
            statusText = modeLabel,
        ) {
            InfoRow("检索模式", modeLabel)
            InfoRow("召回条数 TopK", "$topK")
            InfoRow("相似度阈值", String.format("%.2f", threshold))
            InfoRow("最近对话条数", "$recent")
        }
        Spacer(Modifier.height(Ledger.CardGap))

        if (filtered.isEmpty()) {
            LedgerEmptyState(
                icon = LedgerIcons.Memory,
                title = if (query.isBlank()) "还没有记忆" else "没有匹配的记忆",
                hint = if (query.isBlank()) "助手在对话中写下的要点会出现在这里。" else "换个关键词试试。",
                actionLabel = if (query.isBlank()) "添加一条" else null,
                onAction = if (query.isBlank()) ({ showAdd = true }) else null,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Ledger.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Ledger.CardGap),
            ) {
                items(filtered, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onDelete = { pendingDelete = memory },
                    )
                }
            }
        }
    }

    if (showAdd) {
        var draft by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("fact") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = colors.card,
            confirmButton = {
                LedgerButton(
                    text = "保存",
                    tone = LedgerButtonTone.Primary,
                    onClick = {
                        onAdd(draft, type)
                        showAdd = false
                    },
                )
            },
            dismissButton = {
                LedgerButton(text = "取消", onClick = { showAdd = false })
            },
            title = { Text("添加记忆", style = LedgerType.cardTitle, color = colors.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LedgerSearchField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "一句话说清这条记忆…",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("fact" to "事实", "preference" to "偏好", "task" to "任务", "note" to "笔记")
                            .forEach { (id, label) ->
                                val selected = type == id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Ledger.RadiusPill))
                                        .background(if (selected) colors.accentSoft else colors.surfaceSoft)
                                        .clickable { type = id }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = LedgerType.caption,
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
            containerColor = colors.card,
            confirmButton = {
                LedgerButton(
                    text = "删除",
                    tone = LedgerButtonTone.Danger,
                    onClick = {
                        onDelete(target.id)
                        pendingDelete = null
                    },
                )
            },
            dismissButton = { LedgerButton(text = "取消", onClick = { pendingDelete = null }) },
            title = { Text("删除这条记忆？", style = LedgerType.cardTitle, color = colors.ink) },
            text = { Text(target.content, style = LedgerType.body, color = colors.body) },
        )
    }
}

/** 键值信息行（上游 `flex justify-between` + `text-sm`/`text-xs`）。 */
@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = LedgerType.body, color = colors.muted, modifier = Modifier.weight(1f))
        LedgerStatusPill(text = value)
    }
}

/**
 * 记忆卡（上游卡片结构：`bg-white rounded-2xl border shadow-sm` + 类型徽标 + 正文 + meta）。
 */
@Composable
private fun MemoryCard(
    memory: MemoryUi,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    LedgerCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LedgerStatusPill(text = memory.typeLabel, tone = LedgerButtonTone.Primary)
            Spacer(Modifier.weight(1f))
            LedgerIconButton(
                icon = LedgerIcons.Trash,
                contentDescription = "删除",
                onClick = onDelete,
                tint = colors.error,
            )
        }
        Text(text = memory.content, style = LedgerType.body, color = colors.body)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = memory.sourceLabel, style = LedgerType.caption, color = colors.mutedSoft)
            Spacer(Modifier.weight(1f))
            memory.similarity?.let { sim ->
                Text(
                    text = String.format("%.2f", sim),
                    style = LedgerType.caption,
                    color = colors.accentDeep,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sim.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(colors.accentGraphic),
                    )
                }
            }
        }
    }
}

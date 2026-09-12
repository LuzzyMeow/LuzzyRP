package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.chat.BranchStat
import com.luzzymeow.luzzyrp.chat.BranchTree
import com.luzzymeow.luzzyrp.chat.ChatBranch
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 剧情分支列表（DESIGN-compose §17）——**我们自己的「会话列表」语义**。
 *
 * 对应上游 `ui-components.js` 的 `StoryBranchModal`（挂在聊天页顶栏同一位置）：
 * 一次「会话」= 角色 × 剧情分支，分支自某一楼分叉、各自独立延续。
 * 上游用 SVG 路线图画树；本版用**层级缩进 + 连接线**表达同一结构
 * （Compose 里画 SVG 路线图成本高、且缩进对读屏更友好），信息字段与上游一致：
 * 分支名 / 楼数 / 字数 / 起点 / 当前。
 *
 * 信息密度（huashu：AI·上下文感知产品走高密度型）：楼数、字数、分叉来源、起点/当前
 * 全是**真实算出来的**差异信息，没有任何装饰图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchListSheet(
    tree: BranchTree,
    stats: Map<String, BranchStat>,
    onSwitch: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf<ChatBranch?>(null) }
    var deleting by remember { mutableStateOf<ChatBranch?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painter = painterResource(LuzzyIcons.Branch),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "剧情分支",
                    fontFamily = LuzzyFonts.Lora,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${tree.branches.size} 个分支",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "每条分支从某一楼分叉后独立延续；在这里发送的消息只进当前分支。",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )

            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tree.sorted().forEach { branch ->
                    val depth = tree.depthOf(branch.id)
                    BranchRow(
                        branch = branch,
                        stat = stats[branch.id] ?: BranchStat.Empty,
                        depth = depth,
                        active = branch.id == tree.activeId,
                        onSwitch = { onSwitch(branch.id) },
                        onRename = { renaming = branch },
                        onDelete = { deleting = branch },
                    )
                }
            }
        }
    }

    renaming?.let { branch ->
        RenameDialog(
            branch = branch,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRename(branch.id, name)
                renaming = null
            },
        )
    }

    deleting?.let { branch ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分支", fontFamily = LuzzyFonts.Body, fontSize = 17.sp) },
            text = {
                Text(
                    text = "「${branch.name}」及其后代的全部楼层会被删除，且不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(branch.id)
                    deleting = null
                }) {
                    Text("删除", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消", fontFamily = LuzzyFonts.Body) }
            },
        )
    }
}

/**
 * 单个分支行：层级缩进 + 连接线 + 分支名 + 真实统计 + 状态标识 + 动作。
 *
 * 触控目标（pro-rules：Android ≥48dp）：动作按钮一律 48dp 热区，图标 18dp。
 */
@Composable
private fun BranchRow(
    branch: ChatBranch,
    stat: BranchStat,
    depth: Int,
    active: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val indent = (depth * 16).dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp),
            )
            // 树形连接线画在卡片内部左侧留白处：竖脊贯穿本行 + 肘部指向内容。
            // （画在卡片外会被圆角裁掉，且贴边 2dp 太细看不见——实测踩过。）
            .drawBehind {
                if (depth > 0) {
                    val x = 9.dp.toPx()
                    val stroke = 1.5.dp.toPx()
                    val line = Color(0xFFBEB6A8).copy(alpha = 0.85f)
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                    drawLine(line, Offset(x, size.height / 2f), Offset(19.dp.toPx(), size.height / 2f), stroke)
                }
            }
            .padding(
                start = if (depth > 0) 24.dp else 12.dp,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = branch.name,
                    fontSize = 14.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (branch.isMain) BadgeChip("起点", MaterialTheme.colorScheme.tertiary)
                if (active) BadgeChip("当前", MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "${stat.floorLabel} · ${stat.wordCountLabel}",
                fontSize = 11.5.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 分叉来源单独成行：与统计挤在同一行时会被动作按钮压到换行（实测）
            branch.forkFloor?.let { floor ->
                Text(
                    text = "自${if (branch.parentId == ChatBranch.MainId) "主线" else "上级"}第 $floor 楼分出",
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (!active) {
            TextButton(onClick = onSwitch, modifier = Modifier.size(width = 64.dp, height = 48.dp)) {
                Text("进入", fontFamily = LuzzyFonts.Body, fontSize = 13.sp)
            }
        }
        if (!branch.isMain) {
            IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(LuzzyIcons.Edit),
                    contentDescription = "重命名「${branch.name}」",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(LuzzyIcons.Trash),
                    contentDescription = "删除「${branch.name}」",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 重命名（上限 30 字，与上游 `ui-components.js` 的编辑弹窗同值）。 */
@Composable
private fun RenameDialog(
    branch: ChatBranch,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(branch.name) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名分支", fontFamily = LuzzyFonts.Body, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ChatBranch.MaxNameLength) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${name.length}/${ChatBranch.MaxNameLength}",
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text("保存", fontFamily = LuzzyFonts.Body) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontFamily = LuzzyFonts.Body) }
        },
    )
}

package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.Motion

/**
 * 思考节点（DESIGN-compose §14；形态对齐 rikkahub）。
 *
 * 考察来源 `docs/rikkahub-master/.../ui/components/message/`（ChainOfThought.kt /
 * ChatMessageCot.kt / ChatMessageTools.kt / ChatMessageReasoning.kt / ChatMessageActions.kt）。
 *
 * **形态（2026-09-12 用户指示修订）**：
 * ① 节点卡位于**模型输出气泡内部**（同一玻璃面板，不再是气泡上方的独立卡）；
 * ② 子节点内容过长时**卡内滚动**（[NodeBodyMaxHeight]），不无限延伸；
 * ③ 逐节点时序：**自动展开 → 内容流式 → 完成后自动收起**（由 [ThinkingCard.activeIndex] 驱动）。
 *
 * **语义差异**：rikkahub 无「记忆注入」节点（其记忆是模型主动写记忆的工具调用），
 * 本实现按 LuzzyRP 自有语义（WebView 版 patch 016/031 的记忆召回）建模。
 */

/** 展开的子节点内容最大高度（超出改为卡内滚动，见 §14.3）。 */
private val NodeBodyMaxHeight = 200.dp

/** 思考节点类型（三类）。 */
sealed interface ThinkNode {
    val label: String
    val extra: String?

    /** ① 工具调用：入参（流式到达）+ 真实执行结果。 */
    data class Tool(
        val toolName: String,
        val args: String,
        val result: String?,
        val done: Boolean = true,
        override val extra: String? = null,
    ) : ThinkNode {
        override val label: String get() = "调用工具 $toolName"
    }

    /** ② 记忆召回（会话检索；LuzzyRP 自有语义）。 */
    data class MemoryRecall(
        val shards: List<MemoryShard>,
        val range: String,
    ) : ThinkNode {
        override val label: String get() = "记忆召回 · ${shards.size} 片"
        override val extra: String? get() = range
    }

    /** ③ 头脑风暴（模型 reasoning 字段，真实逐字流式）。 */
    data class Brainstorm(
        val text: String,
        val seconds: Double,
        val streaming: Boolean,
    ) : ThinkNode {
        override val label: String
            get() = if (streaming) "思考中…" else "思考了 %.1f 秒".format(seconds)
        override val extra: String?
            get() = if (streaming) null else "%.1fs".format(seconds)
    }
}

/** 记忆分片（召回节点展开内容）。 */
data class MemoryShard(val turn: String, val score: String, val text: String)

/** 点呼吸（live 态图标槽；对齐 rikkahub DotLoading）。 */
@Composable
fun DotLoading(sizeDp: Int = 10) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
    )
}

private fun thinkIconRes(node: ThinkNode): Int = when (node) {
    is ThinkNode.Tool -> LuzzyIcons.Sliders
    is ThinkNode.MemoryRecall -> LuzzyIcons.Memory
    is ThinkNode.Brainstorm -> LuzzyIcons.Info
}

/** 时间线单步：图标槽（穿线）+ label + extra + 展开指示 + 展开内容（可滚动）。 */
@Composable
private fun ThinkNodeRow(
    node: ThinkNode,
    live: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: (@Composable () -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = content != null, onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 图标槽 24dp（20dp 卡底色圆遮住时间线竖线）
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                )
                if (live) {
                    DotLoading(10)
                } else {
                    Icon(
                        painter = painterResource(thinkIconRes(node)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = node.label,
                fontSize = 13.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            node.extra?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (content != null) {
                Icon(
                    painter = painterResource(LuzzyIcons.ChevronDown),
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
        // 展开/收起 = 高度 + 淡入淡出（§14.3③「完成后自动收起」的可见动效）
        AnimatedVisibility(
            visible = expanded && content != null,
            enter = expandVertically(animationSpec = tween(Motion.EnterMs)) + fadeIn(tween(Motion.EnterMs)),
            exit = shrinkVertically(animationSpec = tween(Motion.ExitMs)) + fadeOut(tween(Motion.ExitMs)),
        ) {
            Box(
                Modifier
                    .padding(start = 32.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    // 过长内容卡内滚动，不无限延伸（§14.3②）
                    .heightIn(max = NodeBodyMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) { content?.invoke() }
        }
    }
}

/** 展开内容（按节点类型）。 */
@Composable
private fun ThinkNodeBody(node: ThinkNode) {
    when (node) {
        is ThinkNode.Tool -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "入参",
                fontSize = 10.sp, fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = node.args.ifEmpty { "…" },
                fontSize = 10.sp, lineHeight = 15.sp,
                fontFamily = LuzzyFonts.Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            node.result?.let { r ->
                Text(
                    text = "结果",
                    fontSize = 10.sp, fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = r,
                    fontSize = 11.sp, lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ThinkNode.MemoryRecall -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.shards.forEach { sh ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeChip(sh.turn, MaterialTheme.colorScheme.primary)
                        BadgeChip(sh.score, MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        text = sh.text,
                        fontSize = 12.sp, lineHeight = 18.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ThinkNode.Brainstorm -> Text(
            text = node.text,
            fontSize = 12.sp, lineHeight = 18.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 思考卡（§14.1）：**气泡内**的子卡（非独立玻璃面板）。
 *
 * 折叠态 = 一行摘要（点 + 摘要 + 「再显示 N 步」+ chevron）；展开态 = 时间线节点列表。
 * [activeIndex] 为「当前正在产出内容的节点」下标——该节点自动展开，其内容流式写入；
 * 下标移走即自动收起（-1 = 全部收起）。用户手动点击的展开状态优先于自动态。
 */
@Composable
fun ThinkingCard(
    nodes: List<ThinkNode>,
    isLive: Boolean,
    activeIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    var cardOverride by remember { mutableStateOf<Boolean?>(null) }
    val expanded = cardOverride ?: isLive
    val opens = remember { mutableStateMapOf<Int, Boolean>() }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Motion.EnterMs),
        label = "cotChevron",
    )

    val summary = remember(nodes, isLive) {
        if (isLive) "思考中…"
        else {
            val head = nodes.firstOrNull()?.let {
                when (it) {
                    is ThinkNode.Brainstorm -> "思考了 %.1f 秒".format(it.seconds)
                    is ThinkNode.MemoryRecall -> "记忆召回"
                    is ThinkNode.Tool -> "工具调用"
                }
            } ?: "思考"
            if (nodes.size > 1) "$head · ${nodes.size} 步" else head
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
            .border(
                width = if (isLive) 1.5.dp else 1.dp,
                color = if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { cardOverride = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLive) DotLoading(10)
            else Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
            )
            Text(
                text = summary,
                fontSize = 12.5.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "再显示 ${nodes.size} 步",
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = painterResource(LuzzyIcons.ChevronDown),
                contentDescription = if (expanded) "收起思考" else "展开思考",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(Motion.EnterMs)) + fadeIn(tween(Motion.EnterMs)),
            exit = shrinkVertically(animationSpec = tween(Motion.ExitMs)) + fadeOut(tween(Motion.ExitMs)),
        ) {
            Column(
                Modifier.drawBehind {
                    val x = 12.dp.toPx()
                    drawLine(
                        color = Color(0xFFBEB6A8).copy(alpha = 0.35f),
                        start = Offset(x, 18.dp.toPx()),
                        end = Offset(x, size.height - 18.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            ) {
                nodes.forEachIndexed { i, node ->
                    val hasBody = when (node) {
                        is ThinkNode.Brainstorm -> node.text.isNotBlank()
                        is ThinkNode.MemoryRecall -> node.shards.isNotEmpty()
                        is ThinkNode.Tool -> true
                    }
                    // 自动态：正在产出内容的节点展开；用户点过的以用户为准
                    val isOpen = opens[i] ?: (i == activeIndex)
                    ThinkNodeRow(
                        node = node,
                        live = isLive && i == activeIndex,
                        expanded = isOpen,
                        onToggle = { opens[i] = !isOpen },
                        content = if (hasBody) {
                            { ThinkNodeBody(node) }
                        } else null,
                    )
                }
            }
        }
    }
}

/**
 * 消息操作行（§14.2）：复制 / 重新生成 / 编辑 / 更多 / **多结果切换**（`‹ n/m ›`）。
 * 位于气泡正下方；图标 18dp + onSurfaceVariant，**热区 48dp**（pro-rules：Android 触控目标
 * 下限 48dp；此前 32dp 不合规，交付前清单核对时改掉）。FlowRow 保证 4 图标 + 切换器
 * 在 336dp 气泡宽度下自动换行。
 *
 * 全部为**真实现**（2026-09-12 用户要求「做实质功能」）：
 * 复制走系统剪贴板、编辑开就地编辑弹窗、重新生成真实再跑一次请求并累积候选、
 * 更多菜单提供「复制 Markdown 源码 / 删除此消息 / 删除此消息及之后」。
 * [onRegenerate] 为 null 表示该消息不可重新生成（如用户消息），此时该项不出现。
 */
@Composable
fun MessageActionRow(
    branchIndex: Int = 0,
    branchCount: Int = 1,
    onBranchChange: (Int) -> Unit = {},
    onCopy: () -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDeleteAfter: () -> Unit = {},
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(
            2.dp,
            alignment = if (alignEnd) Alignment.End else Alignment.Start,
        ),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(LuzzyIcons.Copy, "复制", onCopy),
            Triple(LuzzyIcons.Refresh, "重新生成", onRegenerate),
            Triple(LuzzyIcons.Edit, "编辑", onEdit),
            Triple(LuzzyIcons.DotsHorizontal, "更多", { menuOpen = true }),
        ).forEach { (res, desc, action) ->
            if (action == null) return@forEach
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = action),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(res),
                        contentDescription = desc,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen && res == LuzzyIcons.DotsHorizontal, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("复制 Markdown 源码", fontFamily = LuzzyFonts.Body, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(LuzzyIcons.Copy),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除此消息", fontFamily = LuzzyFonts.Body, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(LuzzyIcons.Trash),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除此消息及之后", fontFamily = LuzzyFonts.Body, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(LuzzyIcons.ChevronRight),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDeleteAfter()
                        },
                    )
                }
            }
        }
        if (branchCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(
                    painter = painterResource(LuzzyIcons.ChevronLeft),
                    contentDescription = "上一个结果",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (branchIndex <= 0) 0.5f else 1f),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = branchIndex > 0) { onBranchChange(branchIndex - 1) },
                )
                Text(
                    text = "${branchIndex + 1}/$branchCount",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(LuzzyIcons.ChevronRight),
                    contentDescription = "下一个结果",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (branchIndex >= branchCount - 1) 0.5f else 1f),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = branchIndex < branchCount - 1) {
                            onBranchChange(branchIndex + 1)
                        },
                )
            }
        }
    }
}

/** 就地编辑弹窗（用户消息/AI 消息共用；纯文本编辑，Markdown 源码即所见）。 */
@Composable
fun EditMessageDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LuzzyFonts.Body, fontSize = 17.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = LuzzyFonts.Body,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && text != initial,
            ) { Text("保存", fontFamily = LuzzyFonts.Body) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontFamily = LuzzyFonts.Body) }
        },
    )
}

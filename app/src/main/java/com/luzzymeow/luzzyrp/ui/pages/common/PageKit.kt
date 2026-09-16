package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.loomSpring

/**
 * 页面通用组件（Loom v4 重皮；**对外 API 不变**——所有调用点零改动即换新皮肤）。
 *
 * 旧实现（P1 静态稿）与 Loom v4 的映射：
 * - [PageScaffold] → LoomScaffold 薄壳（大标题头 + 织纹画布）
 * - [SettingCard] → LoomCard(tier=Card)
 * - [EntryCard] → LoomCard + LoomSwitch + LoomOverflowMenu
 * - [LuzzySwitch] → Loom 开关（spring 动画）
 * - [EmptyState] → LoomEmpty
 * - [BadgeChip] → LoomBadge
 */
// （v3.1 Loom v4：本文件从「P1 静态稿组件」整体换皮；命名对齐上游 settings-page-header 语义的历史注释保留在各组件内。）

/**
 * 非沉浸页的统一骨架：`LoomScaffold`（织纹画布 + 大标题头）+ 内容槽。
 *
 * v3.1：内部换成 LoomScaffold（织机画布 + 大标题 + 汉堡钮），对外签名不变。
 */
@Composable
fun PageScaffold(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    LoomScaffold(
        title = title,
        iconRes = iconRes,
        onOpenDrawer = onOpenDrawer,
        accent = accent,
        headerActions = { actions() },
        content = content,
    )
}

/** 页头（LoomScaffold 内建，本函数保留为兼容别名）。
 * 新代码直接用 [PageScaffold] / [LoomScaffold]。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    // 兼容壳：LoreBookEdit/WorldEntryEdit 等页头仍指向这里（无画布语境下的顶栏）
    androidx.compose.material3.TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        navigationIcon = {
            IconButtonCompat(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(LuzzyIcons.Menu),
                    contentDescription = "打开菜单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = { actions() },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun IconButtonCompat(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, content = content)
}

/** 分组标题（12sp uppercase 字距；v3.1 换 primary 色与字距）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    LoomSectionLabel(text = text, modifier = modifier)
}

/** 设置卡容器（LoomCard Card 层 + hairline 边 + 18dp 圆角）。 */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    LoomCard(modifier = modifier, tier = LoomTier.Card, content = content)
}

/** 卡内行：leading 图标 + 标题 + 支撑文本 + trailing 槽。 */
@Composable
fun SettingRow(
    title: String,
    supporting: String? = null,
    leadingIconRes: Int? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = com.luzzymeow.luzzyrp.ui.rememberReduceMotion()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            // onClick 参数此前只是声明了、从未接上（参数存在但 Row 不可点）——D3 的迁移报告行
            // 是第一个真实调用方，一跑就暴露。修在组件层：一处设防，所有调用方受益。
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, interactionSource = interaction, indication = null) else Modifier)
            .graphicsLayer { if (pressed && !reduce) { scaleX = 0.985f; scaleY = 0.985f } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** 胶囊徽标（范围/注入位置/状态等语义色块；颜色永远不是唯一指示）。 */
@Composable
fun BadgeChip(text: String, tint: Color, modifier: Modifier = Modifier) {
    LoomBadge(text = text, tint = tint, modifier = modifier)
}

/**
 * 开关（承袭上游 settings-toggle 44×24 语义；v3.1 换 Loom 开关：spring 动画）。
 *
 * 2026-09-13（W2）起**可交互**：传 [onCheckedChange] 即成为真开关；触控目标 48dp；
 * 不传回调 = 纯展示；[label] 给读屏用。
 */
@Composable
fun LuzzySwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    label: String? = null,
) {
    LoomSwitch(checked = checked, modifier = modifier, onCheckedChange = onCheckedChange, label = label)
}

/** 条目徽标（文字 + 语义色）。**颜色永远不是唯一指示**——徽标一律带文字。 */
data class EntryBadge(val text: String, val tint: Color)

/** 「⋯」菜单里的一项。 */
data class EntryMenuAction(
    val label: String,
    val onClick: () -> Unit,
    /** 删除类操作用 error 色（视觉上区分，但仍需确认框兜底）。 */
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * 数据条目行卡（世界书 / 预设共用；v3.1 = LoomCard + rail + LoomSwitch + LoomOverflowMenu）。
 *
 * 一行 = 一个可扫读的条目：标题 + 徽标组 + 支撑行；右侧是启停开关与「⋯」菜单。
 * - 点标题区 = 编辑（[onOpen] 为空则不可点）；
 * - 开关 = 启用/停用（[label] 给读屏）；
 * - 菜单 = 编辑 / 上移 / 下移 / 删除（**排序不做拖拽**：菜单项天然可达）。
 */
@Composable
fun EntryCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<EntryBadge> = emptyList(),
    supporting: String? = null,
    menu: List<EntryMenuAction> = emptyList(),
    onOpen: (() -> Unit)? = null,
) {
    LoomCard(modifier = modifier, tier = LoomTier.Card, rail = MaterialTheme.colorScheme.tertiary.takeIf { checked }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (onOpen != null) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpen)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        } else {
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        },
                    ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontFamily = LuzzyFonts.Body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    badges.forEach { BadgeChip(it.text, it.tint) }
                }
                if (supporting != null) {
                    Text(
                        text = supporting,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LuzzySwitch(checked = checked, onCheckedChange = onCheckedChange, label = "启用 $title")
            if (menu.isNotEmpty()) {
                LoomOverflowMenu(
                    label = "$title 的更多操作",
                    actions = menu.map { LoomMenuAction(it.label, it.onClick, it.destructive, it.enabled) },
                )
            }
        }
    }
}

/**
 * 大号统计数字 + 小标签（用量页/记忆页的统计行）。
 */
@Composable
fun StatMini(label: String, value: String) {
    Column {
        Text(
            text = value,
            fontSize = 20.sp,
            fontFamily = LuzzyFonts.Lora,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 空态（Loom 形态：圆环图标 + 主副文）。 */
@Composable
fun EmptyState(iconRes: Int, title: String, supporting: String, modifier: Modifier = Modifier) {
    LoomEmpty(iconRes = iconRes, title = title, supporting = supporting, modifier = modifier)
}

/** 行间发丝线（设置卡内的行分隔；比 Divider 更细更淡）。 */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(1.dp)
            .background(Loom.current.hairline),
    )
}
package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 页面通用组件（DESIGN-compose §13.3；命名对齐上游 settings-page-header 语义）。
 */

/** 页头（M3 TopAppBar 透明底）：汉堡（开抽屉）+ 页图标 + 标题 + 右侧动作槽。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(LuzzyIcons.Menu),
                    contentDescription = "打开菜单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 分组标题（上游 settings-section-heading：12sp uppercase 字距）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontFamily = LuzzyFonts.Body,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 设置卡容器（card 底 + hairline 边 + 16dp 圆角）。 */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 胶囊徽标（范围/注入位置/状态等语义色块）。 */
@Composable
fun BadgeChip(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(tint.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

/**
 * 开关（承袭上游 settings-toggle 44×24 语义）。
 *
 * 2026-09-13（W2）起**可交互**：
 * - 传 [onCheckedChange] 即成为真开关（`Role.Switch` 语义 + 涟漪），仪器化测试可用
 *   `assertIsOn/assertIsOff` 直接断言；
 * - 触控目标用 `minimumInteractiveComponentSize()` 撑到 **48dp**（Android 下限），
 *   视觉仍是 44×24 —— 视觉尺寸不动，只是热区变大；
 * - 不传回调 = 纯展示（历史调用点不受影响）；
 * - [label] 给读屏用（「启用 钟楼红苹果树」），屏读不该念出一个没有名字的开关。
 */
@Composable
fun LuzzySwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    label: String? = null,
) {
    val interaction = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    val semantics = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(semantics)
            .size(width = 44.dp, height = 24.dp)
            .then(interaction)
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(20.dp)
                .background(Color.White, CircleShape),
        )
    }
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
 * 数据条目行卡（世界书 / 预设共用，DESIGN-compose §23）。
 *
 * 一行 = 一个可扫读的条目：标题 + 徽标组 + 支撑行；右侧是启停开关与「⋯」菜单。
 * - 点标题区 = 编辑（[onOpen] 为空则不可点）；
 * - 开关 = 启用/停用（[label] 给读屏）；
 * - 菜单 = 编辑 / 上移 / 下移 / 删除（**排序不做拖拽**：菜单项天然可达，
 *   pro-rules 要求「拖拽必须有非拖拽替代」，本批直接用可达的那一种）。
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
    var menuOpen by remember { mutableStateOf(false) }
    SettingCard(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(LuzzyIcons.DotsHorizontal),
                            contentDescription = "$title 的更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = action.label,
                                        fontFamily = LuzzyFonts.Body,
                                        fontSize = 14.sp,
                                        color = if (action.destructive) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                enabled = action.enabled,
                                onClick = {
                                    menuOpen = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 空态（居中图标 + 主副文；上游空态语义）。 */
@Composable
fun EmptyState(iconRes: Int, title: String, supporting: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = supporting,
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
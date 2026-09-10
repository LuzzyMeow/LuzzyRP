package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIconButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.pressScale
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 聊天页页头（**与管理页同骨架**，DESIGN.md §管理页组件规范 #1 + §聊天页组件像素规格「页头」）。
 *
 * **2026-09-10 P2 顶栏语言统一（用户免除三方向门，落档
 * `docs/design/direction-approved-assistant.md`）**：改前这里是「上游聊天页皮肤」——
 * 112dp 黑色渐隐**覆盖层** + 白字 + Canvas 手绘 1.6dp 图标。那套皮肤在上游是给
 * **压在角色背景图上**的聊天页做可读性用的；助手聊天页背后是纯 `canvas`，既无图可压，
 * 又与八张管理页（canvas 底 + 深字 20sp Bold + 2dp 图标 + 40dp 方钮）同处一个栈——
 * 用户在两者间切换等于换一整套视觉语言（审查档 A1，用户反馈「感觉有断层」）。
 *
 * 现骨架 = [com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader] 同源：
 * `h-12`(48dp) 行 + 水平 16dp（`.management-view p-4`），行下 16dp（`mb-4`）由调用方以
 * [Ledger.PageHeaderGap] 给出；**黑渐隐整体取消**，页头不再覆盖消息流。
 *
 * 与上游聊天页页头的差异**只剩用户指定的一处内容差异**：中段是
 * 头像 + 助手名 + 会话标题（可点开会话信息），右上角是「助手设置」而非「清空聊天」。
 * 图标一律取自 [LedgerIcons]（VectorDrawable，2dp 描边），与管理页同一套来源。
 */
@Composable
fun ChatTopBar(
    title: String,
    subtitle: String?,
    avatarText: String,
    onMenu: () -> Unit,
    onTitleClick: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Ledger.PageHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：汉堡 → 侧栏（上游 `.mobile-menu-button`：`w-6 h-6` `text-gray-600` `mr-3`）
        val menuInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(Ledger.IconButtonSize)
                .pressScale(menuInteraction)
                .clip(RoundedCornerShape(Ledger.RadiusSm))
                .clickable(interactionSource = menuInteraction, indication = null, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LedgerIcons.Menu),
                contentDescription = "打开侧栏",
                tint = colors.muted,
                modifier = Modifier.size(Ledger.IconSize),
            )
        }
        Spacer(Modifier.width(4.dp))

        // 中：头像 + 名称 + chevron（点击展开会话信息；用户指定的聊天页内容差异）
        val titleInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Ledger.RadiusMd))
                .clickable(interactionSource = titleInteraction, indication = null, onClick = onTitleClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像＝页头「前置图标」位：`bg-primary-100 text-primary-600`（折叠行图标方块同款配色）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft)
                    .border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarText,
                    style = LedgerType.label,
                    color = colors.accentButton,
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = LedgerType.pageTitle,
                    color = colors.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = LedgerType.caption,
                        color = colors.mutedSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(LedgerIcons.ChevronDown),
                contentDescription = null,
                tint = colors.mutedSoft,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(Ledger.IconSizeSm),
            )
        }

        // 右：助手专属设置（用户指定的唯一差异点；上游 `.settings-page-header` 右＝`flex gap-2` 方钮组）
        LedgerIconButton(
            icon = LedgerIcons.Settings,
            contentDescription = "助手设置",
            onClick = onSettings,
        )
    }
}

/** 消息区左右内边距（上游 `px-2` = 8dp）。 */
val CHAT_CONTENT_HORIZONTAL_PADDING = 8.dp

/**
 * 消息间距。上游 `space-y-12`(48dp) 是为角色头像/时间留白；**助手气泡无头像**，48dp 显散
 * → 收敛到 **32dp**（DESIGN.md §聊天页组件像素规格「气泡间距」）。
 * ⚠ **待真机目测确认**：若仍显松散取 24dp。
 */
val CHAT_MESSAGE_SPACING = 32.dp

/** 消息区底部内边距（滚动到底时给输入岛让位）。 */
val CHAT_CONTENT_BOTTOM_PADDING = 16.dp

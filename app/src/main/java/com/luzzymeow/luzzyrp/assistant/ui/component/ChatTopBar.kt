package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons

/**
 * 聊天页顶栏（对齐上游 `index.html` 的 Chat Header，用户 2026-09-09 指定）。
 *
 * 上游结构：`h-28` 黑色渐隐层 + `h-12` 行（`w-9 h-9` 圆头像 + 名字 + chevron，右侧按钮组）。
 * 差异点（用户指定）：**右上角按钮从「清空聊天」改为助手设置**。
 *
 * 颜色用白色系（与上游 `text-white/80`、`text-white/70` 对齐）——渐隐层保证在浅色画布上
 * 依然可读（与上游无角色背景时的表现一致）。
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_GRADIENT_HEIGHT)
            .background(
                Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.50f),
                    0.20f to Color.Black.copy(alpha = 0.40f),
                    0.40f to Color.Black.copy(alpha = 0.25f),
                    0.60f to Color.Black.copy(alpha = 0.15f),
                    0.80f to Color.Black.copy(alpha = 0.06f),
                    0.95f to Color.Black.copy(alpha = 0.02f),
                    1.00f to Color.Transparent,
                )
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_ROW_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：汉堡 → 会话/助手抽屉（上游 `#icon-menu` 原图形，24dp）
            Icon(
                painter = painterResource(LedgerIcons.Menu),
                contentDescription = "打开侧栏",
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onMenu),
            )

            // 中：头像 + 名称 + chevron（点击展开会话信息）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(onClick = onTitleClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // chevron：上游 `w-4 h-4` = 16dp
                Icon(
                    painter = painterResource(LedgerIcons.ChevronDown),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }

            // 右：助手专属设置（用户指定的唯一差异点；上游清空按钮为 `w-5 h-5` = 20dp）
            Icon(
                painter = painterResource(LedgerIcons.Settings),
                contentDescription = "助手设置",
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onSettings),
            )
        }
    }
}

/** 上游 `h-28` = 112dp。 */
private val HEADER_GRADIENT_HEIGHT = 112.dp

/** 上游 `h-12` = 48dp。 */
private val HEADER_ROW_HEIGHT = 48.dp

/** 消息区顶部让位（上游 `pt-14` = 56dp）。 */
val CHAT_CONTENT_TOP_PADDING = 56.dp

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

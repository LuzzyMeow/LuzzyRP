package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.DrawerEntry
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 右侧抽屉（方向 A §1.1/§1.2：宽 min(300dp, 82%)，行高 56dp，底部固定「设置」）。
 *
 * 二级入口：记忆 / 技能 / MCP / 工作区 / 终端 / 设置。转场由宿主控制
 * （右滑 12dp + 淡入 200ms / 返回 140ms）。
 */
@Composable
fun SideDrawerContent(
    entries: List<DrawerEntry>,
    assistantName: String,
    modelLabel: String,
    onSelect: (DrawerEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(colors.canvas),
    ) {
        // 头部：当前助手 + 模型 chip
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                text = assistantName,
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .clip(LuzzyShapes.pill)
                    .background(colors.surfaceSoft)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline)
        )
        entries.forEach { entry ->
            DrawerRow(entry.label) { onSelect(entry); onClose() }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline)
        )
        Text(
            text = "关闭",
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedSoft,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun DrawerRow(label: String, onClick: () -> Unit) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.accentGraphic.copy(alpha = 0.5f))
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.body)
    }
}

package com.luzzymeow.luzzyrp.assistant.ui.component

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 会话列表行（方向 A §1.2：行高 68dp，两行文本）。
 *
 * 标题 15dp + 摘要 13dp muted + 右侧时间；发丝线通栏分隔。
 */
@Composable
fun ConversationRow(
    conversation: ConversationUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = conversation.summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = conversation.updatedAtLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(1.dp)
                .background(colors.hairline)
        )
    }
}

/** 日期分组头（32dp：Lora 12dp + 发丝线通栏）。 */
@Composable
fun ConversationGroupHeader(label: String, modifier: Modifier = Modifier) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedSoft,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.hairline)
        )
    }
}

/** 空态（无会话 / 无搜索结果两种文案，PLAN §6.2）。 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceSoft),
        )
        Spacer(Modifier.size(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.body)
        Spacer(Modifier.size(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(16.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentButton,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

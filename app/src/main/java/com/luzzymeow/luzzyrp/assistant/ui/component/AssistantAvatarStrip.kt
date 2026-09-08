package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyMotion
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 助手切换头像条（方向 A §1.1）。
 *
 * 40dp 圆头像 + Lora 名字；选中态 = coral 圆环 + `surface-card` 底；
 * 末尾 `+` 进「全部助手」管理页。单屏 5 个（横向滚动）。
 */
@Composable
fun AssistantAvatarStrip(
    assistants: List<AssistantUi>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    LazyRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(assistants, key = { it.id }) { assistant ->
            AssistantAvatarItem(
                assistant = assistant,
                selected = assistant.id == selectedId,
                onClick = { onSelect(assistant.id) },
            )
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.hairlineStrong, CircleShape)
                        .clickable(onClick = onOpenManager),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.muted,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "新建",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedSoft,
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatarItem(
    assistant: AssistantUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LuzzyTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) colors.surfaceCard else colors.surfaceSoft)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) colors.accentGraphic else colors.hairline,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = assistant.initial,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.accentDeep else colors.body,
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = assistant.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.ink else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(56.dp),
        )
    }
}

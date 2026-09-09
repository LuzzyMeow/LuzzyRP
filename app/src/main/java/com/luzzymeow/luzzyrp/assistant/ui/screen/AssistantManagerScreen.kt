package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/** 全部助手管理页（方向 A：顶栏头像条 `+` 进入）。 */
@Composable
fun AssistantManagerScreen(
    assistants: List<AssistantUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.body,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text("全部助手", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(assistants, key = { it.id }) { assistant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(LuzzyShapes.card)
                        .background(colors.surfaceSoft)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(assistant.initial, style = MaterialTheme.typography.titleMedium, color = colors.body)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(assistant.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                        Text(
                            text = assistant.lastTitle ?: "暂无会话",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted,
                        )
                    }
                    Text(
                        text = assistant.modelLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedSoft,
                    )
                }
            }
        }
    }
}

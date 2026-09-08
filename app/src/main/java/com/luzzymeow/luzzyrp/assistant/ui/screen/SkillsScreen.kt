package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.EmptyState
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.skill.SkillRow
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 技能页（方向 A 抽屉二级）。
 *
 * 每项两个开关：**全局**（所有助手注入）与**本助手**（仅当前助手注入）；
 * 正文进系统提示词，`tools:` 仅作提示，不绕过工具开关（PLAN §8.3）。
 */
@Composable
fun SkillsScreen(
    skills: List<SkillRow>,
    message: String?,
    onToggleGlobal: (String, Boolean) -> Unit,
    onToggleBinding: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(title = "技能", subtitle = "共 ${skills.size} 个", onBack = onBack)

        if (message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceCard)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        if (skills.isEmpty()) {
            EmptyState(
                title = "还没有技能",
                hint = "技能是给模型的流程说明（Markdown + front-matter），导入后按需启用。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(skills, key = { it.id }) { skill ->
                    SkillCard(skill, onToggleGlobal, onToggleBinding)
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillRow,
    onToggleGlobal: (String, Boolean) -> Unit,
    onToggleBinding: (String, Boolean) -> Unit,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceCard)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(skill.sourceLabel, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }
        }
        if (skill.description.isNotBlank()) {
            Text(skill.description, style = MaterialTheme.typography.bodyMedium, color = colors.bodyStrongMid)
        }
        ToggleRow("全局启用", skill.enabledGlobal) { onToggleGlobal(skill.id, it) }
        ToggleRow("本助手启用", skill.boundToAssistant) { onToggleBinding(skill.id, it) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LuzzyTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.canvas,
                checkedTrackColor = colors.accentButton,
                uncheckedThumbColor = colors.mutedSoft,
                uncheckedTrackColor = colors.hairline,
            ),
        )
    }
}

package com.luzzymeow.luzzyrp.assistant.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.model.MemoryUi
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 记忆卡（方向 A §1.5）：类型 chip + 正文 15dp/1.65 + 底部 meta（来源 / 时间 / 相似度条 4dp）。
 *
 * `similarity == null`（`full` 模式无嵌入模型）时不渲染相似度条。
 */
@Composable
fun MemoryCard(memory: MemoryUi, modifier: Modifier = Modifier) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LuzzyShapes.card)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceCard)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = memory.typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
        }
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.body,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = memory.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedSoft,
            )
            Spacer(Modifier.weight(1f))
            memory.similarity?.let { sim ->
                Text(
                    text = String.format("%.2f", sim),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentDeep,
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.hairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sim.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(colors.accentGraphic)
                    )
                }
            }
        }
    }
}

/** 检索框（44dp，surface-soft 底 + hairline；输入即搜由上层 300ms 防抖）。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(LuzzyShapes.button)
            .background(colors.surfaceSoft)
            .border(1.dp, colors.hairline, LuzzyShapes.button)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "⌕", style = MaterialTheme.typography.titleMedium, color = colors.mutedSoft)
        Spacer(Modifier.size(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.mutedSoft)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.body,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(colors.accentButton),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 模式条（记忆页顶部：混合 · TopK 8 · 阈值 0.35 · 最近 5 条）。 */
@Composable
fun MemoryModeBar(
    modeLabel: String,
    topK: Int,
    threshold: Float,
    recent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(LuzzyShapes.pill)
                .background(colors.surfaceCard)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(text = modeLabel, style = MaterialTheme.typography.labelMedium, color = colors.accentDeep)
        }
        Text(
            text = "TopK $topK · 阈值 $threshold · 最近 $recent 条",
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedSoft,
        )
    }
}

/** 页面头（`settings-page-header` 惯例：返回 + 标题 + 可选右侧动作）。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.body,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }
        }
        action?.invoke()
    }
}

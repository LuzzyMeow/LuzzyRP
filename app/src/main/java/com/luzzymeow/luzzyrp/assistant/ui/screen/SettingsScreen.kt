package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.settings.SettingsState
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 助手设置页（方向 A 抽屉二级，PLAN §11.1）。
 *
 * 四段卡：提示词与模型 / 参数 / 请求体扩展 / 预览最终请求（密钥脱敏）。
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onName: (String) -> Unit,
    onPrompt: (String) -> Unit,
    onModel: (String) -> Unit,
    onTemperature: (String) -> Unit,
    onTopP: (String) -> Unit,
    onMaxTokens: (String) -> Unit,
    onExtraBody: (String) -> Unit,
    onMemoryMode: (String) -> Unit,
    onSearchProvider: (String) -> Unit = {},
    onSearxngUrl: (String) -> Unit = {},
    onTavilyKey: (String) -> Unit = {},
    onBraveKey: (String) -> Unit = {},
    onSaveSearch: () -> Unit = {},
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
    onDismissMessage: () -> Unit,
    onClearAudit: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(
            title = "设置",
            subtitle = if (state.hasWebConfig) "模型来自 Web 端供应商配置" else "尚未读取到 Web 端供应商配置",
            onBack = onBack,
            action = {
                Text(
                    text = "保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentButton,
                    modifier = Modifier.clickable(onClick = onSave).padding(8.dp),
                )
            },
        )

        if (state.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(LuzzyShapes.card)
                    .background(colors.surfaceCard)
                    .clickable(onClick = onDismissMessage)
                    .padding(12.dp),
            ) {
                Text(state.message, style = MaterialTheme.typography.labelMedium, color = colors.body)
            }
            Spacer(Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("提示词与模型") {
                Field("助手名称", state.name, onName, singleLine = true)
                Field("系统提示词", state.systemPrompt, onPrompt, minHeight = 120.dp)
                if (state.availableModels.isNotEmpty()) {
                    Text("模型", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    state.availableModels.take(24).forEach { model ->
                        val selected = state.modelRef == model
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(LuzzyShapes.button)
                                .background(if (selected) colors.accentSoft else colors.surfaceCard)
                                .clickable { onModel(model) }
                                .padding(10.dp),
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (selected) colors.accentDeep else colors.body,
                            )
                        }
                    }
                } else {
                    Field("模型引用（providerId::模型名）", state.modelRef, onModel, singleLine = true)
                }
            }

            SectionCard("参数") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) { Field("temperature", state.temperature, onTemperature, singleLine = true) }
                    Box(modifier = Modifier.weight(1f)) { Field("top_p", state.topP, onTopP, singleLine = true) }
                    Box(modifier = Modifier.weight(1f)) { Field("max_tokens", state.maxTokens, onMaxTokens, singleLine = true) }
                }
                Text("记忆模式", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("full" to "全文", "embed" to "向量", "hybrid" to "混合").forEach { (id, label) ->
                        val selected = state.memoryMode == id
                        Box(
                            modifier = Modifier
                                .clip(LuzzyShapes.pill)
                                .background(if (selected) colors.accentSoft else colors.surfaceCard)
                                .clickable { onMemoryMode(id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accentDeep else colors.muted,
                            )
                        }
                    }
                }
            }

            SectionCard("联网搜索") {
                Text("搜索提供方", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("duckduckgo" to "DuckDuckGo", "searxng" to "SearXNG").forEach { (id, label) ->
                        val selected = state.searchProvider == id
                        Box(
                            modifier = Modifier
                                .clip(LuzzyShapes.pill)
                                .background(if (selected) colors.accentSoft else colors.surfaceCard)
                                .clickable { onSearchProvider(id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accentDeep else colors.muted,
                            )
                        }
                    }
                }
                if (state.searchProvider == "searxng") {
                    Field("SearXNG 实例地址（需开启 format=json）", state.searxngUrl, onSearxngUrl, singleLine = true)
                } else {
                    Text(
                        text = "DuckDuckGo 无需配置（公共端点，可能被限流）",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedSoft,
                    )
                }
                Text(
                    text = "API Key（加密存储，保存后不回显）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                Field(
                    if (state.tavilyKeySet) "Tavily API Key（已配置，留空则不改动）" else "Tavily API Key",
                    state.tavilyKey,
                    onTavilyKey,
                    singleLine = true,
                )
                Field(
                    if (state.braveKeySet) "Brave API Key（已配置，留空则不改动）" else "Brave API Key",
                    state.braveKey,
                    onBraveKey,
                    singleLine = true,
                )
                Text(
                    text = "保存搜索设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentButton,
                    modifier = Modifier.clickable(onClick = onSaveSearch),
                )
            }

            SectionCard("请求体扩展") {
                Field(
                    "JSON（禁止覆盖 messages / tools / stream / model）",
                    state.extraBodyJson,
                    onExtraBody,
                    minHeight = 96.dp,
                )
            }

            SectionCard("工具审计（最近 ${state.auditEntries.size} 条）") {
                Text(
                    text = "参数只记键名与长度（脱敏），结果截断 400 字",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedSoft,
                )
                if (state.auditEntries.isEmpty()) {
                    Text("（暂无调用记录）", style = MaterialTheme.typography.labelMedium, color = colors.mutedSoft)
                } else {
                    state.auditEntries.take(20).forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (row.ok) "✓" else "✗",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (row.ok) colors.success else colors.error,
                            )
                            Text(
                                text = row.toolName,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = colors.body,
                                modifier = Modifier.weight(1f),
                            )
                            Text(row.durationLabel, style = MaterialTheme.typography.labelMedium, color = colors.mutedSoft)
                            Text(row.timeLabel, style = MaterialTheme.typography.labelMedium, color = colors.mutedSoft)
                        }
                        Text(
                            text = row.argsPreview,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colors.mutedSoft,
                        )
                    }
                    Text(
                        text = "清空审计",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.error,
                        modifier = Modifier.clickable(onClick = onClearAudit).padding(top = 4.dp),
                    )
                }
            }

            SectionCard("预览最终请求") {
                Text(
                    text = if (state.showPreview) "收起预览" else "展开预览（密钥脱敏）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentButton,
                    modifier = Modifier.clickable(onClick = onTogglePreview),
                )
                if (state.showPreview) {
                    Text(
                        text = state.previewText,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = colors.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        content()
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = LuzzyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(LuzzyShapes.button)
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairline, LuzzyShapes.button)
                .padding(10.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedSoft,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
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

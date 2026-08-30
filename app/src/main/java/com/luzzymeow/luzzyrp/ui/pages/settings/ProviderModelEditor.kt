package com.luzzymeow.luzzyrp.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.luzzymeow.luzzyrp.core.common.TokenCountParser
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ModelCapability
import com.luzzymeow.luzzyrp.core.model.ThinkingDepth
import com.luzzymeow.luzzyrp.core.model.ThinkingDepthAdapter
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing

/**
 * 新增模型表单（6.1）：模型 id/显示名/上下文长度（支持 1024K、1M、1024000）/最大输出（默认=上下文）/
 * 温度/思考深度档位（ThinkingDepthAdapter 按模型 id 自适配）+ 字段自检（6.3）。
 */
@Composable
internal fun ProviderModelEditor(onSave: (Model) -> Unit) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    var maxOutText by remember { mutableStateOf("") }
    var temperatureText by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 6.3 字段自检联动：id 输入时实时给出该家族可用的思考档位
    val depthOptions = if (modelId.isBlank()) emptyList() else ThinkingDepthAdapter.optionsFor(modelId)

    Spacer(Modifier.padding(top = LuzzySpacing.LG))
    Text("新增模型", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.padding(top = LuzzySpacing.SM))
    LuzzyTextField(modelId, { modelId = it; error = null }, placeholder = "模型 id *（如 deepseek-v4-pro）",
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.padding(top = LuzzySpacing.XS))
    LuzzyTextField(displayName, { displayName = it }, placeholder = "显示名称（可选）",
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.padding(top = LuzzySpacing.XS))
    LuzzyTextField(contextText, { contextText = it }, placeholder = "上下文长度（支持 1024K / 1M / 1024000）",
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.padding(top = LuzzySpacing.XS))
    LuzzyTextField(maxOutText, { maxOutText = it }, placeholder = "最大输出（留空 = 与上下文一致）",
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.padding(top = LuzzySpacing.XS))
    LuzzyTextField(temperatureText, { temperatureText = it }, placeholder = "温度（可选，0-2）",
        modifier = Modifier.fillMaxWidth())

    if (depthOptions.isNotEmpty()) {
        Spacer(Modifier.padding(top = LuzzySpacing.XS))
        Text(
            "思考深度（" + ThinkingDepthAdapter.familyOf(modelId).name + " 档位）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.XS)) {
            depthOptions.forEach { d ->
                EditorChip(
                    label = when (d) {
                        ThinkingDepth.OFF -> "关闭"
                        ThinkingDepth.LOW -> "低"
                        ThinkingDepth.MEDIUM -> "中"
                        ThinkingDepth.HIGH -> "高"
                        ThinkingDepth.MAX -> "最高"
                        ThinkingDepth.DEFAULT -> "默认"
                    },
                    selected = depth == d.name,
                    onClick = { depth = if (depth == d.name) null else d.name },
                )
            }
        }
    }

    error?.let {
        Spacer(Modifier.padding(top = LuzzySpacing.XS))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.padding(top = LuzzySpacing.SM))
    TextButton(onClick = {
        // 6.3 字段自检
        if (modelId.isBlank()) { error = "模型 id 不能为空"; return@TextButton }
        if (modelId.any { it.isWhitespace() }) { error = "模型 id 不能包含空格"; return@TextButton }
        val context = TokenCountParser.parse(contextText)
        if (contextText.isNotBlank() && context == null) {
            error = "上下文长度格式无效（示例：1024000、1024K、1M）"; return@TextButton
        }
        val maxOut = maxOutText.trim().let {
            if (it.isEmpty()) context else TokenCountParser.parse(it)
        }
        if (maxOutText.isNotBlank() && maxOut == null) {
            error = "最大输出格式无效"; return@TextButton
        }
        val temperature = temperatureText.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull() }
        if (temperatureText.isNotBlank() && (temperature == null || temperature < 0 || temperature > 2)) {
            error = "温度需为 0-2 的数字"; return@TextButton
        }
        onSave(
            Model(
                id = modelId.trim(),
                displayName = displayName.trim(),
                capabilities = ModelCapability(reasoning = depth != null, tool = true),
                contextLength = context ?: 128_000L,
                maxOutputTokens = maxOut ?: context ?: 0L, // 6.1：默认与上下文一致
                temperature = temperature,
                thinkingDepth = depth,
            )
        )
        modelId = ""; displayName = ""; contextText = ""; maxOutText = ""; temperatureText = ""
        depth = null; error = "已添加模型"
    }) { Text("+ 添加模型") }
}

@Composable
private fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AuroraSurface(
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = LuzzySpacing.SM, vertical = LuzzySpacing.XS),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.Ledger
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButton
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerButtonTone
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerCollapseCard
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerIcons
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerListRow
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerPageHeader
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerSearchField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerSegmented
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerStatusPill
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerTextField
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerToggleRow
import com.luzzymeow.luzzyrp.assistant.ui.component.ledger.LedgerType
import com.luzzymeow.luzzyrp.assistant.ui.settings.AuditRow
import com.luzzymeow.luzzyrp.assistant.ui.settings.SettingsState
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 设置页（DESIGN.md §管理页组件规范）。
 *
 * 结构：`LedgerPageHeader`（齿轮图标 + 「设置」+ 右侧保存按钮）→ 可折叠卡片组
 * （提示词与模型 / 参数 / 联网搜索 / 请求体扩展 / 工具审计 / 预览最终请求）。
 * 折叠范式与上游 `.settings-collapse` 一致（360ms `cubic-bezier(.22,1,.36,1)`）。
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
    onToggleTool: (String, Boolean) -> Unit = { _, _ -> },
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
    onDismissMessage: () -> Unit,
    onClearAudit: () -> Unit = {},
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var openPrompt by remember { mutableStateOf(true) }
    var openParams by remember { mutableStateOf(false) }
    var openSearch by remember { mutableStateOf(false) }
    var openTools by remember { mutableStateOf(false) }
    var openExtra by remember { mutableStateOf(false) }
    var openAudit by remember { mutableStateOf(false) }
    var openPreview by remember { mutableStateOf(false) }

    // 日历权限（运行时权限）：状态在 UI 侧查（VM 不该持有 Activity Context），
    // 授权对话框用 ActivityResult 契约发起——这是 App 内**唯一**能拿到日历权限的入口
    // （此前既没声明权限、也没有申请流程，calendar_read/write 必然失败：2026-09-11 静态审查修复）。
    val context = LocalContext.current
    fun calendarGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    var calendarOk by remember { mutableStateOf(calendarGranted()) }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> calendarOk = result.values.any { it } || calendarGranted() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = Ledger.PagePadding),
    ) {
        LedgerPageHeader(
            icon = LedgerIcons.Settings,
            title = "设置",
            onMenu = onMenu,
            actions = {
                LedgerButton(text = "保存", onClick = onSave)
            },
        )
        Spacer(Modifier.height(Ledger.PageHeaderGap))

        if (state.message != null) {
            LedgerCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.message,
                        style = LedgerType.body,
                        color = colors.body,
                        modifier = Modifier.weight(1f),
                    )
                    LedgerButton(text = "关闭", onClick = onDismissMessage)
                }
            }
            Spacer(Modifier.height(Ledger.CardGap))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Ledger.PagePadding),
            verticalArrangement = Arrangement.spacedBy(Ledger.CardGap),
        ) {
            LedgerCollapseCard(
                icon = LedgerIcons.Conversation,
                title = "提示词与模型",
                expanded = openPrompt,
                onToggle = { openPrompt = !openPrompt },
                statusText = if (state.hasWebConfig) "Web 端配置" else "未同步",
            ) {
                // 未同步时给出**可执行**说明（用户 2026-09-10 实测反馈过「尚未读取到 Web 端供应商配置」，
                // 当时只有状态文字、没有出路：2026-09-11 静态审查补成可执行提示）。
                if (!state.hasWebConfig) {
                    Text(
                        text = "尚未读取到 Web 端供应商配置：请到 LuzzyRP「设置 → 供应商」配置并选用模型，" +
                            "回到助手时会自动同步（助手不重复保存 Key）。",
                        style = LedgerType.caption,
                        color = if (colors.isDark) colors.warning else colors.accentDeep,
                    )
                }
                LabeledField("助手名称", state.name, onName, singleLine = true)
                LabeledField("系统提示词", state.systemPrompt, onPrompt, minHeight = 120.dp)
                if (state.availableModels.isNotEmpty()) {
                    Text("模型", style = LedgerType.caption, color = colors.muted)
                    state.availableModels.take(24).forEach { model ->
                        val selected = state.modelRef == model
                        LedgerListRow(
                            title = model,
                            onClick = { onModel(model) },
                            trailing = {
                                if (selected) LedgerStatusPill("已选", tone = LedgerButtonTone.Primary)
                            },
                        )
                    }
                } else {
                    LabeledField("模型引用（providerId::模型名）", state.modelRef, onModel, singleLine = true)
                }
            }

            LedgerCollapseCard(
                icon = LedgerIcons.Sliders,
                title = "参数",
                expanded = openParams,
                onToggle = { openParams = !openParams },
                statusText = "T ${state.temperature}",
            ) {                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField("temperature", state.temperature, onTemperature, singleLine = true)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField("top_p", state.topP, onTopP, singleLine = true)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField("max_tokens", state.maxTokens, onMaxTokens, singleLine = true)
                    }
                }
                Text("记忆模式", style = LedgerType.caption, color = colors.muted)
                LedgerSegmented(
                    options = listOf("full" to "全文", "embed" to "向量", "hybrid" to "混合"),
                    selected = state.memoryMode,
                    onSelect = onMemoryMode,
                )
            }

            LedgerCollapseCard(
                icon = LedgerIcons.Search,
                title = "联网搜索",
                expanded = openSearch,
                onToggle = { openSearch = !openSearch },
                statusText = if (state.searchProvider == "searxng") "SearXNG" else "DuckDuckGo",
            ) {
                Text("搜索提供方", style = LedgerType.caption, color = colors.muted)
                LedgerSegmented(
                    options = listOf("duckduckgo" to "DuckDuckGo", "searxng" to "SearXNG"),
                    selected = state.searchProvider,
                    onSelect = onSearchProvider,
                )
                if (state.searchProvider == "searxng") {
                    LabeledField("SearXNG 实例地址（需开启 format=json）", state.searxngUrl, onSearxngUrl, singleLine = true)
                } else {
                    Text(
                        text = "DuckDuckGo 无需配置（公共端点，可能被限流）",
                        style = LedgerType.caption,
                        color = colors.mutedSoft,
                    )
                }
                Text("API Key（加密存储，保存后不回显）", style = LedgerType.caption, color = colors.muted)
                LabeledField(
                    if (state.tavilyKeySet) "Tavily API Key（已配置，留空则不改动）" else "Tavily API Key",
                    state.tavilyKey,
                    onTavilyKey,
                    singleLine = true,
                )
                LabeledField(
                    if (state.braveKeySet) "Brave API Key（已配置，留空则不改动）" else "Brave API Key",
                    state.braveKey,
                    onBraveKey,
                    singleLine = true,
                )
                LedgerButton(text = "保存搜索设置", onClick = onSaveSearch)
            }

            // 工具开关（2026-09-11 静态审查补齐）：T2/T3 档默认关闭，契约上「需用户在设置里逐项
            // 开启」（ApprovalGate 文档 / PLAN §12.1），但此前**既无开关 UI、也无开关写入**——
            // 日历/终端/截屏/短信/发到 RP 会话等 8 类工具永远开不了，属交互死路。
            LedgerCollapseCard(
                icon = LedgerIcons.Workspace,
                title = "工具开关",
                expanded = openTools,
                onToggle = { openTools = !openTools },
                statusText = "${state.tools.count { it.enabled }}/${state.tools.size} 开",
            ) {
                Text(
                    text = "默认关闭的工具需在此逐项开启；开启后每次调用仍会弹审批卡（安全兜底不变）。",
                    style = LedgerType.caption,
                    color = colors.mutedSoft,
                )
                // 分两组呈现：用户进这一屏多半是为了开「默认关」那批，先给它们，再给可关的常开项。
                val offTools = state.tools.filter { it.defaultOff }
                val onTools = state.tools.filterNot { it.defaultOff }
                if (offTools.isNotEmpty()) {
                    Text("需手动开启（默认关）", style = LedgerType.caption, color = colors.muted)
                    offTools.forEach { tool ->
                        LedgerToggleRow(
                            label = tool.label,
                            checked = tool.enabled,
                            onCheckedChange = { onToggleTool(tool.name, it) },
                            hint = tool.hint,
                        )
                    }
                }
                if (onTools.isNotEmpty()) {
                    Text("默认开启（可关闭）", style = LedgerType.caption, color = colors.muted)
                    onTools.forEach { tool ->
                        LedgerToggleRow(
                            label = tool.label,
                            checked = tool.enabled,
                            onCheckedChange = { onToggleTool(tool.name, it) },
                            hint = tool.hint,
                        )
                    }
                }
                // 日历工具需要运行时权限：本地没声明过权限就永远失败，故开启后在此就地授予。
                val calendarOn = state.tools.any { it.name.startsWith("calendar_") && it.enabled }
                if (calendarOn) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = Ledger.ListRowMinHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("日历权限", style = LedgerType.label, color = colors.body)
                            Text(
                                text = if (calendarOk) "已授予" else "未授予——未授予时日历工具必然失败",
                                style = LedgerType.caption,
                                color = if (calendarOk) colors.success else colors.warning,
                            )
                        }
                        if (!calendarOk) {
                            LedgerButton(
                                text = "授予",
                                onClick = {
                                    calendarLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            LedgerCollapseCard(
                icon = LedgerIcons.Terminal,
                title = "请求体扩展",
                expanded = openExtra,
                onToggle = { openExtra = !openExtra },
            ) {
                Text(
                    text = "JSON（禁止覆盖 messages / tools / stream / model）",
                    style = LedgerType.caption,
                    color = colors.mutedSoft,
                )
                LedgerTextField(
                    value = state.extraBodyJson,
                    onValueChange = onExtraBody,
                    minHeight = 96.dp,
                    placeholder = "{\"provider\": {\"order\": [\"x\"]}}",
                )
            }

            LedgerCollapseCard(
                icon = LedgerIcons.Info,
                title = "工具审计",
                expanded = openAudit,
                onToggle = { openAudit = !openAudit },
                statusText = "最近 ${state.auditEntries.size} 条",
            ) {
                Text(
                    text = "参数只记键名与长度（脱敏），结果截断 400 字",
                    style = LedgerType.caption,
                    color = colors.mutedSoft,
                )
                if (state.auditEntries.isEmpty()) {
                    Text("（暂无调用记录）", style = LedgerType.caption, color = colors.mutedSoft)
                } else {
                    state.auditEntries.take(20).forEach { row: AuditRow ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LedgerStatusPill(
                                text = if (row.ok) "成功" else "失败",
                                tone = if (row.ok) LedgerButtonTone.Primary else LedgerButtonTone.Danger,
                            )
                            Text(
                                text = row.toolName,
                                style = LedgerType.caption,
                                fontFamily = FontFamily.Monospace,
                                color = colors.body,
                                modifier = Modifier.weight(1f),
                            )
                            Text(row.durationLabel, style = LedgerType.caption, color = colors.mutedSoft)
                            Text(row.timeLabel, style = LedgerType.caption, color = colors.mutedSoft)
                        }
                        Text(
                            text = row.argsPreview,
                            style = LedgerType.caption,
                            fontFamily = FontFamily.Monospace,
                            color = colors.mutedSoft,
                        )
                    }
                    LedgerButton(text = "清空审计", tone = LedgerButtonTone.Danger, onClick = onClearAudit)
                }
            }

            LedgerCollapseCard(
                icon = LedgerIcons.ExternalLink,
                title = "预览最终请求",
                expanded = openPreview || state.showPreview,
                onToggle = onTogglePreview,
            ) {
                if (state.showPreview && state.previewText.isNotBlank()) {
                    Text(
                        text = state.previewText,
                        style = LedgerType.caption,
                        fontFamily = FontFamily.Monospace,
                        color = colors.body,
                    )
                } else {
                    Text(
                        text = "展开后显示将发送的请求体（密钥脱敏）",
                        style = LedgerType.caption,
                        color = colors.mutedSoft,
                    )
                }
            }
        }
    }
}

/** 带标签的输入框（上游 `text-sm` 标签 + `rounded-xl` 输入面）。 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = Ledger.InputMinHeight,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = LedgerType.caption, color = LuzzyTheme.colors.muted)
        LedgerTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minHeight = minHeight,
        )
    }
}

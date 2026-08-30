package com.luzzymeow.luzzyrp.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.data.datastore.Settings
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.navigation.Route
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置族共享 ViewModel：读改 SettingsStore。 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsStore.settingsFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }
}

// ---------------------------------------------------------------------------
// 主设置页
// ---------------------------------------------------------------------------

@Composable
fun SettingsPage(onBack: () -> Unit, onOpenRoute: (Route) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("设置", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
        ) {
            items(SETTING_SECTIONS, key = { it.first }) { (icon, title, subtitle, route) ->
                AuroraSurface(onClick = { onOpenRoute(route) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(icon), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(LuzzySpacing.MD))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(painterResource(LuzzyIcons.Expand.res), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private val SETTING_SECTIONS = listOf(
    Tuple4(LuzzyIcons.Key.res, "供应商与模型", "API Key、模型管理、生成参数与思考深度", Route.SettingsProviders as Route),
    Tuple4(LuzzyIcons.Brain.res, "记忆设置", "长期记忆（ACE）与三级摘要", Route.MemorySettings),
    Tuple4(LuzzyIcons.Palette.res, "外观", "主题模式与显示偏好", Route.SettingsAppearance),
    Tuple4(LuzzyIcons.Info.res, "关于", "版本信息、更新日志与日志导出", Route.SettingsAbout),
)

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ---------------------------------------------------------------------------
// 供应商列表
// ---------------------------------------------------------------------------

@Composable
fun SettingsProvidersPage(onBack: () -> Unit, onOpenDetail: (String) -> Unit) {
    val viewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("供应商", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                viewModel.update { s ->
                    val newId = "custom_" + java.util.UUID.randomUUID().toString().take(8)
                    s.copy(
                        providers = s.providers + com.luzzymeow.luzzyrp.core.model.ProviderSetting(
                            id = newId, name = "新供应商",
                            baseUrl = "https://", apiKey = "",
                        ),
                    )
                }
            }) {
                Icon(painterResource(LuzzyIcons.Plus.res), contentDescription = "新增供应商",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
        ) {
            items(settings?.providers.orEmpty(), key = { it.id }) { provider ->
                AuroraSurface(
                    onClick = { onOpenDetail(provider.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                provider.baseUrl + if (provider.apiKey.isNotBlank()) " · 已配置密钥" else " · 未配置密钥",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (provider.id == settings?.currentProviderId) {
                            Icon(painterResource(LuzzyIcons.Check.res), contentDescription = "当前使用",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 供应商详情（编辑密钥/端点/选择模型）
// ---------------------------------------------------------------------------

@Composable
fun SettingsProviderDetailPage(providerId: String, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val settings by viewModel.settings.collectAsState()
    val provider = settings?.providers?.firstOrNull { it.id == providerId }

    var apiKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var baseUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(provider) {
        provider?.let {
            if (apiKey.isBlank()) apiKey = it.apiKey
            if (baseUrl.isBlank()) baseUrl = it.baseUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LuzzySpacing.LG),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(provider?.name ?: "供应商", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.size(LuzzySpacing.MD))
        LuzzyTextField(baseUrl, { baseUrl = it }, placeholder = "API 端点", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(LuzzySpacing.MD))
        LuzzyTextField(apiKey, { apiKey = it }, placeholder = "API Key", modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.size(LuzzySpacing.LG))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模型", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = {
                viewModel.update { st -> st.copy(providers = st.providers.filterNot { it.id == providerId }) }
                onBack()
            }) { Text("删除供应商", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.size(LuzzySpacing.SM))
        provider?.models?.forEach { model ->
            AuroraSurface(
                onClick = {
                    viewModel.update { s ->
                        s.copy(currentProviderId = provider.id, currentModelId = model.id)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.displayNameOrId(), style = MaterialTheme.typography.bodyMedium)
                        val caps = buildList {
                            if (model.capabilities.reasoning) add("推理")
                            if (model.capabilities.tool) add("工具")
                            if (model.capabilities.embedding) add("嵌入")
                            if (model.capabilities.vision) add("视觉")
                        }
                        Text(
                            "${model.id} · ${caps.joinToString("/").ifBlank { "文本" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (model.id == settings?.currentModelId && provider.id == settings?.currentProviderId) {
                        Icon(painterResource(LuzzyIcons.Check.res), contentDescription = "当前",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        viewModel.update { st ->
                            st.copy(providers = st.providers.map {
                                if (it.id == providerId) it.copy(models = it.models.filterNot { m -> m.id == model.id })
                                else it
                            })
                        }
                    }) {
                        Icon(painterResource(LuzzyIcons.Trash.res), contentDescription = "删除模型",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        ProviderModelEditor(onSave = { model ->
            viewModel.update { st ->
                st.copy(providers = st.providers.map {
                    if (it.id == providerId) it.copy(models = it.models + model) else it
                })
            }
        })

        // —— 生成参数并入（6.1）：全局温度与思考深度 ——
        Spacer(Modifier.size(LuzzySpacing.LG))
        Text("生成参数（全局）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(LuzzySpacing.SM))
        settings?.let { st ->
            Text("温度：" + (st.temperature ?: 1.0), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Slider(
                value = (st.temperature ?: 1.0).toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(temperature = v.toDouble()) } },
                valueRange = 0f..2f,
            )
            Text("思考深度（按模型 id 自适配档位）", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.XS)) {
                listOf("默认" to null, "关闭" to "OFF", "低" to "LOW", "高" to "HIGH", "最高" to "MAX")
                    .forEach { (label, depthName) ->
                        AuroraSurface(
                            onClick = { viewModel.update { it.copy(thinkingDepth = depthName) } },
                            containerColor = if (st.thinkingDepth == depthName) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = LuzzySpacing.SM, vertical = LuzzySpacing.XS),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
            }
        }

        Spacer(Modifier.size(LuzzySpacing.LG))
        androidx.compose.material3.Button(
            onClick = {
                viewModel.update { s ->
                    s.copy(
                        providers = s.providers.map {
                            if (it.id == providerId) it.copy(apiKey = apiKey.trim(), baseUrl = baseUrl.trim()) else it
                        },
                        currentProviderId = if (s.currentProviderId.isBlank()) providerId else s.currentProviderId,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
    }
}

// ---------------------------------------------------------------------------
// 生成参数
// ---------------------------------------------------------------------------

@Composable
fun SettingsGenerationPage(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("生成参数", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(LuzzySpacing.LG)) {
            settings?.let { s ->
                // 温度
                Text("温度：${s.temperature ?: 1.0}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = (s.temperature ?: 1.0).toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(temperature = v.toDouble()) } },
                    valueRange = 0f..2f,
                )
                Spacer(Modifier.size(LuzzySpacing.MD))

                // 思考深度
                Text("思考深度", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.SM)) {
                    listOf("模型默认" to null, "开启" to "high", "最大" to "max").forEach { (label, effort) ->
                        val selected = s.thinkingEffort == effort && (effort != null || s.thinkingEnabled == null)
                        AuroraSurface(
                            onClick = {
                                viewModel.update { it.copy(thinkingEffort = effort, thinkingEnabled = effort?.let { _ -> true }) }
                            },
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.size(LuzzySpacing.LG))

                // 上下文策略
                Text("历史轮数（0 = 不限制）", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.contextConfig.historyRounds.toFloat(),
                    onValueChange = { v ->
                        viewModel.update { it.copy(contextConfig = it.contextConfig.copy(historyRounds = v.toInt())) }
                    },
                    valueRange = 0f..200f,
                )
                Spacer(Modifier.size(LuzzySpacing.LG))

                // 记忆与摘要
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("长期记忆（ACE）", style = MaterialTheme.typography.titleSmall)
                        Text("自动检索、反思与沉淀长期事实", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.memoryEnabled, onCheckedChange = { v -> viewModel.update { it.copy(memoryEnabled = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("三级摘要", style = MaterialTheme.typography.titleSmall)
                        Text("A 每轮 / B 每 10 轮 / C 每 50 轮", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.summaryEnabled, onCheckedChange = { v -> viewModel.update { it.copy(summaryEnabled = v) } })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 外观
// ---------------------------------------------------------------------------

@Composable
fun SettingsAppearancePage(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = org.koin.androidx.compose.koinViewModel()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("外观", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(LuzzySpacing.LG)) {
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(LuzzySpacing.SM))
            Row(horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.SM)) {
                listOf("跟随系统" to com.luzzymeow.luzzyrp.ui.theme.ThemeMode.SYSTEM,
                    "亮色" to com.luzzymeow.luzzyrp.ui.theme.ThemeMode.LIGHT,
                    "暗色" to com.luzzymeow.luzzyrp.ui.theme.ThemeMode.DARK,
                    "AMOLED" to com.luzzymeow.luzzyrp.ui.theme.ThemeMode.AMOLED).forEach { (label, mode) ->
                    val selected = settings?.themeMode == mode
                    AuroraSurface(
                        onClick = { viewModel.update { it.copy(themeMode = mode) } },
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.size(LuzzySpacing.LG))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("引用高亮", style = MaterialTheme.typography.titleSmall)
                    Text("会话中 \"…\" 引用文本着色", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings?.quoteHighlightEnabled ?: true,
                    onCheckedChange = { v -> viewModel.update { it.copy(quoteHighlightEnabled = v) } })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 关于（6.6：CHANGELOG 同步渲染 + 应用日志查看/导出/分享）
// ---------------------------------------------------------------------------

@Composable
fun SettingsAboutPage(onBack: () -> Unit) {
    val logger = org.koin.compose.koinInject<com.luzzymeow.luzzyrp.data.logger.AppLogger>()
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs by logger.recent.collectAsState()
    var changelog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    LaunchedEffect(Unit) {
        // CHANGELOG.md 打包为 assets，随版本同步（发版流程保证）
        changelog = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().readText()
        }.getOrDefault("（未找到更新日志）")
    }

    // 导出：SAF 建文档 + 系统分享
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { uriSnapshot -> kotlinx.coroutines.MainScope().launch {
        logger.exportTo(uriSnapshot)
    } } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("关于", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LuzzySpacing.LG),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(com.luzzymeow.luzzyrp.R.drawable.luzzy_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.size(LuzzySpacing.MD))
                Column {
                    Text("LuzzyRP", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "v" + com.luzzymeow.luzzyrp.BuildConfig.VERSION_NAME + " · versionCode " + com.luzzymeow.luzzyrp.BuildConfig.VERSION_CODE,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(LazyListSpacing2()))

            // —— 应用日志（6.6）——
            AuroraSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("应用日志", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("内存 ${logs.size} 条 · 保留 3 天", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(LuzzySpacing.SM))
                    // 最近日志（倒序，最多显示 50 条）
                    Column {
                        logs.takeLast(50).reversed().forEach { entry ->
                            Text(
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                    .format(java.util.Date(entry.ts)) + " [${entry.category}] ${entry.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (logs.isEmpty()) {
                            Text("暂无日志", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.size(LazyListSpacing2()))
                    Row {
                        TextButton(onClick = {
                            val formatter = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                            exportLauncher.launch("luzzy-logs-" + formatter.format(java.util.Date()) + ".json")
                        }) { Text("导出 JSON") }
                        TextButton(onClick = {
                            kotlinx.coroutines.MainScope().launch {
                                val file = logger.exportToCache() ?: return@launch
                                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM,
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context, context.packageName + ".fileprovider", file))
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(share, "分享日志"))
                            }
                        }) { Text("分享") }
                        TextButton(onClick = logger::clearInMemory) { Text("清空显示") }
                    }
                }
            }

            Spacer(Modifier.size(LazyListSpacing2()))
            Text("更新日志（CHANGELOG.md，随版本同步）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(LuzzySpacing.SM))
            Text(
                changelog,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(LazyListSpacing2()))
            Text("技术栈：Kotlin 2.4 · Jetpack Compose · Material 3 Expressive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("许可证：CC BY-NC 4.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("github.com/LuzzyMeow/LuzzyRP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(LuzzySpacing.XXXL))
        }
    }
}

private fun LazyListSpacing2() = LuzzySpacing.LG

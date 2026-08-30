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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    Tuple4(LuzzyIcons.Key.res, "供应商与模型", "API Key、端点与模型管理", Route.SettingsProviders as Route),
    Tuple4(LuzzyIcons.Sparkle.res, "生成参数", "温度、思考深度与上下文策略", Route.SettingsGeneration),
    Tuple4(LuzzyIcons.Palette.res, "外观", "主题模式与显示偏好", Route.SettingsAppearance),
    Tuple4(LuzzyIcons.Info.res, "关于", "版本信息与开源许可", Route.SettingsAbout),
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
        Text("模型", style = MaterialTheme.typography.titleSmall)
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
// 关于
// ---------------------------------------------------------------------------

@Composable
fun SettingsAboutPage(onBack: () -> Unit) {
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
            Text("关于", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(LuzzySpacing.LG), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(LuzzySpacing.LG))
            androidx.compose.foundation.Image(
                painter = painterResource(com.luzzymeow.luzzyrp.R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.size(LuzzySpacing.MD))
            Text("LuzzyRP", style = MaterialTheme.typography.headlineSmall)
            Text(
                "v" + com.luzzymeow.luzzyrp.BuildConfig.VERSION_NAME + " · versionCode " + com.luzzymeow.luzzyrp.BuildConfig.VERSION_CODE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(LuzzySpacing.MD))
            Text("每次对话，都像一本有你的小说。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(LuzzySpacing.LG))
            Text(
                "技术栈：Kotlin 2.4 · Jetpack Compose · Material 3 Expressive\n" +
                    "许可证：CC BY-NC 4.0\n" +
                    "github.com/LuzzyMeow/LuzzyRP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

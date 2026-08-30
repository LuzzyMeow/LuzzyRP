package com.luzzymeow.luzzyrp.ui.pages.presets

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.PromptPreset
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.repository.PromptPresetRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 预设列表 ViewModel（7.2）。 */
class PresetsViewModel(
    private val presetRepository: PromptPresetRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val presets: StateFlow<List<PromptPreset>> = presetRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<com.luzzymeow.luzzyrp.data.datastore.Settings?> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setActive(id: String) {
        viewModelScope.launch { settingsStore.update { it.copy(activePresetId = id) } }
    }

    fun clearActive() {
        viewModelScope.launch { settingsStore.update { it.copy(activePresetId = "") } }
    }

    fun duplicate(preset: PromptPreset) {
        viewModelScope.launch { presetRepository.duplicate(preset) }
    }

    fun delete(preset: PromptPreset) {
        viewModelScope.launch {
            presetRepository.delete(preset.id)
            if (settings.value?.activePresetId == preset.id) clearActive()
        }
    }
}

/** 预设列表页：启用切换（单选激活）/ 编辑 / 复制 / 删除。 */
@Composable
fun PresetsPage(onBack: () -> Unit, onOpenDetail: (String) -> Unit) {
    val viewModel: PresetsViewModel = org.koin.androidx.compose.koinViewModel()
    val presets by viewModel.presets.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activeId = settings?.activePresetId.orEmpty()

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
            Text("预设", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onOpenDetail("") }) {
                Icon(painterResource(LuzzyIcons.Plus.res), contentDescription = "新建预设",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (presets.isEmpty()) {
            Text(
                "暂无预设。预设是一组可开关的注入提示词条目（注入至 system），适合沉淀角色扮演规范。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(LuzzySpacing.XL),
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
        ) {
            items(presets, key = { it.id }) { preset ->
                AuroraSurface(
                    onClick = { onOpenDetail(preset.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${preset.entries.size} 条目 · ${preset.entries.count { it.enabled }} 启用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "启用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(horizontal = LuzzySpacing.XS))
                        Switch(
                            checked = activeId == preset.id,
                            onCheckedChange = { checked ->
                                if (checked) viewModel.setActive(preset.id) else viewModel.clearActive()
                            },
                        )
                        IconButton(onClick = { viewModel.duplicate(preset) }) {
                            Icon(painterResource(LuzzyIcons.Copy.res), contentDescription = "复制",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.delete(preset) }) {
                            Icon(painterResource(LuzzyIcons.Trash.res), contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}


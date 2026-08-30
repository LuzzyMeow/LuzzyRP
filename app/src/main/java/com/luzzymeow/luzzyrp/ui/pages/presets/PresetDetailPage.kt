package com.luzzymeow.luzzyrp.ui.pages.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.InjectionPosition
import com.luzzymeow.luzzyrp.core.model.PromptPreset
import com.luzzymeow.luzzyrp.core.model.PresetEntry
import com.luzzymeow.luzzyrp.core.model.PromptRole
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.repository.PromptPresetRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** 预设编辑 ViewModel：预设详情 + 条目增删改 + 保存。 */
class PresetDetailViewModel(
    private val presetId: String,
    private val presetRepository: PromptPresetRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val preset = MutableStateFlow<PromptPreset?>(null)
    val saved = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            preset.value = if (presetId.isBlank()) {
                PromptPreset(id = "", name = "", createdAt = System.currentTimeMillis())
            } else {
                presetRepository.getById(presetId)
            }
        }
    }

    fun update(transform: (PromptPreset) -> PromptPreset) {
        preset.value = preset.value?.let(transform)
        saved.value = false
    }

    fun addEntry() {
        update { p ->
            p.copy(entries = p.entries + PresetEntry(
                id = UUID.randomUUID().toString(),
                name = "新条目 ${p.entries.size + 1}",
                content = "",
            ))
        }
    }

    fun removeEntry(entryId: String) {
        update { p -> p.copy(entries = p.entries.filterNot { it.id == entryId }) }
    }

    fun save() {
        val current = preset.value ?: return
        if (current.name.isBlank()) return
        viewModelScope.launch {
            val stored = presetRepository.save(current)
            preset.value = stored
            saved.value = true
        }
    }
}

/** 位置选项（含 @Depth）。 */
private val POSITION_OPTIONS = listOf(
    "↑Char" to InjectionPosition.BEFORE_CHAR,
    "↓Char" to InjectionPosition.AFTER_CHAR,
    "↑EM" to InjectionPosition.BEFORE_EXAMPLE,
    "↓EM" to InjectionPosition.AFTER_EXAMPLE,
    "@Depth" to InjectionPosition.AT_DEPTH,
)

@Composable
fun PresetDetailPage(presetId: String, onBack: () -> Unit) {
    val viewModel: PresetDetailViewModel =
        org.koin.androidx.compose.koinViewModel { org.koin.core.parameter.parametersOf(presetId) }
    val preset by viewModel.preset.collectAsState()
    val saved by viewModel.saved.collectAsState()

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
            Text("编辑预设", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::addEntry) {
                Icon(painterResource(LuzzyIcons.Plus.res), contentDescription = "添加条目",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (saved) {
            Text(
                "已保存", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG),
            )
        }

        val p = preset ?: return
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
        ) {
            item {
                LuzzyTextField(
                    p.name, { n -> viewModel.update { it.copy(name = n) } },
                    placeholder = "预设名称 *", modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(LuzzySpacing.MD))
                Text("提示词条目", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(LuzzySpacing.SM))
            }
            itemsIndexed(p.entries, key = { _, e -> e.id }) { _, entry ->
                AuroraSurface(modifier = Modifier.fillMaxWidth().padding(vertical = LuzzySpacing.XS)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LuzzyTextField(
                            entry.name,
                            { n -> viewModel.update { pre ->
                                pre.copy(entries = pre.entries.map {
                                    if (it.id == entry.id) it.copy(name = n) else it
                                })
                            } },
                            placeholder = "条目名",
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { checked ->
                                viewModel.update { pre ->
                                    pre.copy(entries = pre.entries.map {
                                        if (it.id == entry.id) it.copy(enabled = checked) else it
                                    })
                                }
                            },
                        )
                        IconButton(onClick = { viewModel.removeEntry(entry.id) }) {
                            Icon(painterResource(LuzzyIcons.Close.res), contentDescription = "删除条目",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        POSITION_OPTIONS.forEach { (label, position) ->
                            val selected = entry.position == position
                            AuroraSurface(
                                onClick = {
                                    viewModel.update { pre ->
                                        pre.copy(entries = pre.entries.map {
                                            if (it.id == entry.id) it.copy(position = position) else it
                                        })
                                    }
                                },
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = LuzzySpacing.SM, vertical = LuzzySpacing.XS),
                                modifier = Modifier.padding(end = LuzzySpacing.XS),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (entry.position == InjectionPosition.AT_DEPTH) {
                            LuzzyTextField(
                                entry.depth.toString(),
                                { d -> viewModel.update { pre ->
                                    pre.copy(entries = pre.entries.map {
                                        if (it.id == entry.id) it.copy(depth = d.toIntOrNull() ?: 4) else it
                                    })
                                } },
                                modifier = Modifier.size(width = 72.dp, height = 52.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(LuzzySpacing.XS))
                    LuzzyTextField(
                        entry.content,
                        { c -> viewModel.update { pre ->
                            pre.copy(entries = pre.entries.map {
                                if (it.id == entry.id) it.copy(content = c) else it
                            })
                        } },
                        placeholder = "内容（原样注入 system）",
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(LuzzySpacing.SM))
            }
            item {
                Spacer(Modifier.size(LuzzySpacing.MD))
                androidx.compose.material3.Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存") }
                Spacer(Modifier.size(LuzzySpacing.XXXL))
            }
        }
    }
}

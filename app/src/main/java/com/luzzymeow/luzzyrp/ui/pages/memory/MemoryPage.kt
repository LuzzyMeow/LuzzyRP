package com.luzzymeow.luzzyrp.ui.pages.memory

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
import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.MemoryItem
import com.luzzymeow.luzzyrp.data.repository.MemoryRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ACE 长期记忆页 ViewModel。 */
class MemoryViewModel(
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    val memories: StateFlow<List<MemoryItem>> = memoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggle(item: MemoryItem) {
        viewModelScope.launch { memoryRepository.setActive(item.id, !item.active) }
    }

    fun delete(item: MemoryItem) {
        viewModelScope.launch { memoryRepository.delete(item.id) }
    }
}

/** 长期记忆管理页：条目列表（评分/计数）+ 启停 + 删除。 */
@Composable
fun MemoryPage(onBack: () -> Unit) {
    val viewModel: MemoryViewModel = org.koin.androidx.compose.koinViewModel()
    val memories by viewModel.memories.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.luzzymeow.luzzyrp.ui.components.AuroraTopBar(
                title = "长期记忆",
                onBack = onBack,
            )
            Text("${memories.size} 条", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = LuzzySpacing.LG))
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
        ) {
            items(memories, key = { it.id }) { item ->
                AuroraSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.content, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.size(2.dp))
                                Text(
                                    "${item.category} · 有用 ${item.helpful} / 无关 ${item.neutral} / 冲突 ${item.harmful}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = item.active, onCheckedChange = { viewModel.toggle(item) })
                            IconButton(onClick = { viewModel.delete(item) }) {
                                Icon(painterResource(LuzzyIcons.Trash.res), contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


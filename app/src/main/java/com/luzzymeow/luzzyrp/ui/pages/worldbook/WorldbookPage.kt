package com.luzzymeow.luzzyrp.ui.pages.worldbook

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.luzzymeow.luzzyrp.core.model.PromptRole
import com.luzzymeow.luzzyrp.core.model.RecallStrategy
import com.luzzymeow.luzzyrp.core.model.Worldbook
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 世界书二级页 ViewModel（4.6/5.4）。
 * cardId 非空 → 管理该卡的世界书（无则首次进入自动创建）；条目 CRUD 全字段。
 */
class WorldbookViewModel(
    private val cardId: String?,
    private val worldbookRepository: WorldbookRepository,
    private val cardRepository: CharacterCardRepository,
) : ViewModel() {

    /** 当前管理的世界书 id。 */
    val bookId = MutableStateFlow<String?>(null)

    val book: StateFlow<Worldbook?> = bookId
        .map { id -> id?.let { worldbookRepository.getById(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            if (cardId != null) {
                val card = cardRepository.getById(cardId)
                val existing = card?.worldbookId
                bookId.value = existing ?: run {
                    val created = cardRepository.createWorldbook("${card?.name ?: "角色"}的世界书", cardId)
                    card?.let { cardRepository.save(it.copy(worldbookId = created)) }
                    created
                }
            }
        }
    }

    fun toggleBook(enabled: Boolean) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            worldbookRepository.getById(id)?.let { worldbookRepository.save(it.copy(enabled = enabled)) }
        }
    }

    fun renameBook(name: String) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            worldbookRepository.getById(id)?.let { worldbookRepository.save(it.copy(name = name)) }
        }
    }

    fun addEntry() {
        val id = bookId.value ?: return
        viewModelScope.launch {
            worldbookRepository.saveEntry(
                WorldbookEntry(
                    id = UUID.randomUUID().toString(),
                    worldbookId = id,
                    comment = "新条目",
                    content = "",
                    strategies = listOf(RecallStrategy.KEYWORD),
                )
            )
        }
    }

    fun updateEntry(entry: WorldbookEntry) {
        viewModelScope.launch { worldbookRepository.saveEntry(entry) }
    }

    fun toggleEntry(entry: WorldbookEntry) {
        viewModelScope.launch { worldbookRepository.saveEntry(entry.copy(enabled = !entry.enabled)) }
    }

    fun deleteEntry(entry: WorldbookEntry) {
        viewModelScope.launch { worldbookRepository.deleteEntry(entry.id) }
    }

    fun importSillyTavern(uri: android.net.Uri) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            runCatching { cardRepository.importWorldbookJson(id, uri) }
                .onSuccess { message.value = "已导入 $it 条（外部作者设定默认启用）" }
                .onFailure { message.value = "导入失败：${it.message}" }
        }
    }

    fun deleteBook() {
        val id = bookId.value ?: return
        viewModelScope.launch {
            worldbookRepository.delete(id)
            bookId.value = null
            message.value = "世界书已删除"
        }
    }
}

/** 世界书二级页：书信息（名称/启停/删除）+ 条目列表（全字段编辑，点卡片展开）。 */
@Composable
fun WorldbookPage(cardId: String?, onBack: () -> Unit) {
    val viewModel: WorldbookViewModel =
        org.koin.androidx.compose.koinViewModel { org.koin.core.parameter.parametersOf(cardId) }
    val book by viewModel.book.collectAsState()
    val message by viewModel.message.collectAsState()
    var editingEntryId by remember { mutableStateOf<String?>(null) }

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
            Text("世界书", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::addEntry) {
                Icon(painterResource(LuzzyIcons.Plus.res), contentDescription = "新增条目",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG))
        }

        val b = book
        if (b == null) {
            Column(Modifier.padding(LuzzySpacing.XL)) {
                Text("尚未选择世界书", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(LuzzySpacing.LG))
                androidx.compose.material3.TextButton(onClick = onBack) { Text("返回") }
            }
            return
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
        ) {
            item {
                AuroraSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LuzzyTextField(
                                b.name, { viewModel.renameBook(it) },
                                placeholder = "世界书名称",
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(LuzzySpacing.SM))
                            Switch(checked = b.enabled, onCheckedChange = viewModel::toggleBook)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${b.entries.size} 条目",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(onClick = viewModel::deleteBook) {
                                Text("删除世界书", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.size(LuzzySpacing.SM))
            }
            items(b.entries, key = { it.id }) { entry ->
                AuroraSurface(
                    onClick = {
                        editingEntryId = if (editingEntryId == entry.id) null else entry.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.comment.ifBlank { "（未命名条目）" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                strategyLabel(entry) + " · " + positionLabel(entry) +
                                    if (entry.probability < 100) " · ${entry.probability}%" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = entry.enabled, onCheckedChange = { viewModel.toggleEntry(entry) })
                        IconButton(onClick = { viewModel.deleteEntry(entry) }) {
                            Icon(painterResource(LuzzyIcons.Trash.res), contentDescription = "删除条目",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (editingEntryId == entry.id) {
                        Spacer(Modifier.size(LuzzySpacing.SM))
                        EntryEditor(entry = entry, onChange = viewModel::updateEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditor(entry: WorldbookEntry, onChange: (WorldbookEntry) -> Unit) {
    Column {
        LuzzyTextField(
            entry.comment, { onChange(entry.copy(comment = it)) },
            placeholder = "条目名称",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(LuzzySpacing.XS))
        LuzzyTextField(
            entry.content, { onChange(entry.copy(content = it)) },
            placeholder = "内容（注入文本）",
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(LuzzySpacing.SM))
        Text("激活策略", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StrategyChip(
                label = "常驻激活", selected = RecallStrategy.CONSTANT in entry.strategies,
                onClick = {
                    val has = RecallStrategy.CONSTANT in entry.strategies
                    val next = if (has) entry.strategies - RecallStrategy.CONSTANT
                    else entry.strategies + RecallStrategy.CONSTANT
                    onChange(entry.copy(strategies = next.ifEmpty { listOf(RecallStrategy.KEYWORD) }))
                },
            )
            Spacer(Modifier.size(LuzzySpacing.XS))
            StrategyChip(
                label = "关键词激活", selected = RecallStrategy.KEYWORD in entry.strategies,
                onClick = {
                    val has = RecallStrategy.KEYWORD in entry.strategies
                    val next = if (has) entry.strategies - RecallStrategy.KEYWORD
                    else entry.strategies + RecallStrategy.KEYWORD
                    onChange(entry.copy(strategies = next.ifEmpty { listOf(RecallStrategy.CONSTANT) }))
                },
            )
        }
        if (RecallStrategy.KEYWORD in entry.strategies) {
            Spacer(Modifier.size(LuzzySpacing.XS))
            LuzzyTextField(
                entry.keys.joinToString(","),
                { raw -> onChange(entry.copy(keys = raw.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() })) },
                placeholder = "关键词（逗号分隔，任一命中触发）",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(LuzzySpacing.XS))
        Text("激活概率：${entry.probability}%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = entry.probability.toFloat(),
            onValueChange = { onChange(entry.copy(probability = it.toInt())) },
            valueRange = 0f..100f,
        )
        Spacer(Modifier.size(LuzzySpacing.XS))
        Text("注入位置", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            POSITION_OPTIONS.forEach { (label, position) ->
                StrategyChip(
                    label = label,
                    selected = entry.position == position,
                    onClick = { onChange(entry.copy(position = position)) },
                )
                Spacer(Modifier.size(LuzzySpacing.XS))
            }
        }
        if (entry.position == InjectionPosition.AT_DEPTH) {
            Spacer(Modifier.size(LuzzySpacing.XS))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LuzzyTextField(
                    entry.depth.toString(),
                    { d -> onChange(entry.copy(depth = d.toIntOrNull() ?: 4)) },
                    modifier = Modifier.size(width = 88.dp, height = 52.dp),
                )
                Spacer(Modifier.size(LuzzySpacing.SM))
                PromptRole.entries.forEach { role ->
                    StrategyChip(
                        label = role.name,
                        selected = entry.depthRole == role,
                        onClick = { onChange(entry.copy(depthRole = role)) },
                    )
                    Spacer(Modifier.size(LuzzySpacing.XS))
                }
            }
        }
    }
}

@Composable
private fun StrategyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AuroraSurface(
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = LuzzySpacing.SM, vertical = LuzzySpacing.XS),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val POSITION_OPTIONS = listOf(
    "↑Char" to InjectionPosition.BEFORE_CHAR,
    "↓Char" to InjectionPosition.AFTER_CHAR,
    "↑EM" to InjectionPosition.BEFORE_EXAMPLE,
    "↓EM" to InjectionPosition.AFTER_EXAMPLE,
    "@Depth" to InjectionPosition.AT_DEPTH,
)

private fun strategyLabel(entry: WorldbookEntry): String = buildList {
    if (RecallStrategy.CONSTANT in entry.strategies) add("常驻")
    if (RecallStrategy.KEYWORD in entry.strategies) add("关键词")
    if (RecallStrategy.VECTOR in entry.strategies) add("向量")
}.joinToString("+").ifBlank { "未启用" }

private fun positionLabel(entry: WorldbookEntry): String = when (entry.position) {
    InjectionPosition.BEFORE_CHAR -> "↑Char"
    InjectionPosition.AFTER_CHAR -> "↓Char"
    InjectionPosition.BEFORE_EXAMPLE -> "↑EM"
    InjectionPosition.AFTER_EXAMPLE -> "↓EM"
    InjectionPosition.AT_DEPTH -> "@Depth(${entry.depthRole.name.lowercase()}/${entry.depth})"
}

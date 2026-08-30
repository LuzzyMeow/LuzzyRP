package com.luzzymeow.luzzyrp.ui.pages.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 角色卡详情 ViewModel：读取 + 编辑 + 导出。 */
class CharacterDetailViewModel(
    private val cardId: String,
    private val cardRepository: CharacterCardRepository,
) : ViewModel() {

    val card = MutableStateFlow<CharacterCard?>(null)
    val exportMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { card.value = cardRepository.getById(cardId) }
    }

    fun save(name: String, description: String, personality: String, scenario: String, firstMes: String) {
        val current = card.value ?: return
        if (current.readonly) return
        viewModelScope.launch {
            card.value = cardRepository.save(
                current.copy(
                    name = name.ifBlank { current.name },
                    description = description,
                    personality = personality,
                    scenario = scenario,
                    firstMes = firstMes,
                )
            )
            exportMessage.value = "已保存"
        }
    }

    fun exportPng() {
        viewModelScope.launch {
            runCatching { cardRepository.exportToPng(cardId) }
                .onSuccess { exportMessage.value = "PNG 已导出：${it.absolutePath}" }
                .onFailure { exportMessage.value = "导出失败：${it.message}" }
        }
    }

    fun exportJson() {
        viewModelScope.launch {
            runCatching { cardRepository.exportToJson(cardId) }
                .onSuccess { exportMessage.value = "JSON 已导出：${it.absolutePath}" }
                .onFailure { exportMessage.value = "导出失败：${it.message}" }
        }
    }
}

@Composable
fun CharacterDetailPage(cardId: String, onBack: () -> Unit) {
    val viewModel: CharacterDetailViewModel =
        org.koin.androidx.compose.koinViewModel { org.koin.core.parameter.parametersOf(cardId) }
    val card by viewModel.card.collectAsState()
    val message by viewModel.exportMessage.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var firstMes by remember { mutableStateOf("") }

    LaunchedEffect(card) {
        card?.let {
            if (name.isBlank() && description.isBlank()) {
                name = it.name
                description = it.description
                personality = it.personality
                scenario = it.scenario
                firstMes = it.firstMes
            }
        }
    }

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
            Text("角色详情", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::exportJson) {
                Icon(painterResource(LuzzyIcons.Code.res), contentDescription = "导出 JSON",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::exportPng) {
                Icon(painterResource(LuzzyIcons.Share.res), contentDescription = "导出 PNG",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        message?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LuzzySpacing.LG),
        ) {
            card?.let { c ->
                if (c.readonly) {
                    AuroraSurface(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "内置角色卡（只读）：内容不可编辑，可直接开始对话。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.size(LuzzySpacing.MD))
                }
            }

            LuzzyTextField(name, { name = it }, placeholder = "名字", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(description, { description = it }, placeholder = "角色设定",
                minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(personality, { personality = it }, placeholder = "性格",
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(scenario, { scenario = it }, placeholder = "场景",
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(firstMes, { firstMes = it }, placeholder = "开场白",
                minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.LG))

            if (card?.readonly != true) {
                androidx.compose.material3.Button(
                    onClick = { viewModel.save(name, description, personality, scenario, firstMes) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

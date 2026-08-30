package com.luzzymeow.luzzyrp.ui.pages.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 角色卡库 ViewModel：列表 + PNG/JSON 导入。 */
class CharactersViewModel(
    private val cardRepository: CharacterCardRepository,
) : ViewModel() {

    val cards: StateFlow<List<CharacterCard>> = cardRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 导入结果提示（错误回显）。 */
    val importMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun importPng(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { cardRepository.importFromPng(uri) }
                .onSuccess { importMessage.value = "已导入角色卡：${it.name}" }
                .onFailure { importMessage.value = "导入失败：${it.message}" }
        }
    }

    fun importJson(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { cardRepository.importFromJson(uri) }
                .onSuccess { importMessage.value = "已导入角色卡：${it.name}" }
                .onFailure { importMessage.value = "导入失败：${it.message}" }
        }
    }

    fun createBlank(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { cardRepository.createBlank("新角色") }
                .onSuccess { onReady(it.id) }
                .onFailure { importMessage.value = "创建失败：${it.message}" }
        }
    }

    fun clearMessage() { importMessage.value = null }
}

@Composable
fun CharactersPage(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val viewModel: CharactersViewModel = org.koin.androidx.compose.koinViewModel()
    val cards by viewModel.cards.collectAsState()
    val message by viewModel.importMessage.collectAsState()

    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importPng)
    }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importJson)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons.Back.res),
                    contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("角色卡库", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onOpenDetail("new") }) {
                Icon(painterResource(com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons.Plus.res),
                    contentDescription = "新建角色卡", tint = MaterialTheme.colorScheme.tertiary)
            }
            IconButton(onClick = { jsonPicker.launch("application/json") }) {
                Icon(painterResource(com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons.Code.res),
                    contentDescription = "导入 JSON", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { pngPicker.launch("image/png") }) {
                Icon(painterResource(com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons.Image.res),
                    contentDescription = "导入 PNG", tint = MaterialTheme.colorScheme.primary)
            }
        }

        message?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.LG, vertical = LuzzySpacing.XS),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(LuzzySpacing.SM),
                onClick = viewModel::clearMessage,
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(LuzzySpacing.SM))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
            verticalArrangement = Arrangement.spacedBy(LuzzySpacing.MD),
            horizontalArrangement = Arrangement.spacedBy(LuzzySpacing.MD),
        ) {
            items(cards, key = { it.id }) { card ->
                CharacterGridCell(card = card, onClick = { onOpenDetail(card.id) })
            }
        }
    }
}

@Composable
private fun CharacterGridCell(card: CharacterCard, onClick: () -> Unit) {
    AuroraSurface(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.MD),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(84.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (card.avatarPath != null) {
                    AsyncImage(
                        model = java.io.File(card.avatarPath),
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        card.name.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.size(LuzzySpacing.SM))
            Text(
                card.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (card.readonly) {
                Text("内置", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package com.luzzymeow.luzzyrp.ui.pages.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LocalExtendColors
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 角色卡详情/编辑 ViewModel（5.2/5.4）。
 * 字段全集：头像/聊天背景(透明度)/角色设定/开场白(<CUT>)/世界书入口。
 */
class CharacterDetailViewModel(
    private val context: android.content.Context,
    private val cardId: String,
    private val cardRepository: CharacterCardRepository,
) : ViewModel() {

    val card = MutableStateFlow<CharacterCard?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            card.value = if (cardId == "new") {
                // 新建模式：先落一张空白卡（保存时命名）
                cardRepository.createBlank("新角色")
            } else {
                cardRepository.getById(cardId)
            }
        }
    }

    fun save(
        name: String, description: String, personality: String, scenario: String,
        firstMes: String, backgroundOpacity: Int,
    ) {
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
                    chatBackground = current.chatBackground.copy(opacity = backgroundOpacity),
                )
            )
            message.value = "已保存"
        }
    }

    fun setAvatar(uri: android.net.Uri) {
        val current = card.value ?: return
        if (current.readonly) return
        viewModelScope.launch {
            runCatching {
                val tmp = copyUriToCache(current.id, "avatar", uri)
                cardRepository.saveAvatarFromFile(current.id, tmp)
                card.value = cardRepository.getById(current.id)
                message.value = "头像已更新"
            }.onFailure { message.value = "头像设置失败：${it.message}" }
        }
    }

    fun setBackground(uri: android.net.Uri) {
        val current = card.value ?: return
        if (current.readonly) return
        viewModelScope.launch {
            runCatching {
                val tmp = copyUriToCache(current.id, "bg", uri)
                cardRepository.saveChatBackground(current.id, tmp)
                card.value = cardRepository.getById(current.id)
                message.value = "聊天背景已更新"
            }.onFailure { message.value = "背景设置失败：${it.message}" }
        }
    }

    fun exportPng() {
        val current = card.value ?: return
        viewModelScope.launch {
            runCatching { cardRepository.exportToPng(current.id) }
                .onSuccess { message.value = "PNG 已导出：${it.absolutePath}" }
                .onFailure { message.value = "导出失败：${it.message}" }
        }
    }

    fun exportJson() {
        val current = card.value ?: return
        viewModelScope.launch {
            runCatching { cardRepository.exportToJson(current.id) }
                .onSuccess { message.value = "JSON 已导出：${it.absolutePath}" }
                .onFailure { message.value = "导出失败：${it.message}" }
        }
    }

    /** URI → 缓存文件（头像/背景导入的统一入口）。 */
    private fun copyUriToCache(cardId: String, kind: String, uri: android.net.Uri): File {
        val dest = File(context.cacheDir, "import/$cardId-$kind-${System.currentTimeMillis()}").apply {
            parentFile?.mkdirs()
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取所选文件")
        return dest
    }
}

package com.luzzymeow.luzzyrp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 会话列表（家）状态（P2：接真实 Room 数据）。
 *
 * 首次进入若无助手 → 自动建一个默认助手（[AssistantRepository.ensureDefaultAssistant]），
 * 避免「空列表 + 无处可去」；会话按日期分组（今天 / 昨天 / 7 天内 / 本月 / 更早）。
 */
class AssistantListViewModel(private val runtime: AssistantRuntime) : ViewModel() {

    private val _state = MutableStateFlow(AssistantListState())
    val state: StateFlow<AssistantListState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val default = runCatching { runtime.repository.ensureDefaultAssistant() }.getOrNull()
            val assistants = runCatching { runtime.repository.assistants() }.getOrDefault(emptyList())
            val selected = _state.value.selectedId?.takeIf { id -> assistants.any { it.id == id } }
                ?: default?.id
                ?: assistants.firstOrNull()?.id
            val conversations = selected?.let { id ->
                runCatching { runtime.repository.conversations(id) }.getOrDefault(emptyList())
            } ?: emptyList()
            _state.value = AssistantListState(
                assistants = assistants.map { it.toUi() },
                selectedId = selected,
                conversations = conversations.map { it.toUi() },
                loading = false,
            )
        }
    }

    fun select(assistantId: String) {
        _state.value = _state.value.copy(selectedId = assistantId)
        refresh()
    }

    fun createConversation(onCreated: (String) -> Unit) {
        val assistantId = _state.value.selectedId ?: return
        viewModelScope.launch {
            val created = runCatching { runtime.repository.createConversation(assistantId) }.getOrNull() ?: return@launch
            refresh()
            onCreated(created.id)
        }
    }

    fun createAssistant(name: String) {
        viewModelScope.launch {
            runCatching { runtime.repository.createAssistant(name) }
            refresh()
        }
    }

    private fun AssistantEntity.toUi() = AssistantUi(
        id = id,
        name = name,
        initial = name.take(1),
        modelLabel = modelId?.substringAfterLast("::") ?: "未配置模型",
        lastTitle = null,
        lastTimeLabel = null,
    )

    private fun ConversationEntity.toUi() = ConversationUi(
        id = id,
        assistantId = assistantId,
        title = title,
        summary = summary ?: "",
        updatedAtLabel = relativeLabel(updatedAt),
        group = groupOf(updatedAt),
    )

    private fun groupOf(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.isAfter(today.minusDays(7)) -> "7 天内"
            date.year == today.year && date.month == today.month -> "本月"
            else -> "更早"
        }
    }

    private fun relativeLabel(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        return when (time.toLocalDate()) {
            today -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "昨天"
            else -> time.format(DateTimeFormatter.ofPattern("M 月 d 日"))
        }
    }
}

data class AssistantListState(
    val assistants: List<AssistantUi> = emptyList(),
    val selectedId: String? = null,
    val conversations: List<ConversationUi> = emptyList(),
    val loading: Boolean = true,
)

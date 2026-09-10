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
 * [用户 2026-09-10] **不再自动创建内置预设助手**（原内置「阿墨」已删除）——零助手时由
 * UI 空态引导新建（[createAssistant]），避免"删了预设却无处可去"。
 * 会话按日期分组（今天 / 昨天 / 7 天内 / 本月 / 更早）。
 */
class AssistantListViewModel(private val runtime: AssistantRuntime) : ViewModel() {

    private val _state = MutableStateFlow(AssistantListState())
    val state: StateFlow<AssistantListState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val assistants = runCatching { runtime.repository.assistants() }.getOrDefault(emptyList())
            val selected = _state.value.selectedId?.takeIf { id -> assistants.any { it.id == id } }
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

    /** 删除助手及其从属数据；若删的是当前选中项，refresh() 会自动回落到列表首个或空态。 */
    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            runCatching { runtime.repository.deleteAssistant(assistantId) }
            if (_state.value.selectedId == assistantId) {
                _state.value = _state.value.copy(selectedId = null)
            }
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

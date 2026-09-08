package com.luzzymeow.luzzyrp.assistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import com.luzzymeow.luzzyrp.assistant.ui.model.MemoryUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 记忆页状态（P2：接真实 Room 记忆）。
 *
 * 模式条数值来自助手设置（TopK / 阈值 / 混合最近条数），与检索实现保持一致。
 */
class MemoryViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryState())
    val state: StateFlow<MemoryState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val assistant = runCatching { runtime.repository.assistant(assistantId) }.getOrNull()
            val hits = runCatching { runtime.memoryStore.list(assistantId, null, 200) }.getOrDefault(emptyList())
            _state.value = MemoryState(
                memories = hits.map { hit ->
                    MemoryUi(
                        id = hit.id,
                        typeLabel = typeLabel(hit.type),
                        content = hit.content,
                        sourceLabel = relativeLabel(hit.createdAtMillis),
                        timeLabel = relativeLabel(hit.createdAtMillis),
                        similarity = hit.similarity,
                    )
                },
                modeLabel = modeLabel(assistant?.memoryMode),
                topK = assistant?.memoryTopK ?: 8,
                threshold = assistant?.memoryThreshold ?: 0.35f,
                recent = 5,
            )
        }
    }

    private fun modeLabel(mode: String?): String = when (mode) {
        "embed" -> "向量"
        "hybrid" -> "混合"
        else -> "全文"
    }

    private fun typeLabel(type: String): String = when (type) {
        "fact" -> "事实"
        "preference" -> "偏好"
        "task" -> "任务"
        else -> "笔记"
    }

    private fun relativeLabel(millis: Long): String {
        if (millis <= 0L) return "—"
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

data class MemoryState(
    val memories: List<MemoryUi> = emptyList(),
    val modeLabel: String = "全文",
    val topK: Int = 8,
    val threshold: Float = 0.35f,
    val recent: Int = 5,
)

package com.luzzymeow.luzzyrp.assistant.ui.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import com.luzzymeow.luzzyrp.assistant.domain.skill.SkillParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 技能页状态（PLAN §8.2）。
 *
 * 两个开关维度：**全局启用**（所有助手注入）与**本助手绑定**（仅当前助手注入）；
 * 同名技能以「用户导入 > 内置」覆盖（导入时按名合并）。
 */
class SkillsViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SkillsState())
    val state: StateFlow<SkillsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            // 首次进入导入内置技能（幂等）
            runCatching { runtime.skillRepository.importBuiltinsIfNeeded() }
            val skills = runCatching { runtime.skillRepository.all() }.getOrDefault(emptyList())
            val bindings = runCatching { runtime.skillRepository.bindingsFor(assistantId) }
                .getOrDefault(emptyList())
                .associateBy { it.skillId }
            _state.value = SkillsState(
                skills = skills.map { entity ->
                    SkillRow(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        sourceLabel = when (entity.source) {
                            "builtin" -> "内置"
                            "url" -> "链接"
                            else -> "导入"
                        },
                        enabledGlobal = entity.enabledGlobal,
                        boundToAssistant = bindings[entity.id]?.enabled == true,
                    )
                },
                loading = false,
            )
        }
    }

    fun toggleGlobal(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.skillRepository.setEnabledGlobal(skillId, enabled) }
            refresh()
        }
    }

    fun toggleBinding(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.skillRepository.setBinding(skillId, assistantId, enabled) }
            refresh()
        }
    }

    fun delete(skillId: String) {
        viewModelScope.launch {
            runCatching { runtime.skillRepository.delete(skillId) }
            refresh()
        }
    }

    /** 粘贴 / 导入 Markdown；解析失败把原因写进状态（不静默吞）。 */
    fun importMarkdown(markdown: String, fallbackName: String? = null) {
        viewModelScope.launch {
            runCatching { runtime.skillRepository.importMarkdown(markdown, fallbackName) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "已导入：${it.name}")
                    refresh()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        message = if (error is SkillParseException) "导入失败：${error.message}" else "导入失败：${error.message}",
                    )
                }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

data class SkillsState(
    val skills: List<SkillRow> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

data class SkillRow(
    val id: String,
    val name: String,
    val description: String,
    val sourceLabel: String,
    val enabledGlobal: Boolean,
    val boundToAssistant: Boolean,
)

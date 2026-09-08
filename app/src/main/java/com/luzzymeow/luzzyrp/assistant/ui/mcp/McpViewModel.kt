package com.luzzymeow.luzzyrp.assistant.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpException
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MCP 页状态（PLAN §9）。
 *
 * 导入流程：粘贴 JSON → [McpViewModel.importJson] 解析落库 → 列表出现 →
 * 用户开启「全局启用」→ [McpViewModel.connect] 握手并注册工具（T2，默认关闭 + 逐调用审批）。
 */
class McpViewModel(private val runtime: AssistantRuntime) : ViewModel() {

    private val _state = MutableStateFlow(McpState())
    val state: StateFlow<McpState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val servers = runCatching { runtime.mcpRepository.all() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                servers = servers.map { entity ->
                    McpRow(
                        id = entity.id,
                        name = entity.name,
                        transportLabel = entity.transport.uppercase(),
                        url = entity.url ?: entity.command ?: "",
                        enabledGlobal = entity.enabledGlobal,
                        lastError = entity.lastError,
                        connected = entity.lastConnectedAt != null && entity.lastError == null,
                    )
                },
                loading = false,
            )
        }
    }

    fun importJson(raw: String) {
        if (raw.isBlank()) return
        viewModelScope.launch {
            runCatching { runtime.mcpRepository.importJson(raw) }
                .onSuccess { imported ->
                    _state.value = _state.value.copy(
                        message = "已导入 ${imported.size} 个服务器：" + imported.joinToString("、") { it.name },
                    )
                    refresh()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        message = if (error is McpException) "导入失败：${error.message}" else "导入失败：${error.message}",
                    )
                }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.mcpRepository.setEnabledGlobal(id, enabled) }
            if (enabled) connect(id) else {
                runCatching { runtime.mcpRepository.unregisterTools(id) }
                refresh()
            }
        }
    }

    fun connect(id: String) {
        viewModelScope.launch {
            val (ok, message) = runCatching { runtime.mcpRepository.connect(id) }
                .getOrDefault(false to "连接异常")
            _state.value = _state.value.copy(message = message, connectingId = null)
            refresh()
            if (!ok) Unit
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { runtime.mcpRepository.delete(id) }
            refresh()
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

data class McpState(
    val servers: List<McpRow> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    val connectingId: String? = null,
)

data class McpRow(
    val id: String,
    val name: String,
    val transportLabel: String,
    val url: String,
    val enabledGlobal: Boolean,
    val lastError: String?,
    val connected: Boolean,
)

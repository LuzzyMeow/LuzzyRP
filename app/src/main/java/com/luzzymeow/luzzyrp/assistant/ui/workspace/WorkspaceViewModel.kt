package com.luzzymeow.luzzyrp.assistant.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 工作区页状态（PLAN §10.1）。
 *
 * 只暴露 `files/` 子树（Agent 读写区）；`attachments/`、`exports/` 由导入导出流程管理。
 * 路径越界由 [com.luzzymeow.luzzyrp.assistant.data.workspace.WorkspaceManager] 抛异常，
 * 本层捕获后转为可读提示（不崩、不静默）。
 */
class WorkspaceViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val path = _state.value.currentPath
            val entries = runCatching { runtime.workspaceManager.list(assistantId, path) }
                .fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        _state.value = _state.value.copy(message = "读取失败：${error.message}")
                        emptyList()
                    },
                )
            val usage = runCatching { runtime.workspaceManager.quotaUsageBytes(assistantId) }.getOrDefault(0L)
            _state.value = _state.value.copy(
                entries = entries.map { entry ->
                    FileRow(
                        name = entry.name,
                        relativePath = entry.relativePath,
                        isDirectory = entry.isDirectory,
                        sizeLabel = if (entry.isDirectory) "目录" else humanSize(entry.sizeBytes),
                        modifiedLabel = entry.lastModified,
                    )
                },
                usageLabel = humanSize(usage) + " / 2 GB",
                loading = false,
            )
        }
    }

    fun enter(relativePath: String) {
        _state.value = _state.value.copy(currentPath = relativePath, preview = null)
        refresh()
    }

    /** 返回上一级（到 files/ 为止）。 */
    fun up() {
        val current = _state.value.currentPath
        if (current == "files" || current.isBlank()) return
        val parent = current.substringBeforeLast('/', "files")
        enter(parent)
    }

    fun preview(relativePath: String) {
        viewModelScope.launch {
            val text = runCatching { runtime.workspaceManager.readText(assistantId, relativePath) }
                .fold({ it }, { "读取失败：${it.message}" })
            _state.value = _state.value.copy(preview = Preview(relativePath, text.take(PREVIEW_LIMIT)))
        }
    }

    fun closePreview() {
        _state.value = _state.value.copy(preview = null)
    }

    fun delete(relativePath: String) {
        viewModelScope.launch {
            runCatching { runtime.workspaceManager.delete(assistantId, relativePath) }
                .onFailure { _state.value = _state.value.copy(message = "删除失败：${it.message}") }
            refresh()
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        const val PREVIEW_LIMIT = 8000
    }
}

data class WorkspaceState(
    val currentPath: String = "files",
    val entries: List<FileRow> = emptyList(),
    val usageLabel: String = "—",
    val loading: Boolean = true,
    val preview: Preview? = null,
    val message: String? = null,
)

data class FileRow(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeLabel: String,
    val modifiedLabel: Long,
)

data class Preview(val relativePath: String, val text: String)

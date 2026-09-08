package com.luzzymeow.luzzyrp.assistant.data.workspace

/**
 * 工作区条目（workspace_list 工具与 UI 共用，PLAN §10.1）。
 *
 * relativePath 一律用 / 分隔、相对于工作区根（如 files/docs/note.md）。
 */
data class WorkspaceEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    /** 目录为 0（不递归统计；总用量见 WorkspaceManager.quotaUsageBytes）。 */
    val sizeBytes: Long,
    val lastModified: Long,
)

package com.luzzymeow.luzzyrp.assistant.data.workspace

/**
 * 工作区元数据 .meta/workspace.json（PLAN §4.2：创建时间 / 大小 / 配额）。
 *
 * 只写不解析（除 createdAt 用正则回读）：元数据是诊断信息，损坏时按新建处理即可，
 * 不引入 JSON 依赖（本层无 kotlinx-serialization，见 app/build.gradle.kts 依赖清单）。
 *
 * assistantId 已由 WorkspaceManager 限制为 [A-Za-z0-9._-]，无需 JSON 转义。
 */
internal data class WorkspaceMeta(
    val assistantId: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val quotaBytes: Long,
    val maxFileBytes: Long,
    val usageBytes: Long,
) {

    fun toJson(): String = buildString(256) {
        append("{\n")
        append("  \"schemaVersion\": ").append(schemaVersion).append(",\n")
        append("  \"assistantId\": \"").append(assistantId).append("\",\n")
        append("  \"createdAt\": ").append(createdAt).append(",\n")
        append("  \"updatedAt\": ").append(updatedAt).append(",\n")
        append("  \"quotaBytes\": ").append(quotaBytes).append(",\n")
        append("  \"maxFileBytes\": ").append(maxFileBytes).append(",\n")
        append("  \"usageBytes\": ").append(usageBytes).append("\n")
        append("}\n")
    }
}

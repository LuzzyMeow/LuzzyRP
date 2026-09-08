package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 助手（PLAN v1.5.0 §4.1 表 assistant）。
 *
 * 一个助手 = 一套独立的提示词 / 模型参数 / 记忆 / 技能绑定 / MCP 绑定 / 工作区（PLAN §6.1）。
 * 字段与 §4.1 逐字段一致，禁止增删改（assistant.db version 1，无迁移；任何字段变更须走迁移评审）。
 * 本表不存任何密钥：API Key 走独立加密区（见 prefs/SecretStore）。
 */
@Entity(tableName = "assistant")
data class AssistantEntity(
    /** uuid */
    @PrimaryKey val id: String,
    val name: String,
    /** 工作区相对路径（如 attachments/avatar.png），null = 未设置 */
    val avatarPath: String?,
    /** 助手提示词（可含变量） */
    val systemPrompt: String,
    /** 引用 Web 端供应商 id（只读镜像，密钥不在此表） */
    val providerId: String?,
    /** 模型（providerId::bareId 语义） */
    val modelId: String?,
    val temperature: Float?,
    val topP: Float?,
    val maxTokens: Int?,
    /** 请求体扩展（JSON） */
    val extraBodyJson: String?,
    /** 其他参数（stop/seed/reasoning_effort…） */
    val paramsJson: String?,
    /** full | embed | hybrid（PLAN §7.1） */
    val memoryMode: String,
    /** 嵌入模型（providerId::bareId） */
    val embeddingModelRef: String?,
    val memoryTopK: Int,
    val memoryThreshold: Float,
    /** sandbox | host（PLAN §10.2） */
    val workspaceMode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
) {
    companion object {
        const val MEMORY_MODE_FULL = "full"
        const val MEMORY_MODE_EMBED = "embed"
        const val MEMORY_MODE_HYBRID = "hybrid"
        const val WORKSPACE_MODE_SANDBOX = "sandbox"
        const val WORKSPACE_MODE_HOST = "host"
    }
}

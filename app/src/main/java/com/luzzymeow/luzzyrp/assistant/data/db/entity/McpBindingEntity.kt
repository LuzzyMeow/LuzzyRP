package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity

/**
 * MCP 服务器与助手的绑定（PLAN v1.5.0 §4.1 表 mcp_binding，复合主键 assistantId + serverId）。
 */
@Entity(tableName = "mcp_binding", primaryKeys = ["assistantId", "serverId"])
data class McpBindingEntity(
    val assistantId: String,
    val serverId: String,
    val enabled: Boolean,
)

package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 工具调用审计（PLAN v1.5.0 §4.1 表 tool_audit；§13.2 所有工具调用落库）。
 *
 * 索引与 §4.1 完全一致（createdAt）。argsJson / resultPreview **必须脱敏**
 * （禁含 API Key、MCP 密钥、文件内容全文）——脱敏在写入前的调用层完成，本层不做内容判定。
 * id 自增；分页查询按 createdAt DESC, id DESC（同毫秒可稳定排序）。
 */
@Entity(tableName = "tool_audit", indices = [Index("createdAt")])
data class ToolAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assistantId: String,
    val conversationId: String,
    val toolName: String,
    val argsJson: String,
    val resultPreview: String,
    val approved: Boolean,
    val ok: Boolean,
    val durationMs: Long,
    val createdAt: Long,
)

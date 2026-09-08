package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息（PLAN v1.5.0 §4.1 表 message）。
 *
 * 索引与 §4.1 完全一致（conversationId / createdAt）。
 * 关键词检索走 FTS4 镜像表 message_fts（见 MessageFtsEntity 与 AssistantFtsCallback）。
 *
 * 注意：本表主键为 TEXT uuid，SQLite 会另分配隐式 rowid；FTS 同步触发器以该 rowid 关联，
 * 因此**禁止**用 INSERT OR REPLACE 写 message（会换 rowid 并残留孤儿 FTS 行）——
 * DAO 的 @Insert 一律用 OnConflictStrategy.ABORT。
 */
@Entity(
    tableName = "message",
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** user | assistant | tool | system */
    val role: String,
    /** markdown 原文 */
    val content: String,
    /** 思考内容（思考卡） */
    val reasoning: String?,
    /** 本轮 toolCalls（JSON） */
    val toolCallsJson: String?,
    val toolCallId: String?,
    val toolName: String?,
    /** complete | streaming | error | cancelled */
    val status: String,
    val createdAt: Long,
    val tokenUsageJson: String?,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"
        const val ROLE_SYSTEM = "system"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_STREAMING = "streaming"
        const val STATUS_ERROR = "error"
        const val STATUS_CANCELLED = "cancelled"
    }
}

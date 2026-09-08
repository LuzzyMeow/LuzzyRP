package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话（PLAN v1.5.0 §4.1 表 conversation）。
 *
 * 索引与 §4.1 完全一致（assistantId / updatedAt）；会话列表按 assistantId 过滤、
 * 按 updatedAt 分组排序（PLAN §6.2）。删除助手时按 assistantId 批量清理。
 */
@Entity(
    tableName = "conversation",
    indices = [Index("assistantId"), Index("updatedAt")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val assistantId: String,
    val title: String,
    /** 自动标题 / 摘要（可空） */
    val summary: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
)

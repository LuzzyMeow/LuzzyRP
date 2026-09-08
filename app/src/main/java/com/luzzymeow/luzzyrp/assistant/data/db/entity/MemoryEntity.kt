package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 助手记忆（PLAN v1.5.0 §4.1 表 memory；需求 3）。
 *
 * 索引与 §4.1 完全一致（assistantId / scope / createdAt）。
 * embedding 为 float32 小端 BLOB（无嵌入模型时为 null），检索为纯 Kotlin 余弦暴力扫描（PLAN §7.2）。
 *
 * 注意：ByteArray 字段在 data class 的 equals/hashCode 中按引用比较，语义比较请用
 * contentEquals；本表按 id 判定同一性，实际无影响。
 */
@Entity(
    tableName = "memory",
    indices = [Index("assistantId"), Index("scope"), Index("createdAt")],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    /** 归属助手（用户要求「助手分隔记忆」，PLAN §7.4） */
    val assistantId: String,
    /** global | assistant */
    val scope: String,
    /** fact | preference | task | note */
    val type: String,
    val content: String,
    /** user | agent | import */
    val source: String,
    val conversationId: String?,
    /** float32 小端；无嵌入模型时 null */
    val embedding: ByteArray?,
    val embeddingModelRef: String?,
    val dim: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
) {
    companion object {
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_ASSISTANT = "assistant"
        const val TYPE_FACT = "fact"
        const val TYPE_PREFERENCE = "preference"
        const val TYPE_TASK = "task"
        const val TYPE_NOTE = "note"
        const val SOURCE_USER = "user"
        const val SOURCE_AGENT = "agent"
        const val SOURCE_IMPORT = "import"
    }
}

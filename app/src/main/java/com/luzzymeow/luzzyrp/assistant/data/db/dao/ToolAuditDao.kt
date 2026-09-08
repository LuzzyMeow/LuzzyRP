package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ToolAuditEntity
import kotlinx.coroutines.flow.Flow

/**
 * 工具审计 DAO（PLAN §4.1 / §13.2：所有工具调用落库，可在设置中查看 / 清空）。
 *
 * 分页按 createdAt DESC, id DESC（id 自增，同毫秒稳定排序）。
 */
@Dao
interface ToolAuditDao {

    /** 返回新行 id（自增主键）。 */
    @Insert
    suspend fun insert(audit: ToolAuditEntity): Long

    @Query("SELECT * FROM tool_audit ORDER BY createdAt DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ToolAuditEntity>

    @Query(
        """
        SELECT * FROM tool_audit
        WHERE assistantId = :assistantId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageByAssistant(assistantId: String, limit: Int, offset: Int): List<ToolAuditEntity>

    @Query("SELECT * FROM tool_audit WHERE conversationId = :conversationId ORDER BY createdAt DESC, id DESC")
    suspend fun getByConversation(conversationId: String): List<ToolAuditEntity>

    @Query("SELECT * FROM tool_audit ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ToolAuditEntity>>

    @Query("SELECT COUNT(*) FROM tool_audit")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tool_audit WHERE assistantId = :assistantId")
    suspend fun countByAssistant(assistantId: String): Int

    @Query("DELETE FROM tool_audit WHERE createdAt < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM tool_audit")
    suspend fun clear()

    @Query("DELETE FROM tool_audit WHERE assistantId = :assistantId")
    suspend fun clearByAssistant(assistantId: String)
}

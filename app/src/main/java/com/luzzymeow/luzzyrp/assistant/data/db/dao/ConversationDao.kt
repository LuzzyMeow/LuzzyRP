package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话表 DAO（PLAN §4.1 / §6.2）。
 *
 * - 默认视图：按 assistantId 过滤、按 updatedAt DESC 排序（UI 再按 今天/昨天/7天/本月/更早 分组）；
 * - observeDayBuckets 直接给出按天聚合的计数，便于分组渲染；
 * - 标题检索用 LIKE（FTS4 只索引 message，标题同时快照进 message_fts.title）。
 */
@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversation WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversation WHERE assistantId = :assistantId")
    suspend fun deleteByAssistant(assistantId: String)

    @Query("SELECT * FROM conversation WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversation WHERE assistantId = :assistantId AND archived = 0 ORDER BY updatedAt DESC")
    fun observeByAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    fun observeByAssistantIncludingArchived(assistantId: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversation
        WHERE assistantId = :assistantId AND archived = 0 AND updatedAt BETWEEN :from AND :to
        ORDER BY updatedAt DESC
        """
    )
    fun observeByAssistantInRange(assistantId: String, from: Long, to: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation WHERE assistantId = :assistantId ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun pageByAssistant(assistantId: String, limit: Int, offset: Int): List<ConversationEntity>

    @Query(
        """
        SELECT * FROM conversation
        WHERE assistantId = :assistantId AND title LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchByTitle(assistantId: String, query: String, limit: Int): List<ConversationEntity>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', updatedAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
        FROM conversation
        WHERE assistantId = :assistantId AND archived = 0
        GROUP BY day
        ORDER BY day DESC
        """
    )
    fun observeDayBuckets(assistantId: String): Flow<List<ConversationDayBucket>>

    @Query("UPDATE conversation SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversation SET summary = :summary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String?, updatedAt: Long)

    @Query("UPDATE conversation SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE conversation SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM conversation WHERE assistantId = :assistantId AND archived = 0")
    suspend fun countByAssistant(assistantId: String): Int
}

package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 记忆表 DAO（PLAN §4.1 / §7）。
 *
 * - full 模式：getVisibleTo（本助手 + global）按 updatedAt DESC 取最近 N 条全文注入；
 * - embed / hybrid 模式：getEmbeddedVisibleTo 取带向量的条目，在 Kotlin 侧做余弦暴力扫描（§7.2）；
 * - 记忆按助手分隔（§7.4），global 作用域跨助手可见。
 */
@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<MemoryEntity>)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory WHERE assistantId = :assistantId")
    suspend fun deleteByAssistant(assistantId: String)

    @Query("SELECT * FROM memory WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memory WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    fun observeByAssistant(assistantId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    suspend fun getByAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE assistantId = :assistantId AND scope = :scope ORDER BY updatedAt DESC")
    suspend fun getByScope(assistantId: String, scope: String): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE assistantId = :assistantId AND type = :type ORDER BY updatedAt DESC")
    suspend fun getByType(assistantId: String, type: String): List<MemoryEntity>

    /** 本助手可见的全部记忆（本助手 + global），按更新时间倒序。 */
    @Query(
        """
        SELECT * FROM memory
        WHERE assistantId = :assistantId OR scope = 'global'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getVisibleTo(assistantId: String, limit: Int): List<MemoryEntity>

    /** 本助手可见且已生成向量的记忆（embed / hybrid 召回候选）。 */
    @Query(
        """
        SELECT * FROM memory
        WHERE (assistantId = :assistantId OR scope = 'global')
          AND embedding IS NOT NULL AND dim = :dim
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getEmbeddedVisibleTo(assistantId: String, dim: Int): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE embedding IS NOT NULL ORDER BY updatedAt DESC")
    suspend fun getAllEmbedded(): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memory
        WHERE (assistantId = :assistantId OR scope = 'global') AND content LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchByContent(assistantId: String, query: String, limit: Int): List<MemoryEntity>

    @Query("UPDATE memory SET lastUsedAt = :usedAt WHERE id IN (:ids)")
    suspend fun touchUsed(ids: List<String>, usedAt: Long)

    @Query(
        """
        UPDATE memory
        SET embedding = :embedding, embeddingModelRef = :modelRef, dim = :dim, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateEmbedding(id: String, embedding: ByteArray?, modelRef: String?, dim: Int, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM memory WHERE assistantId = :assistantId")
    suspend fun countByAssistant(assistantId: String): Int
}

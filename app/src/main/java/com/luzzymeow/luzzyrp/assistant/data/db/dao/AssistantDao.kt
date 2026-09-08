package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import kotlinx.coroutines.flow.Flow

/**
 * 助手表 DAO（PLAN §4.1 / §6.1）。
 *
 * 列表按 sortOrder 升序、创建时间升序（用户可拖拽排序）。删除助手的工作区目录
 * 由 WorkspaceManager.deleteWorkspace 负责（§6.1：默认保留，用户勾选才删）。
 */
@Dao
interface AssistantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assistant: AssistantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assistants: List<AssistantEntity>)

    @Update
    suspend fun update(assistant: AssistantEntity)

    @Query("DELETE FROM assistant WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM assistant WHERE id = :id")
    suspend fun getById(id: String): AssistantEntity?

    @Query("SELECT * FROM assistant ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<AssistantEntity>

    @Query("SELECT * FROM assistant ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<AssistantEntity>>

    @Query("SELECT COUNT(*) FROM assistant")
    suspend fun count(): Int

    @Query("UPDATE assistant SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE assistant SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)
}

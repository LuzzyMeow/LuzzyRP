package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * 技能表 DAO（PLAN §4.1 / §8）。
 *
 * enabledGlobal 为全局开关；某助手实际可用 = 全局开 且 绑定开（见 SkillBindingDao）。
 */
@Dao
interface SkillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skills: List<SkillEntity>)

    @Update
    suspend fun update(skill: SkillEntity)

    @Query("DELETE FROM skill WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM skill WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT * FROM skill WHERE id = :id")
    suspend fun getById(id: String): SkillEntity?

    @Query("SELECT * FROM skill WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SkillEntity?

    @Query("SELECT * FROM skill ORDER BY name ASC")
    suspend fun getAll(): List<SkillEntity>

    @Query("SELECT * FROM skill ORDER BY name ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skill WHERE enabledGlobal = 1 ORDER BY name ASC")
    suspend fun getGloballyEnabled(): List<SkillEntity>

    @Query("UPDATE skill SET enabledGlobal = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabledGlobal(id: String, enabled: Boolean, updatedAt: Long)

    @Query(
        """
        SELECT * FROM skill
        WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    suspend fun search(query: String): List<SkillEntity>

    @Query("SELECT COUNT(*) FROM skill")
    suspend fun count(): Int
}

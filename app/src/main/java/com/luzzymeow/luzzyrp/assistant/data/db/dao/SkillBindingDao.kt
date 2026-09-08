package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * 技能绑定表 DAO（PLAN §4.1 / §8.2 加载与启用）。
 *
 * 复合主键 (skillId, assistantId)；upsert 用 REPLACE 覆盖同键行（无 FTS，安全）。
 */
@Dao
interface SkillBindingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: SkillBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bindings: List<SkillBindingEntity>)

    @Query("DELETE FROM skill_binding WHERE skillId = :skillId AND assistantId = :assistantId")
    suspend fun delete(skillId: String, assistantId: String)

    @Query("DELETE FROM skill_binding WHERE skillId = :skillId")
    suspend fun deleteBySkill(skillId: String)

    @Query("DELETE FROM skill_binding WHERE assistantId = :assistantId")
    suspend fun deleteByAssistant(assistantId: String)

    @Query("SELECT * FROM skill_binding WHERE assistantId = :assistantId")
    suspend fun getByAssistant(assistantId: String): List<SkillBindingEntity>

    @Query("SELECT * FROM skill_binding WHERE skillId = :skillId")
    suspend fun getBySkill(skillId: String): List<SkillBindingEntity>

    @Query("UPDATE skill_binding SET enabled = :enabled WHERE skillId = :skillId AND assistantId = :assistantId")
    suspend fun setEnabled(skillId: String, assistantId: String, enabled: Boolean)

    /** 某助手实际启用的技能（全局开 + 绑定开）。 */
    @Query(
        """
        SELECT s.* FROM skill AS s
        INNER JOIN skill_binding AS b ON b.skillId = s.id
        WHERE b.assistantId = :assistantId AND b.enabled = 1 AND s.enabledGlobal = 1
        ORDER BY s.name ASC
        """
    )
    suspend fun getEnabledForAssistant(assistantId: String): List<SkillEntity>

    @Query(
        """
        SELECT s.* FROM skill AS s
        INNER JOIN skill_binding AS b ON b.skillId = s.id
        WHERE b.assistantId = :assistantId AND b.enabled = 1 AND s.enabledGlobal = 1
        ORDER BY s.name ASC
        """
    )
    fun observeEnabledForAssistant(assistantId: String): Flow<List<SkillEntity>>
}

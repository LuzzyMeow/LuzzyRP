package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

/**
 * MCP 绑定表 DAO（PLAN §4.1 / §9.4）。
 *
 * 复合主键 (assistantId, serverId)。
 */
@Dao
interface McpBindingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: McpBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bindings: List<McpBindingEntity>)

    @Query("DELETE FROM mcp_binding WHERE assistantId = :assistantId AND serverId = :serverId")
    suspend fun delete(assistantId: String, serverId: String)

    @Query("DELETE FROM mcp_binding WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("DELETE FROM mcp_binding WHERE assistantId = :assistantId")
    suspend fun deleteByAssistant(assistantId: String)

    @Query("SELECT * FROM mcp_binding WHERE assistantId = :assistantId")
    suspend fun getByAssistant(assistantId: String): List<McpBindingEntity>

    @Query("SELECT * FROM mcp_binding WHERE serverId = :serverId")
    suspend fun getByServer(serverId: String): List<McpBindingEntity>

    @Query("UPDATE mcp_binding SET enabled = :enabled WHERE assistantId = :assistantId AND serverId = :serverId")
    suspend fun setEnabled(assistantId: String, serverId: String, enabled: Boolean)

    /** 某助手实际可用的服务器（全局开 + 绑定开）。 */
    @Query(
        """
        SELECT s.* FROM mcp_server AS s
        INNER JOIN mcp_binding AS b ON b.serverId = s.id
        WHERE b.assistantId = :assistantId AND b.enabled = 1 AND s.enabledGlobal = 1
        ORDER BY s.name ASC
        """
    )
    suspend fun getEnabledForAssistant(assistantId: String): List<McpServerEntity>

    @Query(
        """
        SELECT s.* FROM mcp_server AS s
        INNER JOIN mcp_binding AS b ON b.serverId = s.id
        WHERE b.assistantId = :assistantId AND b.enabled = 1 AND s.enabledGlobal = 1
        ORDER BY s.name ASC
        """
    )
    fun observeEnabledForAssistant(assistantId: String): Flow<List<McpServerEntity>>
}

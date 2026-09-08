package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpServerEntity
import kotlinx.coroutines.flow.Flow

/**
 * MCP 服务器表 DAO（PLAN §4.1 / §9）。
 *
 * lastError 写入前必须脱敏（禁含密钥）；敏感头不入库（见 McpServerEntity 注释）。
 */
@Dao
interface McpServerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: McpServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(servers: List<McpServerEntity>)

    @Update
    suspend fun update(server: McpServerEntity)

    @Query("DELETE FROM mcp_server WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM mcp_server WHERE id = :id")
    suspend fun getById(id: String): McpServerEntity?

    @Query("SELECT * FROM mcp_server ORDER BY name ASC")
    suspend fun getAll(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_server ORDER BY name ASC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_server WHERE enabledGlobal = 1 ORDER BY name ASC")
    suspend fun getGloballyEnabled(): List<McpServerEntity>

    @Query("UPDATE mcp_server SET enabledGlobal = :enabled WHERE id = :id")
    suspend fun setEnabledGlobal(id: String, enabled: Boolean)

    @Query("UPDATE mcp_server SET lastConnectedAt = :lastConnectedAt, lastError = :lastError WHERE id = :id")
    suspend fun updateConnectionState(id: String, lastConnectedAt: Long?, lastError: String?)

    @Query("SELECT COUNT(*) FROM mcp_server")
    suspend fun count(): Int
}

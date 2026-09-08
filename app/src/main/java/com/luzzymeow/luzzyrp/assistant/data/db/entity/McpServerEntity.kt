package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MCP 服务器（PLAN v1.5.0 §4.1 表 mcp_server；需求 5）。
 *
 * transport: http | sse（stdio 见 §9.3，字段预留：command/argsJson/envJson）。
 * **密钥不入本表**：headersJson 只允许非敏感头（如 Accept）；Authorization 等敏感值走
 * 独立加密区（prefs/SecretStore），此处仅存引用 id。lastError 必须脱敏后再写（禁含密钥）。
 */
@Entity(tableName = "mcp_server")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** http | sse */
    val transport: String,
    val url: String?,
    /** 非敏感请求头（JSON）；敏感头走加密区 */
    val headersJson: String?,
    /** stdio 预留 */
    val command: String?,
    /** stdio 预留 */
    val argsJson: String?,
    /** stdio 预留（环境变量中若含密钥，只存引用 id） */
    val envJson: String?,
    val enabledGlobal: Boolean,
    /** null = 全部工具 */
    val toolAllowlistJson: String?,
    val lastConnectedAt: Long?,
    val lastError: String?,
) {
    companion object {
        const val TRANSPORT_HTTP = "http"
        const val TRANSPORT_SSE = "sse"
        const val TRANSPORT_STDIO = "stdio"
    }
}

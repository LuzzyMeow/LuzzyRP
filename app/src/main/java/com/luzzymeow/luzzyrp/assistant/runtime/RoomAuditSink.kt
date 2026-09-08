package com.luzzymeow.luzzyrp.assistant.runtime

import com.luzzymeow.luzzyrp.assistant.data.db.dao.ToolAuditDao
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ToolAuditEntity
import com.luzzymeow.luzzyrp.assistant.domain.tool.AuditEntry
import com.luzzymeow.luzzyrp.assistant.domain.tool.AuditSink

/**
 * 审计落库（PLAN §13.2）。
 *
 * 写入内容**已由调用方脱敏/截断**（[com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry]）；
 * 本层再兜底截断一次，并保证异常不外泄（审计失败不得影响工具执行）。
 */
class RoomAuditSink(private val dao: ToolAuditDao) : AuditSink {

    override suspend fun record(entry: AuditEntry) {
        runCatching {
            dao.insert(
                ToolAuditEntity(
                    assistantId = entry.assistantId,
                    conversationId = entry.conversationId,
                    toolName = entry.toolName,
                    argsJson = entry.argsPreview.take(MAX_FIELD),
                    resultPreview = entry.resultPreview.take(MAX_FIELD),
                    approved = entry.approved,
                    ok = entry.ok,
                    durationMs = entry.durationMs,
                    createdAt = entry.createdAtMillis,
                )
            )
        }
    }

    /** 最近 N 条（设置页审计面板用）。 */
    suspend fun recent(assistantId: String, limit: Int = 50): List<ToolAuditEntity> =
        runCatching { dao.pageByAssistant(assistantId, limit, 0) }.getOrDefault(emptyList())

    /** 清空本助手审计。 */
    suspend fun clear(assistantId: String) {
        runCatching { dao.clearByAssistant(assistantId) }
    }

    /** 清理 N 天前的审计。 */
    suspend fun prune(beforeMillis: Long): Int = runCatching { dao.deleteBefore(beforeMillis) }.getOrDefault(0)

    private companion object {
        const val MAX_FIELD = 500
    }
}

package com.luzzymeow.luzzyrp.assistant.domain.tool

/**
 * 工具审计端口（PLAN §13.2：所有工具调用落 `tool_audit`，参数脱敏，可在设置中查看/清空）。
 *
 * domain 只声明接口——持久化在 runtime 层（Room）。实现**必须**：
 * - 截断/脱敏参数与结果预览（不得写入密钥、文件正文）；
 * - 自身异常**不得影响工具执行**（调用方已 try/catch）。
 */
fun interface AuditSink {
    suspend fun record(entry: AuditEntry)

    companion object {
        /** 未接入实现时的空操作。 */
        val NONE: AuditSink = AuditSink { }
    }
}

/** 一条审计记录（字段与 `tool_audit` 表对齐）。 */
data class AuditEntry(
    val assistantId: String,
    val conversationId: String,
    val toolName: String,
    /** 已脱敏/截断的参数摘要。 */
    val argsPreview: String,
    /** 已截断的结果预览。 */
    val resultPreview: String,
    val approved: Boolean,
    val ok: Boolean,
    val durationMs: Long,
    val createdAtMillis: Long,
)

package com.luzzymeow.luzzyrp.assistant.data.workspace

/**
 * 工作区操作异常基类（PLAN §10.1）。
 *
 * 设计约定：**不静默返回 null / false**——路径越界、配额超限、文件过大、目标不存在
 * 一律抛具体异常，由工具层捕获后转成 ToolResult.Error 并落 tool_audit（§13.2）。
 */
open class WorkspaceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 路径越界 / 非法路径（../ 逃逸、绝对路径、盘符、符号链接穿越、非法助手 id）。
 *
 * 这是**安全事件**而非普通错误：调用方除返回错误外，应记录审计（不带文件内容）。
 */
class WorkspaceSecurityException(
    message: String,
    cause: Throwable? = null,
) : WorkspaceException(message, cause)

/** 单文件超过 maxFileBytes（默认 64MB，PLAN §10.1）。 */
class WorkspaceFileTooLargeException(
    val sizeBytes: Long,
    val maxFileBytes: Long,
    message: String,
) : WorkspaceException(message)

/** 工作区总用量超过配额（默认 2GB，PLAN §10.1）。 */
class WorkspaceQuotaExceededException(
    val usageBytes: Long,
    val quotaBytes: Long,
    val projectedBytes: Long,
) : WorkspaceException(
    "工作区配额不足：已用 " + usageBytes + " B，写入后 " + projectedBytes + " B，配额 " + quotaBytes + " B",
)

/** 目标路径不存在。 */
class WorkspaceNotFoundException(message: String) : WorkspaceException(message)

/** 目录非空且未指定递归删除等操作前置条件不满足。 */
class WorkspaceOperationException(message: String, cause: Throwable? = null) : WorkspaceException(message, cause)

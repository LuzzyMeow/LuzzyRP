package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.serialization.json.JsonObject

/**
 * 工具分级（PLAN §12.1）。默认开关与审批策略由 [com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate] 决定。
 *
 * | 档 | 默认 | 说明 |
 * |----|------|------|
 * | T0 | 开 | 只读，无副作用 |
 * | T1 | 开（逐调用审批） | 只动助手自己的数据 |
 * | T2 | **关** | 写设备/外部，需用户显式开启 + 审批 |
 * | T3 | **关** | 高危（屏幕自动化 / 短信 / 提权），本版不实现 |
 */
enum class ToolTier(val defaultEnabled: Boolean, val requiresApproval: Boolean) {
    T0_READ(defaultEnabled = true, requiresApproval = false),
    T1_WRITE_APP(defaultEnabled = true, requiresApproval = true),
    T2_WRITE_DEVICE(defaultEnabled = false, requiresApproval = true),
    T3_DANGEROUS(defaultEnabled = false, requiresApproval = true),
}

/** 模型发起的一次工具调用（已从各协议归一）。 */
data class ToolCall(
    /** 协议侧 id（OpenAI `tool_call.id` / Anthropic `tool_use.id` / Gemini 合成 id）。 */
    val id: String,
    /** 工具名（MCP 工具为 `mcp__<serverId>__<toolName>`）。 */
    val name: String,
    /** 原始参数 JSON（协议可能分片下发，由传输层拼装完整后传入）。 */
    val arguments: JsonObject,
    /** 原始参数字符串（保留给审计与错误提示；解析失败时非空）。 */
    val rawArguments: String = arguments.toString(),
)

/** 工具产生的附件（图片等；落工作区 `attachments/` 后回传相对路径）。 */
data class Attachment(
    val kind: String,
    val relativePath: String,
    val mimeType: String? = null,
)

/**
 * 工具执行结果（PLAN §12.3）。
 *
 * 约定：**工具异常必须转成 [Error] 回灌给模型**，不得让异常逃逸打断整轮
 * （PLAN §5.2）。[Error.retryable] 供 Agent 循环决定是否重试。
 */
sealed interface ToolResult {
    data class Ok(
        val text: String,
        val attachments: List<Attachment> = emptyList(),
    ) : ToolResult

    data class Error(
        val message: String,
        val retryable: Boolean = false,
    ) : ToolResult

    /** `ask_user` 专用：暂停循环，等待用户选择（PLAN §12.2）。 */
    data class NeedUserInput(
        val prompt: AskUserPrompt,
    ) : ToolResult
}

/** 澄清提问载荷（`ask_user` 工具）。 */
data class AskUserPrompt(
    val question: String,
    val options: List<AskUserOption> = emptyList(),
    val allowMultiple: Boolean = false,
    /** 是否允许用户自由输入（除选项外）。 */
    val allowFreeText: Boolean = true,
)

data class AskUserOption(
    val label: String,
    val description: String? = null,
)

/**
 * 工具执行上下文（PLAN §12.3）。
 *
 * 由 Agent 循环构造，注入助手 id / 会话 id / 工作区端口 / 审批器 / 取消信号。
 * **工具实现不得自行读取全局单例**——一切依赖经此传入，便于单测。
 */
interface ToolContext {
    val assistantId: String
    val conversationId: String
    /** 工作区访问端口（沙盒/宿主模式差异由实现吸收；路径越界防护在实现内）。 */
    val workspace: WorkspaceAccess
    /** 取消信号（用户点停止 / 超时）。 */
    val cancelled: () -> Boolean
    /** 日志器：**白名单式**，禁止写入密钥或文件内容（PLAN §13.2）。 */
    val log: (String) -> Unit
}

/**
 * 工作区访问端口（domain 不依赖 Android 文件 API）。
 *
 * 实现见 `assistant/data/workspace/WorkspaceManager`（P0 数据层）。
 * 所有相对路径必须落在助手工作区内，越界抛异常（PLAN §10.1）。
 */
interface WorkspaceAccess {
    suspend fun list(relativeDir: String = ""): List<WorkspaceEntry>
    suspend fun read(relativePath: String): ByteArray
    suspend fun write(relativePath: String, bytes: ByteArray)
    suspend fun delete(relativePath: String)
    suspend fun move(fromRelative: String, toRelative: String)
    suspend fun mkdir(relativeDir: String)
    suspend fun exists(relativePath: String): Boolean
}

/** 工作区目录项（domain 侧最小视图）。 */
data class WorkspaceEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

/**
 * 工具契约（PLAN §12.3）。
 *
 * [parameters] 是 JSON Schema 对象（供各协议转成 `tools[].function.parameters`），
 * 用 `com.luzzymeow.luzzyrp.assistant.domain.tool.Schema` 的 DSL 构造。
 */
interface Tool {
    val name: String
    val description: String
    val tier: ToolTier
    val parameters: JsonObject

    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}

package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 工具注册表（PLAN §5.2 / §12）。
 *
 * 职责：
 * - 按名注册 / 查找（内置工具、Skill 工具、MCP 工具统一入口）；
 * - 产出协议侧 `tools` schema 数组（**仅**启用且 [ApprovalGate.isEnabled] 的工具）；
 * - 自动生成「工具使用约定」文本，供 [com.luzzymeow.luzzyrp.assistant.domain.prompt.ContextBuilder] 注入系统提示词；
 * - 执行分发：未注册 / 已关闭 / 抛异常一律转 [ToolResult.Error]（**绝不抛给 Agent 循环**）。
 *
 * MCP 工具命名空间：`mcp__<serverId>__<toolName>`（PLAN §12.2）。`serverId` 与 `toolName`
 * 内部若含 `__`，解析按**第一个**分隔符切分（toolName 允许含 `__`）。
 */
class ToolRegistry(
    tools: List<Tool> = emptyList(),
    /** 工具开关 + 审批策略（与 [com.luzzymeow.luzzyrp.assistant.domain.loop.AgentLoop] 共用同一实例）。 */
    val approval: ApprovalGate = ApprovalGate(),
    /** 审计落库端口（PLAN §13.2；默认空操作，实现失败不得影响执行）。 */
    private val audit: AuditSink = AuditSink.NONE,
    /** 审计时间源（单测可注入）。 */
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val byName = LinkedHashMap<String, Tool>()

    init {
        tools.forEach { register(it) }
    }

    /** 注册（同名覆盖，后注册者生效）。 */
    fun register(tool: Tool) {
        require(tool.name.isNotBlank()) { "工具名不能为空" }
        byName[tool.name] = tool
    }

    fun registerAll(tools: Iterable<Tool>) = tools.forEach { register(it) }

    fun unregister(name: String) {
        byName.remove(name)
    }

    /** 按名查找（含已关闭的工具——执行时再判开关，便于给出「已关闭」的明确错误）。 */
    fun find(name: String): Tool? = byName[name]

    /** 全部已注册工具（注册顺序）。 */
    fun all(): List<Tool> = byName.values.toList()

    /** 用户已开启的工具。 */
    fun enabled(): List<Tool> = all().filter { approval.isEnabled(it) }

    /** 协议侧 `tools` 数组（OpenAI `[{type:function,function:{…}}]`）。 */
    fun schemas(): List<JsonObject> = enabled().map { toolSchema(it) }

    /**
     * 执行一次工具调用。
     *
     * - 未注册 → `Error(未知工具)`；
     * - 已关闭 → `Error(工具已关闭)`；
     * - 工具抛异常 → `Error(工具执行异常)`（取消除外，取消必须继续向上传播）。
     */
    suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult {
        val tool = byName[call.name]
            ?: return ToolResult.Error("未知工具：${call.name}", retryable = false)
        if (!approval.isEnabled(tool)) {
            return ToolResult.Error("工具已关闭：${call.name}（请在助手设置中开启）", retryable = false)
        }
        val startedAt = now()
        val result = try {
            tool.execute(call.arguments, ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ToolResult.Error(
                message = "工具执行异常（${tool.name}）：${e.message ?: e.javaClass.simpleName}",
                retryable = false,
            )
        }
        recordAudit(tool, call, result, startedAt, ctx)
        return result
    }

    /** 落审计（脱敏 + 截断；异常静默——审计失败不影响对话）。 */
    private suspend fun recordAudit(
        tool: Tool,
        call: ToolCall,
        result: ToolResult,
        startedAt: Long,
        ctx: ToolContext,
    ) {
        val preview = when (result) {
            is ToolResult.Ok -> result.text.take(AUDIT_PREVIEW_LIMIT)
            is ToolResult.Error -> result.message.take(AUDIT_PREVIEW_LIMIT)
            is ToolResult.NeedUserInput -> "（等待用户输入）"
        }
        runCatching {
            audit.record(
                AuditEntry(
                    assistantId = ctx.assistantId,
                    conversationId = ctx.conversationId,
                    toolName = tool.name,
                    argsPreview = redactArgs(call).take(AUDIT_ARGS_LIMIT),
                    resultPreview = preview,
                    approved = true,
                    ok = result !is ToolResult.Error,
                    durationMs = (now() - startedAt).coerceAtLeast(0),
                    createdAtMillis = startedAt,
                )
            )
        }
    }

    /**
     * 参数脱敏：只保留键名与「值类型/长度」，**不回显值内容**——工具参数可能含用户隐私
     * 或密钥（如 MCP 头、剪贴板文本），审计面板是给用户看的，不需要原文。
     */
    internal fun redactArgs(call: ToolCall): String = call.arguments.entries.joinToString(", ", "{", "}") { (key, value) ->
        val text = value.toString()
        "$key:${text.length}字"
    }

    /**
     * 自动生成「工具使用约定」（PLAN §5.4 系统提示词末段）。
     *
     * 只列**当前启用**的工具——与 [schemas] 保持一致，避免提示词里出现模型根本看不到的工具。
     */
    fun conventions(): String {
        val list = enabled()
        val header = buildString {
            append("## 工具使用约定\n")
            if (list.isEmpty()) {
                append("本轮没有可用工具，请直接回答，不要编造工具结果。")
                return@buildString
            }
            append("可用工具（共 ").append(list.size).append(" 个）：\n")
            list.forEach { tool ->
                append("- ").append(tool.name).append("（").append(tierLabel(tool.tier)).append("）：")
                append(tool.description.lineSequence().firstOrNull()?.trim().orEmpty())
                if (tool.tier.requiresApproval) append(" [需用户审批]")
                append('\n')
            }
            append(
                """
                |调用规则：
                |1. 需要外部信息或要执行动作时优先调用工具，不要凭空编造工具结果。
                |2. 工具返回以 `ERROR:` 开头表示失败，请阅读原因并调整方案，不要重复同样的无效调用。
                |3. 需求不明确时用 ask_user 向用户澄清（会暂停等待用户回答）。
                |4. 工具开关与审批由用户控制：被拒绝或不可用的工具请改用其他途径，并向用户说明。
                """.trimMargin()
            )
        }
        return header
    }

    companion object {
        const val AUDIT_PREVIEW_LIMIT: Int = 400
        const val AUDIT_ARGS_LIMIT: Int = 300

        const val MCP_PREFIX: String = "mcp__"
        private const val MCP_SEPARATOR = "__"

        /** 组装 MCP 工具名：`mcp__<serverId>__<toolName>`。 */
        fun mcpToolName(serverId: String, toolName: String): String = "$MCP_PREFIX${serverId}$MCP_SEPARATOR$toolName"

        /** 解析 MCP 工具名；非 MCP 工具返回 null。 */
        fun parseMcpName(name: String): McpToolRef? {
            if (!name.startsWith(MCP_PREFIX)) return null
            val rest = name.removePrefix(MCP_PREFIX)
            val split = rest.indexOf(MCP_SEPARATOR)
            if (split <= 0 || split >= rest.length - MCP_SEPARATOR.length) return null
            val serverId = rest.substring(0, split)
            val toolName = rest.substring(split + MCP_SEPARATOR.length)
            if (serverId.isBlank() || toolName.isBlank()) return null
            return McpToolRef(serverId = serverId, toolName = toolName)
        }

        /** [Tool] → OpenAI 工具声明。 */
        fun toolSchema(tool: Tool): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("function"))
            put(
                "function",
                buildJsonObject {
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", tool.parameters)
                }
            )
        }

        private fun tierLabel(tier: ToolTier): String = when (tier) {
            ToolTier.T0_READ -> "T0 只读"
            ToolTier.T1_WRITE_APP -> "T1 写（应用内）"
            ToolTier.T2_WRITE_DEVICE -> "T2 写（设备/外部）"
            ToolTier.T3_DANGEROUS -> "T3 高危"
        }
    }
}

/** MCP 工具名解析结果。 */
data class McpToolRef(val serverId: String, val toolName: String)

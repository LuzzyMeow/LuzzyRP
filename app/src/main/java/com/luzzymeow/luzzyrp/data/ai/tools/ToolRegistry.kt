package com.luzzymeow.luzzyrp.data.ai.tools

import com.luzzymeow.luzzyrp.core.ai.params.ToolDefinition
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import kotlinx.serialization.json.JsonElement

/**
 * 工具定义（应用侧）。
 *
 * [INVARIANT-AGENTIC] 工具协议（Agentic 不变性 A5/A6）：
 *   - needsApproval 为 true 的工具在模型调用后进入 Pending 状态等待用户审批
 *     （ToolApprovalState 五状态机，续跑见 GenerationHandler）；
 *   - 被动工具不注册到 [ToolRegistry]（经系统提示注入 + 每轮预执行），
 *     见 PromptAssembler 的被动召回层。
 */
data class ToolDef(
    val name: String,
    val description: String,
    /** JSON Schema 参数定义。 */
    val parametersJson: String,
    /** 注入系统提示的工具说明（稳定前缀层）。 */
    val systemPrompt: String,
    /** 是否需要用户审批（默认不需要）。 */
    val needsApproval: (JsonElement) -> Boolean = { false },
    /** 执行：入参为解析后的 JSON，出参为回填到 Tool part 的输出。 */
    val execute: suspend (JsonElement) -> List<UIMessagePart>,
)

/** 工具注册表：主动工具集合 → 协议 ToolDefinition 列表。 */
class ToolRegistry(private val tools: List<ToolDef>) {

    private val byName: Map<String, ToolDef> = tools.associateBy { it.name }

    fun find(name: String): ToolDef? = byName[name]

    fun isEmpty(): Boolean = tools.isEmpty()

    /** 转为协议参数（原生 function calling）。 */
    fun toDefinitions(): List<ToolDefinition> = tools.map {
        ToolDefinition(
            name = it.name,
            description = it.description,
            parametersJson = it.parametersJson,
        )
    }

    /** 工具系统提示聚合（注入稳定前缀）。 */
    fun systemPrompts(): String = tools.joinToString("\n\n") { it.systemPrompt }
}

/** 工具输出构造辅助。 */
object ToolOutputs {
    fun text(value: String): List<UIMessagePart> = listOf(UIMessagePart.Text(value))

    fun error(message: String): List<UIMessagePart> =
        listOf(UIMessagePart.Text("""{"error": ${kotlinx.serialization.json.JsonPrimitive(message)}}"""))
}

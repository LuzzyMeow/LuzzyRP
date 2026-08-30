package com.luzzymeow.luzzyrp.core.ai.params

import com.luzzymeow.luzzyrp.core.model.Model

/**
 * 文本生成参数。
 *
 * [INVARIANT-AGENTIC] tools 与 toolChoice（Agentic 不变性 A5）：
 *   - toolChoice 仅允许 AUTO / NONE，全项目禁止 REQUIRED
 *     （强制工具调用会破坏模型自主性与 RP 叙事节奏）；
 *   - 被动工具不进入 tools 列表（经系统提示注入，见 GenerationHandler）。
 */
data class TextGenerationParams(
    val model: Model,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Long? = null,
    /** 可主动调用的工具定义（原生 function calling 协议）。 */
    val tools: List<ToolDefinition> = emptyList(),
    val toolChoice: ToolChoice = ToolChoice.AUTO,
    /** 推理力度（思考深度）：null = 模型默认。 */
    val thinking: ThinkingConfig = ThinkingConfig.Default,
    /** 附加请求头。 */
    val customHeaders: Map<String, String> = emptyMap(),
    /** 附加请求体字段（JSON 对象字符串，深合并进顶层）。 */
    val customBody: String = "",
)

/** 思考深度配置。 */
data class ThinkingConfig(
    val enabled: Boolean? = null,   // null = 跟随默认
    val effort: String? = null,     // low / medium / high / max
    val budgetTokens: Long? = null, // 思考预算（Claude）
) {
    companion object {
        val Default = ThinkingConfig()
        val Off = ThinkingConfig(enabled = false)
        val On = ThinkingConfig(enabled = true)
    }
}

/** 工具选择模式（仅 AUTO / NONE —— 禁止 REQUIRED）。 */
enum class ToolChoice { AUTO, NONE }

/** 原生 function calling 工具定义。 */
data class ToolDefinition(
    val name: String,
    val description: String,
    /** JSON Schema 形式的参数定义（字符串化）。 */
    val parametersJson: String,
)

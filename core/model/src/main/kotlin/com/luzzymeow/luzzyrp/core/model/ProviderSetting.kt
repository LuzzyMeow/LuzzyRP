package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LLM 供应商设置与模型。
 *
 * [INVARIANT-STREAMING] ProviderSetting 是 Provider<T> 的类型参数：
 *   ProviderManager 依此路由到对应协议实现（不变性 S1/S2）。
 */

/** 模型能力位。 */
@Serializable
data class ModelCapability(
    /** 支持推理输出（思考卡片）。 */
    val reasoning: Boolean = false,
    /** 支持视觉输入。 */
    val vision: Boolean = false,
    /** 支持工具调用（原生 function calling）。 */
    val tool: Boolean = true,
    /** 支持嵌入（向量化）。 */
    val embedding: Boolean = false,
)

@Serializable
data class Model(
    val id: String,
    /** 显示名（可自定义，≤20 字符）。 */
    val displayName: String = "",
    val capabilities: ModelCapability = ModelCapability(),
    /** 上下文长度（tokens）。 */
    val contextLength: Long = 128_000,
    /** 最大输出（tokens）；0 或未填 = 与上下文长度一致（6.1 规则）。 */
    val maxOutputTokens: Long = 0,
    /** 模型级温度覆盖（null = 跟随全局生成参数）。 */
    val temperature: Double? = null,
    /** 思考深度（ThinkingDepth 序列名；null = DEFAULT）。按模型 id 自适配档位（ThinkingDepthAdapter）。 */
    val thinkingDepth: String? = null,
) {
    fun displayNameOrId(): String = displayName.ifBlank { id }
}

/** 供应商协议类型。 */
@Serializable
enum class ProviderType {
    /** OpenAI 兼容（chat/completions；覆盖 GLM/DeepSeek/方舟/OpenRouter 等）。 */
    @SerialName("openai") OPENAI,

    /** Anthropic messages API。 */
    @SerialName("anthropic") ANTHROPIC,

    /** Google Gemini API。 */
    @SerialName("google") GOOGLE,
}

/**
 * 供应商设置。
 * 所有协议统一为 OpenAI 兼容形态的 baseUrl + apiKey + 模型列表；
 * Anthropic/Google 的差异由协议实现层消化。
 */
@Serializable
data class ProviderSetting(
    val id: String,
    val name: String,
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String,
    val apiKey: String,
    val models: List<Model> = emptyList(),
    /** 附加请求头。 */
    val customHeaders: Map<String, String> = emptyMap(),
    /**
     * 附加请求体字段（JSON 对象字符串，如 {"reasoning_effort":"max"} 或
     * {"thinking":{"type":"enabled"}}），请求组装时深合并进 body 顶层。
     */
    val extraBodyJson: String = "",
    /** 是否内置预置档案（可编辑，删除后可恢复）。 */
    val builtin: Boolean = false,
)

/** 内置供应商档案（Task-V0.4.4 规格，均可编辑/删除）。 */
object BuiltinProviders {

    val DEEPSEEK = ProviderSetting(
        id = "builtin_deepseek",
        name = "DeepSeek",
        type = ProviderType.OPENAI,
        baseUrl = "https://api.deepseek.com",
        apiKey = "",
        models = listOf(
            Model(
                id = "deepseek-v4-pro",
                displayName = "DeepSeek V4 Pro",
                capabilities = ModelCapability(reasoning = true, tool = true),
                contextLength = 1_048_576,
                maxOutputTokens = 393_216,
            ),
            Model(
                id = "deepseek-v4-flash",
                displayName = "DeepSeek V4 Flash",
                capabilities = ModelCapability(reasoning = true, tool = true),
                contextLength = 1_048_576,
                maxOutputTokens = 393_216,
            ),
        ),
        extraBodyJson = """{"reasoning_effort":"max"}""",
        builtin = true,
    )

    val ARK_CODING_PLAN = ProviderSetting(
        id = "builtin_ark_coding_plan",
        name = "火山方舟 CodingPlan",
        type = ProviderType.OPENAI,
        baseUrl = "https://ark.cn-beijing.volces.com/api/coding/v3",
        apiKey = "",
        models = listOf(
            Model(
                id = "glm-5.2",
                displayName = "GLM-5.2",
                capabilities = ModelCapability(reasoning = true, tool = false),
                contextLength = 1_048_576,
                maxOutputTokens = 131_072,
            ),
            Model(
                id = "deepseek-v4-pro",
                displayName = "DeepSeek V4 Pro（方舟）",
                capabilities = ModelCapability(reasoning = true, tool = true),
                contextLength = 1_048_576,
                maxOutputTokens = 393_216,
            ),
            Model(
                id = "doubao-embedding-vision",
                displayName = "Doubao Embedding Vision",
                capabilities = ModelCapability(embedding = true, tool = false, vision = true),
                contextLength = 0,
                maxOutputTokens = 0,
            ),
        ),
        extraBodyJson = """{"thinking":{"type":"enabled"}}""",
        builtin = true,
    )

    /** 初始档案列表（首次启动写入 SettingsStore）。 */
    fun defaults(): List<ProviderSetting> = listOf(DEEPSEEK, ARK_CODING_PLAN)
}

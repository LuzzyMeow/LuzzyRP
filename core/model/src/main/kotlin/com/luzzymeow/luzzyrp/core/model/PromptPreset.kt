package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [INVARIANT-AGENTIC] 思考深度自适配（规定 2 的参数面）。
 *
 * 按**模型 id 检测家族**，映射为各家的正确请求字段（字段形态经官方文档核实）：
 *
 * | 家族（id 含） | 请求字段 | 档位 |
 * |---|---|---|
 * | deepseek | `reasoning_effort` | OFF→none · HIGH→high · MAX→max（DeepSeek 官方三档 None/High/Max） |
 * | glm（5.3+） | `reasoning_effort` | LOW/HIGH/MAX（GLM-5.3 不再支持关闭思考与 thinking 字段） |
 * | glm（其余） | `thinking.type` | OFF→disabled · HIGH→enabled（GLM-4.5/5/5.2 双态） |
 * | gpt / o1/o3/o4 | `reasoning_effort` | OFF→none · LOW/MEDIUM/HIGH |
 * | opus / claude | `thinking:{type:adaptive}` + `output_config.effort` | OFF→thinking disabled · LOW/HIGH→low/high · MAX→xhigh |
 * | 其他 | `reasoning_effort` | LOW/MEDIUM/HIGH（OpenAI 兼容默认） |
 *
 * 检测顺序：deepseek → glm → gpt/o 系列 → opus/claude → 默认。
 * 输出为**请求体顶层字段片段**，由 Provider 以深合并写入（不覆盖用户 extraBody 之外的既有字段）。
 */
@Serializable
enum class ThinkingDepth {
    /** 跟随模型默认（不发送任何思考字段）。 */
    @SerialName("default") DEFAULT,

    /** 关闭思考。 */
    @SerialName("off") OFF,

    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,

    /** 最高档（deepseek max / glm max / opus xhigh）。 */
    @SerialName("max") MAX,
}

object ThinkingDepthAdapter {

    private val FamilyJson = Json { encodeDefaults = true }

    /** 模型 id → 家族。 */
    fun familyOf(modelId: String): ThinkingFamily = when {
        modelId.contains("deepseek", ignoreCase = true) -> ThinkingFamily.DEEPSEEK
        modelId.contains("glm", ignoreCase = true) -> ThinkingFamily.GLM
        modelId.contains("opus", ignoreCase = true) || modelId.contains("claude", ignoreCase = true) -> ThinkingFamily.CLAUDE
        Regex("(^|[^a-z])(gpt|o[134])([^a-z]|$)", RegexOption.IGNORE_CASE).containsMatchIn(modelId) -> ThinkingFamily.OPENAI
        else -> ThinkingFamily.GENERIC
    }

    /** 该模型在设置页可选择的档位（UI 依据）。 */
    fun optionsFor(modelId: String): List<ThinkingDepth> = when (familyOf(modelId)) {
        ThinkingFamily.DEEPSEEK -> listOf(ThinkingDepth.OFF, ThinkingDepth.HIGH, ThinkingDepth.MAX)
        ThinkingFamily.GLM ->
            if (Regex("glm-5\\.3", RegexOption.IGNORE_CASE).containsMatchIn(modelId)) {
                listOf(ThinkingDepth.LOW, ThinkingDepth.HIGH, ThinkingDepth.MAX)  // 5.3 不可关闭
            } else {
                listOf(ThinkingDepth.OFF, ThinkingDepth.HIGH)
            }
        ThinkingFamily.CLAUDE -> listOf(
            ThinkingDepth.OFF, ThinkingDepth.LOW, ThinkingDepth.MEDIUM, ThinkingDepth.HIGH, ThinkingDepth.MAX,
        )
        ThinkingFamily.OPENAI -> listOf(ThinkingDepth.OFF, ThinkingDepth.LOW, ThinkingDepth.MEDIUM, ThinkingDepth.HIGH)
        ThinkingFamily.GENERIC -> listOf(ThinkingDepth.LOW, ThinkingDepth.MEDIUM, ThinkingDepth.HIGH)
    }

    /**
     * 生成该 (模型, 档位) 的请求体顶层字段片段；DEFAULT 返回空对象（不发送）。
     * 返回的 JsonObject 由 Provider mergeCustomBody 同路径深合并。
     */
    fun requestBodyFragment(modelId: String, depth: ThinkingDepth?): JsonElement {
        if (depth == null || depth == ThinkingDepth.DEFAULT) return buildJsonObject { }
        return when (familyOf(modelId)) {
            ThinkingFamily.DEEPSEEK -> buildJsonObject {
                put("reasoning_effort", when (depth) {
                    ThinkingDepth.OFF -> "none"
                    ThinkingDepth.MAX -> "max"
                    else -> "high"
                })
            }

            ThinkingFamily.GLM -> {
                if (Regex("glm-5\\.3", RegexOption.IGNORE_CASE).containsMatchIn(modelId)) {
                    buildJsonObject {
                        put("reasoning_effort", when (depth) {
                            ThinkingDepth.LOW -> "low"
                            ThinkingDepth.MAX -> "max"
                            else -> "high"
                        })
                    }
                } else {
                    buildJsonObject {
                        put("thinking", buildJsonObject {
                            put("type", if (depth == ThinkingDepth.OFF) "disabled" else "enabled")
                        })
                    }
                }
            }

            ThinkingFamily.CLAUDE -> buildJsonObject {
                if (depth == ThinkingDepth.OFF) {
                    put("thinking", buildJsonObject { put("type", "disabled") })
                } else {
                    put("thinking", buildJsonObject {
                        put("type", "adaptive")
                        put("display", "summarized")
                    })
                    put("output_config", buildJsonObject {
                        put("effort", when (depth) {
                            ThinkingDepth.LOW -> "low"
                            ThinkingDepth.MEDIUM -> "medium"
                            ThinkingDepth.MAX -> "xhigh"
                            else -> "high"
                        })
                    })
                }
            }

            else -> buildJsonObject {
                put("reasoning_effort", when (depth) {
                    ThinkingDepth.OFF -> "none"
                    ThinkingDepth.LOW -> "low"
                    ThinkingDepth.MEDIUM -> "medium"
                    ThinkingDepth.HIGH -> "high"
                    ThinkingDepth.MAX -> "high"
                    else -> "high"
                })
            }
        }
    }
}

/** 思考深度家族（检测用）。 */
enum class ThinkingFamily { DEEPSEEK, GLM, CLAUDE, OPENAI, GENERIC }

/**
 * 提示词预设（RP-Hub 式「编辑预设」：预设 = 名称 + 提示词条目列表）。
 * 条目可独立启用/编辑/复制；注入位置支持 相对（↑Char/↓Char/↑EM/↓EM）与 @Depth×3 角色。
 * 启用的预设经 PromptAssembler 注入 system 层（相对位置）或按深度插入消息列。
 */
@Serializable
data class PromptPreset(
    val id: String,
    val name: String,
    val entries: List<PresetEntry> = emptyList(),
    val builtin: Boolean = false,
    val readonly: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class PresetEntry(
    val id: String,
    /** 条目名（如「角色扮演-04 文风」）。 */
    val name: String,
    /** 注入角色（@Depth 时生效；相对位置默认 SYSTEM）。 */
    val role: PromptRole = PromptRole.SYSTEM,
    /** 注入位置。 */
    val position: InjectionPosition = InjectionPosition.BEFORE_CHAR,
    /** @Depth 深度（position == AT_DEPTH 时生效）。 */
    val depth: Int = 4,
    /** 内容（原样注入，不审查不改写）。 */
    val content: String,
    val enabled: Boolean = true,
    /** 同位置内排序。 */
    val order: Int = 100,
)

/** 用户档案（7.1）：头像 / 名字 / 身份，注入 system 稳定前缀的用户段。 */
@Serializable
data class UserProfile(
    val name: String = "",
    /** 身份/自我设定（如「大学生，喜欢科幻」）。 */
    val identity: String = "",
    val avatarPath: String? = null,
) {
    /** 是否已填写（空档案不注入）。 */
    val isConfigured: Boolean get() = name.isNotBlank() || identity.isNotBlank()
}

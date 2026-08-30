package com.luzzymeow.luzzyrp.core.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * [INVARIANT-STREAMING] 消息领域模型（HARD_REQUIREMENTS 规定 1 的数据结构基础）
 *
 * 设计与参考蓝本 rikkahub 对齐：
 *   - 一条消息由多个 part 组成（文本/推理/工具调用/图片），而非单一文本字段——
 *     这是「思考卡片 + 正文气泡 + 工具卡片」三区呈现的结构前提；
 *   - 工具调用结果（output）直接存放在 Tool part 内（原地回填），不存在
 *     独立的 TOOL 角色消息——这是 KV 缓存命中率与 Agentic 不变性的要求。
 */

/** 消息角色。 */
@Serializable
enum class Role {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

/**
 * 工具调用审批状态（五状态机，HARD_REQUIREMENTS Agentic 不变性 A4）。
 *
 * 状态流转：
 *   Auto（默认，无需审批）
 *    └→ Pending（needsApproval 命中，等待用户）→ Approved（同意，继续执行）
 *                                            → Denied(reason)（拒绝，回填拒绝原因）
 *    └→ Answered(answer)（ask_user 类工具：用户的回答即工具输出）
 */
@Serializable
sealed interface ToolApprovalState {
    /** 默认：允许自动执行。 */
    @Serializable
    @SerialName("auto")
    data object Auto : ToolApprovalState

    /** 待用户审批（生成循环在此 break，等待续跑）。 */
    @Serializable
    @SerialName("pending")
    data object Pending : ToolApprovalState

    /** 用户已批准。 */
    @Serializable
    @SerialName("approved")
    data object Approved : ToolApprovalState

    /** 用户拒绝（reason 为拒绝理由，回填为工具输出）。 */
    @Serializable
    @SerialName("denied")
    data class Denied(val reason: String) : ToolApprovalState

    /** ask_user 类工具：用户已作答（answer 即输出）。 */
    @Serializable
    @SerialName("answered")
    data class Answered(val answer: String) : ToolApprovalState
}

/** 消息组成部分。 */
@Serializable
sealed interface UIMessagePart {

    /** 正文文本（可存在多个 part，增量合并到末尾一个）。 */
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : UIMessagePart

    /** 推理内容（思考卡片数据源；从不进入正文气泡）。 */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val thinking: String,
        val signature: String? = null,
        val durationMs: Long? = null,
    ) : UIMessagePart

    /**
     * 工具调用。
     *
     * [INVARIANT-AGENTIC] output 直接内嵌于本 part（原地回填），
     * 生成循环严禁新建 TOOL 角色消息（Agentic 不变性 A2）。
     */
    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: JsonElement,
        val output: List<UIMessagePart>? = null,
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
    ) : UIMessagePart {
        /** 是否已有执行结果（原地回填完成）。 */
        val isExecuted: Boolean get() = output != null

        /** 是否等待用户审批（三 break 条件之二）。 */
        val isPendingApproval: Boolean get() = approvalState is ToolApprovalState.Pending
    }

    /** 图片（用户输入或模型流式输出）。 */
    @Serializable
    @SerialName("image")
    data class Image(val data: String, val mime: String = "image/png") : UIMessagePart
}

/** 一条会话消息。 */
@Serializable
data class UIMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val parts: List<UIMessagePart> = emptyList(),
    val modelId: String? = null,
    val createdAt: Instant = Clock.System.now(),
    /** 本轮生成的 token 用量（流式尾包写入；token 统计行数据源）。 */
    val usage: TokenUsage? = null,
    // 注意：createdAt/usage 不进入模型请求体序列化（KV 稳定性），仅本地持久化与展示使用
) {
    /** 拼接全部正文文本（用于显示与关键词召回扫描）。 */
    fun textContent(): String = parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    /** 拼接全部推理文本（思考卡片展示）。 */
    fun reasoningContent(): String = parts.filterIsInstance<UIMessagePart.Reasoning>().joinToString("") { it.thinking }

    /** 是否包含待执行的（未回填结果的）工具调用。 */
    fun hasUnexecutedTools(): Boolean = parts.any {
        it is UIMessagePart.Tool && !it.isExecuted
    }

    /** 是否包含待审批的工具调用。 */
    fun hasPendingApprovalTools(): Boolean = parts.any {
        it is UIMessagePart.Tool && it.isPendingApproval
    }
}

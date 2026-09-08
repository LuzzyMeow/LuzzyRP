package com.luzzymeow.luzzyrp.assistant.domain.loop

import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult

/**
 * Agent 循环事件（PLAN §5.1）。
 *
 * 纯 Kotlin（无 Android 依赖），由 [AgentLoop] 以 `Flow<AgentEvent>` 发出，
 * ViewModel 消费后驱动 Compose 渲染（思考卡 / 工具卡 / 步骤组 / 审批弹窗）。
 */
sealed interface AgentEvent {

    /** 一轮开始（turn 从 0 起）。 */
    data class TurnStarted(val turn: Int) : AgentEvent

    /** 思考内容增量（reasoning / thinking block）。 */
    data class ReasoningDelta(val text: String) : AgentEvent

    /** 正文增量（markdown 原文，渲染层节流 100ms）。 */
    data class TextDelta(val text: String) : AgentEvent

    /** 工具调用开始（渲染工具卡）。 */
    data class ToolCallStarted(val call: ToolCall) : AgentEvent

    /** 需要用户审批（写类工具逐调用审批，PLAN §13.1）。 */
    data class ToolCallApproval(val call: ToolCall) : AgentEvent

    /** 工具流式输出增量（长命令 / 长输出）。 */
    data class ToolCallProgress(val id: String, val chunk: String) : AgentEvent

    /** 工具执行完成。 */
    data class ToolCallFinished(val id: String, val result: ToolResult) : AgentEvent

    /** `ask_user` 暂停：等待用户回答（结果以工具消息回灌）。 */
    data class AwaitingUserInput(
        val callId: String,
        val question: String,
        val options: List<String>,
        val allowMultiple: Boolean,
    ) : AgentEvent

    /** token 用量。 */
    data class Usage(val input: Int, val output: Int) : AgentEvent

    /**
     * 一轮结束。
     * reason: `stop` | `max_rounds` | `cancelled` | `error`
     */
    data class TurnFinished(val reason: String) : AgentEvent

    /** 错误（[retryable] 供 UI 提示是否可重试）。 */
    data class Error(val message: String, val retryable: Boolean) : AgentEvent
}

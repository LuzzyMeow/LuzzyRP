package com.luzzymeow.luzzyrp.assistant.domain.loop

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmError
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmTransport
import com.luzzymeow.luzzyrp.assistant.domain.llm.ToolCallAccumulator
import com.luzzymeow.luzzyrp.assistant.domain.prompt.ContextBuilder
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.domain.prompt.PromptSpec
import com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillDocument
import com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate
import com.luzzymeow.luzzyrp.assistant.domain.tool.AskUserPrompt
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull

/** [AgentEvent.TurnFinished] 的 reason 取值（契约见 `AgentEvent.kt`）。 */
object TurnReason {
    const val STOP: String = "stop"
    const val MAX_ROUNDS: String = "max_rounds"
    const val CANCELLED: String = "cancelled"
    const val ERROR: String = "error"
}

/**
 * 审批裁决端口（硬性要求 11 第 2 层：写类工具**逐调用**审批）。
 *
 * 事件流只单向输出，无法把「用户点了什么」带回循环，故用这个挂起函数做双向通道：
 * 实现通常是「把审批卡推给 UI，挂起等待用户点按」（`CompletableDeferred`）。
 *
 * 默认实现**拒绝**（安全优先）——调用方若要用计划里「发出审批事件即执行」的宽松语义，
 * 显式传 `ApprovalProvider { _, _ -> true }`；`ApprovalGate.allowOnce/allowForSession`
 * 的既有许可会在本端口之前被消费，用户已批准过就不会再次询问。
 */
fun interface ApprovalProvider {
    suspend fun approve(call: ToolCall, tool: Tool): Boolean
}

/** 未配置工作区时的空实现（工具调用会拿到明确错误，而不是崩溃）。 */
object NoWorkspaceAccess : WorkspaceAccess {
    private fun unavailable(): Nothing = throw IllegalStateException("未配置工作区")
    override suspend fun list(relativeDir: String): List<WorkspaceEntry> = emptyList()
    override suspend fun read(relativePath: String): ByteArray = unavailable()
    override suspend fun write(relativePath: String, bytes: ByteArray) = unavailable()
    override suspend fun delete(relativePath: String) = unavailable()
    override suspend fun move(fromRelative: String, toRelative: String) = unavailable()
    override suspend fun mkdir(relativeDir: String) = unavailable()
    override suspend fun exists(relativePath: String): Boolean = false
}

/**
 * 一次 `run` 的输入（PLAN §5.2 的 `assistant` + `conversation` 折叠为纯 domain 数据）。
 *
 * [request] 是供应商侧模板（baseUrl / apiKey / model / 采样参数 / extraBody / extraHeaders），
 * 循环每轮只覆写 `messages` 与 `tools`——**循环自己不碰网络、不读配置**。
 */
data class AgentRunConfig(
    val request: LlmRequest,
    val assistantId: String,
    val conversationId: String,
    val assistantName: String = "助手",
    val systemPrompt: String = "",
    val workspacePath: String = "",
    val workspace: WorkspaceAccess = NoWorkspaceAccess,
    /** 技能正文（按「全局 → 助手」顺序传入）。 */
    val skills: List<SkillDocument> = emptyList(),
    /** null = 由 [ToolRegistry.conventions] 自动生成。 */
    val toolConventions: String? = null,
    val memoryMode: MemoryMode = MemoryMode.FULL,
    val memoryLimit: Int = ContextBuilder.DEFAULT_MEMORY_LIMIT,
    val nowMillis: Long = System.currentTimeMillis(),
    val zoneId: String = ZoneId.systemDefault().id,
    /** 协作式取消信号（用户点「停止」）——见 [AgentLoop.run] 的取消语义。 */
    val cancelled: () -> Boolean = { false },
    /** 诊断日志（白名单：实现不得写入密钥或文件内容）。 */
    val log: (String) -> Unit = {},
    val approvalProvider: ApprovalProvider = ApprovalProvider { _, _ -> false },
)

/**
 * Agent 循环（PLAN §5.2，纯 Kotlin：不依赖 Android、不直接发网络）。
 *
 * 事件顺序（每轮）：
 * `TurnStarted` → `ReasoningDelta` / `TextDelta` / `Usage`* →（有工具调用时）
 * `ToolCallStarted` →（需审批时 `ToolCallApproval`）→ `ToolCallFinished`（逐个）→ 下一轮；
 * 无工具调用 → `TurnFinished("stop")`。
 *
 * 终止语义：
 * | 情形 | 事件 |
 * |------|------|
 * | 模型未请求工具 | `TurnFinished("stop")` |
 * | 预算超限（轮次 / 时长 / token） | `TurnFinished("max_rounds")` |
 * | `ToolResult.NeedUserInput`（`ask_user`） | `AwaitingUserInput` + `TurnFinished("stop")`，本轮结束（UI 回灌答案后由调用方再次 `run`） |
 * | 传输层错误 | `Error` + `TurnFinished("error")` |
 * | 协作式取消（`config.cancelled`） | `TurnFinished("cancelled")` |
 *
 * 取消说明：**Flow 收集被取消**（UI 直接 cancel）时下游已不存在，任何事件都送不出去，
 * 循环只是停止（工具协程被结构化并发取消）；需要「取消事件」请用 `config.cancelled`
 * 协作式信号（UI 点停止 → 置位 → 循环在下一个检查点发 `TurnFinished("cancelled")`）。
 *
 * 工具异常 / 超时 / 拒绝一律转 [ToolResult.Error] 回灌模型，**不中断整轮**。
 * HARDLINE 拦截在具体工具实现内（如 `terminal_run` / `run_code`），循环只保证不崩。
 */
class AgentLoop(
    private val transport: LlmTransport,
    private val tools: ToolRegistry,
    private val prompt: ContextBuilder = ContextBuilder(),
    private val approval: ApprovalGate = tools.approval,
    private val budget: BudgetGuard = BudgetGuard(),
) {

    /**
     * 执行一次用户输入（多轮工具调用直到模型收尾或触发终止条件）。
     *
     * [history] 为**已完成**的会话消息（不含本轮输入），由调用方持久化。
     */
    fun run(config: AgentRunConfig, history: List<LlmMessage>, userInput: String): Flow<AgentEvent> = channelFlow {
        budget.start()
        val spec = PromptSpec(
            assistantName = config.assistantName,
            systemPrompt = config.systemPrompt,
            model = config.request.model,
            workspacePath = config.workspacePath,
            skills = config.skills,
            toolConventions = config.toolConventions ?: tools.conventions(),
            memoryMode = config.memoryMode,
            memoryLimit = config.memoryLimit,
            nowMillis = config.nowMillis,
            zoneId = config.zoneId,
        )
        var messages: List<LlmMessage> = prompt.build(spec, history, userInput)
        var completedTurns = 0

        while (true) {
            if (config.cancelled()) {
                send(AgentEvent.TurnFinished(TurnReason.CANCELLED))
                return@channelFlow
            }
            budget.check(completedTurns)?.let { stop ->
                config.log("预算超限（$stop），结束本轮")
                send(AgentEvent.TurnFinished(BudgetGuard.REASON_MAX_ROUNDS))
                return@channelFlow
            }

            send(AgentEvent.TurnStarted(completedTurns))
            // 每轮开始前按需压缩（PLAN §5.4）：超限时旧轮转 system 摘要
            if (completedTurns > 0) messages = prompt.compressIfNeeded(messages)

            val text = StringBuilder()
            val accumulator = ToolCallAccumulator()
            var failure: LlmError? = null

            transport.stream(config.request.copy(messages = messages, tools = tools.schemas()))
                .collect { delta ->
                    delta.reasoning?.takeIf { it.isNotEmpty() }?.let { send(AgentEvent.ReasoningDelta(it)) }
                    delta.content?.takeIf { it.isNotEmpty() }?.let {
                        text.append(it)
                        send(AgentEvent.TextDelta(it))
                    }
                    if (delta.toolCalls.isNotEmpty()) accumulator.accept(delta.toolCalls)
                    delta.usage?.let {
                        budget.recordUsage(it.input, it.output)
                        send(AgentEvent.Usage(it.input, it.output))
                    }
                    if (delta.error != null) failure = delta.error
                }

            failure?.let { error ->
                // 传输层错误消息已脱敏（不含密钥）
                config.log("LLM 请求失败：${error.message}")
                send(AgentEvent.Error(error.message, error.retryable))
                send(AgentEvent.TurnFinished(TurnReason.ERROR))
                return@channelFlow
            }

            val calls = accumulator.build()
            messages = messages + LlmMessage(role = LlmRole.ASSISTANT, content = text.toString(), toolCalls = calls)
            if (calls.isEmpty()) {
                send(AgentEvent.TurnFinished(TurnReason.STOP))
                return@channelFlow
            }

            var awaiting: Pair<String, AskUserPrompt>? = null
            for (call in calls) {
                if (config.cancelled()) {
                    send(AgentEvent.TurnFinished(TurnReason.CANCELLED))
                    return@channelFlow
                }
                send(AgentEvent.ToolCallStarted(call))

                val tool = tools.find(call.name)
                if (tool != null && approval.needsApproval(tool)) {
                    // 用户已点过「允许一次」→ 直接消费许可，不再弹卡（PLAN §13.1 第 2 层）
                    if (!approval.consumeOnce(tool.name)) {
                        send(AgentEvent.ToolCallApproval(call))
                        if (!askProvider(config, call, tool)) {
                            val denied = ToolResult.Error("用户未批准该工具调用：${call.name}", retryable = false)
                            messages = messages + toolMessage(call, denied)
                            send(AgentEvent.ToolCallFinished(call.id, denied))
                            continue
                        }
                    }
                }

                val result = executeTool(call, config)
                messages = messages + toolMessage(call, result)
                send(AgentEvent.ToolCallFinished(call.id, result))
                if (result is ToolResult.NeedUserInput) {
                    awaiting = call.id to result.prompt
                    break
                }
            }

            awaiting?.let { (callId, question) ->
                send(
                    AgentEvent.AwaitingUserInput(
                        callId = callId,
                        question = question.question,
                        options = question.options.map { it.label },
                        allowMultiple = question.allowMultiple,
                    )
                )
                // 本轮结束：用户答案由调用方作为 TOOL 消息回灌，然后再次 run
                send(AgentEvent.TurnFinished(TurnReason.STOP))
                return@channelFlow
            }

            completedTurns++
            budget.check(completedTurns)?.let { stop ->
                config.log("预算超限（$stop），结束本轮")
                send(AgentEvent.TurnFinished(BudgetGuard.REASON_MAX_ROUNDS))
                return@channelFlow
            }
        }
    }

    /** 问 [ApprovalProvider] 要裁决（挂起直到用户点按）；异常按拒绝处理。 */
    private suspend fun askProvider(config: AgentRunConfig, call: ToolCall, tool: Tool): Boolean {
        return try {
            config.approvalProvider.approve(call, tool)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            config.log("审批器异常（${e.javaClass.simpleName}），按拒绝处理")
            false
        }
    }

    /**
     * 执行一次工具：超时 → `Error(retryable = true)`；异常 → `Error`；
     * 工具日志同时作为 `ToolCallProgress` 推给 UI（长输出工具用）。
     */
    private suspend fun ProducerScope<AgentEvent>.executeTool(
        call: ToolCall,
        config: AgentRunConfig,
    ): ToolResult {
        val context = object : ToolContext {
            override val assistantId: String = config.assistantId
            override val conversationId: String = config.conversationId
            override val workspace: WorkspaceAccess = config.workspace
            override val cancelled: () -> Boolean = config.cancelled
            override val log: (String) -> Unit = { chunk ->
                config.log(chunk)
                trySend(AgentEvent.ToolCallProgress(call.id, chunk))
            }
        }
        val outcome = withTimeoutOrNull(budget.toolTimeoutMs) {
            try {
                tools.execute(call, context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ToolResult.Error(
                    message = "工具异常（${call.name}）：${e.message ?: e.javaClass.simpleName}",
                    retryable = false,
                )
            }
        }
        return outcome ?: ToolResult.Error(
            message = "工具执行超时（${budget.toolTimeoutMs}ms）：${call.name}",
            retryable = true,
        )
    }

    /** 工具结果 → TOOL 消息（回灌给模型）。 */
    private fun toolMessage(call: ToolCall, result: ToolResult): LlmMessage {
        val content = when (result) {
            is ToolResult.Ok -> buildString {
                append(result.text.ifBlank { "（工具返回空结果）" })
                if (result.attachments.isNotEmpty()) {
                    append("\n[附件] ")
                    append(
                        result.attachments.joinToString("、") { attachment ->
                            attachment.relativePath + (attachment.mimeType?.let { "($it)" } ?: "")
                        }
                    )
                }
            }

            is ToolResult.Error ->
                "ERROR: ${result.message}" + if (result.retryable) "（可重试）" else ""

            is ToolResult.NeedUserInput -> "（已向用户提问，等待回答）"
        }
        return LlmMessage(role = LlmRole.TOOL, content = content, toolCallId = call.id)
    }
}

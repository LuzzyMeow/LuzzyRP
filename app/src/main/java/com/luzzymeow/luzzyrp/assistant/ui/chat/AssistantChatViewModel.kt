package com.luzzymeow.luzzyrp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.loop.AgentEvent
import com.luzzymeow.luzzyrp.assistant.domain.loop.AgentRunConfig
import com.luzzymeow.luzzyrp.assistant.domain.loop.ApprovalProvider
import com.luzzymeow.luzzyrp.assistant.domain.loop.TurnReason
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageRoleUi
import com.luzzymeow.luzzyrp.assistant.ui.model.MessageUi
import com.luzzymeow.luzzyrp.assistant.ui.model.StepGroupUi
import com.luzzymeow.luzzyrp.assistant.ui.model.StepUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ThinkingUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ToolCardUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ToolStatusUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 会话页状态机（P1）。
 *
 * 职责：把 [AgentEvent] 流翻译成 [MessageUi] 列表，并承载两条**双向通道**——
 * 审批（[ApprovalProvider] 挂起等用户点按）与澄清提问（`ask_user`）。
 *
 * 安全（硬性要求）：本类**不打印**任何密钥；工具参数原样进 UI（用户有权看见），
 * 但请求模板中的 apiKey 只存在于 [AssistantRuntime] 的请求对象内。
 */
class AssistantChatViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
    private val conversationId: String,
    private val assistantName: String,
    private val systemPrompt: String,
    private val workspacePath: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 已完成的历史（不含正在流式的那条）。 */
    private val history = mutableListOf<LlmMessage>()

    private var runJob: Job? = null

    /** 协作式取消信号（AgentLoop 在检查点读取）。 */
    @Volatile
    private var cancelled = false

    /** 审批通道：UI 点按后 complete。 */
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var pendingApprovalCall: ToolCall? = null

    // ------------------------------------------------------------------
    // 对外动作
    // ------------------------------------------------------------------

    fun send(text: String) {
        if (text.isBlank() || runJob?.isActive == true) return
        history += LlmMessage(role = LlmRole.USER, content = text)
        _state.value = _state.value.copy(
            messages = _state.value.messages + MessageUi(
                id = "u-${System.nanoTime()}",
                role = MessageRoleUi.USER,
                content = text,
            ),
        )
        cancelled = false
        runJob = viewModelScope.launch { startRunInternal(text) }
    }

    /** 用户点「停止」：置位协作式取消信号，循环会在下一个检查点发 cancelled。 */
    fun stop() {
        cancelled = true
        approvalDeferred?.complete(false)
        approvalDeferred = null
        pendingApprovalCall = null
        _state.value = _state.value.copy(pendingApproval = null)
    }

    /** 审批：允许一次 / 本会话始终允许。 */
    fun approve(allowForSession: Boolean) {
        val call = pendingApprovalCall
        if (call != null) {
            if (allowForSession) runtime.approvalGate.allowForSession(call.name)
            else runtime.approvalGate.allowOnce(call.name)
        }
        pendingApprovalCall = null
        _state.value = _state.value.copy(pendingApproval = null)
        approvalDeferred?.complete(true)
        approvalDeferred = null
    }

    fun deny() {
        pendingApprovalCall = null
        _state.value = _state.value.copy(pendingApproval = null)
        approvalDeferred?.complete(false)
        approvalDeferred = null
    }

    /** 回答 `ask_user`：作为新的一轮用户输入回灌（循环本轮已结束）。 */
    fun answer(text: String) {
        _state.value = _state.value.copy(pendingQuestion = null)
        send(text)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        cancelled = true
        runJob?.cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private suspend fun startRunInternal(userInput: String) {
        val request = runtime.resolveRequestById(assistantId)
        if (request == null) {
            _state.value = _state.value.copy(
                error = "未配置供应商或 API Key——请在 Web 端「设置 → 供应商」中填写后回到助手页重试。",
            )
            return
        }
        val assistant = runtime.assistantEntity(assistantId)
        val config = AgentRunConfig(
            request = request,
            assistantId = assistantId,
            conversationId = conversationId,
            assistantName = assistantName,
            systemPrompt = systemPrompt,
            workspacePath = workspacePath,
            workspace = runtime.workspaceAccessFor(assistantId),
            memoryMode = MemoryMode.fromId(assistant?.memoryMode),
            memoryLimit = assistant?.memoryTopK ?: 8,
            cancelled = { cancelled },
            log = { /* 白名单：不落任何内容 */ },
            approvalProvider = ApprovalProvider { call, _ -> awaitApproval(call) },
        )

        val streamingId = "a-${System.nanoTime()}"
        val textBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        val toolCards = mutableListOf<ToolCardUi>()
        val steps = mutableListOf<StepUi>()

        _state.value = _state.value.copy(
            streaming = true,
            error = null,
            messages = _state.value.messages + MessageUi(
                id = streamingId,
                role = MessageRoleUi.ASSISTANT,
                content = "",
                streaming = true,
            ),
        )

        runtime.loop.run(config, history.toList(), userInput).collect { event ->
            when (event) {
                is AgentEvent.ReasoningDelta -> {
                    reasoningBuffer.append(event.text)
                    refreshStreaming(streamingId, textBuffer, reasoningBuffer, toolCards, steps)
                }

                is AgentEvent.TextDelta -> {
                    textBuffer.append(event.text)
                    refreshStreaming(streamingId, textBuffer, reasoningBuffer, toolCards, steps)
                }

                is AgentEvent.ToolCallStarted -> {
                    toolCards += ToolCardUi(
                        name = event.call.name,
                        argsSummary = summarizeArgs(event.call),
                        status = ToolStatusUi.RUNNING,
                        durationLabel = null,
                    )
                    refreshStreaming(streamingId, textBuffer, reasoningBuffer, toolCards, steps)
                }

                is AgentEvent.ToolCallApproval -> Unit // 审批卡由 ApprovalProvider 推

                is AgentEvent.ToolCallFinished -> {
                    val index = toolCards.indexOfLast { it.status == ToolStatusUi.RUNNING }
                    val status = when (event.result) {
                        is ToolResult.Ok -> ToolStatusUi.SUCCESS
                        is ToolResult.Error -> ToolStatusUi.FAILED
                        is ToolResult.NeedUserInput -> ToolStatusUi.SUCCESS
                    }
                    val preview = when (event.result) {
                        is ToolResult.Ok -> event.result.text.take(400)
                        is ToolResult.Error -> event.result.message.take(400)
                        is ToolResult.NeedUserInput -> null
                    }
                    if (index >= 0) {
                        toolCards[index] = toolCards[index].copy(status = status, resultPreview = preview)
                        steps += StepUi(
                            name = toolCards[index].name,
                            detail = when (event.result) {
                                is ToolResult.Ok -> "ok"
                                is ToolResult.Error -> "failed"
                                is ToolResult.NeedUserInput -> "ask_user"
                            },
                            ok = event.result !is ToolResult.Error,
                        )
                    }
                    refreshStreaming(streamingId, textBuffer, reasoningBuffer, toolCards, steps)
                }

                is AgentEvent.AwaitingUserInput -> {
                    _state.value = _state.value.copy(
                        pendingQuestion = PendingQuestion(
                            question = event.question,
                            options = event.options,
                            allowMultiple = event.allowMultiple,
                        ),
                    )
                }

                is AgentEvent.Error -> {
                    _state.value = _state.value.copy(error = event.message)
                }

                is AgentEvent.Usage -> {
                    _state.value = _state.value.copy(usage = event.input to event.output)
                }

                is AgentEvent.TurnStarted -> Unit

                is AgentEvent.TurnFinished -> {
                    if (event.reason == TurnReason.MAX_ROUNDS) {
                        _state.value = _state.value.copy(
                            error = "已达本轮上限（轮次 / 时长 / token 预算），可继续追问。",
                        )
                    }
                }

                is AgentEvent.ToolCallProgress -> Unit
            }
        }

        val finalText = textBuffer.toString()
        if (finalText.isNotBlank()) history += LlmMessage(role = LlmRole.ASSISTANT, content = finalText)
        _state.value = _state.value.copy(streaming = false)
    }

    private suspend fun awaitApproval(call: ToolCall): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        pendingApprovalCall = call
        _state.value = _state.value.copy(
            pendingApproval = PendingApproval(toolName = call.name, argsJson = call.arguments.toString()),
        )
        return deferred.await()
    }

    private fun refreshStreaming(
        id: String,
        text: StringBuilder,
        reasoning: StringBuilder,
        tools: List<ToolCardUi>,
        steps: List<StepUi>,
    ) {
        val stepGroup = if (steps.size >= 3) {
            StepGroupUi(stepCount = steps.size, totalDurationLabel = "—", steps = steps.toList())
        } else {
            null
        }
        val thinking = if (reasoning.isNotEmpty()) {
            ThinkingUi(
                summary = reasoning.toString().lineSequence().firstOrNull().orEmpty().take(48),
                fullText = reasoning.toString(),
                durationLabel = "—",
            )
        } else {
            null
        }
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { message ->
                if (message.id != id) {
                    message
                } else {
                    message.copy(
                        content = text.toString(),
                        thinking = thinking,
                        tools = tools.toList(),
                        stepGroup = stepGroup,
                    )
                }
            },
        )
    }

    private fun summarizeArgs(call: ToolCall): String = runCatching {
        val first = call.arguments.entries.firstOrNull()?.let { (key, value) ->
            "$key=${value.toString().trim('"').take(32)}"
        } ?: ""
        if (call.arguments.size > 1) "$first …" else first
    }.getOrDefault("")
}

/** 会话页 UI 状态。 */
data class ChatUiState(
    val messages: List<MessageUi> = emptyList(),
    val streaming: Boolean = false,
    val pendingApproval: PendingApproval? = null,
    val pendingQuestion: PendingQuestion? = null,
    val error: String? = null,
    val usage: Pair<Int, Int>? = null,
)

data class PendingApproval(val toolName: String, val argsJson: String)

data class PendingQuestion(
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean,
)

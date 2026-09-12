package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.OpenAiTransport
import com.luzzymeow.luzzyrp.chat.llm.ToolCallAccumulator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 聊天引擎（P2）：把「真实会话检索 → 真实工具调用 → 真实流式生成」串成一条事件流。
 *
 * **零模拟**：每个事件都由真实来源产生——
 * ① [Event.Recall] 来自本机会话检索（[RecallEngine]，词面重叠打分）；
 * ② [Event.ToolCallStarted] / [Event.ToolCallArgs] 来自模型真实发出的 `tool_calls` 增量分片；
 * ③ [Event.ToolCallFinished] 来自 [WorldBookTool.execute] 对模型参数的真实执行结果；
 * ④ [Event.Reasoning] / [Event.Content] 来自 SSE 帧的 `reasoning_content` / `content` 增量；
 * ⑤ [Event.Finished] 来自流末尾的 `finish_reason`。
 *
 * 工具循环：模型请求工具 → 应用执行 → 结果回填 → **再次真实请求**继续生成（最多 [MaxRounds] 轮）。
 *
 * 取消：取消本 Flow 的收集即可（[com.luzzymeow.luzzyrp.chat.llm.SseClient] 会立即关闭连接）。
 */
class ChatEngine(
    private val transport: LlmTransport = OpenAiTransport(),
) {

    /** 引擎事件（按真实发生顺序发出）。 */
    sealed interface Event {
        /** 生成前的真实会话检索结果（全部命中一次性给出）。 */
        data class Recall(val hits: List<RecallEngine.Hit>, val range: String) : Event

        /** 模型开始一次工具调用。 */
        data class ToolCallStarted(val name: String) : Event

        /** 工具参数增量分片（真实逐片到达，用于节点内流式展示）。 */
        data class ToolCallArgs(val chunk: String) : Event

        /** 工具执行完成（[result] 为本机真实执行结果）。 */
        data class ToolCallFinished(val name: String, val args: String, val result: String) : Event

        /** 思考内容增量（模型 reasoning 字段）。 */
        data class Reasoning(val chunk: String) : Event

        /** 正文增量。 */
        data class Content(val chunk: String) : Event

        data class Finished(val finishReason: String?) : Event

        data class Failed(val message: String) : Event
    }

    /**
     * 发起一轮真实对话。
     *
     * @param history 既有对话（仅 user/assistant 正文，按时间升序）。
     * @param userText 本轮用户输入。
     */
    fun run(
        config: TransportConfig,
        history: List<LlmMessage>,
        userText: String,
    ): Flow<Event> = flow {
        if (!config.configured) {
            emit(Event.Failed("未配置供应商：请填写 Base URL / API Key / 模型"))
            return@flow
        }

        // ── ① 真实会话检索（本机执行；命中的历史轮次注入 system） ──
        var turnNo = 0
        val turns: List<Pair<Int, String>> = history
            .filter { it.role == LlmRole.USER || it.role == LlmRole.ASSISTANT }
            .map { m ->
                if (m.role == LlmRole.USER) turnNo++
                turnNo to m.content
            }
        val hits = RecallEngine.search(turns, userText)
        if (hits.isNotEmpty()) {
            emit(Event.Recall(hits, RecallEngine.rangeLabel(hits)))
        }

        // ── ② system = 人设 + 工具提示 + 召回块 ──
        val systemParts = buildList {
            add(VanioCard.persona)
            add(VanioCard.worldToolHint)
            RecallEngine.renderForPrompt(hits).takeIf { it.isNotEmpty() }?.let(::add)
        }
        var messages: List<LlmMessage> = buildList {
            add(LlmMessage(role = LlmRole.SYSTEM, content = systemParts.joinToString("\n\n")))
            addAll(history)
            add(LlmMessage(role = LlmRole.USER, content = userText))
        }

        var round = 0
        // 工具调用被写成文本时的协议噪声过滤（逐轮独立：每轮正文各自成段）
        while (round < MaxRounds) {
            round++
            val markup = ToolMarkupFilter.Stream()
            val acc = ToolCallAccumulator()
            val announced = mutableSetOf<Int>()
            var finishReason: String? = null
            var error: String? = null

            transport.stream(
                LlmRequest(
                    messages = messages,
                    protocol = Protocol,
                    baseUrl = config.chatEndpoint(),
                    apiKey = config.apiKey,
                    model = config.model,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    stream = true,
                    // 工具开关是**真实请求差异**：关闭后不发 tools，模型无从请求工具
                    tools = if (config.toolsEnabled) WorldBookTool.schemas else emptyList(),
                ),
            ).collect { delta ->
                delta.error?.let { error = it.message }
                delta.reasoning?.takeIf { it.isNotEmpty() }?.let { emit(Event.Reasoning(it)) }
                delta.content?.takeIf { it.isNotEmpty() }?.let { chunk ->
                    // 先过协议噪声过滤：DSML 工具标记绝不进正文
                    markup.accept(chunk).takeIf { it.isNotEmpty() }?.let { emit(Event.Content(it)) }
                }
                if (delta.toolCalls.isNotEmpty()) {
                    acc.accept(delta.toolCalls)
                    delta.toolCalls.forEach { d ->
                        if (announced.add(d.index) && !d.name.isNullOrBlank()) {
                            emit(Event.ToolCallStarted(d.name))
                        }
                        d.argumentsChunk?.takeIf { it.isNotEmpty() }?.let { emit(Event.ToolCallArgs(it)) }
                    }
                }
                delta.finishReason?.let { finishReason = it }
            }

            error?.let {
                emit(Event.Failed(it))
                return@flow
            }

            // 本轮正文尾巴（被行缓冲扣住的部分）先放出去，再决定是续跑工具还是收尾
            markup.flush().takeIf { it.isNotEmpty() }?.let { emit(Event.Content(it)) }

            // ── ③ 模型请求了工具：真实执行 → 结果回填 → 再请求一轮 ──
            if (finishReason == FinishToolCalls && !acc.isEmpty()) {
                val calls = acc.build()
                messages = messages + LlmMessage(role = LlmRole.ASSISTANT, toolCalls = calls)
                for (call in calls) {
                    val result = WorldBookTool.execute(call.name, call.rawArguments)
                    emit(Event.ToolCallFinished(call.name, call.rawArguments, result))
                    messages = messages + LlmMessage(
                        role = LlmRole.TOOL,
                        content = result,
                        toolCallId = call.id,
                        name = call.name,
                    )
                }
                continue
            }

            emit(Event.Finished(finishReason))
            return@flow
        }
        emit(Event.Finished("max_rounds"))
    }

    private companion object {
        const val Protocol = "openai"

        /** `finish_reason` 值：模型请求工具（OpenAI 线格式）。 */
        const val FinishToolCalls = "tool_calls"

        /** 最多 3 次真实请求（1 次生成 + 2 轮工具续写），防止工具循环失控。 */
        const val MaxRounds = 3
    }
}

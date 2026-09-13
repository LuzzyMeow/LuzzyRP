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
 * ① [Event.Recall] 由装配层（`RequestBuilder`）检索后经 [ChatRequest.recallHits] 传入；
 * ② [Event.ToolCallStarted] / [Event.ToolCallArgs] 来自模型真实发出的 `tool_calls` 增量分片；
 * ③ [Event.ToolCallFinished] 来自 [WorldBookTool] 对模型参数的真实执行结果；
 * ④ [Event.Reasoning] / [Event.Content] 来自 SSE 帧的 `reasoning_content` / `content` 增量；
 * ⑤ [Event.Finished] 来自流末尾的 `finish_reason`。
 *
 * 工具循环：模型请求工具 → 应用执行 → 结果回填 → **再次真实请求**继续生成（最多 [MaxRounds] 轮）。
 *
 * **请求组装已完全外移**（P5-A / A6）：引擎只面对一个**已装配好的** [ChatRequest]，
 * 负责「发出去 → 收回来 → 事件化 → 工具续跑」。组装（含召回检索与尾部快照）全在纯函数层
 * （[PromptAssembler] + `ui.pages.chat.RequestBuilder`）——只有这样「前缀是否纯追加」才可单测，
 * 也才不会出现「界面走一条组装路径、测试走另一条」的双真源。
 *
 * 取消：取消本 Flow 的收集即可（[com.luzzymeow.luzzyrp.chat.llm.SseClient] 会立即关闭连接）。
 */
class ChatEngine(
    private val transport: LlmTransport = OpenAiTransport(),
    /**
     * 工具执行器（读真库世界书）。
     *
     * 默认走演示书（`WorldBookTool.execute` 的无参路径），只为「单独使用引擎不崩」；
     * 正常路径由调用方用 `WorldBookTool.withEntries(激活条目)` 注入——
     * 这样「模型查到的」与「生成前扫到的」是**同一份数据**。
     */
    private val toolRunner: (String, String) -> String = { name, args -> WorldBookTool.execute(name, args) },
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

        /** 本轮的用量（供应商在流末尾给出；取不到就不发）。 */
        data class Usage(val info: UsageInfo) : Event

        data class Finished(val finishReason: String?) : Event

        data class Failed(val message: String) : Event
    }

    /**
     * 发起一轮真实对话。
     *
     * @param request 已装配好的请求（[PromptAssembler] + `RequestBuilder` 的纯函数产物）。
     *        引擎**不再自己组装**：装配语义属于纯函数层，只有这样「前缀是否纯追加」才可单测。
     * @param toolRunner 本轮工具执行器。**按轮传入**（而不是构造时固定）是因为
     *        「本次激活的世界书条目」是逐轮算出来的；传 null 用构造时的默认。
     */
    fun run(
        config: TransportConfig,
        request: ChatRequest,
        toolRunner: ((String, String) -> String)? = null,
    ): Flow<Event> = flow {
        if (!config.configured) {
            emit(Event.Failed("未配置供应商：请填写 Base URL / API Key / 模型"))
            return@flow
        }

        // ── ① 真实会话召回：命中已在装配层算好，这里只把事件回放给界面 ──
        // 召回块本身已经写进尾部快照（见 RequestBuilder），引擎不持有第二份。
        if (request.recallHits.isNotEmpty()) {
            emit(Event.Recall(request.recallHits, RecallEngine.rangeLabel(request.recallHits)))
        }

        var messages: List<LlmMessage> = request.messages

        var round = 0
        // 工具调用被写成文本时的协议噪声过滤（逐轮独立：每轮正文各自成段）
        while (round < MaxRounds) {
            round++
            val markup = ToolMarkupFilter.Stream()
            val acc = ToolCallAccumulator()
            val announced = mutableSetOf<Int>()
            var finishReason: String? = null
            var error: String? = null

            val llmRequest = LlmRequest(
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
            )
            // 观测层（A7）：**只统计不干预**——它自己吞掉一切异常，绝不打断生成。
            // 放在这里（而不是界面层）是因为只有这里能看到**每一次真实请求**，含工具续跑那一轮。
            CacheObserver.onRequest(llmRequest)

            transport.stream(llmRequest).collect { delta ->
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
                // 用量：供应商在流末尾给（OpenAI 已开 stream_options.include_usage）
                UsageInfo.from(delta)?.let {
                    CacheObserver.onUsage(it)
                    emit(Event.Usage(it))
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
                    val result = (toolRunner ?: this@ChatEngine.toolRunner)(call.name, call.rawArguments)
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

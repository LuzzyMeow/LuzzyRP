package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.OpenAiTransport
import com.luzzymeow.luzzyrp.chat.llm.ToolCall
import com.luzzymeow.luzzyrp.chat.llm.ToolCallAccumulator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * **完整 Agent Loop**（批 B）——DSH `kick() → turn() → step()` 的 Kotlin 等价实现。
 *
 * （证据：`agent-loop/src/agent.ts:225-238, 269-350, 352-498`；结束原因
 * `session/src/types.ts:199-221`；工具配对 `tool-calls.ts:147-161, 249-260`）
 *
 * ## 两级循环
 *
 * - **turn** = 一次用户意图（一次点击发送到彻底停下来的全过程）；
 * - **step** = 一次「请求 → 工具 → 回填」。一个 turn 里可以有多个 step。
 *
 * 全程 `while`、**非递归**（DSH 同）：递归会把「第几步」藏进调用栈，中断与恢复都要靠猜。
 *
 * ## 与批 A 的关系（同一个引擎，不是并列的第二条链路）
 *
 * 组装仍然完全在纯函数层（`PromptAssembler` + `RequestBuilder`），本类只负责
 * 「把请求发出去 → 收事件 → 工具续跑 → 收尾」。所以批 A 的前缀纯追加性质必须继续成立：
 * **工具续跑那一轮也只能在尾部追加**（有单测钉住），否则整个批 A 的收益会被工具调用吃掉。
 *
 * ## 终止条件（B2）
 *
 * | 情形 | [FinishReason] |
 * |---|---|
 * | 没有 tool_call（**主终止条件**） | [FinishReason.Completed] |
 * | 撞输出上限（`length` / `max_tokens`） | [FinishReason.MaxTokens]（**粘性**：不再续跑工具） |
 * | 外部要求中止 | [FinishReason.Aborted] |
 * | 传输错误 | [FinishReason.Error] |
 * | step 数达 [maxSteps] | [FinishReason.StepLimit]（**我们的加法**，DSH 无上限） |
 *
 * 取消（协程被 cancel）不是终止原因：那时 Flow 已经死了，收尾与落库由调用方负责（见 B4）。
 */
class AgentLoop(
    private val transport: LlmTransport = OpenAiTransport(),
    /**
     * 工具执行器（读真库世界书）。
     *
     * 默认走演示书（`WorldBookTool.execute` 的无参路径），只为「单独使用引擎不崩」；
     * 正常路径由调用方用 `WorldBookTool.withEntries(激活条目)` 注入——
     * 这样「模型查到的」与「生成前扫到的」是**同一份数据**。
     */
    private val toolRunner: (String, String) -> String = { name, args -> WorldBookTool.execute(name, args) },
    /** step 安全上限。**这是我们的加法**（DSH 靠外部监督，不设上限）。 */
    private val maxSteps: Int = MAX_STEPS,
) {

    /** 循环状态（B1）。界面与测试据此知道「现在跑到哪一步」。 */
    sealed interface State {
        data object Idle : State

        /** [step] 从 1 开始计数（第几次请求）。 */
        data class Running(val turn: Int, val step: Int) : State

        /** 收到中止请求、正在给未启动的工具调用补配对结果。 */
        data object Aborting : State

        /** 启动时的崩溃修复（扫未闭合的工具配对）。 */
        data object Repairing : State

        /** 收尾（把这一步的结果交给调用方落库）。 */
        data object Finishing : State
    }

    /** 结束原因（DSH `SessionStatus` 的归一，见 `session/src/types.ts:199-221`）。 */
    enum class FinishReason(val id: String) {
        /** 没有 tool_call → 模型自己收的（**主终止条件**）。 */
        Completed("completed"),

        /** 撞输出上限；**粘性**：一旦命中就不再续跑工具。 */
        MaxTokens("max_tokens"),

        /** step 数达上限（我们的安全阀）。 */
        StepLimit("step_limit"),

        /** 被要求中止。 */
        Aborted("aborted"),

        /** 传输/协议错误。 */
        Error("error"),
    }

    /** 引擎事件（按真实发生顺序发出）。 */
    sealed interface Event {
        /** 生成前的真实会话检索结果（全部命中一次性给出）。 */
        data class Recall(val hits: List<RecallEngine.Hit>, val range: String) : Event

        /** 一个 step 开始（界面可用它标注「第 N 步」，也让中断有据可依）。 */
        data class StepStarted(val turn: Int, val step: Int) : Event

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

        /**
         * 收尾。[wire] 是供应商给的原始 `finish_reason`（要做「被截断」提示时用它，
         * 见 `UsageFormat.isTruncated`）；[reason] 是归一后的循环语义。
         */
        data class Finished(val reason: FinishReason, val wire: String? = null) : Event

        data class Failed(val message: String) : Event
    }

    private val _state = MutableStateFlow<State>(State.Idle)

    /** 当前状态（只读；观测与测试用）。 */
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 发起一轮完整对话（一个 turn）。
     *
     * @param request 已装配好的请求（`RequestBuilder` 的纯函数产物）。
     * @param toolRunner 本轮工具执行器（「本次激活的世界书条目」逐轮不同，故按轮传入）。
     * @param shouldAbort 中止探针：每个工具调用**之间**被询问一次。返回 true 时，
     *        未启动的调用会补上合成错误结果（[ToolPairing.abortTimes]）——**配对完整性**优先于
     *        立刻停下：模型下一轮若看到「有调用没有结果」，会以为是自己发错了。
     */
    fun run(
        config: TransportConfig,
        request: ChatRequest,
        toolRunner: ((String, String) -> String)? = null,
        shouldAbort: () -> Boolean = { false },
    ): Flow<Event> = flow {
        if (!config.configured) {
            emit(Event.Failed("未配置供应商：请填写 Base URL / API Key / 模型"))
            return@flow
        }

        // ── 召回：命中已在装配层算好，这里只把事件回放给界面（引擎不持有第二份） ──
        if (request.recallHits.isNotEmpty()) {
            emit(Event.Recall(request.recallHits, RecallEngine.rangeLabel(request.recallHits)))
        }

        var messages: List<LlmMessage> = request.messages
        val executed = toolRunner ?: this@AgentLoop.toolRunner
        var step = 0

        while (true) {
            // step 上限：**在开新 step 之前**判，这样「跑满 50 步」是明确终止而不是第 51 次请求
            if (step >= maxSteps) {
                _state.value = State.Idle
                emit(Event.Finished(FinishReason.StepLimit))
                return@flow
            }
            step++
            _state.value = State.Running(turn = 1, step = step)
            emit(Event.StepStarted(turn = 1, step = step))

            val markup = ToolMarkupFilter.Stream()
            val acc = ToolCallAccumulator()
            val announced = mutableSetOf<Int>()
            var wireFinish: String? = null
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
            CacheObserver.onRequest(llmRequest)

            transport.stream(llmRequest).collect { delta ->
                error = delta.error?.let { it.message } ?: error
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
                delta.finishReason?.let { wireFinish = it }
            }

            // 本轮正文尾巴（被行缓冲扣住的部分）先放出去，再决定后续
            markup.flush().takeIf { it.isNotEmpty() }?.let { emit(Event.Content(it)) }

            if (error != null) {
                _state.value = State.Idle
                emit(Event.Failed(error!!))
                return@flow
            }

            // 粘性 max-tokens：**即使模型同时请求了工具也不再续跑**——输出已经断了，
            // 再续只会得到半截的工具参数（DSH agent.ts:305-310 同）。
            if (isTruncated(wireFinish)) {
                _state.value = State.Idle
                emit(Event.Finished(FinishReason.MaxTokens, wireFinish))
                return@flow
            }

            val calls = if (wireFinish == FinishToolCalls) acc.build() else emptyList()
            if (calls.isEmpty()) {
                _state.value = State.Idle
                emit(Event.Finished(FinishReason.Completed, wireFinish))
                return@flow
            }

            // ── 工具：**按模型给出的顺序**逐个执行，每个调用都必然配一条结果 ──
            messages = messages + LlmMessage(role = LlmRole.ASSISTANT, toolCalls = calls)
            val outcomes = mutableListOf<ToolPairing.Outcome>()
            for (call in calls) {
                if (shouldAbort()) {
                    _state.value = State.Aborting
                    // 未启动的调用（含当前这条）补合成错误结果，配对不留缺口
                    outcomes += ToolPairing.abortTimes(calls.drop(outcomes.size))
                    break
                }
                val result = runCatching { executed(call.name, call.rawArguments) }
                    .getOrElse { failure ->
                        // 工具执行**绝不允许把整个 turn 掀翻**：工具是本地代码（世界书检索、
                        // 将来的文件/网络操作），它抛异常不该等于「应用崩了」。
                        // 而且「配对必须完整」是硬约束——把异常本身作为结果回填，
                        // 模型据此知道这次调用失败了，比让它看到「有调用没结果」安全得多。
                        "Error: tool execution failed (${failure.javaClass.simpleName}). " +
                            "The call did not complete; retry is safe only for read-only operations."
                    }
                outcomes += ToolPairing.Outcome(call = call, result = result, executed = true)
                emit(Event.ToolCallFinished(call.name, call.rawArguments, result))
            }
            // 合成结果也要**发事件**：界面上那个工具节点必须显示「被中止」，
            // 否则用户看到的是「模型调了工具但什么都没发生」（同一件事的两种说法不一致）。
            outcomes.filterNot { it.executed }.forEach {
                emit(Event.ToolCallFinished(it.call.name, it.call.rawArguments, it.result))
            }
            // 配对结果按**模型顺序**回填（不是按完成顺序）
            for (outcome in outcomes) {
                messages = messages + LlmMessage(
                    role = LlmRole.TOOL,
                    content = outcome.result,
                    toolCallId = outcome.call.id,
                    name = outcome.call.name,
                )
            }
            if (outcomes.any { !it.executed }) {
                _state.value = State.Idle
                emit(Event.Finished(FinishReason.Aborted, wireFinish))
                return@flow
            }
        }
    }

    companion object {
        const val Protocol = "openai"

        /** `finish_reason` 值：模型请求工具（OpenAI 线格式）。 */
        const val FinishToolCalls = "tool_calls"

        /** 撞输出上限的结束原因（三协议归一：OpenAI `length` / Anthropic `max_tokens`）。 */
        val TRUNCATED = setOf("length", "max_tokens")

        fun isTruncated(wire: String?): Boolean = wire?.lowercase() in TRUNCATED

        /**
         * step 安全上限。
         *
         * **这是我们的加法**（如实标注）：DSH 不设上限，因为它有外部监督与预算控制；
         * 我们是「一次点击跑到停」，没有外部刹车，所以需要一个防御模型死循环的硬界。
         * 50 步对正常的 RP 场景足够宽（正常一轮 1~3 步）。
         */
        const val MAX_STEPS: Int = 50
    }
}

/** 工具调用与结果的配对（B3）。纯函数，可单测。 */
object ToolPairing {

    /** 一次工具调用的结果（[executed] = false 表示它没被真正执行，结果是合成的）。 */
    data class Outcome(
        val call: ToolCall,
        val result: String,
        val executed: Boolean,
    )

    /**
     * 未启动的调用 → 合成错误结果（DSH `tool-calls.ts:249-260` 的文案）。
     *
     * 为什么必须补：模型下一轮会看到自己的 `tool_calls`，若其中某条**没有**对应的
     * tool 结果消息，多数供应商会直接报协议错误（配对是硬要求），而宽松的供应商会让模型
     * 以为「工具没返回」从而反复重试同一个调用。补一条明确的「被中止」比留空更安全。
     */
    fun abortTimes(calls: List<ToolCall>): List<Outcome> =
        calls.map { Outcome(call = it, result = ABORTED, executed = false) }

    /** 中止导致未派发的调用 → 合成结果的固定文案。 */
    const val ABORTED: String = "Error: tool call aborted before dispatch"

    /**
     * **未拿到结果**的调用 → 合成结果文案（崩溃/中断修复）。
     *
     * ⚠️ 这里的修复点是**展开进请求的那一刻**（[ToolTrail.expand]），不是「启动时扫日志」：
     * 我们的存储把「一次生成的正文 + 工具轨迹」放在**同一行**（见 [ToolStep]），
     * 所以「有调用没结果」在存储层就是轨迹里的 `result == null`，
     * 它**永远进不了请求**——比启动时扫一遍日志更早、也更难绕过。
     *
     * 文案刻意保守：明确说「结果未知」，并提示「只读/幂等操作才可重试」。
     * 若写成「执行失败」，模型会放心重试——对一个已经产生副作用的调用重试是危险的。
     */
    const val UNKNOWN: String =
        "Error: previous run was interrupted before this tool returned a result. " +
            "The outcome is unknown — only retry if the operation is read-only or idempotent."
}

package com.luzzymeow.luzzyrp.chat.llm

import kotlin.math.max

/**
 * 预算守卫（纯 Kotlin + 可注入时钟，可单测）。
 *
 * 四道闸：
 * | 项 | 默认 | 触发结果 |
 * |----|------|---------|
 * | 轮次 | 24 | 调用方结束本轮 |
 * | 单次工具超时 | 120s | 该次调用转错误，流程继续 |
 * | 总时长 | 30 分钟 | 调用方结束本轮 |
 * | token（输入+输出累计） | 20 万 | 调用方结束本轮 |
 *
 * v2.0 说明：上下文装配已归 WebView 侧 JS，本层的原生传输是**单次请求**语义，
 * 因此 [BudgetGuard] 目前只被 [com.luzzymeow.luzzyrp.chat.ChatJobs] 用于登记
 * usage 与诊断；多轮工具循环若将来下沉到 Kotlin，可直接复用本类而无需改动。
 *
 * 本文件自 v1.5.0 的助手模块（commit 0392b662 前）恢复；仅有 KDoc 去除了对已删除
 * 类型的引用，代码逻辑一字未改（其单测逐条恢复，即为该结论的证据）。
 */
class BudgetGuard(
    /** 轮次上限。 */
    val maxRounds: Int = DEFAULT_MAX_ROUNDS,
    /** 单次工具执行超时。 */
    val toolTimeoutMs: Long = DEFAULT_TOOL_TIMEOUT_MS,
    /** 单次 `run` 的总时长上限。 */
    val totalDurationMs: Long = DEFAULT_TOTAL_DURATION_MS,
    /** token 预算：输入 + 输出累计（缺省见类注释）。 */
    val tokenBudget: Long = DEFAULT_TOKEN_BUDGET,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 超限类型。 */
    enum class StopReason { MAX_ROUNDS, TIMEOUT, TOKEN_BUDGET }

    private var startedAtMillis: Long = NOT_STARTED
    private var inputTokens: Long = 0
    private var outputTokens: Long = 0

    /** 开始一次 run（重置用量与计时）。 */
    fun start() {
        startedAtMillis = clock()
        inputTokens = 0
        outputTokens = 0
    }

    /** 是否已 [start]。 */
    fun started(): Boolean = startedAtMillis != NOT_STARTED

    /** 已用时长（未 start 时为 0，时钟回拨安全）。 */
    fun elapsedMs(): Long =
        if (!started()) 0L else (clock() - startedAtMillis).coerceAtLeast(0L)

    /** 记录一轮的 token 用量（负数按 0 计）。 */
    fun recordUsage(input: Int, output: Int) {
        inputTokens += max(0, input).toLong()
        outputTokens += max(0, output).toLong()
    }

    val inputTokensUsed: Long get() = inputTokens
    val outputTokensUsed: Long get() = outputTokens
    val tokensUsed: Long get() = inputTokens + outputTokens

    /**
     * 检查是否该停：[completedTurns] = 已完成的轮数。
     *
     * 语义：轮次达上限、时长超限、token 超限任一命中即返回原因；否则 null（继续）。
     */
    fun check(completedTurns: Int): StopReason? {
        if (completedTurns >= maxRounds) return StopReason.MAX_ROUNDS
        if (started() && elapsedMs() > totalDurationMs) return StopReason.TIMEOUT
        if (tokensUsed >= tokenBudget) return StopReason.TOKEN_BUDGET
        return null
    }

    /** 一轮开始前的检查（等价于 [check]，语义更直白）。 */
    fun checkBeforeTurn(turn: Int): StopReason? = check(turn)

    /** 一轮结束后的检查（[completedTurns] 为**已完成**轮数）。 */
    fun checkAfterTurn(completedTurns: Int): StopReason? = check(completedTurns)

    /** 事件流里的统一 reason：预算类一律映射为 `max_rounds`。 */
    fun reasonId(stop: StopReason): String = when (stop) {
        StopReason.MAX_ROUNDS, StopReason.TIMEOUT, StopReason.TOKEN_BUDGET -> REASON_MAX_ROUNDS
    }

    companion object {
        const val DEFAULT_MAX_ROUNDS: Int = 24
        const val DEFAULT_TOOL_TIMEOUT_MS: Long = 120_000L
        const val DEFAULT_TOTAL_DURATION_MS: Long = 30L * 60L * 1_000L
        const val DEFAULT_TOKEN_BUDGET: Long = 200_000L

        /** 预算类终止在事件流中的统一 reason。 */
        const val REASON_MAX_ROUNDS: String = "max_rounds"

        private const val NOT_STARTED = -1L
    }
}

package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **前缀缓存观测层**（A7）——DSH「前缀稳定是涌现的」的可量化仪表盘。
 *
 * ## 纪律：只统计，不干预
 *
 * 本对象**永不**修改请求、结果或流程；任何一步算不出来都静默降级（返回 null）。
 * 观测层一旦能影响主链路，它自己就会成为新的故障源——所以它读的是请求的**副本视图**
 * （只做 `toOpenAiJson()` 序列化取长度），不持有也不改写任何消息对象。
 *
 * ## 口径（与 WebView 版 `ext/luzzy-prefix-guard.js` 逐字对齐）
 *
 * 两代实现（JS / Kotlin）的数字必须能互相印证，所以这里不发明新口径：
 * - 每轮把 messages 逐条序列化成协议字节，算**与上一轮的公共前缀字符数**；
 * - 单轮 `commonRatio = 公共前缀字符 / 上一轮总字符`；
 * - 累计 `avgCommonRatio = Σ公共前缀字符 / Σ上一轮总字符`（**加权**，不是各轮算术均值）；
 * - `hitRate = ΣcachedTokens / ΣpromptTokens`（供应商没给 cached 就不算，不把未知当 0）。
 *
 * ## 为什么还要记「请求头（缓存纪元）」
 *
 * 前缀缓存是**服务端按模型/供应商维度**存的：system 一个字没改，但把模型从 A 换成 B，
 * 上个纪元的缓存对新模型完全不存在。只盯「公共前缀占比」会把这种情况误读成「缓存该命中」，
 * 所以每轮同时比一个请求头指纹（协议 + 端点 + 模型 + 采样值 + 工具集），
 * 变化就记一次 [Summary.headerChanges]——**它是负控试验的判据**（PLAN §7.1-2）。
 */
object CacheObserver {

    /**
     * 请求头指纹 = 「缓存纪元」的身份。
     *
     * **刻意不含 `apiKey`**：轮换密钥不该被记成缓存失效（同一个模型同一份前缀，
     * 服务端缓存仍然有效）。含工具的**内容哈希**而不是数量：工具集改了名字/参数同样失效。
     */
    data class RequestHeader(
        val protocol: String,
        val baseUrl: String,
        val model: String,
        val temperature: Double?,
        val maxTokens: Int?,
        val toolsHash: Int,
    ) {
        /** 一行可读标识（日志与用量页用；不含密钥）。 */
        val label: String get() = "$protocol · $model · t=$temperature · max=$maxTokens · tools#${toolsHash}"

        companion object {
            fun of(request: LlmRequest): RequestHeader = RequestHeader(
                protocol = request.protocol,
                baseUrl = request.baseUrl,
                model = request.model,
                temperature = request.temperature,
                maxTokens = request.maxTokens,
                toolsHash = request.tools.joinToString("\u0000") { it.toString() }.hashCode(),
            )
        }
    }

    /** 一轮请求的观测记录。 */
    data class Turn(
        val index: Int,
        val messageCount: Int,
        /** 本轮请求的协议字节长度。 */
        val chars: Int,
        /** 与上一轮的公共前缀字符数（首轮为 0）。 */
        val commonChars: Int,
        /** 上一轮请求的协议字节长度（首轮为 0）。 */
        val previousChars: Int,
        /** 公共前缀占比（首轮无对比对象 → null）。 */
        val commonRatio: Double?,
        val header: RequestHeader,
        val headerChanged: Boolean,
        val promptTokens: Int? = null,
        val cachedTokens: Int? = null,
    ) {
        /** 本轮命中率（供应商给了 cached 才有）。 */
        val hitRate: Double? get() = rate(cachedTokens, promptTokens)
    }

    /** 会话级汇总（用量页与 adb 探针读它）。 */
    data class Summary(
        /** 已观测的请求轮数（含工具续跑那一轮——它也是真实请求）。 */
        val rounds: Int = 0,
        /** 有对比对象的轮数（= rounds - 1，首轮不算）。 */
        val comparableRounds: Int = 0,
        val avgCommonRatio: Double? = null,
        val lastCommonRatio: Double? = null,
        val promptTokens: Int = 0,
        val cachedTokens: Int = 0,
        val hitRate: Double? = null,
        val headerChanges: Int = 0,
        val lastHeaderLabel: String? = null,
        /** 最近若干轮明细（界面表格用；有上限，见 [MAX_TURNS]）。 */
        val turns: List<Turn> = emptyList(),
    ) {
        /** 公共前缀是否达到批 A 的验收线（PLAN §7.1：≥ 0.95）。 */
        val meetsTarget: Boolean get() = (avgCommonRatio ?: 0.0) >= TARGET_RATIO

        /**
         * 一行给日志/adb 探针的汇总。
         *
         * 真机（release 包、不可 run-as）**只能靠这一行**看缓存表现，所以四项关键数字必须齐：
         * 轮数 / 公共前缀占比 / 命中率 / 缓存纪元变化次数。
         */
        fun report(): String = buildString {
            append("轮 $rounds")
            append(" · 公共前缀 ").append(percent(avgCommonRatio))
            append(" · 末轮 ").append(percent(lastCommonRatio))
            append(" · 命中 ").append(percent(hitRate))
                .append("（").append(format(cachedTokens)).append("/").append(format(promptTokens)).append("）")
            append(" · 纪元变化 $headerChanges")
            lastHeaderLabel?.let { append(" · ").append(it) }
        }
    }

    /** 验收线（PLAN §7.1-1）。 */
    const val TARGET_RATIO: Double = 0.95

    /** 明细上限：界面只画最近这些轮，避免长会话把内存拖起来。 */
    const val MAX_TURNS: Int = 50

    private val _summary = MutableStateFlow(Summary())

    /** 供界面（用量页）订阅；观测层唯一的对外可变状态。 */
    val summary: StateFlow<Summary> = _summary.asStateFlow()

    /** 当前汇总（日志探针 / 非 Compose 调用点用）。 */
    fun current(): Summary = _summary.value

    // ---- 内部累计量（与 WebView 版 stats 一一对应）----
    private var previousSignatures: List<String>? = null
    private var previousChars: Int = 0

    /** 累计「公共前缀字符」与「上一轮总字符」（加权占比的分子分母）。 */
    private var sumCommonChars: Int = 0
    private var sumPreviousChars: Int = 0

    private var lastHeader: RequestHeader? = null
    private var headerChanges: Int = 0
    private var lastTurn: Turn? = null

    /**
     * 观测一次真实请求（在 `transport.stream()` 之前调用）。
     *
     * @return 本轮记录；算不出来时返回 null（**静默降级**，绝不影响生成）。
     */
    fun onRequest(request: LlmRequest): Turn? = runCatching {
        val signatures = request.messages.map(::signature)
        val chars = signatures.sumOf { it.length }
        val previous = previousSignatures
        val common = if (previous == null) 0 else commonPrefixChars(previous, signatures)
        val ratio = if (previous != null && previousChars > 0) {
            common.toDouble() / previousChars
        } else {
            null
        }
        val header = RequestHeader.of(request)
        val changed = lastHeader != null && lastHeader != header

        if (previous != null) {
            sumCommonChars += common
            sumPreviousChars += previousChars
        }
        if (changed) headerChanges++

        val turn = Turn(
            index = (_summary.value.rounds) + 1,
            messageCount = request.messages.size,
            chars = chars,
            commonChars = common,
            previousChars = if (previous == null) 0 else previousChars,
            commonRatio = ratio,
            header = header,
            headerChanged = changed,
        )
        lastTurn = turn
        previousSignatures = signatures
        previousChars = chars
        lastHeader = header
        publish(turn)
        turn
    }.getOrNull()

    /**
     * 记账本轮用量（流末尾的 usage 帧到达时调用）。
     *
     * 累计值按 `cached` 的**可得性**分开算：供应商没给该字段时**只加分母**，不算成 0。
     * 把「未上报」当成「命中 0%」会粉饰出一条假的下降曲线；偏低是保守方向，
     * 且 [Summary.report] 同时给出原始计数（cached/prompt）供核对。
     */
    fun onUsage(usage: UsageInfo?) {
        runCatching {
            if (usage == null) return
            val current = _summary.value
            val turns = current.turns.toMutableList()
            if (turns.isNotEmpty()) {
                val updated = turns[turns.lastIndex].copy(
                    promptTokens = usage.input,
                    cachedTokens = usage.cached,
                )
                turns[turns.lastIndex] = updated
                lastTurn = updated
            }
            val promptTokens = current.promptTokens + usage.input
            val cachedTokens = current.cachedTokens + (usage.cached ?: 0)
            val known = usage.cached != null || current.cachedTokens > 0
            _summary.value = current.copy(
                promptTokens = promptTokens,
                cachedTokens = cachedTokens,
                hitRate = if (known) rate(cachedTokens, promptTokens) else null,
                turns = turns,
            )
        }
    }

    /** 清空全部统计（测试与「新会话」用）。 */
    fun reset() {
        previousSignatures = null
        previousChars = 0
        sumCommonChars = 0
        sumPreviousChars = 0
        lastHeader = null
        headerChanges = 0
        lastTurn = null
        _summary.value = Summary()
    }

    // ------------------------------------------------------------------ 纯函数

    /**
     * 两组消息签名的**公共前缀字符数**（逐条比字节；某一条不同即停在那里）。
     *
     * 与 WebView 版 `commonPrefixLen` 同语义：比的是**协议字节**而不是「文本是否相似」——
     * 服务端的 KV 缓存就是按 token 前缀命中的，差一个字符等于差一个 token。
     */
    fun commonPrefixChars(previous: List<String>, current: List<String>): Int {
        var total = 0
        val limit = minOf(previous.size, current.size)
        for (index in 0 until limit) {
            if (previous[index] != current[index]) break
            total += current[index].length
        }
        return total
    }

    /** 一条消息的协议字节签名（就是真正发出去的那串 JSON）。 */
    private fun signature(message: LlmMessage): String = message.toOpenAiJson().toString()

    private fun publish(turn: Turn) {
        val current = _summary.value
        val turns = (current.turns + turn).takeLast(MAX_TURNS)
        _summary.value = current.copy(
            rounds = current.rounds + 1,
            comparableRounds = if (turn.commonRatio != null) current.comparableRounds + 1 else current.comparableRounds,
            avgCommonRatio = if (sumPreviousChars > 0) sumCommonChars.toDouble() / sumPreviousChars else null,
            lastCommonRatio = turn.commonRatio,
            headerChanges = headerChanges,
            lastHeaderLabel = turn.header.label,
            turns = turns,
        )
    }

    private fun rate(part: Int?, whole: Int?): Double? {
        if (part == null || whole == null || whole <= 0) return null
        return part.toDouble() / whole
    }

    private fun percent(value: Double?): String =
        if (value == null) "无数据" else "%.4f".format(value)

    private fun format(value: Int): String = "%,d".format(value)
}

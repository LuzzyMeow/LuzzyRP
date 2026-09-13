package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole

/**
 * **token 估算**（无分词器时的确定性口径）。
 *
 * 为什么不引分词器：三家协议各有各的分词器（同一句话在三家下 token 数都不同），
 * 引一份只会让「压缩时机」看起来精确、实际仍偏；而压缩的门槛本来就是一个**保守的**安全阀
 * （真正的精确值由供应商在 `usage.prompt_tokens` 里给，见 `UsageInfo`）。
 *
 * 口径（刻意偏**高估**，早压缩比溢出好）：
 * - CJK 一字 ≈ 1 token；
 * - 其余字符 4 个 ≈ 1 token（向上取整）；
 * - 每条消息再加固定的结构开销（role / 分隔符）。
 *
 * 天花板（如实登记）：英文长文会被高估约 10~20%，等宽/代码文本差距更大；
 * 偏差由用户填的 `contextWindow` 吸收（见 `TransportConfig.contextWindow` 的说明）。
 * 升级路径：把上一轮 `UsageInfo.input` 与当时的估算做个比值校准（DSH 用真实 usage 定预算）。
 */
object ContextBudget {

    /** 单条消息的结构开销（role 标记 + 分隔符）。 */
    private const val MESSAGE_OVERHEAD = 4

    fun estimate(messages: List<LlmMessage>): Int = messages.sumOf { estimateOne(it) }

    /** 单条消息的估算（正文 + 名字 + 工具调用参数）。 */
    fun estimateOne(message: LlmMessage): Int {
        var tokens = MESSAGE_OVERHEAD + estimateText(message.content)
        tokens += estimateText(message.name.orEmpty())
        for (call in message.toolCalls) {
            tokens += estimateText(call.name) + estimateText(call.rawArguments) + 8
        }
        return tokens
    }

    /** 文本 → token 的保守估算（见类注释的口径）。 */
    fun estimateText(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }

    /** 表意文字与全角标点：这些字符的 token 密度接近 1:1。 */
    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2E80..0x9FFF || // 部首扩展 / 假名 / 注音 / CJK 统一表意
            code in 0xF900..0xFAFF || // 兼容表意
            code in 0xAC00..0xD7AF || // 谚文音节
            code in 0xFF00..0xFFEF // 全角形式 / 全角标点
    }
}

/**
 * **上下文压缩**（B5）——DSH `compaction-basic` 的 Kotlin 等价实现。
 *
 * （证据：`compaction-basic/src/config.ts:20,23` 的阈值与保留比例；
 * `compaction-basic/src/region.ts:117-154` 的裁切边界；`region.ts:515-524` 的摘要请求重放。）
 *
 * ## 三条硬约束（各有一条单测钉住）
 *
 * 1. **system 头与所有非历史消息永不裁**：角色卡 / 破限预设 / 本轮输入都属于「这一轮必须成立的前提」，
 *    裁掉它们不是省 token，是换了一个模型。只有 [LlmMessage.fromHistory] 的消息参与裁切。
 * 2. **不切开 tool_call / tool_result 配对**：结果没有对应的调用、或调用没有结果，
 *    两家协议都会直接报错；所以裁切单位是**整组**（`assistant(tool_calls)` + 紧随的 `tool` 结果）。
 * 3. **摘要请求按原前缀原样重放**：`system + tools + 被裁消息` 逐字节不动，只在尾部追加摘要指令
 *    → 这一次摘要调用**吃一次前缀缓存**（DSH 同法）。所以摘要请求与正式请求只有 `messages` 不同。
 *
 * ## 为什么 `Plan` 里带 [Plan.cut] 而不是「裁好的消息」
 *
 * 摘要是**要花钱的异步调用**：先算计划（纯函数、可单测），拿到摘要正文后再 [rebuild]。
 * 两步分开之后，「计划错了」与「摘要失败了」是两件可以分别断言、分别降级的事。
 */
object Compaction {

    /** 触发阈值：用量达上下文窗口的这个比例时压缩（DSH `config.ts:20`）。 */
    const val THRESHOLD: Double = 0.8

    /** 保留比例：预算留给「最近的这一段」（DSH `config.ts:23`）。 */
    const val RETAIN_RATIO: Double = 0.16

    /** 触发线（tokens）。 */
    fun triggerTokens(contextWindow: Int): Int = (contextWindow * THRESHOLD).toInt()

    /** 保留段预算（tokens）。 */
    fun keepTokens(contextWindow: Int): Int = (contextWindow * RETAIN_RATIO).toInt().coerceAtLeast(1)

    /** 是否该压缩。[contextWindow] ≤ 0 表示用户关掉了自动压缩。 */
    fun needed(messages: List<LlmMessage>, contextWindow: Int): Boolean =
        contextWindow > 0 && ContextBudget.estimate(messages) >= triggerTokens(contextWindow)

    /**
     * 裁切计划。
     *
     * @param cut 保留段在原消息序列里的起始下标（其之前的历史全部被裁）
     * @param dropped 被裁掉的**历史**消息条数（非历史消息一条都不裁）
     * @param kept 保留的消息条数（含本轮输入等非历史消息）
     * @param keptTokens 保留段里历史部分的估算 tokens（用于核对预算）
     */
    data class Plan(
        val cut: Int,
        val dropped: Int,
        val kept: Int,
        val tokensBefore: Int,
        val keptTokens: Int,
        /** 摘要请求要发的消息：**原前缀逐字节重放** + 一条摘要指令。 */
        val summaryRequest: List<LlmMessage>,
    )

    /**
     * 算裁切计划；没有可裁的历史时返回 null（例如第一轮、或历史全在一组里）。
     *
     * 预算从**最新**往最老累加，整单位保留：这样留下的永远是「最近的连续一段」，
     * 而不是「挑几条重要的」——后者会让模型看到断裂的对话（且每次都挑得不一样）。
     */
    fun plan(messages: List<LlmMessage>, keepTokens: Int): Plan? {
        if (messages.none { it.fromHistory }) return null
        val units = units(messages)

        var used = 0
        var keptUnits = 0
        var keepFrom = -1
        for (unit in units.asReversed()) {
            if (!messages[unit.first].fromHistory) continue
            val cost = unit.sumOf { ContextBudget.estimateOne(messages[it]) }
            // 最新的单位**无条件保留**：只揣着简报去回答本轮，等于把「刚才说到哪」也裁掉了
            if (keptUnits > 0 && used + cost > keepTokens) break
            used += cost
            keptUnits++
            keepFrom = unit.first
        }
        if (keepFrom < 0 || keepFrom == 0) return null

        return Plan(
            cut = keepFrom,
            dropped = messages.take(keepFrom).count { it.fromHistory },
            kept = messages.size - keepFrom,
            tokensBefore = ContextBudget.estimate(messages),
            keptTokens = used,
            summaryRequest = messages.take(keepFrom) + LlmMessage(role = LlmRole.USER, content = Instruction),
        )
    }

    /**
     * 用摘要正文重建消息序列：`原头（system/预设/…） + 简报 + 保留尾部`。
     *
     * 简报落在**它取代的那段历史的末尾**——位置上等价于「被裁掉的那段变成了一句话」，
     * 于是下一轮的前缀 = 头 + 简报 + 尾部，之后照旧纯追加（压缩只断一次前缀，不是每轮都断）。
     */
    fun rebuild(messages: List<LlmMessage>, cut: Int, summary: String): List<LlmMessage> =
        messages.take(cut).filterNot { it.fromHistory } + summaryMessage(summary) + messages.drop(cut)

    /** 简报消息：wire 上是 user，但不参与「用户发言」的语义（见返回值上的标记）。 */
    fun summaryMessage(summary: String): LlmMessage = LlmMessage(
        role = LlmRole.USER,
        content = SummaryHeader + "\n\n" + summary.trim(),
        // 不是用户发言：召回轮号（turnsOf）据此跳过它，否则「第 N 轮」会虚高
        runtimeSnapshot = true,
        // 它是已定稿的历史事实，不参与相邻同 role 合并
        fromHistory = true,
    )

    /** 简报正文的抬头：让模型知道这段是什么、以及它与后续消息同样有效。 */
    const val SummaryHeader: String =
        "[对话简报] 以下是此前对话的压缩简报，取代被裁掉的旧消息；" +
            "其中的事实、关系与未了结的事同样有效，请继续据此回应。"

    /**
     * 摘要指令（追加在原前缀之后）。
     *
     * 刻意写全四类「后续轮次真正会用到」的东西：缺了第 ③ 类（未了结的线头），
     * 模型会把用户没得到回应的请求当成没提过。
     */
    const val Instruction: String =
        "[上下文压缩] 以上是本次对话到目前为止的全部内容。请把它压缩成一份简报，供后续轮次继续使用：" +
            "① 出场角色与当前关系/状态；② 已发生的关键事件（按顺序，含具体的人名、地点、物品、承诺）；" +
            "③ 未了结的线头与用户尚未得到回应的请求；④ 通过工具查到、之后仍会用到的设定事实。" +
            "不要编造、不要对用户说话、不要复述这段说明，只输出简报正文，并使用对话原本的语言。"

    /**
     * 把消息切成「整体保留 / 整体丢弃」的单位。
     *
     * 只有一种跨消息的单位：`assistant(tool_calls)` 与紧随其后的 `tool` 结果——
     * 它们是协议层的一个原子结构（少一半就是非法请求），所以必须同进同退。
     */
    private fun units(messages: List<LlmMessage>): List<IntRange> {
        val units = mutableListOf<IntRange>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            if (message.role == LlmRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
                var end = index
                while (end + 1 < messages.size && messages[end + 1].role == LlmRole.TOOL) end++
                units += index..end
                index = end + 1
            } else {
                units += index..index
                index++
            }
        }
        return units
    }
}

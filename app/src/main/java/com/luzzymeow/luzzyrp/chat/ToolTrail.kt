package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.ToolCall

/**
 * 一次工具调用的**耐久记录**（B3）。
 *
 * ## 为什么必须落库，而不是只活在那一轮的内存里
 *
 * DSH 的第一原则是 **"Model-visible ⟺ durably referenced"**：模型看到过的事实必须能耐久引用。
 * 工具调用也一样——它发生在「用户这一轮」的中间，但**下一轮**模型就该看得见自己查过什么。
 * 不落库的后果有两个，都是真实缺陷：
 *
 * 1. **模型失忆**：它上一轮查了世界书，下一轮完全不记得，于是重复查、或前后设定矛盾；
 * 2. **无法修复**：进程若在工具执行中途被杀，存储里没有「有调用没结果」这回事，
 *    下一轮自然也无从补救（[ToolPairing.repaired] 就永远不会被触发 = 死代码）。
 *
 * ## 为什么 `result` 可空
 *
 * `null` = **这次调用没拿到结果**（回合被中断/进程被杀）。它落库后，
 * 下一轮由 [ToolPairing.repaired] 补上「结果未知，勿盲目重试」的合成结果——
 * 这正是「中断」与「崩溃修复」两件事在数据上的接头处。
 */
data class ToolStep(
    val name: String,
    val args: String,
    /** null = 未拿到结果（见类注释）。 */
    val result: String?,
)

/**
 * 工具轨迹 ↔ 协议消息的**纯函数**变换（B3）。
 *
 * 单独成对象而不是塞进 `RequestBuilder`：它有两个独立可测的性质——
 * 「展开成什么序列」与「悬空配对怎么补」，与「这条消息属于哪一段」无关。
 */
object ToolTrail {

    /**
     * 把一条带工具轨迹的 AI 消息展开成协议消息序列：
     *
     * ```
     * assistant(tool_calls)  →  tool(结果) × N  →  assistant(正文)
     * ```
     *
     * ## 顺序为什么是这个
     *
     * 工具是在「这一轮」的正文之前发生的；落库时两者合成了一行（正文 + 轨迹），
     * 展开时必须还原成真实发生顺序，否则模型看到的是「先说话、后查资料」——
     * 那会让它以为工具结果是它说完话才拿到的（DSH `tool-calls.ts:147-161` 同）。
     *
     * ## 为什么要合成 id，而不是存 id
     *
     * 配对只需要「assistant 里的 id」与「tool 消息里的 id」**内部一致**；
     * 真实的协议 id 由供应商生成、跨轮没有意义。合成 id 用「消息在历史里的下标 + 序号」，
     * 于是**同一条历史每次都展开成同样的字节**——这是前缀缓存能继续成立的前提
     * （批 A 的不变量不能被批 B 破坏）。
     */
    fun expand(messageIndex: Int, text: String, trail: List<ToolStep>): List<LlmMessage> {
        if (trail.isEmpty()) return listOf(LlmMessage(role = LlmRole.ASSISTANT, content = text))
        val calls = trail.mapIndexed { i, step ->
            ToolCall(
                id = callId(messageIndex, i),
                name = step.name,
                // 参数可能不是合法 JSON（模型截断）——原样带回去，别在这里二次解析
                rawArguments = step.args,
            )
        }
        return buildList {
            add(LlmMessage(role = LlmRole.ASSISTANT, toolCalls = calls))
            trail.forEachIndexed { i, step ->
                add(
                    LlmMessage(
                        role = LlmRole.TOOL,
                        // 悬空的那条在这里就补上：模型永远不该看到「有调用没结果」的序列
                        content = step.result ?: ToolPairing.UNKNOWN,
                        toolCallId = callId(messageIndex, i),
                        name = step.name,
                    ),
                )
            }
            add(LlmMessage(role = LlmRole.ASSISTANT, content = text))
        }
    }

    /** 合成 id：`call_<消息下标>_<序号>`（见 [expand] 关于决定性的说明）。 */
    fun callId(messageIndex: Int, stepIndex: Int): String = "call_${messageIndex}_$stepIndex"

    /** 轨迹里是否有**悬空**调用（有调用无结果）——修复的判据。 */
    fun hasDangling(trail: List<ToolStep>): Boolean = trail.any { it.result == null }
}

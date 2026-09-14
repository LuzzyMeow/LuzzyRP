package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luzzymeow.luzzyrp.chat.AgentLoop
import com.luzzymeow.luzzyrp.chat.CotParser
import com.luzzymeow.luzzyrp.chat.RecallEngine

/** 一次工具调用的实时槽位（参数由模型真实逐片发出，故内容会持续增长）。 */
class ToolSlot(name: String) {
    var name by mutableStateOf(name)
    var args by mutableStateOf("")
    var result by mutableStateOf<String?>(null)
}

/**
 * 一次真实生成的实时状态（P2）。
 *
 * **真流式纪律**：每个引擎事件到达即追加一次文本——不插值、不节流、不批量合成，
 * 界面看到的增长与 SSE 帧到达一一对应（1 个增量 = 1 次状态更新）。
 *
 * 节点时序（对应 DESIGN-compose §14.3③「自动展开 → 流式 → 完成后自动收起」）：
 * [activeNode] 指向「当前正在产出内容的节点」，该节点自动展开、内容流入；
 * 下标移走（或置 -1）即自动收起。工具参数与思考内容都走真实增量，故展开期间可见逐字增长。
 */
class LiveTurn {
    var generating by mutableStateOf(true)
    var recall by mutableStateOf<ThinkNode.MemoryRecall?>(null)
    val tools = mutableStateListOf<ToolSlot>()
    var reasoning by mutableStateOf("")
    var reasoningDone by mutableStateOf(false)
    var body by mutableStateOf("")
    var activeNode by mutableStateOf(-1)
    var error by mutableStateOf<String?>(null)
    var finishReason by mutableStateOf<String?>(null)

    /** 归一后的结束原因（B 批循环语义）；[Finished] 到达前为 null。 */
    var finish by mutableStateOf<AgentLoop.FinishReason?>(null)

    /** 当前跑到第几个 step（B1 的两级循环；第一个 step 前为 0）。 */
    var lastStep by mutableStateOf(0)

    /**
     * 本轮**被中断**（用户点停止 / 协程被取消）。
     *
     * 由调用方在取消路径上置 true：取消不是引擎事件（那时 Flow 已经死了），
     * 所以只能由「谁取消、谁记账」。落库后它就是「这半截话是用户打断的」的凭据（B4）。
     */
    var interrupted by mutableStateOf(false)

    /**
     * 本轮真实发生过的**工具调用轨迹**（B3，随消息落库）。
     *
     * 直接由 [tools] 派生：槽位是按**事件到达顺序**建的，也就是模型给出的顺序，
     * 所以这里不需要额外排序（`.sortedBy` 反而会在同一次调用的重试上出错）。
     * `result == null` 表示这次调用没拿到结果（被中断）→ 下一轮会被修复补上。
     */
    val toolTrail: List<com.luzzymeow.luzzyrp.chat.ToolStep>
        get() = tools.map { com.luzzymeow.luzzyrp.chat.ToolStep(it.name, it.args, it.result) }

    /** 本轮真实用量（流末尾由供应商给出）与墙钟耗时（脚注用）。 */
    var usage by mutableStateOf<com.luzzymeow.luzzyrp.chat.UsageInfo?>(null)

    /** 发起时刻（`apply` 首次被调用时打点，避免构造时机与网络时刻混淆）。 */
    private var startedAtMs = 0L
    var elapsedMs by mutableStateOf<Long?>(null)
        private set

    private var reasoningStartMs = 0L
    private var reasoningEndMs = 0L

    /**
     * 正文**原始**累积（含内联 CoT 标记；[body] 是剥掉 CoT 后的展示口径）。
     *
     * 为什么流式期就剥：此前 Content 直接追加原文，收尾 `AiResult` 才 `CotParser.mainOf`——
     * 结果是「首轮正文出现 CoT、输出完毕后消失」的跳变（用户实测缺陷）。
     * 现在流式与收尾**同源**（都过 [CotParser]），顺带治掉半截标签（`<thi`）的闪现
     * （残片被 [CotParser] 剔除，不再显示）。
     */
    private var rawBody = ""

    /** SSE 独立思考流的原始累积（`reasoning_content`）；与内联 CoT 分开记，展示层拼接。 */
    private var sseReasoning = ""

    /** 内联 CoT 是否出现过（决定 Content 期间思考节点的展开/收起时序）。 */
    private var inlineCotShown = false

    private fun composedReasoning(parsed: CotParser.Parsed = CotParser.parse(rawBody)): String =
        when {
            sseReasoning.isEmpty() -> parsed.cot
            parsed.cot.isEmpty() -> sseReasoning
            else -> "$sseReasoning\n\n${parsed.cot}"
        }

    /** 按真实发生顺序组装节点（召回 → 工具 → 思考）。 */
    val nodes: List<ThinkNode>
        get() = buildList {
            recall?.let { add(it) }
            tools.forEach { add(ThinkNode.Tool(it.name, it.args, it.result, done = it.result != null)) }
            if (reasoning.isNotEmpty()) {
                add(
                    ThinkNode.Brainstorm(
                        text = reasoning,
                        seconds = reasoningSeconds(),
                        streaming = !reasoningDone,
                    ),
                )
            }
        }

    private fun reasoningSeconds(): Double {
        if (reasoningStartMs == 0L) return 0.0
        val end = if (reasoningEndMs == 0L) System.currentTimeMillis() else reasoningEndMs
        return ((end - reasoningStartMs) / 1000.0).coerceAtLeast(0.1)
    }

    private fun indexOfLastTool(): Int = (if (recall != null) 1 else 0) + tools.size - 1

    /** 应用一个引擎事件（唯一的状态入口）。 */
    fun apply(event: AgentLoop.Event) {
        if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
        when (event) {
            is AgentLoop.Event.Recall -> {
                recall = ThinkNode.MemoryRecall(
                    shards = event.hits.map {
                        MemoryShard(
                            turn = "第 ${it.turn} 轮",
                            score = "相关度 ${RecallEngine.percent(it.score)}",
                            text = it.text,
                        )
                    },
                    range = event.range,
                )
                activeNode = 0
            }

            is AgentLoop.Event.ToolCallStarted -> {
                tools.add(ToolSlot(event.name))
                activeNode = indexOfLastTool()
            }

            is AgentLoop.Event.ToolCallArgs -> {
                tools.lastOrNull()?.let { it.args += event.chunk }
                activeNode = indexOfLastTool()
            }

            is AgentLoop.Event.ToolCallFinished -> {
                val slot = tools.lastOrNull { it.name == event.name && it.result == null }
                    ?: ToolSlot(event.name).also { tools.add(it) }
                slot.args = event.args
                slot.result = event.result
                activeNode = indexOfLastTool()
            }

            is AgentLoop.Event.Reasoning -> {
                if (reasoningStartMs == 0L) reasoningStartMs = System.currentTimeMillis()
                reasoningEndMs = System.currentTimeMillis()
                sseReasoning += event.chunk
                reasoning = composedReasoning()
                activeNode = nodes.lastIndex
            }

            is AgentLoop.Event.Content -> {
                rawBody += event.chunk
                val parsed = CotParser.parse(rawBody)
                // 展示与收尾同源（AiResult 构造时也是 CotParser.mainOf）
                body = parsed.main
                if (parsed.cot.isNotEmpty()) {
                    // 内联 CoT：并入思考节点（此前收尾后「既不在节点也不在正文」地消失）
                    reasoning = composedReasoning(parsed)
                    reasoningEndMs = System.currentTimeMillis()
                    if (!inlineCotShown) {
                        inlineCotShown = true
                        if (reasoningStartMs == 0L) reasoningStartMs = System.currentTimeMillis()
                        reasoningDone = false
                    }
                    if (parsed.isFinished) {
                        // CoT 闭合：节点收起，正文接管
                        reasoningDone = true
                        activeNode = -1
                    } else {
                        activeNode = nodes.lastIndex
                    }
                } else {
                    // 无内联 CoT：正文开始 → 既有思考流收起
                    if (!reasoningDone && sseReasoning.isNotEmpty()) reasoningDone = true
                    activeNode = -1
                }
            }

            is AgentLoop.Event.Usage -> {
                usage = event.info
            }

            is AgentLoop.Event.StepStarted -> {
                // 一个 step 开始 = 又发了一次真实请求。界面据此把「生成中」的起始时刻打点，
                // 也让「第几步」这类诊断信息有真实来源（不再靠猜）。
                if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
                lastStep = event.step
            }

            is AgentLoop.Event.Finished -> {
                elapsedMs = System.currentTimeMillis() - startedAtMs
                reasoningDone = true
                generating = false
                activeNode = -1
                // 展示与「被截断」判据都用**供应商原始值**（`stop` / `length` …）；
                // 归一后的循环语义放在 [finish] 里（`completed` / `max_tokens` / `step_limit` …）。
                finishReason = event.wire ?: event.reason.id
                finish = event.reason
            }

            is AgentLoop.Event.Failed -> {
                error = event.message
                generating = false
                activeNode = -1
            }

            // 压缩（B5）改的是**发给模型的序列**，不是对话本身——简报跟尾部快照一样不进列表，
            // 所以界面上没有要改的东西。落库记账由调用方做（`ChatPage` 用 `dropped` 写水位线）。
            is AgentLoop.Event.Compacted -> Unit

            // 压缩失败：引擎已把它变成事件（不静默）；界面呈现留给设计流程（AGENTS §13）。
            is AgentLoop.Event.CompactionFailed -> Unit
        }
    }
}

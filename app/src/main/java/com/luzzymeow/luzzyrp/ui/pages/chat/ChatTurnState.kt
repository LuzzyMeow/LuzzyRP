package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.luzzymeow.luzzyrp.chat.ChatEngine
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

    /** 本轮真实用量（流末尾由供应商给出）与墙钟耗时（脚注用）。 */
    var usage by mutableStateOf<com.luzzymeow.luzzyrp.chat.UsageInfo?>(null)

    /** 发起时刻（`apply` 首次被调用时打点，避免构造时机与网络时刻混淆）。 */
    private var startedAtMs = 0L
    var elapsedMs by mutableStateOf<Long?>(null)
        private set

    private var reasoningStartMs = 0L
    private var reasoningEndMs = 0L

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
    fun apply(event: ChatEngine.Event) {
        if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
        when (event) {
            is ChatEngine.Event.Recall -> {
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

            is ChatEngine.Event.ToolCallStarted -> {
                tools.add(ToolSlot(event.name))
                activeNode = indexOfLastTool()
            }

            is ChatEngine.Event.ToolCallArgs -> {
                tools.lastOrNull()?.let { it.args += event.chunk }
                activeNode = indexOfLastTool()
            }

            is ChatEngine.Event.ToolCallFinished -> {
                val slot = tools.lastOrNull { it.name == event.name && it.result == null }
                    ?: ToolSlot(event.name).also { tools.add(it) }
                slot.args = event.args
                slot.result = event.result
                activeNode = indexOfLastTool()
            }

            is ChatEngine.Event.Reasoning -> {
                if (reasoningStartMs == 0L) reasoningStartMs = System.currentTimeMillis()
                reasoningEndMs = System.currentTimeMillis()
                reasoning += event.chunk
                activeNode = nodes.lastIndex
            }

            is ChatEngine.Event.Content -> {
                // 思考内容流式结束 → 思考节点自动收起（下标移走），正文接管
                if (!reasoningDone && reasoning.isNotEmpty()) reasoningDone = true
                activeNode = -1
                body += event.chunk
            }

            is ChatEngine.Event.Usage -> {
                usage = event.info
            }

            is ChatEngine.Event.Finished -> {
                elapsedMs = System.currentTimeMillis() - startedAtMs
                reasoningDone = true
                generating = false
                activeNode = -1
                finishReason = event.finishReason
            }

            is ChatEngine.Event.Failed -> {
                error = event.message
                generating = false
                activeNode = -1
            }
        }
    }
}

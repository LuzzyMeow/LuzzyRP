package com.luzzymeow.luzzyrp.core.ai.util

import com.luzzymeow.luzzyrp.core.model.UIMessagePart

/**
 * 工具边界分组：把一条 assistant 消息的 parts 按「内容块 / 工具调用块」切分。
 *
 * 协议要求（OpenAI/Anthropic 均如此）：assistant 消息的 tool_calls 必须与
 * tool 结果消息成对相邻。存储层我们把工具结果原地回填在 Tool part 内
 * （KV 不变性），发送给协议时按此分组还原出 tool_calls + role:"tool" 序列。
 */
sealed interface PartGroup {
    /** 连续的内容 parts（文本/推理/图片）。 */
    data class Content(val parts: List<UIMessagePart>) : PartGroup

    /** 连续的工具调用 parts。 */
    data class Tools(val tools: List<UIMessagePart.Tool>) : PartGroup
}

fun groupPartsByToolBoundary(parts: List<UIMessagePart>): List<PartGroup> {
    val groups = mutableListOf<PartGroup>()
    var contentBuffer = mutableListOf<UIMessagePart>()
    var toolBuffer = mutableListOf<UIMessagePart.Tool>()

    fun flushContent() {
        if (contentBuffer.isNotEmpty()) {
            groups.add(PartGroup.Content(contentBuffer.toList()))
            contentBuffer = mutableListOf()
        }
    }

    fun flushTools() {
        if (toolBuffer.isNotEmpty()) {
            groups.add(PartGroup.Tools(toolBuffer.toList()))
            toolBuffer = mutableListOf()
        }
    }

    for (part in parts) {
        if (part is UIMessagePart.Tool) {
            flushContent()
            toolBuffer.add(part)
        } else {
            flushTools()
            if (part is UIMessagePart.Reasoning || part is UIMessagePart.Text || part is UIMessagePart.Image) {
                contentBuffer.add(part)
            }
        }
    }
    flushContent()
    flushTools()
    return groups
}

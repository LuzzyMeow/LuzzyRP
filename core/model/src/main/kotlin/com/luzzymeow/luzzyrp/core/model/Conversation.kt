package com.luzzymeow.luzzyrp.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * 会话与消息节点树。
 *
 * 分支模型（参考 rikkahub）：一次「重新生成」在父节点下产生新的兄弟节点，
 * selectIndex 记录当前选中的分支，历史可回溯（后端永久保留全部消息）。
 */

/** 上下文策略：发送给模型的对话历史裁剪方式。 */
@Serializable
data class ContextConfig(
    /**
     * 保留的历史轮数：0 = 不限制（全量）；1-200 = 仅保留最近 N 轮。
     * 超出窗口的历史依靠「三级摘要 + 长期记忆 + 世界书召回」补偿。
     */
    val historyRounds: Int = 0,
    /** 每轮最大字符数上限（超长消息截断保护，0 = 不截断）。 */
    val maxRoundChars: Int = 0,
)

/** 三级摘要计数器（调度依据：A 每轮 / B 每 10 轮 / C 每 50 轮）。 */
@Serializable
data class SummaryCounters(
    val totalRounds: Int = 0,
    val aCount: Int = 0,
    val bCount: Int = 0,
    val cCount: Int = 0,
    val lastBAtRound: Int = -1,
    val lastCAtRound: Int = -1,
)

/** 消息节点：树中的一个分叉点，持有该分支的消息序列。 */
@Serializable
data class MessageNode(
    val id: String,
    val parentId: String? = null,
    /** 当前选中子分支在 children 中的下标（用于树导航）。 */
    val selectIndex: Int = 0,
    val messages: List<UIMessage> = emptyList(),
    val createdAt: Instant? = null,
)

/** 会话聚合根（UI 单一真源 MutableStateFlow<Conversation> 的载体）。 */
@Serializable
data class Conversation(
    val id: String,
    val title: String = "新对话",
    val nodes: List<MessageNode> = emptyList(),
    /** 当前叶节点 id（消息列表 = 从根到叶路径上各节点的 messages 串联）。 */
    val currentNodeId: String? = null,
    /** 绑定的角色卡（null = 普通对话）。 */
    val cardId: String? = null,
    val worldbookIds: List<String> = emptyList(),
    val regexIds: List<String> = emptyList(),
    val uiTemplateId: String? = null,
    /** 是否启用 ACE 长期记忆。 */
    val enableMemory: Boolean = true,
    val contextConfig: ContextConfig = ContextConfig(),
    val summaryCounters: SummaryCounters = SummaryCounters(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    /**
     * 当前可见消息序列：根→叶路径上全部节点的 messages 串联。
     * 生成管线与 UI 渲染都以该序列为准。
     */
    fun currentMessages(): List<UIMessage> {
        if (nodes.isEmpty()) return emptyList()
        val byId = nodes.associateBy { it.id }
        val chain = mutableListOf<MessageNode>()
        var cursor: MessageNode? = currentNodeId?.let { byId[it] } ?: nodes.lastOrNull()
        while (cursor != null) {
            chain.add(cursor)
            cursor = cursor.parentId?.let { byId[it] }
        }
        chain.reverse()
        return chain.flatMap { it.messages }
    }
}

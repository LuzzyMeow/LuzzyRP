package com.luzzymeow.luzzyrp.chat

/**
 * 运行时上下文**尾部快照**（A3）——DSH `RuntimeContextProjection` 的 Kotlin 等价实现。
 *
 * （`packages/core/agent-loop/src/runtime-context.ts:147-158`；文案模板
 * `packages/core/system-prompt/src/index.ts:297-301`）
 *
 * ## 它解决什么问题
 *
 * 会随轮次变化的内容（世界书按深度插入的三类、记忆召回、将来的时间/状态）如果**插进历史中间**
 * 或**改写已有消息**，那么每轮的请求都会从那个位置起与上一轮不同 → 服务端前缀缓存**从那里断裂**。
 *
 * DSH 的办法：这类内容一律**追加到尾部**，并且
 *
 * 1. **内容没变就一条都不发**（[project] 返回 null）——这是收益最大的一条：
 *    多数轮次里世界书命中与召回结果其实没变，于是请求是上一轮的严格延伸；
 * 2. 变了才发一条新快照，开头**声明作废旧快照**——旧快照永远留在历史里，前缀永不被改写。
 *
 * ## 为什么「不变就不发」是安全的
 *
 * 因为快照内容**自带效力声明**（[HEADER]）：模型看到新快照就会以后者为准；
 * 而没发新快照的那一轮，上一轮的快照仍在上下文里且内容与当前状态**逐字相同**——
 * 所以「不发」等于「重申」，语义等价。这正是 DSH 那句
 * `if (this.retained?.text === snapshot) return` 的底气。
 *
 * ## 与 system 的关系
 *
 * system 里只放**不变**的内容（[PromptSections]）；快照放在**历史之后、本轮用户输入之前**。
 * 位置选择理由：DSH 把它放在 claimed 用户消息之后（`agent.ts:245-254`），
 * 但我们各协议都要求「最后一条是 user」才自然成轮，故置其前，语义相同而不打断该惯例。
 */
object RuntimeSnapshots {

    /** 一节快照内容。`order` 小的在前；同 order 按 `name` code-unit 序（决定化）。 */
    data class Snapshot(val name: String, val order: Int, val text: String)

    // ---- order 常量 ----
    const val ORDER_WORLD_DYNAMIC = 100
    const val ORDER_MEMORY_RECALL = 200

    /** 快照头部声明（DSH 原文，逐字保留其语义）。 */
    const val HEADER = "Current runtime context. This snapshot supersedes earlier runtime-context snapshots."

    /** 内容清空时的声明（DSH `CLEARED` 同义）——不能什么都不发，否则旧快照会被误当成仍然有效。 */
    const val CLEARED = "Current runtime context: none. Earlier runtime-context snapshots no longer apply."

    /**
     * 算出**本轮该不该发快照**、发什么文本。
     *
     * @param current 当前内容（空 = 没有动态上下文）
     * @param retained 上一次**已发出**的快照文本（随会话持久化；从未发过则 null）
     * @return null = **不要发**（内容与上次逐字相同，或本来就没有且从未发过）；
     *         非 null = 要发的快照文本
     */
    fun project(current: List<Snapshot>, retained: String?): String? {
        // 从来没有动态内容、也没发过 → 不发（连「清空声明」都不必）
        if (current.isEmpty() && retained == null) return null
        val body = current
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.order }, { it.name }))
            .joinToString("\n\n") { it.text }
        val text = if (body.isBlank()) CLEARED else "$HEADER\n\n$body"
        // ★ 核心：逐字相同就不发。这一条决定了「多数轮次前缀完全不变」。
        if (text == retained) return null
        return text
    }

    /**
     * 快照文本 → 是否属于「清空声明」（用于判断 retained 该不该被当成有效内容）。
     * 仅便于测试与观测，不改行为。
     */
    fun isCleared(text: String): Boolean = text == CLEARED
}

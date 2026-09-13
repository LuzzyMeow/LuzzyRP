package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.ChatRequest
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.RecallEngine
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole

/**
 * **请求 = 会话状态的纯函数**（A6）。
 *
 * ## 它守的是什么
 *
 * 服务端前缀缓存只在「本轮请求是上一轮请求的**严格延伸**」时命中。要拿到这个性质，
 * 必须同时成立两件事，而它们此前分别散在 `ChatPage` 的三处重复代码里、无人守：
 *
 * 1. **历史永不改写**——历史消息逐字搬进请求，不合并、不重排、不重写；
 * 2. **落盘顺序 = 请求顺序**——本轮若发出尾部快照，它在请求里位于「用户输入之前」，
 *    那么它在**存储里也必须**位于用户消息之前。挂在列表末尾 = 下一轮它出现在用户消息
 *    之后 → 存储顺序与请求顺序不一致 → 前缀照样从那里断开（会话 73 回退的第一个真缺陷）。
 *
 * 所以这里把「这次请求长什么样」与「这次要按什么顺序落盘什么」放在**同一个返回值**里：
 * 调用方没有机会只做对一半。
 *
 * ## `retained` 从**日志**推出来，不从 kv 读（对 PLAN §3-A3 的修正）
 *
 * A3 原方案把「上次已发出的快照文本」按会话作用域记在 `kv` 里。重做时发现那样是**错的**：
 *
 * - 「重新生成第 3 楼」时，前缀只到第 3 楼，而 kv 里记的是**最新一轮**的快照 →
 *   去重判据立刻失真（内容明明没变却判定要重发，或反过来把该发的吞掉）；
 * - 快照落盘失败时（演示态不落盘、IO 出错）kv 仍然记着「已发过」→ 上下文**静默丢失**。
 *
 * 而**日志本身**就是「模型看到过什么」的真源：最后一条快照消息 = 上一个已发出的快照。
 * 于是重启天然安全（消息在库里）、重放历史中段天然正确、落盘失败还会自动重发（自愈）。
 * 这条修正的代价是 `PromptInputSource` 里那三个 kv 读写方法被删除（见其类注释）。
 *
 * ## 为什么落在 `ui/pages/chat` 而不是 `chat/`
 *
 * 计划 §3-A6 写的是 `chat/RequestBuilder.kt`，实际落在这里（**如实登记的偏离**）：
 * 本类的输入是 [ChatMessage]（会话状态），而 `ChatMessage` 定义在 `ui.pages.chat`。
 * 放进 `chat` 会让纯逻辑包反向依赖界面包。本文件仍是**纯 Kotlin、零 Compose / 零 Android
 * 依赖**，`RequestBuilderTest` 是 JVM 单测——「可测」这个目的没有打折，只是包名与计划不同。
 */
object RequestBuilder {

    /** 一次请求的完整计划：**请求长什么样** + **这次要按什么顺序落盘什么**。 */
    data class Plan(
        /** 请求里带的历史（不含本轮输入；含既往快照，按存储原序）。 */
        val history: List<LlmMessage>,
        /** 本轮要发出的尾部快照文本（null = 不发，因为内容与日志里最后一条逐字相同）。 */
        val snapshotText: String?,
        /** 可直接交给传输的完整消息序列。 */
        val messages: List<LlmMessage>,
        /** 本轮记忆召回命中（与写进快照的是同一份）。 */
        val recallHits: List<RecallEngine.Hit>,
        /**
         * 本轮**必须按此顺序落盘**的条目：快照（若有）在前、用户消息在后。
         *
         * 顺序即语义：反过来就会让「存储顺序 ≠ 请求顺序」，下一轮前缀从快照处断开。
         * 重放（[freshTurn] = false）时为空——那一轮的一切都已经在日志里了。
         */
        val appends: List<ChatMessage>,
    ) {
        /** 引擎入参。 */
        val request: ChatRequest get() = ChatRequest(messages = messages, recallHits = recallHits)
    }

    /**
     * 算出本轮请求。
     *
     * @param state 当前分支的**全部**会话消息（含既往快照；按存储原序）
     * @param userText 本轮用户输入
     * @param input 本轮取数结果（角色 / 用户 / 预设 / 世界书；**不含** retained——它从 [state] 推）
     * @param freshTurn `true` = 新的一轮（可以发新快照并落盘）；
     *        `false` = 重放既有轮次（「重新生成」「编辑后重跑」）——
     *        只能用日志里已有的快照，一条新的都不发（新快照没有位置可落，见类注释）
     */
    fun plan(
        state: List<ChatMessage>,
        userText: String,
        input: PromptAssembler.Input,
        freshTurn: Boolean = true,
    ): Plan {
        val history = historyOf(state)
        val hits = RecallEngine.search(turnsOf(history), userText)
        val effective = input.copy(
            history = history,
            userText = userText,
            recallBlock = RecallEngine.renderForPrompt(hits),
            // ★ 去重判据 = 日志里最后一条快照（不是 kv；理由见类注释）
            retainedSnapshot = lastSnapshotText(state),
            emitSnapshot = freshTurn,
        )
        val assembled = PromptAssembler.assembleDetailed(effective)
        return Plan(
            history = history,
            snapshotText = assembled.snapshotText,
            messages = assembled.messages,
            recallHits = hits,
            appends = if (!freshTurn) {
                emptyList()
            } else {
                buildList {
                    assembled.snapshotText?.let { add(ChatMessage.Snapshot(it)) }
                    if (userText.isNotBlank()) add(ChatMessage.User(userText))
                }
            },
        )
    }

    /** 会话日志里**最后一条**已发出的快照文本（没有则为 null）。 */
    fun lastSnapshotText(state: List<ChatMessage>): String? =
        state.filterIsInstance<ChatMessage.Snapshot>().lastOrNull()?.text

    /**
     * 会话消息 → 请求消息。
     *
     * 快照按 **user 消息**进请求（三协议都只认 user/assistant/system/tool），
     * 并带上 [LlmMessage.runtimeSnapshot] 标记——召回轮号与观测层据此把它与真实发言区分开。
     *
     * 全部标 `fromHistory = true`：历史段**不参与相邻同 role 合并**
     * （合并会让一条消息的内容取决于它的邻居，下一轮邻居一变就改写了已进历史的字节）。
     */
    fun historyOf(state: List<ChatMessage>): List<LlmMessage> = state.map { message ->
        val mapped = when (message) {
            is ChatMessage.User -> LlmMessage(role = LlmRole.USER, content = message.text)
            is ChatMessage.Ai -> LlmMessage(role = LlmRole.ASSISTANT, content = message.raw)
            is ChatMessage.Snapshot -> LlmMessage(
                role = LlmRole.USER,
                content = message.text,
                runtimeSnapshot = true,
            )
        }
        if (mapped.fromHistory) mapped else mapped.copy(fromHistory = true)
    }

    /**
     * 召回用的「轮次」序列（`轮号 to 正文`）。
     *
     * **快照不算一轮**：它是运行时上下文，不是用户发言。若把它算进去，
     * 后面每一轮的「第 N 轮」标注都会虚高（用户看到的是错的轮号）。
     */
    fun turnsOf(history: List<LlmMessage>): List<Pair<Int, String>> {
        var turnNo = 0
        return history
            .filter { !it.runtimeSnapshot }
            .filter { it.role == LlmRole.USER || it.role == LlmRole.ASSISTANT }
            .map { message ->
                if (message.role == LlmRole.USER) turnNo++
                turnNo to message.content
            }
    }
}

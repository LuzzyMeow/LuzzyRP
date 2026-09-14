package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.ChatRequest
import com.luzzymeow.luzzyrp.chat.Compaction
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.RecallEngine
import com.luzzymeow.luzzyrp.chat.RegexScript
import com.luzzymeow.luzzyrp.chat.RegexScripts
import com.luzzymeow.luzzyrp.chat.ToolTrail
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
     * @param regexScripts 生效的正则脚本（全局 + 当前角色）。**只有勾了「仅提示词」的条目
     *        会改请求字节**；默认什么都不勾 = 仅用户可见，提示词侧一律跳过（见 [RegexScripts]）
     * @param promptUserName `{{user}}` 在提示词侧的替换值。**传档案里的原名，不要传显示期的
     *        「你」兜底**：这个名字会进 system/预设/角色块，编一个第二人称代词去顶替人名只会
     *        让模型把人称搞混；没配过名字就传空 → 占位符原样保留
     * @param styleFilterEnabled 文风过滤开关（默认开，与上游一致）。它**同时**作用在
     *        提示词侧（此处）与显示期（`MessageBody`）——上游把这一调用放在 `processRegex`
     *        出口，于是两条路径都经过它（`app.js:4090`）
     */
    fun plan(
        state: List<ChatMessage>,
        userText: String,
        input: PromptAssembler.Input,
        freshTurn: Boolean = true,
        regexScripts: List<RegexScript> = emptyList(),
        promptUserName: String = "",
        styleFilterEnabled: Boolean = true,
        userAttachments: List<ChatAttachment> = emptyList(),
    ): Plan {
        val history = historyOf(state)
        val hits = RecallEngine.search(turnsOf(history), userText)
        val effective = input.copy(
            history = history,
            userText = userText,
            userAttachments = userAttachments,
            recallBlock = RecallEngine.renderForPrompt(hits),
            // ★ 去重判据 = 日志里最后一条快照（不是 kv；理由见类注释）
            retainedSnapshot = lastSnapshotText(state),
            emitSnapshot = freshTurn,
        )
        val assembled = PromptAssembler.assembleDetailed(effective)
        // 提示词侧正则（上游 `app.js:6511-6518`）：对**整条已装配好的消息序列**套用，
        // depth = 条数 - 1 - 下标。位置在最后——它改变的是发给模型的字节，不是落盘的字节。
        // 同一处还有文风过滤（②，上游 `processRegex` 出口 `app.js:4090`，只对 assistant）。
        val messages = RegexScripts.applyToPromptMessages(
            messages = assembled.messages,
            scripts = regexScripts,
            userName = promptUserName,
            styleFilterEnabled = styleFilterEnabled,
        )
        return Plan(
            history = history,
            snapshotText = assembled.snapshotText,
            messages = messages,
            recallHits = hits,
            appends = if (!freshTurn) {
                emptyList()
            } else {
                buildList {
                    assembled.snapshotText?.let { add(ChatMessage.Snapshot(it)) }
                    if (userText.isNotBlank() || userAttachments.isNotEmpty()) {
                        add(ChatMessage.User(userText, attachments = userAttachments))
                    }
                }
            },
        )
    }

    /**
     * 会话日志里**最后一条**已发出的快照文本（没有则为 null）。
     *
     * 只看**未被压缩取代**的那一段：被取代的快照已经不在请求里了，若还拿它当「已发过」，
     * 模型就会在压缩之后**永久丢失**运行时上下文（不报错、不崩溃，最难查的那一类）。
     */
    fun lastSnapshotText(state: List<ChatMessage>): String? {
        val from = compactionWatermark(state).coerceAtLeast(0)
        if (from >= state.size) return null
        return state.subList(from, state.size).filterIsInstance<ChatMessage.Snapshot>().lastOrNull()?.text
    }

    /**
     * 「被压缩取代」的水位线：最后一条 [ChatMessage.Compacted] 的下标（没有则 -1）。
     *
     * 水位线**本身留在请求里**（它就是那份简报），**它之前**的历史才被取代——
     * 所以 [historyOf] 的起点是「水位线下标」而不是「水位线下标 + 1」。
     *
     * ## 为什么水位线是一条**消息**而不是一个下标记录
     *
     * 压缩（B5）把最老的一段历史换成了简报。模型此后**看不到**那一段——按 DSH 的第一原则
     * 「Model-visible ⟺ durably referenced」，这件事必须落在日志里，否则下一轮请求又会把
     * 整段历史带上：于是每轮都要重新摘要（多花钱）、且每轮的前缀都断在开头
     * （把批 A 挣来的前缀缓存全部还回去）。
     *
     * 用**行位置**表达语义（「这条之前的历史都被取代」）而不是记「被取代的下标集合」，
     * 是因为位置对编辑 / 删除 / 分支天然免疫：删掉中间一条、或删掉水位线本身，
     * 语义都不会错（删掉水位线 = 恢复整段历史，自愈）。
     */
    fun compactionWatermark(state: List<ChatMessage>): Int =
        state.indexOfLast { it is ChatMessage.Compacted }

    /**
     * 会话消息 → 请求消息。
     *
     * 快照按 **user 消息**进请求（三协议都只认 user/assistant/system/tool），
     * 并带上 [LlmMessage.runtimeSnapshot] 标记——召回轮号与观测层据此把它与真实发言区分开。
     *
     * **带工具轨迹的 AI 消息会展开成三段落**（B3）：
     * `assistant(tool_calls)` → `tool(结果)` → `assistant(正文)`。
     * 展开是**纯函数且决定性**的（id 由「消息下标 + 序号」合成），所以同一条历史
     * 每次都展开成同样的字节——批 A 的前缀纯追加性质不会被批 B 破坏
     * （`AgentLoopTest.多 step 的工具续跑每一轮都是上一轮的逐字节延伸` 钉住这一条）。
     *
     * 全部标 `fromHistory = true`：历史段**不参与相邻同 role 合并**
     * （合并会让一条消息的内容取决于它的邻居，下一轮邻居一变就改写了已进历史的字节）。
     *
     * 水位线之前的历史**不进请求**（被简报取代，见 [compactionWatermark]）；
     * 每条消息都带上 [LlmMessage.sourceIndex]，压缩后调用方才能把水位线落回存储。
     */
    fun historyOf(state: List<ChatMessage>): List<LlmMessage> {
        // 起点是**水位线本身**：它之前的历史被取代，它自己就是那份简报（见 compactionWatermark）
        val from = compactionWatermark(state)
        val out = ArrayList<LlmMessage>(state.size - from.coerceAtLeast(0))
        state.forEachIndexed { index, message ->
            if (index < from) return@forEachIndexed
            val emitted = when (message) {
                is ChatMessage.User -> listOf(
                    // 带图片的历史消息以 parts 进请求（模型必须持续看得见它看过的图，
                    // 这是「Model-visible ⟺ durably referenced」的另一面）；纯文本走旧路径，
                    // 请求字节与引入 C4 之前逐字节一致（前缀缓存不受影响）。
                    LlmMessage(
                        role = LlmRole.USER,
                        content = message.text,
                        rawContent = userContentParts(message.text, message.attachments),
                    ),
                )
                is ChatMessage.Ai -> ToolTrail.expand(index, message.raw, message.current.toolTrail)
                is ChatMessage.Snapshot -> listOf(
                    LlmMessage(role = LlmRole.USER, content = message.text, runtimeSnapshot = true),
                )

                // 简报：正文形态只有 [Compaction.summaryMessage] 一处定义（抬头 + 标记），
                // 这里直接调它，免得压缩路径与「重启后重读」路径拼出两种字节。
                is ChatMessage.Compacted -> listOf(Compaction.summaryMessage(message.text))
            }
            // 同一存储行展开出的多条消息共享同一个存储下标：压缩后写水位线要用它
            // （`ToolTrail.expand` 会把一条 AI 消息展开成 调用 + 结果 + 正文 三条）。
            emitted.forEach { out += it.copy(fromHistory = true, sourceIndex = index) }
        }
        return out
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

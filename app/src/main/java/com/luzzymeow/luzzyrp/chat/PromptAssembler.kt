package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole

/**
 * **请求组装**（P5-A）：把「角色 / 用户 / 预设 / 世界书 / 召回 / 历史」拼成 messages 序列。
 *
 * 纯 Kotlin、无 Android 依赖、**无 IO** → 整条上游语义可 JVM 单测（喂真实夹具即可断言），
 * 不必起模拟器、不必联网。这是本批可验证的前提：组装一旦留在 `AgentLoop.run()` 里，
 * 就只能靠「发一次请求看结果」来验，而那种验法既慢又不可靠。
 *
 * ## 消息序列（照抄上游 `app.js:4613-4918` 的最终顺序）
 *
 * ```
 * 1. system（**单条**，内容按以下顺序 \n\n 连接）
 *    ① 破限预设 → ② 世界书 system_top → ③ global_note
 *    → ④ 其余 system 预设（\n\n---\n\n 连接）→ ⑤ 用户信息块
 *    → ⑥ 世界书 before_char → ⑦ 角色块 [Character] → ⑧ after_char
 *    → ⑨ 工具提示 → ⑩ 记忆召回块
 * 2. User/AI 类预设（按数组序，各为独立消息）
 * 3. 角色前置 user 消息（= ⑥⑦⑧ 拼成的一条，与上游一致）
 * 4. first_mes（若历史首条不是它）
 * 5. 历史消息（全量，上游也不截断）
 * 6. 本轮 user 输入
 * 7. 世界书 at_depth / user_top / assistant_top（就地插入）
 * 8. 相邻同 role 合并（仅 user/assistant）
 * ```
 *
 * ## 两处**如实登记的偏离**（不是疏漏，是与上游不同的决定）
 *
 * 1. **角色块用 `description` + `personality`**：上游模板只取 `name` + `personality`
 *    （`built-in-content.js:66`），`description` 只用于搜索过滤（`app.js:2861`）。
 *    但迁移来的真实卡把**人设正文放在 `description`**（夹具里书店女孩、谢昭、夏梧的
 *    `personality` 都是空串）——照搬上游会让这些角色的定义**静默全丢**。
 *    故：两者都非空时按 `description\n\npersonality` 连接。
 * 2. **召回块放 system 末尾**：上游把它作为独立 user 消息插 `at_depth`（默认深度 1）；
 *    我们沿用现有行为（`AgentLoop` 原本就拼在 system 尾部），因为改动会牵动思考节点标注
 *    与 `DESIGN-compose §15.2` 的既有约定。要换成上游口径是独立的一小步，登记在案。
 */
object PromptAssembler {

    /** 角色块的数据（从角色卡 payload 取，字段名与上游一致）。 */
    data class CharacterView(
        val name: String,
        /** 人设正文（上游只在搜索里用，我们进 prompt——见类注释的偏离 1）。 */
        val description: String = "",
        val personality: String = "",
        val mesExample: String = "",
        val firstMes: String = "",
    ) {
        /** 角色块是否值得进请求：只有名字也能用，但空名字没有任何意义。 */
        val isUsable: Boolean get() = name.isNotBlank()
    }

    /** 用户信息（`kv["user"]` / 人设档案）。 */
    data class UserView(
        val name: String = "",
        val description: String = "",
        val preferences: String = "",
    ) {
        val isEmpty: Boolean get() = name.isBlank() && description.isBlank() && preferences.isBlank()
    }

    /** 组装的全部输入。默认值让「什么都没配」也能安全跑（不注入任何块）。 */
    data class Input(
        val character: CharacterView? = null,
        val user: UserView = UserView(),
        val presets: List<PresetEntry> = emptyList(),
        /** 本次**激活**的世界书条目（已过扫描），不是全量。 */
        val worldEntries: List<WorldEntry> = emptyList(),
        /** 记忆召回块（会随轮次变 → 进尾部快照，不进 system）。 */
        val recallBlock: String = "",
        val toolHint: String = "",
        /** 历史（不含本轮输入），按时间升序；`raw` 会被保留（内联 CoT 与现在一致）。 */
        val history: List<LlmMessage> = emptyList(),
        val userText: String = "",
        /**
         * 本轮用户输入**随带的图片附件**（C4）。
         *
         * 进请求的形态是 OpenAI parts（`userContentParts`）：正文在前、图片在后，
         * OpenAI 直通、Anthropic / Gemini 的 wire 翻译。**路径形态在这里还是路径**
         * ——解析成 data URL 在发请求前的最后一步（`resolveImageParts`），payload 里永远不存 base64。
         */
        val userAttachments: List<com.luzzymeow.luzzyrp.ui.pages.chat.ChatAttachment> = emptyList(),
        /**
         * 上一次**已发出**的尾部快照文本（随会话持久化）。
         *
         * `null` = 从未发过。传错（例如每次都传 null）不会出错，只是会**每轮都重发快照** →
         * 前缀从快照处断裂。所以接线时务必读会话里记的那一行为准。
         *
         * **接线的实际取法**（A6）：不读 kv，读**会话日志里最后一条快照消息**
         * （`RequestBuilder.lastSnapshotText`）——日志才是「模型看到过什么」的真源，
         * 重启后天然还在，且对「重放历史中段」也天然正确。
         */
        val retainedSnapshot: String? = null,
        /**
         * 是否允许本轮**发出新的尾部快照**（默认允许）。
         *
         * `false` = **重放模式**：这次请求只是把既有历史重发一遍（「重新生成」「编辑后重跑」），
         * 因此只能用日志里已经有的快照，一条新的都不许发。
         *
         * 为什么必须有这个开关：重放时新快照在日志里**没有位置可落**（它该插在历史中段，
         * 而存储是按 sortIndex 追加的）。若请求里发了、日志里没有，下一轮前缀就从那里断开——
         * 这正是「存储顺序必须等于请求顺序」那条不变式的另一面。
         */
        val emitSnapshot: Boolean = true,
    )

    /** 组装结果：消息序列 + **本轮实际发出的快照文本**（调用方据此更新 retained）。 */
    data class Result(
        val messages: List<LlmMessage>,
        val snapshotText: String?,
    )

    /**
     * 组装。返回**可直接发给传输层**的 messages。
     *
     * 兼容入口（只关心 messages 的调用方用它）。
     */
    fun assemble(input: Input): List<LlmMessage> = assembleDetailed(input).messages

    /**
     * 组装（完整结果）。
     *
     * 消息序列（计划 §3-A3 修订版）：
     * ```
     * 1. system（单条，内容 = PromptSections 渲染的稳定块）
     * 2. User/AI 类预设（按数组序，各为独立消息）
     * 3. 角色前置 user 消息（before_char + 角色块 + after_char）
     * 4. first_mes（若历史首条不是它）
     * 5. 历史消息（**原样搬运，永不改写**）
     * 6. 尾部快照（**内容没变就不发**；位置在历史之后、本轮输入之前）
     * 7. 本轮 user 输入
     * 8. 相邻同 role 合并（仅 user/assistant）
     * ```
     */
    fun assembleDetailed(input: Input): Result {
        val world = WorldBookActivator.groupByPosition(input.worldEntries)
        val enabledPresets = input.presets.filter { it.enabled && it.content.isNotBlank() }
        val messagePresets = enabledPresets.filter { it.role != PresetRole.System }

        // ── 1. system：只放稳定块（会变的内容一律不进 system） ──
        //
        // 即使一个块都没有，也**始终发一条 system**：空库/未配置时若整个 system 消失，
        // 部分供应商会因为「没有 system / 首条不是 system」而拒绝请求，而且工具提示、
        // 将来的全局规则都失去了落点。内容为空的 system 是无害的。
        val systemText = PromptSections.render(
            PromptSections.stableSections(
                presets = input.presets,
                worldEntries = input.worldEntries,
                character = input.character,
                user = input.user,
                toolHint = input.toolHint,
            ),
        )

        // ── 2. User/AI 类预设（各为独立消息；上游紧跟首条 system） ──
        val presetMessages = messagePresets.map { preset ->
            LlmMessage(
                role = if (preset.role == PresetRole.Assistant) LlmRole.ASSISTANT else LlmRole.USER,
                content = preset.content,
            )
        }

        // ── 3. 角色前置 user 消息 ──
        val prelude = buildList {
            renderIfNotEmpty(world[WorldPosition.BeforeChar])?.let(::add)
            buildCharacterBlock(input.character)?.let(::add)
            renderIfNotEmpty(world[WorldPosition.AfterChar])?.let(::add)
        }.joinToString("\n\n")
        val preludeMessage = prelude.takeIf { it.isNotBlank() }
            ?.let { LlmMessage(role = LlmRole.USER, content = it) }

        // ── 4. first_mes（历史首条不是它时才补） ──
        val firstMes = input.character?.firstMes.orEmpty().trim()
        val firstMesMessage = if (firstMes.isNotEmpty() && !historyStartsWithFirstMes(input.history, firstMes)) {
            LlmMessage(
                role = LlmRole.ASSISTANT,
                content = firstMes,
                name = input.character?.name?.takeIf { it.isNotBlank() },
            )
        } else {
            null
        }

        // ── 5. 历史（原样搬运，保留 raw；**标记 fromHistory 以免被合并改写**） ──
        val historyMessages = input.history.map { if (it.fromHistory) it else it.copy(fromHistory = true) }

        // ── 6. 尾部快照（不变则不发；重放模式一律不发） ──
        // 位置：历史之后、本轮输入之前；调用方**必须把它落盘**（见 `Result.snapshotText` 的说明），
        // 于是下一轮它就在历史里、位置不变 → 请求才真正是纯追加。
        val snapshot = if (!input.emitSnapshot) {
            null
        } else {
            RuntimeSnapshots.project(
                current = PromptSections.snapshotSections(input.worldEntries, input.recallBlock),
                retained = input.retainedSnapshot,
            )
        }
        val snapshotMessage = snapshot?.let { LlmMessage(role = LlmRole.USER, content = it) }

        // ── 7. 本轮用户输入 ──
        // 带图片时 content 换成 parts 数组（rawContent 通道）：正文在前、图片在后。
        // 纯文本时与引入附件之前逐字节一致（前缀缓存不受 C4 影响）。
        val thisTurnUser = input.userText.takeIf { it.isNotBlank() || input.userAttachments.isNotEmpty() }
            ?.let {
                val parts = com.luzzymeow.luzzyrp.ui.pages.chat.userContentParts(input.userText, input.userAttachments)
                LlmMessage(role = LlmRole.USER, content = input.userText, rawContent = parts)
            }

        // ── 8. 合并策略：**历史段一律不合并**；只合并本轮新构造的相邻同 role ──
        //
        // 这是一处与上游的**有意偏离**，理由是缓存（本计划 §1.3）：
        // 上游「拼完整串再合并」会把历史里相邻的同 role 消息粘成一条。而**合并后那条消息的内容
        // 取决于它的邻居**——下一轮邻居变了（例如新增了一条 user 快照），合并结果就变 →
        // 已经进入历史的那条消息被改写 → **前缀从这里断裂**。
        //
        // 代价：可能给供应商送去相邻同 role 的消息。主流供应商都接受（上游自己的历史里也常有
        // 连续 user），而换来的「历史逐字节不变」是缓存成立的前提。
        val bands = listOf(
            // 本轮新构造的段：可合并（它们每轮都重算，合并与否不影响缓存）
            // system 恒定存在（见上），内容为空也没有关系
            listOf(LlmMessage(role = LlmRole.SYSTEM, content = systemText)),
            presetMessages,
            listOfNotNull(preludeMessage),
            listOfNotNull(firstMesMessage),
            // ★ 历史段：**逐字保留**，不做任何合并
            historyMessages,
            listOfNotNull(snapshotMessage),
            listOfNotNull(thisTurnUser),
        )
        return Result(
            messages = bands.flatMapIndexed { index, band ->
                if (index == HISTORY_BAND) band else mergeWithinBand(band)
            },
            snapshotText = snapshot,
        )
    }

    /** 历史段在 [assembleDetailed] 的 bands 里的下标（唯一不做合并的段）。 */
    private const val HISTORY_BAND = 4

    /** 段内合并：相邻同 role（user/assistant）按 `\n\n` 连接；system 与 tool 不参与。 */
    internal fun mergeWithinBand(band: List<LlmMessage>): List<LlmMessage> {
        val merged = mutableListOf<LlmMessage>()
        for (message in band) {
            val previous = merged.lastOrNull()
            val mergeable = message.role == LlmRole.USER || message.role == LlmRole.ASSISTANT
            if (previous != null && mergeable && previous.role == message.role) {
                merged[merged.lastIndex] = previous.copy(
                    content = listOf(previous.content, message.content)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n"),
                    name = previous.name ?: message.name,
                )
            } else {
                merged += message
            }
        }
        return merged
    }

    // ------------------------------------------------------------------ 各块构造

    /** ⑤ 用户信息块（上游 `built-in-content.js:59-64`）。空用户不注入。 */
    fun buildUserInfo(user: UserView): String? {
        if (user.isEmpty) return null
        return buildString {
            append("[User Info]")
            append("\nName: ").append(user.name)
            append("\nDescription: ").append(user.description)
            append("\nPreferences: ").append(user.preferences)
        }
    }

    /**
     * ⑦ 角色块（上游 `[Character]` + `Name:`/`Personality:` + `mes_example`）。
     *
     * **偏离**：`description` 也进 prompt（见类注释）；两者都空时只留 Name，块仍然成立。
     */
    fun buildCharacterBlock(character: CharacterView?): String? {
        if (character == null || !character.isUsable) return null
        val persona = listOf(character.description.trim(), character.personality.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        return buildString {
            append("[Character]\n")
            append("Name: ${character.name}")
            if (persona.isNotEmpty()) append("\nPersonality: $persona")
            val example = character.mesExample.trim()
            if (example.isNotEmpty()) append("\n\n$example")
        }
    }

    /** 历史首条是否**就是** first_mes（上游据此决定要不要补一条，`app.js:4645-4671`）。 */
    fun historyStartsWithFirstMes(history: List<LlmMessage>, firstMes: String): Boolean {
        val first = history.firstOrNull() ?: return false
        if (first.role != LlmRole.ASSISTANT) return false
        // 上游比的是「剥离 CoT 后的正文」；我们这边历史正文可能带内联 CoT，
        // 用 CotParser.mainOf 剥离后再比，避免「开场白被内联 CoT 包着」时误判成缺少
        val main = runCatching { CotParser.mainOf(first.content).trim() }.getOrDefault(first.content.trim())
        return main == firstMes.trim()
    }

    private fun renderIfNotEmpty(entries: List<WorldEntry>?): String? =
        entries?.let { WorldBookActivator.render(it) }?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ 注入

    /**
     * 上游 `safeTargetLimit`：**1（system）+ 预设消息数 + （有角色前置时）1**。
     * `at_depth` 不得插到这条线之上——否则世界书会挤进 system 与预设之间。
     */
    fun safeTargetLimit(presetMessageCount: Int, hasPrelude: Boolean): Int =
        1 + presetMessageCount + if (hasPrelude) 1 else 0

    /**
     * `at_depth` 注入：**新开一条 user 消息**，插到从末尾倒数第 `depth` 条 user/assistant **之前**。
     *
     * 逐条对齐上游 `findDepthIndex`（`data-services.js:990-1012`）：反向遍历、只对 user/assistant
     * 递减计数、`countdown < 0` 时命中、下限钳到 [safeTargetLimit]。
     * 组内按 `order` **升序**逐条插入（上游先排序再 forEach splice）。
     */
    private fun applyDepthInjection(messages: MutableList<LlmMessage>, entries: List<WorldEntry>, safeLimit: Int) {
        if (entries.isEmpty()) return
        entries.sortedBy { it.order }.forEach { entry ->
            if (entry.content.isBlank()) return@forEach
            val index = findDepthIndex(messages, entry.depth, safeLimit)
            messages.add(
                index,
                LlmMessage(
                    role = LlmRole.USER,
                    content = "[${entry.comment.ifBlank { "Entry" }}]\n${entry.content}",
                ),
            )
        }
    }

    /**
     * 深度锚点：从末尾反向找第 `depth` 条 user/assistant 的**前一位**；钳到 [safeLimit]。
     * 找不到（历史比 depth 还短）→ 返回 [safeLimit]（上游 `Math.max(-1, limit) = limit`，同）。
     */
    fun findDepthIndex(messages: List<LlmMessage>, depth: Int, safeLimit: Int): Int {
        var countdown = depth
        for (i in messages.indices.reversed()) {
            val role = messages[i].role
            if (role == LlmRole.USER || role == LlmRole.ASSISTANT) countdown--
            if (countdown < 0) return maxOf(i, safeLimit)
        }
        return safeLimit
    }

    /**
     * `user_top` / `assistant_top`：**就地前插**到最后一条该 role 消息的 content 里（不是新消息）。
     * 上游 `data-services.js:1041-1063`。该 role 一条都没有时不注入（上游同）。
     */
    private fun applyTopInjection(messages: MutableList<LlmMessage>, entries: List<WorldEntry>, role: LlmRole) {
        if (entries.isEmpty()) return
        val text = WorldBookActivator.render(entries)
        if (text.isBlank()) return
        val index = messages.indexOfLast { it.role == role }
        if (index < 0) return
        val target = messages[index]
        messages[index] = target.copy(content = "$text\n\n${target.content}")
    }

    // ------------------------------------------------------------------ 合并

    /**
     * 相邻同 role 合并（上游 `mergeConsecutiveRoleMessages`，`data-services.js:658-701`）。
     *
     * **只合并「本轮新构造的」相邻消息，绝不碰历史**——这是本实现与上游的关键差别，
     * 也是缓存能不能守住的分水岭：
     *
     * 上游是「拼完整串再合并」，于是**角色前置 user 消息会与被它接上的历史首条 user 合并**
     * （prelude 是 user role）。那条合并后的消息**每轮都不同**（prelude 每轮重新拼），
     * 而它已经进了历史位置 → 下一轮请求从那里起与上一轮不同 → **前缀缓存断裂**。
     *
     * 所以这里按 [LlmMessage.source] 分界：历史段内不做跨段合并，段与段之间也不合并。
     * 代价是可能出现两条相邻的 user 消息（多数供应商接受；上游自己的合并本就是为了压掉这类重复）。
     */
    fun mergeConsecutive(messages: List<LlmMessage>): List<LlmMessage> {
        val merged = mutableListOf<LlmMessage>()
        for (message in messages) {
            val previous = merged.lastOrNull()
            val mergeable = message.role == LlmRole.USER || message.role == LlmRole.ASSISTANT
            // 已经标记为「历史」的段不参与合并：它必须逐字节保持原样
            val previousIsHistory = previous?.fromHistory == true
            if (previous != null && mergeable && previous.role == message.role && !previousIsHistory) {
                merged[merged.lastIndex] = previous.copy(
                    content = listOf(previous.content, message.content).filter { it.isNotBlank() }
                        .joinToString("\n\n"),
                    name = previous.name ?: message.name,
                )
            } else {
                merged += message
            }
        }
        return merged
    }
}

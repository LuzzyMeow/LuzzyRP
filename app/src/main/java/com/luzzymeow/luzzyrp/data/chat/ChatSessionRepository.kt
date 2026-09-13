package com.luzzymeow.luzzyrp.data.chat

import com.luzzymeow.luzzyrp.chat.ChatBranch
import com.luzzymeow.luzzyrp.data.legacy.LegacyKeys
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.BranchEntity
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.store.MessageEntity
import com.luzzymeow.luzzyrp.ui.pages.chat.AiResult
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatMessage
import com.luzzymeow.luzzyrp.ui.pages.chat.ThinkNode
import com.luzzymeow.luzzyrp.ui.pages.chat.text

/**
 * 聊天会话的读写（P4-B-3.4）：把存储行与界面消息模型对上，界面只调这里。
 *
 * ## 为什么写入按「单条操作」而不是「整段重写」
 *
 * 这正是选 Room 的理由（`docs/DESIGN-migration.md` §8.2）：追加一条/改一条只碰一行。
 * 所以这里**没有** `saveAll(list)` 这种接口，只有：
 * [append] / [updateContent] / [deleteFrom] —— 与界面上真实发生的动作一一对应。
 *
 * 顺带解决一个保真问题：旧数据里用户消息带着 `isSelf` / `avatar` / `imageAttachments` 等字段，
 * 它们都存在 payload 列里。按行更新**不动 payload**，于是这些字段在编辑/重生成之后依然在；
 * 若用「删了重插整段」，它们会在每次编辑时被丢掉。
 *
 * ## 已知缺口（如实登记）
 *
 * 多候选（「重新生成」产生的 `‹ n/m ›`）**目前只持久化被选中的那一版**：
 * 存储里一条消息一行，候选集合是纯界面态。要持久化候选需要改表（一条消息多行候选），
 * 属 P5 范围；现在重启后只保留当前展示的那一版。
 */
class ChatSessionRepository(private val store: LuzzyStore) {

    /** 一次会话的完整快照（一个角色 + 它的全部分支与消息）。 */
    data class Session(
        val character: CharacterEntity,
        val branches: List<ChatBranch>,
        val activeBranchId: String,
        /** key = branchId（**不是**存储 scope 字符串；两者由 [scopeOf] 统一换算）。 */
        val messagesByBranch: Map<String, List<ChatMessage>>,
    ) {
        val isEmpty: Boolean get() = messagesByBranch.values.all { it.isEmpty() }
    }

    // ---------------------------------------------------------------- 读

    /**
     * 载入当前会话；**空库返回 null**（调用方据此回落演示数据，不在这里造数据）。
     *
     * 角色选择顺序：kv 里记的当前角色 → 最早创建的角色。
     */
    suspend fun load(): Session? {
        val characters = store.characters()
        if (characters.isEmpty()) return null
        val remembered = store.string(LuzzyStore.KEY_ACTIVE_CHARACTER)
        val character = characters.firstOrNull { it.uuid == remembered } ?: characters.first()

        val branchRows = store.branches(character.uuid)
        val branches = branchRows.map { it.toChatBranch() }.ifEmpty {
            // 旧数据里分支容器可能缺失（迁移器已补主线；这里是防御性的第二层）
            listOf(ChatBranch.main())
        }
        val rememberedActive = store.activeBranchId(character.uuid)
        val active = branches.firstOrNull { it.id == rememberedActive }?.id
            ?: branches.firstOrNull { it.id == LegacyKeys.MAIN_BRANCH_ID }?.id
            ?: branches.first().id

        // **只装载当前分支**（P4-C 性能专项）：旧写法把该角色**所有分支的全部消息**读进内存，
        // 成本随分支数线性增长（20 条分支 × 上千条 = 几万行 + 几万个对象），而界面一次只看一条。
        // 其余分支由 [loadBranch] 在切换时按需装入。
        val messages = LinkedHashMap<String, List<ChatMessage>>()
        messages[active] = store.messages(scopeOf(character.uuid, active)).map { it.toChatMessage() }
        return Session(character, branches, active, messages)
    }

    /**
     * 一条会话（角色 × 分支）的摘要 —— **跨角色平铺总览**的数据层。
     *
     * 只带列表要显示的东西（角色名/分支名/条数/末条预览），不搬历史正文：
     * 总览页要为每个分支各取一次摘要，把正文一起读出来会让「打开一个列表」变成搬几 MB。
     * 呈现层（总览页）尚未设计（新页面属视觉产出，按硬性规定 9 需先过设计流程），
     * 所以这一层先把数据准备好并按语义排好序。
     */
    data class SessionSummary(
        val characterUuid: String,
        val characterName: String,
        val characterAvatarPath: String?,
        val branchId: String,
        val branchName: String,
        val isMain: Boolean,
        val messageCount: Int,
        /**
         * 预览文字（可能很长，呈现层自行截断）。
         *
         * **取最后一条用户发言**（用户 2026-09-13 拍板）：目录式读法里人记得住的是自己说过的话，
         * 而且能绕开模型输出里的脏前缀。**无用户发言时回落末条正文**——否则那一行会空着，
         * 与已批准的版式不符（这一条偏离已在 `boards-v5/direction-approved-v5.md` §3 如实登记）。
         */
        val previewText: String?,
    )

    /**
     * 全部会话的平铺摘要。
     *
     * 排序：**角色按创建时间**（旧数据里角色卡没有修改时间，创建时间是唯一稳定的次序），
     * 角色内**主线在前、其余按创建时间**（与分支列表的排序口径一致）。
     * 空会话（没有任何消息的分支）**也列出来**——用户需要看到「这个分支还是空的」，
     * 而不是让它凭空消失。
     */
    suspend fun overview(): List<SessionSummary> {
        // **常数次查询**（与数据集规模无关）：角色 1 次 + 全部分支 1 次 + 全会话聚合 1 次。
        // 旧写法是「每角色查分支 + 每会话查 3 次」，实测 90 条会话 332ms（PerfProfileTest 基线）。
        val characters = store.characters()
        if (characters.isEmpty()) return emptyList()
        val branchesByCharacter = store.allBranches().groupBy { it.characterUuid }
        val stats = store.scopeStats()
            .associateBy { it.scopeId }
            .mapValues { (_, s) ->
                // 预览取的是**正文**：旧的 assistant 消息把思维链内联在 content 里，
                // 不剥掉的话总览会显示「<thinking>[情景意图分析]…」（真实数据里就有）
                s.copy(
                    lastContent = s.lastContent?.let { com.luzzymeow.luzzyrp.chat.CotParser.mainOf(it) },
                    lastUserContent = s.lastUserContent?.let { com.luzzymeow.luzzyrp.chat.CotParser.mainOf(it) },
                )
            }

        val result = mutableListOf<SessionSummary>()
        for (character in characters) {
            val branches = branchesByCharacter[character.uuid].orEmpty().ifEmpty {
                listOf(BranchEntity(character.uuid, ChatBranch.MainId, "主线", null, 0L, 0L, 0, 0, 0, true))
            }
            for (branch in branches) {
                val stat = stats[scopeOf(character.uuid, branch.branchId).suffix()]
                result += SessionSummary(
                    characterUuid = character.uuid,
                    characterName = character.name,
                    characterAvatarPath = character.avatarPath,
                    branchId = branch.branchId,
                    branchName = branch.name,
                    isMain = branch.isMain,
                    messageCount = stat?.messageCount ?: 0,
                    // 预览取最后一条用户发言；无用户发言时回落末条正文（见 SessionSummary.previewText）
                    previewText = stat?.lastUserContent ?: stat?.lastContent,
                )
            }
        }
        return result
    }

    /**
     * 按需装载某条分支的消息（切换分支时用）。
     *
     * 与 [load] 只装当前分支配套：切换是**高频动作**，但每次只需要一条分支。
     */
    suspend fun loadBranch(characterUuid: String, branchId: String): List<ChatMessage> =
        store.messages(scopeOf(characterUuid, branchId)).map { it.toChatMessage() }

    /** 存储作用域：主线是裸 uuid，分支带 `__branch__`（与旧存储逐字一致）。 */
    fun scopeOf(characterUuid: String, branchId: String): ScopeId = ScopeId(characterUuid, branchId)

    // ---------------------------------------------------------------- 写

    /** 当前角色（kv 里记的；空库返回 null）。总览用它标出「正在写的那条」。 */
    suspend fun currentCharacterUuid(): String? =
        store.string(LuzzyStore.KEY_ACTIVE_CHARACTER)?.takeIf { it.isNotBlank() }
            ?: store.characters().firstOrNull()?.uuid

    /** 某角色当前所在分支（`branch_meta.activeBranchId`）。 */
    suspend fun currentBranchId(characterUuid: String): String? = store.activeBranchId(characterUuid)

    /** 记住当前角色（启动时恢复用）。 */
    suspend fun rememberActiveCharacter(uuid: String) = store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, uuid)

    suspend fun rememberActiveBranch(characterUuid: String, branchId: String) {
        val meta = store.branches(characterUuid)
        store.replaceBranches(
            characterUuid = characterUuid,
            branches = meta,
            activeBranchId = branchId,
        )
    }

    /** 追加一条消息（发送、生成收尾）。[sortIndex] 由调用方给出，避免边追加边查计数导致跳号。 */
    suspend fun append(characterUuid: String, branchId: String, message: ChatMessage, reasoning: String? = null) {
        val scope = scopeOf(characterUuid, branchId)
        val nextIndex = store.messageCount(scope)
        store.appendMessage(encodeMessage(scope.suffix(), nextIndex, message, reasoning))
    }

    /** 就地改写正文（编辑消息、候选切换）。**只动 content 列，payload 原样保留。** */
    suspend fun updateContent(characterUuid: String, branchId: String, index: Int, text: String) {
        store.updateMessageContent(scopeOf(characterUuid, branchId).suffix(), index, text)
    }

    /** 删除某条，或删除它及之后（`andAfter = true`）。 */
    suspend fun delete(characterUuid: String, branchId: String, index: Int, andAfter: Boolean) {
        val scope = scopeOf(characterUuid, branchId)
        if (andAfter) {
            store.deleteMessagesFrom(scope, index)
        } else {
            store.deleteMessageAt(scope.suffix(), index)
        }
    }

    /** 新建分支并把它当前的消息副本落盘（从某楼分叉）。 */
    suspend fun createBranch(
        characterUuid: String,
        branch: ChatBranch,
        copiedMessages: List<ChatMessage>,
        makeActive: Boolean,
    ) {
        val existing = store.branches(characterUuid)
        val rows = existing + branch.toEntity(characterUuid, isMain = branch.id == ChatBranch.MainId)
        store.replaceBranches(
            characterUuid = characterUuid,
            branches = rows,
            activeBranchId = if (makeActive) branch.id else store.activeBranchId(characterUuid) ?: ChatBranch.MainId,
        )
        if (copiedMessages.isNotEmpty()) {
            store.replaceMessages(
                scopeOf(characterUuid, branch.id),
                copiedMessages.mapIndexed { index, message ->
                    encodeMessage(scopeOf(characterUuid, branch.id).suffix(), index, message, reasoning = null)
                },
            )
        }
    }

    /** 删除分支：连它的消息一起清掉（上游语义：删除分支即删除该分支的会话数据）。 */
    suspend fun deleteBranch(characterUuid: String, branchId: String) {
        store.replaceBranches(
            characterUuid = characterUuid,
            branches = store.branches(characterUuid).filterNot { it.branchId == branchId },
            activeBranchId = store.activeBranchId(characterUuid)
                ?.takeIf { it != branchId } ?: ChatBranch.MainId,
        )
        store.deleteScope(scopeOf(characterUuid, branchId))
    }

    /** 重命名分支。 */
    suspend fun renameBranch(characterUuid: String, branchId: String, name: String) {
        val rows = store.branches(characterUuid).map {
            if (it.branchId == branchId) it.copy(name = name) else it
        }
        store.replaceBranches(
            characterUuid = characterUuid,
            branches = rows,
            activeBranchId = store.activeBranchId(characterUuid) ?: ChatBranch.MainId,
        )
    }

    // ---------------------------------------------------------------- 映射

    private fun BranchEntity.toChatBranch(): ChatBranch = ChatBranch(
        id = branchId,
        name = name,
        parentId = parentId,
        createdAt = createdAt,
        isMain = isMain,
        forkFloor = forkFloor,
    )

    private fun ChatBranch.toEntity(characterUuid: String, isMain: Boolean): BranchEntity = BranchEntity(
        characterUuid = characterUuid,
        branchId = id,
        name = name,
        parentId = parentId,
        createdAt = createdAt,
        updatedAt = createdAt,
        forkFloor = forkFloor ?: 0,
        messageCount = 0,
        wordCount = 0,
        isMain = isMain,
    )

    /**
     * 存储行 → 界面消息。
     *
     * 实体构造在文件末尾的 [decodeMessage]（纯函数、**不需要数据库**）——
     * 「快照认得回来吗」「思考节点还原了吗」这两件事必须能在 JVM 单测里钉住，
     * 而 `ChatSessionRepository` 的实例要 Room 才建得出来。
     */
    internal fun MessageEntity.toChatMessage(): ChatMessage = decodeMessage(this)

    companion object {
        /**
         * 快照行的 `role` 取值。
         *
         * **不是 `user`**，尽管它在请求里就是一条 user 消息——理由全在**聚合查询**上：
         * 会话总览的「N 条」与「末条用户发言（预览）」都是 SQL 聚合出来的，而快照
         * 既不是消息也不是用户发言。给它一个独立 role，`WHERE role = 'user'` 天然把它排除，
         * 不必在每条 SQL 里写「payload 里没有某个标记」这种脆判据。
         * 代价是 `COUNT(*)` 要显式只数 `user`/`assistant`（见 `MessageDao.scopeStats`）。
         */
        const val ROLE_SNAPSHOT = "snapshot"

        /**
         * payload 里的私有标记键（**不改表**：payload 本来就是「其余字段的 JSON」）。
         *
         * 与 [ROLE_SNAPSHOT] 双保险：认回时两者任一命中即算快照。将来若有人用别的写法
         * 落盘快照（例如经迁移通道进来），只要带这个键就仍然认得回来。
         */
        const val PAYLOAD_SNAPSHOT_KEY = "luzzySnapshot"

        /** 存储行 role：快照独立成一种；其余按 user/assistant。 */
        fun roleOf(message: ChatMessage): String = when (message) {
            is ChatMessage.Snapshot -> ROLE_SNAPSHOT
            is ChatMessage.User -> "user"
            is ChatMessage.Ai -> "assistant"
        }

        /** 新行 payload：只有快照带标记（其余保持 `{}`，老行的多余字段由按行更新保住）。 */
        fun payloadOf(message: ChatMessage): String =
            if (message is ChatMessage.Snapshot) """{"$PAYLOAD_SNAPSHOT_KEY":true}""" else "{}"

        /** payload 是否带快照标记（宽松解析：坏 JSON / 缺键一律 false）。 */
        fun isSnapshotPayload(payload: String): Boolean = runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(payload)
            val value = (element as? kotlinx.serialization.json.JsonObject)
                ?.get(PAYLOAD_SNAPSHOT_KEY) as? kotlinx.serialization.json.JsonPrimitive
            value?.content == "true"
        }.getOrDefault(false)

        /** 这一行是不是尾部快照（role 或 payload 任一命中即算，坏 JSON 一律当普通消息）。 */
        fun isSnapshotRow(entity: MessageEntity): Boolean =
            entity.role == ROLE_SNAPSHOT || isSnapshotPayload(entity.payload)
    }
}

/**
 * 界面消息 → 存储行（纯函数，可单测）。
 *
 * `payload` 的**默认值 `{}`** 是刻意的：新行没有旧结构的多余字段（`isSelf` / `avatar` /
 * `imageAttachments`），而那些字段在**旧行**里必须原样活着——所以更新走「只动 content 列」
 * 的按行更新（见 [LuzzyDao.updateContent][com.luzzymeow.luzzyrp.data.store.MessageDao.updateContent]），
 * 绝不删了重插。
 */
internal fun encodeMessage(
    scopeId: String,
    sortIndex: Int,
    message: ChatMessage,
    reasoning: String?,
): MessageEntity = MessageEntity(
    scopeId = scopeId,
    sortIndex = sortIndex,
    id = null,
    role = ChatSessionRepository.roleOf(message),
    name = message.name,
    content = message.text(),
    reasoning = reasoning,
    payload = ChatSessionRepository.payloadOf(message),
)

/**
 * 存储行 → 界面消息（纯函数，可单测）。
 *
 * 两条**认回**规则都在这一个函数里，因为它们都属于「重启后不能少东西」：
 *
 * ① **尾部快照**（A6）：认不回来就会被渲染成一条用户发言（内容是
 *    `Current runtime context…`，用户会以为自己说过这句话），还会被算进楼数；
 * ② **思考内容**（P4）：可能来自两处——内联在 content 里的 `<thinking>…</thinking>`
 *    （旧版 WebView 的存法，见 `CotParser`）与 `reasoning` 列。只认后者就是本轮修的缺陷：
 *    迁移进来的消息思维链跑到正文里、节点是空的。
 */
internal fun decodeMessage(entity: MessageEntity): ChatMessage = when {
    ChatSessionRepository.isSnapshotRow(entity) -> ChatMessage.Snapshot(entity.content)
    entity.role == "user" -> ChatMessage.User(entity.content)
    else -> {
        val inline = com.luzzymeow.luzzyrp.chat.CotParser.parse(entity.content)
        val nodes = buildList {
            if (inline.cot.isNotBlank()) {
                add(ThinkNode.Brainstorm(text = inline.cot, seconds = 0.0, streaming = false))
            }
            if (!entity.reasoning.isNullOrBlank() && entity.reasoning.trim() != inline.cot.trim()) {
                add(ThinkNode.Brainstorm(text = entity.reasoning, seconds = 0.0, streaming = false))
            }
        }
        ChatMessage.Ai(
            name = entity.name ?: "AI",
            results = listOf(AiResult(raw = entity.content, thinkNodes = nodes)),
        )
    }
}

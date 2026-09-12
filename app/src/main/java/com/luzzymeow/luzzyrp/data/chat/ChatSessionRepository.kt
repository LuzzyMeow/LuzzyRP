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

        val messages = LinkedHashMap<String, List<ChatMessage>>()
        for (branch in branches) {
            messages[branch.id] = store.messages(scopeOf(character.uuid, branch.id)).map { it.toChatMessage() }
        }
        return Session(character, branches, active, messages)
    }

    /** 存储作用域：主线是裸 uuid，分支带 `__branch__`（与旧存储逐字一致）。 */
    fun scopeOf(characterUuid: String, branchId: String): ScopeId = ScopeId(characterUuid, branchId)

    // ---------------------------------------------------------------- 写

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
        store.appendMessage(
            MessageEntity(
                scopeId = scope.suffix(),
                sortIndex = nextIndex,
                id = null,
                role = if (message is ChatMessage.User) "user" else "assistant",
                name = message.name,
                content = message.text(),
                reasoning = reasoning,
                // 新消息没有旧结构的多余字段；旧消息的 payload 由「按行更新」保住
                payload = "{}",
            ),
        )
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
                    MessageEntity(
                        scopeId = scopeOf(characterUuid, branch.id).suffix(),
                        sortIndex = index,
                        id = null,
                        role = if (message is ChatMessage.User) "user" else "assistant",
                        name = message.name,
                        content = message.text(),
                        reasoning = null,
                        payload = "{}",
                    )
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
     * 思考内容（`reasoning`）**必须还原成思考节点**：否则重启后用户看到的消息会「少一块」——
     * 这正是「思考节点随消息常驻」的持久化面。旧数据里工具节点没有独立字段
     * （工具调用在旧版是流式期状态，未落盘），故只还原 brainstorming 节点。
     */
    internal fun MessageEntity.toChatMessage(): ChatMessage = when (role) {
        "user" -> ChatMessage.User(content)
        else -> {
            val nodes = reasoning?.takeIf { it.isNotBlank() }
                ?.let { listOf(ThinkNode.Brainstorm(text = it, seconds = 0.0, streaming = false)) }
                ?: emptyList()
            ChatMessage.Ai(
                name = name ?: "AI",
                results = listOf(AiResult(raw = content, thinkNodes = nodes)),
            )
        }
    }
}

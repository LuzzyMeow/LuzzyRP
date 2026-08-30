package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.model.Conversation
import com.luzzymeow.luzzyrp.core.model.MessageNode
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.data.db.dao.ConversationDao
import com.luzzymeow.luzzyrp.data.db.dao.MessageNodeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 会话仓库：会话 + 消息节点树 + 分支（重 roll）操作的唯一入口。
 *
 * 分支模型：每次重新生成在父节点下新建兄弟节点，selectIndex 指向当前选中分支。
 * 写入策略：消息变更 = 更新当前节点的 messagesJson + touch 会话（ lastMessageAt）。
 */
class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val nodeDao: MessageNodeDao,
) {

    fun observeActiveConversations(): Flow<List<Conversation>> =
        conversationDao.observeActive().map { list -> list.map { it.toDomain(emptyList()) } }

    fun observeArchivedConversations(): Flow<List<Conversation>> =
        conversationDao.observeArchived().map { list -> list.map { it.toDomain(emptyList()) } }

    fun observeConversation(id: String): Flow<Conversation?> =
        combineConversationWithNodes(id)

    private fun combineConversationWithNodes(id: String): Flow<Conversation?> {
        return kotlinx.coroutines.flow.combine(
            conversationDao.observeById(id),
            nodeDao.observeByConversation(id),
        ) { entity, nodeEntities ->
            entity?.toDomain(nodeEntities.map { it.toDomain() })
        }
    }

    suspend fun getConversation(id: String): Conversation? {
        val entity = conversationDao.getById(id) ?: return null
        return entity.toDomain(nodeDao.getByConversation(id).map { it.toDomain() })
    }

    /** 新建会话（含开场白首节点）。 */
    suspend fun createConversation(conversation: Conversation, greeting: List<UIMessage> = emptyList()): Conversation {
        val now = System.currentTimeMillis()
        val withTime = conversation.copy(
            createdAt = conversation.createdAt ?: kotlin.time.Instant.fromEpochMilliseconds(now),
            updatedAt = kotlin.time.Instant.fromEpochMilliseconds(now),
        )
        conversationDao.upsert(withTime.toEntity(lastMessageAt = now))
        val rootNode = MessageNode(
            id = UUID.randomUUID().toString(),
            parentId = null,
            messages = greeting,
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(now),
        )
        nodeDao.upsert(rootNode.toEntity(withTime.id))
        conversationDao.update(withTime.toEntity(lastMessageAt = now).copy(currentNodeId = rootNode.id))
        return getConversation(withTime.id) ?: withTime
    }

    /** 保存会话元信息（标题/置顶/绑定等）。 */
    suspend fun saveConversation(conversation: Conversation) {
        val now = System.currentTimeMillis()
        conversationDao.upsert(
            conversation.copy(updatedAt = kotlin.time.Instant.fromEpochMilliseconds(now))
                .toEntity(lastMessageAt = now)
        )
    }

    /** 更新指定节点的消息列表（生成过程中的流式写入与最终落库共用此入口）。 */
    suspend fun updateNodeMessages(conversationId: String, nodeId: String, messages: List<UIMessage>) {
        val existing = nodeDao.getById(nodeId) ?: return
        nodeDao.update(existing.copy(messagesJson = serializeMessages(messages)))
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    /** 追加新消息（用户输入）：写入当前节点末尾；节点为空则不新建分支。 */
    suspend fun appendMessageToCurrentNode(conversationId: String, message: UIMessage) {
        val conversation = getConversation(conversationId) ?: return
        val currentNodeId = conversation.currentNodeId ?: return
        val node = nodeDao.getById(currentNodeId) ?: return
        val messages = deserializeMessages(node.messagesJson) + message
        nodeDao.update(node.copy(messagesJson = serializeMessages(messages)))
        conversationDao.touch(conversationId, System.currentTimeMillis())
    }

    /**
     * 重新生成分支：在 parent 节点下创建新子节点（携带截断到指定消息之前的上下文），
     * 并把会话的 currentNodeId 切换过去。
     *
     * @param branchMessages 新分支的起始消息序列（不含将重新生成的内容）
     * @return 新节点 id
     */
    suspend fun branchForRegenerate(conversationId: String, branchMessages: List<UIMessage>): String {
        val conversation = getConversation(conversationId) ?: error("会话不存在：$conversationId")
        val parentId = conversation.currentNodeId ?: error("会话无当前节点")
        val now = System.currentTimeMillis()
        val newNode = MessageNode(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            messages = branchMessages,
            createdAt = kotlin.time.Instant.fromEpochMilliseconds(now),
        )
        nodeDao.upsert(newNode.toEntity(conversationId))

        // 兄弟节点计数 → selectIndex 指向最新分支
        val siblings = nodeDao.getByConversation(conversationId).filter { it.parentId == parentId }
        nodeDao.setSelectIndex(parentId, siblings.indexOfFirst { it.id == newNode.id }.coerceAtLeast(0))
        conversationDao.update(
            conversation.toEntity(lastMessageAt = now).copy(currentNodeId = newNode.id)
        )
        return newNode.id
    }

    /** 切换分支（历史导航）。 */
    suspend fun switchBranch(conversationId: String, nodeId: String) {
        val conversation = getConversation(conversationId) ?: return
        conversationDao.update(conversation.toEntity(lastMessageAt = System.currentTimeMillis()).copy(currentNodeId = nodeId))
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.delete(id)
    }

    suspend fun setPinned(id: String, pinned: Boolean) = conversationDao.setPinned(id, pinned)

    suspend fun setArchived(id: String, archived: Boolean) = conversationDao.setArchived(id, archived)

    suspend fun rename(id: String, title: String) {
        val entity = conversationDao.getById(id) ?: return
        conversationDao.update(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun conversationCount(): Int = conversationDao.count()

    // —— JSON 边界 ——
    private fun serializeMessages(messages: List<UIMessage>): String =
        com.luzzymeow.luzzyrp.core.common.JsonInstant.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(UIMessage.serializer()), messages,
        )

    private fun deserializeMessages(json: String): List<UIMessage> =
        runCatching {
            com.luzzymeow.luzzyrp.core.common.JsonInstant.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(UIMessage.serializer()), json,
            )
        }.getOrDefault(emptyList())
}

package com.luzzymeow.luzzyrp.assistant.runtime

import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.dao.CjkBigram
import com.luzzymeow.luzzyrp.assistant.data.db.MessageFtsIndexer
import com.luzzymeow.luzzyrp.assistant.data.db.dao.FtsQueryBuilder
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MessageEntity
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import java.util.UUID

/**
 * 助手数据仓库（P2 持久化层）。
 *
 * 把 Room DAO 收拢成面向用例的接口，供 ViewModel 直接调用；同时承担两件容易出错的事：
 * 1. **FTS 索引随写**：一律走 `MessageDao` 的 `*Indexed` 事务方法（唯一写入口，
 *    触发器方案已被移除，见数据层注释）；
 * 2. **中文检索双通道**：正文走 FTS bigram（空则 LIKE 回落），标题走 LIKE
 *    （`ConversationDao.searchByTitle`）。
 */
class AssistantRepository(private val database: AssistantDatabase) {

    private val assistantDao = database.assistantDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val messageFtsDao = database.messageFtsDao()

    private val now: () -> Long = System::currentTimeMillis

    // ------------------------------------------------------------------
    // 助手
    // ------------------------------------------------------------------

    suspend fun assistants(): List<AssistantEntity> = assistantDao.getAll()

    /** 首次进入且一个助手都没有时，创建一个默认助手（否则 UI 无处可去）。 */
    suspend fun ensureDefaultAssistant(): AssistantEntity {
        val existing = assistantDao.getAll()
        if (existing.isNotEmpty()) return existing.first()
        val ts = now()
        val entity = AssistantEntity(
            id = UUID.randomUUID().toString(),
            name = "阿墨",
            avatarPath = null,
            systemPrompt = DEFAULT_SYSTEM_PROMPT,
            providerId = null,
            modelId = null,
            temperature = null,
            topP = null,
            maxTokens = null,
            extraBodyJson = null,
            paramsJson = null,
            memoryMode = AssistantEntity.MEMORY_MODE_HYBRID,
            embeddingModelRef = null,
            memoryTopK = 8,
            memoryThreshold = 0.35f,
            workspaceMode = AssistantEntity.WORKSPACE_MODE_HOST,
            createdAt = ts,
            updatedAt = ts,
            sortOrder = 0,
        )
        assistantDao.upsert(entity)
        return entity
    }

    suspend fun createAssistant(name: String): AssistantEntity {
        val ts = now()
        val entity = AssistantEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            avatarPath = null,
            systemPrompt = DEFAULT_SYSTEM_PROMPT,
            providerId = null,
            modelId = null,
            temperature = null,
            topP = null,
            maxTokens = null,
            extraBodyJson = null,
            paramsJson = null,
            memoryMode = AssistantEntity.MEMORY_MODE_HYBRID,
            embeddingModelRef = null,
            memoryTopK = 8,
            memoryThreshold = 0.35f,
            workspaceMode = AssistantEntity.WORKSPACE_MODE_HOST,
            createdAt = ts,
            updatedAt = ts,
            sortOrder = assistantDao.count(),
        )
        assistantDao.upsert(entity)
        return entity
    }

    suspend fun assistant(id: String): AssistantEntity? = assistantDao.getById(id)

    suspend fun updateAssistant(entity: AssistantEntity) = assistantDao.update(entity)

    /** 软删除（归档）；工作区目录默认保留（PLAN §6.1）。 */
    suspend fun archiveAssistant(id: String) {
        val entity = assistantDao.getById(id) ?: return
        assistantDao.update(entity.copy(updatedAt = now()))
    }

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------

    suspend fun conversations(assistantId: String, limit: Int = 200): List<ConversationEntity> =
        conversationDao.pageByAssistant(assistantId, limit, 0)

    suspend fun conversation(id: String): ConversationEntity? = conversationDao.getById(id)

    suspend fun createConversation(assistantId: String, title: String = "新会话"): ConversationEntity {
        val ts = now()
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            assistantId = assistantId,
            title = title,
            summary = null,
            createdAt = ts,
            updatedAt = ts,
            archived = false,
        )
        conversationDao.upsert(entity)
        return entity
    }

    suspend fun renameConversation(id: String, title: String) =
        conversationDao.updateTitle(id, title, now())

    suspend fun archiveConversation(id: String, archived: Boolean = true) =
        conversationDao.setArchived(id, archived, now())

    suspend fun deleteConversation(id: String) = messageDao.deleteByConversationIndexed(id).also {
        conversationDao.deleteById(id)
    }

    /** 标题检索（CJK 走 LIKE，见数据层决策 D3b）。 */
    suspend fun searchConversations(assistantId: String, query: String, limit: Int = 30): List<ConversationEntity> =
        conversationDao.searchByTitle(assistantId, query, limit)

    // ------------------------------------------------------------------
    // 消息
    // ------------------------------------------------------------------

    suspend fun messages(conversationId: String, limit: Int = 500): List<MessageEntity> =
        messageDao.pageByConversation(conversationId, limit, 0)

    /** 追加一条消息并同步 FTS 索引。 */
    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        reasoning: String? = null,
        toolCallsJson: String? = null,
        toolCallId: String? = null,
        toolName: String? = null,
        status: String = MessageEntity.STATUS_COMPLETE,
        tokenUsageJson: String? = null,
    ): MessageEntity {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            reasoning = reasoning,
            toolCallsJson = toolCallsJson,
            toolCallId = toolCallId,
            toolName = toolName,
            status = status,
            createdAt = now(),
            tokenUsageJson = tokenUsageJson,
        )
        messageDao.insertIndexed(entity)
        conversationDao.touch(conversationId, entity.createdAt)
        return entity
    }

    suspend fun updateMessage(entity: MessageEntity) = messageDao.updateIndexed(entity)

    /** 会话首条用户消息 → 自动标题（截断；模型生成标题留作 P2 增强）。 */
    suspend fun autoTitleIfNeeded(conversationId: String, userText: String) {
        val conversation = conversationDao.getById(conversationId) ?: return
        if (conversation.title != "新会话") return
        val title = userText.replace('\n', ' ').trim().take(24).ifBlank { "新会话" }
        conversationDao.updateTitle(conversationId, title, now())
    }

    /** 正文检索：FTS bigram 优先，无命中回落 LIKE（数据层决策 D3）。 */
    suspend fun searchMessages(assistantId: String, query: String, limit: Int = 50): List<MessageEntity> {
        val expression = FtsQueryBuilder.toMatchExpression(query)
        if (expression != null) {
            val hits = runCatching {
                messageFtsDao.searchInAssistant(expression, assistantId, limit)
            }.getOrDefault(emptyList())
            if (hits.isNotEmpty()) return hits
        }
        return runCatching { messageFtsDao.searchFallback(assistantId, query, limit) }.getOrDefault(emptyList())
    }

    /** 重建索引（导入/异常后修复用）。 */
    suspend fun rebuildIndex(): Int = MessageFtsIndexer(database).rebuildAll()

    /** 把持久化消息映射为领域消息（供 ContextBuilder 装配历史）。 */
    fun toLlmMessages(rows: List<MessageEntity>): List<LlmMessage> = rows.mapNotNull { row ->
        when (row.role) {
            MessageEntity.ROLE_USER -> LlmMessage(LlmRole.USER, row.content)
            MessageEntity.ROLE_ASSISTANT -> LlmMessage(LlmRole.ASSISTANT, row.content, row.reasoning)
            MessageEntity.ROLE_SYSTEM -> LlmMessage(LlmRole.SYSTEM, row.content)
            MessageEntity.ROLE_TOOL -> LlmMessage(
                LlmRole.TOOL,
                row.content,
                toolCallId = row.toolCallId,
                name = row.toolName,
            )
            else -> null
        }
    }

    /** 工具调用落库用的 JSON（只存协议字段，不存结果正文）。 */
    fun toolCallsJson(calls: List<ToolCall>): String? =
        if (calls.isEmpty()) null else calls.joinToString(prefix = "[", postfix = "]") { call ->
            """{"id":"${call.id}","name":"${call.name}","arguments":${call.arguments}}"""
        }

    /** 统计（设置页/诊断用）。 */
    suspend fun messageCount(assistantId: String): Int = messageDao.countByAssistant(assistantId)

    suspend fun conversationCount(assistantId: String): Int = conversationDao.countByAssistant(assistantId)

    /** 会话导出（PLAN §6.2：Markdown / JSON 两种）。 */
    suspend fun exportConversation(conversationId: String, asJson: Boolean): String {
        val conversation = conversationDao.getById(conversationId) ?: return ""
        val rows = messageDao.getByConversation(conversationId)
        return if (asJson) {
            buildString {
                append("{\"id\":\"").append(conversation.id).append("\",")
                append("\"title\":\"").append(escape(conversation.title)).append("\",")
                append("\"createdAt\":").append(conversation.createdAt).append(',')
                append("\"messages\":[")
                rows.forEachIndexed { index, row ->
                    if (index > 0) append(',')
                    append("{\"role\":\"").append(row.role).append("\",")
                    append("\"content\":\"").append(escape(row.content)).append("\",")
                    append("\"createdAt\":").append(row.createdAt).append('}')
                }
                append("]}")
            }
        } else {
            buildString {
                append("# ").append(conversation.title).append("\n\n")
                rows.forEach { row ->
                    append("**").append(roleLabel(row.role)).append("**\n\n")
                    row.reasoning?.takeIf { it.isNotBlank() }?.let { append("> ").append(it).append("\n\n") }
                    append(row.content).append("\n\n")
                }
            }
        }
    }

    private fun roleLabel(role: String): String = when (role) {
        MessageEntity.ROLE_USER -> "用户"
        MessageEntity.ROLE_ASSISTANT -> "助手"
        MessageEntity.ROLE_TOOL -> "工具"
        else -> "系统"
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "你是用户的贴身助手，中文回答，简洁直接。\n" +
                "需要事实时先查记忆或检索；需要写入/执行前先说明意图。\n" +
                "工具返回错误时如实说明，不要编造结果。"

        /** 供 UI 复用的中文分词（与索引同源，避免查询侧与索引侧切分不一致）。 */
        fun searchTextOf(text: String): String = CjkBigram.searchText(text)
    }
}

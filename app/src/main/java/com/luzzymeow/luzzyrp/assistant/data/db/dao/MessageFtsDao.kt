package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MessageEntity

/**
 * message_fts（FTS4）检索 DAO（PLAN §4.1 末段 / §6.2）。
 *
 * 检索分两级（派发者 2026-09-09 拍板「bigram 为主 + LIKE 兜底」）：
 * 1. FTS：matchExpression 必须先用 FtsQueryBuilder.toMatchExpression 生成
 *    （与索引侧同一套 CjkBigram 切分；禁止把用户原始输入直接拼进 MATCH）；
 * 2. LIKE 回落：FTS 结果为空（或查询无有效 token）时调 searchFallback，
 *    content LIKE '%kw%' ESCAPE '\'，% / _ / \ 已由 LikeEscaper 转义。
 *
 * 本 DAO **不写 FTS 行**：写入（含 search_text 生成）由 MessageDao 的事务方法负责，
 * 重建由 MessageFtsIndexer 负责；这里只保留标题刷新与清空。
 */
@Dao
interface MessageFtsDao {

    @Query(
        """
        SELECT m.id, m.conversationId, m.role, m.content, m.reasoning, m.toolCallsJson,
               m.toolCallId, m.toolName, m.status, m.createdAt, m.tokenUsageJson
        FROM message AS m
        INNER JOIN message_fts ON message_fts.rowid = m.rowid
        WHERE message_fts MATCH :matchExpression
        ORDER BY m.createdAt DESC, m.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun search(matchExpression: String, limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT m.id, m.conversationId, m.role, m.content, m.reasoning, m.toolCallsJson,
               m.toolCallId, m.toolName, m.status, m.createdAt, m.tokenUsageJson
        FROM message AS m
        INNER JOIN message_fts ON message_fts.rowid = m.rowid
        WHERE message_fts MATCH :matchExpression AND m.conversationId = :conversationId
        ORDER BY m.createdAt DESC, m.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun searchInConversation(matchExpression: String, conversationId: String, limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT m.id, m.conversationId, m.role, m.content, m.reasoning, m.toolCallsJson,
               m.toolCallId, m.toolName, m.status, m.createdAt, m.tokenUsageJson
        FROM message AS m
        INNER JOIN message_fts ON message_fts.rowid = m.rowid
        INNER JOIN conversation AS c ON c.id = m.conversationId
        WHERE message_fts MATCH :matchExpression AND c.assistantId = :assistantId
        ORDER BY m.createdAt DESC, m.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun searchInAssistant(matchExpression: String, assistantId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM message_fts WHERE message_fts MATCH :matchExpression")
    suspend fun countMatches(matchExpression: String): Int

    // ---------------- LIKE 回落 ----------------

    /**
     * LIKE 回落（keyword 已转义，仅供 searchFallback 与测试直接调用）。
     * ESCAPE '\' 声明转义符；\ % _ 均由 LikeEscaper 处理。
     */
    @Query(
        """
        SELECT m.id, m.conversationId, m.role, m.content, m.reasoning, m.toolCallsJson,
               m.toolCallId, m.toolName, m.status, m.createdAt, m.tokenUsageJson
        FROM message AS m
        INNER JOIN conversation AS c ON c.id = m.conversationId
        WHERE c.assistantId = :assistantId AND m.content LIKE '%' || :escapedKeyword || '%' ESCAPE '\'
        ORDER BY m.createdAt DESC, m.rowid DESC
        LIMIT :limit
        """
    )
    suspend fun searchFallbackEscaped(assistantId: String, escapedKeyword: String, limit: Int): List<MessageEntity>

    /** LIKE 回落入口：对 % / _ / \ 自动转义（上层在 FTS 结果为空时调用）。 */
    suspend fun searchFallback(assistantId: String, keyword: String, limit: Int): List<MessageEntity> =
        searchFallbackEscaped(assistantId, LikeEscaper.escape(keyword), limit)

    // ---------------- 维护 ----------------

    /** 会话重命名时刷新 FTS 里的标题快照（另有 conversation_title_au 触发器自动同步，此为手工兜底）。 */
    @Query(
        """
        UPDATE message_fts SET title = :title
        WHERE rowid IN (SELECT rowid FROM message WHERE conversationId = :conversationId)
        """
    )
    suspend fun refreshTitleForConversation(conversationId: String, title: String)

    @Query("DELETE FROM message_fts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM message_fts")
    suspend fun count(): Int

    /** 重建索引时按 rowid 分页读取正文（MessageFtsIndexer 用）。 */
    @Query("SELECT id, content FROM message ORDER BY rowid ASC LIMIT :limit OFFSET :offset")
    suspend fun messageContentPage(limit: Int, offset: Int): List<MessageContentRow>

    /** 补建索引：只取 message_fts 里还没有的 rowid（MessageFtsIndexer 用）。 */
    @Query("SELECT id, content FROM message WHERE rowid NOT IN (SELECT rowid FROM message_fts) ORDER BY rowid ASC LIMIT :limit")
    suspend fun messageContentMissing(limit: Int): List<MessageContentRow>
}

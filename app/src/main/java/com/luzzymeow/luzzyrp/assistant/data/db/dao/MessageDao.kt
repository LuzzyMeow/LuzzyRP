package com.luzzymeow.luzzyrp.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息表 DAO（PLAN §4.1 / §6.2）。
 *
 * **写入必须走 *Indexed 方法**（同一事务内写 message + message_fts，含 CjkBigram 生成的 search_text）：
 * insertIndexed / insertAllIndexed / updateIndexed / deleteIndexed / deleteByConversationIndexed /
 * deleteByAssistantIndexed。裸 insert/update/delete 仅供事务方法内部与重建使用，直接调用会让 FTS 索引漂移。
 *
 * message 表禁止 INSERT OR REPLACE：主键是 TEXT uuid，SQLite 另分配隐式 rowid，FTS 以该 rowid 关联，
 * REPLACE 会换 rowid 并残留孤儿 FTS 行——故 @Insert 一律 ABORT。
 *
 * 排序用 createdAt 升序、rowid 升序兜底（同一毫秒内按插入顺序稳定）。
 */
@Dao
interface MessageDao {

    // ---------------- 裸写（内部使用） ----------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM message WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM message WHERE conversationId IN (SELECT id FROM conversation WHERE assistantId = :assistantId)")
    suspend fun deleteByAssistant(assistantId: String)

    // ---------------- FTS 同步写（唯一对外写入口） ----------------

    @Transaction
    suspend fun insertIndexed(message: MessageEntity) {
        insert(message)
        insertFtsRow(message.id, CjkBigram.searchText(message.content))
    }

    @Transaction
    suspend fun insertAllIndexed(messages: List<MessageEntity>) {
        insertAll(messages)
        for (message in messages) {
            insertFtsRow(message.id, CjkBigram.searchText(message.content))
        }
    }

    @Transaction
    suspend fun updateIndexed(message: MessageEntity) {
        update(message)
        deleteFtsRow(message.id)
        insertFtsRow(message.id, CjkBigram.searchText(message.content))
    }

    @Transaction
    suspend fun deleteIndexed(id: String) {
        deleteFtsRow(id)
        deleteById(id)
    }

    @Transaction
    suspend fun deleteByConversationIndexed(conversationId: String) {
        deleteFtsRowsByConversation(conversationId)
        deleteByConversation(conversationId)
    }

    @Transaction
    suspend fun deleteByAssistantIndexed(assistantId: String) {
        deleteFtsRowsByAssistant(assistantId)
        deleteByAssistant(assistantId)
    }

    /** 按 message.id 定位隐式 rowid 写入 FTS 行（message 行必须已存在）。 */
    @Query(
        """
        INSERT INTO message_fts(rowid, content, title, search_text)
        SELECT m.rowid, m.content,
               COALESCE((SELECT c.title FROM conversation AS c WHERE c.id = m.conversationId), ''),
               :searchText
        FROM message AS m
        WHERE m.id = :messageId
        """
    )
    suspend fun insertFtsRow(messageId: String, searchText: String)

    @Query("DELETE FROM message_fts WHERE rowid = (SELECT rowid FROM message WHERE id = :messageId)")
    suspend fun deleteFtsRow(messageId: String)

    @Query("DELETE FROM message_fts WHERE rowid IN (SELECT rowid FROM message WHERE conversationId = :conversationId)")
    suspend fun deleteFtsRowsByConversation(conversationId: String)

    @Query(
        """
        DELETE FROM message_fts WHERE rowid IN (
            SELECT rowid FROM message
            WHERE conversationId IN (SELECT id FROM conversation WHERE assistantId = :assistantId)
        )
        """
    )
    suspend fun deleteFtsRowsByAssistant(assistantId: String)

    // ---------------- 读 ----------------

    @Query("SELECT * FROM message WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    suspend fun getByConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY createdAt DESC, rowid DESC LIMIT :limit")
    suspend fun getRecent(conversationId: String, limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT * FROM message
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, rowid ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageByConversation(conversationId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("UPDATE message SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContent(id: String, content: String, status: String)

    @Query("UPDATE message SET reasoning = :reasoning WHERE id = :id")
    suspend fun updateReasoning(id: String, reasoning: String?)

    @Query("UPDATE message SET toolCallsJson = :toolCallsJson, toolCallId = :toolCallId, toolName = :toolName WHERE id = :id")
    suspend fun updateToolCalls(id: String, toolCallsJson: String?, toolCallId: String?, toolName: String?)

    @Query("UPDATE message SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE message SET tokenUsageJson = :tokenUsageJson WHERE id = :id")
    suspend fun updateTokenUsage(id: String, tokenUsageJson: String?)

    @Query("SELECT COUNT(*) FROM message WHERE conversationId = :conversationId")
    suspend fun countByConversation(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM message WHERE conversationId IN (SELECT id FROM conversation WHERE assistantId = :assistantId)")
    suspend fun countByAssistant(assistantId: String): Int
}

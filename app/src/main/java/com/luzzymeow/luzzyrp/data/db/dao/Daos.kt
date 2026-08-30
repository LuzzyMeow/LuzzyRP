package com.luzzymeow.luzzyrp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luzzymeow.luzzyrp.data.db.entity.CharacterCardEntity
import com.luzzymeow.luzzyrp.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.data.db.entity.FavoriteEntity
import com.luzzymeow.luzzyrp.data.db.entity.MemoryEntity
import com.luzzymeow.luzzyrp.data.db.entity.MessageNodeEntity
import com.luzzymeow.luzzyrp.data.db.entity.RegexScriptEntity
import com.luzzymeow.luzzyrp.data.db.entity.SummaryEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO 汇总。
 * 查询风格与 rikkahub 一致：列表走 Flow 响应式；写操作 suspend；
 * 复杂结构（消息列表）由仓库层做 JSON 编解码，DAO 只搬运字符串。
 */

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, lastMessageAt DESC")
    fun observeActive(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE cardId = :cardId ORDER BY lastMessageAt DESC")
    suspend fun getByCard(cardId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY lastMessageAt DESC")
    fun search(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE conversations SET lastMessageAt = :time, updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Dao
interface MessageNodeDao {

    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): List<MessageNodeEntity>

    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId")
    fun observeByConversation(conversationId: String): Flow<List<MessageNodeEntity>>

    @Query("SELECT * FROM message_nodes WHERE id = :id")
    suspend fun getById(id: String): MessageNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: MessageNodeEntity)

    @Update
    suspend fun update(node: MessageNodeEntity)

    @Query("UPDATE message_nodes SET selectIndex = :index WHERE id = :id")
    suspend fun setSelectIndex(id: String, index: Int)

    @Query("DELETE FROM message_nodes WHERE id = :id")
    suspend fun delete(id: String)

    /** 级联删除：删除会话的全部节点（ConversationDao.delete 的 CASCADE 已覆盖；此方法供单会话清理）。 */
    @Transaction
    @Query("DELETE FROM message_nodes WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

@Dao
interface CharacterCardDao {

    @Query("SELECT * FROM character_cards ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards WHERE id = :id")
    fun observeById(id: String): Flow<CharacterCardEntity?>

    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: String): CharacterCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CharacterCardEntity)

    @Query("DELETE FROM character_cards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM character_cards")
    suspend fun count(): Int
}

@Dao
interface WorldbookDao {

    @Query("SELECT * FROM worldbooks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WorldbookEntity>>

    @Query("SELECT * FROM worldbooks WHERE id = :id")
    suspend fun getById(id: String): WorldbookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: WorldbookEntity)

    @Query("DELETE FROM worldbooks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM worldbook_entries WHERE worldbookId = :worldbookId ORDER BY `order` ASC")
    fun observeEntries(worldbookId: String): Flow<List<WorldbookEntryEntity>>

    @Query("SELECT * FROM worldbook_entries WHERE worldbookId = :worldbookId ORDER BY `order` ASC")
    suspend fun getEntries(worldbookId: String): List<WorldbookEntryEntity>

    @Query("SELECT * FROM worldbook_entries WHERE id = :id")
    suspend fun getEntryById(id: String): WorldbookEntryEntity?

    @Query("SELECT * FROM worldbook_entries WHERE vectorIndexed = 0 AND enabled = 1")
    suspend fun getPendingVectorEntries(): List<WorldbookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: WorldbookEntryEntity)

    @Query("DELETE FROM worldbook_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("UPDATE worldbook_entries SET vectorIndexed = :indexed WHERE id = :id")
    suspend fun setVectorIndexed(id: String, indexed: Boolean)
}

@Dao
interface RegexDao {

    @Query("SELECT * FROM regex_scripts ORDER BY name ASC")
    fun observeAll(): Flow<List<RegexScriptEntity>>

    @Query("SELECT * FROM regex_scripts")
    suspend fun getAll(): List<RegexScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: RegexScriptEntity)

    @Query("DELETE FROM regex_scripts WHERE id = :id AND preset IS NULL")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM regex_scripts WHERE preset IS NOT NULL")
    suspend fun presetCount(): Int
}

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories WHERE active = 1 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE active = 1")
    suspend fun getActive(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    /** 评分淘汰候选：按 (helpful - harmful) 升序 + 失活时间升序，取最弱的前 N 条。 */
    @Query(
        "SELECT * FROM memories WHERE active = 1 " +
            "ORDER BY (helpful - harmful) ASC, lastRetrievedAt ASC LIMIT :limit"
    )
    suspend fun getWeakest(limit: Int): List<MemoryEntity>
}

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summaries WHERE conversationId = :conversationId ORDER BY roundIndex ASC")
    suspend fun getByConversation(conversationId: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE conversationId = :conversationId AND level = :level ORDER BY roundIndex DESC")
    suspend fun getByLevel(conversationId: String, level: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE conversationId = :conversationId AND level = 'c' ORDER BY roundIndex ASC")
    fun observePermanent(conversationId: String): Flow<List<SummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE conversationId = :conversationId AND level = :level AND roundIndex IN (:rounds)")
    suspend fun deleteByRounds(conversationId: String, level: String, rounds: List<Int>)
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE conversationId = :conversationId AND messageId = :messageId LIMIT 1")
    suspend fun getByMessage(conversationId: String, messageId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)
}

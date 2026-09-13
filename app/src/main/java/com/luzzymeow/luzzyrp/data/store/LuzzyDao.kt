package com.luzzymeow.luzzyrp.data.store

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY createdAt ASC, uuid ASC")
    suspend fun all(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE uuid = :uuid")
    suspend fun byId(uuid: String): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterEntity)

    @Query("DELETE FROM characters WHERE uuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("DELETE FROM characters")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int
}

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches WHERE characterUuid = :characterUuid ORDER BY isMain DESC, createdAt ASC")
    suspend fun of(characterUuid: String): List<BranchEntity>

    /**
     * 一次取全部角色全部分支（总览用）。
     *
     * 为什么不做成「每个角色查一次」：总览要为每张卡取分支，N 张卡就是 N 次挂起查询。
     * 实测 90 条会话时 `overview()` 因此高达 332ms（见 `PerfProfileTest` 基线）——
     * 这里合成 1 次查询，把往返次数与数据集规模解耦。
     */
    @Query("SELECT * FROM branches ORDER BY characterUuid ASC, isMain DESC, createdAt ASC")
    suspend fun all(): List<BranchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BranchEntity>)

    @Query("DELETE FROM branches WHERE characterUuid = :characterUuid")
    suspend fun deleteOf(characterUuid: String)

    @Query("DELETE FROM branches")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(entity: BranchMetaEntity)

    @Query("SELECT * FROM branch_meta WHERE characterUuid = :characterUuid")
    suspend fun meta(characterUuid: String): BranchMetaEntity?

    @Query("DELETE FROM branch_meta")
    suspend fun clearMeta()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE scopeId = :scopeId ORDER BY sortIndex ASC")
    suspend fun of(scopeId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE scopeId = :scopeId")
    suspend fun countOf(scopeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MessageEntity>)

    /**
     * 追加一条（发送、流式生成收尾用）。
     *
     * 单独给一个方法是为了让「追加」在代码里显式可见——这是唯一的高频写路径，
     * 也是选 Room 而非「整段会话一列 JSON」的理由（见 [MessageEntity] 的说明）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun append(entity: MessageEntity)

    /**
     * 就地改写正文。**只动 content 列**：payload（旧结构多余字段，如 isSelf / avatar /
     * imageAttachments）原样保留——这是「按行更新」相对「整段删了重插」的关键好处。
     */
    @Query("UPDATE messages SET content = :content WHERE scopeId = :scopeId AND sortIndex = :sortIndex")
    suspend fun updateContent(scopeId: String, sortIndex: Int, content: String)

    @Query("DELETE FROM messages WHERE scopeId = :scopeId AND sortIndex = :sortIndex")
    suspend fun deleteAt(scopeId: String, sortIndex: Int)

    @Query("DELETE FROM messages WHERE scopeId = :scopeId AND sortIndex >= :fromIndex")
    suspend fun deleteFrom(scopeId: String, fromIndex: Int)

    @Query("DELETE FROM messages WHERE scopeId = :scopeId")
    suspend fun deleteScope(scopeId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Query("SELECT DISTINCT scopeId FROM messages")
    suspend fun scopes(): List<String>

    /**
     * **一次**取全部会话的（条数 + 末条正文 + 末条用户发言）——总览页的唯一数据源。
     *
     * 为什么是一条 SQL 而不是「每条会话查 3 次」：后者是 3N 次挂起查询，
     * 实测 90 条会话 = 332ms（`PerfProfileTest` 基线），而这里的写法是**常数次**查询：
     * 内层 `GROUP BY` 取条数与末条下标，两个 `LEFT JOIN` 靠 `scopeId` 索引把末条正文取回来。
     * 全部是 SQLite 3.18（API 26 自带）就支持的能力，**没用窗口函数**。
     *
     * **只数真正的消息**（A6）：`messages` 表里还住着**尾部快照**行
     * （`role = 'snapshot'`，见 `ChatSessionRepository.ROLE_SNAPSHOT`）——它是给模型的运行时
     * 上下文，不是对话内容。若算进 `COUNT(*)`，总览页的「N 条」会凭空多出约一倍；
     * 而 `lastContent` 若取到快照行，预览会显示 `Current runtime context…` 这种给人看的噪声。
     * 所以条数与末条下标都按 `role IN ('user','assistant')` 过滤；
     * `lastUserContent` 因为写的就是 `role = 'user'`，天然不受影响。
     */
    @Query(
        """
        SELECT c.scopeId AS scopeId,
               c.n AS messageCount,
               la.content AS lastContent,
               lu.content AS lastUserContent
        FROM (SELECT scopeId AS scopeId,
                     SUM(CASE WHEN role IN ('user','assistant') THEN 1 ELSE 0 END) AS n,
                     MAX(CASE WHEN role IN ('user','assistant') THEN sortIndex ELSE -1 END) AS lastIdx
              FROM messages GROUP BY scopeId) c
        LEFT JOIN messages la ON la.scopeId = c.scopeId AND la.sortIndex = c.lastIdx
        LEFT JOIN (SELECT scopeId AS scopeId, MAX(sortIndex) AS userIdx
                   FROM messages WHERE role = 'user' GROUP BY scopeId) u
               ON u.scopeId = c.scopeId
        LEFT JOIN messages lu ON lu.scopeId = u.scopeId AND lu.sortIndex = u.userIdx
        """,
    )
    suspend fun scopeStats(): List<ScopeStats>

    /**
     * 某作用域最后一条的正文（会话总览的预览行）。
     *
     * 单独给一条查询而不是「取全部再取末项」：重度用户的单段会话可达上万条，
     * 总览页要为每个分支各取一次——把它做成 `ORDER BY ... DESC LIMIT 1` 是 O(1) 级，
     * 取全部就是每次翻页都在搬几 MB。
     */
    @Query("SELECT content FROM messages WHERE scopeId = :scopeId ORDER BY sortIndex DESC LIMIT 1")
    suspend fun lastContent(scopeId: String): String?

    /**
     * 最后一条**用户**发言（会话总览的预览行）。
     *
     * 与 [lastContent] 分开是**设计决定**（用户 2026-09-13 拍板）：目录式读法里，
     * 人记得住的通常是自己说过的话，而且这样能绕开「模型输出里的脏前缀」
     * （真实数据里有一条末条正文以 `<thinking>` 开头）。
     */
    @Query("SELECT content FROM messages WHERE scopeId = :scopeId AND role = 'user' ORDER BY sortIndex DESC LIMIT 1")
    suspend fun lastUserContent(scopeId: String): String?
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE scopeId = :scopeId AND kind = :kind ORDER BY sortIndex ASC")
    suspend fun of(scopeId: String, kind: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MemoryEntity>)

    @Query("DELETE FROM memories WHERE scopeId = :scopeId AND kind = :kind")
    suspend fun deleteGroup(scopeId: String, kind: String)

    @Query("DELETE FROM memories")
    suspend fun clear()
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE kind = :kind AND owner = :owner ORDER BY slot ASC")
    suspend fun of(kind: String, owner: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE kind = :kind ORDER BY owner ASC, slot ASC")
    suspend fun ofKind(kind: String): List<RecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RecordEntity>)

    @Query("DELETE FROM records WHERE kind = :kind AND owner = :owner")
    suspend fun deleteGroup(kind: String, owner: String)

    @Query("DELETE FROM records")
    suspend fun clear()
}

@Dao
interface KvDao {
    @Query("SELECT value FROM kv WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: KvEntity)

    @Query("DELETE FROM kv WHERE key = :key")
    suspend fun remove(key: String)

    @Query("SELECT key FROM kv")
    suspend fun keys(): List<String>

    @Query("DELETE FROM kv")
    suspend fun clear()
}

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun count(): Int

    @Query("DELETE FROM attachments")
    suspend fun clear()
}

/**
 * 新数据层的数据库（P4-B）。
 *
 * 迁移与业务写入都在**事务**里做（`RoomDatabase.withTransaction`，见 [LuzzyStore] 与
 * [MigrationWriter]），避免「删了一半、插了一半」的半套数据。
 *
 * `version = 1`：这是**全新**的库，与旧 WebView 的 IndexedDB 无关；
 * 旧数据通过迁移通道（导出 → 迁移器 → [MigrationWriter]）进入，不依赖 Room 的迁移能力。
 */
@Database(
    entities = [
        CharacterEntity::class,
        BranchEntity::class,
        BranchMetaEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        RecordEntity::class,
        KvEntity::class,
        AttachmentEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LuzzyDatabase : RoomDatabase() {
    abstract fun characters(): CharacterDao
    abstract fun branches(): BranchDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun records(): RecordDao
    abstract fun kv(): KvDao
    abstract fun attachments(): AttachmentDao

    companion object {
        const val NAME = "luzzy.db"
    }
}

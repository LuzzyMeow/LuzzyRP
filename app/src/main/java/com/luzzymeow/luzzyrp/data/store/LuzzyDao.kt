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

    @Query("DELETE FROM messages WHERE scopeId = :scopeId AND sortIndex >= :fromIndex")
    suspend fun deleteFrom(scopeId: String, fromIndex: Int)

    @Query("DELETE FROM messages WHERE scopeId = :scopeId")
    suspend fun deleteScope(scopeId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Query("SELECT DISTINCT scopeId FROM messages")
    suspend fun scopes(): List<String>
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

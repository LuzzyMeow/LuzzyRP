package com.luzzymeow.luzzyrp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luzzymeow.luzzyrp.data.db.dao.CharacterCardDao
import com.luzzymeow.luzzyrp.data.db.dao.ConversationDao
import com.luzzymeow.luzzyrp.data.db.dao.FavoriteDao
import com.luzzymeow.luzzyrp.data.db.dao.MemoryDao
import com.luzzymeow.luzzyrp.data.db.dao.MessageNodeDao
import com.luzzymeow.luzzyrp.data.db.dao.RegexDao
import com.luzzymeow.luzzyrp.data.db.dao.SummaryDao
import com.luzzymeow.luzzyrp.data.db.dao.WorldbookDao
import com.luzzymeow.luzzyrp.data.db.entity.CharacterCardEntity
import com.luzzymeow.luzzyrp.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.data.db.entity.FavoriteEntity
import com.luzzymeow.luzzyrp.data.db.entity.MemoryEntity
import com.luzzymeow.luzzyrp.data.db.entity.MessageNodeEntity
import com.luzzymeow.luzzyrp.data.db.entity.PromptPresetEntity
import com.luzzymeow.luzzyrp.data.db.entity.RegexScriptEntity
import com.luzzymeow.luzzyrp.data.db.entity.SummaryEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntryEntity

/**
 * [INVARIANT-DB] LuzzyRP 数据库。
 *
 * - v0.1.0 自版本 1 起步（绿地项目不伪造迁移历史）；schema 导出至 app/schemas/，
 *   后续所有 schema 变更必须使用 AutoMigration（复杂搬运用 MigrationSpec），
 *   保证用户数据升级安全。
 * - sqlite-vec 虚表（memory_vec / worldbook_vec / summary_vec）在 onOpen 回调
 *   以 raw SQL 创建（Room 不映射虚表），见 [VectorTables]。
 */
@Database(
    version = 2,
    exportSchema = true,
    entities = [
        ConversationEntity::class,
        PromptPresetEntity::class,
        MessageNodeEntity::class,
        CharacterCardEntity::class,
        WorldbookEntity::class,
        WorldbookEntryEntity::class,
        RegexScriptEntity::class,
        MemoryEntity::class,
        SummaryEntity::class,
        FavoriteEntity::class,
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageNodeDao(): MessageNodeDao
    abstract fun characterCardDao(): CharacterCardDao
    abstract fun worldbookDao(): WorldbookDao
    abstract fun regexDao(): RegexDao
    abstract fun memoryDao(): MemoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun promptPresetDao(): com.luzzymeow.luzzyrp.data.db.dao.PromptPresetDao

    companion object {

        /**
         * v1 → v2（2026-08-30）：
         *   - worldbook_entries 增列 depthRole（@Depth 注入角色，默认 SYSTEM）
         *   - 新表 prompt_presets（提示词预设）
         * v1 发布于 v0.1.x，schema JSON 未曾导出，故手写 SQL 迁移（不破坏用户数据）。
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE worldbook_entries ADD COLUMN depthRole TEXT NOT NULL DEFAULT 'SYSTEM'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS prompt_presets (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "entriesJson TEXT NOT NULL, " +
                        "builtin INTEGER NOT NULL, " +
                        "readonly INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "luzzy.db")
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .addCallback(VectorTables.callback())
                .build()
    }
}

/**
 * sqlite-vec 向量虚表集合（记忆/世界书/摘要三类嵌入检索）。
 * vec0 虚表在数据库打开时惰性创建；embedding 维度按行写入（vec0 支持变维），
 * 检索时限定与查询向量同维。
 */
object VectorTables {

    private const val MEMORY_VEC = """
        CREATE VIRTUAL TABLE IF NOT EXISTS memory_vec USING vec0(
            rowid INTEGER PRIMARY KEY,
            memory_id TEXT,
            embedding FLOAT[1]
        )
    """

    private const val WORLDBOOK_VEC = """
        CREATE VIRTUAL TABLE IF NOT EXISTS worldbook_vec USING vec0(
            rowid INTEGER PRIMARY KEY,
            entry_id TEXT,
            embedding FLOAT[1]
        )
    """

    private const val SUMMARY_VEC = """
        CREATE VIRTUAL TABLE IF NOT EXISTS summary_vec USING vec0(
            rowid INTEGER PRIMARY KEY,
            summary_id TEXT,
            conversation_id TEXT,
            level TEXT,
            embedding FLOAT[1]
        )
    """

    fun statements(): List<String> = listOf(MEMORY_VEC, WORLDBOOK_VEC, SUMMARY_VEC)

    fun callback() = object : androidx.room.RoomDatabase.Callback() {
        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onOpen(db)
            // sqlite-vec 扩展由 ai.sqlite:vector 自动向 SQLiteDatabase 注入
            statements().forEach { sql ->
                // 变维占位（FLOAT[1]）仅保证建表成功；实际维度在写入时由仓库层
                // 重建对应虚表（drop + create with 正确维度）
                runCatching { db.execSQL(sql) }
            }
        }
    }

    /** 以指定嵌入维度重建虚表（首次写入真实向量时调用）。 */
    fun rebuildWithDimension(db: androidx.sqlite.db.SupportSQLiteDatabase, dimension: Int) {
        listOf(
            "memory_vec" to "CREATE VIRTUAL TABLE IF NOT EXISTS memory_vec USING vec0(memory_id TEXT, embedding FLOAT[$dimension])",
            "worldbook_vec" to "CREATE VIRTUAL TABLE IF NOT EXISTS worldbook_vec USING vec0(entry_id TEXT, embedding FLOAT[$dimension])",
            "summary_vec" to "CREATE VIRTUAL TABLE IF NOT EXISTS summary_vec USING vec0(summary_id TEXT, conversation_id TEXT, level TEXT, embedding FLOAT[$dimension])",
        ).forEach { (table, createSql) ->
            db.execSQL("DROP TABLE IF EXISTS $table")
            db.execSQL(createSql)
        }
    }
}

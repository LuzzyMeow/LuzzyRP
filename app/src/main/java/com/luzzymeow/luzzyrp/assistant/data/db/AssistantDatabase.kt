package com.luzzymeow.luzzyrp.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.dao.AssistantDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.ConversationDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.McpBindingDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.McpServerDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.MemoryDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.MessageDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.MessageFtsDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.SkillBindingDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.SkillDao
import com.luzzymeow.luzzyrp.assistant.data.db.dao.ToolAuditDao
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpServerEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MemoryEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MessageEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MessageFtsEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.ToolAuditEntity
import java.io.File

/** 助手库版本（version 1，PLAN §4.1；无迁移）。 */
private const val ASSISTANT_DB_VERSION = 1

/**
 * 助手数据库（PLAN v1.5.0 §4.1 十表 + FTS4 镜像）。
 *
 * - 落盘位置按 PLAN §4.2：filesDir/assistant/db/assistant.db（不是默认 databases/ 目录）；
 * - exportSchema = true：schema 导出到 app/schemas/.../1.json，入库便于版本 diff；
 * - **不使用 fallbackToDestructiveMigration**：version 1 无迁移，将来加表必须写 Migration，
 *   否则升级会抛 IllegalStateException（宁可在开发期炸掉，也不能静默清库）；
 * - FTS 同步触发器由 AssistantFtsCallback 在每次打开时确保存在（幂等）。
 *
 * 本类不含任何密钥字段；密钥走独立加密区（见 prefs/SecretStore）。
 */
@Database(
    entities = [
        AssistantEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        MemoryEntity::class,
        SkillEntity::class,
        SkillBindingEntity::class,
        McpServerEntity::class,
        McpBindingEntity::class,
        ToolAuditEntity::class,
    ],
    version = ASSISTANT_DB_VERSION,
    exportSchema = true,
)
abstract class AssistantDatabase : RoomDatabase() {

    abstract fun assistantDao(): AssistantDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun messageFtsDao(): MessageFtsDao
    abstract fun memoryDao(): MemoryDao
    abstract fun skillDao(): SkillDao
    abstract fun skillBindingDao(): SkillBindingDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun mcpBindingDao(): McpBindingDao
    abstract fun toolAuditDao(): ToolAuditDao

    companion object {
        const val VERSION: Int = ASSISTANT_DB_VERSION
        const val DB_FILE_NAME: String = "assistant.db"

        /** PLAN §4.2：filesDir/assistant/db */
        const val DB_DIR_RELATIVE: String = "assistant/db"

        /** 数据库文件的绝对路径（filesDir/assistant/db/assistant.db）。 */
        fun databaseFile(context: Context): File =
            File(File(context.applicationContext.filesDir, DB_DIR_RELATIVE), DB_FILE_NAME)

        /**
         * 构建数据库实例（线程安全由 AssistantDatabaseProvider 保证）。
         *
         * 传绝对路径给 Room：Android 的 Context.getDatabasePath 支持绝对路径，
         * 从而把库落在 PLAN §4.2 规定的 filesDir/assistant/db 下。
         */
        fun build(context: Context): AssistantDatabase {
            val appContext = context.applicationContext
            val dbFile = databaseFile(appContext)
            dbFile.parentFile?.mkdirs()
            return Room.databaseBuilder(appContext, AssistantDatabase::class.java, dbFile.absolutePath)
                .addCallback(AssistantFtsCallback())
                .build()
        }
    }
}

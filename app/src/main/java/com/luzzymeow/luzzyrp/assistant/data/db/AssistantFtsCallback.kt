package com.luzzymeow.luzzyrp.assistant.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * message_fts 的维护回调（PLAN §4.1 末段；中文方案见 CjkBigram）。
 *
 * 演进说明（2026-09-09 拍板后）：
 * - FTS 写入**不再由触发器负责**——触发器内无法计算中文 bigram，且会与 DAO 事务双写；
 *   现在唯一写入口是 MessageDao.insertIndexed / updateIndexed / deleteIndexed 等事务方法；
 * - 打开数据库时 DROP 掉早期版本的 AFTER INSERT/UPDATE/DELETE 触发器（升级兜底，幂等）；
 * - 保留会话标题同步触发器：会话重命名后刷新 FTS 里已有行的 title 快照（不涉及 bigram）。
 *
 * 索引漂移兜底：MessageFtsIndexer.indexMissing / rebuildAll（崩溃或历史数据修复）。
 */
internal class AssistantFtsCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(SQL_DROP_TRIGGER_MESSAGE_FTS_INSERT)
        db.execSQL(SQL_DROP_TRIGGER_MESSAGE_FTS_DELETE)
        db.execSQL(SQL_DROP_TRIGGER_MESSAGE_FTS_UPDATE)
        db.execSQL(SQL_CREATE_TRIGGER_CONVERSATION_TITLE)
    }

    companion object {
        const val SQL_DROP_TRIGGER_MESSAGE_FTS_INSERT: String = "DROP TRIGGER IF EXISTS message_fts_ai"
        const val SQL_DROP_TRIGGER_MESSAGE_FTS_DELETE: String = "DROP TRIGGER IF EXISTS message_fts_ad"
        const val SQL_DROP_TRIGGER_MESSAGE_FTS_UPDATE: String = "DROP TRIGGER IF EXISTS message_fts_au"

        /** 会话重命名时同步刷新 FTS 里的标题快照（否则标题检索会命中旧标题）。 */
        const val SQL_CREATE_TRIGGER_CONVERSATION_TITLE: String = """
            CREATE TRIGGER IF NOT EXISTS conversation_title_au AFTER UPDATE OF title ON conversation BEGIN
                UPDATE message_fts SET title = NEW.title
                WHERE rowid IN (SELECT rowid FROM message WHERE conversationId = NEW.id);
            END
        """
    }
}

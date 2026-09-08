package com.luzzymeow.luzzyrp.assistant.data.db

import androidx.room.withTransaction
import com.luzzymeow.luzzyrp.assistant.data.db.dao.CjkBigram

/**
 * message_fts 索引维护（PLAN §4.1；中文 bigram 方案）。
 *
 * 用途：
 * - [indexMissing]：崩溃 / 历史数据导致 message 有行而 FTS 缺行时补建（幂等，可反复调用）；
 * - [rebuildAll]：清空后全量重建（数据导入 / 迁移 / 方案升级后用）。
 *
 * search_text 由 CjkBigram.searchText 纯函数生成，同一正文永远得到同一索引值，
 * 因此重建幂等（重复执行结果完全一致）。分批写入，每批一个事务，避免长事务锁库。
 */
class MessageFtsIndexer(private val database: AssistantDatabase) {

    /** 补建缺失行；返回本次补建条数。 */
    suspend fun indexMissing(pageSize: Int = DEFAULT_PAGE_SIZE): Int {
        require(pageSize > 0) { "pageSize 必须为正数" }
        val ftsDao = database.messageFtsDao()
        val messageDao = database.messageDao()
        var indexed = 0
        while (true) {
            val page = ftsDao.messageContentMissing(pageSize)
            if (page.isEmpty()) break
            database.withTransaction {
                for (row in page) {
                    messageDao.insertFtsRow(row.id, CjkBigram.searchText(row.content))
                }
            }
            indexed += page.size
        }
        return indexed
    }

    /** 清空后全量重建；返回重建条数。 */
    suspend fun rebuildAll(pageSize: Int = DEFAULT_PAGE_SIZE): Int {
        require(pageSize > 0) { "pageSize 必须为正数" }
        val ftsDao = database.messageFtsDao()
        val messageDao = database.messageDao()
        ftsDao.clear()
        var indexed = 0
        var offset = 0
        while (true) {
            val page = ftsDao.messageContentPage(pageSize, offset)
            if (page.isEmpty()) break
            database.withTransaction {
                for (row in page) {
                    messageDao.insertFtsRow(row.id, CjkBigram.searchText(row.content))
                }
            }
            indexed += page.size
            offset += page.size
        }
        return indexed
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 500
    }
}

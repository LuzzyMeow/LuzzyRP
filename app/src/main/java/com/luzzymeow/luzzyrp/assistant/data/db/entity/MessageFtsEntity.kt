package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * message 的 FTS4 关键词检索镜像表（PLAN §4.1 末段 + 派发者 2026-09-09 拍板的中文方案）。
 *
 * 方案说明：
 * - **FTS4 而非 FTS5**：FTS4 由 Android 自带 SQLite 提供（API 26+ 全量可用），FTS5 需 API 30+ 才默认编译；
 * - **独立虚表 + DAO 事务同步**：Room 的 @Fts4(contentEntity = ...) 要求 FTS 列全部存在于内容实体
 *   （message 没有 title / search_text 列），故用独立虚表；写入由 MessageDao 的
 *   insertIndexed / updateIndexed / deleteIndexed 在**同一事务**内完成（rowid 对应 message 隐式 rowid）。
 *   早期版本的 AFTER INSERT/UPDATE/DELETE 触发器已移除（触发器内无法计算 bigram，会与 DAO 双写）；
 *   AssistantFtsCallback 仍会在打开时 DROP 掉旧触发器（升级兜底）并维护会话标题同步触发器。
 * - content = 消息正文；title = 会话标题快照；**search_text = 正文 + 中文 bigram / 小写拉丁 token**
 *   （见 CjkBigram）——unicode61 无中文分词，bigram 才能召回中文；
 * - 单字中文查询由 MessageFtsDao.searchFallback 的 LIKE 回落兜底。
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "message_fts")
data class MessageFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0L,
    /** 消息正文 */
    val content: String,
    /** 会话标题快照 */
    val title: String,
    /** 正文 + bigram / 小写拉丁 token（CjkBigram.searchText 生成） */
    @ColumnInfo(name = "search_text")
    val searchText: String,
)

package com.luzzymeow.luzzyrp.assistant.data.db.dao

/** message 的 id + content 投影（重建 / 补建 FTS 索引用，见 MessageFtsIndexer）。 */
data class MessageContentRow(
    val id: String,
    val content: String,
)

package com.luzzymeow.luzzyrp.assistant.data.db.dao

/**
 * 会话按天聚合投影（PLAN §6.2 按日期分组：今天 / 昨天 / 7 天内 / 本月 / 更早）。
 *
 * day 为本地时区的 yyyy-MM-dd（SQLite strftime 生成），分组语义在 UI 侧按当前日期换算。
 */
data class ConversationDayBucket(
    val day: String,
    val count: Int,
)

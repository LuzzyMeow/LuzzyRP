package com.luzzymeow.luzzyrp.assistant.data.db.dao

/**
 * LIKE 通配符转义（FTS 无命中时的回落检索，见 MessageFtsDao.searchFallback）。
 *
 * 用户输入里的 % / _ / \ 必须转义，否则会被当成通配符（% 命中一切、_ 命中任意单字符）。
 * 顺序固定：先转义反斜杠自身，再转义 % 与 _；SQL 侧用 ESCAPE '\' 声明转义符。
 */
object LikeEscaper {

    const val ESCAPE_CHAR: Char = '\\'

    fun escape(keyword: String): String {
        if (keyword.isEmpty()) return keyword
        val builder = StringBuilder(keyword.length + 8)
        for (ch in keyword) {
            if (ch == ESCAPE_CHAR || ch == '%' || ch == '_') builder.append(ESCAPE_CHAR)
            builder.append(ch)
        }
        return builder.toString()
    }
}

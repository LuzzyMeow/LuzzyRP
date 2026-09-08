package com.luzzymeow.luzzyrp.assistant.data.db.dao

import org.junit.Assert.assertEquals
import org.junit.Test

/** LikeEscaper 单测：% / _ / \ 必须转义，否则回落检索会被通配符污染。 */
class LikeEscaperTest {

    @Test
    fun escapesPercentUnderscoreAndBackslash() {
        assertEquals("100\\%\\_\\\\", LikeEscaper.escape("100%_\\"))
    }

    @Test
    fun leavesPlainTextUntouched() {
        assertEquals("报告 2024", LikeEscaper.escape("报告 2024"))
        assertEquals("hello world", LikeEscaper.escape("hello world"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", LikeEscaper.escape(""))
    }

    @Test
    fun escapeCharIsBackslash() {
        assertEquals('\\', LikeEscaper.ESCAPE_CHAR)
    }
}

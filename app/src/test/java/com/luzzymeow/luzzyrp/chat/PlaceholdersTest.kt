package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 占位符替换单测。
 *
 * 关键不是「能不能替换」，而是三条**边界**：
 * 名字为空时**保留占位符**（别替换成空白）、只认那两种占位符（别误伤 `{{其他}}`）、
 * 以及大小写/空格容忍（真实数据里模型写的形态不统一）。
 */
class PlaceholdersTest {

    @Test
    fun `替换 char 与 user`() {
        assertEquals(
            "Vanio 把手抬起来，看向林澈。",
            Placeholders.render("{{char}} 把手抬起来，看向{{user}}。", "Vanio", "林澈"),
        )
    }

    @Test
    fun `大小写与空格容忍`() {
        assertEquals("Vanio", Placeholders.render("{{ CHAR }}", "Vanio", "林澈"))
        assertEquals("Vanio", Placeholders.render("{{Char}}", "Vanio", "林澈"))
        assertEquals("林澈", Placeholders.render("{{\tuser\n}}", "Vanio", "林澈"))
    }

    @Test
    fun `名字为空时保留占位符而不是替换成空白`() {
        // 宁可不替换，也不要让正文出现「洞」
        assertEquals("{{char}}说", Placeholders.render("{{char}}说", "", "林澈"))
        assertEquals("{{user}}说", Placeholders.render("{{user}}说", "Vanio", ""))
    }

    @Test
    fun `其它花括号内容不受影响`() {
        assertEquals("{{other}}", Placeholders.render("{{other}}", "Vanio", "林澈"))
        assertEquals("{\"a\":1}", Placeholders.render("{\"a\":1}", "Vanio", "林澈"))
        // `{{characters}}` 不是占位符，必须原样保留（正则用了词尾 `}}` 而不是前缀匹配）
        assertEquals("{{characters}}x", Placeholders.render("{{characters}}x", "Vanio", "林澈"))
    }

    @Test
    fun `重复出现全部替换`() {
        assertEquals("V 和 V 和 V", Placeholders.render("{{char}} 和 {{char}} 和 {{char}}", "V", "林澈"))
    }

    @Test
    fun `没有占位符时原样返回（且不做多余拷贝）`() {
        val plain = "一段普通正文"
        assertTrue(Placeholders.render(plain, "V", "林澈") === plain)
        assertEquals("", Placeholders.render("", "V", "林澈"))
    }
}

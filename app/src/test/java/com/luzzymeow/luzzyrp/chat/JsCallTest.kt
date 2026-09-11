package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChatJsCall] 单测：原生 → JS 的调用串拼装。
 *
 * 这是**唯一**把模型正文拼进 JS 源码的地方，转义错一个字符就是页面级语法错误，
 * 所以逐字符断言。
 */
class JsCallTest {

    @Test
    fun `普通字符串原样包进单引号`() {
        assertEquals("'abc'", ChatJsCall.jsStringLiteral("abc"))
        assertEquals("''", ChatJsCall.jsStringLiteral(""))
    }

    @Test
    fun `引号与反斜杠被转义`() {
        assertEquals("""'a\'b'""", ChatJsCall.jsStringLiteral("a'b"))
        assertEquals("""'a\\b'""", ChatJsCall.jsStringLiteral("""a\b"""))
        assertEquals("""'{"a":1}'""", ChatJsCall.jsStringLiteral("""{"a":1}"""))
    }

    @Test
    fun `控制字符转义成转义序列`() {
        assertEquals("""'a\nb'""", ChatJsCall.jsStringLiteral("a\nb"))
        assertEquals("""'a\rb'""", ChatJsCall.jsStringLiteral("a\rb"))
        assertEquals("""'a\tb'""", ChatJsCall.jsStringLiteral("a\tb"))
    }

    @Test
    fun `行分隔符被转义（旧 WebView 会把它当换行劈断字符串）`() {
        assertEquals("""'a\u2028b'""", ChatJsCall.jsStringLiteral("a\u2028b"))
        assertEquals("""'a\u2029b'""", ChatJsCall.jsStringLiteral("a\u2029b"))
    }

    @Test
    fun `中文与 emoji 原样保留`() {
        assertEquals("'你好🐱'", ChatJsCall.jsStringLiteral("你好🐱"))
    }

    @Test
    fun `onEvent 调用串包含存在性检测与两个转义参数`() {
        val js = ChatJsCall.onEvent("j-1", """{"type":"delta","content":"a'b"}""")
        assertTrue(js.startsWith("try{"))
        assertTrue(js.endsWith("}catch(e){}"))
        assertTrue(js.contains("window.Luzzy&&window.Luzzy.chatNative"))
        assertTrue(js.contains("typeof b.onEvent==='function'"))
        // jobId 与事件串都按字面量转义：事件串里的双引号必须原样出现（不是被转义成 \"）
        assertTrue(js.contains("b.onEvent('j-1','{\"type\":\"delta\",\"content\":\"a\\'b\"}')"))
    }

    @Test
    fun `onEvent 对含换行的正文仍然语法安全`() {
        val js = ChatJsCall.onEvent("j", "line1\nline2")
        assertTrue(js.contains("""'line1\nline2'"""))
        assertTrue(!js.contains("line1\nline2"))
    }

    @Test
    fun `jobId 里的引号不会逃逸出字符串`() {
        val js = ChatJsCall.onEvent("j');alert(1);//", "{}")
        assertTrue(js.contains("""'j\');alert(1);//'"""))
        assertTrue(!js.contains("b.onEvent('j');alert"))
    }
}

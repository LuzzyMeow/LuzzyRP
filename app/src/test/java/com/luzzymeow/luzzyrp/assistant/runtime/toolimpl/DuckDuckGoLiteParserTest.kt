package com.luzzymeow.luzzyrp.assistant.runtime.toolimpl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DuckDuckGo Lite HTML 解析单测（宽容解析：抽不到就空，绝不抛）。 */
class DuckDuckGoLiteParserTest {

    private val html = """
        <html><body>
        <table>
          <tr><td><a rel="nofollow" href="https://kotlinlang.org/">Kotlin Programming Language</a></td></tr>
          <tr><td class="result-snippet">Kotlin 是一门现代静态类型语言 &amp; 多平台。</td></tr>
          <tr><td><a rel="nofollow" href="https://duckduckgo.com/y.js?ad">广告</a></td></tr>
          <tr><td><a rel="nofollow" href="https://github.com/JetBrains/kotlin">JetBrains/kotlin</a></td></tr>
          <tr><td class="result-snippet">Kotlin 编译器与标准库源码。</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析标题链接与摘要`() {
        val results = DuckDuckGoLiteParser.parse(html)
        assertEquals(2, results.size)
        assertEquals("Kotlin Programming Language", results[0].title)
        assertEquals("https://kotlinlang.org/", results[0].url)
        assertTrue(results[0].snippet.contains("多平台"))
        assertEquals("JetBrains/kotlin", results[1].title)
        assertTrue(results[1].snippet.contains("编译器"))
    }

    @Test
    fun `跳过引擎自身链接`() {
        val results = DuckDuckGoLiteParser.parse(html)
        assertTrue(results.none { it.url.contains("duckduckgo.com") })
    }

    @Test
    fun `HTML 实体被反转义且标签被剥离`() {
        val results = DuckDuckGoLiteParser.parse(html)
        assertTrue(results[0].snippet.contains("&"))
        assertTrue(!results[0].title.contains("<"))
    }

    @Test
    fun `空或无关 HTML 返回空列表不抛`() {
        assertTrue(DuckDuckGoLiteParser.parse("").isEmpty())
        assertTrue(DuckDuckGoLiteParser.parse("<html><body>no results</body></html>").isEmpty())
    }
}

package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **HTML 分段**的门禁（HTML 直通渲染的入口判据）。
 *
 * 用例形状全部取自真实输入：用户真机那条「散文 + 行内 `<div>` 铭文卡片」是主用例，
 * 其余三条对应上游 `runtime-services.js` 的三条 HTML 通道。
 */
class HtmlBlocksTest {

    /** 真机实测的形状：散文在前，`<div style=…>` 卡片嵌在同一段里。 */
    private val realMessage = "原本只是渗光的刻痕齐齐亮起，一层一层地往上传，悬在他和你中间。 " +
        """<div style="background:#0d1416;color:#bfe9e4;padding:18px;">""" +
        """<div style="color:#7df8cf;">PÆRMIΓ</div>""" +
        """<div>[一] 此并不通天，纳万物之影。</div></div>"""

    @Test
    fun `没有 HTML 时是单段——普通消息零成本`() {
        val segments = HtmlBlocks.segments("**正文** 一句 *动作*。")
        assertEquals(1, segments.size)
        assertTrue(segments.single() is MessageSegment.Markdown)
        assertFalse(HtmlBlocks.hasHtml("**正文** 一句 *动作*。"))
    }

    @Test
    fun `散文中间的 div 卡片被切成 HTML 段`() {
        val segments = HtmlBlocks.segments(realMessage)
        assertEquals("应当切成 散文 / 卡片 两段", 2, segments.size)
        assertTrue(segments[0] is MessageSegment.Markdown)
        val html = segments[1] as MessageSegment.Html
        assertTrue("卡片从 <div 开始", html.html.startsWith("<div"))
        assertTrue("卡片到配平的 </div> 结束", html.html.endsWith("</div>"))
        assertTrue("嵌套的 div 必须一起带走", html.html.contains("PÆRMIΓ"))
        assertTrue(HtmlBlocks.hasHtml(realMessage))
    }

    @Test
    fun `整条消息以块级标签开头时整段是 HTML（上游第二条通道）`() {
        val message = """<table><tr><td>状态</td><td>受伤</td></tr></table>"""
        val segments = HtmlBlocks.segments(message)
        assertEquals(1, segments.size)
        assertEquals(message, (segments.single() as MessageSegment.Html).html)
    }

    @Test
    fun `卡片后面还能有散文`() {
        val message = """<div>面板</div>他放下手，看了你一眼。"""
        val segments = HtmlBlocks.segments(message)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is MessageSegment.Html)
        assertEquals("他放下手，看了你一眼。", (segments[1] as MessageSegment.Markdown).text)
    }

    @Test
    fun `配不平的 HTML 不进 HTML 段——流式到一半不变卡片`() {
        val half = """<div style="background:#000;"><div>写到一半"""
        val segments = HtmlBlocks.segments(half)
        assertEquals(1, segments.size)
        assertTrue("闭合标签没到就仍按文本渲染", segments.single() is MessageSegment.Markdown)
        assertFalse(HtmlBlocks.hasHtml(half))
    }

    @Test
    fun `嵌套同名标签要按深度配平`() {
        val message = """<div>外<div>内</div>还是外</div>尾巴"""
        val segments = HtmlBlocks.segments(message)
        assertEquals(2, segments.size)
        val html = (segments[0] as MessageSegment.Html).html
        assertTrue("外层闭合才算完", html.endsWith("还是外</div>"))
        assertEquals("尾巴", (segments[1] as MessageSegment.Markdown).text)
    }

    @Test
    fun `空元素算单标签区域`() {
        val segments = HtmlBlocks.segments("上面<hr>下面")
        assertEquals(3, segments.size)
        assertEquals("<hr>", (segments[1] as MessageSegment.Html).html)
    }

    @Test
    fun `围栏里的 HTML 连围栏一起换成 HTML 段（上游 replaceHtmlCodeBlocks 同义）`() {
        val message = "看这个：\n```html\n<div class=\"card\">卡片</div>\n```\n就这样。"
        val segments = HtmlBlocks.segments(message)
        assertEquals(3, segments.size)
        val html = (segments[1] as MessageSegment.Html).html
        assertTrue(html.startsWith("<div"))
        assertFalse("围栏标记不该留在气泡里", (segments[0] as MessageSegment.Markdown).text.contains("```"))
        assertFalse((segments[2] as MessageSegment.Markdown).text.contains("```"))
    }

    @Test
    fun `普通代码块照旧是 Markdown`() {
        val message = "```kotlin\nval x = 1\n```"
        val segments = HtmlBlocks.segments(message)
        assertEquals(1, segments.size)
        assertTrue(segments.single() is MessageSegment.Markdown)
    }

    @Test
    fun `script 与 iframe 不触发 HTML 段`() {
        assertFalse(HtmlBlocks.hasHtml("""<script>alert(1)</script>"""))
        assertFalse(HtmlBlocks.hasHtml("""<iframe src="https://example.com"></iframe>"""))
    }

    @Test
    fun `分段不丢字符——拼回去与原文相同`() {
        val segments = HtmlBlocks.segments(realMessage)
        val joined = segments.joinToString("") {
            when (it) {
                is MessageSegment.Markdown -> it.text
                is MessageSegment.Html -> it.html
            }
        }
        assertEquals(realMessage, joined)
    }
}

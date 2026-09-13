package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **行内 HTML → 样式片段**（[InlineHtml]）的确定性单测。
 *
 * 这一层是「正则脚本注入了 `<span style="color:…">` 却看不到高亮」的落点：上游由浏览器渲染行内标签，
 * Compose 版必须自己把**标签 + CSS 子集**翻译成结构化片段。用例覆盖三类判据：
 * ① 样式映射对不对（含颜色/字号两种解析）；② 该丢的有没有丢干净（脚本、未知标签的边界）；
 * ③ 畸形写法会不会吞正文（未闭合、多余闭标签、属性名相似）。
 */
class InlineHtmlTest {

    private fun spans(text: String): List<MdSpan> =
        InlineHtml.rewrite(MarkdownParser.parse(text).firstOrNull()?.let { blockSpans(it) }.orEmpty())

    private fun blockSpans(block: MdBlock): List<MdSpan> = when (block) {
        is MdBlock.Paragraph -> block.spans
        is MdBlock.Heading -> block.text
        else -> emptyList()
    }

    /** 走「解析 → 展开」全链（与运行时同一条路径）。 */
    private fun blocks(text: String): List<MdBlock> = InlineHtml.rewriteBlocks(MarkdownParser.parse(text))

    private fun firstHtml(text: String): MdSpan.Html {
        val found = collect(spans(text)).filterIsInstance<MdSpan.Html>()
        assertTrue("期望有样式片段，实际：${spans(text)}", found.isNotEmpty())
        return found.first()
    }

    private fun collect(spans: List<MdSpan>): List<MdSpan> = spans.flatMap { span ->
        listOf(span) + when (span) {
            is MdSpan.Emphasis -> collect(span.spans)
            is MdSpan.Strong -> collect(span.spans)
            is MdSpan.Strike -> collect(span.spans)
            is MdSpan.Html -> collect(span.spans)
            is MdSpan.Link -> collect(span.text)
            is MdSpan.Code, is MdSpan.Text -> emptyList()
        }
    }

    // ────────────────────────── 样式映射

    @Test
    fun `正则脚本注入的内联颜色变成样式片段`() {
        val html = firstHtml("<span style=\"color:#e0a\">「今晚有雨」</span>")
        assertEquals(0xFFEE00AA.toInt(), html.style.color)
        assertEquals("「今晚有雨」", html.spans.plainText())
    }

    @Test
    fun `六位与八位十六进制颜色（八位是 RRGGBBAA）`() {
        assertEquals(0xFFC9A227.toInt(), firstHtml("<span style=\"color:#C9A227\">x</span>").style.color)
        assertEquals(0x80112233.toInt(), firstHtml("<span style=\"color:#11223380\">x</span>").style.color)
    }

    @Test
    fun `rgb 与 rgba 颜色`() {
        assertEquals(0xFF112233.toInt(), firstHtml("<span style=\"color:rgb(17,34,51)\">x</span>").style.color)
        assertEquals(0x7F112233.toInt(), firstHtml("<span style=\"color:rgba(17,34,51,0.5)\">x</span>").style.color)
    }

    @Test
    fun `具名颜色与背景色`() {
        val html = firstHtml("<span style=\"color:tomato;background-color:khaki\">x</span>")
        assertEquals(0xFFFF6347.toInt(), html.style.color)
        assertEquals(0xFFF0E68C.toInt(), html.style.background)
    }

    @Test
    fun `认不出的颜色不塞猜测值，只是不改样式`() {
        // 没有可用的样式 → 不套空壳，正文照常保留
        assertTrue(collect(spans("<span style=\"color:notacolor\">x</span>")).none { it is MdSpan.Html })
        assertEquals("x", spans("<span style=\"color:notacolor\">x</span>").plainText())
    }

    @Test
    fun `字号百分比 像素 em 与关键字`() {
        assertEquals(1.2f, firstHtml("<span style=\"font-size:120%\">x</span>").style.sizeScale)
        assertEquals(1.5f, firstHtml("<span style=\"font-size:24px\">x</span>").style.sizeScale)
        assertEquals(1.3f, firstHtml("<span style=\"font-size:1.3em\">x</span>").style.sizeScale)
        assertEquals(1.2f, firstHtml("<span style=\"font-size:large\">x</span>").style.sizeScale)
    }

    @Test
    fun `字号倍率被夹在安全区间内`() {
        assertEquals(InlineHtml.MAX_SCALE, firstHtml("<span style=\"font-size:800%\">x</span>").style.sizeScale)
        assertEquals(InlineHtml.MIN_SCALE, firstHtml("<span style=\"font-size:10%\">x</span>").style.sizeScale)
    }

    @Test
    fun `font-weight normal 能显式取消外层的加粗`() {
        val html = firstHtml("<b>粗<span style=\"font-weight:normal\">细</span></b>")
        val inner = collect(html.spans).filterIsInstance<MdSpan.Html>().single()
        assertFalse(inner.style.bold!!)
    }

    @Test
    fun `text-decoration 同时给下划线与删除线`() {
        val html = firstHtml("<span style=\"text-decoration:underline line-through\">x</span>")
        assertTrue(html.style.underline!!)
        assertTrue(html.style.strike!!)
    }

    // ────────────────────────── 语义标签

    @Test
    fun `语义标签的固有样式`() {
        assertTrue(firstHtml("<b>x</b>").style.bold!!)
        assertTrue(firstHtml("<strong>x</strong>").style.bold!!)
        assertTrue(firstHtml("<i>x</i>").style.italic!!)
        assertTrue(firstHtml("<u>x</u>").style.underline!!)
        assertTrue(firstHtml("<s>x</s>").style.strike!!)
        assertEquals(0.85f, firstHtml("<small>x</small>").style.sizeScale)
        assertEquals(HtmlBaseline.Sub, firstHtml("<sub>x</sub>").style.baseline)
        assertEquals(HtmlBaseline.Super, firstHtml("<sup>x</sup>").style.baseline)
        assertTrue(firstHtml("<kbd>x</kbd>").style.mono!!)
    }

    @Test
    fun `嵌套标签逐层生效`() {
        val html = firstHtml("<b>粗<i>斜</i></b>")
        assertTrue(html.style.bold!!)
        val inner = collect(html.spans).filterIsInstance<MdSpan.Html>().single()
        assertTrue(inner.style.italic!!)
        assertEquals("粗斜", html.spans.plainText())
    }

    @Test
    fun `未闭合标签自动闭合到片段末尾（不吞正文）`() {
        val html = firstHtml("<span style=\"color:red\">半句话")
        assertEquals("半句话", html.spans.plainText())
    }

    @Test
    fun `多余的闭标签什么都不做`() {
        assertEquals("正文", spans("正文</span>").plainText())
    }

    @Test
    fun `br 变换行`() {
        assertEquals("上\n下", spans("上<br>下").plainText())
        assertEquals("上\n下", spans("上<br/>下").plainText())
    }

    // ────────────────────────── 丢弃 / 保真边界

    @Test
    fun `script 与 style 连内容一起丢（不把代码当正文显示）`() {
        assertEquals("前 后", spans("前 <script>alert('x')</script> 后").plainText().replace("  ", " "))
        assertEquals("a", spans("<style>.x{color:red}</style>a").plainText())
    }

    @Test
    fun `未知标签丢标签留内容`() {
        assertEquals("文字", spans("<foo bar=\"1\">文字</foo>").plainText())
    }

    @Test
    fun `属性名做边界匹配（data-color 不是 color）`() {
        assertTrue(collect(spans("<span data-color=\"red\">x</span>")).none { it is MdSpan.Html })
        assertEquals("x", spans("<span data-color=\"red\">x</span>").plainText())
    }

    @Test
    fun `实体编码的尖括号是字面文本不当作标签`() {
        assertEquals("<b>x</b>", spans("&lt;b&gt;x&lt;/b&gt;").plainText())
    }

    @Test
    fun `行内代码片段里的标签不解析`() {
        // 反引号本身由 Markdown 解析器吃掉（那是它的记号），留给我们的是代码内容
        val spans = spans("看 `<b>x</b>` 哦")
        assertEquals("看 <b>x</b> 哦", spans.plainText())
        assertTrue(spans.any { it is MdSpan.Code })
        assertTrue(collect(spans).none { it is MdSpan.Html })
    }

    @Test
    fun `没有标签时 hasTags 走快速判据`() {
        assertFalse(InlineHtml.hasTags("普通正文，没有标签"))
        assertFalse(InlineHtml.hasTags("数学式 a < b"))
        assertTrue(InlineHtml.hasTags("<span>x</span>"))
    }

    @Test
    fun `Markdown 与行内标签混排时两边都保留`() {
        val blocks = blocks("**粗** 与 <span style=\"color:red\">红</span>")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals("粗 与 红", paragraph.spans.plainText())
        assertTrue(paragraph.spans.any { it is MdSpan.Strong })
        assertTrue(collect(paragraph.spans).any { it is MdSpan.Html })
    }

    @Test
    fun `href 是 http 时变成链接片段`() {
        val link = spans("<a href=\"https://example.com\">点这里</a>").filterIsInstance<MdSpan.Link>().single()
        assertEquals("https://example.com", link.url)
        assertEquals("点这里", link.text.plainText())
    }

    @Test
    fun `href 不是 http 时不生成可点链接`() {
        assertTrue(spans("<a href=\"javascript:alert(1)\">x</a>").none { it is MdSpan.Link })
    }

    @Test
    fun `标题与表格单元格里的标签也会展开`() {
        val heading = blocks("## 标题 <b>加粗</b>").single() as MdBlock.Heading
        assertTrue(collect(heading.text).any { it is MdSpan.Html })

        val table = blocks("| a |\n|---|\n| <b>x</b> |").single() as MdBlock.Table
        assertTrue(table.rows.flatten().flatten().let { collect(it).any { span -> span is MdSpan.Html } })
    }
}

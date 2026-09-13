package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **消息 HTML 清洗**的门禁（安全边界）。
 *
 * 消息 HTML 来自模型 = **不可信输入**，渲染容器是应用里的 WebView。这些用例钉的就是
 * 「不可信内容进渲染器之前必须已经被剥掉执行能力」。
 */
class HtmlSanitizerTest {

    @Test
    fun `脚本标签连同内容一起删掉`() {
        val dirty = """<div>前<script>alert('x')</script>后</div>"""
        val clean = HtmlSanitizer.sanitize(dirty)
        assertFalse("script 标签必须消失", clean.contains("<script", ignoreCase = true))
        assertFalse("脚本正文也不该留下", clean.contains("alert"))
        assertTrue("普通内容不受影响", clean.contains("前后"))
    }

    @Test
    fun `未闭合的脚本也删到结尾——宁可不显示也不放进来`() {
        val clean = HtmlSanitizer.sanitize("""<div>内容<script>var a = 1;""")
        assertFalse(clean.contains("<script", ignoreCase = true))
        assertFalse(clean.contains("var a"))
    }

    @Test
    fun `iframe object embed form 一律删除`() {
        listOf(
            """<iframe src="https://evil.example"></iframe>""",
            """<object data="x.swf"></object>""",
            """<embed src="x.swf">""",
            """<form action="https://evil.example"><input name="a"></form>""",
        ).forEach { dirty ->
            val clean = HtmlSanitizer.sanitize(dirty)
            assertFalse("应被删：$dirty", clean.contains("<iframe") || clean.contains("<object") ||
                clean.contains("<embed") || clean.contains("<form"))
        }
    }

    @Test
    fun `内联事件属性被剥掉`() {
        val clean = HtmlSanitizer.sanitize("""<div onclick="steal()" onmouseover='x()' style="color:red">t</div>""")
        assertFalse(clean.contains("onclick"))
        assertFalse(clean.contains("onmouseover"))
        assertTrue("样式必须保留（卡片的样子来自它）", clean.contains("""style="color:red""""))
    }

    @Test
    fun `脚本 URL 与内联 HTML 文档被剥掉（含实体编码绕过）`() {
        val clean = HtmlSanitizer.sanitize(
            """<a href="javascript:alert(1)">a</a><a href="java&#115;cript:alert(2)">b</a>""" +
                """<a href="JaVaScRiPt:alert(3)">c</a><iframe srcdoc="<b>x</b>"></iframe>""",
        )
        assertFalse(clean.contains("javascript:", ignoreCase = true))
        assertFalse("实体编码的绕过也要拦住", clean.contains("java&#115;cript"))
        assertFalse(clean.contains("srcdoc"))
        assertFalse(HtmlSanitizer.sanitize("""<a href="data:text/html;base64,PHNjcmlwdD4=">x</a>""")
            .contains("text/html"))
    }

    @Test
    fun `样式与结构全部保留——保真与安全在这里不冲突`() {
        val dirty = """<div style="background:#0d1416;border-radius:4px" class="card" id="p1">""" +
            """<span style="color:#7df8cf">PÆRMIΓ</span></div>"""
        val clean = HtmlSanitizer.sanitize(dirty)
        assertTrue(clean.contains("background:#0d1416"))
        assertTrue(clean.contains("border-radius:4px"))
        assertTrue(clean.contains("class=\"card\""))
        assertTrue(clean.contains("id=\"p1\""))
        assertTrue(clean.contains("PÆRMIΓ"))
    }

    @Test
    fun `style 标签保留（CSS 不执行代码）`() {
        val clean = HtmlSanitizer.sanitize("""<style>.card{color:red}</style><div class="card">x</div>""")
        assertTrue(clean.contains("<style>"))
        assertTrue(clean.contains(".card{color:red}"))
    }

    @Test
    fun `闭合标签必须原样保留——不能变成空标签`() {
        // 真机目测抓到的缺陷：`</span>` 被拼成 `<>`，卡片上每行都多一个 `<>`。
        // 纯文本断言（contains）看不见这种破坏，所以这里比的是**整串等价**。
        val html = """<div style="color:red"><span>[一]</span>正文</div>"""
        assertEquals(html, HtmlSanitizer.sanitize(html))
        assertEquals("自闭合标签会被规范成 ` />`（合法写法）", "<br />", HtmlSanitizer.sanitize("<br/>"))
        assertEquals("<div></div>", HtmlSanitizer.sanitize("<div></div>"))
    }

    @Test
    fun `安全链接保留、危险属性照旧剥掉`() {
        val clean = HtmlSanitizer.sanitize("""<a href="https://ok.example" onclick="x()">链接</a>""")
        assertEquals("""<a href="https://ok.example">链接</a>""", clean)
    }

    @Test
    fun `空输入与纯文本不炸`() {
        assertTrue(HtmlSanitizer.sanitize("").isEmpty())
        assertTrue(HtmlSanitizer.sanitize("   ").isEmpty())
        assertTrue(HtmlSanitizer.sanitize("纯文本").contains("纯文本"))
    }

    @Test
    fun `文档包装带禁脚本 CSP 与透明底`() {
        val document = HtmlSanitizer.document("""<div onclick="x()">c</div>""")
        assertTrue("必须有 CSP", document.contains("Content-Security-Policy"))
        assertTrue("脚本源必须为 none", document.contains("script-src"))
        assertTrue(document.contains("default-src 'none'"))
        assertTrue("透明底：卡片自己的背景色才生效", document.contains("background:transparent"))
        assertFalse("事件属性在文档里也必须已剥掉", document.contains("onclick"))
    }

    @Test
    fun `明文兜底会转义`() {
        assertTrue(HtmlSanitizer.escape("<b>&</b>") == "&lt;b&gt;&amp;&lt;/b&gt;")
    }

    // ---------------- 以下四条来自设计门评审（2026-09-14）的必须调整项 ----------------

    @Test
    fun `固定高度与裁剪声明被去掉——不许静默裁掉内容`() {
        val clean = HtmlSanitizer.sanitize(
            """<div style="background:#0d1416;height:120px;max-height:80px;overflow:hidden;color:#bfe9e4">正文</div>""",
        )
        assertFalse("height 会造成静默裁剪", clean.contains("height:120px"))
        assertFalse(clean.contains("max-height"))
        assertFalse(clean.contains("overflow"))
        assertTrue("其余样式必须保留", clean.contains("background:#0d1416"))
        assertTrue(clean.contains("color:#bfe9e4"))
    }

    @Test
    fun `死控件降级或删除——JS 关着时按钮点了没反应`() {
        val clean = HtmlSanitizer.sanitize(
            """<button style="color:red">点我</button><label>标签</label><input type="text" value="x">""",
        )
        assertFalse("button 不该留下（可点但无反应）", clean.contains("<button"))
        assertFalse(clean.contains("<label"))
        assertFalse("输入框直接删", clean.contains("<input"))
        assertTrue("内容与样式降级保留", clean.contains("<span style=\"color:red\">点我</span>"))
        assertTrue(clean.contains("<span>标签</span>"))
    }

    @Test
    fun `文档基线注入主题色 字号 与减少动效`() {
        val document = HtmlSanitizer.document(
            html = "<div>c</div>",
            textColorCss = "#123456",
            linkColorCss = "#654321",
            baseFontSizePx = 17,
            reduceMotion = true,
        )
        assertTrue("主题正文色兜底（深底无 color 的卡片才可读）", document.contains("color:#123456"))
        assertTrue(document.contains("a{color:#654321;}"))
        assertTrue("字号跟随正文基准", document.contains("font-size:17px"))
        assertTrue("系统关动画时卡内动效也要停", document.contains("animation:none !important"))
        assertTrue("同时尊重 prefers-reduced-motion", document.contains("prefers-reduced-motion"))
    }

    @Test
    fun `不要求减少动效时不注入停用声明`() {
        val document = HtmlSanitizer.document(html = "<div>c</div>", reduceMotion = false)
        // 判据落在**通配规则**上：媒体查询那条永远在（那是给系统偏好用的），
        // 这里要区分的是「无条件停用」有没有被注入。
        assertFalse(
            "不关动画时通配规则里不该有停用声明",
            Regex("""\*\{max-width:100%;box-sizing:border-box;animation:none""").containsMatchIn(document),
        )
        assertTrue("但媒体查询始终在", document.contains("@media (prefers-reduced-motion: reduce)"))
    }
}

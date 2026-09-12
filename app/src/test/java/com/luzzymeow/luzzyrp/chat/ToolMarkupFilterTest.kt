package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具调用被写成文本时的协议噪声过滤单测。
 *
 * 样本取自 2026-09-12 真机实测（DeepSeek 在 `tool_calls` 之外又用 DSML 文本复述了一遍，
 * 那段标记原样流进了回复正文）。
 *
 * **红线**：过滤不得牺牲逐字流式（用户定的「1 字 = 1 次更新」）——见最后两条用例。
 */
class ToolMarkupFilterTest {

    private val realSample = """
        <| | DSML | | calls>
        <| | DSML | | invoke name="world_info_lookup">
        <| | DSML | | parameter name="query" string="true">钟楼 红苹果树 来历 种下</| | DSML | | parameter>
        </| | DSML | | invoke>
        </| | DSML | | calls>
    """.trimIndent()

    @Test
    fun `真实样本整块被剔除`() {
        val cleaned = ToolMarkupFilter.strip(realSample)
        assertFalse(cleaned.contains("DSML"))
        assertEquals("", cleaned.trim())
    }

    @Test
    fun `正常剧情文本一个字都不动`() {
        val prose = "「嘘——！小声点！」\n*左右看了看*\n他蹲在彩绘窗洒下的光斑里。"
        assertEquals(prose, ToolMarkupFilter.strip(prose))
        assertFalse(ToolMarkupFilter.containsMarkup(prose))
        // 含尖括号但不是协议标签：不动
        val math = "他说 a < b 而 b > c，还有 <div> 这种"
        assertEquals(math, ToolMarkupFilter.strip(math))
    }

    @Test
    fun `噪声夹在正文中间时只去掉噪声`() {
        val mixed = "第一句。\n<| | DSML | | invoke>\n第二句。"
        assertEquals("第一句。\n第二句。", ToolMarkupFilter.strip(mixed))
    }

    @Test
    fun `流式：跨 delta 切断的标记块不泄漏`() {
        val stream = ToolMarkupFilter.Stream()
        val out = StringBuilder()
        realSample.forEach { out.append(stream.accept(it.toString())) }
        out.append(stream.flush())
        assertFalse("跨片切断也不能漏出 DSML", out.toString().contains("DSML"))
        assertEquals("", out.toString().trim())
    }

    @Test
    fun `流式：普通文本逐片即刻放行（不按行扣住）`() {
        val stream = ToolMarkupFilter.Stream()
        // 没有任何换行也要一片一片立刻出——这是「1 字 = 1 次更新」的守卫
        assertEquals("你", stream.accept("你"))
        assertEquals("好", stream.accept("好"))
        assertEquals("呀", stream.accept("呀"))
        assertEquals("", stream.flush())
    }

    @Test
    fun `流式：未闭合的开标签会短暂扣住后续文本，收尾时去标签放回（不吞正文）`() {
        val stream = ToolMarkupFilter.Stream()
        assertEquals("开场白。", stream.accept("开场白。<| | DSML | | invoke>"))
        // 块没闭合 → 后续文本扣住等待（宁可延迟，也不先把噪声放上屏）
        assertEquals("", stream.accept("接上文。"))
        // 收尾：去掉标签把正文交回
        assertEquals("接上文。", stream.flush())
    }

    @Test
    fun `流式：扣住的残片若只是普通文本，收尾时原样交回`() {
        val stream = ToolMarkupFilter.Stream()
        val out = StringBuilder()
        out.append(stream.accept("他说 a <"))
        out.append(stream.accept("b"))
        out.append(stream.flush())
        assertEquals("他说 a <b", out.toString())
        assertTrue(out.toString().startsWith("他说"))
    }
}

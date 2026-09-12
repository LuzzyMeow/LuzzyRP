package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 解析（GFM → [MdBlock]/[MdSpan]）的确定性单测。
 *
 * 语义基线 = **上游 WebView 版**（marked v15 + GFM + `breaks:true`）：
 * `*动作*` 是标准 emphasis、`「对白」`只是普通文本——故本文件也钉死这两条，
 * 防止再次退回「自造的对白/动作/叙述三分类」。
 */
class MarkdownParserTest {

    private fun parse(text: String) = MarkdownParser.parse(text)

    @Test
    fun `空串与纯空白产出空块列表`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `纯文本产出单个段落`() {
        val blocks = parse("他蹲在彩绘窗洒下的光斑里。")
        assertEquals(1, blocks.size)
        val p = blocks.single() as MdBlock.Paragraph
        assertEquals("他蹲在彩绘窗洒下的光斑里。", p.plainText())
    }

    @Test
    fun `单星号是 emphasis 斜体（对齐上游 marked 语义）`() {
        val p = parse("*左右看了看，把那颗苹果塞进兜里*").single() as MdBlock.Paragraph
        assertTrue(p.spans.any { it is MdSpan.Emphasis })
        // 星号本身不进正文
        assertFalse(p.plainText().contains("*"))
    }

    @Test
    fun `中文引号对白只是普通文本，不是特殊块`() {
        val blocks = parse("「嘘——！小声点！」")
        val p = blocks.single() as MdBlock.Paragraph
        assertEquals("「嘘——！小声点！」", p.plainText())
        assertEquals(1, p.spans.size)
    }

    @Test
    fun `双星号是 strong、双波浪是删除线`() {
        val p = parse("**很**重要~~不是~~").single() as MdBlock.Paragraph
        assertTrue(p.spans.any { it is MdSpan.Strong })
        assertTrue(p.spans.any { it is MdSpan.Strike })
    }

    @Test
    fun `行内代码与链接`() {
        val p = parse("跑 `flutter build` 看 [文档](https://example.com/a)").single() as MdBlock.Paragraph
        assertEquals("flutter build", p.spans.filterIsInstance<MdSpan.Code>().single().text)
        val link = p.spans.filterIsInstance<MdSpan.Link>().single()
        assertEquals("https://example.com/a", link.url)
        assertEquals("文档", link.text.plainText())
    }

    @Test
    fun `未闭合的强调符降级为纯文本（流式容错）`() {
        val p = parse("**他还没写完").single() as MdBlock.Paragraph
        assertTrue(p.spans.none { it is MdSpan.Strong })
        assertEquals("**他还没写完", p.plainText())
    }

    @Test
    fun `未闭合围栏渲染为进行中的代码块（流式容错）`() {
        val blocks = parse("```json\n{\"keywords\": [\"钟楼\"]")
        val fence = blocks.filterIsInstance<MdBlock.CodeFence>().single()
        assertEquals("json", fence.language)
        assertTrue(fence.code.contains("钟楼"))
        assertFalse("未闭合必须可辨识，供 UI 显示进行中态", fence.closed)
    }

    @Test
    fun `已闭合围栏 closed 为真且语言可选`() {
        val closed = parse("```json\n{}\n```").filterIsInstance<MdBlock.CodeFence>().single()
        assertEquals("json", closed.language)
        assertTrue(closed.closed)
        val bare = parse("```\nplain\n```").filterIsInstance<MdBlock.CodeFence>().single()
        assertEquals("", bare.language)
        assertTrue(bare.closed)
    }

    @Test
    fun `标题按级别解析，一到三级之外也保留级别`() {
        assertEquals(1, (parse("# 一").single() as MdBlock.Heading).level)
        assertEquals(2, (parse("## 二").single() as MdBlock.Heading).level)
        assertEquals(3, (parse("### 三").single() as MdBlock.Heading).level)
        assertEquals(4, (parse("#### 四").single() as MdBlock.Heading).level)
    }

    @Test
    fun `引用块内可含嵌套段落`() {
        val quote = parse("> 第一行\n> 第二行").single() as MdBlock.Quote
        assertTrue(quote.blocks.isNotEmpty())
        assertTrue(quote.plainText().contains("第一行"))
    }

    @Test
    fun `无序列表两项、有序列表起点可读`() {
        val ul = parse("- 苹果\n- 梨").single() as MdBlock.BulletList
        assertFalse(ul.ordered)
        assertEquals(2, ul.items.size)
        assertEquals("苹果", ul.items.first().spans.plainText())

        val ol = parse("1. 一\n2. 二").single() as MdBlock.BulletList
        assertTrue(ol.ordered)
        assertEquals(1, ol.startNumber)
        assertEquals(2, ol.items.size)
    }

    @Test
    fun `分隔线`() {
        assertTrue(parse("---").any { it is MdBlock.Rule })
    }

    @Test
    fun `GFM 表格解析表头与数据行`() {
        val table = parse("| 名 | 值 |\n| --- | --- |\n| 楼 | 12 |").single() as MdBlock.Table
        assertEquals(2, table.header.size)
        assertEquals("名", table.header[0].plainText())
        assertEquals(1, table.rows.size)
        assertEquals("12", table.rows[0][1].plainText())
    }

    @Test
    fun `混排保持文档顺序`() {
        val blocks = parse("# 标题\n\n正文一\n\n```\ncode\n```\n\n> 引用")
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.CodeFence)
        assertTrue(blocks[3] is MdBlock.Quote)
    }

    @Test
    fun `长文解析耗时在预算内（流式逐字上屏的成本门）`() {
        val long = buildString {
            repeat(100) { i ->
                append("第 $i 段：他蹲在彩绘窗洒下的光斑里，靴尖沾着落叶，*尾巴尖翘成一个小钩*。\n\n")
            }
        }
        assertTrue("样本应达到数千字量级（当前 ${long.length} 字）", long.length > 3000)
        // 预热（JIT）
        repeat(3) { MarkdownParser.parse(long) }
        val start = System.nanoTime()
        repeat(10) { MarkdownParser.parse(long) }
        val avgMs = (System.nanoTime() - start) / 10.0 / 1_000_000.0
        println("[成本门] ${long.length} 字平均单次解析 ${"%.2f".format(avgMs)}ms")
        assertTrue("平均单次解析 ${"%.2f".format(avgMs)}ms 超出预算 60ms", avgMs < 60.0)
    }
}

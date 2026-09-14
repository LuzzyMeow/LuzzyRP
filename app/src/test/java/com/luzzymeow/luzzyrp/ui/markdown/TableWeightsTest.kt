package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表格**列宽估宽**（C5）——纯 JVM 单测。
 *
 * 为什么值得钉：列宽算错**不报错**，只是「看起来不对」——窄列被压成一条缝
 * （用户看不见内容）、宽列疯狂折行（整表高得离谱）。这两种都是静默的观感缺陷，
 * 只有把口径写成断言才能防回归。
 */
class TableWeightsTest {

    private fun text(value: String): List<MdSpan> = listOf(MdSpan.Text(value))

    private fun table(header: List<String>, vararg rows: List<String>) = MdBlock.Table(
        header = header.map { text(it) },
        rows = rows.map { row -> row.map { text(it) } },
    )

    @Test
    fun `空表与零列不做除法`() {
        assertTrue(estimateTableWeights(MdBlock.Table(header = emptyList(), rows = emptyList()), 0).isEmpty())
        val weights = estimateTableWeights(MdBlock.Table(header = emptyList(), rows = emptyList()), 3)
        assertEquals("没有内容也要给每列一个可用的权重（否则 weight(0f) 会让列消失）", 3, weights.size)
        assertTrue(weights.all { it > 0f })
    }

    @Test
    fun `宽列拿到更大的权重`() {
        // 「项」列短、「说明」列长 —— 说明列必须更宽
        val t = table(
            listOf("项", "说明"),
            listOf("甲", "这是一段明显更长的说明文字，长到需要更多横向空间"),
        )
        val weights = estimateTableWeights(t, 2)
        assertTrue("说明列应比项列宽：$weights", weights[1] > weights[0])
    }

    @Test
    fun `窄列不会被压成零宽（最小权重兜底）`() {
        // 一列全是「是」，另一列是 80 字长文
        val t = table(
            listOf("行", "正文"),
            listOf("是", "很长".repeat(40)),
        )
        val weights = estimateTableWeights(t, 2)
        val ratio = weights[1] / weights[0]
        assertTrue("长列不应吞掉整张表（比值失控）：$ratio", ratio < 6f)
        assertTrue("窄列必须有可读的占比：${weights[0]} / $weights", weights[0] > 1f)
    }

    @Test
    fun `CJK 按两格计宽`() {
        // 两列都是 4 个字，但一列中文、一列拉丁：中列应更宽（显示宽度 8 vs 4）
        val t = table(
            listOf("aaaa", "中文四字"),
        )
        val weights = estimateTableWeights(t, 2)
        assertTrue("中文列按显示宽度应更宽：$weights", weights[1] > weights[0])
    }

    @Test
    fun `行长不齐时按列下标对齐且不越界`() {
        // 第二行只有一列（数据行长度不齐是真实数据里会出现的）
        val t = MdBlock.Table(
            header = listOf(text("A"), text("B"), text("C")),
            rows = listOf(listOf(text("只有一列"))),
        )
        val weights = estimateTableWeights(t, 3)
        assertEquals(3, weights.size)
        assertTrue("缺列的位置仍有兜底权重", weights.all { it > 0f })
    }

    @Test
    fun `表头参与估宽（表头很长时那一列也变宽）`() {
        val t = MdBlock.Table(
            header = listOf(text("一个特别长的表头名称"), text("x")),
            rows = listOf(listOf(text("a"), text("b"))),
        )
        val weights = estimateTableWeights(t, 2)
        assertTrue("表头长度必须计入：$weights", weights[0] > weights[1])
    }

    @Test
    fun `带样式的单元格按纯文本计宽`() {
        val styled = MdBlock.Table(
            header = listOf(listOf(MdSpan.Strong(listOf(MdSpan.Text("加粗中文"))) )),
            rows = listOf(listOf(listOf(MdSpan.Code("code")))),
        )
        val weights = estimateTableWeights(styled, 1)
        assertEquals(1, weights.size)
        assertTrue("有内容就有权重：$weights", weights[0] > 1f)
    }
}

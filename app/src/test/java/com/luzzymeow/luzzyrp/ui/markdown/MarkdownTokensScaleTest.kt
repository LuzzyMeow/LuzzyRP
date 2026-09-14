package com.luzzymeow.luzzyrp.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * D1 字号缩放的纯函数层：字号/行高跟随缩放，dp 间距与行数阈值不跟随（上游只缩字体）。
 */
class MarkdownTokensScaleTest {

    @Test
    fun `字号与行高按缩放比例放大`() {
        val scaled = MarkdownTokens().scaled(1.25f)
        assertEquals(13.5f * 1.25f, scaled.bodySize.value, 1e-4f)
        assertEquals(23f * 1.25f, scaled.bodyLineHeight.value, 1e-4f)
        assertEquals(18f * 1.25f, scaled.h1Size.value, 1e-4f)
        assertEquals(16f * 1.25f, scaled.h2Size.value, 1e-4f)
        assertEquals(11.5f * 1.25f, scaled.codeSize.value, 1e-4f)
        assertEquals(17f * 1.25f, scaled.codeLineHeight.value, 1e-4f)
    }

    @Test
    fun `缩到最小档 12px（0_75）不破版`() {
        val scaled = MarkdownTokens().scaled(0.75f)
        assertEquals(13.5f * 0.75f, scaled.bodySize.value, 1e-4f)
        assertEquals(18f * 0.75f, scaled.h1Size.value, 1e-4f)
    }

    @Test
    fun `dp 间距与折叠阈值不缩放（上游只缩字体）`() {
        val base = MarkdownTokens()
        val scaled = base.scaled(1.25f)
        assertEquals(base.blockGap, scaled.blockGap)
        assertEquals(base.listIndent, scaled.listIndent)
        assertEquals(base.codeCollapseLines, scaled.codeCollapseLines)
    }

    @Test
    fun `1 倍缩放返回原实例（避免重组期无谓重建）`() {
        val base = MarkdownTokens()
        assertSame(base, base.scaled(1f))
    }
}

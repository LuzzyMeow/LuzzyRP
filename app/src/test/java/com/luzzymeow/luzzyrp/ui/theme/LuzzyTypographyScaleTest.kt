package com.luzzymeow.luzzyrp.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * D1：Typography 按用户字号整体缩放。
 *
 * 关键判据：**M3 默认样式（本仓库只覆盖 6 个品牌样式，其余 9 个用 M3 默认）也必须一起缩**——
 * 只缩品牌样式会让同一界面上出现「有的跟随字号、有的不跟」的半缩放破版。
 */
class LuzzyTypographyScaleTest {

    @Test
    fun `品牌样式按缩放比例放大`() {
        val t = luzzyTypography(1.25f)
        assertEquals(14f * 1.25f, t.bodyMedium.fontSize.value, 1e-4f)
        assertEquals(23f * 1.25f, t.bodyMedium.lineHeight.value, 1e-4f)
        assertEquals(22f * 1.25f, t.headlineSmall.fontSize.value, 1e-4f)
        assertEquals(12f * 1.25f, t.labelMedium.fontSize.value, 1e-4f)
    }

    @Test
    fun `M3 默认样式也一起缩放（防半缩放）`() {
        val base = luzzyTypography(1f)
        val scaled = luzzyTypography(2f)
        assertEquals(base.bodySmall.fontSize.value * 2f, scaled.bodySmall.fontSize.value, 1e-3f)
        assertEquals(base.displayLarge.fontSize.value * 2f, scaled.displayLarge.fontSize.value, 1e-3f)
        assertEquals(base.labelSmall.fontSize.value * 2f, scaled.labelSmall.fontSize.value, 1e-3f)
        assertEquals(base.titleSmall.lineHeight.value * 2f, scaled.titleSmall.lineHeight.value, 1e-3f)
    }

    @Test
    fun `缩小时同样成立`() {
        val scaled = luzzyTypography(0.75f)
        assertEquals(15f * 0.75f, scaled.bodyLarge.fontSize.value, 1e-4f)
    }

    @Test
    fun `1 倍返回基线实例（避免重组期无谓重建）`() {
        assertSame(LuzzyTypography, luzzyTypography(1f))
    }
}

package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.graphics.Color
import com.luzzymeow.luzzyrp.ui.theme.LuzzySemantic
import com.luzzymeow.luzzyrp.ui.theme.luzzyColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * **Loom v4 对比度门禁**（确定性判据；「看起来对」的守卫）。
 *
 * 对亮暗两套 ColorScheme 各断言一组关键文字/背景对：
 * - 正文（14sp 级）≥ **4.5:1**（WCAG AA 正文）
 * - 大字 / 图形（18sp+ / 图标）≥ **3.0:1**
 *
 * 计算用相对亮度公式（WCAG 2.x）；色值全部来自 `luzzyColorScheme`（与生产同一推导），
 * 不另造色。此测试红了 = 新皮肤在真实色板上有不可读文字，直接修 token 而不是改测试。
 */
class ThemeContrastTest {

    // ── WCAG 相对亮度 ──────────────────────────────────

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertBody(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name 正文对比度 $r < 4.5", r >= 4.5)
    }

    private fun assertLarge(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name 大字/图形对比度 $r < 3.0", r >= 3.0)
    }

    @Test
    fun `亮色主题正文对比达标`() {
        val s = luzzyColorScheme(dark = false)
        assertBody("亮 onSurface/surface", s.onSurface, s.surface)
        assertBody("亮 onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
        assertBody("亮 onSurfaceVariant/surfaceContainerLow", s.onSurfaceVariant, s.surfaceContainerLow)
        assertBody("亮 onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertBody("亮 onPrimary/primary", s.onPrimary, s.primary)
        assertBody("亮 onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertBody("亮 onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertBody("亮 onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertBody("亮 onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer)
    }

    @Test
    fun `暗色主题正文对比达标`() {
        val s = luzzyColorScheme(dark = true)
        assertBody("暗 onSurface/surface", s.onSurface, s.surface)
        assertBody("暗 onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
        assertBody("暗 onSurfaceVariant/surfaceContainerLow", s.onSurfaceVariant, s.surfaceContainerLow)
        assertBody("暗 onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh)
        assertBody("暗 onPrimary/primary", s.onPrimary, s.primary)
        assertBody("暗 onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertBody("暗 onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertBody("暗 onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertBody("暗 onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer)
    }

    @Test
    fun `品牌强调与语义色可达图形级对比`() {
        listOf(false, true).forEach { dark ->
            val s = luzzyColorScheme(dark = dark)
            val tag = if (dark) "暗" else "亮"
            // 页面 accent（图标/线程线）属图形级 3:1
            assertLarge("$tag primary/surface（accent）", s.primary, s.surface)
            assertLarge("$tag secondary/surface", s.secondary, s.surface)
            assertLarge("$tag tertiary/surface", s.tertiary, s.surface)
        }
    }

    @Test
    fun `语义色对亮暗 surface 均不低于图形级`() {
        listOf(false, true).forEach { dark ->
            val s = luzzyColorScheme(dark = dark)
            val tag = if (dark) "暗" else "亮"
            // 语义色主要用作徽标文字（10-12sp 小字），按 4.5 断言（徽标自带文字，是语义的唯一载体之一）
            assertBody("$tag Success/surface", LuzzySemantic.Success, s.surface)
            assertBody("$tag Warning/surface", LuzzySemantic.Warning, s.surface)
            assertBody("$tag Error/surface", LuzzySemantic.Error, s.surface)
        }
    }
}
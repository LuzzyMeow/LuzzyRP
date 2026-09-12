package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HCT 色板快照测试（v3.0 P1）。
 *
 * 钉死 seed #CC785C → TONAL_SPOT 的生成结果：任何 MCU 升级或取色逻辑改动都会在此暴露。
 * 权威值 = MCU 2021 spec 生成结果（2026-09-12 已回填 `docs/DESIGN-compose.md` §2）。
 */
class LuzzyPaletteSnapshotTest {

    private fun hex(c: Color): String {
        val a = (c.alpha * 255.0f).toInt()
        val r = (c.red * 255.0f).toInt()
        val g = (c.green * 255.0f).toInt()
        val b = (c.blue * 255.0f).toInt()
        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
        return "#%06X".format(argb and 0xFFFFFF)
    }

    @Test
    fun `light scheme snapshot - key roles`() {
        val s = luzzyColorScheme(dark = false)
        assertEquals("#8F4C35", hex(s.primary))
        assertEquals("#FFDBD0", hex(s.primaryContainer))
        assertEquals("#FFF4F1", hex(s.surface))
    }

    @Test
    fun `dark scheme snapshot - key roles`() {
        val s = luzzyColorScheme(dark = true)
        assertEquals("#FFB59D", hex(s.primary))
        assertEquals("#723520", hex(s.primaryContainer))
        assertEquals("#231917", hex(s.surface))
    }

    @Test
    fun `seed hue sanity`() {
        val hct = hct.Hct.fromInt(LuzzySeed.CORAL)
        println("SEED HCT hue=${hct.hue} chroma=${hct.chroma} tone=${hct.tone}")
        // #CC785C 为珊瑚陶土：暖橙-红 hue 域、中高 chroma、中明度
        assertTrue("hue ${hct.hue}", hct.hue in 30.0..55.0)
        assertTrue("chroma ${hct.chroma}", hct.chroma in 30.0..60.0)
        assertTrue("tone ${hct.tone}", hct.tone in 55.0..70.0)
    }

    @Test
    fun `extend colors ramp shape`() {
        val light = extendFor(dark = false)
        val dark = extendFor(dark = true)
        assertEquals(10, light.gray.size)
        // 暗色 = 亮色反转（同 rikkahub 语义）
        assertEquals(light.gray.first(), dark.gray.last())
        assertEquals(light.gray.last(), dark.gray.first())
    }
}
class LuzzyPaletteDump {
    @Test
    fun `dump all roles`() {
        fun hex(c: Color): String {
            val a = (c.alpha * 255.0f).toInt(); val r = (c.red * 255.0f).toInt()
            val g = (c.green * 255.0f).toInt(); val b = (c.blue * 255.0f).toInt()
            return "#%06X".format(((a shl 24) or (r shl 16) or (g shl 8) or b) and 0xFFFFFF)
        }
        val light = luzzyColorScheme(dark = false)
        val dark = luzzyColorScheme(dark = true)
        val roles: Map<String, androidx.compose.material3.ColorScheme.() -> Color> = mapOf(
            "primary" to { primary }, "onPrimary" to { onPrimary },
            "primaryContainer" to { primaryContainer }, "onPrimaryContainer" to { onPrimaryContainer },
            "secondary" to { secondary }, "secondaryContainer" to { secondaryContainer },
            "tertiary" to { tertiary }, "tertiaryContainer" to { tertiaryContainer },
            "error" to { error }, "background" to { background },
            "surface" to { surface }, "surfaceVariant" to { surfaceVariant },
            "outline" to { outline }, "outlineVariant" to { outlineVariant },
            "surfaceContainerLowest" to { surfaceContainerLowest },
            "surfaceContainerLow" to { surfaceContainerLow },
            "surfaceContainer" to { surfaceContainer },
            "surfaceContainerHigh" to { surfaceContainerHigh },
            "surfaceContainerHighest" to { surfaceContainerHighest },
            "surfaceDim" to { surfaceDim }, "surfaceBright" to { surfaceBright },
            "inverseSurface" to { inverseSurface }, "inversePrimary" to { inversePrimary },
        )
        roles.forEach { (name, getter) ->
            println("ROLE $name light=" + hex(light.getter()) + " dark=" + hex(dark.getter()))
        }
    }
}

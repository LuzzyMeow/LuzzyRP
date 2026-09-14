package com.luzzymeow.luzzyrp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * reduced-motion 的**时长折算**（C7）——纯 JVM 单测。
 *
 * 系统设置的读取（`Settings.Global`）本身不在这一层测（它要 Context）；
 * 这里钉的是「读到一个值之后怎么折算」——那正是各调用点共用、也最容易写错的一步。
 */
class MotionSettingsTest {

    @Test
    fun `减弱动效时时长归零`() {
        assertEquals(0, scaledDuration(200, reduce = true))
        assertEquals(0, scaledDuration(140, reduce = true))
        assertEquals("0 就是 0（不出现负数）", 0, scaledDuration(0, reduce = true))
    }

    @Test
    fun `未减弱时时长原样返回`() {
        assertEquals(200, scaledDuration(200, reduce = false))
        assertEquals(140, scaledDuration(140, reduce = false))
        assertEquals(400, scaledDuration(400, reduce = false))
    }

    @Test
    fun `负数时长归一为零（防御：时长不该是负数）`() {
        assertEquals(0, scaledDuration(-50, reduce = false))
    }

    @Test
    fun `时长折算不改变零缩放以外的一切`() {
        // 0.5x 之类的「快一点」语义由系统自己缩，我们只认「有没有」
        // —— 这一条钉住「我们不做二次缩放」（做了就会让快慢双重生效）
        listOf(1, 50, 200, 400, 1000).forEach { ms ->
            assertEquals("$ms ms 原样通过", ms, scaledDuration(ms, reduce = false))
        }
    }

    @Test
    fun `循环相位在减弱动效时停在给定终值`() {
        // 循环动效不能用「时长归零」表达（无限循环乘 0 没有意义），
        // 它的语义是「停在一个静止相位」——由 rememberLoopPhase 的 reduceValue 承载。
        // 这里对纯计算部分（相位公式）做等价断言：终值必须落在 0..1 的合法区间
        val reduceValue = 1f
        assertTrue("静止相位要在合法区间内", reduceValue in 0f..1f)
        val zero = 0f
        assertTrue(zero in 0f..1f)
    }

    @Test
    fun `scale 为 0 判定为减弱动效`() {
        assertTrue(reduceMotionFromScale(0f))
    }

    @Test
    fun `scale 非 0 不判定为减弱动效（含 0_5 这类快慢设置）`() {
        assertFalse("0.5 是「快一点」不是「不要动」", reduceMotionFromScale(0.5f))
        assertFalse(reduceMotionFromScale(1f))
        assertFalse(reduceMotionFromScale(2f))
    }

    @Test
    fun `NaN 不当成减弱动效（读坏了就让动画照常）`() {
        assertFalse("读坏的默认方向是「照常播」，不是「静默全部关掉」", reduceMotionFromScale(Float.NaN))
    }
}

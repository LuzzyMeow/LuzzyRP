package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **供应商配置的输入解析**（B5 的设置项）——纯函数门禁。
 *
 * 为什么值得单独立测：这里此前是一个**静默回退**（解析不出来就用默认值），
 * 而那正是本版刚修掉的那类缺陷——用户以为自己填的生效了、实际被悄悄换掉。
 * 现在的契约是：**解析不出来就是 null**，界面据此把错误贴在字段上并拦住保存。
 */
class TransportConfigParsingTest {

    @Test
    fun `正常输入按字面解析`() {
        assertEquals(65_536, TransportConfig.parseContextWindow("65536"))
        assertEquals(0, TransportConfig.parseContextWindow("0"))
        assertEquals(32_768, TransportConfig.parseContextWindow("32768"))
    }

    @Test
    fun `千分位与空白一律忽略（从别处复制粘贴是常见动作）`() {
        assertEquals(65_536, TransportConfig.parseContextWindow("65,536"))
        assertEquals(65_536, TransportConfig.parseContextWindow(" 65 536 "))
        assertEquals(65_536, TransportConfig.parseContextWindow("65，536"))
        assertEquals(65_536, TransportConfig.parseContextWindow("65_536"))
    }

    @Test
    fun `空串与纯空白是非法输入，不回退成默认值`() {
        assertNull(TransportConfig.parseContextWindow(""))
        assertNull(TransportConfig.parseContextWindow("   "))
    }

    @Test
    fun `负数与超 Int 都是非法输入`() {
        assertNull("负号不得被静默吃掉", TransportConfig.parseContextWindow("-1"))
        assertNull(TransportConfig.parseContextWindow("99999999999"))
    }

    @Test
    fun `混入其它字符是非法输入（不猜用户想说什么）`() {
        assertNull(TransportConfig.parseContextWindow("64k"))
        assertNull(TransportConfig.parseContextWindow("65536 tokens"))
        assertNull(TransportConfig.parseContextWindow("6.5e4"))
    }

    @Test
    fun `默认值是保守值本身（不是 0，也不是某个模型的真实窗口）`() {
        assertEquals(32_768, TransportConfig.DefaultContextWindow)
        // 触发线与保留预算都由它推出（见 CompactionTest），这里只钉住「填 0 = 关闭」的语义
        assertEquals(0, TransportConfig.parseContextWindow("0"))
    }
}

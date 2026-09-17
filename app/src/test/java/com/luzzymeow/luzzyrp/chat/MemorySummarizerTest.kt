package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MemorySummarizer] 触发检查的 JVM 单测（v3.2 批 C）。
 *
 * 只测纯函数 [MemorySummarizer.shouldSummarize]：网络/存储路径不进 JVM 测试
 * （真实调用在仪器化链路上由「发送一轮」端到端覆盖；失败静默已在其内部保证）。
 */
class MemorySummarizerTest {

    // shouldSummarize 是纯函数、不碰 store（传 null 即可；误触 summarizeAndStore 才会炸）
    private val summarizer = MemorySummarizer(store = null)

    @Test
    fun `未到阈值不触发`() {
        val s = MemorySummarizer.Settings(enabled = true, everyTurns = 5)
        assertEquals(null, summarizer.shouldSummarize(s, 0, 0))
        assertEquals(null, summarizer.shouldSummarize(s, 4, 0))
    }

    @Test
    fun `到阈值触发且进度不重复`() {
        val s = MemorySummarizer.Settings(enabled = true, everyTurns = 5)
        assertEquals(5, summarizer.shouldSummarize(s, 5, 0))
        assertEquals(10, summarizer.shouldSummarize(s, 10, 5))
        // 已覆盖到 5，本轮才 7 轮 → 不重复总结
        assertEquals(null, summarizer.shouldSummarize(s, 7, 5))
    }

    @Test
    fun `跳档——一口气过了两档只取最新一档`() {
        val s = MemorySummarizer.Settings(enabled = true, everyTurns = 5)
        // 12 轮 → due = 10（不是 5）
        assertEquals(10, summarizer.shouldSummarize(s, 12, 0))
    }

    @Test
    fun `关闭后永不触发`() {
        val s = MemorySummarizer.Settings(enabled = false, everyTurns = 5)
        assertEquals(null, summarizer.shouldSummarize(s, 50, 0))
    }

    @Test
    fun `设置序列化往返`() {
        val s = MemorySummarizer.Settings(enabled = false, everyTurns = 8)
        val round = MemorySummarizer.Settings.from(s.toJson())
        assertEquals(s, round)
        // 坏值兜底：缺字段 = 默认；超界收敛
        assertEquals(MemorySummarizer.Settings(), MemorySummarizer.Settings.from(null))
        assertEquals(
            20,
            MemorySummarizer.Settings.from(
                kotlinx.serialization.json.buildJsonObject {
                    put("enabled", kotlinx.serialization.json.JsonPrimitive(true))
                    put("everyTurns", kotlinx.serialization.json.JsonPrimitive(99))
                },
            ).everyTurns,
        )
    }
}
package com.luzzymeow.luzzyrp.chat.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BudgetGuard] 单测：轮次 / 时长 / token 三闸边界与可注入时钟。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复，仅改包名。
 */
class BudgetGuardTest {

    @Test
    fun `轮次上限边界`() {
        val guard = BudgetGuard(maxRounds = 3, clock = { 0L })
        guard.start()
        assertNull(guard.check(0))
        assertNull(guard.check(2))
        assertEquals(BudgetGuard.StopReason.MAX_ROUNDS, guard.check(3))
        assertEquals(BudgetGuard.StopReason.MAX_ROUNDS, guard.check(4))
    }

    @Test
    fun `总时长边界（恰好等于不算超）`() {
        var now = 0L
        val guard = BudgetGuard(maxRounds = 100, totalDurationMs = 1_000L, clock = { now })
        guard.start()
        now = 1_000L
        assertNull(guard.check(0))
        now = 1_001L
        assertEquals(BudgetGuard.StopReason.TIMEOUT, guard.check(0))
    }

    @Test
    fun `token 预算按输入加输出累计`() {
        val guard = BudgetGuard(maxRounds = 100, tokenBudget = 100L, clock = { 0L })
        guard.start()
        guard.recordUsage(input = 40, output = 59)
        assertEquals(99L, guard.tokensUsed)
        assertNull(guard.check(0))
        guard.recordUsage(input = 1, output = 0)
        assertEquals(100L, guard.tokensUsed)
        assertEquals(BudgetGuard.StopReason.TOKEN_BUDGET, guard.check(0))
    }

    @Test
    fun `未 start 时时长为 0 且时长闸不触发`() {
        val guard = BudgetGuard(maxRounds = 100, totalDurationMs = 1L, clock = { 10_000L })
        assertTrue(!guard.started())
        assertEquals(0L, guard.elapsedMs())
        assertNull(guard.check(0))
    }

    @Test
    fun `时钟回拨不产生负时长`() {
        var now = 5_000L
        val guard = BudgetGuard(maxRounds = 100, totalDurationMs = 1_000L, clock = { now })
        guard.start()
        now = 1_000L
        assertEquals(0L, guard.elapsedMs())
        assertNull(guard.check(0))
    }

    @Test
    fun `负数用量按 0 计`() {
        val guard = BudgetGuard(clock = { 0L })
        guard.start()
        guard.recordUsage(-5, -3)
        assertEquals(0L, guard.tokensUsed)
    }

    @Test
    fun `start 重置用量与计时`() {
        var now = 0L
        val guard = BudgetGuard(maxRounds = 100, clock = { now })
        guard.start()
        guard.recordUsage(10, 10)
        now = 500L
        guard.start()
        assertEquals(0L, guard.tokensUsed)
        assertEquals(0L, guard.elapsedMs())
    }

    @Test
    fun `默认值保持不变`() {
        val guard = BudgetGuard()
        assertEquals(24, guard.maxRounds)
        assertEquals(120_000L, guard.toolTimeoutMs)
        assertEquals(30L * 60L * 1_000L, guard.totalDurationMs)
        assertEquals(200_000L, guard.tokenBudget)
    }

    @Test
    fun `预算类终止统一映射 max_rounds`() {
        val guard = BudgetGuard()
        assertEquals("max_rounds", guard.reasonId(BudgetGuard.StopReason.MAX_ROUNDS))
        assertEquals("max_rounds", guard.reasonId(BudgetGuard.StopReason.TIMEOUT))
        assertEquals("max_rounds", guard.reasonId(BudgetGuard.StopReason.TOKEN_BUDGET))
    }
}

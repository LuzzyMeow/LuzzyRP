package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆召回设置（记忆页「记忆引擎」卡的真源）。
 *
 * 判据全部是「同输入同输出」的纯函数：读设置、写设置、归一化、以及关掉开关时**真的不检索**。
 * 这些是「界面上的开关到底有没有作用」的最小可验证单位——真机截图看不出开关有没有接到请求上。
 */
class RecallOptionsTest {

    @Test
    fun `缺省时给默认值`() {
        val options = RecallOptions.from(null)
        assertTrue(options.enabled)
        assertEquals(RecallOptions.DEFAULT_TOP_K, options.topK)
        assertEquals(RecallOptions.DEFAULT_MIN_SCORE, options.minScore, 1e-9)
    }

    @Test
    fun `读得回写进去的三个字段`() {
        val settings = buildJsonObject {
            put("recall", buildJsonObject {
                put("enabled", false)
                put("topK", 4)
                put("minScore", 0.2)
            })
        }
        val options = RecallOptions.from(settings)
        assertFalse(options.enabled)
        assertEquals(4, options.topK)
        assertEquals(0.2, options.minScore, 1e-9)
    }

    @Test
    fun `越界与坏类型一律归一化到合法区间`() {
        val settings = buildJsonObject {
            put("recall", buildJsonObject {
                put("topK", 99)
                put("minScore", 5.0)
            })
        }
        val options = RecallOptions.from(settings)
        assertEquals(RecallOptions.TOP_K_STEPS.last(), options.topK)
        assertEquals(RecallOptions.MIN_SCORE_STEPS.last(), options.minScore, 1e-9)

        // 字符串形态的数字（旧数据里数字常是字符串）要**照常读出来**，与 UsageAggregate 同口径——
        // 读不出来的（"abc"）才回落默认值。两者混为一谈会让老用户设置静默失效。
        val stringNumber = buildJsonObject { put("recall", buildJsonObject { put("topK", "3") }) }
        assertEquals(3, RecallOptions.from(stringNumber).topK)

        val garbage = buildJsonObject { put("recall", buildJsonObject { put("topK", "abc") }) }
        assertEquals(RecallOptions.DEFAULT_TOP_K, RecallOptions.from(garbage).topK)
    }

    @Test
    fun `写回只动 recall 子对象，其余字段逐字保留`() {
        // `emptyTurns` 是旧版迁移进来的真实字段：整对象覆盖会把它抹掉，而且界面上看不出来
        val existing = buildJsonObject {
            put("emptyTurns", buildJsonObject { put("uuid:vector", JsonPrimitive(3)) })
            put("recall", buildJsonObject { put("topK", 1) })
            put("mode", "vector")
        }
        val merged = RecallOptions.mergeInto(existing, RecallOptions(topK = 5))
        assertEquals("vector", (merged["mode"] as JsonPrimitive).content)
        val emptyTurns = merged["emptyTurns"] as JsonObject
        assertEquals(3, (emptyTurns["uuid:vector"] as JsonPrimitive).content.toInt())
        // recall 段被整体替换（旧的 topK=1 不该残留）
        assertEquals(5, (RecallOptions.from(merged)).topK)
    }

    @Test
    fun `写回可以落在空设置上（首次使用没有 memorySettings）`() {
        val merged = RecallOptions.mergeInto(null, RecallOptions(enabled = false, topK = 3, minScore = 0.3))
        val options = RecallOptions.from(merged)
        assertFalse(options.enabled)
        assertEquals(3, options.topK)
        assertEquals(0.3, options.minScore, 1e-9)
    }

    @Test
    fun `关掉开关时真的不检索（而不是检索完再丢弃）`() {
        val history = listOf(1 to "钟楼顶上的红苹果树", 2 to "少年把苹果塞进兜里")
        val on = RecallOptions(enabled = true, topK = 2, minScore = 0.05)
        val off = on.copy(enabled = false)
        assertTrue(on.search(history, "苹果").isNotEmpty())
        assertTrue(off.search(history, "苹果").isEmpty())
    }

    @Test
    fun `topK 真的限制条数、阈值真的提高门槛`() {
        val history = listOf(
            1 to "苹果苹果苹果",
            2 to "苹果树",
            3 to "苹果园",
        )
        assertEquals(1, RecallOptions(topK = 1, minScore = 0.05).search(history, "苹果").size)
        assertEquals(3, RecallOptions(topK = 5, minScore = 0.05).search(history, "苹果").size)
        // 阈值拉满：只有高度重叠的第 1 轮能过
        val strict = RecallOptions(topK = 5, minScore = 0.99).search(history, "苹果苹果苹果")
        assertEquals(1, strict.size)
        assertEquals(1, strict.first().turn)
    }

    @Test
    fun `阈值标签是整数百分比`() {
        assertEquals("8%", RecallOptions(minScore = 0.08).minScoreLabel)
        assertEquals("30%", RecallOptions(minScore = 0.3).minScoreLabel)
    }

    @Test
    fun `JsonNull 不会把默认值顶掉`() {
        val settings = buildJsonObject {
            put("recall", buildJsonObject {
                put("enabled", JsonNull)
                put("topK", JsonNull)
            })
        }
        val options = RecallOptions.from(settings)
        assertTrue(options.enabled)
        assertEquals(RecallOptions.DEFAULT_TOP_K, options.topK)
    }
}

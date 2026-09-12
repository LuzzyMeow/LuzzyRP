package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用量脚注与截断判定（T4/T5）的纯逻辑单测。
 *
 * 背景：传输层**早已**解析了 usage 与 finishReason，但引擎从不把它们变成事件、
 * UI 也从不渲染 —— 数据白存。这两块补上后，「这轮花了多少」「回复为什么断」才可见。
 */
class UsageFormatTest {

    @Test
    fun `没有数据时不给脚注（整行不渲染）`() {
        assertNull(UsageFormat.line(usage = null, elapsedMs = null))
        assertNull(UsageFormat.line(usage = null, elapsedMs = 0L))
    }

    @Test
    fun `完整脚注：输入含缓存、输出、耗时、吞吐`() {
        val line = UsageFormat.line(
            usage = UsageInfo(input = 1234, output = 456, cached = 1100),
            elapsedMs = 12_300L,
        )
        assertEquals("输入 1,234（缓存 1,100） · 输出 456 · 12.3s · 37 tok/s", line)
    }

    @Test
    fun `无缓存命中时不显示缓存括号`() {
        val line = UsageFormat.line(UsageInfo(input = 10, output = 20, cached = 0), null)
        assertEquals("输入 10 · 输出 20", line)
    }

    @Test
    fun `无耗时也能只给用量`() {
        assertEquals("输入 5 · 输出 6", UsageFormat.line(UsageInfo(5, 6), null))
    }

    @Test
    fun `截断判定覆盖三协议的归一值`() {
        assertTrue(UsageFormat.isTruncated("length"))
        assertTrue(UsageFormat.isTruncated("max_tokens"))
        assertTrue(UsageFormat.isTruncated("LENGTH"))
        assertFalse(UsageFormat.isTruncated("stop"))
        assertFalse(UsageFormat.isTruncated("end_turn"))
        assertFalse(UsageFormat.isTruncated(null))
    }

    @Test
    fun `从传输层增量取用量与缓存命中（DeepSeek 口径）`() {
        val delta = LlmDelta(
            usage = LlmDelta.Usage(input = 900, output = 120),
            rawUsage = buildJsonObject {
                put("prompt_tokens", JsonPrimitive(900))
                put("completion_tokens", JsonPrimitive(120))
                put("prompt_cache_hit_tokens", JsonPrimitive(768))
            },
        )
        assertEquals(UsageInfo(900, 120, 768), UsageInfo.from(delta))
    }

    @Test
    fun `从传输层增量取用量与缓存命中（OpenAI 嵌套口径）`() {
        val delta = LlmDelta(
            usage = LlmDelta.Usage(input = 500, output = 60),
            rawUsage = buildJsonObject {
                put(
                    "prompt_tokens_details",
                    buildJsonObject { put("cached_tokens", JsonPrimitive(384)) },
                )
            },
        )
        assertEquals(UsageInfo(500, 60, 384), UsageInfo.from(delta))
    }

    @Test
    fun `没有用量帧时不产出信息；有帧但缺缓存字段时 cached 为 null（不猜）`() {
        assertNull(UsageInfo.from(LlmDelta(content = "x")))
        // 有 usage 帧 → 如实上报；缓存字段缺失 → cached=null（而不是编个 0）
        assertEquals(UsageInfo(1, 2, null), UsageInfo.from(LlmDelta(usage = LlmDelta.Usage(1, 2))))
    }

    @Test
    fun `千分位与吞吐不出现负数或除零`() {
        assertEquals("0", UsageFormat.grouped(0))
        assertEquals("1,000,000", UsageFormat.grouped(1_000_000))
        val line = UsageFormat.line(UsageInfo(1, 10), 1L)
        assertTrue(line!!.endsWith("0.0s"))
    }
}

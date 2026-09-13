package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前缀缓存观测层（A7）——**批 A 验收指标的可执行版本**（PLAN §7.1）。
 *
 * 口径与 WebView 版 `ext/luzzy-prefix-guard.js` **逐字对齐**，这样两代实现的数字可以互相印证：
 * - 每轮算「与上一轮 messages 的公共前缀字符数」（逐条比协议字节）；
 * - 单轮 `commonRatio = 公共前缀字符 / 上一轮总字符`；
 * - 累计 `avgCommonRatio = Σ公共前缀字符 / Σ上一轮总字符`（**加权**，不是各轮均值）。
 *
 * 纪律：观测层**只统计不干预**——它不许改请求、不许改结果、失败也不许打断生成。
 */
class CacheObserverTest {

    @After
    fun tearDown() = CacheObserver.reset()

    private fun request(
        messages: List<LlmMessage>,
        model: String = "test-model",
        temperature: Double? = null,
        maxTokens: Int? = 1200,
        tools: List<JsonObject> = emptyList(),
    ) = LlmRequest(
        messages = messages,
        protocol = "openai",
        baseUrl = "https://example.invalid/v1/chat/completions",
        apiKey = "test-key",
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        tools = tools,
    )

    private fun system(text: String) = LlmMessage(role = LlmRole.SYSTEM, content = text)
    private fun user(text: String) = LlmMessage(role = LlmRole.USER, content = text)
    private fun assistant(text: String) = LlmMessage(role = LlmRole.ASSISTANT, content = text)

    private fun tool(name: String) = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put("function", buildJsonObject { put("name", JsonPrimitive(name)) })
    }

    // ---------------------------------------------------------------- 公共前缀

    @Test
    fun `严格延伸时公共前缀占比为 1（纯追加）`() {
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"))))
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"), assistant("回复"), user("第二句"))))

        val summary = CacheObserver.current()
        assertEquals("两轮都记账", 2, summary.rounds)
        assertEquals(1.0, summary.avgCommonRatio!!, 1e-9)
        assertEquals(1.0, summary.lastCommonRatio!!, 1e-9)
    }

    @Test
    fun `第一轮没有对比对象，单轮占比为 null 且不进累计`() {
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"))))

        val summary = CacheObserver.current()
        assertEquals(1, summary.rounds)
        assertEquals("没有可比对象就不该给出占比", 0, summary.comparableRounds)
        assertNull(summary.avgCommonRatio)
        assertNull(summary.lastCommonRatio)
    }

    @Test
    fun `system 变了 → 前缀从第一条就断开，占比为 0`() {
        CacheObserver.onRequest(request(listOf(system("旧 system"), user("第一句"))))
        CacheObserver.onRequest(request(listOf(system("新 system"), user("第一句"))))

        assertEquals(0.0, CacheObserver.current().avgCommonRatio!!, 1e-9)
    }

    @Test
    fun `历史中间被改写 → 公共前缀只到被改写的那一条之前`() {
        val prefix = listOf(system("S"), user("第一句"), assistant("回复一"))
        CacheObserver.onRequest(request(prefix + user("第二句")))
        // 把中间那条 AI 回复改掉（例如「编辑消息」），其后全部作废
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"), assistant("改过的回复")) + user("第二句")))

        val ratio = CacheObserver.current().lastCommonRatio!!
        assertTrue("只该命中前两条，实际=$ratio", ratio in 0.01..0.99)
    }

    @Test
    fun `累计口径是加权的 Σ公共前缀 Σ上轮长度（与 WebView 版一致）`() {
        val a = listOf(system("S"), user("第一句"))
        val b = a + listOf(assistant("回复"), user("第二句"))
        val c = b + listOf(assistant("回复二"), user("第三句"))
        CacheObserver.onRequest(request(a))
        CacheObserver.onRequest(request(b))
        CacheObserver.onRequest(request(c))

        val summary = CacheObserver.current()
        assertEquals(2, summary.comparableRounds)
        assertEquals("两轮都是严格延伸 → 加权占比仍为 1", 1.0, summary.avgCommonRatio!!, 1e-9)
    }

    // ---------------------------------------------------------------- 命中率

    @Test
    fun `命中率 = cached 与 prompt 之比`() {
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"))))
        CacheObserver.onUsage(UsageInfo(input = 2_000, output = 100, cached = 1_500))

        val summary = CacheObserver.current()
        assertEquals(2_000, summary.promptTokens)
        assertEquals(1_500, summary.cachedTokens)
        assertEquals(0.75, summary.hitRate!!, 1e-9)
    }

    @Test
    fun `供应商没给 cached 字段时命中率为 null（不猜、不把 0 当命中）`() {
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"))))
        CacheObserver.onUsage(UsageInfo(input = 2_000, output = 100, cached = null))

        val summary = CacheObserver.current()
        assertEquals(2_000, summary.promptTokens)
        assertEquals("cached 未知时不该算成 0%", 0, summary.cachedTokens)
        assertNull(summary.hitRate)
    }

    // ---------------------------------------------------------------- 请求头指纹（缓存纪元）

    @Test
    fun `请求头没变不记事件，变了记一次`() {
        CacheObserver.onRequest(request(listOf(system("S")), model = "model-a"))
        CacheObserver.onRequest(request(listOf(system("S")), model = "model-a"))
        assertEquals("同头不算变化", 0, CacheObserver.current().headerChanges)

        // 换模型 = 换了一个缓存纪元：旧前缀在新模型上不存在
        CacheObserver.onRequest(request(listOf(system("S")), model = "model-b"))
        assertEquals(1, CacheObserver.current().headerChanges)
    }

    @Test
    fun `采样参数与工具集变化都算请求头变化`() {
        val base = request(listOf(system("S")), temperature = 0.7, tools = listOf(tool("world_info_lookup")))
        CacheObserver.onRequest(base)
        CacheObserver.onRequest(base.copy(temperature = 1.0))
        assertEquals("温度变了要记", 1, CacheObserver.current().headerChanges)

        CacheObserver.reset()
        CacheObserver.onRequest(base)
        CacheObserver.onRequest(base.copy(tools = emptyList()))
        assertEquals("工具集变了要记（tools 进请求体，前缀随之失效）", 1, CacheObserver.current().headerChanges)
    }

    @Test
    fun `首轮建立纪元而不算变化`() {
        CacheObserver.onRequest(request(listOf(system("S"))))
        assertEquals("第一次没有可比的头，不该虚假报警", 0, CacheObserver.current().headerChanges)
    }

    @Test
    fun `指纹把密钥排除在外——密钥轮换不该被记成缓存纪元变化`() {
        val a = request(listOf(system("S"))).copy(apiKey = "key-1")
        val b = request(listOf(system("S"))).copy(apiKey = "key-2")
        assertEquals(CacheObserver.RequestHeader.of(a), CacheObserver.RequestHeader.of(b))
        assertNotEquals("但模型不同必须区分开", CacheObserver.RequestHeader.of(a), CacheObserver.RequestHeader.of(b.copy(model = "m2")))
    }

    // ---------------------------------------------------------------- 只统计不干预

    @Test
    fun `观察不改写请求内容（只统计不干预）`() {
        val messages = listOf(system("S"), user("第一句"))
        val before = messages.map { it.toOpenAiJson().toString() }
        CacheObserver.onRequest(request(messages))
        assertEquals("观测层绝不能改请求", before, messages.map { it.toOpenAiJson().toString() })
    }

    @Test
    fun `重置清空全部统计`() {
        CacheObserver.onRequest(request(listOf(system("S"))))
        CacheObserver.onUsage(UsageInfo(input = 10, output = 1, cached = 5))
        CacheObserver.reset()

        val summary = CacheObserver.current()
        assertEquals(0, summary.rounds)
        assertNull(summary.avgCommonRatio)
        assertNull(summary.hitRate)
        assertTrue(summary.turns.isEmpty())
    }

    // ---------------------------------------------------------------- adb 探针

    @Test
    fun `report 行含四项关键数字（真机 adb 探针的唯一现场）`() {
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句")), model = "model-a"))
        CacheObserver.onRequest(request(listOf(system("S"), user("第一句"), assistant("回复"), user("第二句"))))
        CacheObserver.onUsage(UsageInfo(input = 1_000, output = 50, cached = 800))

        val line = CacheObserver.current().report()
        assertTrue("要有公共前缀占比：$line", line.contains("公共前缀"))
        assertTrue("要有命中率：$line", line.contains("命中"))
        assertTrue("要有轮数：$line", line.contains("轮"))
        assertTrue("要能看出请求头变过几次：$line", line.contains("纪元"))
    }

    @Test
    fun `未命中缓存时 report 如实写「无数据」而不是编一个 0`() {
        val line = CacheObserver.Summary().report()
        assertTrue("空账本也要可读：$line", line.contains("无数据") || line.contains("—"))
        assertFalse("空账本不该声称命中了什么", line.contains("100.0%"))
    }
}

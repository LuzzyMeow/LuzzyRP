package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.JsonLenient
import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmError
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.ToolCallDelta
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChatJobs] 单测：桥接契约的行为面。
 *
 * 关注四件事：
 * 1. **立即返回 + 参数非法返回空串**（JS 靠空串判定降级）；
 * 2. **事件形状**（四型 JSON，键序与字段名是 JS 侧硬依赖）；
 * 3. **节流**（高频增量合并成 ~120ms 一批 + 终局强制冲刷）；
 * 4. **不抛异常**（没有出口、出口抛错、传输层抛错都只能转成事件）。
 */
class ChatJobsTest {

    /** 按脚本吐帧的假传输。 */
    private class FakeTransport(
        private val frames: List<LlmDelta>,
        private val gapMs: Long = 0,
        private val throwAfterFrames: Throwable? = null,
    ) : LlmTransport {
        val requests: MutableList<LlmRequest> = Collections.synchronizedList(mutableListOf<LlmRequest>())

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            frames.forEach { frame ->
                emit(frame)
                if (gapMs > 0) delay(gapMs)
            }
            throwAfterFrames?.let { throw it }
        }
    }

    /** 事件收集器：等终局事件 + 按类型取载荷。 */
    private class Recorder {
        val events: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())
        private val terminal = CountDownLatch(1)

        val sink = ChatEventSink { jobId, json ->
            events += jobId to json
            if (eventTypeOf(json) == "done" || eventTypeOf(json) == "error") terminal.countDown()
        }

        fun awaitTerminal(timeoutMs: Long = 10_000): Boolean = terminal.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun types(): List<String> = events.map { eventTypeOf(it.second) }

        fun payloads(type: String): List<String> =
            events.map { it.second }.filter { eventTypeOf(it) == type }

        fun hasType(type: String): Boolean = types().contains(type)
    }

    private fun jobs(
        recorder: Recorder?,
        transport: LlmTransport,
        nowMillis: () -> Long = System::currentTimeMillis,
        flushIntervalMs: Long = ChatJobs.FLUSH_INTERVAL_MS,
    ) = ChatJobs(
        sink = { recorder?.sink },
        transport = transport,
        flushIntervalMs = flushIntervalMs,
        nowMillis = nowMillis,
    )

    private val plan = """
        {"jobId":"j-1","protocol":"openai","baseUrl":"https://a/v1/chat/completions",
         "apiKey":"sk-k","model":"m","messages":[{"role":"user","content":"hi"}]}
    """.trimIndent()

    /** 把 plan 里的 jobId 换掉（用 JsonPrimitive 转义，保证含引号的 id 也能成合法 JSON）。 */
    private fun planWith(jobId: String) = plan.replace("\"j-1\"", JsonPrimitive(jobId).toString())

    /** 等到 manager 没有在跑的任务（或超时）。 */
    private fun awaitIdle(manager: ChatJobs, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (manager.activeCount() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    private fun awaitEvent(recorder: Recorder, type: String, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!recorder.hasType(type) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return recorder.hasType(type)
    }

    // ---------- 契约：立即返回与非法入参 ----------

    @Test
    fun `start 返回 jobId 并落到传输层`() {
        val recorder = Recorder()
        val transport = FakeTransport(listOf(LlmDelta(content = "ok", finishReason = "stop")))
        val manager = jobs(recorder, transport)
        try {
            assertEquals("j-1", manager.start(plan))
            assertTrue(recorder.awaitTerminal())
            assertEquals(1, transport.requests.size)
            assertEquals("openai", transport.requests.single().protocol)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `非法 plan 返回空串且不发任何事件`() {
        val recorder = Recorder()
        val manager = jobs(recorder, FakeTransport(emptyList()))
        try {
            assertEquals("", manager.start("not json"))
            assertEquals("", manager.start("""{"protocol":"openai"}"""))
            assertEquals("", manager.start("""{"jobId":"j","protocol":"cohere"}"""))
            assertTrue(recorder.events.isEmpty())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `abort 对不存在的 job 返回 false`() {
        val manager = jobs(null, FakeTransport(emptyList()))
        try {
            assertFalse(manager.abort("nope"))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `abort 中止在跑的任务且不再发 done`() {
        val recorder = Recorder()
        val transport = FakeTransport(listOf(LlmDelta(content = "A")), gapMs = 5_000)
        val manager = jobs(recorder, transport, flushIntervalMs = 10_000)
        try {
            assertEquals("j-1", manager.start(plan))
            assertTrue("应当先收到首个 delta", awaitEvent(recorder, "delta"))
            assertTrue(manager.abort("j-1"))
            Thread.sleep(300)
            assertEquals(listOf("delta"), recorder.types())
            assertFalse(manager.isActive("j-1"))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `同 jobId 重入会换掉旧任务`() {
        val recorder = Recorder()
        val transport = FakeTransport(listOf(LlmDelta(content = "A")), gapMs = 5_000)
        val manager = jobs(recorder, transport, flushIntervalMs = 10_000)
        try {
            assertEquals("j-1", manager.start(plan))
            assertEquals("j-1", manager.start(plan))
            Thread.sleep(200)
            assertEquals(1, manager.activeCount())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `shutdown 后不再有活动任务`() {
        val transport = FakeTransport(listOf(LlmDelta(content = "A")), gapMs = 5_000)
        val manager = jobs(null, transport, flushIntervalMs = 10_000)
        assertEquals("j-1", manager.start(plan))
        manager.shutdown()
        assertEquals(0, manager.activeCount())
    }

    // ---------- 事件形状 ----------

    @Test
    fun `delta 事件键序与字段名固定`() {
        val recorder = Recorder()
        val manager = jobs(
            recorder,
            FakeTransport(listOf(LlmDelta(content = "你好", reasoning = "想", finishReason = "stop")))
        )
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertEquals(
                """{"type":"delta","content":"你好","reasoning":"想"}""",
                recorder.payloads("delta").single(),
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `无工具调用时不出现 toolCalls 键`() {
        val recorder = Recorder()
        val manager = jobs(recorder, FakeTransport(listOf(LlmDelta(content = "x"))))
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertTrue(recorder.payloads("delta").single().endsWith(""""reasoning":""}"""))
            assertFalse(recorder.payloads("delta").single().contains("toolCalls"))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `done 事件带 finishReason，缺省为 stop`() {
        val recorder = Recorder()
        val manager = jobs(recorder, FakeTransport(listOf(LlmDelta(content = "x", finishReason = "length"))))
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertEquals("""{"type":"done","finishReason":"length"}""", recorder.payloads("done").single())
        } finally {
            manager.shutdown()
        }

        val fallbackRecorder = Recorder()
        val fallbackManager = jobs(fallbackRecorder, FakeTransport(listOf(LlmDelta(content = "x"))))
        try {
            fallbackManager.start(plan)
            assertTrue(fallbackRecorder.awaitTerminal())
            assertEquals("""{"type":"done","finishReason":"stop"}""", fallbackRecorder.payloads("done").single())
        } finally {
            fallbackManager.shutdown()
        }
    }

    @Test
    fun `error 事件带 message 与 retryable`() {
        val recorder = Recorder()
        val manager = jobs(
            recorder,
            FakeTransport(
                listOf(LlmDelta(error = LlmError("HTTP 500：boom", retryable = true), finishReason = "error"))
            ),
        )
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            val payload = recorder.payloads("error").single()
            assertTrue(payload.startsWith("""{"type":"error","message":"HTTP 500：boom","""))
            assertTrue(payload.endsWith(""""retryable":true}"""))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `usage 事件原样透传供应商对象`() {
        val recorder = Recorder()
        val usage = buildJsonObject { put("prompt_tokens", 11) }
        val manager = jobs(recorder, FakeTransport(listOf(LlmDelta(rawUsage = usage, finishReason = "stop"))))
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertEquals("""{"type":"usage","usage":{"prompt_tokens":11}}""", recorder.payloads("usage").single())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `toolCalls 以累积快照出现（不是增量分片）`() {
        val recorder = Recorder()
        val manager = jobs(
            recorder,
            FakeTransport(
                listOf(
                    LlmDelta(toolCalls = listOf(ToolCallDelta(0, "c1", "t", "{\"a\":")), finishReason = "stop"),
                    LlmDelta(toolCalls = listOf(ToolCallDelta(0, argumentsChunk = "1}"))),
                )
            ),
        )
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertTrue(
                recorder.payloads("delta").last().contains(
                    """"toolCalls":[{"id":"c1","type":"function","function":{"name":"t","arguments":"{\"a\":1}"}}]"""
                )
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `事件里的 jobId 与 plan 里的逐字节一致（原生不自己生成 id）`() {
        // JS 侧按 jobId 查处理器：原生若自己生成 id，前端 setHandler(jobId, fn) 会全部落空
        val uuid = "j-3f2a91c4-7d5e-4b08-9a11-2c6de0f7b3aa"
        listOf(uuid, "j-42", "j'quote\"double", "j 空格/斜杠").forEach { id ->
            val recorder = Recorder()
            val manager = jobs(recorder, FakeTransport(listOf(LlmDelta(content = "x"))))
            try {
                assertEquals("start 必须回显 plan 的 jobId", id, manager.start(planWith(id)))
                assertTrue(recorder.awaitTerminal())
                assertTrue(recorder.events.isNotEmpty())
                assertTrue("事件 jobId 必须回显：$id", recorder.events.all { it.first == id })
            } finally {
                manager.shutdown()
            }
        }
    }

    @Test
    fun `abort 用的也是 plan 里那个 jobId`() {
        val id = "j-abort-echo"
        val transport = FakeTransport(listOf(LlmDelta(content = "A")), gapMs = 5_000)
        val manager = jobs(null, transport, flushIntervalMs = 10_000)
        try {
            assertEquals(id, manager.start(planWith(id)))
            assertTrue(manager.isActive(id))
            assertTrue(manager.abort(id))
            assertFalse(manager.abort(id))
        } finally {
            manager.shutdown()
        }
    }

    // ---------- 节流 ----------

    @Test
    fun `高频增量被合并成一批（时钟冻结时不逐帧发出）`() {
        val recorder = Recorder()
        val frames = (1..50).map { LlmDelta(content = "字") }
        val manager = jobs(recorder, FakeTransport(frames), nowMillis = { 0L }, flushIntervalMs = 120L)
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertEquals(1, recorder.payloads("delta").size)
            assertTrue(recorder.payloads("delta").single().contains("字".repeat(50)))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `慢速流中间也会按节拍落地（不是全攒到结束）`() {
        val recorder = Recorder()
        val manager = jobs(
            recorder,
            FakeTransport(listOf(LlmDelta(content = "A"), LlmDelta(content = "B")), gapMs = 400),
            flushIntervalMs = 120L,
        )
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            val deltas = recorder.payloads("delta")
            assertTrue("应至少落地两批，实际 ${deltas.size}", deltas.size >= 2)
            assertTrue(deltas[0].contains(""""content":"A""""))
            assertTrue(deltas[1].contains(""""content":"B""""))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `流式增量跨帧拼接后是完整正文`() {
        val recorder = Recorder()
        val manager = jobs(
            recorder,
            FakeTransport(listOf(LlmDelta(content = "你"), LlmDelta(content = "好"), LlmDelta(content = "呀"))),
            nowMillis = { 0L },
        )
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            assertTrue(recorder.payloads("delta").single().contains(""""content":"你好呀""""))
        } finally {
            manager.shutdown()
        }
    }

    // ---------- 健壮性 ----------

    @Test
    fun `没有事件出口时不崩溃且任务照跑`() {
        val transport = FakeTransport(listOf(LlmDelta(content = "x", finishReason = "stop")))
        val manager = jobs(null, transport)
        try {
            assertEquals("j-1", manager.start(plan))
            awaitIdle(manager)
            assertEquals(0, manager.activeCount())
            assertEquals(1, transport.requests.size)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `事件出口抛错不影响任务收尾`() {
        val transport = FakeTransport(listOf(LlmDelta(content = "x", finishReason = "stop")))
        val manager = ChatJobs(
            sink = { ChatEventSink { _, _ -> throw IllegalStateException("evaluateJavascript 挂了") } },
            transport = transport,
        )
        try {
            assertEquals("j-1", manager.start(plan))
            awaitIdle(manager)
            assertEquals(0, manager.activeCount())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `传输层意外抛异常转成 error 事件而不是崩溃`() {
        val recorder = Recorder()
        val transport = FakeTransport(listOf(LlmDelta(content = "x")), throwAfterFrames = IllegalStateException("炸了"))
        val manager = jobs(recorder, transport)
        try {
            manager.start(plan)
            assertTrue(recorder.awaitTerminal())
            val payload = recorder.payloads("error").single()
            assertTrue(payload.contains("IllegalStateException"))
            assertTrue(payload.contains(""""retryable":false"""))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `出口返回 null 时静默丢弃（宿主还没接上 WebView）`() {
        val calls = AtomicInteger(0)
        val manager = ChatJobs(
            sink = {
                calls.incrementAndGet()
                null
            },
            transport = FakeTransport(listOf(LlmDelta(content = "x", finishReason = "stop"))),
        )
        try {
            assertEquals("j-1", manager.start(plan))
            awaitIdle(manager)
            assertTrue(calls.get() > 0)
        } finally {
            manager.shutdown()
        }
    }

    // ---------- 能力探测 ----------

    @Test
    fun `capabilities 形状与协议清单`() {
        assertEquals(
            """{"available":true,"protocols":["openai","anthropic","gemini"]}""",
            ChatJobs.capabilitiesJson(available = true),
        )
    }

    @Test
    fun `capabilities 不可用时给 reason 且不带 protocols`() {
        val parsed: JsonObject = JsonLenient.json
            .parseToJsonElement(ChatJobs.capabilitiesJson(available = false, reason = "no-bridge")).jsonObject
        assertFalse(parsed["available"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("no-bridge", parsed["reason"]!!.jsonPrimitive.content)
        assertNull(parsed["protocols"])
    }

    private companion object {
        fun eventTypeOf(json: String): String =
            Regex(""""type":"([a-z]+)"""").find(json)?.groupValues?.get(1) ?: ""
    }
}

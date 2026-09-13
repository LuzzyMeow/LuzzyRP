package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.CacheObserver
import com.luzzymeow.luzzyrp.chat.AgentLoop
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **批 A 验收的可执行版本**（PLAN §7.1）。
 *
 * 走的是**生产路径的全部环节**：`RequestBuilder.plan`（组装 + 落盘顺序）→ 按 `appends` 落盘
 * → `AgentLoop.run`（真实请求构造 + 传输 + 观测层记账）。只有传输被换成假的（零网络），
 * 所以这里的数字与真机上的数字应当同量级；真机实测见 WORKLOG 的探针记录。
 *
 * 两条判据：
 * 1. **连续多轮**：`avgCommonRatio ≥ 0.95`（目标 1.0）；
 * 2. **负控**：故意改一次设置 → **当轮**占比掉到 0.5 以下、累计占比跌破验收线、
 *    并记下一次 `headerChanged`（说明「缓存纪元」判据也是活的，不是永远报 0）。
 *
 * 负控为什么必须存在：一个永远返回 1.0 的仪表盘也能让判据 1 变绿。
 */
class BatchACacheAcceptanceTest {

    /** 记录每一次真实请求的假传输（零网络），并在流末尾给一个带缓存字段的 usage 帧。 */
    private class RecordingTransport : LlmTransport {
        val requests = mutableListOf<LlmRequest>()

        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            requests += request
            emit(LlmDelta(content = "回复"))
            emit(
                LlmDelta(
                    usage = LlmDelta.Usage(input = 1_000, output = 12),
                    rawUsage = buildJsonObject { put("prompt_cache_hit_tokens", JsonPrimitive(800)) },
                    finishReason = "stop",
                ),
            )
        }
    }

    private val config = TransportConfig(
        baseUrl = "https://example.invalid/v1",
        apiKey = "test-key",
        model = "test-model",
    )

    private val character = PromptAssembler.CharacterView(
        name = "谢昭",
        description = "旧书店的老板，说话很慢。",
        firstMes = "「来了。」",
    )

    private fun worldEntry(comment: String, content: String) = WorldEntry(
        comment = comment,
        content = content,
        constant = true,
        position = WorldPosition.AtDepth,
    )

    private fun input(
        preset: String = "你要用克制的语气说话。",
        extraWorld: List<WorldEntry> = emptyList(),
    ) = PromptAssembler.Input(
        character = character,
        presets = listOf(PresetEntry(name = "破限", content = preset)),
        worldEntries = listOf(worldEntry("雨夜", "今晚下着雨。")) + extraWorld,
        toolHint = "涉及设定时可先检索世界书。",
    )

    /** 跑一轮：算计划 → **按计划顺序落盘** → 真实走一遍引擎 → 追加模型回复。 */
    private fun turn(
        state: List<ChatMessage>,
        userText: String,
        promptInput: PromptAssembler.Input,
        transport: LlmTransport,
    ): List<ChatMessage> {
        val plan = RequestBuilder.plan(state, userText, promptInput)
        var next = state + plan.appends
        // 引擎是 suspend flow：这里用 runBlocking 收集（测试体本身已在 runTest 里，故用 toList 语义）
        kotlinx.coroutines.runBlocking {
            AgentLoop(transport, toolRunner = { _, _ -> "{}" }).run(config, plan.request).collect { }
        }
        next = next + ChatMessage.Ai(results = listOf(AiResult(raw = "回复：$userText")))
        return next
    }

    @Before
    fun setUp() = CacheObserver.reset()

    @After
    fun tearDown() = CacheObserver.reset()

    @Test
    fun `连续五轮真实对话的公共前缀占比达到验收线`() = runTest {
        val transport = RecordingTransport()
        var state: List<ChatMessage> = emptyList()
        // 刻意让相邻轮次共享词面（召回块逐轮变化 → 每轮都会追加一条新快照）：这是最坏情况
        listOf(
            "钟楼顶上长着红苹果树",
            "红苹果是谁种的",
            "红苹果树有多高",
            "红苹果好吃吗",
            "那棵红苹果树还在吗",
        ).forEach { text ->
            state = turn(state, text, input(), transport)
        }

        val summary = CacheObserver.current()
        assertEquals("五轮 = 五次真实请求", 5, summary.rounds)
        assertEquals("每一轮都真的发过请求", 5, transport.requests.size)
        // 实测数字写进测试报告（XML 的 system-out）：验收结论要能被复核，不能只有一句「绿了」
        println("[批A验收·连续五轮] " + summary.report())
        assertTrue(
            "实测公共前缀 ${summary.avgCommonRatio} 未达验收线 ${CacheObserver.TARGET_RATIO}｜${summary.report()}",
            summary.meetsTarget,
        )
        assertTrue("缓存命中率应当被测到（假传输给了 cached 字段）", (summary.hitRate ?: 0.0) > 0.0)
    }

    @Test
    fun `负控：改一次设置 → 当轮占比掉到 0_5 以下且累计跌破验收线并记一次纪元变化`() = runTest {
        val transport = RecordingTransport()
        var state: List<ChatMessage> = emptyList()
        listOf("第一句", "第二句", "第三句").forEach { text ->
            state = turn(state, text, input(), transport)
        }
        val before = CacheObserver.current()
        assertTrue("负控之前必须先在验收线之上（否则这条用例证明不了什么）", before.meetsTarget)
        assertEquals("还没改设置，不该有纪元变化", 0, before.headerChanges)

        // ① 改预设正文 → system 变了（同一轮请求从第一条消息起就不同）
        // ② 换模型 → 缓存纪元变了（旧前缀在新模型上不存在）
        val changedInput = input(preset = "你要用锋利的语气说话。")
        val changedConfig = config.copy(model = "another-model")
        val plan = RequestBuilder.plan(state, "第四句", changedInput)
        kotlinx.coroutines.runBlocking {
            AgentLoop(transport, toolRunner = { _, _ -> "{}" }).run(changedConfig, plan.request).collect { }
        }

        val after = CacheObserver.current()
        println("[批A验收·负控] " + after.report())
        assertTrue(
            "当轮占比应掉到 0.5 以下，实测 ${after.lastCommonRatio}｜${after.report()}",
            (after.lastCommonRatio ?: 1.0) < 0.5,
        )
        assertTrue(
            "累计占比应跌破验收线，实测 ${after.avgCommonRatio}",
            !after.meetsTarget,
        )
        assertEquals("换模型必须记一次缓存纪元变化", 1, after.headerChanges)
    }

    @Test
    fun `不改设置时纪元变化恒为 0（负控的反面）`() = runTest {
        val transport = RecordingTransport()
        var state: List<ChatMessage> = emptyList()
        repeat(4) { index ->
            state = turn(state, "第 ${index + 1} 句", input(), transport)
        }
        val summary = CacheObserver.current()
        assertEquals("头一直没变，就不该有纪元变化", 0, summary.headerChanges)
        assertEquals(1.0, summary.avgCommonRatio!!, 1e-9)
    }

    @Test
    fun `快照落盘顺序在每一轮都成立——存储顺序等于请求顺序`() = runTest {
        val transport = RecordingTransport()
        var state: List<ChatMessage> = emptyList()
        repeat(3) { index ->
            val text = "第 ${index + 1} 句"
            val plan = RequestBuilder.plan(state, text, input())
            // 落盘顺序：快照（若有）必须排在用户消息之前
            val snapshotAt = plan.appends.indexOfFirst { it is ChatMessage.Snapshot }
            val userAt = plan.appends.indexOfFirst { it is ChatMessage.User }
            if (snapshotAt >= 0) {
                assertTrue("第 ${index + 1} 轮：快照必须先落盘", snapshotAt < userAt)
            }
            state = turn(state, text, input(), transport)
        }
        assertTrue("这三轮里至少有一轮发过快照（否则用例没测到该测的）",
            state.any { it is ChatMessage.Snapshot })
    }
}

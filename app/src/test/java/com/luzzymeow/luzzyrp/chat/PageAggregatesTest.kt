package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **页面聚合层**（`UsageAggregate` / `MemoryStats`）的纯函数门禁。
 *
 * ## 为什么第一组用例直接用真实迁移夹具
 *
 * 用量与记忆的**字段名**是从上游数据里来的（`inputTokens` / `cacheReadTokens` /
 * `chunkMode` / `sourceRole`…）。手写样例只能证明「我按我以为的字段名解析对了」，
 * 证明不了「用户的真数据能解析出来」——而这正是批 C 要修的东西。
 * 所以第一批用例直接从 `webview-db-fixture.json` 里那条 `rp_hub_token_usage_history`
 * 与两条记忆取数，断言真实字段能被读出来。
 *
 * ## 判据形状：**同输入同输出**
 *
 * 任务点名要求的判据。聚合结果是页面数字的唯一来源，若它对同一份输入给出两种输出，
 * 用户会看到数字自己在跳（重组一次变一个值），而且不报错。
 */
class PageAggregatesTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val fixture: JsonObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        json.parseToJsonElement(text).jsonObject["databases"]!!.jsonObject["RPHubDB"]!!
            .jsonObject["entries"]!!.jsonObject
    }

    /** 真实用量记录（夹具里 9 条）。 */
    private fun realUsage(): List<UsageAggregate.Record> =
        fixture["rp_hub_token_usage_history"]!!.jsonArray.mapNotNull { UsageAggregate.Record.from(it) }

    /** 真实向量记忆（夹具里 2 条）。 */
    private fun realVectorMemories(): List<MemoryStats.Entry> {
        val key = fixture.keys.first { it.startsWith("rp_hub_memories_") }
        return fixture[key]!!.jsonArray.mapNotNull { MemoryStats.entryFrom(it) }
    }

    private fun realClassicMemories(): List<MemoryStats.Entry> {
        val key = fixture.keys.first { it.startsWith("rp_hub_classic_memories_") }
        return fixture[key]!!.jsonArray.mapNotNull { MemoryStats.entryFrom(it) }
    }

    // ────────────────────────── 真实数据（前提自证 + 字段名正确性）

    @Test
    fun `真实用量记录能被解析出来`() {
        val records = realUsage()

        assertEquals("夹具里应有 9 条用量记录（变了就更新这条断言）", 9, records.size)
        val first = records.first()
        assertTrue("输入 token 必须读出来（字段名 inputTokens）", first.inputTokens > 0)
        assertTrue("输出 token 必须读出来", first.outputTokens > 0)
        assertEquals("deepseek-chat", first.model)
        assertEquals("openai", first.protocol)
        assertTrue("时间戳必须是真值（字段名 timestamp）", first.timestamp > 0)
    }

    /** `cacheReadTokens` 是缓存命中率的**唯一**来源（上游 `recordApiUsage` 落的就是它）。 */
    @Test
    fun `真实用量里的缓存读 token 能读出来并算出命中率`() {
        val summarized = UsageAggregate.summarize(realUsage())

        assertNotNull("真实数据必须能算出命中率", summarized.cacheHitRate)
        assertTrue("夹具里的命中率应在 (0,1) 之间", summarized.cacheHitRate!! in 0.0..1.0)
        assertTrue("缓存读 token 必须非零", summarized.cacheReadTokens > 0)
        assertTrue("总用量必须非零", summarized.totalTokens > 0)
    }

    /**
     * 真实夹具里有一条 `reported = true` 但三个 token 数都是 0 的记录吗？
     * 不管有没有，**「没有真实用量」的记录必须被排除在总量之外**——这条用合成数据钉死，
     * 因为它决定「平均每轮 token」会不会被稀释。
     */
    @Test
    fun `没有真实用量的记录不进总量`() {
        val records = listOf(
            record(input = 100, output = 50, reported = true),
            record(input = 0, output = 0, reported = true),   // 报过但没有 token
            record(input = 500, output = 200, reported = false), // 没报（本地估算）→ 不算
        )
        val s = UsageAggregate.summarize(records)

        assertEquals("请求次数统计全部（那是真实发生过的请求）", 3, s.requests)
        assertEquals("但只有 1 条有真实用量", 1, s.measuredRequests)
        assertEquals(100, s.inputTokens)
        assertEquals(50, s.outputTokens)
        assertEquals(150, s.totalTokens)
    }

    @Test
    fun `真实记忆能被解析出来且两种形态字段都在`() {
        val vector = realVectorMemories()
        val classic = realClassicMemories()

        assertTrue("夹具里应有向量分片", vector.isNotEmpty())
        assertTrue("夹具里应有总结记忆", classic.isNotEmpty())

        val v = vector.first()
        assertTrue("覆盖轮次必须读出来（字段名 turn）", v.turn > 0)
        assertTrue("摘要长度必须非零", v.summaryChars > 0)
        assertEquals("paragraph", v.chunkMode)
        assertTrue("来源名应能读出来", v.sourceName.isNotBlank())

        val c = classic.first()
        assertTrue("总结记忆同样有轮次", c.turn > 0)
        assertTrue("总结记忆同样有正文字数", c.summaryChars > 0)
    }

    @Test
    fun `真实向量分片带嵌入维度`() {
        val s = MemoryStats.summarize(realVectorMemories())

        assertTrue("夹具里的向量分片应已嵌入（有 embeddingDims）", s.embeddedShards > 0)
        assertTrue("维度必须是非零", s.embeddingDims > 0)
    }

    // ────────────────────────── 聚合算术

    private fun record(
        input: Int,
        output: Int,
        cached: Int = 0,
        provider: String = "deepseek",
        model: String = "deepseek-chat",
        timestamp: Long = 1_700_000_000_000,
        reported: Boolean = true,
    ) = UsageAggregate.Record(
        timestamp = timestamp,
        type = "chat",
        model = model,
        provider = provider,
        protocol = "openai",
        inputTokens = input,
        outputTokens = output,
        totalTokens = input + output,
        cacheReadTokens = cached,
        durationMs = 1000,
        finishReason = "stop",
        reported = reported,
    )

    @Test
    fun `按供应商与模型分桶`() {
        val s = UsageAggregate.summarize(
            listOf(
                record(100, 50, provider = "deepseek"),
                record(200, 60, provider = "deepseek"),
                record(300, 70, provider = "openai", model = "gpt-4o"),
            ),
            dayOf = { "2026-09-14" },
        )

        assertEquals("按用量降序（deepseek 两条 410 总量更大）", listOf("deepseek", "openai"), s.byProvider.map { it.key })
        // totalTokens = input + output，所以分桶总量是「输入+输出」的和（不是只有输入）
        assertEquals("(100+50) + (200+60) = 410", 410, s.byProvider[0].totalTokens)
        assertEquals(2, s.byProvider[0].requests)
        assertEquals("300 + 70 = 370", 370, s.byProvider[1].totalTokens)
        // 模型维度同样按用量降序：deepseek-chat 两条合计 410 > gpt-4o 的 370
        assertEquals(listOf("deepseek-chat", "gpt-4o"), s.byModel.map { it.key })
        assertEquals(410, s.byModel[0].totalTokens)
    }

    /** 缺 provider / model 时给「未知」占位名，**不显示空串**（空串在列表里看着像 bug）。 */
    @Test
    fun `缺供应商与模型名时用未知占位`() {
        val s = UsageAggregate.summarize(
            listOf(record(10, 5, provider = "", model = "")),
            dayOf = { "2026-09-14" },
        )
        assertEquals(listOf(UsageAggregate.UNKNOWN), s.byProvider.map { it.key })
        assertEquals(listOf(UsageAggregate.UNKNOWN), s.byModel.map { it.key })
    }

    /** 分桶顺序**稳定**：用量相同时按名字（否则每次重组顺序可能变 → 列表跳动）。 */
    @Test
    fun `同用量时排序稳定`() {
        val s = UsageAggregate.summarize(
            listOf(
                record(100, 0, provider = "zeta"),
                record(100, 0, provider = "alpha"),
                record(100, 0, provider = "mid"),
            ),
            dayOf = { "2026-09-14" },
        )
        assertEquals(listOf("alpha", "mid", "zeta"), s.byProvider.map { it.key })
        // 再跑一次仍相同（同输入同输出）
        val again = UsageAggregate.summarize(
            listOf(
                record(100, 0, provider = "zeta"),
                record(100, 0, provider = "alpha"),
                record(100, 0, provider = "mid"),
            ),
            dayOf = { "2026-09-14" },
        )
        assertEquals(s.byProvider.map { it.key }, again.byProvider.map { it.key })
    }

    @Test
    fun `按天分桶并升序`() {
        val s = UsageAggregate.summarize(
            listOf(
                record(10, 5, timestamp = 3),
                record(20, 5, timestamp = 1),
                record(30, 5, timestamp = 1),
            ),
            dayOf = { ts -> when (ts) { 1L -> "2026-09-13"; else -> "2026-09-14" } },
        )
        assertEquals(listOf("2026-09-13", "2026-09-14"), s.byDay.map { it.day })
        assertEquals(2, s.byDay[0].requests)
        // 同一天两条：(20+5) + (30+5) = 60
        assertEquals(60, s.byDay[0].totalTokens)
        assertEquals(1, s.byDay[1].requests)
    }

    @Test
    fun `首末时间戳与平均每轮`() {
        val s = UsageAggregate.summarize(
            listOf(
                record(100, 100, timestamp = 5),
                record(200, 200, timestamp = 9),
            ),
            dayOf = { "2026-09-14" },
        )
        assertEquals(5L, s.firstTimestamp)
        assertEquals(9L, s.lastTimestamp)
        assertEquals("(200+400)/2 = 300", 300.0, s.avgTokensPerRequest!!, 1e-9)
    }

    @Test
    fun `空输入给空汇总而不是崩`() {
        val s = UsageAggregate.summarize(emptyList())

        assertTrue(s.isEmpty)
        assertEquals(0, s.requests)
        assertNull("没有样本就不给命中率（不编造 0%）", s.cacheHitRate)
        assertNull("没有样本就不给平均值", s.avgTokensPerRequest)
        assertNull(s.firstTimestamp)
        assertTrue(s.byProvider.isEmpty())
        assertTrue(s.byDay.isEmpty())
    }

    /** 输入为 0 时命中率是 null 而不是 0（没有分母，别编造 0%）。 */
    @Test
    fun `输入为 0 时不算命中率`() {
        val s = UsageAggregate.summarize(
            listOf(record(0, 10, cached = 0)),
            dayOf = { "2026-09-14" },
        )
        assertNull(s.cacheHitRate)
        assertNull(s.byProvider.first().cacheHitRate)
    }

    /** 记录解析：缺失字段给默认值，非法元素返回 null（不是抛）。 */
    @Test
    fun `记录解析的边界`() {
        assertNull("非对象 → null", UsageAggregate.Record.from(kotlinx.serialization.json.JsonPrimitive("x")))
        val empty = UsageAggregate.Record.from(JsonObject(emptyMap()))
        assertNotNull("空对象也要能解析（全默认值）", empty)
        assertEquals(0, empty!!.inputTokens)
        assertEquals("", empty.model)
        assertFalse(empty.reported)
        assertFalse("没有 token 且有 reported=false → 不算有真实用量", empty.hasTokens)
    }

    /** 字符串形态的 token 数（旧数据里可能出现）也要能读。 */
    @Test
    fun `token 数字段的字符串形态`() {
        val element = json.parseToJsonElement("""{"inputTokens":"123","outputTokens":"45","reported":true}""")
        val r = UsageAggregate.Record.from(element)
        assertNotNull(r)
        assertEquals(123, r!!.inputTokens)
        assertEquals(45, r.outputTokens)
    }

    // ────────────────────────── 记忆统计

    private fun mem(turn: Int, chars: Int, enabled: Boolean = true, dims: Int = 0, mode: String = "paragraph") =
        MemoryStats.Entry(
            turn = turn,
            enabled = enabled,
            chunkMode = mode,
            sourceRole = "mixed",
            sourceName = "A + B",
            embeddingDims = dims,
            summaryChars = chars,
        )

    /** **覆盖轮数是去重计数**，不是条数：一轮可能被切成多个分片。 */
    @Test
    fun `覆盖轮数按去重算而不是条数`() {
        val s = MemoryStats.summarize(
            listOf(mem(1, 100), mem(1, 120), mem(2, 80), mem(3, 60)),
        )
        assertEquals(4, s.shards)
        assertEquals("只覆盖 3 个轮次（第 1 轮有两个分片）", 3, s.coveredTurns)
        assertEquals(3, s.maxTurn)
    }

    @Test
    fun `停用的分片不计入启用数但仍在总数里`() {
        val s = MemoryStats.summarize(listOf(mem(1, 10), mem(2, 10, enabled = false)))
        assertEquals(2, s.shards)
        assertEquals(1, s.enabledShards)
    }

    @Test
    fun `平均字数按条数算`() {
        val s = MemoryStats.summarize(listOf(mem(1, 100), mem(2, 201)))
        assertEquals(301, s.totalChars)
        assertEquals("整数除法：301/2 = 150", 150, s.averageChars)
    }

    @Test
    fun `分块方式按出现次数排序且稳定`() {
        val s = MemoryStats.summarize(
            listOf(mem(1, 1, mode = "paragraph"), mem(2, 1, mode = "sentence"), mem(3, 1, mode = "paragraph")),
        )
        assertEquals(listOf("paragraph", "sentence"), s.chunkModes)
        // 空 chunkMode（总结记忆形态）不进列表
        assertEquals(emptyList<String>(), MemoryStats.summarize(listOf(mem(1, 1, mode = ""))).chunkModes)
    }

    @Test
    fun `空记忆给空统计`() {
        val s = MemoryStats.summarize(emptyList())
        assertTrue(s.isEmpty)
        assertEquals(0, s.shards)
        assertEquals(0, s.embeddedShards)
        assertEquals(0, s.embeddingDims)
        assertTrue(s.chunkModes.isEmpty())
    }

    // ────────────────────────── 同输入同输出（任务点名的判据）

    @Test
    fun `聚合是确定性的——真实数据两次结果逐字段相同`() {
        val first = UsageAggregate.summarize(realUsage())
        val second = UsageAggregate.summarize(realUsage())

        assertEquals(first, second)
        assertEquals(first.byProvider, second.byProvider)
        assertEquals(first.byModel, second.byModel)
        assertEquals(first.byDay, second.byDay)
        // data class 的 equals 已经覆盖全部字段；这里再确认一次「不是碰巧」
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `记忆统计也是确定性的`() {
        assertEquals(
            MemoryStats.summarize(realVectorMemories()),
            MemoryStats.summarize(realVectorMemories()),
        )
    }

    /** 聚合成本的量级（宽松界，只拦灾难性回归）。 */
    @Test
    fun `聚合成本的量级`() {
        val many = (1..5000).map { record(100 + it % 100, 50, provider = "p${it % 7}", model = "m${it % 13}") }
        val elapsed = kotlin.system.measureNanoTime { UsageAggregate.summarize(many) } / 1_000_000
        println("PERF UsageAggregate 5000 条 = ${elapsed}ms")

        assertTrue("5000 条聚合超过 2s 说明数量级变坏了，实际 ${elapsed}ms", elapsed < 2000)
    }

    /**
     * **记忆的作用域是「角色 × 分支」**（会话 76 修掉的一个静默错数）。
     *
     * 第一版 `PageDataSource.memory` 用的是 `ScopeId(uuid)`（默认落到主线 `main`），
     * 而用户的活跃会话**经常不在主线上**。症状：记忆页说 0 条，对话里明明有记忆——
     * 不报错、不崩溃，数字永远不对。
     *
     * 这条用例把**存储后缀**的形状钉住（真正读库的那一层在仪器化测试里，
     * 那里会用一个非主线的活跃分支来跑）：
     * 主线 = 裸 uuid；分支 = `角色__branch__分支`。
     */
    @Test
    fun `记忆作用域必须带分支而不是默认主线`() {
        val character = "e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555"
        val mainScope = com.luzzymeow.luzzyrp.data.legacy.ScopeId(character)
        val branchScope = com.luzzymeow.luzzyrp.data.legacy.ScopeId(character, "b1")

        assertEquals("主线作用域是裸 uuid（上游约定）", character, mainScope.suffix())
        assertEquals("$character" + "__branch__b1", branchScope.suffix())
        // ★ 关键断言：两者**不是**同一个作用域——若实现只按角色取，读到的就是主线那份
        assertTrue(
            "主线与分支必须是两个不同的作用域（否则「按角色取」会静默读到错的那一份）",
            mainScope != branchScope && mainScope.suffix() != branchScope.suffix(),
        )
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
    }
}

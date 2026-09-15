package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 用量趋势图的纯逻辑（用量统计页 v3.2）。
 *
 * 判据全卡在「数对不对」上：分桶边界、系列归类、筛选生效、超限合并。
 * 这些错法在界面上都**不报错**——曲线整体偏一格、某格数字跑到隔壁、筛选没生效但看着正常，
 * 只有把算术抽成纯函数才能逐条钉住。
 */
class UsageChartTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」：2026-09-15 14:30（东八区）。 */
    private val now = java.time.ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant().toEpochMilli()

    private fun record(
        minutesAgo: Long,
        input: Int = 100,
        output: Int = 50,
        provider: String = "deepseek",
        model: String = "deepseek-chat",
        type: String = "chat",
        cached: Int = 0,
    ) = UsageAggregate.Record(
        timestamp = now - minutesAgo * 60_000,
        type = type,
        model = model,
        provider = provider,
        protocol = "openai",
        inputTokens = input,
        outputTokens = output,
        totalTokens = input + output,
        cacheReadTokens = cached,
        durationMs = 1000,
        finishReason = "stop",
        reported = true,
    )

    // ────────────────────────── 分类

    @Test
    fun `类型映射成三档，未知类型落主对话`() {
        assertEquals(UsageChart.Category.MEMORY, UsageChart.categoryOf("summary"))
        assertEquals(UsageChart.Category.MEMORY, UsageChart.categoryOf("embedding"))
        assertEquals(UsageChart.Category.VARIABLES, UsageChart.categoryOf("ui_template"))
        assertEquals(UsageChart.Category.CHAT, UsageChart.categoryOf("chat"))
        // 空 / 大小写 / 前后空格都不该把它算成记忆或变量
        assertEquals(UsageChart.Category.CHAT, UsageChart.categoryOf(""))
        assertEquals(UsageChart.Category.MEMORY, UsageChart.categoryOf(" Summary "))
    }

    // ────────────────────────── 分桶

    @Test
    fun `日粒度是 24 个整点桶，最后一格叫现在`() {
        val buckets = UsageChart.buckets(UsageChart.Granularity.DAY, now, zone)
        assertEquals(24, buckets.size)
        assertEquals("现在", buckets.last().label)
        // 上一格是 13:00-14:00（标签按桶的**起始小时**走，与旧版 `getHours()` 同规则）
        assertEquals("13时", buckets[buckets.size - 2].label)
        // 首格是 23 小时前（本地时区）
        val firstHour = java.time.Instant.ofEpochMilli(buckets.first().startMs).atZone(zone).hour
        assertEquals(15, firstHour)
    }

    @Test
    fun `周粒度是 7 个整天桶，最后一格叫今天`() {
        val buckets = UsageChart.buckets(UsageChart.Granularity.WEEK, now, zone)
        assertEquals(7, buckets.size)
        assertEquals("今天", buckets.last().label)
        assertEquals("9/14", buckets[buckets.size - 2].label)
    }

    @Test
    fun `月粒度是 4 个周桶`() {
        val buckets = UsageChart.buckets(UsageChart.Granularity.MONTH, now, zone)
        assertEquals(4, buckets.size)
        // 4 桶 × 7 天 = 28 天，正好覆盖 windowMs
        val span = buckets.last().endMs - buckets.first().startMs
        assertEquals(UsageChart.windowMs(UsageChart.Granularity.MONTH), span)
    }

    @Test
    fun `桶与桶首尾相接、不留缝也不重叠`() {
        UsageChart.Granularity.entries.forEach { granularity ->
            val buckets = UsageChart.buckets(granularity, now, zone)
            for (index in 0 until buckets.size - 1) {
                assertEquals(
                    "粒度 $granularity 的第 $index 格与下一格之间有缝",
                    buckets[index].endMs,
                    buckets[index + 1].startMs,
                )
            }
        }
    }

    // ────────────────────────── 筛选选项

    @Test
    fun `供应商选项按名字排序，只有存在未归属时才给未归属`() {
        val withBlank = UsageChart.providerOptions(
            listOf(record(1, provider = "zhipu"), record(2, provider = "deepseek"), record(3, provider = "")),
        )
        assertEquals(listOf("全部供应商", "deepseek", "zhipu", "未归属"), withBlank.map { it.second })
        assertNull(withBlank.first().first)

        val withoutBlank = UsageChart.providerOptions(listOf(record(1, provider = "deepseek")))
        assertEquals(listOf("全部供应商", "deepseek"), withoutBlank.map { it.second })
    }

    @Test
    fun `模型选项按用量降序，标签是供应商加模型`() {
        val options = UsageChart.modelOptions(
            records = listOf(
                record(1, input = 10, output = 0, model = "small"),
                record(2, input = 500, output = 0, model = "big"),
            ),
            category = UsageChart.Category.ALL,
            provider = null,
        )
        assertEquals(listOf("deepseek · big", "deepseek · small"), options.map { it.second })
    }

    // ────────────────────────── 图表数据

    @Test
    fun `用量口径是输入加输出，不含缓存读（有意偏离旧版）`() {
        // 缓存读在协议里是 input 的子集，再加一次就是重复计数（开缓存后用量图凭空涨一截）
        val r = record(1, input = 1000, output = 200, cached = 800)
        assertEquals(1200, UsageChart.usageOf(r))
    }

    @Test
    fun `记录落进正确的桶里`() {
        val data = UsageChart.build(
            records = listOf(record(minutesAgo = 30, input = 300, output = 0)), // 落在最后一格（现在）
            granularity = UsageChart.Granularity.DAY,
            category = UsageChart.Category.ALL,
            provider = null,
            now = now,
            zone = zone,
        )
        assertEquals(1, data.series.size)
        assertEquals(24, data.series.first().totals.size)
        assertEquals(300, data.series.first().totals.last())
        assertEquals(0, data.series.first().totals.dropLast(1).sum())
        assertEquals(300, data.peak)
    }

    @Test
    fun `窗口之外的旧记录不画、也不进峰值`() {
        val data = UsageChart.build(
            records = listOf(
                record(minutesAgo = 30, input = 100, output = 0),
                record(minutesAgo = 60 * 40, input = 999_999, output = 0), // 40 小时前：日粒度之外
            ),
            granularity = UsageChart.Granularity.DAY,
            category = UsageChart.Category.ALL,
            provider = null,
            now = now,
            zone = zone,
        )
        assertEquals(100, data.series.first().totals.sum())
        assertEquals(100, data.peak)
    }

    @Test
    fun `分类与供应商筛选各自生效`() {
        val records = listOf(
            record(10, type = "chat", provider = "deepseek", input = 100, output = 0),
            record(20, type = "summary", provider = "deepseek", input = 200, output = 0),
            record(30, type = "chat", provider = "zhipu", input = 400, output = 0),
        )
        val memoryOnly = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.MEMORY, null, now = now, zone = zone,
        )
        assertEquals(200, memoryOnly.series.sumOf { s -> s.totals.sum() })

        val deepseekOnly = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, "deepseek", now = now, zone = zone,
        )
        assertEquals(300, deepseekOnly.series.sumOf { s -> s.totals.sum() })

        // 未归属筛选：没有未归属记录时结果必须是空（不是「回落成全选」）
        val unassigned = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, UsageChart.UNASSIGNED, now = now, zone = zone,
        )
        assertTrue(unassigned.isEmpty)
    }

    @Test
    fun `模型勾选：空集等于全选，非空集只画选中的`() {
        val records = listOf(
            record(10, model = "a", input = 100, output = 0),
            record(20, model = "b", input = 200, output = 0),
        )
        val all = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, null, now = now, zone = zone,
        )
        assertEquals(2, all.series.size)

        val onlyA = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, null,
            selectedModels = setOf(UsageChart.seriesKey("deepseek", "a")), now = now, zone = zone,
        )
        assertEquals(1, onlyA.series.size)
        assertEquals(100, onlyA.series.first().totals.sum())

        // 勾了一个当前筛选下不存在的系列 → 空图（而不是悄悄回落成全选）
        val none = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, null,
            selectedModels = setOf(UsageChart.seriesKey("deepseek", "不存在")), now = now, zone = zone,
        )
        assertTrue(none.isEmpty)
    }

    @Test
    fun `超过 8 条系列时尾部并成其他模型`() {
        val records = (1..11).map { index ->
            record(index.toLong(), model = "m$index", input = (100 - index) * 10, output = 0)
        }
        val data = UsageChart.build(
            records, UsageChart.Granularity.DAY, UsageChart.Category.ALL, null, now = now, zone = zone,
        )
        assertEquals(UsageChart.MAX_SERIES + 1, data.series.size)
        assertEquals(UsageChart.OVERFLOW_KEY, data.series.last().key)
        assertEquals("其他模型（3）", data.series.last().label)
        // 合并项存在说明被合并的系列**没有丢数据**：所有系列之和 == 全部记录之和
        assertEquals(records.sumOf { UsageChart.usageOf(it) }, data.series.sumOf { s -> s.totals.sum() })
        assertEquals(3, data.overflowCount)
    }

    @Test
    fun `零用量记录不入图（避免画出无意义的 0 点）`() {
        val data = UsageChart.build(
            records = listOf(record(10, input = 0, output = 0), record(20, input = 5, output = 0)),
            granularity = UsageChart.Granularity.DAY,
            category = UsageChart.Category.ALL,
            provider = null,
            now = now,
            zone = zone,
        )
        assertEquals(1, data.series.size)
        assertEquals(5, data.series.first().totals.sum())
    }

    @Test
    fun `系统时间戳缺失的记录被忽略，不落进第一格`() {
        val broken = record(10).copy(timestamp = 0)
        val data = UsageChart.build(
            records = listOf(broken, record(20, input = 7, output = 0)),
            granularity = UsageChart.Granularity.DAY,
            category = UsageChart.Category.ALL,
            provider = null,
            now = now,
            zone = zone,
        )
        assertEquals(7, data.series.sumOf { s -> s.totals.sum() })
    }

    @Test
    fun `峰值恒至少为 1（全 0 时不除零）`() {
        val data = UsageChart.build(
            records = emptyList(),
            granularity = UsageChart.Granularity.WEEK,
            category = UsageChart.Category.ALL,
            provider = null,
            now = now,
            zone = zone,
        )
        assertEquals(1, data.peak)
        assertTrue(data.isEmpty)
        assertEquals(7, data.buckets.size)
    }

    @Test
    fun `系列键能拆回供应商与模型（供应商为空也算一个独立系列）`() {
        assertEquals("deepseek" to "deepseek-chat", UsageChart.splitKey("deepseek::deepseek-chat"))
        assertEquals("" to "m", UsageChart.splitKey("::m"))
        assertEquals("deepseek · deepseek-chat", UsageChart.seriesLabel("deepseek", "deepseek-chat"))
        assertEquals("m", UsageChart.seriesLabel("", "m"))
        // 供应商知道、模型空：仍要写出供应商（只写「未知模型」会让人以为是数据缺失）
        assertEquals("deepseek · 未知模型", UsageChart.seriesLabel("deepseek", ""))
    }
}

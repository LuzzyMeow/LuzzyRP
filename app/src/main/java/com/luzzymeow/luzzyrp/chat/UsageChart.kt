package com.luzzymeow.luzzyrp.chat

import java.time.Instant
import java.time.ZoneId

/**
 * **用量趋势图的纯逻辑层**（用量统计页 v3.2 重建）。
 *
 * ## 为什么单独立一层
 *
 * 折线图的每一条曲线都是「按时间窗分桶 → 按系列累加」的算术，出错的形态全是**静默错数**：
 * 时间窗算错 → 曲线整体平移一格；分桶边界错 → 某个点的值跑到隔壁；筛选忘传 → 数字看着对但
 * 与实际不符。这些在界面上都「看起来正常」，只有把算术抽成纯函数才能逐条断言。
 *
 * ## 口径照旧版（WebView）翻译，不是自己发明
 *
 * 源：`tools/patches/entities/012-036-app-js.patch` 的 patch 025（`buildUsageChartBuckets` /
 * `usageChartData` / `usageChartModelOptions` 等）。三粒度与分桶规则逐条对齐：
 *
 * | 粒度 | 窗口 | 桶数 | 桶标签 |
 * |---|---|---|---|
 * | [Granularity.DAY] | 近 24 小时 | 24（每小时一桶） | `23时` … `现在` |
 * | [Granularity.WEEK] | 近 7 天 | 7（每天一桶） | `M/D` … `今天` |
 * | [Granularity.MONTH] | 近 28 天 | 4（每周一桶） | `M/D` |
 *
 * **一条记录的「用量」= 输入 + 输出 + 缓存读**（同旧版 `total`）。刻意不用 `totalTokens`：
 * 旧数据里那个字段有一批是 0（上游早期版本没写它），用它会把真实用量画成空图。
 *
 * ## 分类（类型过滤）
 *
 * 旧版把 `type` 折成三档：`summary` / `embedding` → 记忆系统；`ui_template` → 变量分析；
 * 其余（含空）→ 主对话。页面上「全部」以外选哪一档，图表与统计都只看那一档。
 */
object UsageChart {

    /** 图表粒度（与旧版 `usageChartRange` 同义）。 */
    enum class Granularity(val label: String) {
        DAY("日"),
        WEEK("周"),
        MONTH("月"),
        ;

        companion object {
            fun fromKey(key: String): Granularity =
                entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DAY
        }
    }

    /** 类型分类（与旧版 `isUsageChartCategory` 同义）。 */
    enum class Category(val label: String) {
        ALL("全部"),
        CHAT("主对话"),
        MEMORY("记忆系统"),
        VARIABLES("变量分析"),
    }

    /** 一条记录归属哪一类（`type` 为空按主对话算）。 */
    fun categoryOf(type: String): Category = when (type.trim().lowercase()) {
        "summary", "embedding" -> Category.MEMORY
        "ui_template" -> Category.VARIABLES
        else -> Category.CHAT
    }

    /**
     * 一条记录的用量口径：**输入 + 输出**。
     *
     * **有意偏离旧版**（旧版是「输入 + 输出 + 缓存读」，见 patch 025）。理由：OpenAI 兼容协议里
     * `cacheReadTokens` 是 `inputTokens` 的**子集**（命中缓存那部分输入），再单独加一次就是重复计数——
     * 表现是「开了缓存之后用量图凭空涨一截」。
     * 偏离的代价是图表与旧版数字不完全可比；收益是**图表各系列之和 == 总用量卡的大字**
     * （都走 `inputTokens + outputTokens`），页面自己不会自相矛盾。
     */
    fun usageOf(record: UsageAggregate.Record): Int =
        record.inputTokens + record.outputTokens

    /** 一个时间桶（`[startMs, endMs)`）。 */
    data class Bucket(val startMs: Long, val endMs: Long, val label: String)

    /** 一条曲线（一个「供应商 :: 模型」）。 */
    data class Series(
        val key: String,
        val label: String,
        /** 按总量降序后的序号（决定配色，0 = 量最大）。 */
        val rank: Int,
        val totals: List<Int>,
    )

    /** 图表数据。[peak] 恒 ≥1（避免除零），[overflowCount] > 0 表示尾部被并成「其他模型」。 */
    data class Data(
        val buckets: List<Bucket>,
        val series: List<Series>,
        val peak: Int,
        val overflowCount: Int = 0,
    ) {
        val isEmpty: Boolean get() = series.isEmpty()
        val maxSeries: Int = MAX_SERIES
    }

    /** 单图最多画几条曲线（旧版同值），超出的并成「其他模型（N）」。 */
    const val MAX_SERIES = 8

    /** 未归属（记录里没有 provider）在筛选里的键。 */
    const val UNASSIGNED = ""

    /**
     * 建桶。
     *
     * @param now 当前时刻（注入以便单测固定时间）
     * @param zone 时区（用系统默认——用户看的是「我今天用了多少」，按 UTC 日切会让晚上 8 点后的
     *   用量落到「明天」，与 [UsageAggregate] 的 `byDay` 同一条纪律）
     */
    fun buckets(granularity: Granularity, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Bucket> {
        val zoneNow = Instant.ofEpochMilli(now).atZone(zone)
        return when (granularity) {
            Granularity.DAY -> {
                val hourStart = zoneNow.withMinute(0).withSecond(0).withNano(0)
                (23 downTo 0).map { index ->
                    val start = hourStart.minusHours(index.toLong())
                    Bucket(
                        startMs = start.toInstant().toEpochMilli(),
                        endMs = start.plusHours(1).toInstant().toEpochMilli(),
                        // 最后一格是「现在」而不是小时数：它与「刚才那条请求」对得上，
                        // 而 `14时` 在 14:59 看着像「这一小时已经过完了」
                        label = if (index == 0) "现在" else "${start.hour}时",
                    )
                }
            }

            Granularity.WEEK -> {
                val dayStart = zoneNow.withHour(0).withMinute(0).withSecond(0).withNano(0)
                (6 downTo 0).map { index ->
                    val start = dayStart.minusDays(index.toLong())
                    Bucket(
                        startMs = start.toInstant().toEpochMilli(),
                        endMs = start.plusDays(1).toInstant().toEpochMilli(),
                        label = if (index == 0) "今天" else "${start.monthValue}/${start.dayOfMonth}",
                    )
                }
            }

            Granularity.MONTH -> {
                val dayStart = zoneNow.withHour(0).withMinute(0).withSecond(0).withNano(0)
                (3 downTo 0).map { index ->
                    // 每格 7 天，最后一格到今天为止（旧版同规则：`(index * 7 + 6)` 天前 → `index * 7` 天前）
                    val start = dayStart.minusDays((index * 7 + 6).toLong())
                    val end = dayStart.minusDays((index * 7).toLong()).plusDays(1)
                    Bucket(
                        startMs = start.toInstant().toEpochMilli(),
                        endMs = end.toInstant().toEpochMilli(),
                        label = "${start.monthValue}/${start.dayOfMonth}",
                    )
                }
            }
        }
    }

    /** 窗口毫秒数（筛选用；与桶的总跨度一致）。 */
    fun windowMs(granularity: Granularity): Long = when (granularity) {
        Granularity.DAY -> 24L * 60 * 60 * 1000
        Granularity.WEEK -> 7L * 60 * 60 * 24 * 1000
        Granularity.MONTH -> 28L * 60 * 60 * 24 * 1000
    }

    /** 系列的键：`供应商::模型`（与旧版一致；供应商为空时键以 `::` 开头，不等于忽略供应商）。 */
    fun seriesKey(provider: String, model: String): String = "$provider::$model"

    /** 系列展示名：`供应商名 · 模型名`；供应商为空就只写模型名。 */
    fun seriesLabel(provider: String, model: String): String {
        val bare = model.trim().ifBlank { "未知模型" }
        return if (provider.isBlank()) bare else "$provider · $bare"
    }

    /** 拆开系列键（返回 `供应商 to 模型`）。 */
    fun splitKey(key: String): Pair<String, String> {
        val at = key.indexOf("::")
        return if (at < 0) "" to key else key.substring(0, at) to key.substring(at + 2)
    }

    /**
     * 供应商筛选选项（旧版 `usageChartProviderOptions`）：`全部` + 出现过的供应商（按名字排序）
     * + 有未归属记录时才给 `未归属`。
     *
     * 只有出现过的供应商才列——列一堆零记录的选项等于让用户点进去看空图。
     */
    fun providerOptions(records: List<UsageAggregate.Record>): List<Pair<String?, String>> {
        val names = records.map { it.provider }.filter { it.isNotBlank() }.distinct().sorted()
        val options = mutableListOf<Pair<String?, String>>(null to "全部供应商")
        options += names.map { it as String? to it }
        if (records.any { it.provider.isBlank() }) options += UNASSIGNED to "未归属"
        return options
    }

    /** 模型系列选项（供应商筛选之后，按用量降序）。 */
    fun modelOptions(
        records: List<UsageAggregate.Record>,
        category: Category,
        provider: String?,
    ): List<Pair<String, String>> {
        val totals = LinkedHashMap<String, Int>()
        records.forEach { record ->
            if (!matches(record, category, provider)) return@forEach
            val key = seriesKey(record.provider, record.model)
            totals[key] = (totals[key] ?: 0) + usageOf(record)
        }
        return totals.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    // 同量按标签排：稳定的展示顺序（否则每次重组顺序可能变 → 图例乱跳）
                    .thenBy { splitKey(it.key).let { (p, m) -> seriesLabel(p, m) } },
            )
            .map { entry ->
                val (providerName, model) = splitKey(entry.key)
                entry.key to seriesLabel(providerName, model)
            }
    }

    /** 一条记录是否属于当前筛选（[provider] = null 表示不筛；[UNASSIGNED] 表示只要未归属）。 */
    fun matches(record: UsageAggregate.Record, category: Category, provider: String?): Boolean {
        if (category != Category.ALL && categoryOf(record.type) != category) return false
        if (provider == null) return true
        return record.provider == provider
    }

    /**
     * 组装图表数据。
     *
     * @param selectedModels 选中的系列键（**空集 = 全选**，与旧版 `selected.length && !includes` 同义）
     * @param now 当前时刻（注入以便单测固定）
     */
    fun build(
        records: List<UsageAggregate.Record>,
        granularity: Granularity,
        category: Category,
        provider: String?,
        selectedModels: Set<String> = emptySet(),
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Data {
        val buckets = buckets(granularity, now, zone)
        val cutoff = now - windowMs(granularity)
        val totals = LinkedHashMap<String, IntArray>()
        records.forEach { record ->
            if (record.timestamp <= 0 || record.timestamp < cutoff) return@forEach
            if (!matches(record, category, provider)) return@forEach
            val key = seriesKey(record.provider, record.model)
            if (selectedModels.isNotEmpty() && key !in selectedModels) return@forEach
            val usage = usageOf(record)
            if (usage <= 0) return@forEach
            val index = buckets.indexOfFirst { record.timestamp >= it.startMs && record.timestamp < it.endMs }
            if (index < 0) return@forEach
            val row = totals.getOrPut(key) { IntArray(buckets.size) }
            row[index] += usage
        }

        val ranked = totals.entries
            .map { it.key to it.value }
            .sortedWith(
                compareByDescending<Pair<String, IntArray>> { it.second.sum() }
                    .thenBy { it.first },
            )
        val kept = ranked.take(MAX_SERIES).mapIndexed { index, (key, row) ->
            val (providerName, model) = splitKey(key)
            Series(
                key = key,
                label = seriesLabel(providerName, model),
                rank = index,
                totals = row.toList(),
            )
        }
        val overflow = ranked.drop(MAX_SERIES)
        val series = if (overflow.isEmpty()) {
            kept
        } else {
            val merged = IntArray(buckets.size)
            overflow.forEach { (_, row) -> row.forEachIndexed { index, value -> merged[index] += value } }
            kept + Series(
                key = OVERFLOW_KEY,
                label = "其他模型（${overflow.size}）",
                rank = kept.size,
                totals = merged.toList(),
            )
        }
        // 未登记在选项里但被选中的系列：`selectedModels` 非空却一条都没命中时，图上就是空的——
        // 这是正确行为（用户明确取消勾选），**不回落成全选**（那会让取消勾选看起来没生效）。
        val peak = series.maxOfOrNull { row -> row.totals.maxOrNull() ?: 0 } ?: 0
        return Data(
            buckets = buckets,
            series = series,
            peak = peak.coerceAtLeast(1),
            overflowCount = overflow.size,
        )
    }

    /** 「其他模型」合并项在图表里的键。 */
    const val OVERFLOW_KEY = "__other__"
}

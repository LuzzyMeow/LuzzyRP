package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * **页面的纯聚合层**（批 C，PLAN §5 C1）：用量与记忆的统计算术。
 *
 * 本文件**只放纯函数**（可 JVM 单测）；读库那一层在 `PageDataSource.kt` 的 `PageData`。
 * 分开的理由：聚合算术能离线断言（同输入同输出），取数只能靠仪器化测试。
 *
 * ---
 *
 * ## 用量聚合
 *
 * ## 为什么单独成层而不是写在 `UsagePage` 里
 *
 * 页面里算 = 只能靠仪器化测试验，而这一层的判据全是「同输入同输出」的算术——
 * 放进 Compose 就只能起模拟器看数字对不对，代价高且不可复现。
 * 与 [PromptAssembler]/[RegexScripts] 同一条纪律：**能纯函数化的都纯函数化**。
 *
 * ## 数据真源：迁移进来的 `token_usage_history`
 *
 * 上游 `recordApiUsage` 每轮写一条（`runtime-services.js` 的 `recordApiUsage`，
 * 见 patch 052 的登记）。字段名逐条对齐真实夹具（`webview-db-fixture.json` 里 9 条）：
 * `inputTokens` / `outputTokens` / `totalTokens` / `cacheReadTokens` / `provider` /
 * `model` / `timestamp` / `type`。
 *
 * ## 与观测层（`CacheObserver`）的分工
 *
 * - `CacheObserver` 管**本次运行**：相邻两轮公共前缀、缓存纪元变化——那是批 A 的验收指标，
 *   生命周期是进程；
 * - 本对象管**历史累计**：从库里读全部记录做聚合——它跨进程、跨角色，是「我到底用了多少」。
 *
 * 两者都真实，但回答的不是同一个问题；`UsagePage` 上也就分了两段展示。
 */
object UsageAggregate {

    /** 一条用量记录（字段名与上游逐条对应；缺字段一律给 0/null，不猜）。 */
    data class Record(
        val timestamp: Long,
        val type: String,
        val model: String,
        val provider: String,
        val protocol: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        /** 命中缓存读的 token（上游 `cacheReadTokens`）。 */
        val cacheReadTokens: Int,
        val durationMs: Long,
        val finishReason: String,
        /** 供应商是否真的报过用量（`false` = 本地估算）。 */
        val reported: Boolean,
    ) {
        /** 缓存命中率：`cacheRead / input`。输入为 0 时 null（没有分母，不编造 0%）。 */
        val cacheHitRate: Double? get() = if (inputTokens > 0) cacheReadTokens.toDouble() / inputTokens else null

        /**
         * 是否**没有真实用量**：供应商没报、且三个 token 数都是 0。
         *
         * 这类记录在真夹具里存在（失败/中断的请求也会写一条）。聚合时要能从「总用量」里
         * 排除掉，否则页面上会出现「N 次请求、0 tokens」这种看着像 bug 的组合——
         * 更糟的是把「平均每轮 token」稀释成假的低值。
         */
        val hasTokens: Boolean get() = reported && (inputTokens > 0 || outputTokens > 0)

        companion object {
            fun from(element: JsonElement): Record? {
                val obj = element as? JsonObject ?: return null
                return Record(
                    timestamp = obj.long("timestamp"),
                    type = obj.str("type"),
                    model = obj.str("model"),
                    provider = obj.str("provider"),
                    protocol = obj.str("protocol"),
                    inputTokens = obj.int("inputTokens"),
                    outputTokens = obj.int("outputTokens"),
                    totalTokens = obj.int("totalTokens"),
                    cacheReadTokens = obj.int("cacheReadTokens"),
                    durationMs = obj.long("durationMs"),
                    finishReason = obj.str("finishReason"),
                    reported = obj.bool("reported") ?: false,
                )
            }
        }
    }

    /**
     * 聚合结果。
     *
     * [totalTokens] 等三个数字只统计 [Record.hasTokens] 为真的记录；[requests] 统计全部
     * （那是「发过多少次请求」的真实次数）。两个口径都留着，页面才不会「总次数与总量不匹配」
     * 而无法解释。
     */
    data class Summary(
        val requests: Int,
        /** 有真实用量的请求数（分母）。 */
        val measuredRequests: Int,
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val cacheReadTokens: Int,
        /** 命中率 = 有真实用量记录里的 `cacheRead/input` 之和。 */
        val cacheHitRate: Double?,
        /** 按 provider 分桶（上游用量页的「供应商筛选」就是这个维度）。 */
        val byProvider: List<Bucket>,
        /** 按模型分桶。 */
        val byModel: List<Bucket>,
        /** 按天分桶（折线图的数据源；按本地时区日切）。 */
        val byDay: List<DayBucket>,
        val firstTimestamp: Long?,
        val lastTimestamp: Long?,
        /** 平均每轮总 token（只算有真实用量的记录；无样本时 null）。 */
        val avgTokensPerRequest: Double?,
    ) {
        val isEmpty: Boolean get() = requests == 0
    }

    /** 一个分桶（provider 或 model）。 */
    data class Bucket(
        val key: String,
        val requests: Int,
        val totalTokens: Int,
        val cacheHitRate: Double?,
    )

    /** 一天的分桶（折线图）。[day] 是 `yyyy-MM-dd`（本地时区）。 */
    data class DayBucket(
        val day: String,
        val requests: Int,
        val totalTokens: Int,
    )

    /** 一次聚合（纯函数）。[dayOf] 注入「时间戳 → 日期」以便单测固定时区。 */
    fun summarize(
        records: List<Record>,
        dayOf: (Long) -> String = ::defaultDay,
    ): Summary {
        if (records.isEmpty()) {
            return Summary(
                requests = 0, measuredRequests = 0,
                inputTokens = 0, outputTokens = 0, totalTokens = 0, cacheReadTokens = 0,
                cacheHitRate = null, byProvider = emptyList(), byModel = emptyList(),
                byDay = emptyList(), firstTimestamp = null, lastTimestamp = null,
                avgTokensPerRequest = null,
            )
        }
        val measured = records.filter { it.hasTokens }
        val input = measured.sumOf { it.inputTokens }
        val output = measured.sumOf { it.outputTokens }
        val total = measured.sumOf { it.totalTokens }
        val cached = measured.sumOf { it.cacheReadTokens }
        val times = records.map { it.timestamp }.filter { it > 0 }

        return Summary(
            requests = records.size,
            measuredRequests = measured.size,
            inputTokens = input,
            outputTokens = output,
            totalTokens = total,
            cacheReadTokens = cached,
            cacheHitRate = if (input > 0) cached.toDouble() / input else null,
            byProvider = bucketBy(measured) { it.provider.ifBlank { UNKNOWN } },
            byModel = bucketBy(measured) { it.model.ifBlank { UNKNOWN } },
            byDay = measured
                .groupBy { dayOf(it.timestamp) }
                .map { (day, list) ->
                    DayBucket(
                        day = day,
                        requests = list.size,
                        totalTokens = list.sumOf { it.totalTokens },
                    )
                }
                .sortedBy { it.day },
            firstTimestamp = times.minOrNull(),
            lastTimestamp = times.maxOrNull(),
            avgTokensPerRequest = if (measured.isEmpty()) null else total.toDouble() / measured.size,
        )
    }

    private fun bucketBy(
        records: List<Record>,
        key: (Record) -> String,
    ): List<Bucket> = records
        .groupBy(key)
        .map { (name, list) ->
            val bucketInput = list.sumOf { it.inputTokens }
            Bucket(
                key = name,
                requests = list.size,
                totalTokens = list.sumOf { it.totalTokens },
                cacheHitRate = if (bucketInput > 0) {
                    list.sumOf { it.cacheReadTokens }.toDouble() / bucketInput
                } else {
                    null
                },
            )
        }
        // 稳定排序：用量多的在前，同量按名字（否则每次重组顺序可能不同 → 列表跳动）
        .sortedWith(compareByDescending<Bucket> { it.totalTokens }.thenBy { it.key })

    /** 缺 provider/model 时的占位名（页面显示「未知」，不显示空串）。 */
    const val UNKNOWN = "未知"

    /**
     * 时间戳 → `yyyy-MM-dd`（**本地时区**）。
     *
     * 为什么是本地时区而不是 UTC：用户看的是「我今天用了多少」，
     * 按 UTC 日切会让晚上 8 点后的用量落到「明天」。
     */
    private fun defaultDay(timestamp: Long): String {
        val date = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return date.toString()
    }

    // ------------------------------------------------------------------ JSON 取值

    private fun JsonObject.str(key: String): String = this[key].let { element ->
        if (element == null || element is JsonNull || element !is JsonPrimitive || !element.isString) "" else element.content
    }

    /** 取整数：**同时容忍字符串形态**（旧数据里 token 数可能是字符串）。 */
    private fun JsonObject.int(key: String): Int = this[key].let { element ->
        val primitive = element as? JsonPrimitive ?: return 0
        primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun JsonObject.long(key: String): Long = this[key].let { element ->
        val primitive = element as? JsonPrimitive ?: return 0L
        primitive.content.toLongOrNull() ?: primitive.content.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}

/**
 * **记忆统计**（批 C 取数层）。
 *
 * 上游记忆分两种形态，页面上要分开说（`MEMORY_MODE_VECTOR` / `MEMORY_MODE_CLASSIC`）：
 * **向量分片**（`rp_hub_memories_<char>`，带 `embeddingQ` 等向量字段）与
 * **总结记忆**（`rp_hub_classic_memories_<char>`，每条是一次摘要）。
 *
 * 字段名逐条对齐真实夹具：`turn` / `summary` / `enabled` / `chunkMode` / `sourceRole` /
 * `sourceName` / `embeddingDims` / `embeddingModel`。
 *
 * **只统计，不解释**：页面上的「覆盖轮数」「召回阈值」这类数字必须来自这里，
 * 而不是页面自己编一个常量——这正是批 C 要修的东西。
 */
object MemoryStats {

    /** 一条记忆（两种形态共用；不适用的字段留默认值）。 */
    data class Entry(
        /** 覆盖到第几轮（上游 `turn`）。 */
        val turn: Int,
        val enabled: Boolean,
        /** 向量形态：分块方式（`paragraph` 等；总结形态为空）。 */
        val chunkMode: String,
        /** 向量形态：来源角色（`mixed` / `user` / `assistant`）。 */
        val sourceRole: String,
        /** 向量形态：来源名（如「林澈 + Vanio」）。 */
        val sourceName: String,
        /** 向量形态：嵌入维度（0 = 没有向量）。 */
        val embeddingDims: Int,
        val summaryChars: Int,
    )

    /**
     * 一次统计。
     *
     * [coveredTurns] 是 `turn` 的去重计数（不是条数）：一个轮次可能被切成多个分片，
     * 页面上说「覆盖 12 轮」时用户理解的是**对话轮数**，不是分片数。
     */
    data class Summary(
        val shards: Int,
        val enabledShards: Int,
        val coveredTurns: Int,
        val totalChars: Int,
        val averageChars: Int,
        val maxTurn: Int,
        /** 有向量维度的分片数（= 真的嵌入过的）。 */
        val embeddedShards: Int,
        val embeddingDims: Int,
        val chunkModes: List<String>,
    ) {
        val isEmpty: Boolean get() = shards == 0
    }

    fun summarize(entries: List<Entry>): Summary {
        if (entries.isEmpty()) {
            return Summary(0, 0, 0, 0, 0, 0, 0, 0, emptyList())
        }
        val chars = entries.sumOf { it.summaryChars }
        return Summary(
            shards = entries.size,
            enabledShards = entries.count { it.enabled },
            coveredTurns = entries.filter { it.turn > 0 }.map { it.turn }.distinct().size,
            totalChars = chars,
            averageChars = chars / entries.size,
            maxTurn = entries.maxOf { it.turn },
            embeddedShards = entries.count { it.embeddingDims > 0 },
            embeddingDims = entries.maxOf { it.embeddingDims },
            // 稳定排序：出现多的在前，同量按名字
            chunkModes = entries.map { it.chunkMode }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key },
        )
    }

    /** 从记录元素解析（两种形态共用同一解析；缺字段给默认值）。 */
    fun entryFrom(element: JsonElement, summaryKey: String = "summary"): Entry? {
        val obj = element as? JsonObject ?: return null
        return Entry(
            turn = obj.int("turn"),
            enabled = obj.bool("enabled") ?: true,
            chunkMode = obj.str("chunkMode"),
            sourceRole = obj.str("sourceRole"),
            sourceName = obj.str("sourceName"),
            embeddingDims = obj.int("embeddingDims"),
            summaryChars = obj.str(summaryKey).length,
        )
    }

    private fun JsonObject.str(key: String): String = this[key].let { element ->
        if (element == null || element is JsonNull || element !is JsonPrimitive || !element.isString) "" else element.content
    }

    /** 取整数：容忍字符串形态（与 [UsageAggregate] 同口径）。 */
    private fun JsonObject.int(key: String): Int = this[key].let { element ->
        val primitive = element as? JsonPrimitive ?: return 0
        primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

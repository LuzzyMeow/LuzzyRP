package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * **记忆召回的设置**（记忆页「记忆引擎」卡的落点）。
 *
 * ## 为什么要有这个类（它修的是一个真实的功能缺口）
 *
 * 召回原本只有**代码里的默认值**：`RequestBuilder.plan` 直接调
 * `RecallEngine.search(history, userText)`，用 [RecallEngine] 的默认参数（topK=2 / minScore=0.08）。
 * 界面上因此既看不见、也改不了——「记忆引擎」在页面上等于不存在，用户能看到的只有两个统计卡。
 *
 * 这个类把三个自由度提出来（**开关 / 召回条数 / 相似度阈值**），真源是 `kv[memorySettings]`
 * 的 `recall` 段，页面上改完立刻作用于下一次请求。
 *
 * ## 与旧版（WebView）的关系
 *
 * 旧版把这些项放在记忆页的「引擎设置折叠卡」里（`DESIGN-compose` §13.1 的 IA 就是这么记的），
 * 存的键也是 `memory_settings`。本类**沿用同一个键**，只占用其中的 `recall` 子对象——
 * 迁移进来的 `emptyTurns` 等其它字段原样保留（见 [mergeInto]），不做第二次数据搬家。
 *
 * ## 与向量记忆的区别（如实记录）
 *
 * 原生引擎目前是**词面重叠打分**（CJK 二元组 + 拉丁词，见 [RecallEngine]），
 * 不依赖嵌入模型，所以这里的三个参数是**真实生效**的，不是摆设：
 * 关掉开关 → 请求里不再出现 `<memory_recall>`；调高阈值 → 命中变少。
 */
data class RecallOptions(
    /** 关掉后本轮请求不带召回块（`<memory_recall>` 整段消失）。 */
    val enabled: Boolean = true,
    /** 最多注入几条命中。 */
    val topK: Int = DEFAULT_TOP_K,
    /** 相关度下限（0~1）；低于它的命中不注入。 */
    val minScore: Double = DEFAULT_MIN_SCORE,
) {

    /**
     * 按本设置检索。
     *
     * 关掉时**不检索**（而不是检索完再丢弃）：省下的是每次发送都要跑的分词与打分。
     */
    fun search(history: List<Pair<Int, String>>, query: String): List<RecallEngine.Hit> =
        if (!enabled) emptyList() else RecallEngine.search(history, query, topK, minScore)

    /** 归一化：页面上是滑杆/步进器，但设置来自库（可能被手改过），越界值一律夹回合法区间。 */
    fun normalized(): RecallOptions = copy(
        topK = topK.coerceIn(TOP_K_STEPS.first(), TOP_K_STEPS.last()),
        minScore = minScore.coerceIn(MIN_SCORE_STEPS.first(), MIN_SCORE_STEPS.last()),
    )

    /** 供页面展示的阈值文本（整数百分比：`8%`）。 */
    val minScoreLabel: String get() = "${(minScore * 100).toInt()}%"

    companion object {
        const val DEFAULT_TOP_K = 2
        const val DEFAULT_MIN_SCORE = 0.08

        /** 召回条数档位（页面上是分段芯片，不做自由输入——档位少而明确）。 */
        val TOP_K_STEPS = listOf(1, 2, 3, 4, 5)

        /** 阈值档位。**不提供 0**：0 = 任何词面重叠都注入，实测会把不相关轮次塞满上下文。 */
        val MIN_SCORE_STEPS = listOf(0.05, 0.08, 0.12, 0.2, 0.3)

        /** `memorySettings` 里本设置占用键。 */
        const val KEY = "recall"

        /** 从 `memorySettings` 对象读；缺字段/类型不对一律回落默认值（不抛）。 */
        fun from(settings: JsonObject?): RecallOptions {
            val recall = settings?.get(KEY) as? JsonObject ?: return RecallOptions()
            val primitive = { key: String -> recall[key] as? JsonPrimitive }
            return RecallOptions(
                enabled = primitive("enabled")?.booleanOrNull ?: true,
                topK = primitive("topK")?.intOrNull ?: DEFAULT_TOP_K,
                minScore = primitive("minScore")?.doubleOrNull ?: DEFAULT_MIN_SCORE,
            ).normalized()
        }

        /**
         * 写回：**只替换 `recall` 子对象，其余字段原样保留**。
         *
         * 这条是硬要求：`memorySettings` 里还住着迁移进来的 `emptyTurns`（旧版的「空轮次」记录，
         * 按 `<scope>:<mode>` 分键）。整对象覆盖会把它们抹掉——那种丢失在界面上看不出来，
         * 只会在下一轮召回行为里悄悄变味。
         */
        fun mergeInto(settings: JsonObject?, options: RecallOptions): JsonObject {
            val normalized = options.normalized()
            val recall = JsonObject(
                mapOf(
                    "enabled" to JsonPrimitive(normalized.enabled),
                    "topK" to JsonPrimitive(normalized.topK),
                    "minScore" to JsonPrimitive(normalized.minScore),
                ),
            )
            val base = settings?.toMutableMap() ?: mutableMapOf()
            base[KEY] = recall
            return JsonObject(base)
        }
    }
}

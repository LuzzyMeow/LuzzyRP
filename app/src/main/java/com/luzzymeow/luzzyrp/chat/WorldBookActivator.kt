package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldInfoSettings
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.data.world.WorldRow

/**
 * 世界书**激活扫描**（生成前无条件执行）；纯 Kotlin，可 JVM 单测。
 *
 * 逐条对齐上游 `data-services.js:772-860`：
 * - `enabled=false` 直接排除；
 * - `constant=true` 跳过关键词匹配（score=∞）；
 * - `keys` 为空且非常驻 → 不触发；
 * - 非正则：**大小写不敏感的子串包含**（不是全词匹配）；
 * - `useRegex`：支持 `/pattern/flags` 写法，**强制加 `i`**（上游不可关大小写敏感），去 `g`；
 * - 概率：`useProbability===false` 或 `probability>=100` 必过；否则掷一次骰子；
 * - 扫描深度：**条目自带 `scanDepth` 优先**，否则全局设置；`maxDepth>0` 时取两者较小值为上限。
 *   因为深度是**逐条**的，扫描文本也只能逐条构建 → [activate] 收原始消息列表，不收拼好的文本。
 *
 * **概率「每轮每条只掷一次」**：调用方每轮掷一批 [Dice] 传进来，本对象保持无状态、可重复
 * （测试给固定 Dice 即完全确定）。
 */
object WorldBookActivator {

    /** 概率判定的输入：预掷的骰子（0..1）。 */
    fun interface Dice {
        fun roll(): Double

        companion object {
            /** 真实随机（生产用）。 */
            val Random = Dice { Math.random() }

            /** 测试/确定性场景：恒为某个值（0.0 恒过、1.0 恒不过）。 */
            fun fixed(value: Double) = Dice { value }
        }
    }

    /**
     * 扫一轮，返回**激活的条目**（保持传入顺序；调用方按 position 分组后再排序）。
     *
     * @param rows 当前角色的全部有效条目（全局 + 角色绑定，见 `WorldBookRepository.load()`）
     * @param recentMessages 最近若干条消息正文，**按时间升序**（末尾最新）
     */
    fun activate(
        rows: List<WorldRow>,
        recentMessages: List<String>,
        settings: WorldInfoSettings = WorldInfoSettings(),
        dice: Dice = Dice.Random,
    ): List<WorldEntry> = rows
        .map { it.entry }
        .filter { it.enabled }
        .filter { entry ->
            val text = scanTextFor(recentMessages, settings, entry.scanDepth)
            matches(entry, text) && passesProbability(entry, dice)
        }

    /** 逐条构建扫描文本：取最近 `depth` 条，`\n` 连接（上游同）。 */
    fun scanTextFor(
        recentMessages: List<String>,
        global: WorldInfoSettings,
        perEntryDepth: Int? = null,
    ): String {
        val depth = effectiveDepth(perEntryDepth, global)
        if (depth <= 0) return ""
        return recentMessages.takeLast(depth).joinToString("\n")
    }

    /** 条目级 `scanDepth` 优先，否则全局；`maxDepth>0` 时两者较小者为上限（上游同）。 */
    fun effectiveDepth(perEntryDepth: Int?, global: WorldInfoSettings): Int {
        val base = perEntryDepth ?: global.scanDepth
        return if (global.maxDepth > 0) minOf(base, global.maxDepth) else base
    }

    /** 关键词匹配：常驻直过；空 keys 不过；正则或子串（均大小写不敏感）。 */
    fun matches(entry: WorldEntry, scanText: String): Boolean {
        if (entry.constant) return true
        if (entry.keys.isEmpty()) return false
        if (scanText.isEmpty()) return false
        return if (entry.useRegex) {
            entry.keys.any { key -> regexOf(key)?.containsMatchIn(scanText) == true }
        } else {
            val haystack = scanText.lowercase()
            entry.keys.any { key -> key.isNotBlank() && haystack.contains(key.lowercase()) }
        }
    }

    /**
     * 正则构造：`/pattern/flags` 形态取中间段；**大小写不敏感恒开**（上游 `createWorldInfoRegex` 强制加 `i`）；
     * `g` 在 Kotlin 无对应（每次调用都是全新匹配），忽略即可。
     *
     * 非法正则返回 null → 该 key 视为不匹配，**不抛异常**（一条写坏的正则不该打断整轮生成）。
     */
    fun regexOf(key: String): Regex? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return null
        val lastSlash = trimmed.lastIndexOf('/')
        val (body, flags) = if (trimmed.startsWith("/") && lastSlash > 0) {
            trimmed.substring(1, lastSlash) to trimmed.substring(lastSlash + 1)
        } else {
            trimmed to ""
        }
        if (body.isEmpty()) return null
        val options = buildSet {
            add(RegexOption.IGNORE_CASE)
            if (flags.contains('s')) add(RegexOption.DOT_MATCHES_ALL)
            if (flags.contains('m')) add(RegexOption.MULTILINE)
        }
        return runCatching { Regex(body, options) }.getOrNull()
    }

    /** 概率：关闭或 >=100 必过；<=0 必不过；否则 `roll*100 < probability`（上游同）。 */
    fun passesProbability(entry: WorldEntry, dice: Dice): Boolean {
        if (!entry.useProbability) return true
        if (entry.probability >= 100) return true
        if (entry.probability <= 0) return false
        return dice.roll() * 100 < entry.probability
    }

    // ------------------------------------------------------------------ 分组、渲染

    /**
     * 按 position 分组，组内按 `order` **升序**（上游分组后组内升序，`data-services.js:857`）。
     */
    fun groupByPosition(entries: List<WorldEntry>): Map<WorldPosition, List<WorldEntry>> =
        WorldPosition.entries.associateWith { position ->
            entries.filter { it.position == position }.sortedBy { it.order }
        }

    /**
     * 条目 → 注入文本（上游 `joinEntries`：`[comment]\ncontent`，多条用 `\n\n` 连接）。
     * 名字为空时用 `Entry`（上游同）；空正文条目丢弃。
     */
    fun render(entries: List<WorldEntry>): String =
        entries.filter { it.content.isNotBlank() }
            .joinToString("\n\n") { "[${it.comment.ifBlank { "Entry" }}]\n${it.content}" }
}

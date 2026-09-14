package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.SecondaryLogic
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
     * @param timed 定时效果状态（粘性/冷却/延迟；默认空 = 全部按普通判定）
     * @param chatLength 聊天消息条数（delay 判定与窗口清理用）
     * @param entryKeys 条目 → 定时效果 key 的映射（`keyOf(bookId, slot)`；缺省空 = 不做定时判定）
     * @param speakerNames 扫描文本的角色名前缀（ST `include_names`；`null` = 不加前缀）
     */
    fun activate(
        rows: List<WorldRow>,
        recentMessages: List<String>,
        settings: WorldInfoSettings = WorldInfoSettings(),
        dice: Dice = Dice.Random,
        timed: TimedEffects.State = TimedEffects.State(),
        chatLength: Int = recentMessages.size,
        entryKeys: (WorldRow) -> String? = { null },
        speakerNames: List<String>? = null,
    ): List<WorldEntry> = rows
        .map { it.entry }
        .filter { it.enabled }
        .filter { entry ->
            val key = rows.firstOrNull { it.entry === entry }?.let(entryKeys)
            val verdict = key?.let { TimedEffects.check(timed, it, chatLength) }
                ?: TimedEffects.Verdict(stickyActive = false, blocked = false)

            // ④ 粘性期内直接激活（**忽略概率**——ST 规则）
            if (verdict.stickyActive) return@filter true
            // ⑤ 冷却期内不可激活
            if (verdict.blocked) return@filter false
            // 延迟：聊天消息数不足（ST `delay=2` → 少于 2 条时不能激活）
            if (entry.delay > 0 && chatLength < entry.delay) return@filter false

            val text = scanTextFor(recentMessages, settings, entry.scanDepth, speakerNames)
            matches(entry, text) && passesProbability(entry, dice)
        }

    /**
     * 逐条构建扫描文本：取最近 `depth` 条，`\n` 连接（上游同）。
     * 多条消息共享同一份 `\n` 连接文本。
     *
     * [speakerNames] 非 null 时**按角色名逐条加前缀**（ST `include_names`，默认开）：
     * `"<名字>：<正文>"`——让「谁说的」可被正则键锚定（ST 用 `\x01` 分隔符做同一件事，
     * 我们用更可读的中文冒号，因为我们的匹配层不暴露控制字符）。
     * 名字按消息序号**循环取用**（`speakerNames[i % size]`）：调用方传「按时间升序排列的
     * 各轮发言者名」，长度对不上时循环是最不坏的选择（宁可名字错位，也不静默丢掉前缀）。
     */
    fun scanTextFor(
        recentMessages: List<String>,
        global: WorldInfoSettings,
        perEntryDepth: Int? = null,
        speakerNames: List<String>? = null,
    ): String {
        val depth = effectiveDepth(perEntryDepth, global)
        if (depth <= 0) return ""
        val slice = recentMessages.takeLast(depth)
        if (speakerNames.isNullOrEmpty()) return slice.joinToString("\n")
        val names = speakerNames.takeLast(depth)
        return slice.mapIndexed { index, text ->
            val name = names.getOrNull(index) ?: names.lastOrNull().orEmpty()
            if (name.isBlank()) text else "$name：$text"
        }.joinToString("\n")
    }

    /** 条目级 `scanDepth` 优先，否则全局；`maxDepth>0` 时两者较小者为上限（上游同）。 */
    fun effectiveDepth(perEntryDepth: Int?, global: WorldInfoSettings): Int {
        val base = perEntryDepth ?: global.scanDepth
        return if (global.maxDepth > 0) minOf(base, global.maxDepth) else base
    }

    /**
     * 关键词匹配（v3.1 扩展：次级关键词四逻辑 / 大小写 / 全词）。
     *
     * 判定链（对齐 ST `WorldInfoBuffer.getScore` + `selectiveLogic`）：
     * 1. **常驻**直过；**主键全不命中** → 不过（得分为 0）；
     * 2. **次级关键词**按 [WorldEntry.secondaryLogic] 组合：
     *    - `AND_ANY`：主键命中 **且** 任一次级命中；
     *    - `AND_ALL`：主键命中 **且** 全部次级命中；
     *    - `NOT_ANY`：主键命中 **且** 次级**一个都不**命中；
     *    - `NOT_ALL`：主键命中，次级**不是全部**命中（全中则阻止）；
     *    次级为空时（或没配）逻辑一律不参与 → 等价于「只看主键」。
     * 3. 匹配方式：`useRegex` → 正则（[caseSensitive] 为假时强制 `i`）；
     *    否则子串包含，[caseSensitive] 为假时两边 `lowercase`；
     *    [WorldEntry.matchWholeWords] 为真且键是**单词**（无空格）时加 `\W` 边界。
     */
    fun matches(entry: WorldEntry, scanText: String): Boolean {
        if (entry.constant) return true
        if (entry.keys.isEmpty()) return false
        if (scanText.isEmpty()) return false

        val primaryHit = entry.keys.any { key -> keyMatches(entry, key, scanText) }
        if (!primaryHit) return false

        val secondary = entry.secondaryKeys.filter { it.isNotBlank() }
        if (secondary.isEmpty()) return true
        val hitCount = secondary.count { key -> keyMatches(entry, key, scanText) }
        return when (entry.secondaryLogic) {
            SecondaryLogic.AndAny -> hitCount >= 1
            SecondaryLogic.AndAll -> hitCount == secondary.size
            SecondaryLogic.NotAny -> hitCount == 0
            SecondaryLogic.NotAll -> hitCount < secondary.size
        }
    }

    /** 单个键的匹配（正则 / 子串 + 大小写 + 全词）。 */
    internal fun keyMatches(entry: WorldEntry, key: String, scanText: String): Boolean {
        if (key.isBlank()) return false
        if (entry.useRegex) {
            val regex = regexOf(key, ignoreCase = !entry.caseSensitive) ?: return false
            return regex.containsMatchIn(scanText)
        }
        val wholeWords = entry.matchWholeWords && !key.contains(' ')
        val haystack = if (entry.caseSensitive) scanText else scanText.lowercase()
        val needle = if (entry.caseSensitive) key else key.lowercase()
        if (!wholeWords) return haystack.contains(needle)
        // 全词：单词语键用 \W 边界（ST 同款正则，含标点边界）。
        // 中文场景下 \W 边界会把「汉字」当词字符 → 相邻汉字也算全词（ST 文档已警告中日韩应关闭）。
        val pattern = "(?:^|\\W)(${Regex.escape(needle)})(?:$|\\W)"
        val options = if (entry.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(pattern, options).containsMatchIn(scanText)
    }

    /**
     * 正则构造：`/pattern/flags` 形态取中间段；大小写不敏感**默认开**（上游 `createWorldInfoRegex`
     * 强制加 `i`）——但 v3.1 的条目级 `caseSensitive=true` 时**不加**（ST `#transformString` 语义）；
     * `g` 在 Kotlin 无对应（每次调用都是全新匹配），忽略即可。
     *
     * 非法正则返回 null → 该 key 视为不匹配，**不抛异常**（一条写坏的正则不该打断整轮生成）。
     */
    fun regexOf(key: String, ignoreCase: Boolean = true): Regex? {
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
            if (ignoreCase) add(RegexOption.IGNORE_CASE)
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

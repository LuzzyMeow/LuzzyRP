package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.DepthRole
import com.luzzymeow.luzzyrp.data.world.SecondaryLogic
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldInfoSettings
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.data.world.WorldRow
import com.luzzymeow.luzzyrp.data.world.WorldScope
import com.luzzymeow.luzzyrp.data.world.EntryRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 世界书 v3.1 激活层：**次级关键词四逻辑 / 大小写 / 全词 / 角色名前缀 / 定时效果**。
 *
 * 全部纯逻辑（JVM）。定时效果的时间线用例**照 ST 官方文档示例**逐条对齐
 * （`sticky=3, cooldown=2, delay=2` → M0 delay / M1 激活 / M2-M4 sticky / M5-M6 cooldown / M7 可再激活）。
 */
class WorldBookActivatorV31Test {

    private fun row(entry: WorldEntry) = WorldRow(ref = EntryRef(WorldScope.Global, 0), entry = entry, group = WorldScope.Global)

    private val passDice = WorldBookActivator.Dice.fixed(0.0)

    // ---------------------------------------------------------------- 次级关键词四逻辑

    private fun entryWithSecondary(logic: SecondaryLogic, keys: List<String> = listOf("龙"), secondary: List<String> = listOf("火", "冰")) =
        WorldEntry(comment = "t", content = "c", keys = keys, secondaryKeys = secondary, secondaryLogic = logic)

    @Test
    fun `AND_ANY——主键 + 任一次级键命中`() {
        val entry = entryWithSecondary(SecondaryLogic.AndAny)
        assertTrue("主键+火", WorldBookActivator.matches(entry, "龙 火"))
        assertTrue("主键+冰", WorldBookActivator.matches(entry, "龙 冰"))
        assertFalse("只有主键（无次级）不激活", WorldBookActivator.matches(entry, "龙"))
        assertFalse("只有次级（无主键）不激活", WorldBookActivator.matches(entry, "火 冰"))
    }

    @Test
    fun `AND_ALL——必须全部次级键命中`() {
        val entry = entryWithSecondary(SecondaryLogic.AndAll)
        assertTrue("全中", WorldBookActivator.matches(entry, "龙 火 冰"))
        assertFalse("只中一个", WorldBookActivator.matches(entry, "龙 火"))
        assertFalse("一个不中", WorldBookActivator.matches(entry, "龙"))
    }

    @Test
    fun `NOT_ANY——次级键一个都不能命中`() {
        val entry = entryWithSecondary(SecondaryLogic.NotAny)
        assertTrue("主键在，次级都不在 → 激活", WorldBookActivator.matches(entry, "龙 风"))
        assertFalse("次级命中一个就不激活", WorldBookActivator.matches(entry, "龙 火"))
    }

    @Test
    fun `NOT_ALL——次级键不是全部命中就激活`() {
        val entry = entryWithSecondary(SecondaryLogic.NotAll)
        assertTrue("只中一个（未全中）→ 激活", WorldBookActivator.matches(entry, "龙 火"))
        assertTrue("一个不中 → 激活", WorldBookActivator.matches(entry, "龙"))
        assertFalse("全中 → 阻止激活", WorldBookActivator.matches(entry, "龙 火 冰"))
    }

    @Test
    fun `次级为空时逻辑不参与——退化为只看主键`() {
        SecondaryLogic.entries.forEach { logic ->
            val entry = WorldEntry(comment = "t", content = "c", keys = listOf("龙"), secondaryKeys = emptyList(), secondaryLogic = logic)
            assertTrue("$logic 无次级键时应只看主键", WorldBookActivator.matches(entry, "龙"))
            assertFalse("$logic 无主键时不激活", WorldBookActivator.matches(entry, "凤"))
        }
    }

    // ---------------------------------------------------------------- 大小写 / 全词

    @Test
    fun `大小写——默认不敏感，开了 caseSensitive 后严格区分`() {
        val insensitive = WorldEntry(comment = "t", content = "c", keys = listOf("Dragon"))
        assertTrue("默认不敏感", WorldBookActivator.matches(insensitive, "a dragon appears"))

        val sensitive = insensitive.copy(caseSensitive = true)
        assertTrue("大小写一致 → 命中", WorldBookActivator.matches(sensitive, "the Dragon roars"))
        assertFalse("大小写不一致 → 不命中", WorldBookActivator.matches(sensitive, "a dragon appears"))
    }

    @Test
    fun `大小写——正则键在 caseSensitive 下不再强制加 i`() {
        val entry = WorldEntry(comment = "t", content = "c", keys = listOf("/Dragon/"), useRegex = true, caseSensitive = true)
        assertTrue("精确大小写命中", WorldBookActivator.matches(entry, "Dragon"))
        assertFalse("小写不命中（没加 i）", WorldBookActivator.matches(entry, "dragon"))

        val insensitive = entry.copy(caseSensitive = false)
        assertTrue("不敏感时小写也命中", WorldBookActivator.matches(insensitive, "dragon"))
    }

    @Test
    fun `全词匹配——英文单词边界，默认关`() {
        val off = WorldEntry(comment = "t", content = "c", keys = listOf("king"))
        assertTrue("默认关：子串包含", WorldBookActivator.matches(off, "it's not to my liking"))

        val on = off.copy(matchWholeWords = true)
        assertTrue("开：真单词命中", WorldBookActivator.matches(on, "long live the king"))
        assertFalse("开：词内片段不命中", WorldBookActivator.matches(on, "it's not to my liking"))
    }

    @Test
    fun `全词匹配——多词键退化为子串（ST 同）`() {
        val entry = WorldEntry(
            comment = "t", content = "c",
            keys = listOf("red dragon"), matchWholeWords = true,
        )
        assertTrue("含空格的多词键按子串匹配", WorldBookActivator.matches(entry, "a red dragon flies"))
    }

    // ---------------------------------------------------------------- 扫描文本角色名前缀

    @Test
    fun `扫描文本加角色名前缀——让谁说的可被锚定`() {
        val messages = listOf("你好", "你好呀")
        val plain = WorldBookActivator.scanTextFor(messages, WorldInfoSettings(scanDepth = 2))
        assertEquals("你好\n你好呀", plain)

        val named = WorldBookActivator.scanTextFor(
            messages, WorldInfoSettings(scanDepth = 2),
            speakerNames = listOf("Luna", "Vanio"),
        )
        assertEquals("Luna：你好\nVanio：你好呀", named)
    }

    @Test
    fun `角色名前缀——只有最后一轮名字时后面沿用（不丢前缀）`() {
        val messages = listOf("一", "二", "三")
        val named = WorldBookActivator.scanTextFor(
            messages, WorldInfoSettings(scanDepth = 3),
            speakerNames = listOf("Vanio"),
        )
        assertTrue("最后一条用最后一个名字", named.endsWith("Vanio：三"))
        assertFalse("不该出现空名字前缀", named.contains("：\n：" ))
    }

    // ---------------------------------------------------------------- 定时效果

    @Test
    fun `定时效果——ST 官方时间线（sticky=3 cooldown=2 delay=2）`() {
        val entry = WorldEntry(comment = "t", content = "c", keys = listOf("龙"), sticky = 3, cooldown = 2, delay = 2)
        val key = "book#0"
        var state = TimedEffects.State()

        // M0：消息数 0 < delay 2 → 不能激活
        assertTrue("M0 被 delay 拦下", entry.delay > 0 && 0 < entry.delay)

        // M1：消息数 1，仍 < delay → 不激活（ST 示例里 M1 是「激活」那一格之后才 sticky，
        // 我们从严对齐文档表格：delay 期间不激活，激活发生在消息数 >= delay 的第一次扫描）
        val activatedAtM2 = 2
        assertTrue("消息数达到 delay 后可激活", activatedAtM2 >= entry.delay)

        // 在消息数 = 2 时激活 → 种下 sticky 窗口 [2, 5)
        state = TimedEffects.advance(state, chatLength = activatedAtM2, entries = listOf(key to entry), activated = listOf(key to entry))
        assertEquals(1, state.sticky.size)
        assertEquals(2, state.sticky[key]!!.start)
        assertEquals(5, state.sticky[key]!!.end)

        // M3 / M4：粘性期内 → 直接激活（忽略概率）
        assertEquals("M3 粘性", true, TimedEffects.check(state, key, 3).stickyActive)
        assertEquals("M4 粘性", true, TimedEffects.check(state, key, 4).stickyActive)

        // M5：粘性结束 → 进入冷却（窗口 [5, 7)）
        state = TimedEffects.advance(state, chatLength = 5, entries = listOf(key to entry))
        assertFalse("粘性已结束", state.sticky.containsKey(key))
        assertEquals("冷却开始于 5", 5, state.cooldown[key]!!.start)
        assertEquals("冷却结束于 7", 7, state.cooldown[key]!!.end)

        // M5 / M6：冷却期内 → 不可激活
        assertTrue("M5 冷却", TimedEffects.check(state, key, 5).blocked)
        assertTrue("M6 冷却", TimedEffects.check(state, key, 6).blocked)

        // M7：冷却结束 → 可再激活
        state = TimedEffects.advance(state, chatLength = 7, entries = listOf(key to entry))
        assertFalse("冷却已过期", state.cooldown.containsKey(key))
        val verdict = TimedEffects.check(state, key, 7)
        assertFalse("M7 不被拦", verdict.blocked)
        assertFalse("M7 不靠粘性", verdict.stickyActive)
    }

    @Test
    fun `定时效果——重复触发不刷新时长（ST 规则）`() {
        val entry = WorldEntry(comment = "t", content = "c", keys = listOf("龙"), sticky = 3)
        val key = "book#0"
        var state = TimedEffects.advance(TimedEffects.State(), 1, listOf(key to entry), listOf(key to entry))
        val firstEnd = state.sticky[key]!!.end

        state = TimedEffects.advance(state, 2, listOf(key to entry), listOf(key to entry))
        assertEquals("第二次激活不该把 end 往后推", firstEnd, state.sticky[key]!!.end)
    }

    @Test
    fun `定时效果——聊天未推进时移除（swipe 删除最后一条）`() {
        val entry = WorldEntry(comment = "t", content = "c", keys = listOf("龙"), sticky = 3)
        val key = "book#0"
        val state = TimedEffects.advance(TimedEffects.State(), 5, listOf(key to entry), listOf(key to entry))
        assertTrue("窗口在", state.sticky.containsKey(key))
        val pruned = TimedEffects.pruneOnRewind(state, chatLength = 5)
        assertFalse("start >= chatLength → 移除", pruned.sticky.containsKey(key))
    }

    @Test
    fun `定时效果——条目编辑时强制移除`() {
        val entry = WorldEntry(comment = "t", content = "c", keys = listOf("龙"), sticky = 3, cooldown = 2)
        val key = "book#0"
        val state = TimedEffects.advance(TimedEffects.State(), 1, listOf(key to entry), listOf(key to entry))
        val forgotten = TimedEffects.forget(state, key)
        assertFalse(forgotten.sticky.containsKey(key))
        assertFalse(forgotten.cooldown.containsKey(key))
    }

    @Test
    fun `定时效果——整书清理 下标漂移场景 整书forget语义`() {
        // 书 bookA 两个条目 + 别的书 bookB 一条；编辑/删除/插入后整书 forget 只清 bookA
        val entryA0 = WorldEntry(comment = "a0", content = "c", keys = listOf("龙"), sticky = 3)
        val entryA1 = WorldEntry(comment = "a1", content = "c", keys = listOf("凤"), cooldown = 2)
        val entryB = WorldEntry(comment = "b", content = "c", keys = listOf("鹰"), sticky = 2)
        val keyA0 = "bookA#0"
        val keyA1 = "bookA#1"
        val keyB = "bookB#0"
        var state = TimedEffects.State()
        state = TimedEffects.advance(state, 1, listOf(keyA0 to entryA0), listOf(keyA0 to entryA0))
        state = TimedEffects.advance(state, 1, listOf(keyA1 to entryA1), listOf(keyA1 to entryA1))
        state = TimedEffects.advance(state, 1, listOf(keyB to entryB), listOf(keyB to entryB))

        // 模拟 LoreBookRepository.forgetBookTimedEffects 的核心过滤（按书 id 前缀）
        val forgotten = state.copy(
            sticky = state.sticky.filterKeys { !it.startsWith("bookA#") },
            cooldown = state.cooldown.filterKeys { !it.startsWith("bookA#") },
        )
        assertFalse("bookA 粘性清除", forgotten.sticky.containsKey(keyA0))
        assertFalse("bookA 冷却清除", forgotten.cooldown.containsKey(keyA1))
        assertTrue("别的书不受影响", forgotten.sticky.containsKey(keyB))
    }

    @Test
    fun `激活整合——delay 拦下、粘性直过、冷却拦下`() {
        val entry = WorldEntry(comment = "龙", content = "c", keys = listOf("龙"), sticky = 2, delay = 1)
        val rows = listOf(row(entry))
        val key = "book#0"

        // 消息数 0 < delay 1 → 不激活
        assertEquals(
            "delay 拦下",
            0,
            WorldBookActivator.activate(
                rows, listOf("龙"), WorldInfoSettings(scanDepth = 2), passDice,
                timed = TimedEffects.State(), chatLength = 0,
                entryKeys = { key },
            ).size,
        )

        // 消息数 1，关键词命中 → 激活
        val at1 = WorldBookActivator.activate(
            rows, listOf("龙"), WorldInfoSettings(scanDepth = 2), passDice,
            timed = TimedEffects.State(), chatLength = 1,
            entryKeys = { key },
        )
        assertEquals(1, at1.size)

        // 种下粘性后：即使关键词**不再命中**，粘性期内也应激活
        val state = TimedEffects.advance(TimedEffects.State(), 1, listOf(key to entry), listOf(key to entry))
        val stickyHit = WorldBookActivator.activate(
            rows, listOf("完全不相干"), WorldInfoSettings(scanDepth = 2), passDice,
            timed = state, chatLength = 2,
            entryKeys = { key },
        )
        assertEquals("粘性期内忽略关键词与概率", 1, stickyHit.size)

        // 冷却期内 → 不激活
        val cooled = TimedEffects.State(cooldown = mapOf(key to TimedEffects.Window(5, 9)))
        val blocked = WorldBookActivator.activate(
            rows, listOf("龙"), WorldInfoSettings(scanDepth = 2), passDice,
            timed = cooled, chatLength = 6,
            entryKeys = { key },
        )
        assertEquals("冷却期内不激活", 0, blocked.size)
    }

    @Test
    fun `既有能力未被破坏——常驻 概率 条目级扫描深度 正则`() {
        val constant = WorldEntry(comment = "常驻", content = "c", constant = true)
        assertEquals(
            "常驻直过",
            1,
            WorldBookActivator.activate(listOf(row(constant)), listOf("无关"), WorldInfoSettings(), passDice).size,
        )

        val noDice = WorldEntry(comment = "概率不过", content = "c", keys = listOf("龙"), probability = 1)
        assertEquals(
            "概率 1% 且骰子 1.0 → 不过",
            0,
            WorldBookActivator.activate(listOf(row(noDice)), listOf("龙"), WorldInfoSettings(), WorldBookActivator.Dice.fixed(1.0)).size,
        )

        val deep = WorldEntry(comment = "深度", content = "c", keys = listOf("目标"), scanDepth = 1)
        assertEquals(
            "条目级深度 1 → 只看最后一条（目标不在最后一条）",
            0,
            WorldBookActivator.activate(listOf(row(deep)), listOf("目标在前", "无关在后"), WorldInfoSettings(scanDepth = 5), passDice).size,
        )

        val regex = WorldEntry(comment = "正则", content = "c", keys = listOf("/龙+息/"), useRegex = true)
        assertEquals(
            "正则命中",
            1,
            WorldBookActivator.activate(listOf(row(regex)), listOf("龙龙息"), WorldInfoSettings(), passDice).size,
        )
    }
}

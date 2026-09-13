package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldInfoSettings
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.data.world.WorldRow
import com.luzzymeow.luzzyrp.data.world.WorldScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 世界书激活扫描单测（P5-A）。逐条对齐上游 `data-services.js:772-860`。
 *
 * 骰子一律用 [WorldBookActivator.Dice.fixed] 注入 → 概率相关的行为**完全确定**，
 * 不存在「偶尔绿偶尔红」的用例。
 */
class WorldBookActivatorTest {

    private fun row(
        comment: String,
        keys: List<String> = emptyList(),
        constant: Boolean = false,
        enabled: Boolean = true,
        useRegex: Boolean = false,
        probability: Int = 100,
        useProbability: Boolean = true,
        scanDepth: Int? = null,
        position: WorldPosition = WorldPosition.AtDepth,
        order: Int = 0,
        content: String = "正文",
    ) = WorldRow(
        ref = com.luzzymeow.luzzyrp.data.world.EntryRef(WorldScope.Global, 0),
        entry = WorldEntry(
            comment = comment,
            content = content,
            keys = keys,
            constant = constant,
            enabled = enabled,
            useRegex = useRegex,
            probability = probability,
            useProbability = useProbability,
            scanDepth = scanDepth,
            position = position,
            order = order,
        ),
        group = WorldScope.Global,
    )

    private fun activate(
        rows: List<WorldRow>,
        messages: List<String>,
        settings: WorldInfoSettings = WorldInfoSettings(),
        dice: Double = 0.0,
    ) = WorldBookActivator.activate(rows, messages, settings, WorldBookActivator.Dice.fixed(dice))
        .map { it.comment }

    // ---------------------------------------------------------------- 匹配

    @Test
    fun `关键词是大小写不敏感的子串而非全词`() {
        val rows = listOf(row("苹果", keys = listOf("APPLE")))
        assertEquals(listOf("苹果"), activate(rows, listOf("he picked an apple today")))
        assertEquals("子串也算（上游 includes，不是全词）", listOf("苹果"), activate(rows, listOf("pineapples")))
        assertTrue(activate(rows, listOf("banana")).isEmpty())
    }

    @Test
    fun `中文关键词照常命中`() {
        val rows = listOf(row("钟楼", keys = listOf("苹果树", "钟楼")))
        assertEquals(listOf("钟楼"), activate(rows, listOf("他走向钟楼顶上")))
    }

    @Test
    fun `关掉的条目不触发`() {
        val rows = listOf(row("关掉的", keys = listOf("钟楼"), enabled = false))
        assertTrue(activate(rows, listOf("钟楼")).isEmpty())
    }

    @Test
    fun `常驻条目跳过匹配`() {
        val rows = listOf(row("常驻", constant = true))
        assertEquals("没有任何关键词也应命中", listOf("常驻"), activate(rows, listOf("无关内容")))
    }

    @Test
    fun `空 keys 且非常驻不触发`() {
        assertTrue(activate(listOf(row("无关键词")), listOf("随便什么")).isEmpty())
    }

    @Test
    fun `扫描文本为空时不触发（常驻除外）`() {
        val rows = listOf(row("关键词条", keys = listOf("钟楼")), row("常驻", constant = true))
        assertEquals(listOf("常驻"), activate(rows, emptyList()))
    }

    // ---------------------------------------------------------------- 正则

    @Test
    fun `正则匹配且强制大小写不敏感`() {
        val rows = listOf(row("正则条", keys = listOf("/zhonglou|钟楼/"), useRegex = true))
        assertEquals(listOf("正则条"), activate(rows, listOf("ZHONGLOU is here")))
        assertEquals(listOf("正则条"), activate(rows, listOf("钟楼之上")))
    }

    @Test
    fun `正则的 flags 写法能解析且 i 恒开`() {
        // 写了 g 但不写 i：仍应大小写不敏感（上游强制 i）
        val regex = WorldBookActivator.regexOf("/apple/g")
        assertTrue(regex!!.containsMatchIn("APPLE"))
    }

    @Test
    fun `非法正则不抛异常只是不匹配`() {
        val rows = listOf(row("坏正则", keys = listOf("/[unclosed/"), useRegex = true))
        assertTrue(activate(rows, listOf("随便")).isEmpty())
        assertNull(WorldBookActivator.regexOf("/[unclosed/"))
    }

    @Test
    fun `正则不加斜杠也认（纯 pattern）`() {
        // 注意测试数据本身要能匹配：`z.*u` 对 "zebra zoo" 是不匹配的（串里没有 u）——
        // 这条最早就是我自己写反了期望，被测试抓出来的
        val rows = listOf(row("裸正则", keys = listOf("z.*bra"), useRegex = true))
        assertEquals(listOf("裸正则"), activate(rows, listOf("a zebra here")))
    }

    // ---------------------------------------------------------------- 概率

    @Test
    fun `关闭概率或百分百必过`() {
        assertEquals(
            listOf("必过"),
            activate(listOf(row("必过", keys = listOf("钟楼"), useProbability = false)), listOf("钟楼"), dice = 0.99),
        )
        assertEquals(
            listOf("必过"),
            activate(listOf(row("必过", keys = listOf("钟楼"), probability = 100)), listOf("钟楼"), dice = 0.99),
        )
    }

    @Test
    fun `概率按 roll 乘百比较`() {
        val rows = listOf(row("六成", keys = listOf("钟楼"), probability = 60))
        assertEquals("roll=0.59 → 59 < 60 过", listOf("六成"), activate(rows, listOf("钟楼"), dice = 0.59))
        assertTrue("roll=0.61 → 61 < 60 不过", activate(rows, listOf("钟楼"), dice = 0.61).isEmpty())
    }

    @Test
    fun `概率为零永不过`() {
        assertTrue(
            activate(listOf(row("零", keys = listOf("钟楼"), probability = 0)), listOf("钟楼"), dice = 0.0).isEmpty(),
        )
    }

    @Test
    fun `同一轮内多条共用一个骰子值（一次性掷骰语义）`() {
        // 两条 60% 的条目、同一个 dice=0.7：都应「不过」——若逐条重掷就可能一过一不过
        val rows = listOf(
            row("甲", keys = listOf("钟楼"), probability = 60),
            row("乙", keys = listOf("钟楼"), probability = 60),
        )
        assertTrue(activate(rows, listOf("钟楼"), dice = 0.7).isEmpty())
        assertEquals(2, activate(rows, listOf("钟楼"), dice = 0.5).size)
    }

    // ---------------------------------------------------------------- 扫描深度

    @Test
    fun `扫描深度只取最近 N 条`() {
        val rows = listOf(row("命中", keys = listOf("苹果")))
        val messages = listOf("苹果", "1", "2", "3")  // 苹果在第 1 条（最旧）
        assertTrue("深度 1 只看最后一条 → 不命中", activate(rows, messages, WorldInfoSettings(scanDepth = 1)).isEmpty())
        assertEquals("深度 4 覆盖全部 → 命中", listOf("命中"), activate(rows, messages, WorldInfoSettings(scanDepth = 4)))
    }

    @Test
    fun `条目自带深度优先于全局`() {
        val rows = listOf(row("条目深度4", keys = listOf("苹果"), scanDepth = 4))
        val messages = listOf("苹果", "1", "2", "3")
        assertEquals(
            "全局 1 但条目写 4 → 用条目",
            listOf("条目深度4"),
            activate(rows, messages, WorldInfoSettings(scanDepth = 1)),
        )
    }

    @Test
    fun `maxDepth 夹住全局与条目深度`() {
        assertEquals(2, WorldBookActivator.effectiveDepth(null, WorldInfoSettings(scanDepth = 5, maxDepth = 2)))
        assertEquals(2, WorldBookActivator.effectiveDepth(4, WorldInfoSettings(scanDepth = 5, maxDepth = 2)))
        assertEquals("maxDepth=0 不限制", 5, WorldBookActivator.effectiveDepth(null, WorldInfoSettings(scanDepth = 5, maxDepth = 0)))
    }

    @Test
    fun `深度为零时扫描文本为空`() {
        assertEquals("", WorldBookActivator.scanTextFor(listOf("a", "b"), WorldInfoSettings(scanDepth = 0)))
    }

    // ---------------------------------------------------------------- 分组与渲染

    @Test
    fun `按位置分组且组内按 order 升序`() {
        val entries = listOf(
            WorldEntry(comment = "后", order = 9, position = WorldPosition.SystemTop),
            WorldEntry(comment = "前", order = 1, position = WorldPosition.SystemTop),
            WorldEntry(comment = "深度", order = 0, position = WorldPosition.AtDepth),
        )
        val grouped = WorldBookActivator.groupByPosition(entries)
        assertEquals(listOf("前", "后"), grouped[WorldPosition.SystemTop]!!.map { it.comment })
        assertEquals(listOf("深度"), grouped[WorldPosition.AtDepth]!!.map { it.comment })
        assertTrue(grouped[WorldPosition.UserTop]!!.isEmpty())
    }

    @Test
    fun `渲染成上游的 comment 加内容块`() {
        val text = WorldBookActivator.render(
            listOf(
                WorldEntry(comment = "钟楼", content = "顶上有一棵树"),
                WorldEntry(comment = "", content = "没有名字"),
            ),
        )
        assertEquals("[钟楼]\n顶上有一棵树\n\n[Entry]\n没有名字", text)
    }

    @Test
    fun `空正文条目渲染时丢弃`() {
        assertEquals("", WorldBookActivator.render(listOf(WorldEntry(comment = "空", content = "  "))))
    }

    @Test
    fun `激活结果保持传入顺序`() {
        val rows = listOf(
            row("甲", keys = listOf("钟楼")),
            row("乙", keys = listOf("钟楼")),
            row("丙", keys = listOf("钟楼")),
        )
        assertEquals(listOf("甲", "乙", "丙"), activate(rows, listOf("钟楼")))
    }

    @Test
    fun `真实夹具的全局条目能扫到`() {
        // 夹具里「全局：语言风格」keys=["语气"]、概率 80%；「自动生图」是 constant 但 enabled=false
        val style = WorldEntry(comment = "全局：语言风格", keys = listOf("语气"), content = "风格", probability = 80)
        val autoImage = WorldEntry(comment = "自动生图", content = "生图", constant = true, enabled = false)
        val rows = listOf(
            style.toRow(),
            autoImage.toRow(),
        )
        val hit = activate(rows, listOf("他的语气很平静"), dice = 0.1)
        assertEquals("停用的常驻条目不该出现", listOf("全局：语言风格"), hit)
        assertFalse(hit.contains("自动生图"))
    }

    private fun WorldEntry.toRow() = WorldRow(
        ref = com.luzzymeow.luzzyrp.data.world.EntryRef(WorldScope.Global, 0),
        entry = this,
        group = WorldScope.Global,
    )
}

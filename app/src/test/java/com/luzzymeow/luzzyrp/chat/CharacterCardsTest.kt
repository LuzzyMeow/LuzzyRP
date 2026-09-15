package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色卡页的纯逻辑（v3.2）。
 *
 * 判据围绕「卡片上那四个派生值有没有取对」：世界书 / 正则条数、收藏、描述。
 * 它们全藏在旧版整体搬过来的 payload JSON 里，取错键名**不会报错**——
 * 表现为卡片底部永远写着「0 世界书」，而没人会去怀疑一个 0。
 */
class CharacterCardsTest {

    private fun payload(
        worldInfo: Int = 0,
        regex: Int = 0,
        favoriteAt: String? = null,
        description: String = "",
        extra: String = "",
    ): String {
        val wi = (1..worldInfo).joinToString(",") { """{"keys":["k$it"],"content":"c$it"}""" }
        val rx = (1..regex).joinToString(",") { """{"name":"r$it","pattern":"p"}""" }
        val fav = favoriteAt?.let { ""","favoriteAt":$it""" } ?: ""
        return """{"name":"钟楼下的小恶魔","description":"$description","worldInfo":[$wi],"regexScripts":[$rx]$fav$extra}"""
    }

    @Test
    fun `从 payload 读出世界书与正则条数`() {
        val row = CharacterCards.parse("u1", "Vanio", null, payload(worldInfo = 3, regex = 2), false, 1L)
        assertEquals(3, row.worldInfoCount)
        assertEquals(2, row.regexCount)
        assertEquals("Vanio", row.name)
    }

    @Test
    fun `缺数组或字段类型不对时给 0，不抛`() {
        val noArrays = CharacterCards.parse("u1", "Vanio", null, """{"name":"Vanio"}""", false, 1L)
        assertEquals(0, noArrays.worldInfoCount)
        assertEquals(0, noArrays.regexCount)

        // 类型不对（worldInfo 是字符串而不是数组）
        val wrongType = CharacterCards.parse("u1", "Vanio", null, """{"worldInfo":"不是数组"}""", false, 1L)
        assertEquals(0, wrongType.worldInfoCount)
    }

    @Test
    fun `payload 坏掉也要给出一行（用户至少能看到它、能删它）`() {
        val broken = CharacterCards.parse("u1", "Vanio", null, "{不是 JSON", false, 1L)
        assertEquals("Vanio", broken.name)
        assertEquals(0, broken.worldInfoCount)
        assertFalse(broken.favorite)
    }

    @Test
    fun `收藏认数字、字符串与缺失三种形态`() {
        assertTrue(CharacterCards.parse("u", "N", null, payload(favoriteAt = "1726000000000"), false, 1L).favorite)
        // 旧数据里数字常是字符串
        assertTrue(CharacterCards.parse("u", "N", null, payload(favoriteAt = "\"1726000000000\""), false, 1L).favorite)
        assertFalse(CharacterCards.parse("u", "N", null, payload(favoriteAt = "0"), false, 1L).favorite)
        assertFalse(CharacterCards.parse("u", "N", null, payload(), false, 1L).favorite)
    }

    @Test
    fun `搜索命中名称或描述、大小写不敏感、空关键词全通过`() {
        val row = CharacterCards.parse(
            "u", "Vanio", null, payload(description = "钟楼与红苹果"), false, 1L,
        )
        assertTrue(row.matches("vani"))
        assertTrue(row.matches("VANIO"))
        assertTrue(row.matches("苹果"))
        assertFalse(row.matches("教堂"))
        assertTrue(row.matches("   "))
    }

    @Test
    fun `排序：当前角色最前，其次收藏，再按名字`() {
        val active = CharacterCards.parse("a", "B 卡", null, payload(), isActive = true, createdAt = 1L)
        val favorite = CharacterCards.parse("b", "C 卡", null, payload(favoriteAt = "1"), false, 1L)
        val plainZ = CharacterCards.parse("c", "Z 卡", null, payload(), false, 1L)
        val plainA = CharacterCards.parse("d", "A 卡", null, payload(), false, 1L)
        val sorted = CharacterCards.sorted(listOf(plainZ, favorite, plainA, active))
        assertEquals(listOf("B 卡", "C 卡", "A 卡", "Z 卡"), sorted.map { it.name })
    }

    @Test
    fun `写收藏只动 favoriteAt，其余键逐字保留`() {
        val raw = payload(worldInfo = 2, regex = 1, description = "别动我")
        val on = CharacterCards.withFavorite(raw, true, 999L)
        assertTrue(on.contains("\"favoriteAt\":999"))
        val parsed = CharacterCards.parse("u", "N", null, on, false, 1L)
        assertTrue(parsed.favorite)
        assertEquals(2, parsed.worldInfoCount)
        assertEquals(1, parsed.regexCount)
        assertEquals("别动我", parsed.description)

        val off = CharacterCards.withFavorite(on, false, 1000L)
        assertFalse(CharacterCards.parse("u", "N", null, off, false, 1L).favorite)
    }

    @Test
    fun `坏 payload 上写收藏原样返回（不把它变成一张空卡）`() {
        assertEquals("{坏", CharacterCards.withFavorite("{坏", true, 1L))
    }

    @Test
    fun `空名字回落未命名角色，monogram 回落角字`() {
        val blank = CharacterCards.parse("u", "  ", null, payload(), false, 1L)
        assertEquals("未命名角色", blank.name)
        assertEquals("未", blank.monogram.let { if (it.isBlank()) "未" else it })
        assertEquals("角", CharacterCards.Row("u", "", null, "", 0, 0, false, false, 1L).monogram)
    }
}

package com.luzzymeow.luzzyrp.data.world

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「世界书条目不能按内容作 LazyColumn key」的**证据**（v3.2 收口时补的定性）。
 *
 * ## 为什么需要这条
 *
 * `compose-expert/references/lists-scrolling.md` 的原则是「永远不要用下标作 key」，
 * 而 `LoreBookPages` 的条目列表**恰恰用下标**。那是例外还是违规？判据是：条目有没有
 * 另一个「稳定且唯一」的身份可用。本测试把「没有」钉成事实，免得后人看到下标就当成疏漏改掉。
 *
 * 复制的语义（`LoreBookRepository.duplicateEntry`）= 把原 payload **原样**插到其后
 * ——于是副本与原件的 `comment` / `content` / `order` 全部逐字相同。
 * 按其中任何一个作 key 都会直接撞上 Compose 的重复 key 检查。
 */
class WorldEntryKeyIdentityTest {

    @Test
    fun `复制出来的条目与原条目逐字段相同（内容不可作 key）`() {
        val original = WorldEntry(
            comment = "红苹果树",
            content = "钟楼顶上有棵红苹果树。",
            keys = listOf("苹果"),
            order = 10,
            position = WorldPosition.AtDepth,
        )
        // duplicateEntry 的语义就是「原样再插一份」——这里用同值构造模拟那一份。
        val duplicate = original.copy()

        assertEquals("名字相同 → 按 comment 作 key 会撞", original.comment, duplicate.comment)
        assertEquals("正文相同 → 按 content 作 key 会撞", original.content, duplicate.content)
        assertEquals("order 也相同 → 按 order 作 key 同样会撞", original.order, duplicate.order)
    }

    @Test
    fun `WorldEntry 没有携带身份的唯一字段`() {
        // 反射列出主构造参数名：全部是「内容/配置」语义，没有 id/uid 一类。
        val names = WorldEntry::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
        assertEquals(
            "若将来给 WorldEntry 加了 id/uid，应立刻把 LoreBookPages 的 LazyColumn key 改用它，" +
                "并删掉本测试（它存在的唯一理由就是「当年没有身份字段」）。实际字段=$names",
            emptyList<String>(),
            names.filter { it.equals("id", true) || it.equals("uid", true) },
        )
    }
}

package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `before_char` / `after_char` 两档世界书的**注入次数**门禁（v3.2 修重复注入的守卫）。
 *
 * ## 修的是什么
 *
 * 这两档此前被**逐字发了两遍**：一遍在 system 稳定块（`PromptSections.stableSections`
 * 的 `world-before-char` / `world-after-char`），一遍在角色前置 user 消息（`assembleDetailed`
 * 的 prelude）。修前探针实测：system 里 2 次、整个请求里 4 次。
 *
 * 危害不只是 token：同一段设定出现两次，模型可能当成强调；prelude 是 USER 消息，
 * 等于把设定冒充成用户说的话；两个副本各自变形还会让前缀缓存的「哪里变了」难以定位。
 *
 * ## 判据为什么是「次数」而不是「包含」
 *
 * `contains` 在修复前后**都成立**——正是这种断言让这个缺陷活了下来。
 * 只有数次数才能区分「发了一遍」与「发了两遍」。
 */
class WorldEntryInjectionCountTest {

    private fun entry(comment: String, position: WorldPosition) = WorldEntry(
        comment = comment,
        content = "$comment 的正文",
        constant = true,
        position = position,
    )

    private val world = listOf(
        entry("角色前", WorldPosition.BeforeChar),
        entry("角色后", WorldPosition.AfterChar),
        entry("系统顶", WorldPosition.SystemTop),
    )

    private fun requestText(): String = PromptAssembler.assembleDetailed(
        PromptAssembler.Input(
            worldEntries = world,
            character = PromptAssembler.CharacterView(
                name = "角色",
                description = "描述",
                personality = "",
                mesExample = "",
                firstMes = "",
            ),
            userText = "问题",
        ),
    ).messages.joinToString("\n") { it.content }

    @Test
    fun `before_char 与 after_char 各只注入一次`() {
        val text = requestText()
        assertEquals("`before_char` 必须恰好注入一次", 1, Regex("角色前 的正文").findAll(text).count())
        assertEquals("`after_char` 必须恰好注入一次", 1, Regex("角色后 的正文").findAll(text).count())
    }

    @Test
    fun `system_top 档同样只注入一次（对照：它本来就只有一条路径）`() {
        val text = requestText()
        assertEquals(1, Regex("系统顶 的正文").findAll(text).count())
    }

    @Test
    fun `两档落在 system 稳定块里（按 DESIGN-compose 24_3 的既定口径）`() {
        val systemText = PromptSections.render(PromptSections.stableSections(worldEntries = world))
        assertEquals(1, Regex("角色前 的正文").findAll(systemText).count())
        assertEquals(1, Regex("角色后 的正文").findAll(systemText).count())
    }

    @Test
    fun `角色前置 user 消息里只剩角色块、没有世界书副本`() {
        val assembled = PromptAssembler.assembleDetailed(
            PromptAssembler.Input(
                worldEntries = world,
                character = PromptAssembler.CharacterView(name = "角色", description = "描述"),
                userText = "问题",
            ),
        )
        // 第二条就是 prelude（第一条是 system）
        val prelude = assembled.messages[1].content
        assertEquals("prelude 里不该再有世界书副本", false, prelude.contains("角色前 的正文"))
        assertEquals("prelude 里不该再有世界书副本", false, prelude.contains("角色后 的正文"))
        assertEquals("prelude 必须仍有角色块", true, prelude.contains("[Character]"))
    }
}

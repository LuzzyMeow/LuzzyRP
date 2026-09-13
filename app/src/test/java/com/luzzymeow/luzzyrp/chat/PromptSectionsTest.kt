package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 稳定块 / 尾部快照单测（P5-A 的 A2+A3）。
 *
 * 这组用例守的是**缓存收益**本身：
 * - 同一次组装跑两遍必须**逐字节相同**（决定化排序）；
 * - 内容没变 → 快照**一条都不发**（[RuntimeSnapshots.project] 返回 null）；
 * - 历史**永不被改写**（快照只追加，不就地改）。
 */
class PromptSectionsTest {

    private fun section(name: String, order: Int, text: String) = PromptSections.Section(name, order, text)

    // ---------------------------------------------------------------- 决定化排序

    @Test
    fun `section 按 order 升序、同 order 按名字典序`() {
        val rendered = PromptSections.render(
            listOf(
                section("z-last", 200, "B"),
                section("a-first", 200, "A"),
                section("early", 100, "1"),
            ),
        )
        assertEquals("1\n\nA\n\nB", rendered)
    }

    @Test
    fun `空块被丢弃且不产生多余分隔符`() {
        val rendered = PromptSections.render(
            listOf(
                section("a", 100, "A"),
                section("b", 200, "   "),
                section("c", 300, "C"),
            ),
        )
        assertEquals("A\n\nC", rendered)
    }

    @Test
    fun `同一输入组装两遍逐字节相同（决定化的意义）`() {
        val presets = listOf(
            PresetEntry(name = "破限", role = PresetRole.System, content = "破限内容"),
            PresetEntry(name = "乙", role = PresetRole.System, content = "乙内容"),
            PresetEntry(name = "甲", role = PresetRole.System, content = "甲内容"),
        )
        val world = listOf(
            worldEntry("钟楼", WorldPosition.SystemTop),
            worldEntry("苹果树", WorldPosition.SystemTop),
        )
        fun build() = PromptSections.render(PromptSections.stableSections(presets = presets, worldEntries = world))
        assertEquals("同一输入必须产出同一文本（否则缓存会无故失效）", build(), build())

        // 世界书**打乱顺序**结果必须相同（它的顺序不是用户语义，是并列设定）
        val shuffledWorld = PromptSections.render(
            PromptSections.stableSections(presets = presets, worldEntries = world.reversed()),
        )
        assertEquals("世界书顺序不影响 system 文本", build(), shuffledWorld)
    }

    @Test
    fun `预设顺序是用户语义，不打乱`() {
        // 与上一条的对照：预设的「顺序即注入顺序」（预设页上写着这条语义），
        // 所以它**必须**保留列表顺序——决定化只作用于世界书那种并列内容。
        val presets = listOf(
            PresetEntry(name = "破限", role = PresetRole.System, content = "破限内容"),
            PresetEntry(name = "乙", role = PresetRole.System, content = "乙内容"),
            PresetEntry(name = "甲", role = PresetRole.System, content = "甲内容"),
        )
        val text = PromptSections.render(PromptSections.stableSections(presets = presets))
        assertTrue(text.indexOf("乙内容") < text.indexOf("甲内容"))
    }

    @Test
    fun `世界书在 system 里按 comment 定序（不按用户拖拽顺序）`() {
        val world = listOf(
            worldEntry("丙丙丙", WorldPosition.SystemTop),
            worldEntry("甲甲甲", WorldPosition.SystemTop),
            worldEntry("乙乙乙", WorldPosition.SystemTop),
        )
        val text = PromptSections.render(PromptSections.stableSections(worldEntries = world))
        val positions = listOf("甲甲甲", "乙乙乙", "丙丙丙").map { text.indexOf("[$it]") }
        assertTrue("三条都在", positions.all { it >= 0 })
        // 关键不是「按拼音」而是**决定化**：同一输入任何顺序都得到同一文本（下一条用例钉这个）
        val reversed = PromptSections.render(
            PromptSections.stableSections(worldEntries = world.reversed()),
        )
        assertEquals("拖拽顺序不影响 system 文本", text, reversed)
    }

    @Test
    fun `破限排在最前且其余 system 预设按列表序`() {
        val presets = listOf(
            PresetEntry(name = "防抢话", role = PresetRole.System, content = "防抢话内容"),
            PresetEntry(name = "破限", role = PresetRole.System, content = "破限内容"),
            PresetEntry(name = "防重复", role = PresetRole.System, content = "防重复内容"),
        )
        val text = PromptSections.render(PromptSections.stableSections(presets = presets))
        val breakIdx = text.indexOf("破限内容")
        val antiIdx = text.indexOf("防抢话内容")
        val dupIdx = text.indexOf("防重复内容")
        assertTrue("破限必须是第一块", breakIdx < antiIdx)
        assertTrue("其余预设保持列表顺序（顺序即注入顺序）", antiIdx < dupIdx)
    }

    @Test
    fun `User 与 AI 类预设不进 system`() {
        val presets = listOf(
            PresetEntry(name = "用户侧", role = PresetRole.User, content = "USER_PRESET_MARK"),
            PresetEntry(name = "AI侧", role = PresetRole.Assistant, content = "AI_PRESET_MARK"),
        )
        val text = PromptSections.render(PromptSections.stableSections(presets = presets))
        assertTrue("User 类预设不该出现在 system", !text.contains("USER_PRESET_MARK"))
        assertTrue("AI 类预设不该出现在 system", !text.contains("AI_PRESET_MARK"))
    }

    // ---------------------------------------------------------------- 分派：谁进 system、谁进快照

    @Test
    fun `四类稳定 position 进 system、三类漂移 position 进快照`() {
        val world = listOf(
            worldEntry("系统顶", WorldPosition.SystemTop),
            worldEntry("全局注", WorldPosition.GlobalNote),
            worldEntry("角色前", WorldPosition.BeforeChar),
            worldEntry("角色后", WorldPosition.AfterChar),
            worldEntry("深度插", WorldPosition.AtDepth),
            worldEntry("用户顶", WorldPosition.UserTop),
            worldEntry("AI顶", WorldPosition.AssistantTop),
        )
        val systemText = PromptSections.render(PromptSections.stableSections(worldEntries = world))
        assertTrue(systemText.contains("系统顶"))
        assertTrue(systemText.contains("全局注"))
        assertTrue(systemText.contains("角色前"))
        assertTrue(systemText.contains("角色后"))
        assertTrue("漂移型不进 system", !systemText.contains("深度插"))
        assertTrue("漂移型不进 system", !systemText.contains("用户顶"))
        assertTrue("漂移型不进 system", !systemText.contains("AI顶"))

        val snapshots = PromptSections.snapshotSections(worldEntries = world)
        val snapshotText = snapshots.joinToString("\n") { it.text }
        assertTrue(snapshotText.contains("深度插"))
        assertTrue(snapshotText.contains("用户顶"))
        assertTrue(snapshotText.contains("AI顶"))
        assertTrue("稳定型不进快照", !snapshotText.contains("系统顶"))
    }

    @Test
    fun `记忆召回进快照不进 system`() {
        val recall = "<memory_recall>\n- （第 3 轮）往事\n</memory_recall>"
        val systemText = PromptSections.render(PromptSections.stableSections(worldEntries = emptyList()))
        assertTrue(!systemText.contains("<memory_recall>"))
        val snapshots = PromptSections.snapshotSections(worldEntries = emptyList(), recallBlock = recall)
        assertTrue(snapshots.any { it.text.contains("<memory_recall>") })
    }

    // ---------------------------------------------------------------- 快照去重（收益核心）

    @Test
    fun `内容没变就一条都不发`() {
        val current = listOf(RuntimeSnapshots.Snapshot("world-dynamic", 100, "设定内容"))
        val first = RuntimeSnapshots.project(current, retained = null)
        assertTrue("第一次要发", first != null)
        val second = RuntimeSnapshots.project(current, retained = first)
        assertNull("★ 内容逐字相同 → 不发（这正是缓存收益的来源）", second)
    }

    @Test
    fun `内容变了才发新快照`() {
        val before = RuntimeSnapshots.project(
            listOf(RuntimeSnapshots.Snapshot("world-dynamic", 100, "旧设定")),
            retained = null,
        )
        val after = RuntimeSnapshots.project(
            listOf(RuntimeSnapshots.Snapshot("world-dynamic", 100, "新设定")),
            retained = before,
        )
        assertTrue(after != null)
        assertNotEquals(before, after)
        assertTrue(after!!.startsWith(RuntimeSnapshots.HEADER))
    }

    @Test
    fun `从来没有动态内容时不发（连清空声明都不必）`() {
        assertNull(RuntimeSnapshots.project(emptyList(), retained = null))
    }

    @Test
    fun `发过之后内容清空要发一条作废声明`() {
        val had = RuntimeSnapshots.project(
            listOf(RuntimeSnapshots.Snapshot("world-dynamic", 100, "设定")),
            retained = null,
        )
        val cleared = RuntimeSnapshots.project(emptyList(), retained = had)
        assertEquals(
            "必须显式作废：什么都不发的话，模型会把旧快照当成仍然有效",
            RuntimeSnapshots.CLEARED,
            cleared,
        )
        // 再发一次同样的清空声明 → 不再发
        assertNull(RuntimeSnapshots.project(emptyList(), retained = cleared))
    }

    @Test
    fun `快照顺序决定化（同 order 按名）`() {
        val a = RuntimeSnapshots.project(
            listOf(
                RuntimeSnapshots.Snapshot("zzz", 100, "Z"),
                RuntimeSnapshots.Snapshot("aaa", 100, "A"),
                RuntimeSnapshots.Snapshot("mmm", 50, "M"),
            ),
            retained = null,
        )
        assertTrue(a!!.indexOf("M") < a.indexOf("A"))
        assertTrue(a.indexOf("A") < a.indexOf("Z"))
    }

    @Test
    fun `快照头部带作废声明`() {
        val text = RuntimeSnapshots.project(
            listOf(RuntimeSnapshots.Snapshot("x", 100, "内容")),
            retained = null,
        )
        assertTrue(text!!.startsWith(RuntimeSnapshots.HEADER))
        assertTrue(text.contains("内容"))
    }

    @Test
    fun `空文本的 section 不参与快照也不造成假变化`() {
        val withBlank = listOf(
            RuntimeSnapshots.Snapshot("blank", 100, "   "),
            RuntimeSnapshots.Snapshot("real", 200, "真内容"),
        )
        val a = RuntimeSnapshots.project(withBlank, retained = null)
        val b = RuntimeSnapshots.project(listOf(RuntimeSnapshots.Snapshot("real", 200, "真内容")), retained = null)
        assertEquals("空块被丢弃 → 与不含空块的结果一致（否则会出现假变化）", a, b)
    }

    // ---------------------------------------------------------------- 组装层面的缓存性质

    @Test
    fun `相邻两轮请求在快照不变时是纯追加`() {
        val history = listOf(
            LlmMessage(role = LlmRole.USER, content = "第一句"),
            LlmMessage(role = LlmRole.ASSISTANT, content = "第一句的回复"),
        )
        val base = PromptAssembler.Input(
            character = PromptAssembler.CharacterView(name = "阿离", description = "设定"),
            worldEntries = listOf(worldEntry("动态", WorldPosition.AtDepth)),
            history = history,
            userText = "第二句",
        )
        val turn1 = PromptAssembler.assembleDetailed(base.copy(retainedSnapshot = null))
        assertTrue("第一轮发了快照", turn1.snapshotText != null)

        // ★ 契约：调用方必须把快照**落盘成一条历史消息**（DSH 也是把它 accept 成耐久消息，
        //   agent.ts:245-254）。否则下一轮历史里没有它 → 位置漂移 → 前缀断裂。
        //   这里模拟「已落盘」：把快照消息追加进历史。
        val historyAfterTurn1 = history + listOf(
            LlmMessage(role = LlmRole.USER, content = "第二句"),
            LlmMessage(role = LlmRole.ASSISTANT, content = "第二句的回复"),
            LlmMessage(role = LlmRole.USER, content = turn1.snapshotText!!, fromHistory = true),
        )
        val turn2 = PromptAssembler.assembleDetailed(
            base.copy(history = historyAfterTurn1, userText = "第三句", retainedSnapshot = turn1.snapshotText),
        )

        assertNull("第二轮快照没变 → 不再发新消息", turn2.snapshotText)
        // 实测形状（见 SeqProbe）：
        //   T1: [SYSTEM, prelude, 第一句, 回复, ★快照, 第二句]
        //   T2: [SYSTEM, prelude, 第一句, 回复, 第二句, 回复, ★快照(原样), 第三句]
        //   即：快照落盘后就在历史里，第二轮**原样沿用**它，只在尾部追加。
        assertEquals(
            "system + prelude 逐字不变",
            turn1.messages.take(2).map { it.role to it.content },
            turn2.messages.take(2).map { it.role to it.content },
        )
        assertEquals(
            "第一轮的快照原样出现在第二轮（位置在历史段，不被改写）",
            turn1.snapshotText,
            turn2.messages[6].content,
        )
        assertEquals("第二轮末尾是本轮输入", "第三句", turn2.messages.last().content)
    }

    @Test
    fun `快照不落盘就会每轮重发（反证：落盘是必须的）`() {
        val base = PromptAssembler.Input(
            worldEntries = listOf(worldEntry("动态", WorldPosition.AtDepth)),
            userText = "问题",
        )
        val turn1 = PromptAssembler.assembleDetailed(base.copy(retainedSnapshot = null))
        // 不落盘、但把 retained 传下去：快照「没变」所以第二轮不发 —— 但它也不在历史里，
        // 于是第二轮请求里**没有快照**，与第一轮不同 → 前缀仍会断。
        val turn2 = PromptAssembler.assembleDetailed(base.copy(retainedSnapshot = turn1.snapshotText))
        assertNull("第二轮不发", turn2.snapshotText)
        assertTrue(
            "而这正说明：retained 必须与「快照已落盘」同时成立，否则模型看不到动态上下文",
            turn2.messages.none { it.content.startsWith(RuntimeSnapshots.HEADER) },
        )
    }

    @Test
    fun `快照变化时只在尾部追加而历史不被改写`() {
        // 模拟「第一轮的快照已落盘进历史」
        val firstSnapshotText = RuntimeSnapshots.project(
            PromptSections.snapshotSections(listOf(worldEntry("第一版", WorldPosition.AtDepth))),
            retained = null,
        )!!
        val history = listOf(
            LlmMessage(role = LlmRole.USER, content = "第一句"),
            LlmMessage(role = LlmRole.USER, content = firstSnapshotText, fromHistory = true),
        )
        val base = PromptAssembler.Input(
            character = PromptAssembler.CharacterView(name = "阿离"),
            history = history,
            userText = "第二句",
        )

        val turn1 = PromptAssembler.assembleDetailed(
            base.copy(
                worldEntries = listOf(worldEntry("第一版", WorldPosition.AtDepth)),
                retainedSnapshot = firstSnapshotText,
            ),
        )
        val turn2 = PromptAssembler.assembleDetailed(
            base.copy(
                worldEntries = listOf(worldEntry("第二版", WorldPosition.AtDepth)),
                retainedSnapshot = firstSnapshotText,
            ),
        )

        // system 段逐字不变（缓存靠它）
        assertEquals("system 绝不因快照变化而变", turn1.messages[0].content, turn2.messages[0].content)
        // 旧快照仍在历史里（原文未被改写）
        assertTrue("旧快照仍在历史里", turn2.messages.any { it.content == firstSnapshotText })
        assertTrue("旧内容可见", turn2.messages.any { it.content.contains("第一版") })
        // 新快照追加在尾部
        assertTrue("内容变了 → 必须发新快照", turn2.snapshotText != null)
        assertTrue("新快照含新内容", turn2.snapshotText!!.contains("第二版"))
        // 关键：第二轮的前两条（system + prelude）与第一轮逐字相同 —— 前缀没被改写
        assertEquals(
            "system 与 prelude 逐字不变（快照变化不该动它们）",
            turn1.messages.take(2).map { it.role to it.content },
            turn2.messages.take(2).map { it.role to it.content },
        )
    }

    @Test
    fun `快照的位置在本轮用户输入之前`() {
        val result = PromptAssembler.assembleDetailed(
            PromptAssembler.Input(
                worldEntries = listOf(worldEntry("动态", WorldPosition.AtDepth)),
                userText = "本轮输入",
            ),
        )
        val snapshotIdx = result.messages.indexOfFirst { it.content.startsWith(RuntimeSnapshots.HEADER) }
        val userIdx = result.messages.indexOfLast { it.content == "本轮输入" }
        assertTrue("快照必须在历史之后、本轮输入之前", snapshotIdx in 0 until userIdx)
    }

    @Test
    fun `最后一条消息必须是本轮用户输入`() {
        val result = PromptAssembler.assembleDetailed(
            PromptAssembler.Input(
                character = PromptAssembler.CharacterView(name = "阿离", firstMes = "开场白"),
                worldEntries = listOf(worldEntry("动态", WorldPosition.UserTop)),
                userText = "本轮输入",
            ),
        )
        val last = result.messages.last()
        assertEquals(LlmRole.USER, last.role)
        assertEquals("本轮输入", last.content)
    }

    private fun worldEntry(comment: String, position: WorldPosition) = WorldEntry(
        comment = comment,
        content = "$comment 的正文",
        constant = true,
        position = position,
    )
}

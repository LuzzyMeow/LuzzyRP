package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition

/**
 * system 的**命名 section + order**（A2）：把「哪些内容进 system、以什么顺序」变成可单测的纯函数。
 *
 * ## 为什么要命名与定序（而不是就地拼一串）
 *
 * 服务端前缀缓存按**最长公共前缀**匹配：只要 system 文本有一处变化，它之后的所有内容全部作废。
 * 所以「同 order 内的顺序必须**决定化**」——按 section 名 code-unit 序排
 * （DSH `compareToolNames` 的同款理由：locale-independent，任何机器上结果一致，
 * 见 `system-prompt/src/index.ts:210-239`）。
 *
 * 若在这里按「用户拖拽顺序」或 HashMap 迭代顺序拼，同一次编辑在两次请求里就可能产出不同文本 →
 * 缓存静默失效，且**没有任何报错**。
 *
 * ## 稳定块 vs 易变块的分派（本计划 §1.3）
 *
 * | 内容 | 去哪 | 为什么 |
 * |---|---|---|
 * | 破限 / 其余 system 预设 / 用户信息 / 角色块 / 工具提示 | **稳定块**（本文件） | 不随轮次变 |
 * | 世界书 `system_top`/`global_note`/`before_char`/`after_char` | **稳定块**（本文件） | 位置固定在 system 内，不漂移 |
 * | 世界书 `at_depth`/`user_top`/`assistant_top` + 记忆召回 | **尾部快照**（[RuntimeSnapshots]） | 三者都随轮次漂移（详见计划 §1.3 的数学理由） |
 */
object PromptSections {

    /** 一个命名 section。`order` 小的在前；同 order 按 [name] code-unit 序。 */
    data class Section(val name: String, val order: Int, val text: String)

    // ---- order 常量（留出间隔便于将来插入，不必重排既有项）----
    const val ORDER_BREAK_PRESET = 100
    const val ORDER_SYSTEM_PRESET = 200
    const val ORDER_WORLD_SYSTEM_TOP = 300
    const val ORDER_WORLD_GLOBAL_NOTE = 310
    const val ORDER_USER_INFO = 400
    const val ORDER_CHARACTER = 500
    const val ORDER_WORLD_BEFORE_CHAR = 600
    const val ORDER_WORLD_AFTER_CHAR = 610
    const val ORDER_TOOL_HINT = 700

    /** 破限预设的名字（上游 `BUILTIN_PRESETS` 用它把破限单独提到 system 最前）。 */
    const val BREAK_PRESET_NAME = "破限"

    /**
     * 组装稳定块。
     *
     * @param presets 已启用的预设（内部自行按 role 分流）
     * @param worldEntries **本次激活**的世界书条目（只用于取 4 个稳定 position）
     * @param character 角色块（[PromptAssembler.CharacterView]）
     * @param user 用户信息块
     * @param toolHint 工具使用提示（静态文本）
     */
    fun stableSections(
        presets: List<PresetEntry> = emptyList(),
        worldEntries: List<WorldEntry> = emptyList(),
        character: PromptAssembler.CharacterView? = null,
        user: PromptAssembler.UserView = PromptAssembler.UserView(),
        toolHint: String = "",
    ): List<Section> {
        val world = WorldBookActivator.groupByPosition(worldEntries)
        val enabledPresets = presets.filter { it.enabled && it.content.isNotBlank() }
        val systemPresets = enabledPresets.filter { it.role == PresetRole.System }
        val breakPresets = systemPresets.filter { it.name == BREAK_PRESET_NAME }
        val otherPresets = systemPresets.filter { it.name != BREAK_PRESET_NAME }

        return buildList {
            breakPresets.firstOrNull()?.let {
                add(Section(BREAK_PRESET_NAME, ORDER_BREAK_PRESET, it.content))
            }
            if (otherPresets.isNotEmpty()) {
                add(
                    Section(
                        name = "system-presets",
                        order = ORDER_SYSTEM_PRESET,
                        // 上游用 '\n\n---\n\n' 连接；顺序取**预设列表自身的顺序**
                        // （顺序即注入顺序——那本来就是用户语义的一部分，见 PrestsPage 的说明）
                        text = otherPresets.joinToString("\n\n---\n\n") { it.content },
                    ),
                )
            }
            worldSection("world-system-top", ORDER_WORLD_SYSTEM_TOP, world[WorldPosition.SystemTop])?.let(::add)
            worldSection("world-global-note", ORDER_WORLD_GLOBAL_NOTE, world[WorldPosition.GlobalNote])?.let(::add)
            PromptAssembler.buildUserInfo(user)?.let {
                add(Section("user-info", ORDER_USER_INFO, it))
            }
            PromptAssembler.buildCharacterBlock(character)?.let {
                add(Section("character", ORDER_CHARACTER, it))
            }
            worldSection("world-before-char", ORDER_WORLD_BEFORE_CHAR, world[WorldPosition.BeforeChar])?.let(::add)
            worldSection("world-after-char", ORDER_WORLD_AFTER_CHAR, world[WorldPosition.AfterChar])?.let(::add)
            if (toolHint.isNotBlank()) add(Section("tool-hint", ORDER_TOOL_HINT, toolHint))
        }
    }

    /**
     * 世界书 section：**条目按 `comment` 字典序排**（不是用户拖拽顺序）。
     *
     * 理由：拖拽顺序一变，整段 system 文本就位移 → 缓存作废。而世界书条目在 system 里的
     * **相对顺序对模型语义影响很小**（它们是并列的设定片段，不像预设那样有「注入次序」含义），
     * 所以这里用确定性换缓存稳定是划算的。**用户拖拽顺序仍保留在存储里**，
     * 只影响它在世界书页里的展示顺序。
     */
    private fun worldSection(name: String, order: Int, entries: List<WorldEntry>?): Section? {
        if (entries.isNullOrEmpty()) return null
        val sorted = entries.sortedWith(compareBy({ it.comment }, { it.content.length }, { it.content }))
        val text = WorldBookActivator.render(sorted)
        return text.takeIf { it.isNotBlank() }?.let { Section(name, order, it) }
    }

    /**
     * 渲染成最终 system 文本：`order` 升序，同 order 按 `name` code-unit 序，空块丢弃，`\n\n` 连接。
     * ——DSH `renderPrompt` 的等价实现（`system-prompt/src/index.ts:273-278`）。
     */
    fun render(sections: List<Section>): String = sections
        .filter { it.text.isNotBlank() }
        .sortedWith(compareBy({ it.order }, { it.name }))
        .joinToString("\n\n") { it.text }

    // ------------------------------------------------------------------ 尾部快照分派

    /**
     * 快照 section：由「会漂移」的三类内容 + 记忆召回组成。
     *
     * 世界书里 `at_depth`/`user_top`/`assistant_top` 三种 position 被**改道**到这里
     * （计划 §1.3：它们随轮次漂移，插进历史/改写历史会每轮打断前缀缓存）。
     */
    fun snapshotSections(
        worldEntries: List<WorldEntry> = emptyList(),
        recallBlock: String = "",
    ): List<RuntimeSnapshots.Snapshot> {
        val world = WorldBookActivator.groupByPosition(worldEntries)
        return buildList {
            // 三种漂移型 position 合并成一条快照段：它们同属「本轮的动态设定上下文」
            val drifting = buildList {
                addAll(world[WorldPosition.AtDepth].orEmpty())
                addAll(world[WorldPosition.UserTop].orEmpty())
                addAll(world[WorldPosition.AssistantTop].orEmpty())
            }
            if (drifting.isNotEmpty()) {
                val text = WorldBookActivator.render(
                    drifting.sortedWith(compareBy({ it.comment }, { it.order }, { it.content })),
                )
                if (text.isNotBlank()) {
                    add(RuntimeSnapshots.Snapshot("world-dynamic", RuntimeSnapshots.ORDER_WORLD_DYNAMIC, text))
                }
            }
            if (recallBlock.isNotBlank()) {
                add(RuntimeSnapshots.Snapshot("memory-recall", RuntimeSnapshots.ORDER_MEMORY_RECALL, recallBlock))
            }
        }
    }
}

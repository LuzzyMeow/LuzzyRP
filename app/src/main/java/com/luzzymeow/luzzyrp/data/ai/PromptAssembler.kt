package com.luzzymeow.luzzyrp.data.ai

import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.core.model.InjectionPosition
import com.luzzymeow.luzzyrp.core.model.PromptPreset
import com.luzzymeow.luzzyrp.core.model.PromptRole
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.SummaryItem
import com.luzzymeow.luzzyrp.core.model.SummaryLevel
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.core.model.UserProfile
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import com.luzzymeow.luzzyrp.data.ai.prompts.AgenticProtocol
import com.luzzymeow.luzzyrp.data.ai.prompts.NsfwBlock
import com.luzzymeow.luzzyrp.data.ai.tools.ToolRegistry
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository

/**
 * [INVARIANT-KV] 系统提示三层组装器（HARD_REQUIREMENTS 规定 8）
 *
 * 消息布局（KV 缓存命中的核心设计）：
 *
 *   [1] system：稳定前缀（几乎从不变动）
 *       预设(↑Char) → 角色卡人格 → NSFW 块 → 用户档案 → 世界书(↑Char) →
 *       C 级摘要 → 预设(↓Char/↑EM/↓EM) → 工具说明 → Agentic 协议
 *   [2] …对话历史（append-only，前缀逐字节稳定）…
 *   [3] system：动态上下文块（每轮变动，置于尾部以保护 [1][2] 的缓存）
 *       B 级摘要 → A 级摘要 → 记忆 Top-K → 被动世界书召回
 *   [4] @Depth 注入消息（预设/世界书的 AT_DEPTH 条目，按 depth 距末尾插入，角色可为 system/user/assistant）
 *   [5] user：本轮输入
 *
 * 铁律：
 *   - [1] 内字段顺序固定（改动需 CHANGELOG 声明缓存失效）；
 *   - 任何易变内容禁止进入 [1]；动态内容只允许出现在 [3][4]；
 *   - NSFW 块与预设内容原样注入，不审查不改写。
 */
class PromptAssembler(
    private val worldbookRepository: WorldbookRepository,
) {

    data class Assembled(
        val systemMessage: UIMessage,
        val dynamicBlock: String?,
        /** @Depth 注入项（预设 + 世界书），按 depth 升序。 */
        val depthInjections: List<DepthInjection>,
        val passiveRecallReport: String,
    )

    data class DepthInjection(
        val depth: Int,
        val role: Role,
        val content: String,
    )

    fun assemble(
        card: CharacterCard?,
        toolRegistry: ToolRegistry,
        tagFallback: Boolean,
        summariesC: List<SummaryItem>,
        summariesB: List<SummaryItem>,
        summariesA: List<SummaryItem>,
        memories: List<String>,
        recall: WorldbookRepository.RecallResult,
        preset: PromptPreset?,
        userProfile: UserProfile,
    ): Assembled {
        // ─────────── [1] 稳定前缀 ───────────
        val enabledPresetEntries = preset?.entries?.filter { it.enabled }
            ?.sortedBy { it.order }
            .orEmpty()

        fun presetAt(position: InjectionPosition): List<String> =
            enabledPresetEntries.filter { it.position == position }.map { it.content }

        val stable = StringBuilder()

        // 1.1 预设 ↑Char（最前）
        presetAt(InjectionPosition.BEFORE_CHAR).forEach { stable.append(it.trim()).append("\n\n") }

        // 1.2 角色卡人格 / 默认 RP 提示
        if (card != null) {
            stable.append(card.systemPrompt.ifBlank { DefaultRpPrompt.SYSTEM })
            stable.append("\n\n").append(card.personaPrompt())
        } else {
            stable.append(DefaultRpPrompt.SYSTEM)
        }

        // 1.3 NSFW 块（[INVARIANT-NSFW] 原样注入）
        if (NsfwBlock.BLOCK.isNotBlank()) {
            stable.append("\n\n").append(NsfwBlock.BLOCK)
        }

        // 1.4 用户档案（7.1：注入稳定前缀的用户段）
        if (userProfile.isConfigured) {
            stable.append("\n【用户档案（对话者设定）】\n")
            if (userProfile.name.isNotBlank()) {
                stable.append("• 名字：").append(userProfile.name).append("\n")
            }
            if (userProfile.identity.isNotBlank()) {
                stable.append("• 身份：").append(userProfile.identity).append("\n")
            }
        }

        // 1.5 世界书稳定位（↑Char → 用户档案后；↓Char / ↑EM / ↓EM 依次）
        val stableEntries = recall.stableEntries
        fun section(title: String, entries: List<WorldbookEntry>) {
            if (entries.isNotEmpty()) {
                stable.append("\n【").append(title).append("】\n")
                entries.forEach { stable.append("• ").append(it.content.trim()).append("\n") }
            }
        }
        section("世界设定（先于角色）", stableEntries.filter { it.position == InjectionPosition.BEFORE_CHAR })
        section("世界设定（角色之后）", stableEntries.filter { it.position == InjectionPosition.AFTER_CHAR })

        // 1.6 C 级摘要（永久事实）
        if (summariesC.isNotEmpty()) {
            stable.append("\n【既成事实（永久）】\n")
            summariesC.forEach { stable.append("• ").append(it.content.trim()).append("\n") }
        }

        // 1.7 预设 ↓Char / ↑EM / ↓EM
        section("对话示例引导", stableEntries.filter { it.position == InjectionPosition.BEFORE_EXAMPLE })
        presetAt(InjectionPosition.AFTER_CHAR).forEach { stable.append("\n").append(it.trim()) }
        presetAt(InjectionPosition.BEFORE_EXAMPLE).forEach { stable.append("\n").append(it.trim()) }
        section("示例之后的世界设定", stableEntries.filter { it.position == InjectionPosition.AFTER_EXAMPLE })
        presetAt(InjectionPosition.AFTER_EXAMPLE).forEach { stable.append("\n").append(it.trim()) }

        // 1.8 工具说明 + Agentic 协议
        if (!toolRegistry.isEmpty()) {
            stable.append("\n\n【可用工具】\n").append(toolRegistry.systemPrompts())
            stable.append("\n").append(AgenticProtocol.PROTOCOL)
            if (tagFallback) {
                stable.append(AgenticProtocol.TAG_TOOL_PROTOCOL)
            }
        }

        val systemMessage = UIMessage(
            role = Role.SYSTEM,
            parts = listOf(UIMessagePart.Text(stable.toString())),
        )

        // ─────────── [3] 动态上下文块 ───────────
        val dynamic = StringBuilder()
        if (summariesB.isNotEmpty()) {
            dynamic.append("【剧情回顾（近期阶段）】\n")
            summariesB.takeLast(10).forEach { dynamic.append("• ").append(it.content.trim()).append("\n") }
        }
        if (summariesA.isNotEmpty()) {
            dynamic.append("【最近发生】\n")
            summariesA.takeLast(6).forEach { dynamic.append("• ").append(it.content.trim()).append("\n") }
        }
        if (memories.isNotEmpty()) {
            dynamic.append("【相关长期记忆】\n")
            memories.forEach { dynamic.append("• ").append(it.trim()).append("\n") }
        }
        if (recall.dynamicEntries.isNotEmpty()) {
            dynamic.append("【场景细节】\n")
            recall.dynamicEntries.forEach { dynamic.append("• ").append(it.content.trim()).append("\n") }
        }

        // ─────────── [4] @Depth 注入项 ───────────
        val depthInjections = buildList {
            enabledPresetEntries.filter { it.position == InjectionPosition.AT_DEPTH }.forEach {
                add(DepthInjection(it.depth, it.role.toRole(), it.content))
            }
            recall.dynamicEntries.filter { it.position == InjectionPosition.AT_DEPTH }.forEach {
                add(DepthInjection(it.depth, it.depthRole.toRole(), it.content))
            }
        }.sortedBy { it.depth }

        return Assembled(
            systemMessage = systemMessage,
            dynamicBlock = dynamic.toString().takeIf { it.isNotBlank() },
            depthInjections = depthInjections,
            passiveRecallReport = (recall.stableEntries + recall.dynamicEntries)
                .joinToString("\n") { "• ${it.comment.ifBlank { it.content.take(40) }}" },
        )
    }

    /**
     * 完整消息序列：[1] system → [2] 历史 → [3] 动态块 → [4] @Depth 注入 → [5] 本轮输入。
     * @Depth 深度按「距末尾消息数」语义插入（含注入后总尾部长度）。
     */
    fun buildMessages(
        assembled: Assembled,
        history: List<UIMessage>,
        currentInput: String?,
    ): List<UIMessage> {
        val list = mutableListOf(assembled.systemMessage)
        list.addAll(history)
        assembled.dynamicBlock?.let {
            list.add(UIMessage(role = Role.SYSTEM, parts = listOf(UIMessagePart.Text(it))))
        }
        // @Depth 注入：depth = 距末尾消息数（以当前列表 + 本轮输入为基准）
        val finalCount = 1.takeIf { !currentInput.isNullOrBlank() } ?: 0
        assembled.depthInjections.forEach { injection ->
            val insertIndex = (list.size + finalCount - injection.depth).coerceIn(1, list.size + finalCount)
            val message = UIMessage(role = injection.role, parts = listOf(UIMessagePart.Text(injection.content)))
            list.add(insertIndex.coerceAtMost(list.size + finalCount), message)
        }
        if (!currentInput.isNullOrBlank()) {
            list.add(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(currentInput))))
        }
        return list
    }

    private fun PromptRole.toRole(): Role = when (this) {
        PromptRole.SYSTEM -> Role.SYSTEM
        PromptRole.USER -> Role.USER
        PromptRole.ASSISTANT -> Role.ASSISTANT
    }
}

/** 默认 RP 系统提示（无角色卡时使用；稳定前缀层，措辞变更需 CHANGELOG）。 */
object DefaultRpPrompt {
    const val SYSTEM: String = """你是一位沉浸式文字角色扮演的叙述者与扮演者。

【叙事要求】
- 以第二人称"你"称呼对话者，用生动的动作、神态、环境描写推进场景；
- 对话与叙述交织，节奏自然，避免一次性输出过长的独白；
- 保持角色一致性与世界一致性；不确定的设定宁可留白，不编造。
"""
}

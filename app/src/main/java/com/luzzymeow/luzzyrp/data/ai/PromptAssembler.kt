package com.luzzymeow.luzzyrp.data.ai

import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.core.model.InjectionPosition
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.SummaryItem
import com.luzzymeow.luzzyrp.core.model.SummaryLevel
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
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
 *       角色卡人格 → NSFW 块 → 世界书常驻条目 → C 级摘要 → 工具说明 → Agentic 协议
 *   [2] …对话历史（append-only，前缀逐字节稳定）…
 *   [3] system：动态上下文块（每轮变动，置于尾部以保护 [1][2] 的缓存）
 *       B 级摘要 → A 级摘要 → 记忆 Top-K → 被动世界书召回 → AT_DEPTH 条目
 *   [4] user：本轮输入
 *
 * 铁律：
 *   - [1] 内字段顺序固定（改动需 CHANGELOG 声明缓存失效）；
 *   - 任何易变内容（时间戳/随机 id/最近轮次）禁止进入 [1]；
 *   - 动态内容只允许出现在 [3]，禁止回写历史消息。
 */
class PromptAssembler(
    private val worldbookRepository: WorldbookRepository,
) {

    data class Assembled(
        /** [1] 稳定前缀系统消息。 */
        val systemMessage: UIMessage,
        /** [3] 动态上下块文本（空则不插入）。 */
        val dynamicBlock: String?,
        /** 被动召回报告（展示用）。 */
        val passiveRecallReport: String,
    )

    /**
     * 组装系统提示与动态块。
     *
     * @param card 绑定角色卡（null = 普通对话）
     * @param cardPersonaPrompt 角色卡人格段（card.personaPrompt()）
     * @param toolRegistry 主动工具表（说明进稳定前缀）
     * @param tagFallback 是否启用标签兜底协议说明
     * @param summariesC 永久摘要
     * @param summariesB 阶段摘要
     * @param summariesA 最近轮摘要
     * @param memories 记忆 Top-K（已检索）
     * @param recall 被动世界书召回结果
     */
    fun assemble(
        card: CharacterCard?,
        toolRegistry: ToolRegistry,
        tagFallback: Boolean,
        summariesC: List<SummaryItem>,
        summariesB: List<SummaryItem>,
        summariesA: List<SummaryItem>,
        memories: List<String>,
        recall: WorldbookRepository.RecallResult,
        historyMessages: List<UIMessage>,
    ): Assembled {
        // ─────────── [1] 稳定前缀 ───────────
        val stable = StringBuilder()

        // 1.1 角色卡人格 / 默认 RP 提示
        if (card != null) {
            stable.append(card.systemPrompt.ifBlank { DefaultRpPrompt.SYSTEM })
            stable.append("\n\n").append(card.personaPrompt())
        } else {
            stable.append(DefaultRpPrompt.SYSTEM)
        }

        // 1.2 NSFW 块（[INVARIANT-NSFW] 原样注入，不审查不改写）
        if (NsfwBlock.BLOCK.isNotBlank()) {
            stable.append("\n\n").append(NsfwBlock.BLOCK)
        }

        // 1.3 世界书常驻条目（BEFORE_CHAR → AFTER_CHAR 顺序）
        val stableEntries = recall.stableEntries
        if (stableEntries.isNotEmpty()) {
            val before = stableEntries.filter { it.position == InjectionPosition.BEFORE_CHAR }
            val after = stableEntries.filter { it.position == InjectionPosition.AFTER_CHAR }
            if (before.isNotEmpty()) {
                stable.append("\n\n【世界设定（先于角色）】\n")
                before.forEach { stable.append("• ").append(it.content.trim()).append("\n") }
            }
            if (after.isNotEmpty()) {
                stable.append("\n【世界设定（角色之后）】\n")
                after.forEach { stable.append("• ").append(it.content.trim()).append("\n") }
            }
        }

        // 1.4 C 级摘要（永久事实）
        if (summariesC.isNotEmpty()) {
            stable.append("\n【既成事实（永久）】\n")
            summariesC.forEach { stable.append("• ").append(it.content.trim()).append("\n") }
        }

        // 1.5 工具说明 + Agentic 协议
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

        return Assembled(
            systemMessage = systemMessage,
            dynamicBlock = dynamic.toString().takeIf { it.isNotBlank() },
            passiveRecallReport = (recall.stableEntries + recall.dynamicEntries)
                .joinToString("\n") { "• ${it.comment.ifBlank { it.content.take(40) }}" },
        )
    }

    /**
     * 完整消息序列：[1] + 历史 + [3] + [4]（AT_DEPTH 语义由 [3] 置于尾部实现）。
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
        if (!currentInput.isNullOrBlank()) {
            list.add(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(currentInput))))
        }
        return list
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

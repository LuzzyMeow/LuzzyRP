package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 角色卡领域模型：SillyTavern Character Card v2/v3 全字段 + LuzzyRP 本地扩展。
 * 导入（PNG tEXt `chara`/`ccv3` / JSON）与导出（PNG 写回 tEXt）均映射到本模型。
 */

/** 卡片来源。 */
@Serializable
enum class CardSource {
    /** 内置默认卡（如「鹿溪」），readonly 保护。 */
    @SerialName("builtin") BUILTIN,

    /** 从 SillyTavern PNG/JSON 导入。 */
    @SerialName("imported") IMPORTED,

    /** 应用内创建。 */
    @SerialName("created") CREATED,
}

/** 卡片绑定聊天背景。 */
@Serializable
data class ChatBackground(
    val path: String? = null,
    /** 背景不透明度（0-100）。 */
    val opacity: Int = 45,
    /** 是否启用模糊（AuroraSurface 引擎以层叠模拟，禁 Modifier.blur）。 */
    val blur: Boolean = false,
)

@Serializable
data class CharacterCard(
    val id: String,
    // —— SillyTavern v2/v3 核心字段（spec: chara_card_v2 / chara_card_v3） ——
    val name: String,
    /** 角色设定（description）。 */
    val description: String = "",
    /** 性格（personality）。 */
    val personality: String = "",
    /** 场景（scenario）。 */
    val scenario: String = "",
    /** 第一条消息（first_mes），同时作为会话开场白。 */
    val firstMes: String = "",
    /** 备选开场白（alt_greetings）。 */
    val altGreetings: List<String> = emptyList(),
    /** 系统提示覆盖（system_prompt；空则使用全局默认 RP 系统提示）。 */
    val systemPrompt: String = "",
    /** 历史后注入指令（post_history_instructions）。 */
    val postHistoryInstructions: String = "",
    /** 创作者备注（creator_notes，仅展示不进提示词）。 */
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    /** 对话示例（mes_example）。 */
    val exampleDialogs: String = "",
    // —— 元数据 ——
    val stSpecVersion: String? = null,
    /** 原始卡片 JSON（导出 PNG 时原样写回，保证与 ST 生态双向兼容）。 */
    val stRawJson: String? = null,
    val avatarPath: String? = null,
    val source: CardSource = CardSource.CREATED,
    /** 内置卡只读保护。 */
    val readonly: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    // —— LuzzyRP 生态扩展 ——
    /** 绑定世界书（卡片世界书）。 */
    val worldbookId: String? = null,
    /** 绑定正则脚本。 */
    val regexIds: List<String> = emptyList(),
    /** 绑定 UI 模板。 */
    val uiTemplateId: String? = null,
    val chatBackground: ChatBackground = ChatBackground(),

    // —— 系统提示组装时的分节注入顺序（KV 稳定前缀内的固定次序） ——
) {
    /** 组装角色卡人格段（进入系统提示稳定前缀；字段次序固定，保 KV 命中）。 */
    fun personaPrompt(): String = buildString {
        if (description.isNotBlank()) append("【角色设定】\n").append(description.trim()).append("\n\n")
        if (personality.isNotBlank()) append("【性格】\n").append(personality.trim()).append("\n\n")
        if (scenario.isNotBlank()) append("【场景】\n").append(scenario.trim()).append("\n\n")
        if (exampleDialogs.isNotBlank()) append("【对话示例】\n").append(exampleDialogs.trim()).append("\n\n")
    }.trim()
}

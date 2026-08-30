package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 世界书（Lorebook）领域模型：三策略召回（常驻 / 关键词 / 向量）。
 * 一个条目可同时声明多种策略（strategy 为位标志集合），召回器按策略分别处理。
 */

/** 召回策略位标志。 */
@Serializable
enum class RecallStrategy {
    /** 常驻：每轮无条件注入。 */
    @SerialName("constant") CONSTANT,

    /** 关键词：扫描最近对话（含递归扫描已注入条目），命中 keys 后按概率注入。 */
    @SerialName("keyword") KEYWORD,

    /** 向量：最近用户输入/AI 正文切片的嵌入与条目嵌入做 Top-K 相似度召回。 */
    @SerialName("vector") VECTOR,
}

/** 注入位置（与 SillyTavern position 语义对齐）。 */
@Serializable
enum class InjectionPosition {
    /** 角色设定之前（系统提示稳定前缀内）。 */
    @SerialName("before_char") BEFORE_CHAR,

    /** 角色设定之后（系统提示稳定前缀内）。 */
    @SerialName("after_char") AFTER_CHAR,

    /** 按深度插入对话尾部（动态尾段，depth 指定距末尾消息数）。 */
    @SerialName("at_depth") AT_DEPTH,
}

@Serializable
data class WorldbookEntry(
    val id: String,
    val worldbookId: String,
    /** 条目标题（ST 的 comment 字段）。 */
    val comment: String = "",
    /** 条目正文（注入内容）。 */
    val content: String,
    /** 启用状态。 */
    val enabled: Boolean = true,
    /** 本条目参与的召回策略集合。 */
    val strategies: List<RecallStrategy> = listOf(RecallStrategy.KEYWORD),
    // —— 关键词策略参数 ——
    /** 主关键词（任一命中即触发）。 */
    val keys: List<String> = emptyList(),
    /** 次级关键词（需与主关键词同时命中，optional：空则忽略）。 */
    val keysSecondary: List<String> = emptyList(),
    /** 关键词大小写敏感。 */
    val caseSensitive: Boolean = false,
    /** content 内含正则（keys 按正则解释）。 */
    val useRegex: Boolean = false,
    /** 递归扫描：本条目注入后，其 content 可触发其他关键词条目（最多再扫 1 层）。 */
    val recursion: Boolean = false,
    /** 注入概率（0-100，每次组装时 roll）。 */
    val probability: Int = 100,
    // —— 注入参数 ——
    val position: InjectionPosition = InjectionPosition.BEFORE_CHAR,
    /** at_depth 时的深度（距末尾消息数）。 */
    val depth: Int = 4,
    /** 同位置内排序（小者先注入）。 */
    val order: Int = 100,
    // —— 向量策略参数 ——
    /** 条目已生成嵌入（写 worldbook_vec）。 */
    val vectorIndexed: Boolean = false,
    /** 向量召回相似度阈值（余弦，0-1）。 */
    val minSimilarity: Float = 0.55f,
)

@Serializable
data class Worldbook(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** 绑定角色卡（卡片世界书）；null = 全局世界书。 */
    val cardId: String? = null,
    val entries: List<WorldbookEntry> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

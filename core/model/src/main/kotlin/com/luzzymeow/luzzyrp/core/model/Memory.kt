package com.luzzymeow.luzzyrp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ACE 长期记忆与三级摘要领域模型。
 *
 * ACE 三步循环（Execute → Reflect → Update）：
 *   - Execute：生成时注入 Top-K 记忆 + 提供检索工具；
 *   - Reflect：回复后由模型反思本轮记忆的有用/有害/无关，更新计数；
 *   - Update：从本轮对话提取新事实，嵌入去重（余弦 ≥0.92 合并）后入库；
 *   - 淘汰：评分（helpful − harmful）与失活时长加权，容量超限时淘汰最弱条目。
 */

/** 记忆条目。 */
@Serializable
data class MemoryItem(
    val id: String,
    /** 记忆内容（一句话事实/偏好/设定）。 */
    val content: String,
    /** 分类（关系/事件/偏好/设定/其他）。 */
    val category: String = "其他",
    /** Reflect 阶段累计：有帮助次数。 */
    val helpful: Int = 0,
    /** Reflect 阶段累计：有冲突/有害次数。 */
    val harmful: Int = 0,
    /** Reflect 阶段累计：无关次数。 */
    val neutral: Int = 0,
    /** 启用状态（停用后不参与检索与注入）。 */
    val active: Boolean = true,
    /** 来源会话（可追溯）。 */
    val sourceConversationId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** 最近一次被检索命中的时间（淘汰算法的失活时长依据）。 */
    val lastRetrievedAt: Long = 0,
) {
    /** 综合评分（淘汰排序依据：大者优先保留）。 */
    fun score(nowMs: Long): Double {
        val utility = (helpful - harmful).toDouble()
        val staleDays = if (lastRetrievedAt <= 0) 30.0
        else (nowMs - lastRetrievedAt).coerceAtLeast(0) / (24 * 3600 * 1000.0)
        // 失活越久分越低；轻微惩罚中性漂移
        return utility - staleDays * 0.1 - neutral * 0.01
    }
}

/** 三级摘要条目。 */
@Serializable
data class SummaryItem(
    val id: String,
    val conversationId: String,
    /** 摘要级别。 */
    val level: SummaryLevel,
    /** 生成时的会话轮次。 */
    val roundIndex: Int,
    /** 摘要正文。 */
    val content: String,
    val createdAt: Long = 0,
)

/** 摘要级别（HARD_REQUIREMENTS：A 每轮 ≤50 / B 每 10 轮 ≤10 / C 每 50 轮永久）。 */
@Serializable
enum class SummaryLevel {
    /** A 级：每轮生成，≤50 字，五要素盲续（时间/地点/人物/事件/结果）。 */
    @SerialName("a") A,

    /** B 级：每 10 轮由 A 级合并，≤10 条。 */
    @SerialName("b") B,

    /** C 级：每 50 轮固化，永久保留。 */
    @SerialName("c") C,
}

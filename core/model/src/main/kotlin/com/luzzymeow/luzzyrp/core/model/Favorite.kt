package com.luzzymeow.luzzyrp.core.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 收藏类型。 */
@Serializable
enum class FavoriteType {
    /** 消息收藏。 */
    @SerialName("message") MESSAGE,

    /** 会话收藏。 */
    @SerialName("conversation") CONVERSATION,
}

/** 收藏条目（消息/会话两类）。 */
@Serializable
data class Favorite(
    val id: String,
    val type: FavoriteType,
    val conversationId: String,
    /** 收藏消息所在节点（conversation 级收藏为 null）。 */
    val nodeId: String? = null,
    val messageId: String? = null,
    /** 列表展示摘要。 */
    val excerpt: String = "",
    /** 收藏时的角色卡名（展示用）。 */
    val cardName: String? = null,
    val createdAt: Instant? = null,
)

/** UI 模板：会话气泡的轻量渲染模板（{{placeholder}} 注册表 + 条件段）。 */
@Serializable
data class UITemplate(
    val id: String,
    val name: String,
    /** 模板正文（Pebble 风格变量 + {% if %} 条件段的轻量子集）。 */
    val template: String,
    /** 绑定角色卡（null = 全局）。 */
    val cardId: String? = null,
    val enabled: Boolean = true,
)

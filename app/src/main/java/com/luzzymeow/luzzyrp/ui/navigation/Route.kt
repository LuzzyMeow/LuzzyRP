package com.luzzymeow.luzzyrp.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * LuzzyRP 路由表（Navigation3 `entry<T>` 模式，typed NavKey）。
 * 转场动效统一走 MotionTokens（进入 300ms / 退出 195ms）。
 */
sealed interface Route : NavKey {

    /** 首页：会话列表 + 抽屉。 */
    @Serializable
    data object Home : Route

    /** 聊天页。 */
    @Serializable
    data class Chat(val conversationId: String) : Route

    /** 角色卡库。 */
    @Serializable
    data object Characters : Route

    /** 角色卡详情/编辑。 */
    @Serializable
    data class CharacterDetail(val cardId: String) : Route

    /** 世界书管理。 */
    @Serializable
    data object Worldbooks : Route

    /** 记忆管理。 */
    @Serializable
    data object Memory : Route

    /** 收藏。 */
    @Serializable
    data object Favorites : Route

    /** 历史会话（归档）。 */
    @Serializable
    data object History : Route

    /** 设置族。 */
    @Serializable
    data object Settings : Route

    @Serializable
    data object SettingsProviders : Route

    @Serializable
    data class SettingsProviderDetail(val providerId: String) : Route

    @Serializable
    data object SettingsGeneration : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsAbout : Route
}

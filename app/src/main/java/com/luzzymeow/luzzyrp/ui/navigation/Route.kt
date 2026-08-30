package com.luzzymeow.luzzyrp.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * LuzzyRP 路由表（Navigation3 `entry<T>` 模式，typed NavKey）。
 * 转场动效统一走 MotionTokens（进入 300ms / 退出 195ms）。
 *
 * v0.2.0 菜单重构（4.1-4.7）：移除收藏/历史/搜索/世界书/记忆独立入口；
 * 历史+搜索并入聊天页，世界书并入角色卡，记忆并入设置，新增 预设/用户档案。
 */
sealed interface Route : NavKey {

    /** 聊天首页：会话列表（原「新对话」）。 */
    @Serializable
    data object Home : Route

    /** 聊天页。 */
    @Serializable
    data class Chat(val conversationId: String) : Route

    /** 角色卡（原「角色卡库」）。 */
    @Serializable
    data object Characters : Route

    /** 角色卡详情/编辑。 */
    @Serializable
    data class CharacterDetail(val cardId: String) : Route

    /** 角色卡的世界书二级页（4.6/5.4）。 */
    @Serializable
    data class Worldbook(val cardId: String) : Route

    /** 提示词预设列表（7.2）。 */
    @Serializable
    data object Presets : Route

    /** 预设编辑。 */
    @Serializable
    data class PresetDetail(val presetId: String) : Route

    /** 用户档案（7.1）。 */
    @Serializable
    data object Profile : Route

    /** 记忆设置（长期记忆 + 三级摘要，6.5）。 */
    @Serializable
    data object MemorySettings : Route

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

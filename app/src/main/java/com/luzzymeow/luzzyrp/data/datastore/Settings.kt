package com.luzzymeow.luzzyrp.data.datastore

import com.luzzymeow.luzzyrp.core.model.ContextConfig
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.UserProfile
import com.luzzymeow.luzzyrp.ui.theme.ThemeMode
import kotlinx.serialization.Serializable

/**
 * 全局设置（单一 JSON blob 存入 DataStore Preferences，参考 rikkahub SettingsStore 模式）。
 *
 * [INVARIANT-KV] Settings 中的生成参数仅影响请求组装；修改设置不会改变
 * 既有历史的序列化形态，KV 前缀稳定性不受影响。
 */
@Serializable
data class Settings(
    // —— 供应商 ——
    val providers: List<ProviderSetting> = emptyList(),
    val currentProviderId: String = "",
    val currentModelId: String = "",
    /** 嵌入供应商/模型（记忆与世界书向量召回用；空 = 向量功能禁用）。 */
    val embeddingProviderId: String = "",
    val embeddingModelId: String = "",

    // —— 生成参数 ——
    val temperature: Double? = 0.8,
    val topP: Double? = null,
    val maxTokens: Long? = null,
    /** 思考深度：null=模型默认；off=关闭；其余为 effort（low/medium/high/max）。 */
    val thinkingEffort: String? = null,
    val thinkingEnabled: Boolean? = null,
    /** 思考深度统一档位（v0.2.0：ThinkingDepth 序列名；按模型 id 自适配字段）。 */
    val thinkingDepth: String? = null,

    // —— 上下文 ——
    val contextConfig: ContextConfig = ContextConfig(),

    // —— 记忆（ACE） ——
    val memoryEnabled: Boolean = true,
    /** 记忆容量上限（超过触发评分淘汰）。 */
    val memoryCapacity: Int = 200,
    /** 嵌入去重余弦阈值。 */
    val memoryDedupThreshold: Float = 0.92f,
    /** 检索 Top-K。 */
    val memoryTopK: Int = 6,

    // —— 三级摘要 ——
    val summaryEnabled: Boolean = true,

    // —— 外观 ——
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 引用高亮（"…" 着色）。 */
    val quoteHighlightEnabled: Boolean = true,
    /** 流式打字期间自动滚动跟随。 */
    val autoScroll: Boolean = true,

    // —— 角色 ——
    /** 默认角色卡 id（新会话使用；空 = 无角色普通对话）。 */
    val defaultCardId: String = "",
    /** 用户在 RP 中的自称。 */
    val personaName: String = "我",

    // —— v0.2.0 新增 ——
    /** 启动的提示词预设 id（注入 system；空 = 不注入）。 */
    val activePresetId: String = "",
    /** 用户档案（7.1：头像/名字/身份，注入 system 稳定前缀）。 */
    val userProfile: UserProfile = UserProfile(),
    /** 上次打开的会话（3：启动直达；首次为空 → 默认鹿溪）。 */
    val lastConversationId: String = "",
    /** 是否记录详细日志（6.6；默认开）。 */
    val loggingEnabled: Boolean = true,
    /** 对话注入的世界书（会话级已覆盖；此为全局兜底，可空）。 */
    val globalWorldbookIds: List<String> = emptyList(),
)

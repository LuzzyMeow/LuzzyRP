package com.luzzymeow.luzzyrp.data.datastore

import com.luzzymeow.luzzyrp.core.model.ContextConfig
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
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
)

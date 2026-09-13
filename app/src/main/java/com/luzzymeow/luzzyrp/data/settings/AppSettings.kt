package com.luzzymeow.luzzyrp.data.settings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 亮暗模式。`null`（= [SettingsStore] 里没存值）表示跟随系统。 */
enum class ThemeMode { System, Light, Dark }

/**
 * 应用级设置（P4-B-3.3）。
 *
 * **为什么只有这两项**：主题模式是 DESIGN-compose 已规定、且 KDoc 明确「持久化在 P4」的东西；
 * 旧数据里还有 `fontSize`（12–20），但**用户可调字号属于字体排版**——按硬性规定 9 要单独走设计流程
 * （现状只有「系统 150% 字号不破版」这条鲁棒性要求，没有用户可调字号的规格），故本轮只把它读出来
 * 记进报告、不擅自接成可用设置。
 */
data class AppSettings(
    /** null = 跟随系统（默认）。用户手动切过亮/暗就记成显式值。 */
    val themeMode: ThemeMode? = null,
)

/**
 * 旧数据 `settings` 里**能搬过来给新应用用**的部分。
 *
 * 迁移器已经把整个 legacy settings 原样存进了 `kv["settings"]`，但「存下来」不等于「能用」——
 * 新应用要的是 `baseUrl + apiKey + model` 三个值，而旧结构把它们分散在
 * `apiProviderId` / `apiProviderKeys` / `apiProviderOverrides` / `apiProviders` / `apiUrl` 五处。
 * 这个读数器就是那层翻译；**纯函数**，所以能直接拿真夹具断言。
 */
data class LegacySettings(
    val themeMode: ThemeMode? = null,
    val providerBaseUrl: String? = null,
    val providerApiKey: String? = null,
    val providerModel: String? = null,
    /** 旧版字号（仅记录，见 [AppSettings] 的说明）。 */
    val fontSize: Int? = null,
    val notes: List<String> = emptyList(),
) {
    val hasProvider: Boolean
        get() = !providerBaseUrl.isNullOrBlank() && !providerApiKey.isNullOrBlank() && !providerModel.isNullOrBlank()
}

object LegacySettingsReader {

    private const val REF_SEPARATOR = "::"

    fun read(blob: JsonElement?): LegacySettings {
        val obj = blob as? JsonObject ?: return LegacySettings(notes = listOf("没有旧设置可读"))
        val notes = mutableListOf<String>()

        val themeMode = when (val raw = obj.string("themeMode")?.lowercase()) {
            "dark" -> ThemeMode.Dark
            "light" -> ThemeMode.Light
            null, "" -> null
            else -> {
                notes += "themeMode 值无法识别（$raw），按跟随系统处理"
                null
            }
        }

        val fontSize = obj.intOf("fontSize")?.takeIf { it in 8..40 }

        // 模型引用形如 `providerId::bareId`；旧数据里也可能只写裸 id
        val modelRef = obj.string("model").orEmpty()
        val refProvider = modelRef.substringBefore(REF_SEPARATOR, "").takeIf { modelRef.contains(REF_SEPARATOR) }
        val providerId = refProvider?.takeIf { it.isNotBlank() }
            ?: obj.string("apiProviderId")?.takeIf { it.isNotBlank() }
        val bareModel = if (modelRef.contains(REF_SEPARATOR)) modelRef.substringAfter(REF_SEPARATOR) else modelRef

        val baseUrl = resolveBaseUrl(obj, providerId, notes)
        val apiKey = resolveApiKey(obj, providerId, notes)

        if (providerId.isNullOrBlank()) notes += "旧设置里没有供应商 id，模型配置无法搬过来"
        if (bareModel.isBlank()) notes += "旧设置里没有模型 id"

        return LegacySettings(
            themeMode = themeMode,
            providerBaseUrl = baseUrl,
            providerApiKey = apiKey,
            providerModel = bareModel.takeIf { it.isNotBlank() },
            fontSize = fontSize,
            notes = notes,
        )
    }

    /**
     * 供应商 URL 的优先级（旧结构把这一个值散在四处，顺序不能乱）：
     * 用户编辑过的 override → 用户自定义供应商列表 → 旧版「当前供应商 URL」缓存。
     */
    private fun resolveBaseUrl(obj: JsonObject, providerId: String?, notes: MutableList<String>): String? {
        if (providerId.isNullOrBlank()) return null
        val overrides = obj["apiProviderOverrides"] as? JsonObject
        val fromOverride = (overrides?.get(providerId) as? JsonObject)?.string("apiUrl")
        if (!fromOverride.isNullOrBlank()) return fromOverride

        val fromUserList = (obj["apiProviders"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("id") == providerId }
            ?.string("apiUrl")
        if (!fromUserList.isNullOrBlank()) return fromUserList

        // 回落到旧版缓存字段：只有当它就是当前供应商时才可信（否则会把 A 商的 URL 配到 B 商头上）
        val currentId = obj.string("apiProviderId")
        if (currentId == providerId) {
            val cached = obj.string("apiUrl")
            if (!cached.isNullOrBlank()) return cached
        }
        notes += "找不到供应商 $providerId 的 API 地址（override / 自定义列表 / 缓存里都没有）"
        return null
    }

    private fun resolveApiKey(obj: JsonObject, providerId: String?, notes: MutableList<String>): String? {
        if (providerId.isNullOrBlank()) return null
        val keys = obj["apiProviderKeys"] as? JsonObject
        val byId = keys?.get(providerId)?.stringOrNull()
        if (!byId.isNullOrBlank()) return byId
        if (obj.string("apiProviderId") == providerId) {
            val legacy = obj.string("apiKey")
            if (!legacy.isNullOrBlank()) return legacy
        }
        notes += "找不到供应商 $providerId 的密钥"
        return null
    }
}

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.string(field: String): String? = this[field]?.stringOrNull()

private fun JsonObject.intOf(field: String): Int? =
    (this[field] as? JsonPrimitive)?.content?.toIntOrNull()

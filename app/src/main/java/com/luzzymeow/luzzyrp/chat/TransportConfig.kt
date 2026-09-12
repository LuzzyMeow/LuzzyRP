package com.luzzymeow.luzzyrp.chat

import android.content.Context
import androidx.core.content.edit

/**
 * 供应商传输配置（OpenAI 兼容协议）。
 *
 * **密钥纪律**：apiKey 只保存在设备本地（SharedPreferences），不入库、不进构建产物、
 * 不写日志；界面展示一律经 [maskedKey] 打码。
 *
 * P2 用 SharedPreferences 持久化；P4 数据层建设时迁 DataStore（届时本类改为 DataStore 读取层）。
 */
data class TransportConfig(
    /** 供应商 Base URL（可含 `/v1`），如 `https://api.deepseek.com`。 */
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double? = null,
    val maxTokens: Int = DefaultMaxTokens,
) {
    /** 是否已可发起真实请求。 */
    val configured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /**
     * 聊天补全端点。
     *
     * 与 WebView 版同语义：用户填的 Base URL 直接可用（JS 侧负责拼路径）；
     * 原生侧在此补 `/chat/completions`——已含路径或已含 `/v1` 的两种写法都兼容。
     */
    fun chatEndpoint(): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        if (base.endsWith("/chat/completions")) return base
        return "$base/chat/completions"
    }

    /** 打码展示（界面/日志用；永不回显完整密钥）。 */
    fun maskedKey(): String = maskKey(apiKey)

    companion object {
        const val DefaultMaxTokens: Int = 1200

        /** 打码：保留首 4 位 + 长度，便于用户确认填对了哪一把。 */
        fun maskKey(key: String): String {
            if (key.isBlank()) return "（未填写）"
            val head = key.take(4)
            return "$head…（共 ${key.length} 字符）"
        }
    }
}

/** 配置读写（设备本地；P4 迁 DataStore）。 */
class TransportStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TransportConfig = TransportConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
    )

    fun save(config: TransportConfig) = prefs.edit {
        putString(KEY_BASE_URL, config.baseUrl)
        putString(KEY_API_KEY, config.apiKey)
        putString(KEY_MODEL, config.model)
    }

    private companion object {
        const val PREFS_NAME = "luzzy_transport"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
    }
}

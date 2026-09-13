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
    /**
     * 是否向模型暴露工具（当前仅 `world_info_lookup`）。
     *
     * **真实影响请求体**：关闭后 `tools` 字段不再发送，模型无法请求工具，
     * 思考时间线上也就不会出现工具节点（不是把 UI 藏起来骗人）。
     */
    val toolsEnabled: Boolean = true,
    /**
     * 模型的**上下文窗口**（tokens）——压缩（B5）唯一的门槛输入。
     *
     * 取默认值时是**保守值**：宁可早压缩（多付一次摘要调用），也不要撞供应商的溢出报错——
     * 溢出的表现是「这一轮直接失败」，用户看到的是报错而不是回复。
     * 填对了只影响压缩时机；**填 0 或负数 = 关闭自动压缩**（不压缩，让供应商直接报错）。
     *
     * 天花板（如实登记）：我们**不猜**各家模型的窗口大小（同一家的不同版本也不同，
     * 猜错了是静默的错误行为），所以默认值只是一个安全的floor；用户可在供应商配置里填真实值。
     */
    val contextWindow: Int = DefaultContextWindow,
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

        /**
         * 默认上下文窗口（tokens）。
         *
         * 32,768 是「大多数主流模型都不会低于它」的安全floor：压缩线落在 ~26K tokens，
         * 对 64K/128K 的模型只是提前一点，对 32K 的模型正好够用。
         * 用户填了真实值就按真实值走（见 [TransportConfig.contextWindow]）。
         */
        const val DefaultContextWindow: Int = 32_768

        /**
         * 解析用户输入的上下文窗口（纯函数，可单测）。
         *
         * 返回 **null = 输入非法**：界面据此把错误贴在该字段上并拦住保存。
         * 刻意**不静默回退成默认值**——那正是本版刚修掉的那类缺陷（用户以为自己填的生效了，
         * 实际被悄悄换掉）；也不钳值，因为「我填了 100000 却变成 0」同样是静默改写用户意图。
         *
         * 宽容之处：千分位与空白一律忽略（用户从别处复制 `65,536` 是常见动作）。
         */
        fun parseContextWindow(text: String): Int? {
            val cleaned = text.filterNot { it.isWhitespace() || it == ',' || it == '，' || it == '_' }
            if (cleaned.isEmpty()) return null
            val value = cleaned.toIntOrNull() ?: return null
            return value.takeIf { it >= 0 }
        }

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
        // [修 2026-09-13] temperature 与 maxTokens 此前**没有持久化**：
        // 字段在数据类里、也能被请求用到，但 load/save 都没读写它们 →
        // 用户改完温度重启就回到默认值。属静默丢配置（DSH 说的 "silent per-call drift"）。
        // `getFloat`/`getInt` 的默认值取数据类默认值：没存过时行为与从前完全一致。
        temperature = prefs.getFloat(KEY_TEMPERATURE, TEMPERATURE_UNSET)
            .takeIf { it != TEMPERATURE_UNSET }?.toDouble(),
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, TransportConfig.DefaultMaxTokens),
        toolsEnabled = prefs.getBoolean(KEY_TOOLS, true),
        contextWindow = prefs.getInt(KEY_CONTEXT_WINDOW, TransportConfig.DefaultContextWindow),
    )

    fun save(config: TransportConfig) = prefs.edit {
        putString(KEY_BASE_URL, config.baseUrl)
        putString(KEY_API_KEY, config.apiKey)
        putString(KEY_MODEL, config.model)
        // SharedPreferences 没有 putDouble：用 Float 存（温度的有效位数远小于 Float 精度）
        if (config.temperature == null) remove(KEY_TEMPERATURE)
        else putFloat(KEY_TEMPERATURE, config.temperature.toFloat())
        putInt(KEY_MAX_TOKENS, config.maxTokens)
        putBoolean(KEY_TOOLS, config.toolsEnabled)
        putInt(KEY_CONTEXT_WINDOW, config.contextWindow)
    }

    private companion object {
        const val PREFS_NAME = "luzzy_transport"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_TOOLS = "tools_enabled"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_CONTEXT_WINDOW = "context_window"

        /** 哨兵值：区分「没存过」与「存了 0.0」（0.0 是合法温度）。 */
        const val TEMPERATURE_UNSET = Float.MIN_VALUE
    }
}

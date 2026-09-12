package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.JsonLenient
import com.luzzymeow.luzzyrp.chat.llm.SseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 供应商模型目录（真实 `GET {base}/models`）。
 *
 * 用途：输入岛的模型 chip 点开后列出**该供应商真实提供的**模型，选中即写回配置并影响后续请求
 * ——不是写死一份模型名清单。上游 WebView 版同语义（`/models` 拉取 + 手动模型合并）。
 *
 * 解析是纯函数（[parseModelIds]），可单测；只有 [fetch] 触网。
 */
object ModelCatalog {

    /**
     * 解析 `/models` 响应体 → 模型 id 列表（保序、去重、剔除空串）。
     *
     * 宽容处理三种真实形态：
     * ① OpenAI 标准 `{"data":[{"id":"deepseek-chat"}, …]}`
     * ② 部分中转 `{"models":[{"id":…} | "name"…]}`
     * ③ 裸数组 `[{"id":…}]`
     * 非 JSON / 形状不符 → 空列表（由调用方如实展示「拉取失败」）。
     */
    fun parseModelIds(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { JsonLenient.json.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()
        val candidates: List<JsonObject> = when (root) {
            is JsonObject -> {
                val arr = root["data"] as? JsonArray ?: root["models"] as? JsonArray ?: return emptyList()
                arr.mapNotNull { it as? JsonObject }
            }

            is JsonArray -> root.mapNotNull { it as? JsonObject }
            else -> return emptyList()
        }
        return candidates.asSequence()
            .mapNotNull { obj ->
                (obj["id"] ?: obj["name"] ?: obj["model"])?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /** `/models` 端点（从聊天端点回退：去掉 `/chat/completions` 再拼）。 */
    fun modelsEndpoint(config: TransportConfig): String {
        val base = config.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        return if (base.isEmpty()) "" else "$base/models"
    }

    /**
     * 真实拉取。失败**不抛异常**，以 [Result] 交给 UI 如实展示（与传输层同一纪律）。
     * 密钥只进请求头，不进任何日志与错误文本。
     */
    suspend fun fetch(
        config: TransportConfig,
        client: OkHttpClient = SseClient.defaultClient(),
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val url = modelsEndpoint(config)
        if (url.isEmpty()) return@withContext Result.failure(IllegalStateException("未配置 Base URL"))
        runCatching {
            val builder = Request.Builder().url(url).get()
            if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                parseModelIds(body)
            }
        }
    }
}

package com.luzzymeow.luzzyrp.assistant.domain.memory

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 嵌入客户端（PLAN §7.2）：复用 Web 端供应商的 **OpenAI 兼容 `POST /embeddings`**。
 *
 * 约定：
 * - 模型引用 `providerId::bareId`（由调用方解析后传 [model]）；
 * - 批次上限 32 条/请求，失败重试 1 次；
 * - **密钥只进 Authorization 头，不落日志**（硬性要求：日志脱敏）；
 * - 结果按输入顺序返回；条数不匹配视为失败。
 */
class EmbeddingClient(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /** 单条嵌入（内部走批量接口）。失败抛 [EmbeddingException]。 */
    suspend fun embedOne(text: String, baseUrl: String, apiKey: String, model: String): FloatArray {
        val all = embed(listOf(text), baseUrl, apiKey, model)
        return all.firstOrNull() ?: throw EmbeddingException("嵌入接口未返回向量")
    }

    /** 批量嵌入（自动分批，每批 ≤ [BATCH_SIZE]）。 */
    suspend fun embed(
        texts: List<String>,
        baseUrl: String,
        apiKey: String,
        model: String,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val out = ArrayList<FloatArray>(texts.size)
        texts.chunked(BATCH_SIZE).forEach { batch ->
            out += embedBatch(batch, baseUrl, apiKey, model)
        }
        return out
    }

    private suspend fun embedBatch(
        texts: List<String>,
        baseUrl: String,
        apiKey: String,
        model: String,
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        val endpoint = baseUrl.trimEnd('/') + "/embeddings"
        val payload = buildJsonObject {
            put("model", model)
            put("input", buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }.toString()

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        // 不回显 body（可能含敏感信息），只给状态码
                        throw EmbeddingException("嵌入接口 HTTP ${response.code}")
                    }
                    return@withContext parse(body, texts.size)
                }
            } catch (e: EmbeddingException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt == 0) Thread.sleep(400)
            }
        }
        throw EmbeddingException("嵌入请求失败：${lastError?.message ?: "未知错误"}")
    }

    /** 解析 `data[].embedding`（按 index 排序；缺 index 时按返回顺序）。 */
    internal fun parse(body: String, expected: Int): List<FloatArray> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw EmbeddingException("嵌入响应不是合法 JSON")
        val data = root["data"] as? JsonArray ?: throw EmbeddingException("嵌入响应缺少 data 字段")
        val items = data.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val vector = (obj["embedding"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() }
                ?.toFloatArray()
                ?: return@mapNotNull null
            index to vector
        }.sortedBy { it.first }.map { it.second }
        if (items.size != expected) {
            throw EmbeddingException("嵌入条数不匹配：期望 $expected，实际 ${items.size}")
        }
        return items
    }

    companion object {
        const val BATCH_SIZE = 32
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

/** 嵌入调用失败（网络 / 额度 / 协议）。上层据此**降级为全文模式**（PLAN §7.1）。 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)

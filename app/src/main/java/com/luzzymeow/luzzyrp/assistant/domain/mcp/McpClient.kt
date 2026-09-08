package com.luzzymeow.luzzyrp.assistant.domain.mcp

import com.luzzymeow.luzzyrp.assistant.domain.llm.JsonLenient
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * MCP 客户端（PLAN §9.4）：JSON-RPC 2.0 over **Streamable HTTP / 旧式 SSE**。
 *
 * 流程：`initialize` → `notifications/initialized` → `tools/list` → `tools/call`。
 * - 响应既可能是 `application/json` 也可能是 `text/event-stream`（Streamable HTTP），两者都解析；
 * - 超时：连接 15s / 调用 60s（可配）；
 * - **stdio 不在此实现**（P4 依赖沙盒，见 PLAN §9.3）；
 * - 错误统一转 [McpException]，消息**不含请求头**（避免泄漏 Authorization）。
 */
class McpClient(
    private val client: OkHttpClient = defaultClient(),
    private val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    private val callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
) {

    private val ids = AtomicLong(1)

    /** 握手：返回服务端自报的 serverInfo（失败抛 [McpException]）。 */
    suspend fun initialize(config: McpServerConfig): String {
        val result = rpc(
            config,
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "LuzzyRP")
                        put("version", "1.5.0")
                    },
                )
                put("capabilities", buildJsonObject { })
            },
        )
        // 通知：客户端已就绪（无响应）
        runCatching { notify(config, "notifications/initialized") }
        val serverInfo = result["serverInfo"] as? JsonObject
        val name = serverInfo?.get("name")?.jsonPrimitive?.content ?: "unknown"
        val version = serverInfo?.get("version")?.jsonPrimitive?.content ?: "?"
        return "$name $version"
    }

    /** 列出工具（`tools/list`）。 */
    suspend fun listTools(config: McpServerConfig): List<McpToolSpec> {
        val result = rpc(config, method = "tools/list", params = JsonObject(emptyMap()))
        val tools = result["tools"] as? JsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            McpToolSpec(
                name = name,
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                inputSchemaJson = (obj["inputSchema"] as? JsonObject)?.toString() ?: "{}",
            )
        }
    }

    /** 调用工具（`tools/call`），把 `content[]` 归一为文本 + 图片 URL。 */
    suspend fun callTool(config: McpServerConfig, toolName: String, args: JsonObject): McpCallResult {
        val result = rpc(
            config,
            method = "tools/call",
            params = buildJsonObject {
                put("name", toolName)
                put("arguments", args)
            },
        )
        val isError = result["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val content = result["content"] as? JsonArray
        if (content == null) {
            return McpCallResult(text = result.toString(), isError = isError)
        }
        val textParts = mutableListOf<String>()
        val images = mutableListOf<String>()
        content.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> textParts += obj["text"]?.jsonPrimitive?.content.orEmpty()
                "image" -> {
                    val url = obj["url"]?.jsonPrimitive?.content
                        ?: obj["data"]?.jsonPrimitive?.content?.let { "data:" + (obj["mimeType"]?.jsonPrimitive?.content ?: "image/png") + ";base64," + it }
                    if (url != null) images += url
                }
                "resource" -> {
                    val resource = obj["resource"] as? JsonObject
                    textParts += resource?.get("text")?.jsonPrimitive?.content
                        ?: resource?.get("uri")?.jsonPrimitive?.content.orEmpty()
                }
                else -> textParts += obj.toString()
            }
        }
        return McpCallResult(
            text = textParts.joinToString("\n").ifBlank { "（无文本内容）" },
            isError = isError,
            imageUrls = images,
        )
    }

    /** 可达性探测（导入预览用；不抛异常）。 */
    suspend fun probe(config: McpServerConfig): Pair<Boolean, String?> = runCatching { initialize(config) }
        .fold({ true to it }, { false to (it.message ?: it.javaClass.simpleName) })

    // ------------------------------------------------------------------

    private suspend fun rpc(config: McpServerConfig, method: String, params: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            requireHttp(config)
            val id = ids.getAndIncrement()
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            }.toString()
            val responseText = post(config, payload)
            val envelope = parseEnvelope(responseText, id)
            val error = envelope["error"] as? JsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.content ?: "未知错误"
                val code = error["code"]?.jsonPrimitive?.content ?: "?"
                throw McpException("MCP 调用失败（$method, code=$code）：$message")
            }
            envelope["result"] as? JsonObject
                ?: throw McpException("MCP 响应缺少 result（$method）")
        }

    private suspend fun notify(config: McpServerConfig, method: String) = withContext(Dispatchers.IO) {
        requireHttp(config)
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }.toString()
        runCatching { post(config, payload) }
        Unit
    }

    private fun requireHttp(config: McpServerConfig) {
        if (!config.isHttp) throw McpException("当前仅支持 HTTP / SSE 传输（stdio 见 PLAN §9.3）")
        if (config.url.isNullOrBlank()) throw McpException("服务器 ${config.name} 未配置 URL")
    }

    private fun post(config: McpServerConfig, payload: String): String {
        val builder = Request.Builder()
            .url(config.url!!)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        config.headers.forEach { (key, value) -> builder.header(key, value) }
        val request = builder.post(payload.toRequestBody(JSON_MEDIA)).build()
        return try {
            client.newBuilder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        // 不回显 body（可能含敏感信息），只给状态码
                        throw McpException("MCP HTTP ${response.code}")
                    }
                    body
                }
        } catch (e: McpException) {
            throw e
        } catch (e: IOException) {
            throw McpException("MCP 网络错误：" + (e.message ?: e.javaClass.simpleName), e)
        }
    }

    /** 兼容三种响应形态：纯 JSON / SSE 帧 / JSON-RPC 批。 */
    internal fun parseEnvelope(body: String, id: Long): JsonObject {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw McpException("MCP 响应为空")
        if (trimmed.startsWith("event:") || trimmed.startsWith("data:") || trimmed.startsWith(":")) {
            val candidates = trimmed.split('\n')
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .filter { it.isNotEmpty() && it != "[DONE]" }
                .mapNotNull { runCatching { JsonLenient.json.parseToJsonElement(it).jsonObject }.getOrNull() }
            return candidates.lastOrNull { envelopeId(it) == id }
                ?: candidates.lastOrNull()
                ?: throw McpException("SSE 响应中没有可用的 JSON-RPC 消息")
        }
        val root = runCatching { JsonLenient.json.parseToJsonElement(trimmed) }.getOrNull()
            ?: throw McpException("MCP 响应不是合法 JSON")
        return when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }.lastOrNull { envelopeId(it) == id }
                ?: root.firstOrNull() as? JsonObject
                ?: throw McpException("MCP 批响应为空")
            is JsonObject -> root
            else -> throw McpException("MCP 响应格式不支持")
        }
    }

    private fun envelopeId(envelope: JsonObject): Long? =
        (envelope["id"] as? JsonPrimitive)?.content?.toLongOrNull()

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
        const val DEFAULT_CALL_TIMEOUT_MS = 60_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}

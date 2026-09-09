package com.luzzymeow.luzzyrp.assistant.domain.mcp

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * MCP **stdio** 客户端（PLAN §9.3：需要沙盒内有运行时）。
 *
 * 与 HTTP 客户端同构：`initialize` → `tools/list` → `tools/call`，区别只是承载在
 * [StdioTransport]（换行分隔 JSON-RPC）上。
 *
 * **前置条件**（诚实说明）：Alpine 最小 rootfs **未预装** Node/Python，用户需先
 * `apk add nodejs`（或 `python3`）——进程启动失败时返回明确错误，不假装支持。
 */
class McpStdioClient(
    private val transport: StdioTransport,
    private val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
) {

    private val ids = AtomicLong(1)

    /** 握手（返回 serverInfo 描述）。 */
    fun initialize(): String {
        val result = request(
            "initialize",
            buildJsonObject {
                put("protocolVersion", McpClient.PROTOCOL_VERSION)
                put("clientInfo", buildJsonObject { put("name", "LuzzyRP"); put("version", "1.5.0") })
                put("capabilities", buildJsonObject { })
            },
        )
        runCatching { notify("notifications/initialized") }
        val info = result["serverInfo"] as? JsonObject
        return (info?.get("name")?.jsonPrimitive?.content ?: "unknown") + " " +
            (info?.get("version")?.jsonPrimitive?.content ?: "?")
    }

    /** 列出工具。 */
    fun listTools(): List<McpToolSpec> {
        val result = request("tools/list", JsonObject(emptyMap()))
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

    /** 调用工具。 */
    fun callTool(toolName: String, args: JsonObject): McpCallResult {
        val result = request(
            "tools/call",
            buildJsonObject {
                put("name", toolName)
                put("arguments", args)
            },
        )
        val isError = result["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val text = (result["content"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content }
            ?.joinToString("\n")
            ?: result.toString()
        return McpCallResult(text = text.ifBlank { "（无文本内容）" }, isError = isError)
    }

    /** 发送请求并等待匹配 id 的响应（超时抛 [McpException]）。 */
    private fun request(method: String, params: JsonObject): JsonObject {
        if (!transport.isAlive()) throw McpException("MCP stdio 进程未运行")
        val id = ids.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()
        transport.writeLine(payload)

        val deadline = System.currentTimeMillis() + readTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            val line = transport.readLine(remaining.coerceAtLeast(1)) ?: continue
            val envelope = runCatching {
                com.luzzymeow.luzzyrp.assistant.domain.llm.JsonLenient.json.parseToJsonElement(line).jsonObjectOrNull()
            }.getOrNull() ?: continue
            val responseId = (envelope["id"] as? JsonPrimitive)?.content
            if (responseId != id.toString()) continue
            (envelope["error"] as? JsonObject)?.let { error ->
                throw McpException(
                    "MCP stdio 错误（$method）：${error["message"]?.jsonPrimitive?.content ?: "未知"}"
                )
            }
            return envelope["result"] as? JsonObject ?: throw McpException("MCP stdio 响应缺少 result")
        }
        throw McpException("MCP stdio 超时（${readTimeoutMs}ms）：$method")
    }

    private fun notify(method: String) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }.toString()
        runCatching { transport.writeLine(payload) }
    }

    fun isAlive(): Boolean = transport.isAlive()

    fun close() = transport.close()

    /** 探测（不抛异常）。 */
    suspend fun probe(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        runCatching { initialize() }.fold(
            onSuccess = { true to (null as String?) },
            onFailure = { false to (it.message ?: it.javaClass.simpleName) },
        )
    }

    companion object {
        const val DEFAULT_READ_TIMEOUT_MS: Long = 60_000L
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

/** 在沙盒内启动进程（由 runtime 层实现；domain 不依赖 Android）。 */
fun interface ProotSpawner {
    /** 返回已启动进程（stdin/stdout 可用）；失败返回 null。 */
    fun spawn(command: String, args: List<String>, env: Map<String, String>): Process?
}

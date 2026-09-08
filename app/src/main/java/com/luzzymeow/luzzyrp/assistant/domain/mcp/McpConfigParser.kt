package com.luzzymeow.luzzyrp.assistant.domain.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * MCP 配置解析（PLAN §9.2：自动识别两种常见格式）。
 *
 * ① Claude Desktop / 通用 `mcpServers` 映射：
 * ```jsonc
 * { "mcpServers": { "filesystem": { "command": "npx", "args": [...] },
 *                   "remote": { "url": "https://example.com/mcp", "headers": {...} } } }
 * ```
 * ② 单服务器对象或数组：`{ "name": "...", "type": "http", "url": "...", "headers": {...} }`
 *
 * **不猜不静默**：既不是 ①② 形态、或缺少 url/command 时抛 [McpException] 并说明原因。
 */
object McpConfigParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }

    fun parse(raw: String): List<McpServerConfig> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: throw McpException("不是合法 JSON")
        return when (root) {
            is JsonArray -> root.mapIndexed { index, element -> parseSingle(element, "server$index") }
            is JsonObject -> {
                val servers = root["mcpServers"]
                if (servers is JsonObject) {
                    servers.entries.map { (name, value) -> parseSingle(value, name) }
                } else {
                    listOf(parseSingle(root, "server0"))
                }
            }
            else -> throw McpException("顶层必须是对象或数组")
        }.also {
            if (it.isEmpty()) throw McpException("未解析到任何 MCP 服务器")
        }
    }

    private fun parseSingle(element: kotlinx.serialization.json.JsonElement, fallbackName: String): McpServerConfig {
        val obj = element as? JsonObject ?: throw McpException("服务器配置必须是对象（$fallbackName）")
        val name = obj.str("name")?.takeIf { it.isNotBlank() } ?: fallbackName
        val url = obj.str("url")
        val command = obj.str("command")
        val declaredType = obj.str("type")?.lowercase()

        val transport = when {
            declaredType == McpServerConfig.TRANSPORT_HTTP || declaredType == McpServerConfig.TRANSPORT_SSE ->
                declaredType
            url != null && url.contains("/sse") -> McpServerConfig.TRANSPORT_SSE
            url != null -> McpServerConfig.TRANSPORT_HTTP
            command != null -> McpServerConfig.TRANSPORT_STDIO
            else -> throw McpException("服务器 $name 缺少 url 或 command")
        }

        return McpServerConfig(
            name = name,
            transport = transport,
            url = url,
            headers = obj.stringMap("headers"),
            command = command,
            args = obj.stringList("args"),
            env = obj.stringMap("env"),
        )
    }

    /** 占位符 `${ENV_VAR}` 提示（PLAN §9.2：导入时提示用户填值）。 */
    fun placeholders(config: McpServerConfig): List<String> {
        val pattern = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}")
        val sources = listOfNotNull(config.url) + config.headers.values + config.env.values + config.args
        return sources.flatMap { pattern.findAll(it).map { m -> m.groupValues[1] } }.distinct()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() }?.content

    private fun JsonObject.stringMap(key: String): Map<String, String> {
        val obj = this[key] as? JsonObject ?: return emptyMap()
        return obj.entries.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.content?.let { k to it }
        }.toMap()
    }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
}

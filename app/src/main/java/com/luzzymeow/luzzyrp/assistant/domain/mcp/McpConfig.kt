package com.luzzymeow.luzzyrp.assistant.domain.mcp

/**
 * MCP 服务器配置（PLAN §9.1/§9.2）。
 *
 * 传输：`http`（Streamable HTTP）/ `sse`（旧式 HTTP+SSE）/ `stdio`（预留，P4 依赖沙盒）。
 */
data class McpServerConfig(
    val name: String,
    val transport: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** stdio 预留字段（P4 沙盒内启用前不连接）。 */
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) {
    val isHttp: Boolean get() = transport == TRANSPORT_HTTP || transport == TRANSPORT_SSE

    companion object {
        const val TRANSPORT_HTTP = "http"
        const val TRANSPORT_SSE = "sse"
        const val TRANSPORT_STDIO = "stdio"
    }
}

/** 导入预览项（PLAN §9.2：逐条预览名称/传输/可达性检测）。 */
data class McpImportPreview(
    val config: McpServerConfig,
    /** 可达性检测结果；null = 未检测。 */
    val reachable: Boolean? = null,
    val note: String? = null,
)

/** MCP 工具声明（`tools/list` 结果）。 */
data class McpToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema（原样透传给模型）。 */
    val inputSchemaJson: String,
)

/** MCP 调用结果（`tools/call` 结果，已归一为文本 + 附件）。 */
data class McpCallResult(
    val text: String,
    val isError: Boolean = false,
    val imageUrls: List<String> = emptyList(),
)

/** MCP 连接/调用错误（不携带密钥）。 */
class McpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 工具命名空间（PLAN §9.4：`mcp__<serverId>__<toolName>`，避免与内置撞名）。 */
object McpNamespace {
    const val PREFIX = "mcp__"
    private const val SEPARATOR = "__"

    fun qualify(serverId: String, toolName: String): String = "$PREFIX$serverId$SEPARATOR$toolName"

    /** 解析；非 MCP 工具名返回 null。toolName 允许含 `__`（按第一个分隔符切分）。 */
    fun parse(qualifiedName: String): Pair<String, String>? {
        if (!qualifiedName.startsWith(PREFIX)) return null
        val rest = qualifiedName.removePrefix(PREFIX)
        val index = rest.indexOf(SEPARATOR)
        if (index <= 0 || index + SEPARATOR.length >= rest.length) return null
        return rest.substring(0, index) to rest.substring(index + SEPARATOR.length)
    }
}

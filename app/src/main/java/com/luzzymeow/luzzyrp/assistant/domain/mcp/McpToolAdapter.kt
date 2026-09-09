package com.luzzymeow.luzzyrp.assistant.domain.mcp

import com.luzzymeow.luzzyrp.assistant.domain.llm.JsonLenient
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.serialization.json.JsonObject

/**
 * MCP 工具适配器（PLAN §9.4）：把远端工具包装成 domain [Tool]。
 *
 * 传输无关——HTTP/SSE 与 stdio 都通过 [call] 回调注入（见 [http] / [stdio] 工厂），
 * 因此新增传输不会重复实现参数校验、错误归一与截断逻辑。
 *
 * 约定：
 * - 命名空间 `mcp__<serverId>__<toolName>`，避免与内置工具撞名；
 * - 分级 **T2**（外部工具）——默认关闭，需用户显式开启 + **逐调用审批**；
 * - `inputSchema` 原样透传（远端自己声明参数），**不本地改写**；
 * - 远端错误转 [ToolResult.Error] 回灌模型，不中断整轮。
 */
class McpToolAdapter internal constructor(
    val serverId: String,
    val serverName: String,
    private val spec: McpToolSpec,
    private val call: suspend (toolName: String, args: JsonObject) -> McpCallResult,
) : Tool {

    override val name: String = McpNamespace.qualify(serverId, spec.name)

    override val description: String =
        "[MCP:$serverName] ${spec.description.ifBlank { spec.name }}"

    override val tier: ToolTier = ToolTier.T2_WRITE_DEVICE

    override val parameters: JsonObject =
        runCatching { JsonLenient.json.parseToJsonElement(spec.inputSchemaJson) as? JsonObject }
            .getOrNull() ?: Schema.empty()

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = try {
        val result = call(spec.name, args)
        if (result.isError) {
            ToolResult.Error(result.text.take(2000))
        } else {
            val images = if (result.imageUrls.isEmpty()) "" else
                "\n（图片 ${result.imageUrls.size} 张，已省略 base64/URL 以免灌爆上下文）"
            ToolResult.Ok(result.text.take(20_000) + images)
        }
    } catch (e: McpException) {
        ToolResult.Error("MCP 调用失败（$serverName/${spec.name}）：${e.message}", retryable = true)
    } catch (e: Exception) {
        ToolResult.Error("MCP 调用异常（$serverName/${spec.name}）：${e.message ?: e.javaClass.simpleName}")
    }

    companion object {
        /** HTTP / Streamable HTTP / SSE 传输。 */
        fun http(
            serverId: String,
            serverName: String,
            config: McpServerConfig,
            spec: McpToolSpec,
            client: McpClient,
        ): McpToolAdapter = McpToolAdapter(serverId, serverName, spec) { toolName, args ->
            client.callTool(config, toolName, args)
        }

        /** stdio 传输（沙盒内进程）。 */
        fun stdio(
            serverId: String,
            serverName: String,
            spec: McpToolSpec,
            client: McpStdioClient,
        ): McpToolAdapter = McpToolAdapter(serverId, serverName, spec) { toolName, args ->
            client.callTool(toolName, args)
        }
    }
}

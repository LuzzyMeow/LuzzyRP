package com.luzzymeow.luzzyrp.assistant.runtime

import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.McpServerEntity
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpClient
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpConfigParser
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpException
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpNamespace
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpServerConfig
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpStdioClient
import com.luzzymeow.luzzyrp.assistant.domain.mcp.ProcessStdioTransport
import com.luzzymeow.luzzyrp.assistant.domain.mcp.ProotSpawner
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpToolAdapter
import com.luzzymeow.luzzyrp.assistant.domain.mcp.McpToolSpec
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MCP 服务器仓库（PLAN §9）。
 *
 * 职责：
 * - **JSON 导入**：解析两种格式 → 落库（逐条预览由 UI 层调用 [preview]）；
 * - **连接**：`initialize` → `tools/list` → 生成 [McpToolAdapter] 注册进 [ToolRegistry]；
 * - **连接状态**：成功写 `lastConnectedAt`，失败写 `lastError`（UI 可见）；
 * - 工具**全部按 T2 外部工具**处理（默认关闭 + 逐调用审批，PLAN §9.4）。
 */
class McpRepository(
    private val database: AssistantDatabase,
    private val registry: ToolRegistry,
    private val client: McpClient = McpClient(),
    /** stdio 进程启动器（runtime 层注入 proot 沙盒实现）；未注入时 stdio 明确报「需沙盒」。 */
    private val spawner: ProotSpawner = ProotSpawner { _, _, _ -> null },
) {

    private val stdioSessions = mutableMapOf<String, McpStdioClient>()

    private fun spawnerAvailable(): Boolean = spawner.spawn("true", emptyList(), emptyMap()) != null

    private val serverDao = database.mcpServerDao()
    private val bindingDao = database.mcpBindingDao()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val now: () -> Long = System::currentTimeMillis

    suspend fun all(): List<McpServerEntity> = serverDao.getAll()

    suspend fun get(id: String): McpServerEntity? = serverDao.getById(id)

    /** 解析 JSON 并落库；返回导入的服务器实体。解析失败抛 [McpException]。 */
    suspend fun importJson(raw: String): List<McpServerEntity> {
        val configs = McpConfigParser.parse(raw)
        return configs.map { config ->
            val entity = McpServerEntity(
                id = UUID.randomUUID().toString(),
                name = config.name,
                transport = config.transport,
                url = config.url,
                headersJson = config.headers.toJsonObjectOrNull(),
                command = config.command,
                argsJson = config.args.toJsonArrayOrNull(),
                envJson = config.env.toJsonObjectOrNull(),
                enabledGlobal = false,
                toolAllowlistJson = null,
                lastConnectedAt = null,
                lastError = null,
            )
            serverDao.upsert(entity)
            entity
        }
    }

    /** 导入前预览（含可达性探测；不落库）。 */
    suspend fun preview(raw: String): List<Pair<McpServerConfig, Pair<Boolean, String?>>> =
        McpConfigParser.parse(raw).map { config -> config to client.probe(config) }

    suspend fun setEnabledGlobal(id: String, enabled: Boolean) = serverDao.setEnabledGlobal(id, enabled)

    suspend fun delete(id: String) {
        unregisterTools(id)
        bindingDao.deleteByServer(id)
        serverDao.deleteById(id)
    }

    suspend fun bindingsFor(assistantId: String): List<McpBindingEntity> = bindingDao.getByAssistant(assistantId)

    suspend fun setBinding(serverId: String, assistantId: String, enabled: Boolean) =
        bindingDao.upsert(McpBindingEntity(assistantId, serverId, enabled))

    /**
     * 连接一个服务器并把工具注册进 registry（幂等：先注销旧工具）。
     * 返回 `(成功, 工具数或错误)`。
     */
    suspend fun connect(serverId: String): Pair<Boolean, String> {
        val entity = serverDao.getById(serverId) ?: return false to "服务器不存在"
        val config = entity.toConfig()
        unregisterTools(serverId)
        if (!config.isHttp) {
            return connectStdio(entity, config)
        }
        return try {
            client.initialize(config)
            val specs = client.listTools(config)
            registerTools(entity, config, specs)
            serverDao.updateConnectionState(serverId, now(), null)
            true to "已连接，注册 ${specs.size} 个工具"
        } catch (e: McpException) {
            serverDao.updateConnectionState(serverId, null, e.message)
            false to (e.message ?: "连接失败")
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            serverDao.updateConnectionState(serverId, null, message)
            false to message
        }
    }

    /** 连接所有「全局启用」的服务器（App 启动 / 设置变更后调用）。 */
    suspend fun connectEnabled(): List<Pair<String, String>> {
        val enabled = runCatching { serverDao.getGloballyEnabled() }.getOrDefault(emptyList())
        return enabled.map { entity ->
            val (_, message) = connect(entity.id)
            entity.name to message
        }
    }

    /**
     * stdio 连接（PLAN §9.3）：在 proot 沙盒内启动命令，JSON-RPC 走 stdin/stdout。
     *
     * **前置**：沙盒已安装且命令存在（`npx`/`uvx` 需先 `apk add nodejs`/`python3`）。
     */
    private suspend fun connectStdio(entity: McpServerEntity, config: McpServerConfig): Pair<Boolean, String> {
        val command = config.command ?: return false to "stdio 服务器缺少 command"
        if (!spawnerAvailable()) {
            val note = "stdio 需要 proot 沙盒（首次使用请在终端页切换到沙盒模式完成安装）"
            serverDao.updateConnectionState(entity.id, null, note)
            return false to note
        }
        return try {
            val session = McpStdioClient(ProcessStdioTransport(spawner, command, config.args, config.env))
            val serverInfo = session.initialize()
            val specs = session.listTools()
            specs.forEach { spec ->
                registry.register(McpToolAdapter.stdio(entity.id, entity.name, spec, session))
            }
            stdioSessions[entity.id]?.close()
            stdioSessions[entity.id] = session
            serverDao.updateConnectionState(entity.id, now(), null)
            true to "已连接（$serverInfo），注册 ${specs.size} 个工具"
        } catch (e: McpException) {
            serverDao.updateConnectionState(entity.id, null, e.message)
            false to (e.message ?: "stdio 连接失败")
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            serverDao.updateConnectionState(entity.id, null, message)
            false to message
        }
    }

    /** 断开并注销工具。 */
    suspend fun unregisterTools(serverId: String) {
        stdioSessions.remove(serverId)?.close()
        registry.all()
            .filter { it.name.startsWith(McpNamespace.PREFIX + serverId + "__") }
            .forEach { registry.unregister(it.name) }
    }

    private fun registerTools(entity: McpServerEntity, config: McpServerConfig, specs: List<McpToolSpec>) {
        val allowlist = entity.toolAllowlistJson?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonArray }
                .getOrNull()?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
        }
        specs.forEach { spec ->
            if (allowlist != null && spec.name !in allowlist) return@forEach
            registry.register(
                McpToolAdapter.http(
                    serverId = entity.id,
                    serverName = entity.name,
                    config = config,
                    spec = spec,
                    client = client,
                )
            )
        }
    }

    private fun McpServerEntity.toConfig() = McpServerConfig(
        name = name,
        transport = transport,
        url = url,
        headers = headersJson.toMapOrEmpty(),
        command = command,
        args = argsJson.toListOrEmpty(),
        env = envJson.toMapOrEmpty(),
    )

    private fun String?.toMapOrEmpty(): Map<String, String> {
        if (this.isNullOrBlank()) return emptyMap()
        return runCatching {
            (json.parseToJsonElement(this) as? JsonObject)?.entries
                ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.let { key to it } }
                ?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    private fun String?.toListOrEmpty(): List<String> {
        if (this.isNullOrBlank()) return emptyList()
        return runCatching {
            (json.parseToJsonElement(this) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrNull() ?: emptyList()
    }
}

/** Map → JSON 对象（空则 null）。 */
private fun Map<String, String>.toJsonObjectOrNull(): String? {
    if (isEmpty()) return null
    return buildJsonObject { forEach { (key, value) -> put(key, value) } }.toString()
}

/** List<String> → JSON 数组（空则 null）。 */
private fun List<String>.toJsonArrayOrNull(): String? {
    if (isEmpty()) return null
    return buildJsonArray { forEach { add(JsonPrimitive(it)) } }.toString()
}

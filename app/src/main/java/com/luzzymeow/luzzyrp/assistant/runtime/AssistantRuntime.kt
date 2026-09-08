package com.luzzymeow.luzzyrp.assistant.runtime

import android.content.Context
import com.luzzymeow.luzzyrp.assistant.AssistantConfigHolder
import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabaseProvider
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.data.prefs.AssistantPrefs
import com.luzzymeow.luzzyrp.assistant.data.workspace.WorkspaceManager
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.OpenAiTransport
import com.luzzymeow.luzzyrp.assistant.domain.loop.AgentLoop
import com.luzzymeow.luzzyrp.assistant.domain.loop.BudgetGuard
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.domain.prompt.ContextBuilder
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryItem
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate
import com.luzzymeow.luzzyrp.assistant.domain.tool.CodeRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.AskUserTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.ClipboardReadTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.ClipboardWriteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.GetDeviceInfoTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.GetTimeTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryDeleteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryListTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemorySearchTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryUpdateTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryWriteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.RunCodeTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.TerminalRunTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WebFetchTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceDeleteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceListTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceMkdirTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceMoveTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspacePatchTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceReadTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WorkspaceWriteTool
import com.luzzymeow.luzzyrp.assistant.runtime.memory.EmbeddingConfig
import com.luzzymeow.luzzyrp.assistant.runtime.memory.RoomMemoryStore
import com.luzzymeow.luzzyrp.assistant.runtime.terminal.GlobalShellRunner
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.AndroidClipboardPort
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.AndroidDeviceInfoProvider
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.SystemClockProvider
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 助手运行时装配（PLAN §2.2 集成层）。
 *
 * 把「端口实现 + 工具注册 + 传输层 + 记忆 + 上下文装配」组装成一个 [AgentLoop]，
 * 供 ViewModel 直接消费。**单例由 [AssistantRuntimeProvider] 持有**。
 *
 * 配置来源：Web 端只读推送（[AssistantConfigHolder]）——供应商/Key/模型不在原生侧重复落盘
 * （PLAN §1.1）。
 */
class AssistantRuntime(
    private val context: Context,
    private val prefs: AssistantPrefs = AssistantPrefs.create(context),
    private val database: AssistantDatabase = AssistantDatabaseProvider.get(context),
    private val shellRunnerFactory: (File) -> ShellRunner = { dir ->
        GlobalShellRunner(workingDir = dir, overflowDir = File(dir, "exports"))
    },
    private val codeRunnerFactory: ((File) -> CodeRunner)? = null,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 工具开关的内存快照（DataStore 异步 → 审批门需要同步判定）。 */
    @Volatile
    private var toolSwitches: Map<String, Boolean> = emptyMap()

    val approvalGate: ApprovalGate = ApprovalGate(
        globalSwitch = { name -> toolSwitches[name] },
        policy = ApprovalGate.POLICY_PER_CALL,
    )

    /** 数据仓库（P2 持久化：会话/消息/检索/导出）。 */
    val repository: AssistantRepository = AssistantRepository(database)

    /** 技能仓库（P2：内置导入 / 文件导入 / 全局与助手级启用）。 */
    val skillRepository: SkillRepository = SkillRepository(context, database)


    val workspaceManager: WorkspaceManager = WorkspaceManager(context)

    val memoryStore: RoomMemoryStore = RoomMemoryStore(
        dao = database.memoryDao(),
        embeddingConfig = { assistantId -> embeddingConfigFor(assistantId) },
        onDegraded = { reason -> log(reason) },
    )

    val registry: ToolRegistry = ToolRegistry(approval = approvalGate)

    /** MCP 仓库（P2：JSON 导入 / HTTP·SSE 连接 / 工具注册为 T2 外部工具）。 */
    val mcpRepository: McpRepository = McpRepository(database, registry)

    val transport: OpenAiTransport = OpenAiTransport(log = { msg -> log(msg) })

    val contextBuilder: ContextBuilder = ContextBuilder(
        memory = MemoryProvider { query, mode, limit ->
            val assistantId = activeAssistantId() ?: return@MemoryProvider emptyList()
            memoryStore.search(query, assistantId, limit).map {
                MemoryItem(id = it.id, content = it.content, type = it.type, score = it.similarity?.toDouble())
            }
        },
        summarizer = null,
    )

    val loop: AgentLoop = AgentLoop(
        transport = transport,
        tools = registry,
        prompt = contextBuilder,
        approval = approvalGate,
        budget = BudgetGuard(),
    )

    init {
        registerBuiltinTools()
    }

    // ------------------------------------------------------------------
    // 工具注册
    // ------------------------------------------------------------------

    private fun registerBuiltinTools() {
        val clipboard = AndroidClipboardPort(context)
        val deviceInfo = AndroidDeviceInfoProvider(context)
        val clock = SystemClockProvider()

        registry.registerAll(
            listOf(
                AskUserTool(),
                GetTimeTool(clock),
                GetDeviceInfoTool(deviceInfo),
                ClipboardReadTool(clipboard),
                ClipboardWriteTool(clipboard),
                WebFetchTool(),
                WorkspaceListTool(),
                WorkspaceReadTool(),
                WorkspaceWriteTool(),
                WorkspacePatchTool(),
                WorkspaceDeleteTool(),
                WorkspaceMoveTool(),
                WorkspaceMkdirTool(),
                MemoryWriteTool(memoryStore),
                MemorySearchTool(memoryStore),
                MemoryUpdateTool(memoryStore),
                MemoryDeleteTool(memoryStore),
                MemoryListTool(memoryStore),
            )
        )
    }

    /** 某助手工作区内的宿主 shell（终端页直接使用；工作目录 = 该助手 files/）。 */
    suspend fun shellRunnerFor(assistantId: String): ShellRunner = shellRunnerFactory(workspaceManager.filesDir(assistantId))

    /** 为某助手装配终端/代码执行工具（依赖其工作区目录）。 */
    suspend fun registerExecToolsFor(assistantId: String) {
        val filesDir = workspaceManager.filesDir(assistantId)
        registry.register(TerminalRunTool(shellRunnerFactory(filesDir)))
        codeRunnerFactory?.let { registry.register(RunCodeTool(it(filesDir))) }
    }

    // ------------------------------------------------------------------
    // 配置解析（Web 端只读镜像）
    // ------------------------------------------------------------------

    /** 解析 Web 端推送的配置（未推送 / 解析失败返回 null）。 */
    fun webConfig(): WebAssistantConfig? {
        val raw = AssistantConfigHolder.get()
        if (raw.isBlank()) return null
        return runCatching { parseWebConfig(raw) }.getOrNull()
    }

    /**
     * 构造一次请求模板：模型引用 `providerId::bareId`，找不到对应供应商时回退激活供应商。
     */
    fun resolveRequest(assistant: AssistantEntity): LlmRequest? {
        val config = webConfig() ?: return null
        val (providerId, bareModel) = splitModelRef(assistant.modelId ?: config.activeModelId)
        val provider = config.providers.firstOrNull { it.id == providerId }
            ?: config.providers.firstOrNull { it.id == config.apiProviderId }
            ?: return null
        val apiKey = provider.apiKey.ifBlank { config.apiKey }
        val baseUrl = provider.apiUrl ?: config.apiUrl
        if (baseUrl.isBlank() || apiKey.isBlank()) return null
        return LlmRequest(
            messages = emptyList(),
            protocol = provider.protocol ?: "openai",
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = bareModel.ifBlank { config.activeModelId },
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            extraBody = assistant.extraBodyJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() },
        )
    }

    /** 按 id 取助手实体（不存在返回 null）。 */
    suspend fun assistantEntity(assistantId: String): AssistantEntity? =
        runCatching { database.assistantDao().getById(assistantId) }.getOrNull()

    /** 按 id 构造请求模板（找不到助手/配置返回 null）。 */
    suspend fun resolveRequestById(assistantId: String): LlmRequest? {
        val entity = assistantEntity(assistantId) ?: return null
        return resolveRequest(entity)
    }

    private suspend fun embeddingConfigFor(assistantId: String): EmbeddingConfig? {
        val assistant = database.assistantDao().getById(assistantId) ?: return null
        val ref = assistant.embeddingModelRef?.takeIf { it.isNotBlank() } ?: return null
        val config = webConfig() ?: return null
        val (providerId, bareModel) = splitModelRef(ref)
        val provider = config.providers.firstOrNull { it.id == providerId } ?: return null
        val apiKey = provider.apiKey.ifBlank { config.apiKey }
        if (apiKey.isBlank()) return null
        return EmbeddingConfig(
            baseUrl = provider.apiUrl ?: config.apiUrl,
            apiKey = apiKey,
            model = bareModel,
            modelRef = ref,
        )
    }

    private suspend fun activeAssistantId(): String? =
        prefs.activeAssistantId.let { flow -> runCatching { firstOrNull(flow) }.getOrNull() }

    /** 工作区端口（每助手独立目录，越界由 [WorkspaceManager] 抛异常）。 */
    fun workspaceAccessFor(assistantId: String): WorkspaceAccess = object : WorkspaceAccess {
        override suspend fun list(relativeDir: String): List<WorkspaceEntry> =
            workspaceManager.list(assistantId, relativeDir.ifBlank { "files" }).map { entry ->
                // 数据层 WorkspaceEntry → domain WorkspaceEntry（两层的字段略有差异）
                WorkspaceEntry(
                    relativePath = entry.relativePath,
                    isDirectory = entry.isDirectory,
                    sizeBytes = entry.sizeBytes,
                )
            }

        override suspend fun read(relativePath: String): ByteArray =
            workspaceManager.readBytes(assistantId, relativePath)

        override suspend fun write(relativePath: String, bytes: ByteArray) {
            workspaceManager.writeBytes(assistantId, relativePath, bytes)
        }

        override suspend fun delete(relativePath: String) {
            workspaceManager.delete(assistantId, relativePath)
        }

        override suspend fun move(fromRelative: String, toRelative: String) {
            workspaceManager.move(assistantId, fromRelative, toRelative)
        }

        override suspend fun mkdir(relativeDir: String) {
            workspaceManager.mkdir(assistantId, relativeDir)
        }
        override suspend fun exists(relativePath: String): Boolean =
            workspaceManager.exists(assistantId, relativePath)
    }

    /** 构造工具执行上下文。 */
    fun toolContext(
        assistantId: String,
        conversationId: String,
        onLog: (String) -> Unit = { log(it) },
        cancelled: () -> Boolean = { false },
    ): ToolContext = object : ToolContext {
        override val assistantId = assistantId
        override val conversationId = conversationId
        override val workspace: WorkspaceAccess = workspaceAccessFor(assistantId)
        override val cancelled = cancelled
        override val log: (String) -> Unit = onLog
    }

    /** 诊断日志（**白名单**：禁止写入密钥/文件内容；只写事件名与长度）。 */
    private fun log(message: String) {
        // 交由宿主（MainActivity / 诊断页）接管；默认丢弃，避免日志泄漏
    }

    private suspend fun firstOrNull(flow: kotlinx.coroutines.flow.Flow<String?>): String? {
        var result: String? = null
        flow.collect { result = it; return@collect }
        return result
    }

    private fun splitModelRef(ref: String): Pair<String, String> {
        val idx = ref.indexOf("::")
        return if (idx <= 0) "" to ref else ref.substring(0, idx) to ref.substring(idx + 2)
    }

    private fun parseWebConfig(raw: String): WebAssistantConfig {
        val root = json.parseToJsonElement(raw).jsonObject
        fun str(key: String) = (root[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        val providers = (root["providers"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
            WebProvider(
                id = s("id") ?: return@mapNotNull null,
                name = s("name") ?: "",
                protocol = s("protocol"),
                apiUrl = s("apiUrl"),
                apiKey = s("apiKey") ?: "",
                models = (obj["models"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            )
        } ?: emptyList()
        return WebAssistantConfig(
            apiProviderId = str("apiProviderId"),
            apiUrl = str("apiUrl"),
            apiKey = str("apiKey"),
            activeModelId = str("activeModelId"),
            providers = providers,
        )
    }
}

/** Web 端推送的配置（只读镜像）。 */
data class WebAssistantConfig(
    val apiProviderId: String,
    val apiUrl: String,
    val apiKey: String,
    val activeModelId: String,
    val providers: List<WebProvider>,
)

data class WebProvider(
    val id: String,
    val name: String,
    val protocol: String?,
    val apiUrl: String?,
    val apiKey: String,
    val models: List<String>,
)

/** 进程内单例（与 Room 单例同生命周期）。 */
object AssistantRuntimeProvider {
    @Volatile
    private var instance: AssistantRuntime? = null

    fun get(context: Context): AssistantRuntime {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            instance ?: AssistantRuntime(context.applicationContext).also { instance = it }
        }
    }

    fun close() {
        synchronized(this) { instance = null }
    }
}

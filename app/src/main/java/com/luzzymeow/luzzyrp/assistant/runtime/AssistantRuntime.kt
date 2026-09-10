package com.luzzymeow.luzzyrp.assistant.runtime

import android.content.Context
import com.luzzymeow.luzzyrp.assistant.AssistantConfigHolder
import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabaseProvider
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.data.prefs.AssistantPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.luzzymeow.luzzyrp.assistant.data.workspace.WorkspaceManager
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRequest
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmTransport
import com.luzzymeow.luzzyrp.assistant.domain.llm.defaultRoutingTransport
import com.luzzymeow.luzzyrp.assistant.domain.loop.AgentLoop
import com.luzzymeow.luzzyrp.assistant.domain.loop.BudgetGuard
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.domain.prompt.ContextBuilder
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryItem
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.ApprovalGate
import com.luzzymeow.luzzyrp.assistant.domain.tool.CodeRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.AskUserTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.ClipboardReadTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.CalendarReadTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.CalendarWriteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.ClipboardWriteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.GetDeviceInfoTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.GetTimeTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryDeleteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryListTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemorySearchTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryUpdateTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.MemoryWriteTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.RunCodeTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.SendToRpChatTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.TerminalRunTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WebFetchTool
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.WebSearchTool
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
import com.luzzymeow.luzzyrp.assistant.runtime.terminal.ProotRuntime
import com.luzzymeow.luzzyrp.assistant.runtime.terminal.SandboxCodeRunner
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.AndroidCalendarPort
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.AndroidClipboardPort
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.AndroidDeviceInfoProvider
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.BraveProvider
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.DuckDuckGoProvider
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.SearXngProvider
import com.luzzymeow.luzzyrp.assistant.runtime.toolimpl.TavilyProvider
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

    /**
     * 运行时级协程作用域：常驻的跟随任务（工具开关快照）挂在这里。
     *
     * 用 `SupervisorJob + Default`：单条跟随流失败不应拖垮整表，也不占主线程。
     */
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 工具开关的内存快照（DataStore 异步 → 审批门需要同步判定）。 */
    @Volatile
    private var toolSwitches: Map<String, Boolean> = emptyMap()

    val approvalGate: ApprovalGate = ApprovalGate(
        globalSwitch = { name -> toolSwitches[name] },
        policy = ApprovalGate.POLICY_PER_CALL,
    )

    /** 工具开关跟随任务的句柄（幂等：只装一次）。 */
    private var toolSwitchJob: Job? = null

    init {
        // [2026-09-11 静态审查修复] 此前**没有任何地方**往 toolSwitches 写过值——审批门的
        // globalSwitch 永远返回 null，于是永远取 tier 默认值（T2/T3 全关），用户即便在设置页
        // 打开了开关也不会生效。这里在运行时构造时挂上跟随任务，让内存快照跟随 DataStore。
        observeToolSwitches(runtimeScope)
    }

    // ------------------------------------------------------------------
    // 工具开关（DataStore ⇄ 内存快照）
    // ------------------------------------------------------------------

    /**
     * 让内存快照跟随 DataStore（幂等）。
     *
     * 审批门是**同步**判定（[ApprovalGate.isEnabled]），DataStore 却是异步流，故把显式开关收敛成
     * 一份 `@Volatile` 快照。由 `init` 自动调用一次。
     */
    fun observeToolSwitches(scope: CoroutineScope) {
        if (toolSwitchJob != null) return
        toolSwitchJob = scope.launch {
            prefs.observeExplicitToolSwitches().collect { explicit -> toolSwitches = explicit }
        }
    }

    /**
     * 内置（非 MCP）工具清单——助手设置页「工具开关」用。
     *
     * **默认关闭的（T2/T3）排在前面**：它们是「需用户显式开启」那一档，用户进这一屏就是来开它们的。
     * MCP 工具不在其列（在 MCP 页按服务器管）。
     */
    fun builtinTools(): List<Tool> = registry.all()
        .filterNot { it.name.startsWith(AssistantPrefs.MCP_TOOL_NAME_PREFIX) }
        .sortedWith(compareBy({ it.tier.defaultEnabled }, { it.name }))

    /** 某工具当前是否启用（显式开关优先，否则取 tier 默认）。 */
    fun isToolEnabled(tool: Tool): Boolean = approvalGate.isEnabled(tool)

    /**
     * 一次性读取**显式**开关（DataStore 直读，不经内存快照）。
     *
     * UI 保存后回读必须走这条：内存快照是异步跟随的，写完立刻读快照可能读到旧值，
     * 界面就会「点了没反应」。DataStore 自身读写一致，故设置页以它为真相。
     */
    suspend fun explicitToolSwitches(): Map<String, Boolean> =
        runCatching { prefs.observeExplicitToolSwitches().first() }.getOrDefault(emptyMap())

    /** 写入工具开关（真相在 DataStore，内存快照由 [observeToolSwitches] 跟随）。 */
    suspend fun setToolEnabled(toolName: String, enabled: Boolean) {
        prefs.setToolGlobalSwitch(toolName, enabled)
    }

    /** 数据仓库（P2 持久化：会话/消息/检索/导出）。 */
    val repository: AssistantRepository = AssistantRepository(database)

    /** 技能仓库（P2：内置导入 / 文件导入 / 全局与助手级启用）。 */
    val skillRepository: SkillRepository = SkillRepository(context, database)


    val workspaceManager: WorkspaceManager = WorkspaceManager(context)

    /** 密钥加密存储（AndroidKeyStore AES-GCM；PLAN §13.2：密钥不进 DataStore/Room/日志）。 */
    val secretStore: com.luzzymeow.luzzyrp.assistant.data.prefs.SecretStore =
        com.luzzymeow.luzzyrp.assistant.runtime.prefs.KeystoreSecretStore(
            File(File(context.filesDir, "assistant"), "secrets.json"),
        )

    /** proot 沙盒运行时（随包内置 proot + Alpine rootfs；首次使用释放，PLAN §10.2）。 */
    val prootRuntime: ProotRuntime = ProotRuntime(context)

    val memoryStore: RoomMemoryStore = RoomMemoryStore(
        dao = database.memoryDao(),
        embeddingConfig = { assistantId -> embeddingConfigFor(assistantId) },
        onDegraded = { reason -> log(reason) },
    )

    /** 审计落库（PLAN §13.2）。 */
    val auditSink: RoomAuditSink = RoomAuditSink(database.toolAuditDao())

    val registry: ToolRegistry = ToolRegistry(approval = approvalGate, audit = auditSink)

    /** MCP 仓库（P2：JSON 导入 / HTTP·SSE 连接 / 工具注册为 T2 外部工具）。 */
    val mcpRepository: McpRepository = McpRepository(
        database = database,
        registry = registry,
        spawner = com.luzzymeow.luzzyrp.assistant.domain.mcp.ProotSpawner { command, args, env ->
            prootRuntime.spawnInteractive(command, args, env)
        },
    )

    /** 三协议分派（OpenAI / Anthropic / Gemini，PLAN §5.3）。 */
    val transport: LlmTransport = defaultRoutingTransport(log = { msg -> log(msg) })

    val contextBuilder: ContextBuilder = ContextBuilder(
        memory = MemoryProvider { query, mode, limit ->
            val assistantId = activeAssistantId() ?: return@MemoryProvider emptyList()
            memoryStore.search(query, assistantId, limit).map {
                MemoryItem(id = it.id, content = it.content, type = it.type, score = it.similarity?.toDouble())
            }
        },
        summarizer = LlmSummarizer(
            transport = transport,
            templateProvider = {
                // 复用当前激活助手的请求模板（只读 Web 端配置，不新增密钥落盘）
                val activeId = activeAssistantId()
                activeId?.let { resolveRequestById(it) }
            },
        ),
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
                WebSearchTool(
                    providersProvider = {
                        buildList {
                            add(DuckDuckGoProvider())
                            currentSearxngUrl()?.takeIf { it.isNotBlank() }?.let { add(SearXngProvider(it)) }
                            if (hasSecret(TavilyProvider.KEY_SECRET)) add(TavilyProvider(secretStore))
                            if (hasSecret(BraveProvider.KEY_SECRET)) add(BraveProvider(secretStore))
                        }
                    },
                    defaultProviderIdProvider = { currentSearchProvider() },
                    knownProviderIds = listOf("duckduckgo", "searxng", "tavily", "brave"),
                ),
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
                // T2：默认关闭 + 逐调用审批（日历需运行时权限）
                CalendarReadTool(AndroidCalendarPort(context)),
                CalendarWriteTool(AndroidCalendarPort(context)),
                // 预留：RP 会话回填（默认关；未接线时工具返回「未启用」）
                SendToRpChatTool(),
            )
        )
    }

    /** 某助手工作区内的宿主 shell（终端页直接使用；工作目录 = 该助手 files/）。 */
    suspend fun shellRunnerFor(assistantId: String): ShellRunner = shellRunnerFactory(workspaceManager.filesDir(assistantId))

    /** 沙盒 shell（工作区 bind 到容器 /workspace；未安装时返回明确错误）。 */
    suspend fun sandboxShellFor(assistantId: String): ShellRunner = object : ShellRunner {
        override val mode = ProotRuntime.MODE_SANDBOX
        override suspend fun run(command: String, timeoutMs: Long): com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult =
            prootRuntime.runInWorkspace(workspaceManager.filesDir(assistantId), command, timeoutMs)
    }

    /**
     * 为某助手装配终端/代码执行工具（依赖其工作区目录）。
     *
     * 沙盒可用时：`terminal_run` 走 proot（真 Linux，可 apk add），`run_code` 走沙盒解释器；
     * 否则退回宿主 shell，`run_code` 明确报「需沙盒」。
     */
    suspend fun registerExecToolsFor(assistantId: String) {
        val filesDir = workspaceManager.filesDir(assistantId)
        val sandboxReady = prootRuntime.isInstalled()
        registry.register(
            TerminalRunTool(
                if (sandboxReady) sandboxShellFor(assistantId) else shellRunnerFactory(filesDir),
            )
        )
        val codeRunner = when {
            sandboxReady -> SandboxCodeRunner(prootRuntime, filesDir)
            else -> codeRunnerFactory?.invoke(filesDir)
        }
        codeRunner?.let { registry.register(RunCodeTool(it)) }
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

    /** 密钥是否存在（**只返回布尔**，不回显内容）。 */
    suspend fun hasSecret(key: String): Boolean =
        runCatching { !secretStore.get(key).isNullOrBlank() }.getOrDefault(false)

    suspend fun putSecret(key: String, value: String) {
        if (value.isBlank()) secretStore.remove(key) else secretStore.put(key, value)
    }

    /** 搜索设置读写（设置页用；非密钥）。 */
    suspend fun currentSearchProviderId(): String = currentSearchProvider()

    suspend fun currentSearxngUrlValue(): String? = currentSearxngUrl()

    suspend fun setSearchProviderId(id: String) = prefs.setSearchProvider(id)

    suspend fun setSearxngUrlValue(url: String) = prefs.setSearxngUrl(url)

    /** 当前搜索提供方 id（DataStore）。 */
    private suspend fun currentSearchProvider(): String =
        runCatching { firstOf(prefs.searchProvider) }.getOrNull() ?: AssistantPrefs.DEFAULT_SEARCH_PROVIDER

    /** 当前 SearXNG 实例地址（空 = 未配置）。 */
    private suspend fun currentSearxngUrl(): String? =
        runCatching { firstOf(prefs.searxngUrl) }.getOrNull()

    /**
     * 取 DataStore Flow 的**当前值**。
     *
     * ⚠️ 必须用 `first()`——DataStore 的 flow 是**无限流**（每次变更都重发），
     * `collect { … }` 永远不会返回，会导致整个 Agent 循环挂死（会话 39 修复）。
     */
    private suspend fun <T> firstOf(flow: kotlinx.coroutines.flow.Flow<T>): T? =
        runCatching { flow.first() }.getOrNull()

    /** 诊断日志（**白名单**：禁止写入密钥/文件内容；只写事件名与长度）。 */
    private fun log(message: String) {
        // 交由宿主（MainActivity / 诊断页）接管；默认丢弃，避免日志泄漏
    }

    /** 同上：DataStore Flow 取当前值必须用 `first()`（见 [firstOf]）。 */
    private suspend fun firstOrNull(flow: kotlinx.coroutines.flow.Flow<String?>): String? =
        runCatching { flow.first() }.getOrNull()

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

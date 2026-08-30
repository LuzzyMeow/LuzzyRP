package com.luzzymeow.luzzyrp.service

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.params.ThinkingConfig
import com.luzzymeow.luzzyrp.core.ai.provider.ProviderGateway
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.PromptPreset
import com.luzzymeow.luzzyrp.core.model.ThinkingDepth
import com.luzzymeow.luzzyrp.core.model.ThinkingDepthAdapter
import com.luzzymeow.luzzyrp.data.logger.AppLogger
import com.luzzymeow.luzzyrp.data.repository.PromptPresetRepository
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.Conversation
import com.luzzymeow.luzzyrp.core.model.SummaryCounters
import com.luzzymeow.luzzyrp.core.model.SummaryItem
import com.luzzymeow.luzzyrp.core.model.SummaryLevel
import com.luzzymeow.luzzyrp.core.model.ToolApprovalState
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.data.ai.GenerationHandler
import com.luzzymeow.luzzyrp.data.ai.PromptAssembler
import com.luzzymeow.luzzyrp.data.ai.prompts.TaskPrompts
import com.luzzymeow.luzzyrp.data.ai.tools.BuiltinTools
import com.luzzymeow.luzzyrp.data.datastore.Settings
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.data.repository.ConversationRepository
import com.luzzymeow.luzzyrp.data.repository.MemoryRepository
import com.luzzymeow.luzzyrp.data.repository.SummaryRepository
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话服务：生成任务唯一所有者（参考 rikkahub ChatService）。
 *
 * [INVARIANT-STREAMING] 单一真源（不变性 S5）：
 *   每会话一个 MutableStateFlow<Conversation>；GenerationHandler 的每个增量
 *   事件直写该 Flow；UI collectAsStateWithLifecycle —— 1 字 = 1 次更新。
 *   任务跑在 appScope（离开路由不中断），停止 = 取消 Job（awaitClose →
 *   eventSource.cancel() 断流）。
 *
 * [INVARIANT-AGENTIC] 续跑（不变性 A4）：审批写入 ToolApprovalState 后
 *   重新进入 [resumeGeneration]，Handler 先回填已批准/拒绝的工具再续循环。
 */
class ChatService(
    private val appScope: CoroutineScope,
    private val conversationRepository: ConversationRepository,
    private val cardRepository: CharacterCardRepository,
    private val worldbookRepository: WorldbookRepository,
    private val memoryRepository: MemoryRepository,
    private val summaryRepository: SummaryRepository,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderGateway,
    private val promptAssembler: PromptAssembler,
    private val presetRepository: PromptPresetRepository,
    private val logger: AppLogger,
) {

    /** 每会话单一真源。 */
    private val sessions = ConcurrentHashMap<String, MutableStateFlow<Conversation?>>()

    private val generationJobs = ConcurrentHashMap<String, Job>()

    private val persistMutex = Mutex()

    /** 错误流（UI Toast/错误卡片）。 */
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    /** 正在生成的会话 id 集合（响应式，输入栏 Stop/发送切换）。 */
    private val _generatingIds = MutableStateFlow<Set<String>>(emptySet())
    val generatingIds: StateFlow<Set<String>> = _generatingIds.asStateFlow()

    data class ChatError(val conversationId: String, val message: String, val at: Long = System.currentTimeMillis())

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _errors.value = _errors.value + ChatError("global", throwable.message ?: "未知错误")
    }

    // ------------------------------------------------------------------
    // 会话单一真源
    // ------------------------------------------------------------------

    fun observeConversation(id: String): StateFlow<Conversation?> {
        val flow = sessions.getOrPut(id) { MutableStateFlow<Conversation?>(null) }
        if (flow.value == null) {
            appScope.launch {
                val loaded = conversationRepository.getConversation(id)
                flow.value = loaded
            }
        }
        return flow
    }

    private fun sessionFlow(id: String): MutableStateFlow<Conversation?> =
        sessions.getOrPut(id) { MutableStateFlow<Conversation?>(null) }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        val flow = sessionFlow(id)
        flow.value = flow.value?.let(transform)
    }

    // ------------------------------------------------------------------
    // 发送与生成
    // ------------------------------------------------------------------

    /** 发送用户消息并启动生成。 */
    fun sendMessage(conversationId: String, text: String) {
        if (generationJobs[conversationId]?.isActive == true) return  // 生成中忽略重复发送
        val userMessage = UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(text)))
        logger.info(AppLogger.CAT_USER, "发送消息", "会话=$conversationId 长度=${text.length}")

        appScope.launch(exceptionHandler) {
            rememberLastConversation(conversationId)
            // 追加用户消息到当前节点（存储 + Flow）
            conversationRepository.appendMessageToCurrentNode(conversationId, userMessage)
            sessionFlow(conversationId).value = conversationRepository.getConversation(conversationId)
            startGeneration(conversationId)
        }
    }

    /** 重新生成（分支）：截掉最后一条 assistant 消息，从该处开新分支重新生成。 */
    fun regenerateAtLast(conversationId: String) {
        if (generationJobs[conversationId]?.isActive == true) return
        appScope.launch(exceptionHandler) {
            val conversation = sessionFlow(conversationId).value
                ?: conversationRepository.getConversation(conversationId) ?: return@launch
            val current = conversation.currentMessages()
            val branchBase = current.dropLastWhile { it.role == Role.ASSISTANT }
            val nodeId = conversationRepository.branchForRegenerate(conversationId, branchBase)
            sessionFlow(conversationId).value = conversationRepository.getConversation(conversationId)
            startGeneration(conversationId, nodeId)
        }
    }

    /** 审批回写（工具卡片按钮）→ 状态落库 → 续跑（A4）。 */
    fun handleToolApproval(conversationId: String, toolCallId: String, approved: Boolean, reason: String = "") {
        appScope.launch(exceptionHandler) {
            val newState = if (approved) ToolApprovalState.Approved else ToolApprovalState.Denied(reason)
            updateToolApprovalState(conversationId, toolCallId, newState)
            resumeGeneration(conversationId)
        }
    }

    /** ask_user 类工具：用户作答。 */
    fun answerAskUser(conversationId: String, toolCallId: String, answer: String) {
        appScope.launch(exceptionHandler) {
            updateToolApprovalState(conversationId, toolCallId, ToolApprovalState.Answered(answer))
            resumeGeneration(conversationId)
        }
    }

    /** 停止生成（Stop 按钮）：取消 Job → EventSource 断流。 */
    fun stopGenerating(conversationId: String) {
        generationJobs.remove(conversationId)?.cancel()
        _generatingIds.value = _generatingIds.value - conversationId
    }

    fun isGenerating(conversationId: String): Boolean = generationJobs[conversationId]?.isActive == true

    // ------------------------------------------------------------------
    // 生成主链路
    // ------------------------------------------------------------------

    private fun startGeneration(conversationId: String, fromNodeId: String? = null) {
        val job = appScope.launch(exceptionHandler) {
            runGeneration(conversationId, fromNodeId)
        }
        generationJobs[conversationId] = job
        _generatingIds.value = _generatingIds.value + conversationId
        job.invokeOnCompletion {
            if (generationJobs[conversationId] == job) {
                generationJobs.remove(conversationId)
                _generatingIds.value = _generatingIds.value - conversationId
            }
        }
    }

    private suspend fun runGeneration(conversationId: String, fromNodeId: String? = null) {
        val settings = settingsStore.settingsFlow.first()
        val provider = currentProvider(settings)
            ?: run { pushError(conversationId, "未配置可用的模型供应商，请到设置中添加"); return }
        val model = currentModel(settings, provider)
            ?: run { pushError(conversationId, "未选择模型，请到设置中选择"); return }

        // —— 上下文素材 ——
        val conversation = sessionFlow(conversationId).value
            ?: conversationRepository.getConversation(conversationId) ?: return
        val history = conversation.currentMessages()
        val card = conversation.cardId?.let { cardRepository.getById(it) }

        // —— 被动召回（[INVARIANT-AGENTIC] A6：预执行 + 系统提示注入） ——
        val queryText = history.lastOrNull { it.role == Role.USER }?.textContent().orEmpty()
        val queryEmbedding = runCatching { embedText(settings, queryText) }.getOrNull()
        val recall = worldbookRepository.recall(
            bookIds = conversation.worldbookIds + listOfNotNull(card?.worldbookId),
            conversationMessages = history,
            queryEmbedding = queryEmbedding,
        )
        val memoryItems = if (conversation.enableMemory && settings.memoryEnabled) {
            memoryRepository.recall(queryText, queryEmbedding, settings.memoryTopK)
        } else emptyList()

        // —— 三级摘要 ——
        val summariesC = summaryRepository.getByLevel(conversationId, SummaryLevel.C)
        val summariesB = summaryRepository.getByLevel(conversationId, SummaryLevel.B)
        val summariesA = summaryRepository.getByLevel(conversationId, SummaryLevel.A)

        // —— 工具与组装 ——
        val tagFallback = !model.capabilities.tool
        val toolRegistry = BuiltinTools.create(
            worldbookRepository = worldbookRepository,
            memoryRepository = memoryRepository,
            bookIdsProvider = { conversation.worldbookIds + listOfNotNull(card?.worldbookId) },
        )
        val preset = settings.activePresetId.takeIf { it.isNotBlank() }
            ?.let { runCatching { presetRepository.getById(it) }.getOrNull() }
        val assembled = promptAssembler.assemble(
            card = card,
            toolRegistry = toolRegistry,
            tagFallback = tagFallback,
            summariesC = summariesC,
            summariesB = summariesB,
            summariesA = summariesA,
            memories = memoryItems.map { it.content },
            recall = recall,
            preset = preset,
            userProfile = settings.userProfile,
        )
        val requestMessages = promptAssembler.buildMessages(assembled, history, currentInput = null)

        // 思考深度：模型级覆盖 > 全局设置；按模型 id 自适配档位（[INVARIANT-AGENTIC] 规定 2 参数面）
        val depth = (model.thinkingDepth ?: settings.thinkingDepth)
            ?.let { stored -> runCatching { enumValueOf<ThinkingDepth>(stored) }.getOrNull() }
        val depthFragment = ThinkingDepthAdapter.requestBodyFragment(model.id, depth)
        val mergedExtraBody = com.luzzymeow.luzzyrp.core.ai.util.mergeJsonBodies(
            provider.extraBodyJson,
            depthFragment.toString(),
        )

        val params = TextGenerationParams(
            model = model,
            temperature = model.temperature ?: settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            tools = emptyList(),   // Handler 按阶段填充
            toolChoice = com.luzzymeow.luzzyrp.core.ai.params.ToolChoice.AUTO,  // [INVARIANT-AGENTIC] A5
            thinking = ThinkingConfig(enabled = settings.thinkingEnabled, effort = settings.thinkingEffort),
            customBody = mergedExtraBody,
        )
        logger.info(AppLogger.CAT_MODEL, "请求组装",
            "模型=${model.id} 思考深度=$depth 消息数=${requestMessages.size} 温度=${params.temperature}")

        // —— 生成循环 ——
        val handler = GenerationHandler(providerManager)
        var lastPersist = 0L
        var completed = false

        handler.generate(
            GenerationHandler.Request(
                setting = provider,
                messages = requestMessages,
                params = params,
                tools = toolRegistry,
                maxToolRounds = RP_MAX_TOOL_ROUNDS,   // 两阶段闭环 maxLoops = 3
                tagFallback = tagFallback,
            )
        ).collect { event ->
            when (event) {
                is GenerationHandler.GenerationEvent.Messages -> {
                    // [INVARIANT-STREAMING] 1 字 = 1 次更新：直写单一真源
                    persistToFlow(conversationId, fromNodeId, event.messages)
                    // 节流落库（仅 DB 写节流；StateFlow 仍逐字更新）
                    val now = System.currentTimeMillis()
                    if (now - lastPersist > 800) {
                        lastPersist = now
                        persistToDb(conversationId, fromNodeId, event.messages)
                    }
                }

                is GenerationHandler.GenerationEvent.ToolRound -> {
                    logger.info(AppLogger.CAT_TOOL, "工具轮次完成", "round=${event.round}")
                    persistToDb(conversationId, fromNodeId, event.messages)
                }

                is GenerationHandler.GenerationEvent.ApprovalNeeded -> {
                    persistToDb(conversationId, fromNodeId, event.messages)
                }

                is GenerationHandler.GenerationEvent.Completed -> {
                    completed = true
                    logger.info(AppLogger.CAT_MODEL, "生成完成",
                        "finish=${event.finishReason} 消息数=${event.messages.size}")
                    persistToDb(conversationId, fromNodeId, event.messages)
                    postTurn(conversationId, event.messages.filter { it.role != Role.SYSTEM }, settings)
                }

                is GenerationHandler.GenerationEvent.Failed -> {
                    logger.error(AppLogger.CAT_MODEL, "生成失败", event.error.stackTraceToString().take(4000))
                    pushError(conversationId, event.error.message ?: "生成失败：${event.error.javaClass.simpleName}")
                }
            }
        }
    }

    /** 审批后的续跑：以当前 Flow 内的消息重建请求（Handler 会先回填已批准工具）。 */
    private fun resumeGeneration(conversationId: String) {
        val job = appScope.launch(exceptionHandler) {
            runGeneration(conversationId, fromNodeId = null)
        }
        generationJobs[conversationId] = job
        _generatingIds.value = _generatingIds.value + conversationId
        job.invokeOnCompletion {
            if (generationJobs[conversationId] == job) {
                generationJobs.remove(conversationId)
                _generatingIds.value = _generatingIds.value - conversationId
            }
        }
    }

    // ------------------------------------------------------------------
    // 状态/持久化辅助
    // ------------------------------------------------------------------

    /** 把 Handler 的消息列表（含 system）映射回会话 Flow：过滤 system，保留 user/assistant。 */
    private fun persistToFlow(conversationId: String, nodeId: String?, handlerMessages: List<UIMessage>) {
        val visible = handlerMessages.filter { it.role != Role.SYSTEM }
        updateConversation(conversationId) { conversation ->
            replaceNodeMessages(conversation, nodeId, visible)
        }
    }

    private suspend fun persistToDb(conversationId: String, nodeId: String?, handlerMessages: List<UIMessage>) {
        val visible = handlerMessages.filter { it.role != Role.SYSTEM }
        persistMutex.withLock {
            val flow = sessionFlow(conversationId)
            val conversation = flow.value ?: return@withLock
            val targetNode = nodeId ?: conversation.currentNodeId
            targetNode?.let { conversationRepository.updateNodeMessages(conversationId, it, visible) }
        }
    }

    /** 替换当前（或指定）节点的消息序列。 */
    private fun replaceNodeMessages(conversation: Conversation, nodeId: String?, messages: List<UIMessage>): Conversation {
        val target = nodeId ?: conversation.currentNodeId ?: return conversation
        val nodes = conversation.nodes.map { node ->
            if (node.id == target) node.copy(messages = messages) else node
        }
        return conversation.copy(nodes = nodes)
    }

    private suspend fun updateToolApprovalState(conversationId: String, toolCallId: String, state: ToolApprovalState) {
        fun List<UIMessage>.updated(): List<UIMessage> = map { message ->
            message.copy(parts = message.parts.map { part ->
                if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                    part.copy(approvalState = state)
                } else part
            })
        }
        // Flow 内更新
        updateConversation(conversationId) { conversation ->
            conversation.copy(nodes = conversation.nodes.map { node -> node.copy(messages = node.messages.updated()) })
        }
        // 落库
        val conversation = conversationRepository.getConversation(conversationId) ?: return
        conversation.nodes.forEach { node ->
            nodeDaoUpdateIfChanged(conversationId, node.id, node.messages.updated())
        }
    }

    private suspend fun nodeDaoUpdateIfChanged(conversationId: String, nodeId: String, messages: List<UIMessage>) {
        conversationRepository.updateNodeMessages(conversationId, nodeId, messages)
    }

    private var lastRememberedId = ""
    private suspend fun rememberLastConversation(conversationId: String) {
        if (lastRememberedId == conversationId) return
        lastRememberedId = conversationId
        runCatching { settingsStore.update { it.copy(lastConversationId = conversationId) } }
    }

    private fun currentProvider(settings: Settings): ProviderSetting? =
        settings.providers.firstOrNull { it.id == settings.currentProviderId && it.apiKey.isNotBlank() }
            ?: settings.providers.firstOrNull { it.apiKey.isNotBlank() }

    private fun currentModel(settings: Settings, provider: ProviderSetting): Model? =
        provider.models.firstOrNull { it.id == settings.currentModelId }
            ?: provider.models.firstOrNull { !it.capabilities.embedding }

    private suspend fun embedText(settings: Settings, text: String): FloatArray? {
        if (settings.embeddingProviderId.isBlank() || settings.embeddingModelId.isBlank()) return null
        if (text.isBlank()) return null
        val provider = settings.providers.firstOrNull { it.id == settings.embeddingProviderId } ?: return null
        val model = provider.models.firstOrNull { it.id == settings.embeddingModelId } ?: return null
        return runCatching {
            providerManager.generateEmbedding(provider, listOf(text.take(2000))).firstOrNull()
        }.getOrNull()
    }

    private fun pushError(conversationId: String, message: String) {
        _errors.value = _errors.value + ChatError(conversationId, message)
    }

    fun dismissError(error: ChatError) {
        _errors.value = _errors.value - error
    }

    // ------------------------------------------------------------------
    // 后处理链（异步，不阻塞回复）：标题 / 三级摘要 / ACE Reflect+Update
    // ------------------------------------------------------------------

    private fun postTurn(
        conversationId: String,
        roundMessages: List<UIMessage>,
        settings: Settings,
    ) {
        appScope.launch(exceptionHandler) {
            runCatching { ensureTitle(conversationId, roundMessages) }
        }
        if (settings.summaryEnabled) {
            appScope.launch(exceptionHandler) {
                runCatching { runSummaries(conversationId, roundMessages, settings) }
            }
        }
        appScope.launch(exceptionHandler) {
            if (conversationEnableMemory(conversationId) && settings.memoryEnabled) {
                runCatching { runAceCycle(conversationId, roundMessages, settings) }
            }
        }
    }

    private suspend fun conversationEnableMemory(conversationId: String): Boolean =
        conversationRepository.getConversation(conversationId)?.enableMemory ?: true

    private suspend fun ensureTitle(conversationId: String, roundMessages: List<UIMessage>) {
        val conversation = conversationRepository.getConversation(conversationId) ?: return
        if (conversation.title != "新对话") return
        val settings = settingsStore.settingsFlow.first()
        val provider = currentProvider(settings) ?: return
        val model = currentModel(settings, provider) ?: return
        val roundText = roundMessages.joinToString("\n") { it.textContent() }.take(1200)
        val chunk = providerManager.generateText(
            provider,
            listOf(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(TaskPrompts.title(roundText))))),
            TextGenerationParams(model = model, maxTokens = 32, temperature = 0.7),
        )
        val title = chunk.choices.firstOrNull()?.message?.textContent()?.trim()?.take(24).orEmpty()
        if (title.isNotBlank()) conversationRepository.rename(conversationId, title)
    }

    /** 三级摘要调度：A 每轮 ≤50 / B 每 10 轮 ≤10 / C 每 50 轮永久。 */
    private suspend fun runSummaries(conversationId: String, roundMessages: List<UIMessage>, settings: Settings) {
        val provider = currentProvider(settings) ?: return
        val model = currentModel(settings, provider) ?: return
        val conversation = conversationRepository.getConversation(conversationId) ?: return
        val counters = conversation.summaryCounters
        val newTotal = counters.totalRounds + 1
        val roundText = roundMessages.takeLast(2).joinToString("\n\n") { message ->
            when (message.role) {
                Role.USER -> "用户：" + message.textContent()
                Role.ASSISTANT -> "角色：" + message.textContent()
                else -> message.textContent()
            }
        }.take(4000)

        suspend fun oneShot(prompt: String, maxTokens: Long): String {
            val chunk = providerManager.generateText(
                provider,
                listOf(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(prompt)))),
                TextGenerationParams(model = model, maxTokens = maxTokens, temperature = 0.3),
            )
            return chunk.choices.firstOrNull()?.message?.textContent()?.trim().orEmpty()
        }

        // —— A 级：每轮 ——
        val previousA = summaryRepository.getByLevel(conversationId, SummaryLevel.A)
        val aText = oneShot(TaskPrompts.summaryA(previousA.lastOrNull()?.content.orEmpty(), roundText), 128)
        if (aText.isNotBlank()) {
            summaryRepository.upsert(
                SummaryItem(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    level = SummaryLevel.A,
                    roundIndex = newTotal,
                    content = aText.take(SummaryRepository.A_MAX_CHARS),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

        // —— B 级：每 10 轮合并最近 A 级 ——
        var bCount = counters.bCount
        var lastB = counters.lastBAtRound
        if (newTotal % SummaryRepository.B_INTERVAL_ROUNDS == 0) {
            val pendingA = summaryRepository.getByLevel(conversationId, SummaryLevel.A)
                .filter { it.roundIndex > lastB }
            if (pendingA.isNotEmpty()) {
                val bText = oneShot(TaskPrompts.summaryB(pendingA.map { it.content }), 512)
                val items = bText.lines().filter { it.isNotBlank() }
                    .take(SummaryRepository.B_MAX_ITEMS)
                items.forEachIndexed { index, line ->
                    summaryRepository.upsert(
                        SummaryItem(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            level = SummaryLevel.B,
                            roundIndex = newTotal + index,
                            content = line.trimStart('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ' ', '、'),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                // 已合并的 A 级删除（避免无限膨胀；信息已进入 B 级）
                summaryRepository.deleteByRounds(conversationId, SummaryLevel.A, pendingA.map { it.roundIndex })
                lastB = newTotal
                bCount += 1
            }
        }

        // —— C 级：每 50 轮固化 ——
        var cCount = counters.cCount
        var lastC = counters.lastCAtRound
        if (newTotal % SummaryRepository.C_INTERVAL_ROUNDS == 0) {
            val pendingB = summaryRepository.getByLevel(conversationId, SummaryLevel.B)
                .filter { it.roundIndex > lastC }
            if (pendingB.isNotEmpty()) {
                val cText = oneShot(TaskPrompts.summaryC(pendingB.map { it.content }), 512)
                val items = cText.lines().filter { it.isNotBlank() }.take(8)
                items.forEachIndexed { index, line ->
                    summaryRepository.upsert(
                        SummaryItem(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            level = SummaryLevel.C,
                            roundIndex = newTotal + index,
                            content = line.trimStart('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ' ', '、'),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                lastC = newTotal
                cCount += 1
            }
        }

        // 计数器落库
        val updated = conversation.copy(
            summaryCounters = SummaryCounters(
                totalRounds = newTotal,
                aCount = counters.aCount + 1,
                bCount = bCount,
                cCount = cCount,
                lastBAtRound = lastB,
                lastCAtRound = lastC,
            ),
        )
        conversationRepository.saveConversation(updated)
        sessionFlow(conversationId).value = updated
    }

    /** ACE 循环：Reflect（反馈计数）+ Update（提取新事实 + 去重入库）。 */
    private suspend fun runAceCycle(conversationId: String, roundMessages: List<UIMessage>, settings: Settings) {
        val provider = currentProvider(settings) ?: return
        val model = currentModel(settings, provider) ?: return
        val roundText = roundMessages.takeLast(2).joinToString("\n") { it.textContent() }.take(3000)
        val injected = memoryRepository.recall(roundText, null, settings.memoryTopK)

        suspend fun oneShot(prompt: String, maxTokens: Long): String {
            val chunk = providerManager.generateText(
                provider,
                listOf(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text(prompt)))),
                TextGenerationParams(model = model, maxTokens = maxTokens, temperature = 0.2),
            )
            return chunk.choices.firstOrNull()?.message?.textContent()?.trim().orEmpty()
        }

        // Reflect：对注入记忆逐条判定
        if (injected.isNotEmpty()) {
            val verdictText = oneShot(
                TaskPrompts.aceReflect(injected.map { it.content }, roundText), 512,
            )
            runCatching {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(verdictText) as? kotlinx.serialization.json.JsonArray
                arr?.forEach { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val index = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return@forEach
                    val verdict = (obj["verdict"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@forEach
                    val target = injected.getOrNull(index - 1) ?: return@forEach
                    when (verdict.lowercase()) {
                        "helpful" -> memoryRepository.recordFeedback(target.id, MemoryRepository.Feedback.HELPFUL)
                        "harmful" -> memoryRepository.recordFeedback(target.id, MemoryRepository.Feedback.HARMFUL)
                        else -> memoryRepository.recordFeedback(target.id, MemoryRepository.Feedback.NEUTRAL)
                    }
                }
            }
        }

        // Update：提取新事实 + 嵌入去重
        val existing = memoryRepository.getActiveContents()
        val extractText = oneShot(TaskPrompts.aceExtract(roundText, existing), 512)
        runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(extractText) as? kotlinx.serialization.json.JsonArray
            arr?.forEach { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val content = (obj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@forEach
                val category = (obj["category"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "其他"
                val embedding = embedText(settings, content)
                memoryRepository.upsertWithDedup(
                    content = content,
                    category = category,
                    embedding = embedding,
                    sourceConversationId = conversationId,
                    threshold = settings.memoryDedupThreshold,
                )
            }
        }
    }

    companion object {
        /** RP 两阶段闭环：主动工具轮次上限（maxLoops = 3）。 */
        const val RP_MAX_TOOL_ROUNDS = 3
    }
}

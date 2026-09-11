package com.luzzymeow.luzzyrp.assistant.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntimeProvider
import com.luzzymeow.luzzyrp.assistant.ui.chat.AssistantChatViewModel
import com.luzzymeow.luzzyrp.assistant.ui.chat.AssistantListViewModel
import com.luzzymeow.luzzyrp.assistant.ui.mcp.McpViewModel
import com.luzzymeow.luzzyrp.assistant.ui.memory.MemoryViewModel
import com.luzzymeow.luzzyrp.assistant.ui.model.AssistantUi
import com.luzzymeow.luzzyrp.assistant.ui.model.ConversationUi
import com.luzzymeow.luzzyrp.assistant.ui.screen.ChatScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.ConversationsScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.McpScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.MemoryScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.SettingsScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.SkillsScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.TerminalScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.WorkspaceScreen
import com.luzzymeow.luzzyrp.assistant.ui.settings.SettingsViewModel
import com.luzzymeow.luzzyrp.assistant.ui.skill.SkillsViewModel
import com.luzzymeow.luzzyrp.assistant.ui.terminal.TerminalViewModel
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyAssistantTheme
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyMotion
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme
import com.luzzymeow.luzzyrp.assistant.ui.workspace.WorkspaceViewModel

/**
 * 助手原生页根组件（v1.5.0，方向 A · 卷宗）。
 *
 * 宿主形态：同 Activity 原生覆盖层（PLAN §3.1，用户已选 D1-B）——由 MainActivity 懒创建
 * ComposeView 承载，WebView 保持存活。
 *
 * 导航（用户 2026-09-09 改稿）：**首页 = LuzzyRP 聊天页版式**（深色渐隐顶栏 + 消息流 + 输入岛），
 * 顶栏右上角为助手专属设置按钮（上游同位置是「清空聊天」）；会话列表 / 助手切换 / 功能入口
 * 收进左侧抽屉。转场：内容右移 12dp + 淡入 200ms / 返回 140ms（DESIGN.md 动效纪律）。
 */
@Composable
fun AssistantApp(
    darkTheme: Boolean,
    /** RP 侧栏「助手」子项传入的初始页面（空串 = 首页聊天页版式）。 */
    initialRoute: String = "",
    onExit: () -> Unit,
    /** 首页左上角汉堡 → 回到 LuzzyRP 原侧栏（用户 2026-09-09 指定）。 */
    onOpenRpSidebar: () -> Unit = onExit,
) {
    LuzzyAssistantTheme(darkTheme = darkTheme) {
        AssistantRoot(
            initialRoute = initialRoute,
            onExit = onExit,
            onOpenRpSidebar = onOpenRpSidebar,
        )
    }
}

@Composable
private fun AssistantRoot(
    initialRoute: String,
    onExit: () -> Unit,
    onOpenRpSidebar: () -> Unit,
) {
    // 首页 = 聊天页版式（用户 2026-09-09 改稿）；会话/管理页入口在 LuzzyRP 原侧栏的「助手」子项组。
    var route by remember { mutableStateOf<AssistantRoute>(AssistantRoute.fromSidebarRoute(initialRoute)) }
    // 侧栏子项再次进入时切换页面（覆盖层复用同一 ComposeView）
    LaunchedEffect(initialRoute) {
        val target = AssistantRoute.fromSidebarRoute(initialRoute)
        if (target != AssistantRoute.ChatList) route = target
    }

    val context = LocalContext.current
    val runtime = remember(context) { AssistantRuntimeProvider.get(context) }
    val listFactory = remember(runtime) {
        viewModelFactory { initializer { AssistantListViewModel(runtime) } }
    }
    val listVm: AssistantListViewModel = viewModel(factory = listFactory)
    val listState by listVm.state.collectAsStateWithLifecycle()

    val assistants = listState.assistants
    val selectedAssistant = assistants.firstOrNull { it.id == listState.selectedId }
    val conversations = listState.conversations

    // 首页承载的会话：加载完成后固定一次（避免流式过程中因列表刷新而重建 ViewModel）
    var homeConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(listState.loading, conversations.firstOrNull()?.id) {
        if (homeConversationId == null && !listState.loading) {
            homeConversationId = conversations.firstOrNull()?.id
                ?: AssistantChatViewModel.NEW_CONVERSATION_ID
        }
    }

    // 返回键优先级：二级页 → 退出助手层（PLAN §2.3）
    BackHandler(enabled = true) {
        if (route != AssistantRoute.ChatList) route = AssistantRoute.ChatList else onExit()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState != AssistantRoute.ChatList
                val enter = slideInHorizontally(
                    animationSpec = tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut),
                    initialOffsetX = { full -> if (forward) full / 24 else -full / 24 },
                ) + fadeIn(tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut))
                val exit = slideOutHorizontally(
                    animationSpec = tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut),
                    targetOffsetX = { full -> if (forward) -full / 24 else full / 24 },
                ) + fadeOut(tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut))
                enter togetherWith exit
            },
            label = "assistant-route",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                AssistantRoute.ChatList -> {
                    val targetId = homeConversationId
                    if (targetId == null) {
                        Box(modifier = Modifier.fillMaxSize().background(LuzzyTheme.colors.canvas))
                    } else {
                        ChatPane(
                            runtime = runtime,
                            assistant = selectedAssistant,
                            conversationId = targetId,
                            conversation = conversations.firstOrNull { it.id == targetId },
                            onOpenRpSidebar = onOpenRpSidebar,
                            onOpenConversations = { route = AssistantRoute.Conversations },
                            onOpenSettings = { route = AssistantRoute.Settings },
                            onConversationResolved = { homeConversationId = it },
                        )
                    }
                }

                AssistantRoute.Conversations -> ConversationsScreen(
                    assistants = assistants,
                    selectedAssistant = selectedAssistant,
                    conversations = conversations,
                    onSelectAssistant = listVm::select,
                    // 扁平化（2026-09-11）：点会话 = 切到同为一级入口的「对话」页并打开它，
                    // 不再推进出「会话 → 对话」这种二级关系。
                    onOpenConversation = { id ->
                        homeConversationId = id
                        route = AssistantRoute.ChatList
                    },
                    onNewConversation = {
                        listVm.createConversation { id ->
                            homeConversationId = id
                            route = AssistantRoute.ChatList
                        }
                    },
                    // 助手管理已并入本页（折叠卡），不再有独立二级页
                    onCreateAssistant = { listVm.createAssistant("新助手") },
                    onDeleteAssistant = { id -> listVm.deleteAssistant(id) },
                    onMenu = onOpenRpSidebar,
                )

                AssistantRoute.Memory -> {
                    val memoryVm: MemoryViewModel = viewModel(
                        key = "memory-${selectedAssistant?.id}",
                        factory = viewModelFactory {
                            initializer { MemoryViewModel(runtime, selectedAssistant?.id.orEmpty()) }
                        },
                    )
                    val memoryState by memoryVm.state.collectAsStateWithLifecycle()
                    MemoryScreen(
                        memories = memoryState.memories,
                        modeLabel = memoryState.modeLabel,
                        topK = memoryState.topK,
                        threshold = memoryState.threshold,
                        recent = memoryState.recent,
                        onMenu = onOpenRpSidebar,
                        onAdd = memoryVm::addMemory,
                        onDelete = memoryVm::deleteMemory,
                    )
                }

                AssistantRoute.Skills -> {
                    val skillsVm: SkillsViewModel = viewModel(
                        key = "skills-${selectedAssistant?.id}",
                        factory = viewModelFactory {
                            initializer { SkillsViewModel(runtime, selectedAssistant?.id.orEmpty()) }
                        },
                    )
                    val skillsState by skillsVm.state.collectAsStateWithLifecycle()
                    SkillsScreen(
                        skills = skillsState.skills,
                        message = skillsState.message,
                        onToggleGlobal = skillsVm::toggleGlobal,
                        onToggleBinding = skillsVm::toggleBinding,
                        onDelete = skillsVm::delete,
                        onDismissMessage = skillsVm::dismissMessage,
                        onImportUrl = skillsVm::importFromUrl,
                        onMenu = onOpenRpSidebar,
                    )
                }

                AssistantRoute.Mcp -> {
                    val mcpVm: McpViewModel = viewModel(
                        factory = viewModelFactory { initializer { McpViewModel(runtime) } },
                    )
                    val mcpState by mcpVm.state.collectAsStateWithLifecycle()
                    McpScreen(
                        servers = mcpState.servers,
                        message = mcpState.message,
                        onImport = mcpVm::importJson,
                        onToggle = mcpVm::setEnabled,
                        onConnect = mcpVm::connect,
                        onDelete = mcpVm::delete,
                        onDismissMessage = mcpVm::dismissMessage,
                        onMenu = onOpenRpSidebar,
                    )
                }

                AssistantRoute.Workspace -> {
                    val wsVm: WorkspaceViewModel = viewModel(
                        key = "ws-${selectedAssistant?.id}",
                        factory = viewModelFactory {
                            initializer { WorkspaceViewModel(runtime, selectedAssistant?.id.orEmpty()) }
                        },
                    )
                    val wsState by wsVm.state.collectAsStateWithLifecycle()
                    WorkspaceScreen(
                        currentPath = wsState.currentPath,
                        entries = wsState.entries,
                        usageLabel = wsState.usageLabel,
                        preview = wsState.preview,
                        message = wsState.message,
                        onEnter = wsVm::enter,
                        onUp = wsVm::up,
                        onPreview = wsVm::preview,
                        onDelete = wsVm::delete,
                        onClosePreview = wsVm::closePreview,
                        onDismissMessage = wsVm::dismissMessage,
                        onMenu = onOpenRpSidebar,
                    )
                }

                AssistantRoute.Terminal -> {
                    val termVm: TerminalViewModel = viewModel(
                        key = "term-${selectedAssistant?.id}",
                        factory = viewModelFactory {
                            initializer { TerminalViewModel(runtime, selectedAssistant?.id.orEmpty()) }
                        },
                    )
                    val termState by termVm.state.collectAsStateWithLifecycle()
                    TerminalScreen(
                        lines = termState.lines,
                        running = termState.running,
                        mode = termState.mode,
                        modeLabel = termState.modeLabel,
                        banner = termState.banner,
                        lastExitCode = termState.lastExitCode,
                        installing = termState.installing,
                        installProgress = termState.installProgress,
                        onRun = termVm::run,
                        onClear = termVm::clear,
                        onSetMode = termVm::setMode,
                        onMenu = onOpenRpSidebar,
                    )
                }

                AssistantRoute.Settings -> {
                    val settingsVm: SettingsViewModel = viewModel(
                        key = "settings-${selectedAssistant?.id}",
                        factory = viewModelFactory {
                            initializer { SettingsViewModel(runtime, selectedAssistant?.id.orEmpty()) }
                        },
                    )
                    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = settingsState,
                        onName = settingsVm::updateName,
                        onPrompt = settingsVm::updateSystemPrompt,
                        onModel = settingsVm::updateModelRef,
                        onTemperature = settingsVm::updateTemperature,
                        onTopP = settingsVm::updateTopP,
                        onMaxTokens = settingsVm::updateMaxTokens,
                        onExtraBody = settingsVm::updateExtraBody,
                        onMemoryMode = settingsVm::updateMemoryMode,
                        onSearchProvider = settingsVm::updateSearchProvider,
                        onSearxngUrl = settingsVm::updateSearxngUrl,
                        onTavilyKey = settingsVm::updateTavilyKey,
                        onBraveKey = settingsVm::updateBraveKey,
                        onSaveSearch = settingsVm::saveSearchSettings,
                        onToggleTool = settingsVm::setToolEnabled,
                        onTogglePreview = settingsVm::togglePreview,
                        onSave = settingsVm::save,
                        onDismissMessage = settingsVm::dismissMessage,
                        onClearAudit = settingsVm::clearAudit,
                        onMenu = onOpenRpSidebar,
                    )
                }
            }
        }

    }
}

/** 聊天面板（首页与具体会话共用，避免两处重复）。 */
@Composable
private fun ChatPane(
    runtime: AssistantRuntime,
    assistant: AssistantUi?,
    conversationId: String,
    conversation: ConversationUi?,
    onOpenRpSidebar: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenSettings: () -> Unit,
    onConversationResolved: (String) -> Unit,
) {
    val chatFactory = remember(runtime, conversationId) {
        viewModelFactory {
            initializer {
                AssistantChatViewModel(
                    runtime = runtime,
                    assistantId = assistant?.id.orEmpty(),
                    conversationId = conversationId,
                    assistantName = assistant?.name ?: "助手",
                    systemPrompt = "",
                    workspacePath = "files/",
                    onConversationResolved = onConversationResolved,
                )
            }
        }
    }
    val chatVm: AssistantChatViewModel = viewModel(
        key = "chat-$conversationId",
        factory = chatFactory,
    )
    val chatState by chatVm.state.collectAsStateWithLifecycle()

    ChatScreen(
        assistant = assistant,
        conversation = conversation,
        messages = chatState.messages,
        streaming = chatState.streaming,
        pendingApproval = chatState.pendingApproval,
        pendingQuestion = chatState.pendingQuestion,
        error = chatState.error,
        onOpenSidebar = onOpenRpSidebar,
        onOpenSettings = onOpenSettings,
        onOpenConversationInfo = onOpenConversations,
        onSend = chatVm::send,
        onStop = chatVm::stop,
        onApprove = chatVm::approve,
        onDeny = chatVm::deny,
        onAnswer = chatVm::answer,
        onDismissError = chatVm::dismissError,
    )
}

/** 系统分享（导出会话：写工作区 + 分享给任意应用，用户可另存）。 */
private fun shareText(context: Context, fileName: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "导出会话").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
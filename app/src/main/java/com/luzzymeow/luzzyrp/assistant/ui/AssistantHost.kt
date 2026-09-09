package com.luzzymeow.luzzyrp.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntimeProvider
import com.luzzymeow.luzzyrp.assistant.ui.chat.AssistantChatViewModel
import com.luzzymeow.luzzyrp.assistant.ui.chat.AssistantListViewModel
import com.luzzymeow.luzzyrp.assistant.ui.mcp.McpViewModel
import com.luzzymeow.luzzyrp.assistant.ui.memory.MemoryViewModel
import com.luzzymeow.luzzyrp.assistant.ui.screen.McpScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.SettingsScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.TerminalScreen
import com.luzzymeow.luzzyrp.assistant.ui.settings.SettingsViewModel
import com.luzzymeow.luzzyrp.assistant.ui.screen.WorkspaceScreen
import com.luzzymeow.luzzyrp.assistant.ui.terminal.TerminalViewModel
import com.luzzymeow.luzzyrp.assistant.ui.workspace.WorkspaceViewModel
import com.luzzymeow.luzzyrp.assistant.ui.screen.SkillsScreen
import com.luzzymeow.luzzyrp.assistant.ui.skill.SkillsViewModel
import com.luzzymeow.luzzyrp.assistant.ui.component.SideDrawerContent
import com.luzzymeow.luzzyrp.assistant.ui.model.SampleData
import com.luzzymeow.luzzyrp.assistant.ui.screen.AssistantManagerScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.ChatListScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.ChatScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.MemoryScreen
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyAssistantTheme
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyMotion

/**
 * 助手原生页根组件（v1.5.0，方向 A · 卷宗）。
 *
 * 宿主形态：同 Activity 原生覆盖层（PLAN §3.1，用户已选 D1-B）——由 MainActivity 懒创建
 * `ComposeView` 承载，WebView 保持存活。
 *
 * 导航（方向 A）：**无一级导航**，会话列表即家；记忆/技能/MCP/工作区/终端/设置收进右侧抽屉。
 * 转场：内容右移 12dp + 淡入 200ms / 返回 140ms（DESIGN.md 动效纪律）。
 *
 * 数据：P0 用 [SampleData] 驱动骨架；P1 起由 ViewModel 把 Room 实体映射为同构 UI 模型，
 * 本文件签名不变。
 */
@Composable
fun AssistantApp(
    darkTheme: Boolean,
    onExit: () -> Unit,
) {
    LuzzyAssistantTheme(darkTheme = darkTheme) {
        AssistantRoot(onExit = onExit)
    }
}

@Composable
private fun AssistantRoot(onExit: () -> Unit) {
    var route by remember { mutableStateOf<AssistantRoute>(AssistantRoute.ChatList) }
    var drawerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val runtime = remember(context) { AssistantRuntimeProvider.get(context) }
    val listVm: AssistantListViewModel = viewModel(
        factory = viewModelFactory { initializer { AssistantListViewModel(runtime) } },
    )
    val listState by listVm.state.collectAsStateWithLifecycle()

    val assistants = listState.assistants
    val selectedAssistant = assistants.firstOrNull { it.id == listState.selectedId }
    val conversations = listState.conversations

    // 返回键优先级：抽屉 → 二级页 → 退出助手层（PLAN §2.3）
    BackHandler(enabled = true) {
        when {
            drawerOpen -> drawerOpen = false
            route != AssistantRoute.ChatList -> route = AssistantRoute.ChatList
            else -> onExit()
        }
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
                AssistantRoute.ChatList -> ChatListScreen(
                    assistants = assistants,
                    selectedAssistant = selectedAssistant,
                    conversations = conversations,
                    onSelectAssistant = listVm::select,
                    onOpenManager = { route = AssistantRoute.AssistantManager },
                    onOpenConversation = { route = AssistantRoute.Chat(it) },
                    onOpenDrawer = { drawerOpen = true },
                    onNewConversation = { listVm.createConversation { id -> route = AssistantRoute.Chat(id) } },
                )

                is AssistantRoute.Chat -> {
                    val chatVm: AssistantChatViewModel = viewModel(
                        key = "chat-${current.conversationId}",
                        factory = viewModelFactory {
                            initializer {
                                AssistantChatViewModel(
                                    runtime = runtime,
                                    assistantId = selectedAssistant?.id.orEmpty(),
                                    conversationId = current.conversationId,
                                    assistantName = selectedAssistant?.name ?: "助手",
                                    systemPrompt = "",
                                    workspacePath = "files/",
                                )
                            }
                        },
                    )
                    val chatState by chatVm.state.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()
                    ChatScreen(
                        assistant = selectedAssistant,
                        conversation = conversations.firstOrNull { it.id == current.conversationId },
                        messages = chatState.messages,
                        streaming = chatState.streaming,
                        pendingApproval = chatState.pendingApproval,
                        pendingQuestion = chatState.pendingQuestion,
                        error = chatState.error,
                        onBack = { route = AssistantRoute.ChatList },
                        onOpenDrawer = { drawerOpen = true },
                        onSend = chatVm::send,
                        onStop = chatVm::stop,
                        onApprove = chatVm::approve,
                        onDeny = chatVm::deny,
                        onAnswer = chatVm::answer,
                        onDismissError = chatVm::dismissError,
                        onExport = { asJson ->
                            scope.launch {
                                val text = runCatching {
                                    runtime.repository.exportConversation(current.conversationId, asJson)
                                }.getOrDefault("")
                                if (text.isNotBlank()) {
                                    val name = if (asJson) "conversation.json" else "conversation.md"
                                    shareText(context, name, text)
                                }
                            }
                        },
                    )
                }

                AssistantRoute.AssistantManager -> AssistantManagerScreen(
                    assistants = assistants,
                    onBack = { route = AssistantRoute.ChatList },
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
                        onBack = { route = AssistantRoute.ChatList },
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
                        onBack = { route = AssistantRoute.ChatList },
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
                        onBack = { route = AssistantRoute.ChatList },
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
                        onBack = { route = AssistantRoute.ChatList },
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
                        onBack = { route = AssistantRoute.ChatList },
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
                        onTogglePreview = settingsVm::togglePreview,
                        onSave = settingsVm::save,
                        onDismissMessage = settingsVm::dismissMessage,
                        onClearAudit = settingsVm::clearAudit,
                        onBack = { route = AssistantRoute.ChatList },
                    )
                }
            }
        }

        // 抽屉（右滑 12dp + 淡入 200ms / 关闭 140ms）
        if (drawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable { drawerOpen = false }
            )
            AnimatedContent(
                targetState = drawerOpen,
                transitionSpec = {
                    slideInHorizontally(
                        animationSpec = tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut),
                        initialOffsetX = { it / 8 },
                    ) + fadeIn(tween(LuzzyMotion.ENTER_MS, easing = LuzzyMotion.EaseOut)) togetherWith
                        slideOutHorizontally(
                            animationSpec = tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut),
                            targetOffsetX = { it / 8 },
                        ) + fadeOut(tween(LuzzyMotion.EXIT_MS, easing = LuzzyMotion.EaseOut))
                },
                label = "assistant-drawer",
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(),
            ) { visible ->
                if (visible) {
                    SideDrawerContent(
                        entries = AssistantRoute.drawerEntries,
                        assistantName = selectedAssistant?.name ?: "助手",
                        modelLabel = selectedAssistant?.modelLabel ?: "未配置模型",
                        onSelect = { entry -> route = entry.route },
                        onClose = { drawerOpen = false },
                    )
                }
            }
        }
    }
}

/** 系统分享（导出会话：写工作区 + 分享给任意应用，用户可另存）。 */
private fun shareText(context: Context, fileName: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "导出会话").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

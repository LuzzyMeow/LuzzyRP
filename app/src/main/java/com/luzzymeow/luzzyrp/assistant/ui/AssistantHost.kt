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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luzzymeow.luzzyrp.assistant.ui.component.SideDrawerContent
import com.luzzymeow.luzzyrp.assistant.ui.model.SampleData
import com.luzzymeow.luzzyrp.assistant.ui.screen.AssistantManagerScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.ChatListScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.ChatScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.MemoryScreen
import com.luzzymeow.luzzyrp.assistant.ui.screen.PlaceholderScreen
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
    var selectedAssistantId by remember { mutableStateOf(SampleData.assistants.first().id) }

    val assistants = SampleData.assistants
    val selectedAssistant = assistants.firstOrNull { it.id == selectedAssistantId } ?: assistants.first()
    val conversations = SampleData.conversations.filter { it.assistantId == selectedAssistantId }

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
                    onSelectAssistant = { selectedAssistantId = it },
                    onOpenManager = { route = AssistantRoute.AssistantManager },
                    onOpenConversation = { route = AssistantRoute.Chat(it) },
                    onOpenDrawer = { drawerOpen = true },
                    onNewConversation = { route = AssistantRoute.Chat("new") },
                )

                is AssistantRoute.Chat -> ChatScreen(
                    assistant = selectedAssistant,
                    conversation = conversations.firstOrNull { it.id == current.conversationId },
                    messages = SampleData.messages,
                    onBack = { route = AssistantRoute.ChatList },
                    onOpenDrawer = { drawerOpen = true },
                    onSend = { /* P1：接入 AgentLoop */ },
                )

                AssistantRoute.AssistantManager -> AssistantManagerScreen(
                    assistants = assistants,
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Memory -> MemoryScreen(
                    memories = SampleData.memories,
                    modeLabel = SampleData.MEMORY_MODE_LABEL,
                    topK = SampleData.MEMORY_TOPK,
                    threshold = SampleData.MEMORY_THRESHOLD,
                    recent = SampleData.MEMORY_RECENT,
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Skills -> PlaceholderScreen(
                    title = "技能",
                    note = "Markdown + front-matter（name / description / tools）；全局启用 + 助手绑定。",
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Mcp -> PlaceholderScreen(
                    title = "MCP",
                    note = "HTTP(Streamable) / SSE 传输；JSON 导入 + 逐条预览 + 可达性检测。",
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Workspace -> PlaceholderScreen(
                    title = "工作区",
                    note = "每助手独立 files / attachments / exports；配额 2GB、单文件 64MB；路径越界拒绝。",
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Terminal -> PlaceholderScreen(
                    title = "终端",
                    note = "沙盒（proot 真 Linux）/ 全局（宿主）双模式；危险命令 HARDLINE 无条件拦截。",
                    onBack = { route = AssistantRoute.ChatList },
                )

                AssistantRoute.Settings -> PlaceholderScreen(
                    title = "设置",
                    note = "提示词与模型 / 参数 / 请求体扩展 / 预览最终请求（密钥脱敏）。",
                    onBack = { route = AssistantRoute.ChatList },
                )
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
                        assistantName = selectedAssistant.name,
                        modelLabel = selectedAssistant.modelLabel,
                        onSelect = { entry -> route = entry.route },
                        onClose = { drawerOpen = false },
                    )
                }
            }
        }
    }
}

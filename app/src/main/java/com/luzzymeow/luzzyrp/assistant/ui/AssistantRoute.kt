package com.luzzymeow.luzzyrp.assistant.ui

/**
 * 助手层导航状态（方向 A · 卷宗）。
 *
 * 方向 A **没有全局导航栏**：会话列表就是「家」；记忆 / 技能 / MCP / 工作区 / 终端 / 设置
 * 收进右侧抽屉（2 跳）。故本状态机只有两级：
 * `ChatList ⇄ Chat`（一级）、`ChatList/Chat → Drawer → 各管理页`（二级）。
 *
 * 用密封类 + 栈式返回，不引入 Navigation 库——页面数量少、转场统一（右移 12dp + 淡入 200ms），
 * 手写更可控且零额外依赖。
 */
sealed interface AssistantRoute {
    /** 家：会话列表（首屏）。 */
    data object ChatList : AssistantRoute

    /** 会话页。 */
    data class Chat(val conversationId: String) : AssistantRoute

    /** 全部助手管理页（顶栏头像条 `+` 进入）。 */
    data object AssistantManager : AssistantRoute

    /** 抽屉二级页。 */
    data object Memory : AssistantRoute
    data object Skills : AssistantRoute
    data object Mcp : AssistantRoute
    data object Workspace : AssistantRoute
    data object Terminal : AssistantRoute
    data object Settings : AssistantRoute

    /** 抽屉项 → 路由（null 表示尚未实现的管理页）。 */
    companion object {
        val drawerEntries: List<DrawerEntry> = listOf(
            DrawerEntry("记忆", Memory),
            DrawerEntry("技能", Skills),
            DrawerEntry("MCP", Mcp),
            DrawerEntry("工作区", Workspace),
            DrawerEntry("终端", Terminal),
            DrawerEntry("设置", Settings),
        )
    }
}

/** 抽屉条目（名称 + 目标路由）。 */
data class DrawerEntry(val label: String, val route: AssistantRoute)

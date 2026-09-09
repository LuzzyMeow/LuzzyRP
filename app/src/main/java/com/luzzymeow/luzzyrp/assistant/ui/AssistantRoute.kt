package com.luzzymeow.luzzyrp.assistant.ui

/**
 * 助手层导航状态（方向 A · 卷宗；2026-09-09 按用户要求改稿）。
 *
 * **首页 = LuzzyRP 聊天页版式**（深色渐隐顶栏 + 消息流 + 输入岛），顶栏右上角为助手设置按钮；
 * 会话列表 / 助手切换 / 管理页入口**全部在 LuzzyRP 原侧栏**的「助手」子项组里
 * （用户 2026-09-09 指定：菜单栏归 LuzzyRP）。助手页左上角汉堡 = 回到 LuzzyRP 侧栏。
 *
 * 用密封类 + 栈式返回，不引入 Navigation 库——页面数量少、转场统一（右移 12dp + 淡入 200ms），
 * 手写更可控且零额外依赖。
 */
sealed interface AssistantRoute {
    /** 家：聊天页版式（承载最近一条会话；无会话时首次发送自动新建）。 */
    data object ChatList : AssistantRoute

    /** 指定会话的聊天页。 */
    data class Chat(val conversationId: String) : AssistantRoute

    /** 会话列表（含助手切换 + 新建）；侧栏「会话」子项入口。 */
    data object Conversations : AssistantRoute

    /** 全部助手管理页。 */
    data object AssistantManager : AssistantRoute

    /** 侧栏「助手」子项二级页。 */
    data object Memory : AssistantRoute
    data object Skills : AssistantRoute
    data object Mcp : AssistantRoute
    data object Workspace : AssistantRoute
    data object Terminal : AssistantRoute
    data object Settings : AssistantRoute

    companion object {
        /** RP 侧栏子项路由名 → 助手路由（空串 = 首页）。 */
        fun fromSidebarRoute(route: String): AssistantRoute = when (route.trim().lowercase()) {
            "conversations" -> Conversations
            "memory" -> Memory
            "skills" -> Skills
            "mcp" -> Mcp
            "workspace" -> Workspace
            "terminal" -> Terminal
            "settings" -> Settings
            else -> ChatList
        }
    }
}

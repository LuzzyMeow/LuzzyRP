package com.luzzymeow.luzzyrp.assistant.ui

/**
 * 助手层导航状态（**扁平单页制**，2026-09-11 用户指定改稿）。
 *
 * **每一页都是侧栏的一级入口，彼此没有上下级**（用户原话：「助手项的每一个子项都是独立单页，
 * 均从侧边菜单栏进入，而不是点击后进入二级页面」）。因此：
 * - 页面**不再有「返回上一级」**——每页左上角一律是**汉堡 → 打开 LuzzyRP 侧栏**（导航唯一入口）；
 * - 页与页之间**没有推进关系**：从会话页点一条会话 = **切到同为一级的「对话」页**并打开它；
 * - 原先的二级页已就地消化：**助手管理并入会话页**（页内折叠卡），会话信息/设置等入口保持同级跳转。
 *
 * 用密封类 + 单一 `route` 状态，不引入 Navigation 库——页面数量少、转场统一（DESIGN.md 页面交接
 * 令牌：进 200ms / 出 140ms / `cubic-bezier(.23,1,.32,1)`），手写更可控且零额外依赖。
 */
sealed interface AssistantRoute {
    /** 对话（家）：聊天页版式；承载「当前会话」（无会话时首次发送自动新建）。 */
    data object ChatList : AssistantRoute

    /** 会话列表（含助手切换 + 助手管理折叠卡 + 新建）；侧栏「会话」子项入口。 */
    data object Conversations : AssistantRoute

    /** 侧栏「助手」子项一级页。 */
    data object Memory : AssistantRoute
    data object Skills : AssistantRoute
    data object Mcp : AssistantRoute
    data object Workspace : AssistantRoute
    data object Terminal : AssistantRoute
    data object Settings : AssistantRoute

    companion object {
        /** RP 侧栏子项路由名 → 助手路由（空串 = 对话页）。 */
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

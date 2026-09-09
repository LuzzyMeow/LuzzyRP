package com.luzzymeow.luzzyrp.assistant

/**
 * 助手原生页的宿主控制接口（PLAN §3.1 / §14）。
 *
 * 由 `MainActivity` 实现（同 Activity 原生覆盖层），`LuzzyBridge` 持有引用——
 * 这样桥接层不依赖 Activity 具体类型，便于单测与后续抽离。
 */
interface AssistantController {
    /**
     * 显示助手覆盖层（首次调用触发 ComposeView 懒创建，PLAN §3.2）。
     *
     * @param route 初始页面（RP 侧栏「助手」子项传入）：`conversations` / `memory` / `skills` /
     *   `mcp` / `workspace` / `terminal` / `settings`；空串 = 首页（聊天页版式）。
     */
    fun showAssistant(route: String)

    /** 隐藏助手覆盖层（WebView 保持存活，状态不丢）。 */
    fun hideAssistant()

    /**
     * 隐藏助手并打开 **LuzzyRP 原侧栏**（用户 2026-09-09 指定：助手页左上角汉堡 =
     * 回到 LuzzyRP 的菜单栏，从那里选择助手页）。
     */
    fun openRpSidebar()

    /** 当前是否可见——返回键优先级判定用（PLAN §2.3）。 */
    fun isAssistantVisible(): Boolean

    /** 主题模式联动（DESIGN.md 恒定「暖幕手记」的亮/暗双模式，跟随 Web 端）。 */
    fun setThemeMode(dark: Boolean)
}

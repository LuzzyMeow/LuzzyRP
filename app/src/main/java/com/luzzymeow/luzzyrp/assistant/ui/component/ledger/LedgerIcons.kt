package com.luzzymeow.luzzyrp.assistant.ui.component.ledger

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 图标集（**逐条取自上游 `index.html` 的 SVG path**，24dp / stroke 2 / round）。
 *
 * 助手管理页是新页面，没有对应的上游页面图标，故按**语义就近**选用上游已有的图标，
 * **不自行绘制新形状**（硬性规定 9 第 3 步）。映射关系：
 *
 * | 概念 | 图标 | 上游出处 |
 * |------|------|----------|
 * | 会话 | 文档 | 角色卡简介 / 记忆浏览同族文档图标 |
 * | 记忆 | 灯泡 | 上游「记忆系统」页头 |
 * | 技能 | 书本 | 上游知识/预设族图标 |
 * | MCP | 数据库 | 上游「空间管理」图标 |
 * | 工作区 | 列表 | 上游向量检索图标 |
 * | 终端 | 代码 | 上游代码图标 |
 * | 设置 | 齿轮 | sprite `#icon-settings` |
 * | 助手管理 | 人群 | 上游角色卡管理图标 |
 * | 添加 | 加号 | 上游通用 |
 * | 删除 | 垃圾桶 | sprite `#icon-delete` |
 * | 搜索 | 放大镜 | 上游检索框 |
 * | 刷新 | 循环箭头 | 上游重试按钮 |
 * | 链接/导入 | 外链 | 上游 GitHub 仓库链接 |
 * | 下载/导出 | 下载 | sprite `#icon-export` 同族 |
 * | 关闭 | 叉 | 上游关闭按钮 |
 * | 提示 | 信息/警告 | 上游 toast |
 */
object LedgerIcons {

    private fun vector(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** 会话（文档）。 */
    val Conversation: ImageVector by lazy {
        vector(
            "conversation",
            "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
        )
    }

    /** 记忆（灯泡）。 */
    val Memory: ImageVector by lazy {
        vector(
            "memory",
            "M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z",
        )
    }

    /** 技能（书本）。 */
    val Skills: ImageVector by lazy {
        vector(
            "skills",
            "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253",
        )
    }

    /** MCP（数据库）。 */
    val Mcp: ImageVector by lazy {
        vector(
            "mcp",
            "M4 7c0 1.1 3.58 2 8 2s8-.9 8-2m-16 0c0-1.1 3.58-2 8-2s8 .9 8 2m-16 0v5c0 1.1 3.58 2 8 2s8-.9 8-2V7m-16 5v5c0 1.1 3.58 2 8 2s8-.9 8-2v-5",
        )
    }

    /** 工作区（列表）。 */
    val Workspace: ImageVector by lazy {
        vector("workspace", "M4 7h16M4 12h10M4 17h7")
    }

    /** 终端（代码）。 */
    val Terminal: ImageVector by lazy {
        vector("terminal", "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4")
    }

    /** 设置（齿轮）。 */
    val Settings: ImageVector by lazy {
        vector(
            "settings",
            "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z",
            "M15 12a3 3 0 11-6 0 3 3 0 016 0z",
        )
    }

    /** 助手管理（人群）。 */
    val Assistants: ImageVector by lazy {
        vector(
            "assistants",
            "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z",
        )
    }

    /** 添加。 */
    val Plus: ImageVector by lazy { vector("plus", "M12 4v16m8-8H4") }

    /** 删除（垃圾桶）。 */
    val Trash: ImageVector by lazy {
        vector(
            "trash",
            "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16",
        )
    }

    /** 搜索。 */
    val Search: ImageVector by lazy {
        vector("search", "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
    }

    /** 刷新。 */
    val Refresh: ImageVector by lazy {
        vector(
            "refresh",
            "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15",
        )
    }

    /** 链接 / URL 导入（外链）。 */
    val ExternalLink: ImageVector by lazy {
        vector("external-link", "M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14")
    }

    /** 下载 / 导出。 */
    val Download: ImageVector by lazy {
        vector("download", "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4")
    }

    /** 关闭（叉）。 */
    val Close: ImageVector by lazy { vector("close", "M6 18L18 6M6 6l12 12") }

    /** 信息。 */
    val Info: ImageVector by lazy {
        vector("info", "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z")
    }

    /** 警告。 */
    val Warning: ImageVector by lazy {
        vector(
            "warning",
            "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z",
        )
    }

    /** 调节 / 参数（上游「记忆引擎设置」图标）。 */
    val Sliders: ImageVector by lazy {
        vector(
            "sliders",
            "M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4",
        )
    }

    /** 返回（两段折线，与上游 chevron 同形）。 */
    val ChevronLeft: ImageVector by lazy { vector("chevron-left", "M15 19l-7-7 7-7") }

    /** 展开箭头（两段折线）。 */
    val ChevronDown: ImageVector by lazy { vector("chevron-down", "M19 9l-7 7-7-7") }
}

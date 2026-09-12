package com.luzzymeow.luzzyrp.ui.icons

import com.luzzymeow.luzzyrp.R

/**
 * v3.0 图标体系（LuzzyIcons）。
 *
 * **来源（直接复用，不另行引入图标库）**：
 * 1. **主体 = 之前 LuzzyRP（WebView 版）的 `ic_lz_*` VectorDrawable 集合**
 *    （v0.1.0 起随仓库分发；其形状 = 上游 RP-Hub 内嵌 SVG 的 d 路径原样搬运，
 *    即 Heroicons v1 outline 形状池，MIT）——新旧版本图标**同形**，品牌连续；
 * 2. **补缺 6 枚**（v0.1.0 集合未覆盖）：`Moon`/`Sun`/`DotsHorizontal`/`BookOpen`/
 *    `ChartBar` 取自 Heroicons v1.0.6 官方 SVG（MIT）原文；
 * 3. **兜底**：缺形状时优先补 Heroicons 原文，不引入 material-icons / hugeicons
 *    （rikkahub 的 hugeicons 为本地 jar 二进制，源码不可得且素材许可链不透明）。
 *
 * 用法：`Icon(painterResource(LuzzyIcons.Menu), …)`——图标为 stroke 矢量，颜色走 tint。
 */
object LuzzyIcons {
    // 原版（v0.1.0 集合，上游 RP-Hub SVG 同形）
    val Menu = R.drawable.ic_lz_menu
    val Plus = R.drawable.ic_lz_plus
    val Send = R.drawable.ic_lz_send
    val ChevronDown = R.drawable.ic_lz_chevron_down
    val ChevronLeft = R.drawable.ic_lz_chevron_left
    val Close = R.drawable.ic_lz_close
    val Conversation = R.drawable.ic_lz_conversation
    val Assistants = R.drawable.ic_lz_assistants
    val Sliders = R.drawable.ic_lz_sliders
    val Memory = R.drawable.ic_lz_memory
    val Settings = R.drawable.ic_lz_settings
    val Search = R.drawable.ic_lz_search
    val Info = R.drawable.ic_lz_info
    val Refresh = R.drawable.ic_lz_refresh
    val Download = R.drawable.ic_lz_download
    val ExternalLink = R.drawable.ic_lz_external_link
    val Trash = R.drawable.ic_lz_trash
    val Warning = R.drawable.ic_lz_warning
    val Workspace = R.drawable.ic_lz_workspace
    val Mcp = R.drawable.ic_lz_mcp
    val Skills = R.drawable.ic_lz_skills
    val Terminal = R.drawable.ic_lz_terminal

    // 补缺（Heroicons v1.0.6 官方，MIT）
    val Moon = R.drawable.ic_lz_moon
    val Sun = R.drawable.ic_lz_sun
    val DotsHorizontal = R.drawable.ic_lz_dots_horizontal
    val BookOpen = R.drawable.ic_lz_book_open
    val ChartBar = R.drawable.ic_lz_chart_bar
    val Copy = R.drawable.ic_lz_copy
    val Edit = R.drawable.ic_lz_edit
    val ChevronRight = R.drawable.ic_lz_chevron_right
    /** 剧情分支（Heroicons v1 `share` = 三节点连线；上游 StoryBranchModal 的路线图同语义）。 */
    val Branch = R.drawable.ic_lz_branch
    /** 模型（Heroicons v1 `chip` = 芯片；模型选择入口语义图标）。 */
    val Chip = R.drawable.ic_lz_chip
}
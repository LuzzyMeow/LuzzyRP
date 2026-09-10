package com.luzzymeow.luzzyrp.assistant.ui.component.ledger

import androidx.annotation.DrawableRes
import com.luzzymeow.luzzyrp.R

/**
 * 图标集：**直接复用原项目（RP-Hub）已画好的 SVG 图标**。
 *
 * 每个图标都是上游 `index.html` 的 SVG `d` 路径，落成 Android VectorDrawable
 * （`res/drawable/ic_lz_*.xml`）——由**系统自带 SVG 路径解析器**渲染，
 * 绕开 Compose `addPathNodes` 对「紧凑弧线标志位」的解析缺陷（会把圆画成半圆）。
 *
 * 规格：24dp 视口 / `stroke-width 2` / `stroke-linecap|linejoin round`（与上游一致）。
 * 颜色由调用方通过 `Icon(tint = …)` 指定。
 *
 * 语义映射（助手管理页为新增页面，取上游已有图标按语义就近选用）：
 * 会话=文档 · 记忆=灯泡 · 技能=书本 · MCP=数据库 · 工作区=列表 · 终端=代码 ·
 * 设置=齿轮 · 助手管理=人群 · 参数=调节 · 其余为上游通用图标。
 */
object LedgerIcons {
    @DrawableRes val Conversation: Int = R.drawable.ic_lz_conversation
    @DrawableRes val Memory: Int = R.drawable.ic_lz_memory
    @DrawableRes val Skills: Int = R.drawable.ic_lz_skills
    @DrawableRes val Mcp: Int = R.drawable.ic_lz_mcp
    @DrawableRes val Workspace: Int = R.drawable.ic_lz_workspace
    @DrawableRes val Terminal: Int = R.drawable.ic_lz_terminal
    @DrawableRes val Settings: Int = R.drawable.ic_lz_settings
    @DrawableRes val Assistants: Int = R.drawable.ic_lz_assistants
    @DrawableRes val Sliders: Int = R.drawable.ic_lz_sliders
    @DrawableRes val Plus: Int = R.drawable.ic_lz_plus
    @DrawableRes val Trash: Int = R.drawable.ic_lz_trash
    @DrawableRes val Search: Int = R.drawable.ic_lz_search
    @DrawableRes val Refresh: Int = R.drawable.ic_lz_refresh
    @DrawableRes val ExternalLink: Int = R.drawable.ic_lz_external_link
    @DrawableRes val Download: Int = R.drawable.ic_lz_download
    @DrawableRes val Close: Int = R.drawable.ic_lz_close
    @DrawableRes val Info: Int = R.drawable.ic_lz_info
    @DrawableRes val Warning: Int = R.drawable.ic_lz_warning
    @DrawableRes val ChevronLeft: Int = R.drawable.ic_lz_chevron_left
    @DrawableRes val ChevronDown: Int = R.drawable.ic_lz_chevron_down
    /** 上游 `#icon-menu`（聊天页汉堡 / 侧栏入口）。 */
    @DrawableRes val Menu: Int = R.drawable.ic_lz_menu
    /** 上游聊天页发送按钮图形（纸飞机）。 */
    @DrawableRes val Send: Int = R.drawable.ic_lz_send
}

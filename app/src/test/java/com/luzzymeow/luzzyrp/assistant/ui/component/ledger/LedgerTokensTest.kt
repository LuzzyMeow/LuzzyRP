package com.luzzymeow.luzzyrp.assistant.ui.component.ledger

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组件规格锁定测试（DESIGN.md §管理页组件规范「验收方式」第 1 条）。
 *
 * 这些数值**逐项来自上游 Tailwind 类**（1 CSS px = 1 dp），改动即意味着偏离上游，
 * 必须先在 DESIGN.md 更新规格再改这里——测试失败即拦截。
 */
class LedgerTokensTest {

    @Test
    fun `开关尺寸对齐上游 settings-toggle（2·75rem × 1·5rem）`() {
        assertEquals(44.dp, Ledger.ToggleWidth)   // 2.75rem
        assertEquals(24.dp, Ledger.ToggleHeight)  // 1.5rem
        assertEquals(20.dp, Ledger.ToggleThumb)   // 1.25rem
        assertEquals(2.dp, Ledger.ToggleInset)    // 0.125rem
    }

    @Test
    fun `按钮高度对齐 px-3 py-1·5 + text-xs`() {
        assertEquals(32.dp, Ledger.ButtonHeight)
        assertEquals(12.dp, Ledger.ButtonPaddingH) // px-3
    }

    @Test
    fun `页面骨架对齐 management-view 与 settings-page-header 骨架`() {
        assertEquals(16.dp, Ledger.PagePadding)      // p-4
        assertEquals(48.dp, Ledger.PageHeaderHeight) // h-12
        assertEquals(16.dp, Ledger.PageHeaderGap)    // mb-4
        assertEquals(16.dp, Ledger.CardGap)          // space-y-4
    }

    @Test
    fun `圆角对齐 rounded-lg · xl · 2xl`() {
        assertEquals(8.dp, Ledger.RadiusSm)
        assertEquals(12.dp, Ledger.RadiusMd)
        assertEquals(16.dp, Ledger.RadiusLg)
    }

    @Test
    fun `图标尺寸对齐 w-6 · w-7 · w-5 · w-4`() {
        assertEquals(24.dp, Ledger.IconSize)
        assertEquals(28.dp, Ledger.IconSizeLg)
        assertEquals(20.dp, Ledger.IconSizeMd)
        assertEquals(16.dp, Ledger.IconSizeSm)
        assertEquals(2.dp, Ledger.IconStroke) // stroke-width 2
    }

    @Test
    fun `折叠面板时长对齐 §Motion 令牌（展开 200 · 收起 140）`() {
        // 2026-09-10 修订：原对齐上游 .settings-collapse 的 0.36s，与会话 48 已收敛到令牌的
        // Web 侧栏折叠（200/140）分裂成两种节奏 → 追认为令牌值（审查档 A3）。
        assertEquals(200, Ledger.CollapseExpandMs)
        assertEquals(140, Ledger.CollapseCollapseMs)
    }

    @Test
    fun `输入框与搜索框高度`() {
        assertEquals(44.dp, Ledger.InputMinHeight) // px-4 py-3
        assertEquals(40.dp, Ledger.SearchHeight)   // py-2.5
    }

    @Test
    fun `图标集覆盖助手页所需语义`() {
        val icons = listOf(
            LedgerIcons.Conversation, LedgerIcons.Memory, LedgerIcons.Skills, LedgerIcons.Mcp,
            LedgerIcons.Workspace, LedgerIcons.Terminal, LedgerIcons.Settings, LedgerIcons.Assistants,
            LedgerIcons.Plus, LedgerIcons.Trash, LedgerIcons.Search, LedgerIcons.Refresh,
            LedgerIcons.ExternalLink, LedgerIcons.Download, LedgerIcons.Close,
            LedgerIcons.Info, LedgerIcons.Warning, LedgerIcons.ChevronLeft, LedgerIcons.ChevronDown,
            // 聊天页顶栏/输入岛（2026-09-10 起与 ledger 同源，ChatIcons 已删除）
            LedgerIcons.Menu, LedgerIcons.Sliders, LedgerIcons.Send,
        )
        assertEquals(22, icons.size)
        // 图标是 VectorDrawable 资源 id（复用上游 SVG），非零即有效
        icons.forEach { id -> assertTrue("drawable id 应为非零", id != 0) }
    }
}

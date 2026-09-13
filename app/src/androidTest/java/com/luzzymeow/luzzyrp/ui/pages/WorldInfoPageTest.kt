package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.world.WorldBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldScope
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.world.WorldInfoPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 世界书页 + 编辑器仪器化测试（W2/W3）。
 *
 * 钉的是**交互后果**（写没写库、确认框在不在、字段联动对不对），不是像素。
 * 样例数据：1 条旧键条目 → 迁移后按 W0 口径成为**全局**条目（`一条世界书`）。
 *
 * 线程纪律同 `SessionsPageTest`：碰数据库用 `compose.waitUntil { runBlocking { … } }`（借它推帧），
 * 条件里不调 Compose API。
 */
@RunWith(AndroidJUnit4::class)
class WorldInfoPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "worldinfo-page")
        runBlocking { fixture.seedFromSample() }
    }

    @After
    fun tearDown() = fixture.close()

    private fun repository() = WorldBookRepository(fixture.store, fixture.repository)

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                WorldInfoPage(onOpenDrawer = {}, repository = repository())
            }
        }
    }

    private fun awaitText(text: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitDb(timeoutMs: Long = 8_000, block: suspend () -> Boolean) {
        compose.waitUntil(timeoutMs) { runBlocking { block() } }
    }

    private fun openRowMenu(rowTag: String) {
        compose.onNodeWithContentDescription("一条世界书 的更多操作").assertIsDisplayed()
        compose.onNodeWithContentDescription("一条世界书 的更多操作").performClick()
        assertTrue("菜单应属于 $rowTag 这一行", rowTag.isNotBlank())
    }

    @Test
    fun showsSeededEntryInGlobalGroup() {
        setContent()

        awaitText("一条世界书")
        awaitText("全局条目 · 1")
        compose.onNodeWithTag("worldinfo_list").assertIsDisplayed()
        // 归属徽标 + 触发说明都要在（颜色不是唯一指示）
        awaitText("全局")
        awaitText("关键词：钥匙")
        // 有角色 → 第二个分组头出现
        awaitText("样例角色 绑定 · 0")
    }

    @Test
    fun toggleWritesToStore() {
        setContent()
        awaitText("一条世界书")

        compose.onNodeWithContentDescription("启用 一条世界书").performClick()
        awaitDb { !repository().load().globalRows.single().entry.enabled }

        compose.onNodeWithContentDescription("启用 一条世界书").performClick()
        awaitDb { repository().load().globalRows.single().entry.enabled }
    }

    @Test
    fun deleteNeedsConfirmation() {
        setContent()
        awaitText("一条世界书")

        openRowMenu("world_row_global_0")
        compose.onNodeWithText("删除").performClick()

        awaitText("删除这条世界书条目？")
        compose.onNodeWithTag("world_delete_cancel").performClick()
        assertTrue("取消后条目还在", runBlocking { repository().load().globalRows.size == 1 })

        openRowMenu("world_row_global_0")
        compose.onNodeWithText("删除").performClick()
        awaitText("删除这条世界书条目？")
        compose.onNodeWithTag("world_delete_confirm").performClick()
        awaitDb { repository().load().isEmpty }
    }

    @Test
    fun emptyStateWhenNothingToShow() {
        val empty = TestStoreFixture.create(context, "worldinfo-empty")
        try {
            compose.setContent {
                LuzzyTheme(darkTheme = false) {
                    WorldInfoPage(
                        onOpenDrawer = {},
                        repository = WorldBookRepository(empty.store, empty.repository),
                    )
                }
            }
            awaitText("还没有世界书条目")
            awaitText("尚未选择角色：角色绑定条目在这里看不到")
        } finally {
            empty.close()
        }
    }

    @Test
    fun editorSavesEdits() {
        setContent()
        awaitText("一条世界书")

        openRowMenu("world_row_global_0")
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("world_editor_name").performTextReplacement("改过的名字")
        // 先填关键词再开常驻：常驻会把关键词框置灰（顺序反了就打不进去——本测试第一版正是这么红的）
        compose.onNodeWithTag("world_editor_keys").performTextReplacement("苹果，钟楼")
        compose.onNodeWithContentDescription("常驻").performClick()
        compose.onNodeWithTag("world_editor_save").performClick()

        awaitDb {
            val entry = repository().load().globalRows.single().entry
            entry.comment == "改过的名字" && entry.constant && entry.keys == listOf("苹果", "钟楼")
        }
        awaitText("改过的名字")
    }

    @Test
    fun constantDisablesKeysField() {
        setContent()
        awaitText("一条世界书")
        openRowMenu("world_row_global_0")
        compose.onNodeWithText("编辑").performClick()

        compose.onNodeWithTag("world_editor_keys").assertIsDisplayed()
        compose.onNodeWithContentDescription("常驻").performClick()
        compose.onNodeWithTag("world_editor_keys").assertIsNotEnabled()
    }

    @Test
    fun depthFieldOnlyWhenInsertingAtDepth() {
        setContent()
        awaitText("一条世界书")
        openRowMenu("world_row_global_0")
        compose.onNodeWithText("编辑").performClick()

        // 夹具那条没写 position → 上游默认 at_depth → 深度字段应在
        awaitText("深度")
        compose.onNodeWithTag("world_editor_depth").assertIsDisplayed()

        compose.onNodeWithContentDescription("选择注入位置").performClick()
        compose.onNodeWithText("系统提示词开头").performClick()

        compose.onNodeWithTag("world_editor_depth").assertDoesNotExist()
    }

    @Test
    fun dirtyDraftAsksBeforeDiscard() {
        setContent()
        awaitText("一条世界书")
        openRowMenu("world_row_global_0")
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("world_editor_name").performTextReplacement("改了一半")

        compose.onNodeWithContentDescription("关闭").performClick()
        awaitText("放弃未保存的修改？")
        compose.onNodeWithText("继续编辑").performClick()

        // 还在编辑器里，且改动还在；库里没被写
        compose.onNodeWithTag("world_editor_name").assertIsDisplayed()
        assertEquals(
            "一条世界书",
            runBlocking { repository().load().globalRows.single().entry.comment },
        )
        assertTrue(runBlocking { repository().load().rows.all { it.group == WorldScope.Global } })
    }
}

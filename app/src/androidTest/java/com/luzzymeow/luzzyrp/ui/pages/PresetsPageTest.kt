package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.preset.PresetsPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 预设页 + 编辑器仪器化测试（W4）。
 *
 * 样例数据：1 条 `一条预设`（role=system）。钉的仍是**交互后果**：
 * 顺序真的改了注入顺序（数组序）、启停/删除落盘、正文走二级编辑器能存回来。
 */
@RunWith(AndroidJUnit4::class)
class PresetsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "presets-page")
        runBlocking { fixture.seedFromSample() }
    }

    @After
    fun tearDown() = fixture.close()

    private fun repository() = PresetRepository(fixture.store)

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                PresetsPage(onOpenDrawer = {}, repository = repository())
            }
        }
    }

    /**
     * 等待纪律统一在 `testing/Await.kt`（会话 78）：`waitUntil` 自旋**不推进测试时钟**，
     * 帧驱动的协程续体（页面取数 / 落盘）恢复不了，会以「超时」的形式假红。
     */
    private fun awaitText(text: String, timeoutMs: Long = 8_000) =
        com.luzzymeow.luzzyrp.testing.Await.text(compose, text, timeoutMs)

    private fun awaitDb(timeoutMs: Long = 8_000, block: suspend () -> Boolean) =
        com.luzzymeow.luzzyrp.testing.Await.db(compose, timeoutMs, block)

    private fun openMenu(title: String) {
        compose.onNodeWithContentDescription("$title 的更多操作").performClick()
    }

    @Test
    fun showsSeededPresetWithRoleBadge() {
        setContent()
        awaitText("提示词预设 · 1")
        awaitText("一条预设")
        awaitText("系统提示词")
        compose.onNodeWithTag("presets_list").assertIsDisplayed()
    }

    @Test
    fun toggleWritesToStore() {
        setContent()
        awaitText("一条预设")

        compose.onNodeWithContentDescription("启用 一条预设").performClick()
        awaitDb { !repository().all().single().entry.enabled }
    }

    @Test
    fun newPresetAppendsToEndThenMovesUp() {
        setContent()
        awaitText("一条预设")

        compose.onNodeWithContentDescription("新建预设").performClick()
        compose.onNodeWithTag("preset_editor_name").performTextReplacement("新预设甲")
        compose.onNodeWithText("User 消息").performClick()
        compose.onNodeWithTag("preset_editor_save").performClick()

        awaitDb { repository().all().size == 2 }
        assertEquals(
            "新增应追加到末尾（这决定了注入顺序）",
            listOf("一条预设", "新预设甲"),
            runBlocking { repository().all().map { it.entry.name } },
        )
        assertEquals(PresetRole.User, runBlocking { repository().all()[1].entry.role })

        openMenu("新预设甲")
        compose.onNodeWithText("上移").performClick()
        awaitDb { repository().all().first().entry.name == "新预设甲" }
        assertEquals(
            listOf("新预设甲", "一条预设"),
            runBlocking { repository().all().map { it.entry.name } },
        )
    }

    @Test
    fun deleteNeedsConfirmation() {
        setContent()
        awaitText("一条预设")

        openMenu("一条预设")
        compose.onNodeWithText("删除").performClick()
        awaitText("删除这条预设？")
        compose.onNodeWithTag("preset_delete_cancel").performClick()
        assertEquals(1, runBlocking { repository().all().size })

        openMenu("一条预设")
        compose.onNodeWithText("删除").performClick()
        awaitText("删除这条预设？")
        compose.onNodeWithTag("preset_delete_confirm").performClick()
        awaitDb { repository().all().isEmpty() }
    }

    @Test
    fun editsContentThroughLongTextEditor() {
        setContent()
        awaitText("一条预设")

        openMenu("一条预设")
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("preset_editor_name").performTextReplacement("一条预设（改）")
        compose.onNodeWithText("编辑正文").performClick()
        // 二级全屏编辑器：只有一个滚动容器
        compose.onNodeWithTag("preset_editor_content").performTextReplacement("改过的正文")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("preset_editor_save").performClick()

        awaitDb {
            val entry = repository().all().single().entry
            entry.name == "一条预设（改）" && entry.content == "改过的正文"
        }
    }

    @Test
    fun emptyStateWhenNothingToShow() {
        val empty = TestStoreFixture.create(context, "presets-empty")
        try {
            compose.setContent {
                LuzzyTheme(darkTheme = false) {
                    PresetsPage(onOpenDrawer = {}, repository = PresetRepository(empty.store))
                }
            }
            awaitText("还没有预设条目")
        } finally {
            empty.close()
        }
    }

    @Test
    fun dirtyDraftAsksBeforeDiscard() {
        setContent()
        awaitText("一条预设")

        openMenu("一条预设")
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("preset_editor_name").performTextReplacement("改了一半")
        compose.onNodeWithContentDescription("关闭").performClick()

        awaitText("放弃未保存的修改？")
        compose.onNodeWithText("继续编辑").performClick()
        assertEquals("库里没被写", "一条预设", runBlocking { repository().all().single().entry.name })
    }
}

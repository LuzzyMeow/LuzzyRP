package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
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
 * 会话总览页（方向 B · 分组行）仪器化测试。
 *
 * 设计门与用户选择：`docs/design/boards-v5/direction-approved-v5.md`（方向 B + 预览取用户发言 +
 * 入口在聊天页顶栏）。这条测试钉的是**版式主张**与**交互后果**，不是像素：
 * 角色=分组、分支=行、空会话可见、点一行会把「当前角色/当前分支」写进 kv。
 *
 * 线程纪律同 `ChatPersistenceTest`：碰数据库用 `compose.waitUntil { runBlocking { … } }`
 * （借它推进帧），条件里不调 Compose API。
 */
@RunWith(AndroidJUnit4::class)
class SessionsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "sessions-page")
        runBlocking { fixture.seedFromSample() }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                SessionsPage(
                    onOpenDrawer = {},
                    onOpenSession = { _, _ -> },
                    sessionRepository = fixture.repository,
                )
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

    @Test
    fun showsGroupHeadersAndBranchRows() {
        setContent()

        // 角色=分组头（出现的名字）
        awaitText("样例角色")
        awaitText("第二张")
        // 分支=行。注意**每张卡的主线都叫「主线」**，所以这里必然命中多个 → 用集合断言
        assertTrue(
            "两条主线应各占一行",
            compose.onAllNodes(hasText("主线")).fetchSemanticsNodes().size >= 2,
        )
        // 分支名 + 「分支」标记（非主线才标）
        awaitText("分支甲")
        // 预览取的是**用户发言**，不是末条助手正文
        awaitText("第二句")
        awaitText("分支里的问句")
    }

    @Test
    fun emptySessionsAreVisibleNotFiltered() {
        setContent()
        awaitText("第二张")

        // 样例里第二张卡只有开场白、没有用户发言 → 预览回落末条正文（不是空白行）
        compose.onNodeWithTag("session_row_char-2_main").assertIsDisplayed()
        // 组头与行都在
        compose.onNodeWithTag("sessions_list").assertIsDisplayed()
    }

    @Test
    fun tappingARowRemembersThatSession() {
        setContent()
        // b1 的预览 = 该会话**最后一条用户发言**（用户 2026-09-13 拍板），所以是「分支里的问句」
        awaitText("分支里的问句")

        // **先把当前分支拨到主线**：样例里 char-1 的 activeBranchId 本来就是 b1，
        // 直接拿 b1 当判据是**假绿**——写入还没发生，判据就已经成立。
        // 这一条最早就是这么红的（冷启动首跑）：挂起的写入在测试结束、库被关掉之后才执行 →
        // `attempt to re-open an already-closed object`。判据必须能区分「点之前」与「点之后」。
        runBlocking { fixture.repository.rememberActiveBranch("char-1", "main") }
        assertEquals("main", runBlocking { fixture.store.activeBranchId("char-1") })

        compose.onNodeWithTag("session_row_char-1_b1").performClick()

        // 点一行 = 把「当前角色 + 当前分支」写进 kv（聊天页据此装载，跨页不需要传状态）
        awaitDb { fixture.store.activeBranchId("char-1") == "b1" }
        assertEquals("char-1", runBlocking { fixture.store.string(LuzzyStore.KEY_ACTIVE_CHARACTER) })
    }
}

package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.store.BranchEntity
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.store.MessageEntity
import com.luzzymeow.luzzyrp.data.world.LoreBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.preset.PresetsPage
import com.luzzymeow.luzzyrp.ui.pages.world.LoreBookPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **其余页面的有数据留证**（预设 / 世界书 / 会话总览）：v3.2 全页复查。
 *
 * 模拟器的应用库是空的，这几页在空库上只剩空态——**列表版式、徽标、开关、菜单**都看不到，
 * 而「像不像简化版」恰恰是列表形态决定的。所以这里用应用自己的写入路径种数据再截图。
 */
@RunWith(AndroidJUnit4::class)
class OtherPagesVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "otherpages")
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private suspend fun seed() {
        val store = fixture.store
        // ── 预设：三种注入角色各一条（列表版式靠徽标区分）──
        val presets = PresetRepository(store)
        presets.upsert(null, PresetEntry(name = "世界观设定", content = "这是一个被称作「钟楼」的地方……", role = PresetRole.System, enabled = true))
        presets.upsert(null, PresetEntry(name = "叙述规则", content = "用第二人称叙述，禁止替玩家决定。", role = PresetRole.System, enabled = true))
        presets.upsert(null, PresetEntry(name = "结尾提醒", content = "本轮结尾留一个钩子。", role = PresetRole.User, enabled = false))

        // ── 世界书：一本书 + 若干条目（含 constant 与关键字触发）──
        val books = LoreBookRepository(store)
        val bookId = books.create(name = "钟楼设定集")
        books.upsertEntry(
            bookId,
            slot = null,
            entry = WorldEntry(
                comment = "红苹果树",
                keys = listOf("苹果", "红苹果"),
                content = "钟楼顶上有一棵红苹果树，只有恶魔找得到。",
                order = 10,
            ),
        )
        books.upsertEntry(
            bookId,
            slot = null,
            entry = WorldEntry(
                comment = "钟楼地理",
                keys = listOf("钟楼", "嬷嬷"),
                content = "钟楼分三层，嬷嬷住在二层。",
                constant = true,
                order = 20,
            ),
        )

        // ── 会话：两张卡 × 若干分支（粘性组头 + 分支行）──
        store.upsertCharacter(
            CharacterEntity("k1", "钟楼下的小恶魔", null, 1L, """{"name":"钟楼下的小恶魔"}"""),
        )
        store.upsertCharacter(
            CharacterEntity("k2", "海边的医生", null, 2L, """{"name":"海边的医生"}"""),
        )
        store.replaceBranches(
            characterUuid = "k1",
            branches = listOf(
                BranchEntity("k1", "main", "主线", null, 1L, 3L, 0, 4, 120, true),
                BranchEntity("k1", "b1", "钟楼西侧", "main", 2L, 4L, 2, 2, 60, false),
            ),
            activeBranchId = "b1",
        )
        store.replaceBranches(
            characterUuid = "k2",
            branches = listOf(BranchEntity("k2", "main", "主线", null, 1L, 2L, 0, 1, 30, true)),
            activeBranchId = "main",
        )
        store.replaceMessages(
            ScopeId("k1", "main"),
            listOf(
                MessageEntity(ScopeId("k1", "main").suffix(), 0, "m0", "user", null, "你篮子里装的是什么？", null, "{}"),
                MessageEntity(ScopeId("k1", "main").suffix(), 1, "m1", "assistant", null, "「嘿嘿，想知道？」他把苹果往兜里塞了塞。", null, "{}"),
            ),
        )
        store.replaceMessages(
            ScopeId("k1", "b1"),
            listOf(
                MessageEntity(ScopeId("k1", "b1").suffix(), 0, "n0", "user", null, "嬷嬷的巡夜铃响了吗", null, "{}"),
            ),
        )
        store.replaceMessages(
            ScopeId("k2", "main"),
            listOf(
                MessageEntity(ScopeId("k2", "main").suffix(), 0, "o0", "user", null, "医生，我睡不着", null, "{}"),
            ),
        )
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "k1")
    }

    @Test
    fun 预设页有数据时的列表版式留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                PresetsPage(
                    onOpenDrawer = {},
                    repository = PresetRepository(fixture.store),
                    onImport = {},
                    onExport = {},
                )
            }
        }
        Await.text(compose, "世界观设定", 8_000, substring = true)
        Capture.shot(context, compose, "80-presets-light", stamp)
    }

    @Test
    fun 世界书页有数据时的列表版式留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                LoreBookPage(onOpenDrawer = {}, repository = LoreBookRepository(fixture.store))
            }
        }
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        // **扫描深度必须可达**（v3.2 修的断链：两个滑杆原先挂在不走路由的旧页上）
        Await.text(compose, "扫描深度", 8_000, substring = true)
        Capture.shot(context, compose, "81-worldbook-light", stamp)
        // 进到条目列表
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        compose.waitForIdle()
        runCatching { Capture.shot(context, compose, "82-worldbook-entries", stamp) }
    }

    /**
     * **扫描深度真的能改、且真的落库**。
     *
     * 这条守的是「功能不可达」那类缺陷：滑杆写好了，但挂在没进路由的旧页上——
     * 界面上完全改不到，而且**不报错**（点哪都没有反应，用户只会以为「这版就这样」）。
     *
     * 用**语义动作 SetProgress** 而不是 `swipeRight()`：拖动依赖起点落在滑杆身上，
     * 而滑杆的可点区域随版面变动——那是概率性判据。语义动作是确定性的。
     */
    @Test
    fun 世界书扫描深度可改并落库() {
        val repo = LoreBookRepository(fixture.store)
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                LoreBookPage(onOpenDrawer = {}, repository = repo)
            }
        }
        Await.text(compose, "扫描深度", 8_000, substring = true)
        compose.onAllNodes(hasText("扫描深度", substring = true)).onFirst().assertIsDisplayed()

        compose.onNodeWithTag("world_scan_depth").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress,
        ) { set -> set(7f) }

        Await.db(compose, 10_000) { repo.settings().scanDepth == 7 }
    }

    @Test
    fun 会话总览有数据时的版式留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                SessionsPage(
                    onOpenDrawer = {},
                    onOpenSession = { _, _ -> },
                    sessionRepository = ChatSessionRepository(fixture.store),
                )
            }
        }
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        // 抬头行「N 条 · M 张卡」必须**真的被布局到屏幕上**（v3.2 修的缺陷：它原先挂在页头的
        // actions 槽里，语义树 0 个节点——写了但看不见）。这里量 bounds 而不只是 existence：
        // 「存在但被挤成 0 宽」是本仓库登记过的一类坑（DOM 里有 ≠ 用户看得见）。
        compose.onNodeWithTag("sessions_list").performScrollToNode(hasText("张卡", substring = true))
        compose.waitForIdle()
        val header = compose.onAllNodes(hasText("张卡", substring = true)).onFirst().fetchSemanticsNode()
        assertTrue("抬头行应该在屏内且宽度 > 0（实际 ${header.boundsInRoot}）", header.size.width > 0)
        Capture.shot(context, compose, "83-sessions-light", stamp)
    }
}

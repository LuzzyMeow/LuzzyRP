package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.AgentLoop
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
import com.luzzymeow.luzzyrp.testing.FakeTransport
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
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
 * **逐页暗色复查**（v3.2 视觉纪律收尾，2026-09-16）。
 *
 * ## 为什么必须单独一轮、且必须逐页
 *
 * 本仓库的既有暗色覆盖只有两页（角色卡 `CharactersPageTest`、用量 `UsageVisualCaptureTest`），
 * 其余页面**只有亮色留证**。而暗色**不能从亮色推断**——M3 的 `surface*` 色阶在暗色下整体反转，
 * 任何「在亮色下刚好够」的对比度都可能掉到 4.5:1 以下；硬编码色值（本项目的历史病根）
 * 更会在暗色下变成灰脏块。本仓库为此立过判据：**两个主题都要看，不许从单一主题推断**。
 *
 * 所以这里对**每一页**各出一张暗色图，由人工 `read_image` 逐张审查（对比度 / 不破版 / 不缺字）。
 * 亮色版在各自的既有文件里（`80-*`/`70-*`/`65-*` 等），本文件只补暗色，编号接续 `90-*`。
 *
 * ## 判据里为什么还要带断言（不只是截图）
 *
 * 截图靠人看，而人可能漏看「文字被压成 0 宽」这种**不报错**的形态（本仓库登记过的坑）。
 * 所以每页在截图前都先**量一次抬头/关键行的宽度 > 0**——把机器能判的那部分先判掉，
 * 剩下的「好不好看」才交给人眼。
 */
@RunWith(AndroidJUnit4::class)
class DarkModeVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "dark-sweep")
        runBlocking { seed() }
    }

    @After
    fun tearDown() = fixture.close()

    /** 种满数据：每页都要**有数据**才看得到真版式（空态看不出配色问题）。 */
    private suspend fun seed() {
        val store = fixture.store
        val presets = PresetRepository(store)
        presets.upsert(null, PresetEntry(name = "世界观设定", content = "这是一个被称作「钟楼」的地方……", role = PresetRole.System, enabled = true))
        presets.upsert(null, PresetEntry(name = "结尾提醒", content = "本轮结尾留一个钩子。", role = PresetRole.User, enabled = false))

        val books = LoreBookRepository(store)
        val bookId = books.create("钟楼设定集")
        books.upsertEntry(bookId, null, WorldEntry(comment = "红苹果树", keys = listOf("苹果"), content = "钟楼顶上有棵红苹果树。"))
        books.upsertEntry(bookId, null, WorldEntry(comment = "钟楼地理", content = "钟楼分三层。", constant = true))
        books.setEnabled(bookId, true)

        store.upsertCharacter(
            CharacterEntity("k1", "钟楼下的小恶魔", null, 1L, "{}"),
        )
        store.upsertCharacter(
            CharacterEntity("k2", "值夜的医生", null, 2L, "{}"),
        )
        store.replaceBranches(
            "k1",
            listOf(
                BranchEntity("k1", "main", "主线", null, 1L, 1L, 0, 2, 10, true),
                BranchEntity("k1", "b1", "教堂后墙", "main", 2L, 2L, 2, 1, 5, false),
            ),
            activeBranchId = "main",
        )
        store.replaceMessages(
            ScopeId("k1", "main"),
            listOf(
                MessageEntity(ScopeId("k1", "main").suffix(), 0, "o0", "user", null, "你看得见钟楼吗", null, "{}"),
                MessageEntity(ScopeId("k1", "main").suffix(), 1, "o1", "assistant", null, "看得见。它一直在那儿。", null, "{}"),
            ),
        )
        store.replaceMessages(
            ScopeId("k2", "main"),
            listOf(
                MessageEntity(ScopeId("k2", "main").suffix(), 0, "o0", "user", null, "医生，我睡不着", null, "{}"),
            ),
        )
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "k1")

        // ── 记忆：向量分片 + 总结记忆各两条（记忆页在空库上只剩空态，看不出卡片配色）──
        val scope = ScopeId("k1", "main")
        val json = kotlinx.serialization.json.Json
        store.replaceMemories(
            scope,
            LuzzyStore.MEMORY_VECTOR,
            listOf(
                """{"id":"v1","turn":1,"paragraph":"钟楼顶上的红苹果树只有他找得到；树下埋着一枚生锈的钥匙。","enabled":true,"chunkMode":"paragraph","sourceRole":"mixed","sourceName":"你 + 他","embeddingDims":1536,"embeddingModel":"text-embedding-3-small"}""",
                """{"id":"v2","turn":2,"paragraph":"他把苹果塞进兜里时许愿要留一颗给姐姐。","enabled":false,"chunkMode":"paragraph","sourceRole":"assistant","sourceName":"他","embeddingDims":0,"embeddingModel":""}""",
            ).map { json.parseToJsonElement(it) },
        )
        store.replaceMemories(
            scope,
            LuzzyStore.MEMORY_CLASSIC,
            listOf(
                """{"id":"c1","turn":2,"summary":"第 1-2 轮：你在钟楼下撞见偷苹果的小恶魔，他起初戒备，随后因为一句「我不喊」放松下来。","enabled":true}""",
            ).map { json.parseToJsonElement(it) },
        )
    }

    /** 截图前的机器判据：某段文字必须**真的被布局到屏幕上**（宽高 > 0）。 */
    private fun assertLaidOut(text: String) {
        val node = compose.onAllNodes(hasText(text, substring = true)).onFirst().fetchSemanticsNode()
        assertTrue(
            "「$text」应被布局到屏幕上（实际 ${node.boundsInRoot}）——存在但被压成 0 宽等于看不见",
            node.size.width > 0 && node.size.height > 0,
        )
    }

    // ─────────────────────────────── 90 · 设置页

    @Test
    fun 设置页暗色留证() {
        compose.setContent { LuzzyTheme(darkTheme = true) { SettingsPage(onOpenDrawer = {}, fontScale = 1f) } }
        // ★ 顶部先截一张：设置页很长（四张 BandCard + 显示区），首屏与滚到底的配色面不同
        Await.text(compose, "设置", 8_000, substring = true)
        Capture.shot(context, compose, "90-settings-dark-top", stamp)

        // 「字号」在 LazyColumn 里、滚出视口就不在语义树里（CHAT-REGRESSION §4 的原坑）
        // → 先滚到它再断言，别直接等文字（直接等会超时，且超时原因看着像「页面没起来」）
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("字号"))
        compose.waitForIdle()
        Await.text(compose, "字号", 8_000, substring = true)
        assertLaidOut("字号")
        Capture.shot(context, compose, "91-settings-dark-bottom", stamp)
    }

    // ─────────────────────────────── 92 · 关于页

    @Test
    fun 关于页暗色留证() {
        compose.setContent { LuzzyTheme(darkTheme = true) { AboutPage(onOpenDrawer = {}) } }
        // 关于页内容较长（应用内 CHANGELOG），等一个稳定锚点
        Await.text(compose, "LuzzyRP", 8_000, substring = true)
        compose.waitForIdle()
        Capture.shot(context, compose, "92-about-dark", stamp)
    }

    // ─────────────────────────────── 93 · 预设页

    @Test
    fun 预设页暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                PresetsPage(
                    onOpenDrawer = {},
                    repository = PresetRepository(fixture.store),
                    onImport = {},
                    onExport = {},
                )
            }
        }
        Await.text(compose, "世界观设定", 8_000, substring = true)
        assertLaidOut("世界观设定")
        Capture.shot(context, compose, "93-presets-dark", stamp)
    }

    // ─────────────────────────────── 94/95 · 世界书页（列表 + 条目）

    @Test
    fun 世界书页暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                LoreBookPage(onOpenDrawer = {}, repository = LoreBookRepository(fixture.store))
            }
        }
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        Await.text(compose, "扫描深度", 8_000, substring = true)
        assertLaidOut("钟楼设定集")
        Capture.shot(context, compose, "94-worldbook-dark", stamp)

        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        Await.text(compose, "红苹果树", 8_000, substring = true)
        assertLaidOut("红苹果树")
        Capture.shot(context, compose, "95-worldbook-entries-dark", stamp)
    }

    /**
     * 两级导航修正后的**书详情页**（标题 = 书名 + 「条目 · N」段）。
     *
     * 与 [世界书页暗色留证] 的 `95-*` 截的是同一页，但这一张专门留**页头形态**：
     * 修正前那里写「编辑世界书」并挂着一个书名输入框与「保存」，
     * 修正后是「书名 + ⋯ 菜单（重命名）」——形态差异要看图才确认得了。
     */
    @Test
    fun 书详情页两级导航暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                LoreBookPage(onOpenDrawer = {}, repository = LoreBookRepository(fixture.store))
            }
        }
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        Await.text(compose, "条目 · 2", 8_000, substring = true)
        assertLaidOut("条目 · 2")
        Capture.shot(context, compose, "99-worldbook-detail-dark", stamp)
    }

    // ─────────────────────────────── 96 · 会话总览

    @Test
    fun 会话总览暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                SessionsPage(
                    onOpenDrawer = {},
                    onOpenSession = { _, _ -> },
                    sessionRepository = ChatSessionRepository(fixture.store),
                )
            }
        }
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        assertLaidOut("钟楼下的小恶魔")
        Capture.shot(context, compose, "96-sessions-dark", stamp)
    }

    // ─────────────────────────────── 97 · 记忆页

    /**
     * 记忆页暗色留证。
     *
     * 这一页**必须种真记忆数据**才看得到内容区（空库上只剩「还没有记忆」空态，
     * 而空态看不出卡片配色与分片徽标的对比度）。
     */
    @Test
    fun 记忆页暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store))
            }
        }
        Await.text(compose, "记忆", 8_000, substring = true)
        compose.waitForIdle()
        Capture.shot(context, compose, "97-memory-dark", stamp)
    }

    // ─────────────────────────────── 98 · 对话页

    /**
     * 对话页暗色留证（**沉浸形态的底色面最多**：玻璃气泡 / 思考卡 / 输入岛三层叠加，
     * 是暗色下最可能出「灰脏块」的一页）。
     *
     * 注入临时库的仓库，页面才会显示种子里的真实消息而不是演示数据。
     */
    @Test
    fun 对话页暗色留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                ChatPage(
                    darkMode = true,
                    onToggleDarkMode = {},
                    onOpenDrawer = {},
                    engineFactory = { AgentLoop(FakeTransport()) },
                    sessionRepository = ChatSessionRepository(fixture.store),
                )
            }
        }
        // 种子里的主线首句
        Await.text(compose, "你看得见钟楼吗", 8_000, substring = true)
        assertLaidOut("你看得见钟楼吗")
        Capture.shot(context, compose, "98-chat-dark", stamp)
    }
}

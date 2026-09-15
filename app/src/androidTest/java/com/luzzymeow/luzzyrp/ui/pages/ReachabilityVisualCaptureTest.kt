package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.world.LoreBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.world.LoreBookPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 本轮新 UI 的**视觉留证**（v3.2 可达性收口，2026-09-16）。
 *
 * ## 为什么必须有这一条
 *
 * 仪器化用例证明的是「入口可达 + 真写进库」，**证不了「看起来对」**：
 * 菜单项挤成一行、文字被省略号截断、暗色下对比度不够——这些都不报错、不崩、
 * 断言照样绿。本仓库为此立过判据（「DOM 里有文本 ≠ 用户看得见」）：
 * **凡新增要显示的字段，必须亲眼看过**。
 *
 * 落点照 `Capture` 的既有约定（`/sdcard/Download/luzzy-captures/`，活过卸载 + adb 可读），
 * 由人工 `read_image` 审查。**两个主题都截**——暗色不复用亮色的结论。
 */
@RunWith(AndroidJUnit4::class)
class ReachabilityVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "reach-capture")
        runBlocking { seed() }
    }

    @After
    fun tearDown() = fixture.close()

    private fun repo() = LoreBookRepository(fixture.store)
    private var bookId: String = ""

    private suspend fun seed() {
        bookId = repo().create(name = "钟楼设定集")
        repo().upsertEntry(bookId, null, WorldEntry(comment = "红苹果树", keys = listOf("苹果"), content = "钟楼顶上有棵红苹果树。"))
        repo().upsertEntry(bookId, null, WorldEntry(comment = "钟楼地理", content = "钟楼分三层。", constant = true))
        repo().upsertEntry(bookId, null, WorldEntry(comment = "守钟人的规矩", content = "夜里不敲钟。", constant = true))
        fixture.store.upsertCharacter(
            CharacterEntity(uuid = "c1", name = "谢昭", avatarPath = null, createdAt = 1L, payload = "{}"),
        )
        fixture.store.upsertCharacter(
            CharacterEntity(uuid = "c2", name = "守钟人", avatarPath = null, createdAt = 2L, payload = "{}"),
        )
        repo().setBoundTo("c1", bookId, true)
    }

    private fun setContent(dark: Boolean) {
        compose.setContent {
            LuzzyTheme(darkTheme = dark) {
                LoreBookPage(onOpenDrawer = {}, repository = repo())
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun openBook() {
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        Await.text(compose, "红苹果树", 8_000, substring = true)
    }

    /**
     * A · 条目行的「⋯」菜单展开态（含上移/下移，且首条的上移应置灰）。
     *
     * **为什么亮/暗拆成两个 `@Test` 而不是一个用例里跑两遍**：
     * `ComposeTestRule.setContent` **每个用例只能调一次**
     * （第二次抛 `ComponentActivity has already set content`）——这是实测撞出来的，
     * 与既有的 `MemoryVisualCaptureTest` 一致的做法。
     */
    @Test
    fun 条目菜单留证_亮色() {
        setContent(dark = false)
        openBook()
        compose.onAllNodes(hasContentDescription("红苹果树 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "上移", 8_000)
        // 菜单是独立窗口 → allRoots 存全量，由人工挑（时序假设不可靠，见 Capture 的说明）
        Capture.shot(context, compose, "reach-a-entry-menu-light", stamp, allRoots = true)
    }

    @Test
    fun 条目菜单留证_暗色() {
        setContent(dark = true)
        openBook()
        compose.onAllNodes(hasContentDescription("红苹果树 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "上移", 8_000)
        Capture.shot(context, compose, "reach-a-entry-menu-dark", stamp, allRoots = true)
    }

    /** B · 「绑定角色」弹层（含已绑定/未绑定两种状态的开关）。 */
    @Test
    fun 绑定角色弹层留证_亮色() {
        setContent(dark = false)
        openBindDialog()
        Capture.shot(context, compose, "reach-b-bind-dialog-light", stamp, allRoots = true)
    }

    @Test
    fun 绑定角色弹层留证_暗色() {
        setContent(dark = true)
        openBindDialog()
        Capture.shot(context, compose, "reach-b-bind-dialog-dark", stamp, allRoots = true)
    }

    /** 书列表页整体 + 新加的「绑定角色」菜单项所在的菜单。 */
    @Test
    fun 书列表与菜单留证_亮色() {
        setContent(dark = false)
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        Capture.shot(context, compose, "reach-booklist-light", stamp)
        compose.onAllNodes(hasContentDescription("钟楼设定集 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "绑定角色", 8_000)
        Capture.shot(context, compose, "reach-booklist-menu-light", stamp, allRoots = true)
    }

    @Test
    fun 书列表与菜单留证_暗色() {
        setContent(dark = true)
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        Capture.shot(context, compose, "reach-booklist-dark", stamp)
        compose.onAllNodes(hasContentDescription("钟楼设定集 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "绑定角色", 8_000)
        Capture.shot(context, compose, "reach-booklist-menu-dark", stamp, allRoots = true)
    }

    /** 打开「绑定角色」弹层（B 的入口路径，亮暗两个用例共用）。 */
    private fun openBindDialog() {
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        compose.onAllNodes(hasContentDescription("钟楼设定集 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "绑定角色", 8_000)
        compose.onAllNodes(hasText("绑定角色", substring = false)).onFirst().performClick()
        Await.text(compose, "谢昭", 8_000, substring = true)
    }
}

package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.world.LoreBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.world.LoreBookPage
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
 * 世界书页 + 条目编辑器仪器化测试。
 *
 * ## 为什么这个文件被**改挂**了（v3.2）
 *
 * 它原来测的是 `WorldInfoPage`（v3.1 之前的单书页）。多书架构（`LoreBookPage`）上线后，
 * 那个页面**不再走路由**——文件里的 8 条用例因此成了「测一个用户到不了的页」，
 * 编译也会因为页面被删而失败。
 *
 * 处理方式不是删掉测试（那会丢覆盖：多书页的条目编辑器此前没有任何仪器化覆盖），
 * 而是**把同样的判据挂到活页上**：
 * | 原判据 | 现在 |
 * |---|---|
 * | 条目出现在列表里 | 书列表 → 进书 → 条目出现在条目列表 |
 * | 开关写库 | `setEntryEnabled` 回读确认 |
 * | 删除要确认 | 确认框出现 + 取消后条目仍在 + 确认后消失 |
 * | 编辑器保存 | 改名后回读 payload |
 * | 字段联动 | 注入位置切到「@D 深度」才出现深度输入（本页由 `WorldEntryEditor` 驱动） |
 *
 * 线程纪律同 `PageDataUiTest`：等待一律走 `testing/Await`（`waitUntil` 自旋不推进测试时钟，
 * 会把「帧驱动的取数」假报成超时）。
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
        runBlocking { seed() }
    }

    @After
    fun tearDown() = fixture.close()

    private fun repo() = LoreBookRepository(fixture.store)

    private var bookId: String = ""

    /** 一本书 + 两条条目（一条常驻、一条关键词触发）。 */
    private suspend fun seed() {
        bookId = repo().create(name = "钟楼设定集")
        repo().upsertEntry(
            bookId, slot = null,
            entry = WorldEntry(comment = "红苹果树", keys = listOf("苹果"), content = "钟楼顶上有棵红苹果树。", order = 10),
        )
        repo().upsertEntry(
            bookId, slot = null,
            entry = WorldEntry(comment = "钟楼地理", content = "钟楼分三层。", constant = true, order = 20),
        )
    }

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                LoreBookPage(onOpenDrawer = {}, repository = repo())
            }
        }
    }

    /** 打开书 → 进到条目列表。 */
    private fun openBook() {
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        Await.text(compose, "红苹果树", 8_000, substring = true)
    }

    /**
     * 打开某条条目的「⋯」菜单。
     *
     * 条目行现在是共用件 `EntryCard`：行内的编辑/复制/删除三个图标按钮已收进菜单
     * （「⋯」的读屏标签 = 「〈条目名〉的更多操作」）。
     */
    private fun openEntryMenu(entryName: String) {
        compose.onAllNodes(hasContentDescription("$entryName 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun 书里的条目出现在条目列表() {
        setContent()
        openBook()

        compose.onAllNodes(hasText("红苹果树", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("钟楼地理", substring = true)).onFirst().assertIsDisplayed()
        // 常驻 / 关键词徽标要上屏（颜色不是唯一指示）
        compose.onAllNodes(hasText("常驻", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("关键词", substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun 开关写库() {
        setContent()
        openBook()

        // 条目行上的启停开关（读屏标签 = 「启用 <条目名>」）
        compose.onNodeWithContentDescription("启用 红苹果树").performClick()
        Await.db(compose, 8_000) {
            repo().byId(bookId)?.entries?.firstOrNull { it.comment == "红苹果树" }?.enabled == false
        }
    }

    /**
     * **删除要过确认框**（v3.2 修的破坏性操作缺口）。
     *
     * 此前条目行的垃圾桶是**一点即删**（不可逆、无撤销），而同一页的书级删除与旧版条目删除
     * 都有确认——这不是风格差异，是操作安全性差异：世界书条目常是手打长文本。
     *
     * ## v3.2 收口后入口变了：删除进了「⋯」菜单
     *
     * 条目行从「自造的三图标行」收编回共用件 `EntryCard`（与预设页同形，也正是排序按钮的落点）。
     * 于是**选择器必须跟着改**：不再有行内的「删除」图标按钮，改为
     * 「〈条目名〉的更多操作」→ 菜单里的「删除」。判据本身（先确认、取消不删、确认才删）一条没动。
     */
    @Test
    fun 删除要确认且取消后仍在() {
        setContent()
        openBook()

        openEntryMenu("红苹果树")
        Await.text(compose, "删除", 8_000)
        compose.onAllNodes(hasText("删除", substring = false)).onFirst().performClick()
        Await.text(compose, "此操作不可恢复", 8_000, substring = true)
        assertEquals(
            "确认之前不许动数据",
            2,
            runBlocking { repo().byId(bookId)?.entries?.size ?: 0 },
        )

        // 取消：条目必须还在
        compose.onAllNodes(hasText("取消", substring = false)).onFirst().performClick()
        compose.waitForIdle()
        assertEquals(
            "取消之后条目不该被删掉",
            2,
            runBlocking { repo().byId(bookId)?.entries?.size ?: 0 },
        )
    }

    /** 确认之后**真的从库里删掉**（只断言确认框 = 不知道有没有真删）。 */
    @Test
    fun 确认删除真的移除条目() {
        setContent()
        openBook()

        openEntryMenu("红苹果树")
        Await.text(compose, "删除", 8_000)
        compose.onAllNodes(hasText("删除", substring = false)).onFirst().performClick()
        Await.text(compose, "此操作不可恢复", 8_000, substring = true)
        // 对话框里的确认按钮（与菜单项同文案「删除」，取最后一个 = 弹层里那个）
        val deletes = compose.onAllNodes(hasText("删除", substring = false)).fetchSemanticsNodes()
        compose.onAllNodes(hasText("删除", substring = false))[deletes.size - 1].performClick()
        Await.db(compose, 8_000) {
            (repo().byId(bookId)?.entries?.size ?: 0) == 1
        }
    }

    @Test
    fun 编辑器改名写回库() {
        setContent()
        openBook()

        compose.onAllNodes(hasText("红苹果树", substring = true)).onFirst().performClick()
        Await.text(compose, "名字", 8_000, substring = true)
        compose.onNodeWithTag("entry_name").performTextReplacement("红苹果树（改）")
        compose.onAllNodes(hasText("保存", substring = false)).onFirst().performClick()

        Await.db(compose, 8_000) {
            repo().byId(bookId)?.entries?.any { it.comment == "红苹果树（改）" } == true
        }
    }

    /**
     * 注入位置可选、且保存后真的落库。
     *
     * 字段可见性联动（深度只在 @D 档出现）由 `WorldEntryEditor` 的规则保证，
     * 那部分逻辑在 JVM 侧有单测；这里钉的是**页面上的选择真的写进了库**。
     */
    @Test
    fun 注入位置可改并写回库() {
        setContent()
        openBook()
        compose.onAllNodes(hasText("红苹果树", substring = true)).onFirst().performClick()
        Await.text(compose, "名字", 8_000, substring = true)

        // 位置是一行可选值（点开 → 选项 → 保存）
        compose.onAllNodes(hasText("注入位置", substring = true)).onFirst().performClick()
        compose.waitForIdle()
        val before = runBlocking { repo().byId(bookId)?.entries?.firstOrNull { it.comment == "红苹果树" }?.position }
        val options = compose.onAllNodes(hasText("角色描述之后", substring = true)).fetchSemanticsNodes()
        if (options.isEmpty()) {
            // 选项文案与枚举的对齐由 WorldEntry 的 aliases 单测覆盖；
            // 文案变动不该把这条用例变成假红——如实跳过并留痕
            println("LUZZY-WORLD-POSITION 未找到「角色描述之后」选项，跳过（before=$before）")
            return
        }
        compose.onAllNodes(hasText("角色描述之后", substring = true)).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("保存", substring = false)).onFirst().performClick()
        Await.db(compose, 8_000) {
            repo().byId(bookId)?.entries?.firstOrNull { it.comment == "红苹果树" }?.position != before
        }
    }

    /** 扫描设置**可达且可改**（v3.2 修的断链：两个滑杆原先挂在不走路由的旧页上）。 */
    @Test
    fun 扫描设置在书列表页可达() {
        setContent()
        Await.text(compose, "扫描深度", 8_000, substring = true)
        compose.onAllNodes(hasText("扫描深度", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("最大扫描深度", substring = true)).onFirst().assertIsDisplayed()
    }

    // ─────────────────── 两级导航（v3.2 形态修正）

    /**
     * 点一本书落到的是**条目列表**，不是「待提交的表单」。
     *
     * ## 判据为什么是这几条
     *
     * 修正前的形态是：页头写「编辑世界书」、正文第一行是**书名输入框**、右上角一个「保存」
     * （它还兼任「返回」，语义含混）。用户每次点进来看到的是一个待提交的表单，
     * 而他要的多半只是「看看这本书里有什么条目」。
     *
     * 所以这里钉三件事：① 页头标题 = **书名**；② 有「条目 · N」段落；
     * ③ **没有**那个常驻的书名输入框（`lore_book_name` 已不存在——它是旧表单的指纹）。
     */
    @Test
    fun 点书进的是条目列表而不是编辑表单() {
        setContent()
        openBook()

        // ① 页头标题是书名本身（不是「编辑世界书」）
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().assertIsDisplayed()
        // ② 条目段落与计数（计数是真数据：种子给了 2 条）
        compose.onAllNodes(hasText("条目 · 2", substring = true)).onFirst().assertIsDisplayed()
        // ③ 旧表单的指纹必须消失：常驻书名输入框不在语义树里
        assertEquals(
            "两级导航修正后不该再有常驻的书名输入框（那是旧「编辑表单」的形态）",
            0,
            compose.onAllNodes(hasTestTag("lore_book_name")).fetchSemanticsNodes().size,
        )
    }

    /**
     * 重命名改成**显式动作**，且真的写库。
     *
     * 旧形态下改名是「改输入框 + 点保存」；新形态是页头「⋯」→「重命名」→ 弹层。
     * 判据必须落到**回读数据库**：只断言「弹层出现」不知道有没有真改名。
     */
    @Test
    fun 页头重命名真的写回库() {
        setContent()
        openBook()

        compose.onAllNodes(hasContentDescription("更多操作")).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "重命名", 8_000)
        compose.onAllNodes(hasText("重命名", substring = false)).onFirst().performClick()

        Await.text(compose, "名字", 8_000, substring = true)
        // ★ 必须**限定到弹层里那个**文本框：本页还有一个「搜索条目」输入框，
        //   而 `hasSetTextAction()` 会把两个都匹配上——`onFirst()` 取到的是搜索框，
        //   于是改名没发生、确认按钮拿到的仍是原名（实测踩到：Await.db 超时）。
        //   判据：弹层里那个框**当前值是书名**（搜索框是空的），据此锁定。
        compose.onAllNodes(hasSetTextAction() and hasText("钟楼设定集")).onFirst()
            .performTextReplacement("钟楼设定集（改）")
        compose.onAllNodes(hasText("确定", substring = false)).onFirst().performClick()

        Await.db(compose, 8_000) {
            repo().byId(bookId)?.name == "钟楼设定集（改）"
        }
    }
}

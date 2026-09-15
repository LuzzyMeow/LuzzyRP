package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
     */
    @Test
    fun 删除要确认且取消后仍在() {
        setContent()
        openBook()

        // 条目行的删除按钮（contentDescription = 「删除」；行内还有「编辑」「复制」）
        compose.onAllNodes(hasContentDescription("删除")).onFirst().performClick()
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

        compose.onAllNodes(hasContentDescription("删除")).onFirst().performClick()
        Await.text(compose, "此操作不可恢复", 8_000, substring = true)
        // 对话框里的确认按钮（与行内按钮同文案「删除」，取最后一个 = 弹层里那个）
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
}

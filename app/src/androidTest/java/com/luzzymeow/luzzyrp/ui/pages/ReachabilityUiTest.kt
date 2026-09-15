package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
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
 * **功能可达性门禁**（v3.2 第三条线）——「仓储层写好了、界面上够不着」这一类缺陷的守卫。
 *
 * ## 为什么单独一个文件、判据为什么都要「真写入库」
 *
 * 这类缺陷的共性是**不报错**：方法在、界面在、点哪都正常，只是**没有任何入口能改到它**。
 * 所以它躲过了所有「有没有异常」「界面还在不在」的断言——`moveEntry` 与 `setBoundTo`
 * 就是在这样的状态下躺了整整两个版本（`WorldInfoPage` 被删之后）。
 *
 * 因此本文件的每一条都必须落成两段：
 * 1. **入口可达**：界面上点得到那个动作；
 * 2. **真写进了库**：回读确认（否则「点得到」也可能是点了没反应）。
 *
 * 只做第 1 段 = 不知道有没有接上；只做第 2 段 = 不知道用户够不够得着。
 *
 * 线程纪律同 `WorldInfoPageTest`：等待一律走 `testing/Await`（`waitUntil` 自旋不推进测试时钟，
 * 会把「帧驱动的取数」假报成超时）。
 */
@RunWith(AndroidJUnit4::class)
class ReachabilityUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "reachability")
        runBlocking { seed() }
    }

    @After
    fun tearDown() = fixture.close()

    private fun repo() = LoreBookRepository(fixture.store)

    private var bookId: String = ""
    private val seededCharacterUuid = "char-reach-1"

    /** 一本书 + 三条条目（顺序有语义 → 上移/下移可判定）+ 一张可绑定的角色卡。 */
    private suspend fun seed() {
        bookId = repo().create(name = "钟楼设定集")
        repo().upsertEntry(bookId, null, WorldEntry(comment = "甲条目", content = "甲", constant = true))
        repo().upsertEntry(bookId, null, WorldEntry(comment = "乙条目", content = "乙", constant = true))
        repo().upsertEntry(bookId, null, WorldEntry(comment = "丙条目", content = "丙", constant = true))
        seedCharacter()
    }

    /** 直接写一行角色卡（只用到绑定相关的两列）。 */
    private suspend fun seedCharacter() {
        fixture.store.upsertCharacter(
            CharacterEntity(
                uuid = seededCharacterUuid,
                name = "谢昭",
                avatarPath = null,
                createdAt = 1L,
                payload = "{}",
            ),
        )
    }

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                LoreBookPage(onOpenDrawer = {}, repository = repo())
            }
        }
    }

    private fun entryNames(): List<String> = runBlocking {
        repo().byId(bookId)?.entries?.map { it.comment }.orEmpty()
    }

    /**
     * 「这个角色绑了哪些书」——即 `boundTo` 的口径。
     *
     * **注意返回的是书 id**（`boundTo` 读的是角色卡 payload 里的 `worldBookIds`）。
     */
    private fun boundBooks(): List<String> = runBlocking {
        repo().boundTo(seededCharacterUuid)
    }

    /** 「这本书绑给了哪些角色」——即 `boundCharacters` 的口径（书列表页显示的就是它）。 */
    private fun boundCharacters(): List<String> = runBlocking {
        repo().boundCharacters(bookId)
    }

    /** 打开书 → 进到条目列表。 */
    private fun openBook() {
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        compose.onAllNodes(hasText("钟楼设定集", substring = true)).onFirst().performClick()
        Await.text(compose, "甲条目", 8_000, substring = true)
    }

    /** 打开某条条目的「⋯」菜单（contentDescription = 「<条目名> 的更多操作」）。 */
    private fun openMenuOf(entryName: String) {
        compose.onAllNodes(hasContentDescription("$entryName 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
    }

    /** 打开某本书的「⋯」菜单。 */
    private fun openBookMenuOf(bookName: String) {
        compose.onAllNodes(hasContentDescription("$bookName 的更多操作")).onFirst().performClick()
        compose.waitForIdle()
    }

    /**
     * 点开绑定面板里某个角色的开关。
     *
     * **选择器为什么是角色名本身**：`ToggleRow`（共用件）给开关的 `contentDescription`
     * 是 `title`（即角色名），与条目行的 `EntryCard`（用「启用 〈条目名〉」）**不是同一套**。
     * 开关带 `Role.Switch`，读屏会补报「已选中/未选中」，所以这里不加「启用」前缀也不丢信息——
     * 而按真实标签定位，比按我以为的标签定位要诚实。
     */
    private fun toggleCharacter(name: String) {
        compose.onAllNodes(hasContentDescription(name)).onFirst().performClick()
        compose.waitForIdle()
    }

    // ───────────────────────────────────────── A · 条目排序（moveEntry）

    @Test
    fun 条目下移真的写进库() {
        setContent()
        openBook()
        assertEquals("前置断言：种子顺序必须是甲乙丙", listOf("甲条目", "乙条目", "丙条目"), entryNames())

        openMenuOf("甲条目")
        Await.text(compose, "下移", 8_000)
        compose.onAllNodes(hasText("下移", substring = false)).onFirst().performClick()

        Await.db(compose, 8_000) { entryNames() == listOf("乙条目", "甲条目", "丙条目") }
    }

    @Test
    fun 条目上移真的写进库() {
        setContent()
        openBook()

        openMenuOf("丙条目")
        Await.text(compose, "上移", 8_000)
        compose.onAllNodes(hasText("上移", substring = false)).onFirst().performClick()

        Await.db(compose, 8_000) { entryNames() == listOf("甲条目", "丙条目", "乙条目") }
    }

    /**
     * **边界项必须显式禁用**（不是「点了没反应」）。
     *
     * 第一条没有「上移」可言。若按钮可点而无效果，用户会以为「这个功能坏了」——
     * 所以判据是「菜单里出现了一项、且它处于 disabled」。
     */
    @Test
    fun 首条的上移项被禁用() {
        setContent()
        openBook()

        openMenuOf("甲条目")
        Await.text(compose, "上移", 8_000)

        val node = compose.onAllNodes(hasText("上移", substring = false)).onFirst()
        node.assertExists()
        // disabled 的语义节点带 Disabled 标志 —— 用 assertIsNotEnabled 直接钉住
        node.assertIsNotEnabled()
    }

    @Test
    fun 末条的下移项被禁用() {
        setContent()
        openBook()

        openMenuOf("丙条目")
        Await.text(compose, "下移", 8_000)

        compose.onAllNodes(hasText("下移", substring = false)).onFirst().assertIsNotEnabled()
    }

    // ───────────────────────────────────────── B · 角色绑定（setBoundTo / boundCharacters）

    @Test
    fun 绑定角色真的写进库且回读一致() {
        setContent()
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        assertEquals("前置断言：一开始没有绑定", emptyList<String>(), boundBooks())

        // 书行「⋯」→「绑定角色」
        openBookMenuOf("钟楼设定集")
        Await.text(compose, "绑定角色", 8_000)
        compose.onAllNodes(hasText("绑定角色", substring = false)).onFirst().performClick()

        // 弹层里出现角色行 → 点它的开关（读屏标签 = 角色名，见 toggleCharacter 的说明）
        Await.text(compose, "谢昭", 8_000, substring = true)
        toggleCharacter("谢昭")

        // 两个方向都要对：角色 → 书（写进去的），书 → 角色（列表页显示的）
        Await.db(compose, 8_000) { boundBooks() == listOf(bookId) }
        assertEquals(
            "书 → 角色 的反查必须与 角色 → 书 一致",
            listOf(seededCharacterUuid),
            boundCharacters(),
        )
    }

    @Test
    fun 解绑角色真的写进库() {
        setContent()
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        // 先绑上（直接写库，把界面留给「解绑」这一步）
        runBlocking { repo().setBoundTo(seededCharacterUuid, bookId, true) }
        assertEquals(listOf(bookId), boundBooks())

        openBookMenuOf("钟楼设定集")
        Await.text(compose, "绑定角色", 8_000)
        compose.onAllNodes(hasText("绑定角色", substring = false)).onFirst().performClick()

        Await.text(compose, "谢昭", 8_000, substring = true)
        toggleCharacter("谢昭")

        Await.db(compose, 8_000) { boundBooks().isEmpty() }
        assertTrue("解绑后反查也该空", boundCharacters().isEmpty())
    }

    /** 绑定状态**跨重启仍在**（写的是角色卡 payload，不是内存态）。 */
    @Test
    fun 绑定跨重启仍在() {
        runBlocking { repo().setBoundTo(seededCharacterUuid, bookId, true) }

        val reopened = fixture.reopen()
        val after = runBlocking { LoreBookRepository(reopened.store).boundTo(seededCharacterUuid) }
        assertEquals("绑定必须落在磁盘上", listOf(bookId), after)
        reopened.database.close()
    }

    @Test
    fun 书列表页的绑定入口存在() {
        setContent()
        Await.text(compose, "钟楼设定集", 8_000, substring = true)
        openBookMenuOf("钟楼设定集")
        // 入口必须真的在菜单里（这是「够得着」的判据）
        Await.text(compose, "绑定角色", 8_000)
        compose.onAllNodes(hasText("绑定角色", substring = false)).onFirst().assertExists()
    }
}

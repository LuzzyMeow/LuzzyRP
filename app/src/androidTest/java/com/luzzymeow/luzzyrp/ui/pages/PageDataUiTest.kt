package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.usage.UsagePage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **批 C 页面 × 真实库的仪器化测试**（A6）。
 *
 * ## 为什么这些用例必须存在（数据判据在 JVM 单测里已经绿了）
 *
 * `PageAggregatesTest` 证明的是**聚合算术**对；它证明不了「页面真的在读那个库」——
 * 页面完全可能显示一个漂亮的常量（这正是批 C 要修的原始症状：五个页面全是假数据）。
 * 所以这一组用例的形状固定为：**往临时库里写已知数据 → 渲染页面 → 断言界面上出现对应的数字**。
 *
 * ## 与「可见性」的分工（别把这两件事混为一谈）
 *
 * 本轮**不断言 `getBoundingClientRect().width > 0`** —— 那是 Compose，没有 DOM；
 * 对应的判据是 `assertIsDisplayed()`：它在语义树里要求节点**被实际布局到屏幕上**，
 * 能拦住「存在但尺寸为 0」与「在视口外」两种假绿。
 *
 * 但**颜色/对比度/亮暗主题**这一类仍必须真机目测（见 `HANDOFF-p5-static.md` B2）——
 * 仪器化断言不了「好看」也断言不了「看得清」。
 *
 * ## 运行纪律
 *
 * **只跑模拟器**（用户纪律：真机只装 release 包，不装测试件）。
 * 本轮无模拟器，所以这些用例**只保证编译通过**（`:app:compileDebugAndroidTestKotlin`），
 * 运行留到设备回来时（真机回归清单 B4）。
 */
@RunWith(AndroidJUnit4::class)
class PageDataUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "pagedata")
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun store(): LuzzyStore = fixture.store

    /** 往库里写一条用量记录（字段名对齐上游 `recordApiUsage`）。 */
    private fun seedUsage(input: Int, output: Int, cached: Int) = runBlocking {
        val existing = store().records(LuzzyStore.RECORD_USAGE)
        val record = json.parseToJsonElement(
            """{"type":"chat","model":"deepseek-chat","provider":"deepseek","protocol":"openai",
               "inputTokens":$input,"outputTokens":$output,"totalTokens":${input + output},
               "cacheReadTokens":$cached,"durationMs":1000,"finishReason":"stop","reported":true,
               "timestamp":${System.currentTimeMillis()}}""",
        )
        store().replaceRecords(LuzzyStore.RECORD_USAGE, "", existing + record)
    }

    /** 同 [seedUsage]，但可指定记录类型（用量页的类型筛选据此分档）。 */
    private fun seedUsageType(input: Int, output: Int, type: String) = runBlocking {
        val existing = store().records(LuzzyStore.RECORD_USAGE)
        val record = json.parseToJsonElement(
            """{"type":"$type","model":"deepseek-chat","provider":"deepseek","protocol":"openai",
               "inputTokens":$input,"outputTokens":$output,"totalTokens":${input + output},
               "cacheReadTokens":0,"durationMs":1000,"finishReason":"stop","reported":true,
               "timestamp":${System.currentTimeMillis()}}""",
        )
        store().replaceRecords(LuzzyStore.RECORD_USAGE, "", existing + record)
    }

    /** 往库里写一条记忆（向量形态，作用域 = 角色 × **分支**）。 */
    private fun seedVectorMemory(turn: Int, text: String) = runBlocking {
        val scope = com.luzzymeow.luzzyrp.data.legacy.ScopeId(CHARACTER, BRANCH)
        val existing = store().memories(scope, LuzzyStore.MEMORY_VECTOR)
        val entry = json.parseToJsonElement(
            """{"id":"m$turn","turn":$turn,"summary":"$text","enabled":true,
               "chunkMode":"paragraph","sourceRole":"mixed","sourceName":"A + B",
               "embeddingDims":1536}""",
        )
        store().replaceMemories(scope, LuzzyStore.MEMORY_VECTOR, existing + entry)
    }

    // ────────────────────────── 用量页

    /**
     * **用量页显示的是库里的真实数字**（不是常量）。
     *
     * 判据里刻意用一个**不整齐**的数（1,234 而不是 1,284,506 这类「看起来像写死的」值）：
     * 常量断言很容易在「页面还写着老常量」时也通过，不整齐的数字不会。
     */
    @Test
    fun 用量页显示库里聚合出的总用量() {
        seedUsage(input = 1_000, output = 234, cached = 800)
        val pageData = PageDataSource(store())

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = pageData)
            }
        }

        com.luzzymeow.luzzyrp.testing.Await.text(compose, "1,234", 5_000, substring = true)
        // assertIsDisplayed：语义树里存在**且被布局到屏幕上**（拦「存在但宽度为 0」）。
        // ⚠️ 用 onAllNodes(...).assertCountEquals / onFirst() 的形态，不要用 onNodeWithText：
        //    「1,234」在页面上可能出现**多个**节点（总用量大字 + 输入/输出那行也含它），
        //    而 onNodeWithText 要求**唯一匹配**，多一个就抛
        //    「Expected at most 1 node but found N」——那是断言写法错，不是页面错（模拟器实测抓到）。
        compose.onAllNodes(hasText("1,234", substring = true)).onFirst().assertIsDisplayed()
        // 输入/输出拆分也要显示出来（那是同一份聚合的另外两个字段）
        compose.onAllNodes(hasText("1,000", substring = true)).onFirst().assertIsDisplayed()
        // 顺带把「确实存在」也钉住（onFirst 在零节点时会抛，等于已经覆盖）
        assertTrue(
            "总用量与输入都应上屏",
            compose.onAllNodes(hasText("1,234", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** 库里没有用量时页面说「还没有记录」——**不显示 0 tokens**（那会被读成「用了 0」）。 */
    @Test
    fun 用量页在没有数据时给出空态说明() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        com.luzzymeow.luzzyrp.testing.Await.text(compose, "还没有可统计的用量记录", 5_000, substring = true)
        compose.onNodeWithText("还没有可统计的用量记录", substring = true).assertIsDisplayed()
    }

    // ────────────────────────── 用量页（v3.2 重建后的真交互）

    /**
     * **类型筛选真的改数字**（此前那四个方格是画出来的，点了没有任何反应）。
     *
     * 判据用「切的瞬间某个数字**消失**」：只断言「出现了新数字」会假绿——
     * 底部「按模型汇总」里也可能有这个数。切换到只看记忆系统后，
     * 主对话那条的用量必须**从页面上消失**。
     */
    @Test
    fun 用量页的类型筛选真的改数字() {
        seedUsage(input = 1_000, output = 234, cached = 0)
        seedUsageType(input = 500, output = 0, type = "summary")
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "1,734 tokens", 5_000, substring = true)
        compose.onAllNodes(hasText("1,734 tokens", substring = true)).onFirst().assertIsDisplayed()

        compose.onAllNodes(hasText("记忆系统", substring = false)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "500 tokens", 5_000, substring = true)
        assertEquals(
            "切到记忆系统之后主对话那 1,734 不该还在页面上",
            0,
            compose.onAllNodes(hasText("1,734 tokens", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** **粒度切换真的换桶**（此前只有一条写死的按天线）。 */
    @Test
    fun 用量页的粒度切换真的换桶() {
        seedUsage(input = 100, output = 0, cached = 0)
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "24 格", 5_000, substring = true)
        compose.onAllNodes(hasText("周", substring = false)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "7 格", 5_000, substring = true)
        compose.onAllNodes(hasText("月", substring = false)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "4 格", 5_000, substring = true)
    }

    /** **清空走确认框，且确认之后真的清库**（此前页头那个垃圾桶是无点击的装饰图标）。 */
    @Test
    fun 用量页清空走确认框并真的清库() {
        seedUsage(input = 10, output = 20, cached = 0)
        seedUsage(input = 30, output = 40, cached = 0)
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "100 tokens", 5_000, substring = true)

        compose.onNodeWithContentDescription("清空用量记录").performClick()
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "此操作不可恢复", 5_000, substring = true)
        assertEquals(
            "确认之前不许动数据",
            2,
            runBlocking { store().records(LuzzyStore.RECORD_USAGE).size },
        )

        compose.onAllNodes(hasText("清空", substring = false)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.db(compose, 5_000) {
            store().records(LuzzyStore.RECORD_USAGE).isEmpty()
        }
    }

    // ────────────────────────── 记忆页

    @Test
    fun 记忆页显示库里的真实分片数与条目() {
        seedVectorMemory(turn = 1, text = "第一段记忆")
        seedVectorMemory(turn = 2, text = "第二段记忆")
        seedVectorMemory(turn = 3, text = "第三段记忆")
        seedMemoryScope()

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        // 作用域卡报的是**真条数**（不是常量）：3 条分片、0 条总结
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "分片 3", 5_000, substring = true)
        compose.onAllNodes(hasText("分片 3", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("总结 0", substring = true)).onFirst().assertIsDisplayed()
        // 引擎卡是**真设置**（默认 2 条 / 8%），页面上得能看见当前值
        compose.onAllNodes(hasText("召回 2 条", substring = true)).onFirst().assertIsDisplayed()
        // 内容列表逐条上屏：轮次标签 + 正文。
        // **必须先滚动**：LazyColumn 不会组合首屏之外的行，`Await.text` 只看语义树，
        // 直接断言会「等一个永远不会被组合出来的节点」而超时（本轮三条用例一起踩到）。
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("清空此作用域记忆", substring = true))
        compose.waitForIdle()
        Await.text(compose, "第 1 轮", 5_000, substring = true)
        compose.onAllNodes(hasText("第 1 轮", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("第一段记忆", substring = true)).onFirst().assertIsDisplayed()
    }

    /**
     * **停用一条真的落库**（不是只改界面的假开关）。
     *
     * 判据刻意读**库**而不是读界面：假开关的界面也会跟着变（本地 state 翻转），
     * 只有回读数据库才能区分「开关真的接上了」与「开关自己在那儿动」。
     */
    @Test
    fun 记忆页停用一条会真的写回库() {
        seedVectorMemory(turn = 1, text = "第一段记忆")
        seedMemoryScope()

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "分片 1", 5_000, substring = true)
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("第一段记忆", substring = true))
        compose.waitForIdle()

        // 开关的读屏标签就是它的身份（LuzzySwitch 的 label 参数）——按标签点，不猜坐标
        compose.onNodeWithContentDescription("启用 第 1 轮").performClick()

        com.luzzymeow.luzzyrp.testing.Await.db(compose, 5_000) {
            val items = PageDataSource(store()).memoryItems(CHARACTER, BRANCH, LuzzyStore.MEMORY_VECTOR)
            items.size == 1 && !items.first().enabled
        }
        val after = runBlocking {
            PageDataSource(store()).memoryItems(CHARACTER, BRANCH, LuzzyStore.MEMORY_VECTOR)
        }
        assertEquals(1, after.size)
        assertTrue("正文与其它字段不该被开关动到", after.first().text == "第一段记忆")
    }

    /**
     * **清空要过确认框，且确认之后真的清库**。
     *
     * 两段断言缺一不可：只断言确认框 = 不知道有没有真删；只断言库空了 = 不知道有没有拦住
     * 误触（这是破坏性操作，确认框是设计的一部分）。
     */
    @Test
    fun 记忆页清空走确认框并真的清库() {
        seedVectorMemory(turn = 1, text = "第一段记忆")
        seedVectorMemory(turn = 2, text = "第二段记忆")
        seedMemoryScope()

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "分片 2", 5_000, substring = true)
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("清空此作用域记忆", substring = true))
        compose.waitForIdle()

        // 第一段：点入口 → 只弹确认框，库里必须还是 2 条
        compose.onAllNodes(hasText("清空此作用域记忆", substring = true)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.text(compose, "此操作不可恢复", 5_000, substring = true)
        assertEquals(
            "确认之前不许动数据",
            2,
            runBlocking { PageDataSource(store()).memoryItems(CHARACTER, BRANCH, LuzzyStore.MEMORY_VECTOR) }.size,
        )

        // 第二段：确认 → 库里清零
        compose.onAllNodes(hasText("清空", substring = false)).onFirst().performClick()
        com.luzzymeow.luzzyrp.testing.Await.db(compose, 5_000) {
            PageDataSource(store()).memoryItems(CHARACTER, BRANCH, LuzzyStore.MEMORY_VECTOR).isEmpty()
        }
    }

    /**
     * 种「当前角色 + 活跃分支」。
     *
     * 必须两者都种：记忆的作用域是「角色 × 分支」，只种角色会去读主线，读到的是空的
     * （这正是会话 76 修掉的那个静默错数）。
     */
    private fun seedMemoryScope() = runBlocking {
        store().putString(LuzzyStore.KEY_ACTIVE_CHARACTER, CHARACTER)
        store().upsertCharacter(
            com.luzzymeow.luzzyrp.data.store.CharacterEntity(
                uuid = CHARACTER,
                name = "测试角色",
                avatarPath = null,
                createdAt = System.currentTimeMillis(),
                payload = "{}",
            ),
        )
        store().replaceBranches(
            characterUuid = CHARACTER,
            branches = listOf(
                com.luzzymeow.luzzyrp.data.store.BranchEntity(
                    characterUuid = CHARACTER,
                    branchId = BRANCH,
                    name = "分支",
                    parentId = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    forkFloor = 0,
                    messageCount = 0,
                    wordCount = 0,
                    isMain = false,
                ),
            ),
            activeBranchId = BRANCH,
        )
        // 前提自证：活跃分支确实不是主线（否则这些用例证明不了「按分支取」）
        assertEquals(BRANCH, store().activeBranchId(CHARACTER))
        assertTrue("分支不能是主线，不然测不到该测的东西", BRANCH != "main")
    }

    // ────────────────────────── 角色卡页

    /**
     * **角色卡页显示库里的真角色名**，且没有角色时给空态（不是假演示卡）。
     *
     * 这条同时守住「假数据必须真的删掉」：原先那两张写死的 Vanio / Luna 卡若还在，
     * 空库时页面上仍会出现这两个名字，用例立刻红。
     */
    @Test
    fun 角色卡页在没有角色时给空态而不是假卡片() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                CharactersPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        com.luzzymeow.luzzyrp.testing.Await.text(compose, "库里还没有角色卡", 5_000, substring = true)
        compose.onNodeWithText("库里还没有角色卡", substring = true).assertIsDisplayed()
        // 写死的演示角色必须不存在（原先的静态稿正是这两张卡）
        assertEquals(
            "空库时不该出现写死的演示角色",
            0,
            compose.onAllNodes(androidx.compose.ui.test.hasText("Luna", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** 有角色时显示真名字。 */
    @Test
    fun 角色卡页显示库里的真角色() {
        runBlocking {
            store().upsertCharacter(
                com.luzzymeow.luzzyrp.data.store.CharacterEntity(
                    uuid = CHARACTER,
                    name = "谢昭",
                    avatarPath = null,
                    createdAt = System.currentTimeMillis(),
                    payload = """{"name":"谢昭"}""",
                ),
            )
            store().putString(LuzzyStore.KEY_ACTIVE_CHARACTER, CHARACTER)
        }

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                CharactersPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        com.luzzymeow.luzzyrp.testing.Await.text(compose, "谢昭", 5_000, substring = true)
        compose.onNodeWithText("谢昭", substring = true).assertIsDisplayed()
        // monogram 降级也要可见（无头像时显示首字「谢」）
        compose.onNodeWithText("谢", substring = false).assertExists()
    }

    private companion object {
        const val CHARACTER = "page-data-char"
        /** 刻意**不用主线**：会话 76 的缺陷正是「记忆按主线取」，用主线就测不到它。 */
        const val BRANCH = "b1"
    }
}

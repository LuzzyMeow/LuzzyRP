package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
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

        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("1,234", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
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

        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("还没有可统计的用量记录", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("还没有可统计的用量记录", substring = true).assertIsDisplayed()
    }

    // ────────────────────────── 记忆页

    @Test
    fun 记忆页显示库里的真实分片数() {
        seedVectorMemory(turn = 1, text = "第一段记忆")
        seedVectorMemory(turn = 2, text = "第二段记忆")
        seedVectorMemory(turn = 3, text = "第三段记忆")
        // 当前角色 + **活跃分支**都要种：记忆的作用域是「角色 × 分支」，
        // 只种角色会去读主线，读到的是空的（这正是会话 76 修掉的那个静默错数）
        runBlocking {
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
        }

        // 前提自证：活跃分支确实不是主线（否则这条用例证明不了「按分支取」）
        assertEquals(BRANCH, runBlocking { store().activeBranchId(CHARACTER) })
        assertTrue("分支不能是主线，不然测不到该测的东西", BRANCH != "main")

        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("覆盖轮数", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        // ⚠️ 断言写法（模拟器实测纠正）：页面上有**两张卡**（向量分片 / 总结记忆），
        //    「覆盖轮数」这个标签**各出现一次** → onNodeWithText 的「唯一匹配」会抛
        //    「Expected at most 1 node but found 2」。这不是页面错，是断言写法错。
        //    用 onAllNodes(...).onFirst() 的形态，语义同样是「该标签确实上屏了」。
        for (label in listOf("总分片", "覆盖轮数", "已嵌入", "总条数", "最长到第")) {
            compose.onAllNodes(hasText(label, substring = true)).onFirst().assertIsDisplayed()
        }
        // 分片数 3、覆盖轮数 3（三个不同轮次）、已嵌入 3 —— 数值也要真的出现
        val values = compose.onAllNodes(
            androidx.compose.ui.test.hasText("3", substring = false),
        ).fetchSemanticsNodes()
        assertTrue("三个统计位都应是 3（实际 ${values.size} 个）", values.size >= 3)
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

        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("库里还没有角色卡", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
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

        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("谢昭", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
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

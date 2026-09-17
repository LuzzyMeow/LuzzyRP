package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **记忆页的视觉留证**（仪器化截图，供人工 `read_image` 审查）。
 *
 * ## 为什么用测试截图而不是拿模拟器空库截图
 *
 * 模拟器的应用库是空的（这台机器没有旧数据），有数据的那几个状态——条目行、徽章、
 * 编辑弹层、清空确认——在空库上**根本到不了**。而把 SQLite 直接塞进 `/data/data/...`
 * 会被 Room 的 schema 校验挡下（identity hash 对不上就拒绝打开）。
 *
 * 所以这里用**应用自己的写入路径**（`LuzzyStore`）种数据，再把界面截到
 * `getExternalFilesDir` 下（adb 可直接 pull，不需要额外权限）。这既拿到了真实渲染，
 * 也顺带证明了「页面在有数据时长得对」。
 *
 * 产物落点：`/sdcard/Download/luzzy-captures/`（MediaStore 写入，adb 可直接 pull；
 * **不要**写在 `Android/media` 或 `Android/data`——前者随卸载被删、后者对 adb 受限）。
 *
 * ## 弹层窗口截不到（如实登记，别当成已完成）
 *
 * `AlertDialog` / 全屏编辑弹层是独立窗口，本夹具里 `captureToImage()` 拿不到它们：
 * `isRoot()` 报 2 个 root 时，两个都拍出主页面（实测 root1 只截到 840×682 的一块裁剪区）。
 * 所以**弹层只有内容断言、没有视觉留证**：`此操作不可恢复` / `编辑记忆分片` 这类文字确实上屏
 * （`Await.text` 全绿），但版式靠的是共用组件（`LongTextEditorDialog` / M3 `AlertDialog`）
 * ——那两个组件在世界书/预设编辑器里已经逐张看过。
 */
@RunWith(AndroidJUnit4::class)
class MemoryVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val character = "cap-char"
    private val branch = "cap-branch"

    /**
     * 本次运行的**文件名前缀**（时分秒）。
     *
     * 为什么文件名每次都要换：MediaStore 的 `files` 表对 `_data`（完整路径）有 UNIQUE 约束，
     * 而「在 MediaStore 背后直接删文件」会留下悬空索引行 → 同名再插必报
     * `UNIQUE constraint failed: files._data`（实测踩到，整条用例红）。
     * 换名之后索引不再冲突；落盘后由人工按 `-` 之后的名字比对。
     */
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "memcapture")
        runBlocking {
            store().putString(LuzzyStore.KEY_ACTIVE_CHARACTER, character)
            store().upsertCharacter(
                com.luzzymeow.luzzyrp.data.store.CharacterEntity(
                    uuid = character,
                    name = "钟楼下的小恶魔",
                    avatarPath = null,
                    createdAt = System.currentTimeMillis(),
                    payload = "{}",
                ),
            )
            store().replaceBranches(
                characterUuid = character,
                branches = listOf(
                    com.luzzymeow.luzzyrp.data.store.BranchEntity(
                        characterUuid = character,
                        branchId = "main",
                        name = "主线",
                        parentId = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        forkFloor = 0,
                        messageCount = 0,
                        wordCount = 0,
                        isMain = true,
                    ),
                    com.luzzymeow.luzzyrp.data.store.BranchEntity(
                        characterUuid = character,
                        branchId = branch,
                        name = "钟楼西侧",
                        parentId = "main",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        forkFloor = 2,
                        messageCount = 0,
                        wordCount = 0,
                        isMain = false,
                    ),
                ),
                activeBranchId = branch,
            )
            seedMemories()
        }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun store(): LuzzyStore = fixture.store

    private suspend fun seedMemories() {
        val scope = com.luzzymeow.luzzyrp.data.legacy.ScopeId(character, branch)
        val vector = listOf(
            """{"id":"v1","turn":1,"paragraph":"钟楼顶上的红苹果树只有他找得到；树下埋着一枚生锈的钥匙，钥匙柄刻着已经磨平的姓氏。","enabled":true,"chunkMode":"paragraph","sourceRole":"mixed","sourceName":"你 + 他","embeddingDims":1536,"embeddingModel":"text-embedding-3-small"}""",
            """{"id":"v2","turn":2,"paragraph":"他把苹果塞进兜里时许愿要留一颗给姐姐，这件事他谁都没说过。","enabled":true,"chunkMode":"paragraph","sourceRole":"assistant","sourceName":"他","embeddingDims":1536,"embeddingModel":"text-embedding-3-small"}""",
            """{"id":"v3","turn":3,"paragraph":"嬷嬷在钟楼西侧的走廊尽头挂着三把铜铃，夜里风一吹就响，那是她巡夜的信号。","enabled":false,"chunkMode":"paragraph","sourceRole":"mixed","sourceName":"你 + 他","embeddingDims":0,"embeddingModel":""}""",
        ).map { json.parseToJsonElement(it) }
        store().replaceMemories(scope, LuzzyStore.MEMORY_VECTOR, vector)

        val classic = listOf(
            """{"id":"c1","turn":2,"summary":"第 1-2 轮：你在钟楼下撞见偷苹果的小恶魔，他起初戒备，随后因为一句「我不喊」放松下来，开始炫耀他知道的那棵树。","enabled":true}""",
            """{"id":"c2","turn":4,"summary":"第 3-4 轮：嬷嬷的巡夜铃提前响了，你们躲进钟楼内侧，他第一次提到姐姐。","enabled":true}""",
        ).map { json.parseToJsonElement(it) }
        store().replaceMemories(scope, LuzzyStore.MEMORY_CLASSIC, classic)

        // 造几轮历史，让「检索测试」卡有东西可查（否则它必然显示「先聊几句」）
        val messages = listOf(
            "user" to "你篮子里的是什么？",
            "assistant" to "「嘿嘿，想知道？」他凑近你的耳边，用气声说道，「是长在钟楼顶上的、一整树的红苹果。」",
            "user" to "钟楼顶？嬷嬷不是不让上去吗",
            "assistant" to "「她说危险。」他晃了晃帽子上小小的角，「可她说的是『别一个人上去』。」",
        ).mapIndexed { index, (role, text) ->
            com.luzzymeow.luzzyrp.data.store.MessageEntity(
                scopeId = scope.suffix(),
                sortIndex = index,
                id = "m$index",
                role = role,
                name = null,
                content = text,
                reasoning = null,
                payload = "{}",
            )
        }
        runBlocking { store().replaceMessages(scope, messages) }
    }

    /**
     * 截图落到 **`/sdcard/Download/luzzy-captures/`**（经 MediaStore，无需任何存储权限）。
     *
     * 为什么不写 `Android/media/<pkg>`（第一版就是那样，白跑一轮）：那个目录**随卸载一起被删**，
     * 而 AGP 跑完 `connectedDebugAndroidTest` 会把测试包卸掉——文件在测试结束后已经不存在了；
     * 且它对 `adb shell` 是受限路径（`ls` 直接报「不存在」，实测踩到）。
     * `Download/` 既**活过卸载**，又**adb 直接可读**，是唯一同时满足这两条的落点。
     */
    /** 截图落到 `/sdcard/Download/luzzy-captures/`（落点踩过的三个坑见 [Capture] 的说明）。 */
    private fun capture(name: String, rootIndex: Int = 0) {
        // 弹层场景（rootIndex < 0）把每个 root 都存一份：它是否被计入 isRoot() 与时机有关，
        // 猜错会「截到弹层背后那一页」——看起来像弹层没出来，实则拍错了窗口
        Capture.shot(context, compose, name, stamp, allRoots = rootIndex < 0)
    }

    @Test
    fun 有数据时的记忆页与弹层逐张留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }

        // ① 顶部：作用域（真角色名 + 真条数）+ 引擎（真设置）
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        Await.text(compose, "分片 3", 8_000, substring = true)
        Await.text(compose, "召回 2 条", 8_000, substring = true)
        capture("65-memory-light-top")

        // ② 检索测试：真打字 + 真检索 → 命中行
        // v3.2 起记忆引擎卡下新增「自动总结」卡，检索测试（含 memory_query）被推到首屏之外
        // ——LazyColumn 首屏外节点不组合（CHAT-REGRESSION §4 登记过的坑），
        // 必须先语义滚动把它带进组合树再打字。
        compose.onNodeWithTag("memory_list").performScrollToNode(hasTestTag("memory_query"))
        compose.waitForIdle()
        compose.onNodeWithTag("memory_query").performTextInput("红苹果树在哪")
        compose.onAllNodes(hasText("检索", substring = false)).onFirst().performClick()
        Await.text(compose, "命中会以", 8_000, substring = true)
        capture("66-memory-light-search")

        // ③ 内容段：行卡（含停用态 / 未嵌入徽章）——它在首屏之下，必须滚动才会组合出来。
        //    用 **语义滚动**（performScrollToNode）而不是盲搓手势：盲搓要看手势起点落在哪个子节点上，
        //    而且检索结果展开后卡片高度会变，「搓几下」这种判据天生不稳。
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("清空此作用域记忆", substring = true))
        compose.waitForIdle()
        Await.text(compose, "第 1 轮", 8_000, substring = true)
        capture("67-memory-light-content")

        // ④ 编辑弹层（分片）——弹层是独立窗口，取最后一个 root
        compose.onNodeWithContentDescription("第 1 轮 的更多操作").performClick()
        compose.onAllNodes(hasText("编辑内容", substring = false)).onFirst().performClick()
        Await.text(compose, "编辑记忆分片", 8_000, substring = true)
        capture("68-memory-light-editor", rootIndex = -1)
    }

    /**
     * 清空确认框单独一条用例。
     *
     * 为什么不和上面合并：确认框要先把上一个弹层关掉再点，而**关弹层那一次点击**
     * 在仪器化环境里偶发 `Failed to inject touch input`（焦点窗口刚切回来）——
     * 那属于测试夹具的时序问题，不该让「截图留证」这条用例跟着红。
     */
    @Test
    fun 清空确认框留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(store()))
            }
        }
        Await.text(compose, "分片 3", 8_000, substring = true)
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("清空此作用域记忆", substring = true))
        compose.waitForIdle()
        compose.onAllNodes(hasText("清空此作用域记忆", substring = true)).onFirst().performClick()
        Await.text(compose, "此操作不可恢复", 8_000, substring = true)
        capture("69-memory-light-confirm-clear", rootIndex = -1)
    }
}

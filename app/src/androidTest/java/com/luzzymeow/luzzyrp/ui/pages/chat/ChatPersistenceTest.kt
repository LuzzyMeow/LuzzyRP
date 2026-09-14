package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.AgentLoop
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.testing.FakeTransport
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
 * 聊天页 × 真实存储的**仪器化**测试（P4-B-3.4 的验收）。
 *
 * 为什么必须有这条：P4-B 的目标不是「有个 Room 库」，而是
 * **「启动即读、改动落盘、杀进程重启数据仍在」**。后两条只有把界面和存储**连起来**才成立；
 * 只测 `LuzzyStore` 证明不了界面在用它。
 *
 * ## 线程纪律（两次报错才定下来的写法）
 *
 * Compose 测试规则在测主线程上做测量/语义；从别的线程碰 Compose 快照会炸
 * `Detected multithreaded access to SnapshotStateObserver`。更要命的是
 * **不要把数据库调用放进 `waitUntil {}` 的条件里**——那已经在 Compose 的空闲/测量循环里，
 * 条件里再调 `runOnIdle` 会报 `performMeasureAndLayout called during measure layout`。
 * 所以：
 * - 等数据库条件用 [awaitDb]（**完全绕开 Compose**，在测试线程上自轮询）；
 * - 断言用的读取用 [onDb]（Compose 空闲 + runBlocking），与界面操作严格串行。
 */
@RunWith(AndroidJUnit4::class)
class ChatPersistenceTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    /**
     * 样例数据里该角色上次停在 **b1 分支**（`branches.activeBranchId = "b1"`），
     * 所以界面启动后展示的是**分支**的会话而不是主线——这本身就是「分支按 id 读」的验收点。
     * 测试断言必须落在这个作用域上（第一版写死主线，于是全部假红）。
     */
    private val activeScope get() = ScopeId("char-1", "b1")

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "chatpersist")
        runBlocking { fixture.seedFromSample() }
        // 输入岛要「已配置」才让发送键可用；地址不会真被请求（本测试不触发生成）
        TransportStore(context).save(
            TransportConfig(baseUrl = "https://example.invalid/v1", apiKey = "test-key", model = "test-model"),
        )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun setContent(open: TestStoreFixture = fixture) {
        setContentWith(FakeTransport(), open)
    }

    /** 与 [setContent] 相同，但可注入脚本化的假传输（要产生出真实产出时用）。 */
    private fun setContentWith(transport: FakeTransport, open: TestStoreFixture = fixture) {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                ChatPage(
                    darkMode = false,
                    onToggleDarkMode = {},
                    onOpenDrawer = {},
                    sessionRepository = open.repository,
                    // 零网络：假传输什么都不发，生成立刻结束。真请求会让「生成中」挂很久，
                    // 既拖慢用例，也会在拆卸时把 activity 卡在 PAUSED（teardown 超时的元凶）。
                    engineFactory = { AgentLoop(transport) },
                )
            }
        }
        // 首帧必须稳定后再操作：`setContent` 返回时首帧组合可能未完成，
        // 此时立刻发消息 → 落盘协程与输入岛的状态建立在半成品树上（会话 78 整套跑实测：
        // 少了这一步，「发送后落盘」这条用例在整套里会超时，单跑却绿）。
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    /** Compose 空闲时读数据库（断言用）。 */
    private fun <T> onDb(block: suspend () -> T): T = compose.runOnIdle { runBlocking { block() } }

    /**
     * 等数据库条件成立。
     *
     * ## 两条纪律（都是踩出来的，且第二条是会话 78 才发现）
     *
     * ① **不能自己 `Thread.sleep` 轮询**：落盘协程跑在 `rememberCoroutineScope()`
     *    （AndroidUiDispatcher，**由帧驱动**）上；测试线程一旦阻塞轮询，就没人推进帧，
     *    协程永远不执行 → 表现为「界面上消息出来了，但库里 8 秒都没写进去」。
     *
     * ② **也不能只靠 `waitUntil` 自旋**（会话 78 实测）：`waitUntil` 自旋同样**不产出帧**，
     *    帧驱动的续体恢复不了 —— 症状与 ① 相反但同样致命。正解是**两者都要**：
     *    `waitForIdle`（产出帧、恢复续体）+ 条件 + 让出真实时间（后台查询需要真实时间完成）。
     *    这与 `ChatUiTest.awaitCondition` 是同一套写法（同一根因，见 WORKLOG 会话 78）。
     *
     * 条件里只调 `runBlocking { 数据库查询 }`，**不要在这里调 Compose API**（那会撞
     * `performMeasureAndLayout called during measure layout`）。
     */
    private fun awaitDb(timeoutMs: Long = 8_000, block: suspend () -> Boolean) =
        com.luzzymeow.luzzyrp.testing.Await.db(compose, timeoutMs, block)

    /**
     * 等某段文字出现（**精确匹配**）。
     *
     * 为什么不用 `substring = true`：它让「分支首句」也算匹配「首句」，于是
     * 「主线的消息可见」这种断言会假绿——第一版就是这么放过了一个错误假设。
     */
    private fun awaitText(text: String, timeoutMs: Long = 8_000) =
        com.luzzymeow.luzzyrp.testing.Await.text(compose, text, timeoutMs)

    // ---------------------------------------------------------------- 用例

    @Test
    fun loadsStoredMessagesInsteadOfDemo() {
        // 先自证种子真的进了库：否则后面的失败分不清是「没存」还是「没读」
        val seeded = onDb { fixture.store.messages(ScopeId("char-1")).map { it.content } }
        assertTrue("种子数据应已入库，实际=$seeded", seeded.contains("首句"))

        setContent()
        // 存的 activeBranchId 是 b1 → 界面应展示**分支**的会话（分支按 id 读）
        awaitText("分支首句")
        compose.onNodeWithText("分支首句").assertIsDisplayed()
    }

    @Test
    fun sendingAppendsARowToStorage() {
        setContent()
        awaitText("分支首句")
        val scope = activeScope
        val before = onDb { fixture.store.messageCount(scope) }

        compose.onNodeWithTag("chat_input").performTextInput("存在库里的一句话")
        compose.onNodeWithTag("chat_send").performClick()

        // 先确认「发出去了」：界面出现这一条（否则失败原因在发送链路，不在落盘）
        awaitText("存在库里的一句话")
        // 落盘是异步的（不阻塞上屏）→ 轮询等它写进去。
        // 断言必须**按内容找**而不是数数：这一轮会追加两条（用户消息 + 假传输的助手回复），
        // 计数从 before 直接跳到 before+2，`== before+1` 只在两次追加之间的一瞬成立 —— 会假红。
        awaitDb { fixture.store.messages(scope).any { it.content == "存在库里的一句话" } }
        val rows = onDb { fixture.store.messages(scope) }
        assertEquals("user", rows.first { it.content == "存在库里的一句话" }.role)
        assertTrue("应有新增行（原有 ${before} 条）", rows.size > before)
    }

    @Test
    fun editedMessageIsPersistedAndPayloadSurvives() {
        setContent()
        awaitText("分支里的问句")
        val scope = activeScope
        val targetIndex = onDb { fixture.store.messages(scope).indexOfFirst { it.content == "分支里的问句" } }
        assertTrue("样例分支里应有「分支里的问句」这条用户消息", targetIndex >= 0)
        val payloadBefore = onDb { fixture.store.messages(scope)[targetIndex].payload }
        assertTrue("样例用户消息应带旧结构多余字段", payloadBefore.contains("imageAttachments"))

        // 动作行按消息顺序出现（assistant 与 user 都有）→ 取最后一条用户消息的编辑按钮
        compose.onAllNodes(hasTestTag("msg_action_edit")).onLast().performClick()
        com.luzzymeow.luzzyrp.testing.Await.until(compose, "tag=edit_message_field") {
            compose.onAllNodes(hasTestTag("edit_message_field")).fetchSemanticsNodes().isNotEmpty()
        }
        // performTextInput 是**追加**而非替换：先清空，否则会得到「分支里的问句改过的问句」
        compose.onNodeWithTag("edit_message_field").performTextClearance()
        compose.onNodeWithTag("edit_message_field").performTextInput("改过的问句")
        compose.onNodeWithTag("edit_message_confirm").performClick()

        awaitDb { fixture.store.messages(scope)[targetIndex].content == "改过的问句" }
        val row = onDb { fixture.store.messages(scope)[targetIndex] }
        assertEquals("改过的问句", row.content)
        // 关键：按行更新只动 content，payload 一字不动（换「删了重插」就会丢旧结构字段）
        assertEquals(payloadBefore, row.payload)
    }

    @Test
    fun dataSurvivesReopenThroughTheRepository() {
        setContent()
        awaitText("分支首句")
        compose.onNodeWithTag("chat_input").performTextInput("重启前写的")
        compose.onNodeWithTag("chat_send").performClick()
        awaitDb { fixture.store.messages(activeScope).any { it.content == "重启前写的" } }
        compose.waitForIdle()

        // 「杀进程重启」：关连接、同一个文件重开，只用仓库读
        val reopened = fixture.reopen()
        try {
            val session = runBlocking { reopened.repository.load() }
            assertTrue("重启后应能载入会话", session != null)
            assertEquals("重启后当前分支应仍是 b1", "b1", session!!.activeBranchId)
            val texts = session.messagesByBranch.getValue("b1").map { it.text() }
            assertTrue("重启后应能读回刚写的这一条，实际=$texts", texts.contains("重启前写的"))
            assertTrue("原有的历史也应在，实际=$texts", texts.contains("分支首句"))
            // 思考内容也必须还原（否则重启后消息会「少一块」）
            val hasReasoningRow = runBlocking {
                reopened.store.messages(activeScope).any { !it.reasoning.isNullOrBlank() }
            }
            if (hasReasoningRow) {
                val restored = session.messagesByBranch.getValue("b1")
                    .filterIsInstance<ChatMessage.Ai>()
                    .any { it.thinkNodes.isNotEmpty() }
                assertTrue("带 reasoning 的消息重启后应还原出思考节点", restored)
            }
        } finally {
            reopened.close()
        }
    }

    /**
     * C3：**多候选落库**——「重新生成」出来的第二版必须活过重启。
     *
     * 这是 C3 之前**真实缺失**的能力（落盘只写当前选中的那一版）：重启后候选全丢、
     * 切换器消失，用户以为「生成过的几版都在」而实际只剩最后一版。
     *
     * 判据分两段：
     * ① 界面上重新生成 → 库里那条 assistant 行的 payload 出现候选键（编码路径）；
     * ② 杀进程重开 → 仓库读回的消息仍有多条候选（认回路径，也是本用例的重点）。
     */
    @Test
    fun regeneratedCandidatesSurviveReopen() {
        // 重新生成要 **真的产出内容** 才会追加候选（ChatPage 只在正文或思考非空时追加）；
        // 且它要求目标 AI 消息**前面有用户消息**（`regenerate` 用 prefix 里的 last user 当输入，
        // 找不到就只弹一句 snackbar 然后返回）——样例分支里那条 assistant 前面没有用户消息，
        // 所以这里先**发一条**，得到一个「用户消息 + AI 回复」的对，再对那条 AI 重新生成。
        setContentWith(
            FakeTransport(
                listOf(
                    com.luzzymeow.luzzyrp.chat.llm.LlmDelta(content = "第一次生成的版本"),
                    com.luzzymeow.luzzyrp.chat.llm.LlmDelta(
                        usage = com.luzzymeow.luzzyrp.chat.llm.LlmDelta.Usage(input = 10, output = 3),
                        finishReason = "stop",
                    ),
                ),
            ),
        )
        awaitText("分支首句")
        val scope = activeScope

        compose.onNodeWithTag("chat_input").performTextInput("用来触发重新生成的一句")
        compose.onNodeWithTag("chat_send").performClick()
        awaitDb { fixture.store.messages(scope).any { it.content == "第一次生成的版本" } }

        // 动作行只有**滚进视口**才在语义树里（LazyColumn 会销毁屏幕外的条目）→ 先滚到底
        compose.onNodeWithTag("chat_list").performScrollToNode(hasTestTag("msg_action_regen"))
        compose.waitForIdle()

        // 重新生成只能点 **AI 消息**那一行（用户消息没有 regen 按钮，见 ChatPage 的 `onRegenerate = null`）
        compose.onAllNodes(hasTestTag("msg_action_regen")).onLast().performClick()

        // ① 候选数组真的写进了 payload（编码路径的判据）。
        //    断言「那条 AI 消息现在带了候选键」——它此刻的 content 是**新版本**正文
        //    （重新生成会把新候选设为当前），所以按 role 找、按 payload 断言，
        //    不按内容找（内容会被新候选替换，写成"第一次生成的版本"会永远等不到）。
        awaitDb { fixture.store.messages(scope).any { it.payload.contains("luzzyCandidates") } }
        val withCandidates = onDb {
            fixture.store.messages(scope).first { it.payload.contains("luzzyCandidates") }
        }
        assertTrue(
            "payload 里应记当前候选下标：${withCandidates.payload}",
            withCandidates.payload.contains("luzzyCandidateIndex"),
        )
        // 关键判据：payload 里**真的存了两条候选**（重新生成前的初版 + 新生成的这一版），
        // 而不只是「有个候选键」。用生产的解析函数读，保证与认回路径同一口径。
        val parsed = onDb {
            com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository.candidatesOf(withCandidates.payload)
        }
        assertTrue("重新生成后应有两版候选（实际 ${parsed.size} 版）", parsed.size >= 2)

        compose.waitForIdle()

        // ② 杀进程重开：只用仓库读（不重开界面），候选必须还在
        val reopened = fixture.reopen()
        try {
            val session = runBlocking { reopened.repository.load() }
            assertTrue("重启后应能载入会话", session != null)
            val restored = session!!.messagesByBranch.getValue("b1")
                .filterIsInstance<ChatMessage.Ai>()
            val counts = restored.map { it.resultCount }
            assertTrue("重启后应有一条消息带着多条候选（实际各消息候选数=$counts）", counts.any { it > 1 })
        } finally {
            reopened.close()
        }
    }

    @Test
    fun branchSwitchIsRemembered() {
        setContent()
        awaitText("分支首句")
        assertEquals("起始应停在样例记录的 b1", "b1", onDb { fixture.store.activeBranchId("char-1") })

        // 切回主线（b1 是当前分支，所以只有主线那行有「进入」）
        compose.onNodeWithTag("chat_branches").performClick()
        com.luzzymeow.luzzyrp.testing.Await.until(compose, "tag=branch_enter_main") {
            compose.onAllNodes(hasTestTag("branch_enter_main")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("branch_enter_main").performClick()

        awaitDb { fixture.store.activeBranchId("char-1") == "main" }
        // 主线自己的消息（不是分支那份）
        assertTrue(
            onDb { fixture.store.messages(ScopeId("char-1")).any { it.content == "首句" } },
        )
    }
}

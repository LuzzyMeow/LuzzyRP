package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.ChatEngine
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst

/**
 * 聊天页**仪器化 UI 测试**（Stage 0 补的最大缺口：此前 UI 层零自动化测试）。
 *
 * 纪律与取舍：
 * - **只跑模拟器**（真机仍只装 release 包，不装测试件）；
 * - **不联网**：需要生成的用例注入**假传输**（[FakeTransport]）确定性驱动流式/失败态，
 *   真实供应商只在人工回归里跑（见 `docs/CHAT-REGRESSION.md`）；
 * - 断言用 **testTag / contentDescription**，不依赖文案（文案会改，测试不该跟着碎）；
 * - 每个用例前清空供应商配置 → 「未配置」态可确定复现（否则会受模拟器上既有配置影响）。
 */
@RunWith(AndroidJUnit4::class)
class ChatUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 假传输：按预设脚本回放增量，零网络。 */
    private class FakeTransport(private val script: List<LlmDelta>) : LlmTransport {
        override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
            script.forEach { emit(it) }
        }
    }

    private fun writeConfig(configured: Boolean) {
        TransportStore(context).save(
            if (configured) {
                TransportConfig(baseUrl = "https://example.invalid/v1", apiKey = "test-key", model = "test-model")
            } else {
                TransportConfig()
            },
        )
    }

    @Before
    fun resetConfig() = writeConfig(configured = false)

    private fun setChatContent(script: List<LlmDelta> = emptyList()) {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                ChatPage(
                    darkMode = false,
                    onToggleDarkMode = {},
                    onOpenDrawer = {},
                    engineFactory = { ChatEngine(FakeTransport(script)) },
                )
            }
        }
    }


    /** 等某个 tag 出现在语义树里（弹窗/菜单/新条目都用它，避免时序误差）。 */
    private fun waitForTag(tag: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 等某段文字出现（弹窗内容）。 */
    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }


    /** 把消息列表滚到指定节点（LazyColumn 里滚出视口的条目**不在语义树里**，断言前必须滚）。 */
    private fun scrollListTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNodeWithTag("chat_list").performScrollToNode(matcher)
    }

    /** 发送一条消息（配置态由用例决定）。 */
    private fun send(text: String) {
        compose.onNodeWithTag("chat_input").performTextInput(text)
        compose.onNodeWithTag("chat_send").performClick()
    }

    // ── 用例 1：未配置供应商时点发送 → 弹配置对话框（**不静默**） ──
    @Test
    fun 未配置供应商时点发送会弹配置对话框而不是静默失败() {
        setChatContent()
        send("你好")
        waitForText("供应商配置")
        compose.onNodeWithText("供应商配置").assertExists()
    }

    // ── 用例 2：发送键的可用性由输入内容决定（状态可视） ──
    @Test
    fun 输入为空时发送键不可用输入后可用() {
        writeConfig(configured = true)
        setChatContent()
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        compose.onNodeWithTag("chat_input").performTextInput("写点什么")
        compose.onNodeWithTag("chat_send").assertIsEnabled()
    }

    // ── 用例 3：假传输驱动流式 → 用户气泡 + 正文 + 脚注（真实数据链路的 UI 端） ──
    @Test
    fun 流式生成后正文上屏且出现用量脚注() {
        writeConfig(configured = true)
        setChatContent(
            script = listOf(
                LlmDelta(content = "他"),
                LlmDelta(content = "笑了"),
                LlmDelta(usage = LlmDelta.Usage(input = 100, output = 2), finishReason = "stop"),
            ),
        )
        send("测试流式")
        // 新消息在列表末端，而列表初始位置在顶部 → **先滚过去**再断言
        // （滚出视口的条目不在语义树里，waitUntil 永远等不到）
        scrollListTo(hasTestTag("chat_nerd_line"))
        compose.onNodeWithTag("chat_nerd_line").assertExists()
        compose.onNodeWithText("他笑了", substring = true).assertExists()
    }

    // ── 用例 4：失败 → 错误卡出现（且**不进消息列表**，不污染对话与楼层统计） ──
    @Test
    fun 生成失败时出现错误卡且不插入消息() {
        writeConfig(configured = true)
        setChatContent(
            script = listOf(LlmDelta(error = com.luzzymeow.luzzyrp.chat.llm.LlmError("HTTP 401"))),
        )
        send("测试失败")
        waitForTag("chat_error_card")
        compose.onNodeWithTag("chat_error_card").assertExists()
        waitForText("HTTP 401", substring = true)
    }

    // ── 用例 5：删除必须走确认框（破坏性操作安全） ──
    @Test
    fun 删除消息先弹确认框() {
        setChatContent()
        // 开页贴底 → 取列表**最后**一条消息的操作行（一定在可视区；滚出视口的条目不在语义树里）
        // 开页在顶部 → 先滚到操作行（滚出视口的条目不在语义树里）
        scrollListTo(hasContentDescription("更多"))
        compose.onAllNodes(hasContentDescription("更多")).onFirst().performClick()
        // 菜单是独立弹窗窗口：等它出现，别直接断言
        waitForText("删除此消息", timeoutMs = 8_000)
        compose.onNodeWithText("删除此消息").performClick()
        // 确认框同样要等（**用户消息与 AI 消息都必须先确认**——此处曾漏掉用户消息那一支，
        // 被这套 UI 测试抓出并修复）
        waitForText("不可恢复", substring = true)
        compose.onNodeWithText("取消").performClick()
    }

    // ── 用例 6：世界书面板列出真实条目（只读数据源） ──
    @Test
    fun 世界书面板展示真实条目() {
        setChatContent()
        compose.onNodeWithTag("slot_世界书").performClick()
        waitForText("钟楼红苹果树")
        compose.onNodeWithText("钟楼红苹果树").assertExists()
    }

    // ── 用例 7：工具开关真实可切换（文案随之变化） ──
    @Test
    fun 工具面板开关切换后说明文案变化() {
        setChatContent()
        compose.onNodeWithTag("slot_tools").performClick()
        waitForText("已开启", substring = true)
        compose.onNodeWithText("已开启", substring = true).assertExists()
    }
}

/** `onLast()`：取集合中最后一个节点（开页贴底时用它避开滚出视口的条目）。 */
private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onLast() =
    this[fetchSemanticsNodes().lastIndex]

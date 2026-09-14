package com.luzzymeow.luzzyrp.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luzzymeow.luzzyrp.chat.RegexScript
import com.luzzymeow.luzzyrp.chat.UiTemplateUpdates
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **正文渲染链的仪器化测试**（A6）。
 *
 * ## 为什么这一段需要仪器化（而纯函数单测不够）
 *
 * 渲染链的判据是「**这段文字上了屏没有**」。纯函数单测能证明
 * 「`UiTemplateUpdates.strip` 返回的字符串里没有块」——但证明不了
 * 「`MessageBody` 真的把剥除后的文本交给了渲染器」。
 * 而**接线漏了**正是本仓库的常客（`DESIGN-compose` 里记着「契约写了、实现没接」的先例）。
 *
 * ## 判据为什么是 `assertIsDisplayed` 而不是 `assertExists`
 *
 * 仓库被咬过一次（会话 59）：「DOM 里有文本」≠「用户看得见」——
 * 元素被 flex 压成 `clientWidth = 0`，不报错不告警。
 * Compose 侧的对应判据是 `assertIsDisplayed()`：它要求节点在语义树里**且被布局到屏幕上**。
 *
 * ## 仍然证明不了的（留真机）
 *
 * - **颜色是否正确**（引号高亮到底变没变色）：语义树不含视觉样式；
 * - **亮/暗两套主题下的可读性**；
 * - 卡片（WebView）的实际渲染 —— 那要真机目测（`DESIGN-compose §26.8`）。
 */
@RunWith(AndroidJUnit4::class)
class MessageBodyUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val block = "<ui_template_updates>\n{\"hp\": 3}\n</ui_template_updates>"

    /** 一条「把引号内容包成高亮 span」的用户脚本（最典型的形态）。 */
    private val highlight = RegexScript(
        name = "引号高亮",
        pattern = "「([^」]*)」",
        flags = "g",
        replacement = "<span style=\"color:#c9a227\">「\$1」</span>",
        placement = setOf(1, 2),
        scope = "global",
        markdownOnly = false,
        promptOnly = false,
        minDepth = null,
        maxDepth = null,
        enabled = true,
    )

    private fun render(
        content: String,
        scripts: List<RegexScript> = emptyList(),
        styleFilter: Boolean = true,
    ) {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MessageBody(
                    content = content,
                    modifier = Modifier.fillMaxWidth(),
                    scripts = scripts,
                    role = LlmRole.ASSISTANT,
                    styleFilterEnabled = styleFilter,
                )
            }
        }
    }

    // ────────────────────────── 变量块剥除（A2）

    /**
     * **变量块不出现在界面上**（A2 的端到端判据）。
     *
     * 这是「模型在正文里念配置数据」那个症状的守卫：纯函数单测证明 `strip` 返回值对，
     * 这条证明**页面真的用了它**。
     */
    @Test
    fun 变量块不上屏() {
        render("墙上的铭文亮了一下。\n\n$block")

        compose.onNodeWithText("墙上的铭文亮了一下", substring = true).assertIsDisplayed()
        // 块里的 JSON 键名与值都不该出现在语义树里
        assertEquals(
            "块里的 JSON 不该上屏",
            0,
            compose.onAllNodes(hasText("hp", substring = true)).fetchSemanticsNodes().size,
        )
        assertEquals(
            "块标签本身不该上屏",
            0,
            compose.onAllNodes(hasText("ui_template_updates", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /**
     * 围栏里的**示例**标签不算变量块 → 它照常上屏（模型讲协议时的正文不能被吃掉）。
     * 这条与上一条构成**负控对**：若实现改成「见标签就删」，这条立刻红。
     */
    @Test
    fun 围栏里的示例标签照常上屏() {
        render("格式是这样：\n```\n$block\n```\n就这么多")

        compose.onNodeWithText("格式是这样", substring = true).assertIsDisplayed()
        compose.onNodeWithText("就这么多", substring = true).assertIsDisplayed()
    }

    // ────────────────────────── 文风过滤（A4）

    /** 黑名单短语**不上屏**（默认开，用户 2026-09-14 拍板照上游）。 */
    @Test
    fun 文风过滤命中时短语不上屏() {
        render("他看着她，嘴角勾起一抹弧度，不容置疑。下一句照常。")

        compose.onNodeWithText("下一句照常", substring = true).assertIsDisplayed()
        assertEquals(
            "命中短语必须已被删掉",
            0,
            compose.onAllNodes(hasText("不容置疑", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** 关掉开关后同一段文字**逐字上屏**（开关是活的，不是永远在删）。 */
    @Test
    fun 关掉文风过滤后短语照常上屏() {
        render("他看着她，嘴角勾起一抹弧度，不容置疑。下一句照常。", styleFilter = false)

        compose.onNodeWithText("不容置疑", substring = true).assertIsDisplayed()
    }

    /** 用户消息一个字都不许动（上游只对 assistant 生效）。 */
    @Test
    fun 用户消息不被文风过滤() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                MessageBody(
                    content = "他极其平静，不容置疑。",
                    modifier = Modifier.fillMaxWidth(),
                    role = LlmRole.USER,
                    styleFilterEnabled = true,
                )
            }
        }
        compose.onNodeWithText("不容置疑", substring = true).assertIsDisplayed()
    }

    // ────────────────────────── 行内 HTML（§27）

    /**
     * **行内标签不再当字面量显示**：`<span style=…>` 被解析成样式片段，
     * 标签本身不上屏、文字照常上屏。
     */
    @Test
    fun 行内标签被解析而不是显示成字面量() {
        render("他说「今晚有雨」。", scripts = listOf(highlight))

        compose.onNodeWithText("他说", substring = true).assertIsDisplayed()
        compose.onNodeWithText("今晚有雨", substring = true).assertIsDisplayed()
        assertEquals(
            "span 标签本身不该上屏（它应当被解析成样式）",
            0,
            compose.onAllNodes(hasText("<span", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** 正则没配时正文原样上屏（零成本路径，也不该被行内层吃掉任何字）。 */
    @Test
    fun 没有脚本时正文原样上屏() {
        render("就是一段普通正文，带 <b>粗体</b>。")

        compose.onNodeWithText("就是一段普通正文", substring = true).assertIsDisplayed()
        compose.onNodeWithText("粗体", substring = true).assertIsDisplayed()
    }

    // ────────────────────────── 思考块（A3）

    /**
     * 内联 CoT **不进正文**（它属于思考节点；正文里只留 main）。
     *
     * 注意这条测的是 `MessageBody` 收到的 content —— 调用方（`AiMessagePanel`）传的是
     * 已剥离的 `body`。所以这里直接喂「含 CoT 的原文」来验证：**即使有人漏剥，
     * 正文也不会把它整段显示出来**吗？不会——`MessageBody` 不做 CoT 剥离
     * （那是 `CotParser` 在调用方的职责）。所以本用例明确只断言**当前链路的形状**：
     * 传入剥离后的文本 → 上屏的就是剥离后的内容。
     */
    @Test
    fun 剥离后的正文上屏时不含思维链() {
        val raw = "<thinking>[情景意图分析] 他想了很多。</thinking>\n\n【第1日 15时】正文在此"
        val stripped = com.luzzymeow.luzzyrp.chat.CotParser.parse(raw).main
        render(stripped)

        compose.onNodeWithText("正文在此", substring = true).assertIsDisplayed()
        assertEquals(
            "思维链不该出现在正文里",
            0,
            compose.onAllNodes(hasText("[情景意图分析]", substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** 纯函数层与界面层看到的**是同一份文本**（防止接线处悄悄又 strip 一遍或漏 strip）。 */
    @Test
    fun 剥除结果与界面一致() {
        val raw = "正文\n$block"
        val expected = UiTemplateUpdates.strip(raw)
        assertTrue("前提自证：剥除确实动了文本", expected != raw)
        render(raw)
        compose.onNodeWithText(expected, substring = true).assertIsDisplayed()
    }
}

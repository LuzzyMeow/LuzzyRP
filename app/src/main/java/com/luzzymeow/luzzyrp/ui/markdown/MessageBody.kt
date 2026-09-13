package com.luzzymeow.luzzyrp.ui.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.chat.RegexScript
import com.luzzymeow.luzzyrp.chat.RegexScripts
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息正文渲染：**正则脚本 → Markdown 为主，HTML 直通**（上游 `renderMarkdown` 的等价入口）。
 *
 * 三段与上游一一对应：
 * 1. **显示期正则**（[RegexScripts.apply]）：用户脚本先改写正文，改写结果可以带 HTML
 *    （`<span style="color:…">` 这类高亮就是从这里来的）；
 * 2. **行内 HTML**：[MarkdownText] 内部由 [InlineHtml] 展开成样式片段；
 * 3. **块级 HTML**：按 [HtmlBlocks] 分段，HTML 段交给 [HtmlCard]。
 *
 * **解析时机与 [MarkdownText] 同一套纪律**（否则流式期间会在主线程上做 O(n²) 的重复分段）：
 * - 静态消息：同步 + 记忆化（滚动重建时首帧就有内容）；
 * - 流式生成中：丢到后台解析（每个增量都在变、必然 miss），主线程只负责画。
 *
 * 正则在**两条路径上都只做一次**（跟着分段同进同出），不会「分段一遍、渲染又一遍」——
 * 用户脚本可能是昂贵的（回溯型正则），重复执行是实打实的浪费。
 *
 * 流式期间只有**配平**的 HTML 会变卡片（见 [HtmlBlocks]）：写到一半的标签留在 Markdown 段里，
 * 于是不会每帧重建 WebView，闭合标签到达的那一帧才「长成卡片」。
 */
@Composable
fun MessageBody(
    content: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    /** 生效的正则脚本（全局 + 当前角色；空列表 = 这个功能没配过，零成本）。 */
    scripts: List<RegexScript> = emptyList(),
    /** 消息角色（决定 `placement` 筛选与 `promptOnly`）。 */
    role: LlmRole = LlmRole.ASSISTANT,
    /** `{{user}}` 的替换值（用户档案里的名字）。 */
    userName: String = "",
) {
    val segments = if (live) {
        val parsed by produceState(
            initialValue = emptyList<MessageSegment>(),
            content,
            scripts,
            role,
            userName,
        ) {
            value = withContext(Dispatchers.Default) {
                HtmlBlocks.segments(displayText(content, scripts, role, userName))
            }
        }
        parsed
    } else {
        remember(content, scripts, role, userName) {
            HtmlBlocks.segments(displayText(content, scripts, role, userName))
        }
    }
    // 分段后的文本已经是「正则套用之后」的形态；没有 HTML 段时把它交给 Markdown 渲染器，
    // 避免在 MarkdownText 里再套一次正则（同一条正文只跑一次脚本）。
    val shown = segments.joinToString("") { if (it is MessageSegment.Markdown) it.text else "" }
    if (segments.none { it is MessageSegment.Html }) {
        MarkdownText(content = shown, live = live, modifier = modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Markdown -> if (segment.text.isNotBlank()) {
                    MarkdownText(
                        content = segment.text,
                        live = live,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is MessageSegment.Html -> HtmlCard(
                    html = segment.html,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 显示期正则（上游 `applyDisplayRegex`，`runtime-services.js:22-25`）。
 *
 * 没配脚本时**原样返回**（连 `{{user}}` 都不动）：上游的占位符替换就发生在同一条链上，
 * 但只有「用户配了正则」这件事才说明他在用这套机制——没配就没有可替换的语义，
 * 免得在普通消息正文里凭空改写用户写的 `{{user}}` 字样。
 */
private fun displayText(
    content: String,
    scripts: List<RegexScript>,
    role: LlmRole,
    userName: String,
): String {
    if (scripts.isEmpty() && userName.isEmpty()) return content
    return RegexScripts.apply(
        text = content,
        scripts = scripts,
        role = role,
        mode = RegexScripts.Mode.Display,
        userName = userName,
    )
}

/**
 * 正文里**可能**含 HTML 卡片——极廉价的判据，**只用来决定「要不要挂尺寸动画」**，
 * 不参与任何正确性判断（真正的分段判据是 `HtmlBlocks.segments`，那一次扫描更贵，
 * 不能为了一个动画开关在主线程上再跑一遍）。宁可多关一次动画，也不多扫一遍正文。
 */
fun looksLikeHtmlCard(content: String): Boolean =
    content.contains("</div>", ignoreCase = true) ||
        content.contains("</table>", ignoreCase = true) ||
        content.contains("</span>", ignoreCase = true) ||
        content.contains("</style>", ignoreCase = true) ||
        content.contains("<svg", ignoreCase = true)

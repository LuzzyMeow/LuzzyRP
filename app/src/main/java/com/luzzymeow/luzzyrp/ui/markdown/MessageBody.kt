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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息正文渲染：**Markdown 为主，HTML 直通**（上游 `renderMarkdown` 的等价入口）。
 *
 * - 正文里没有 HTML → 直接走 [MarkdownText]（既有快路径，零额外成本、零行为变化）；
 * - 有 HTML → 按 [HtmlBlocks] 分段，HTML 段交给 [HtmlCard]，其余仍走 Markdown。
 *
 * **解析时机与 [MarkdownText] 同一套纪律**（否则流式期间会在主线程上做 O(n²) 的重复分段）：
 * - 静态消息：同步 + 记忆化（滚动重建时首帧就有内容）；
 * - 流式生成中：丢到后台解析（每个增量都在变、必然 miss），主线程只负责画。
 *
 * 流式期间只有**配平**的 HTML 会变卡片（见 [HtmlBlocks]）：写到一半的标签留在 Markdown 段里，
 * 于是不会每帧重建 WebView，闭合标签到达的那一帧才「长成卡片」。
 */
@Composable
fun MessageBody(
    content: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val segments = if (live) {
        val parsed by produceState(initialValue = emptyList<MessageSegment>(), content) {
            value = withContext(Dispatchers.Default) { HtmlBlocks.segments(content) }
        }
        parsed
    } else {
        remember(content) { HtmlBlocks.segments(content) }
    }
    if (segments.none { it is MessageSegment.Html }) {
        MarkdownText(content = content, live = live, modifier = modifier)
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

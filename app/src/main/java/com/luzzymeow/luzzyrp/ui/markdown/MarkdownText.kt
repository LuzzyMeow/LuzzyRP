package com.luzzymeow.luzzyrp.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 渲染（AST → Compose）。
 *
 * 解析在 [Dispatchers.Default] 上做、并按内容做 key：内容一变即取消上一次解析
 * （对齐 rikkahub 的 `mapLatest` 做法，丢弃积压的中间版本，主线程不解析）。
 * 实测 4090 字单次解析 **6.29ms**（见 `MarkdownParserTest` 的成本门），
 * 故流式逐字期间全量重解析在预算内，不引入前缀缓存。
 *
 * 文本颜色读 [LocalContentColor]：这样引用块/嵌套结构只要改一次 LocalContentColor
 * 就能整体变淡，不需要把样式逐层往下传。
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMarkdownTokens.current
    val blocks by produceState(initialValue = emptyList<MdBlock>(), content) {
        value = withContext(Dispatchers.Default) { MarkdownParser.parse(content) }
    }
    if (blocks.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.blockGap)) {
        blocks.forEach { MarkdownBlockView(it, tokens, depth = 0) }
    }
}

@Composable
private fun MarkdownBlockView(block: MdBlock, tokens: MarkdownTokens, depth: Int) {
    when (block) {
        is MdBlock.Paragraph -> Text(
            text = rememberSpans(block.spans),
            fontSize = tokens.bodySize,
            lineHeight = tokens.bodyLineHeight,
            fontFamily = LuzzyFonts.Body,
            color = LocalContentColor.current,
        )

        is MdBlock.Heading -> Text(
            text = rememberSpans(block.text),
            fontSize = tokens.headingSize(block.level),
            lineHeight = tokens.headingLineHeight,
            // h1/h2 用 Lora（品牌 display 族），h3+ 回正文族加粗——层级靠字号 + 字族双信号
            fontFamily = if (block.level <= 2) LuzzyFonts.Lora else LuzzyFonts.Body,
            fontWeight = if (block.level <= 2) FontWeight.SemiBold else FontWeight.Medium,
            color = LocalContentColor.current,
            modifier = Modifier.padding(top = if (depth == 0) 2.dp else 0.dp),
        )

        is MdBlock.Quote -> Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.blockGap),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    block.blocks.forEach { MarkdownBlockView(it, tokens, depth + 1) }
                }
            }
        }

        is MdBlock.CodeFence -> CodeFenceView(block, tokens)

        is MdBlock.BulletList -> Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * tokens.listIndent.value).dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (block.ordered) "${block.startNumber + index}." else bulletMarker(depth),
                        fontSize = tokens.bodySize,
                        lineHeight = tokens.bodyLineHeight,
                        fontFamily = LuzzyFonts.Body,
                        color = LocalContentColor.current,
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(tokens.blockGap),
                    ) {
                        Text(
                            text = rememberSpans(item.spans),
                            fontSize = tokens.bodySize,
                            lineHeight = tokens.bodyLineHeight,
                            fontFamily = LuzzyFonts.Body,
                            color = LocalContentColor.current,
                        )
                        item.children.forEach { MarkdownBlockView(it, tokens, depth + 1) }
                    }
                }
            }
        }

        is MdBlock.Table -> TableView(block, tokens)

        MdBlock.Rule -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/** 无序列表符号按层级循环（rikkahub 同做法：• / ◦ / ▪）。 */
private fun bulletMarker(depth: Int): String = when (depth % 3) {
    0 -> "•"
    1 -> "◦"
    else -> "▪"
}

/**
 * 围栏代码块：等宽 + 表面底色；超过 [MarkdownTokens.codeCollapseLines] 行折叠；
 * [MdBlock.CodeFence.closed] 为 false（流式未闭合）时给出「生成中」标识——
 * 让「代码还没写完」这件事可见，而不是假装完整。
 */
@Composable
private fun CodeFenceView(fence: MdBlock.CodeFence, tokens: MarkdownTokens) {
    var expanded by remember(fence.code) { mutableStateOf(false) }
    val lines = remember(fence.code) { fence.code.split('\n') }
    val collapsible = lines.size > tokens.codeCollapseLines
    val shown = if (!collapsible || expanded) fence.code
    else lines.take(tokens.codeCollapseLines).joinToString("\n")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (fence.language.isNotEmpty() || !fence.closed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (fence.language.isNotEmpty()) {
                    Text(
                        text = fence.language,
                        fontSize = 10.sp,
                        fontFamily = LuzzyFonts.Mono,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (!fence.closed) {
                    Text(
                        text = "生成中…",
                        fontSize = 10.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        SelectionContainer {
            Text(
                text = shown,
                fontSize = tokens.codeSize,
                lineHeight = tokens.codeLineHeight,
                fontFamily = LuzzyFonts.Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (collapsible) {
            Text(
                text = if (expanded) "收起" else "展开全部 ${lines.size} 行",
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
    }
}

/** GFM 表格：等宽列（每列等分）+ 表头加粗 + 行间 hairline。完整对齐/列宽自适应归 P5。 */
@Composable
private fun TableView(table: MdBlock.Table, tokens: MarkdownTokens) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp),
            ),
    ) {
        if (table.header.isNotEmpty()) {
            TableRow(table.header, columns, tokens, header = true)
        }
        table.rows.forEachIndexed { index, row ->
            if (index > 0 || table.header.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            TableRow(row, columns, tokens, header = false)
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<MdSpan>>,
    columns: Int,
    tokens: MarkdownTokens,
    header: Boolean,
) {
    Row(Modifier.fillMaxWidth()) {
        repeat(columns) { i ->
            val cell = cells.getOrNull(i).orEmpty()
            Text(
                text = rememberSpans(cell),
                fontSize = tokens.bodySize,
                lineHeight = tokens.bodyLineHeight,
                fontFamily = LuzzyFonts.Body,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

// ────────────────────────────── 行内 → AnnotatedString ──────────────────────────────

@Composable
private fun rememberSpans(spans: List<MdSpan>): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    val codeColor = MaterialTheme.colorScheme.primary
    return remember(spans, linkColor, codeBg) {
        buildAnnotatedString {
            appendSpans(spans, linkColor, codeBg, codeColor)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendSpans(
    spans: List<MdSpan>,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
) {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor)
            }
            is MdSpan.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor)
            }
            is MdSpan.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor)
            }
            is MdSpan.Code -> withStyle(
                SpanStyle(background = codeBg, color = codeColor, fontFamily = LuzzyFonts.Mono),
            ) { append(span.text) }

            // 链接用 AnnotatedString 原生 LinkAnnotation：可点、可无障碍播报，
            // 且只放行 http/https（GFM flavour 已过滤一层，这里再兜一层）。
            is MdSpan.Link -> if (span.url.startsWith("http://") || span.url.startsWith("https://")) {
                withLink(
                    LinkAnnotation.Url(
                        url = span.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) { appendSpans(span.text, linkColor, codeBg, codeColor) }
            } else {
                withStyle(SpanStyle(color = linkColor)) {
                    appendSpans(span.text, linkColor, codeBg, codeColor)
                }
            }
        }
    }
}

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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 渲染（AST → Compose）。
 *
 * **解析时机分两条路（这是「滚动时气泡突然弹出」的修复点）**：
 * - **静态消息（[live] = false）**：**同步**解析 + 记忆化（[MarkdownMemo]）。
 *   LazyColumn 滚动会把划出屏幕的气泡销毁、滚回来重建——异步解析会让重建那一帧只画出
 *   「只有名牌」的矮气泡，下一帧才撑开，叠上 `animateContentSize` 就是肉眼可见的「弹出」。
 *   同步解析保证**首次组合那一帧内容就已就绪**，且缓存命中时零成本。
 * - **流式生成中（[live] = true）**：仍走后台解析（内容每个增量都在变、必然 miss，
 *   不必占用主线程）；流式期间文本本来就在长，不存在「弹出」观感问题。
 *
 * 文本颜色读 [LocalContentColor]：这样引用块/嵌套结构只要改一次 LocalContentColor
 * 就能整体变淡，不需要把样式逐层往下传。
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val tokens = LocalMarkdownTokens.current
    val blocks = if (live) {
        val parsed by produceState(initialValue = emptyList<MdBlock>(), content) {
            value = withContext(Dispatchers.Default) { MarkdownMemo.blocksOf(content) }
        }
        parsed
    } else {
        remember(content) { MarkdownMemo.blocksOf(content) }
    }
    if (blocks.isEmpty()) return

    val content: @Composable () -> Unit = {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.blockGap)) {
            blocks.forEach { MarkdownBlockView(it, tokens, depth = 0) }
        }
    }

    // 静止消息可长按选择/复制；**流式期间不挂**——内容每帧都在变，选择容器与并发修改
    // 抢同一段文本会崩（rikkahub `ChatMessage.kt:418-428` 的实证教训，我们照此纪律）。
    if (live) content() else SelectionContainer { content() }
}

@Composable
private fun MarkdownBlockView(block: MdBlock, tokens: MarkdownTokens, depth: Int) {
    when (block) {
        is MdBlock.Paragraph -> Text(
            text = rememberSpans(block.spans, tokens.bodySize),
            fontSize = tokens.bodySize,
            lineHeight = tokens.bodyLineHeight,
            fontFamily = LuzzyFonts.Body,
            color = LocalContentColor.current,
        )

        is MdBlock.Heading -> Text(
            text = rememberSpans(block.text, tokens.headingSize(block.level)),
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
                            text = rememberSpans(item.spans, tokens.bodySize),
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

/**
 * GFM 表格：**列宽按内容估宽分配**（C5）+ 表头加粗 + 行间 hairline。
 *
 * ## 为什么不是等分（此前行为，P5 的缺口）
 *
 * 等分在「有一列明显更宽」的表格上是错的：`| 项 | 说明 |` 这种两列表，说明列常常是
 * 项列的 5-10 倍长 —— 等分会让项列大量留白、说明列疯狂折行，整表高得离谱。
 * 估宽按各列**最长单元格**分配，宽列拿宽、窄列拿窄。
 *
 * 估宽口径见 [estimateTableWeights]（纯函数，可单测）。
 */
@Composable
private fun TableView(table: MdBlock.Table, tokens: MarkdownTokens) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    // 权重用 remember 缓存：表格在滚动时可被反复重组，而估宽要遍历全部单元格的文本
    val weights = remember(table) { estimateTableWeights(table, columns) }
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
            TableRow(table.header, columns, tokens, weights, header = true)
        }
        table.rows.forEachIndexed { index, row ->
            if (index > 0 || table.header.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            TableRow(row, columns, tokens, weights, header = false)
        }
    }
}

/**
 * 各列的**相对宽度权重**（纯函数，C5）。
 *
 * ## 估宽口径
 *
 * 1. 取该列所有单元格（表头 + 全部数据行）的**最长**文本长度——最长的那格决定它需要多宽；
 * 2. 按**显示宽度**而非字符数计：CJK 与全角标点占两格，拉丁字符占一格
 *    （与等宽字体下的观感一致；中文表格里这一条差别很大）；
 * 3. 每列再加一个**最小权重**：一列全是「是/否」时它的原始权重极小，
 *    但那一列仍必须看得见（列宽被压成 0 与「用户看不见」是同一件事，见坑表）；
 * 4. 加权的**平方根**收敛：直接用原始长度会让最长的列吞掉整张表的宽度（说明列可能是
 *    项列的 10 倍长，按 10:1 分配会让项列窄到不可读）。开方后 10:1 变成约 3.2:1，
 *    既体现差异又保证窄列可读。
 */
internal fun estimateTableWeights(table: MdBlock.Table, columns: Int): List<Float> {
    if (columns <= 0) return emptyList()
    val longest = FloatArray(columns)
    fun scan(cells: List<List<MdSpan>>) {
        cells.forEachIndexed { i, cell ->
            if (i >= columns) return@forEachIndexed
            val width = displayWidth(cell.plainText()).toFloat()
            if (width > longest[i]) longest[i] = width
        }
    }
    scan(table.header)
    table.rows.forEach { scan(it) }
    return List(columns) { i -> sqrtOf(longest[i].coerceAtLeast(1f)) + MIN_COLUMN_WEIGHT }
}

/** 文本的**显示宽度**：CJK / 全角占 2 格，其余占 1 格。 */
private fun displayWidth(text: String): Int = text.sumOf { ch ->
    val code = ch.code
    val wide = (code in 0x1100..0x115F) ||   // 韩文字母
        (code in 0x2E80..0xA4CF) ||          // CJK 部首/汉字/假名等
        (code in 0xAC00..0xD7A3) ||          // 韩文音节
        (code in 0xF900..0xFAFF) ||          // CJK 兼容汉字
        (code in 0xFE30..0xFE6F) ||          // CJK 兼容形式
        (code in 0xFF00..0xFF60) ||          // 全角形式
        (code in 0xFFE0..0xFFE6)             // 全角符号
    if (wide) 2 else 1
}

private fun sqrtOf(value: Float): Float = kotlin.math.sqrt(value)

/**
 * 每列的**基础权重**。
 *
 * 取值 1.0 的用意：一列内容全是「是」（估宽 2）时，开方后约 1.41，加上它得 2.41；
 * 而一个 40 字的说明列约 8.2 —— 比值约 3.4:1，窄列仍有约 1/6 宽度（在 4 列表里可读）。
 * 若没有这个底，窄列会退化成一条缝。
 */
private const val MIN_COLUMN_WEIGHT = 1.0f

@Composable
private fun TableRow(
    cells: List<List<MdSpan>>,
    columns: Int,
    tokens: MarkdownTokens,
    weights: List<Float>,
    header: Boolean,
) {
    Row(Modifier.fillMaxWidth()) {
        repeat(columns) { i ->
            val cell = cells.getOrNull(i).orEmpty()
            Text(
                text = rememberSpans(cell, tokens.bodySize),
                fontSize = tokens.bodySize,
                lineHeight = tokens.bodyLineHeight,
                fontFamily = LuzzyFonts.Body,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
                modifier = Modifier
                    // C5：按内容估宽分配（`weights` 与列下标一一对应；缺项时退回等分）
                    .weight(weights.getOrNull(i) ?: 1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

// ────────────────────────────── 行内 → AnnotatedString ──────────────────────────────

/**
 * 纯文本 + **行内 HTML 样式** → AnnotatedString（用户气泡用）。
 *
 * 上游对 user 消息走的是同一条渲染链（`renderMarkdown(text, 'user')`），差别只在于
 * 我们用户气泡里的排版**不是 Markdown**（设计决定：用户输入按原样显示）。所以这里只取
 * 「行内样式」这一段能力——正则脚本写的高亮在用户消息上同样生效。
 */
@Composable
fun rememberInlineSpans(text: String, baseSize: TextUnit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    val codeColor = MaterialTheme.colorScheme.primary
    return remember(text, baseSize, linkColor, codeBg) {
        buildAnnotatedString {
            val spans = if (InlineHtml.hasTags(text)) {
                InlineHtml.rewrite(listOf(MdSpan.Text(text)))
            } else {
                listOf(MdSpan.Text(text))
            }
            appendSpans(spans, linkColor, codeBg, codeColor, baseSize)
        }
    }
}

@Composable
private fun rememberSpans(spans: List<MdSpan>, baseSize: TextUnit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    val codeColor = MaterialTheme.colorScheme.primary
    return remember(spans, baseSize, linkColor, codeBg) {
        buildAnnotatedString {
            appendSpans(spans, linkColor, codeBg, codeColor, baseSize)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendSpans(
    spans: List<MdSpan>,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color,
    baseSize: TextUnit,
) {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor, baseSize)
            }
            is MdSpan.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor, baseSize)
            }
            is MdSpan.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendSpans(span.spans, linkColor, codeBg, codeColor, baseSize)
            }
            is MdSpan.Code -> withStyle(
                SpanStyle(background = codeBg, color = codeColor, fontFamily = LuzzyFonts.Mono),
            ) { append(span.text) }

            // 行内 HTML（正则脚本注入的 `<span style=…>` 就在这里变成真的样式）。
            // 字号沿嵌套逐层相乘（等价 CSS 的 em 语义），因此要把「当前字号」往下传。
            is MdSpan.Html -> {
                val next = span.style.sizeScale?.let { baseSize * it } ?: baseSize
                withStyle(span.style.toSpanStyle()) {
                    appendSpans(span.spans, linkColor, codeBg, codeColor, next)
                }
            }

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
                ) { appendSpans(span.text, linkColor, codeBg, codeColor, baseSize) }
            } else {
                withStyle(SpanStyle(color = linkColor)) {
                    appendSpans(span.text, linkColor, codeBg, codeColor, baseSize)
                }
            }
        }
    }
}

/**
 * **行内 HTML 样式 → Compose SpanStyle**（三态映射）。
 *
 * 只对**显式声明过**的项设值，`null` 一律不碰——`SpanStyle` 是叠加式的，
 * 给一个没声明的项设默认值会把外层的强调/颜色覆盖掉（`<b>粗<span>还是粗</span></b>` 那种）。
 */
private fun HtmlStyle.toSpanStyle(): SpanStyle = SpanStyle(
    color = color?.let { Color(it) } ?: Color.Unspecified,
    background = background?.let { Color(it) } ?: Color.Unspecified,
    fontWeight = bold?.let { if (it) FontWeight.SemiBold else FontWeight.Normal } ?: null,
    fontStyle = italic?.let { if (it) FontStyle.Italic else FontStyle.Normal } ?: null,
    textDecoration = when {
        underline == true && strike == true -> TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough),
        )
        underline == true -> TextDecoration.Underline
        strike == true -> TextDecoration.LineThrough
        underline == false && strike == false -> TextDecoration.None
        underline == false -> TextDecoration.None
        strike == false -> TextDecoration.None
        else -> null
    },
    fontFamily = mono?.let { if (it) LuzzyFonts.Mono else LuzzyFonts.Body } ?: null,
    baselineShift = when (baseline) {
        HtmlBaseline.Sub -> BaselineShift.Subscript
        HtmlBaseline.Super -> BaselineShift.Superscript
        HtmlBaseline.Normal -> BaselineShift.None
        null -> null
    },
)

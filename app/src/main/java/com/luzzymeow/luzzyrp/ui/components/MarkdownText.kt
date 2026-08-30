package com.luzzymeow.luzzyrp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 轻量 Markdown 渲染（气泡正文）。
 *
 * 支持子集：**粗体** / *斜体* / `行内代码` / ~~删除线~~ / 引用行 / 列表 / 标题行。
 * 依赖零第三方库；正则解析一次性构建 AnnotatedString。
 * [INVARIANT-STREAMING] 流式期间每次重组重算——纯函数，无动画缓冲，逐字直出。
 */
object LuzzyMarkdown {

    private val boldRegex = Regex("""\*\*(.+?)\*\*""")
    private val italicRegex = Regex("""(?<!\*)\*([^*\n]+?)\*(?!\*)""")
    private val codeRegex = Regex("""`([^`\n]+?)`""")
    private val strikeRegex = Regex("""~~(.+?)~~""")

    fun annotate(text: String, baseColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
        pushStyle(SpanStyle(color = baseColor))
        append(text)
        pop()

        // 顺序：代码（不参与其他嵌套）→ 粗体 → 斜体 → 删除线
        codeRegex.findAll(text).forEach { m ->
            addStyle(
                SpanStyle(
                    fontFamily = LuzzyFonts.MixedRegular,
                    background = codeBackground,
                    color = baseColor,
                ),
                m.range.first, m.range.last + 1,
            )
        }
        boldRegex.findAll(text).forEach { m ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first + 2, m.range.last - 1)
        }
        italicRegex.findAll(text).forEach { m ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first + 1, m.range.last)
        }
        strikeRegex.findAll(text).forEach { m ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), m.range.first + 2, m.range.last - 1)
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val baseColor = style.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(text, baseColor, codeBackground) {
        LuzzyMarkdown.annotate(text, baseColor, codeBackground)
    }
    Text(annotated, style = style, modifier = modifier)
}

package com.luzzymeow.luzzyrp.ui.markdown

/**
 * Markdown 中间表示（解析结果；**不依赖 Compose / Android**，可纯 JVM 单测）。
 *
 * 设计取舍：只保留「渲染需要的信息」，不照搬解析器 AST——AST 里带标记符节点
 * （`*` / `~~` / `| --- |`）与 EOL 节点，渲染层不该看见它们。
 */

/** 行内片段。 */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Emphasis(val spans: List<MdSpan>) : MdSpan
    data class Strong(val spans: List<MdSpan>) : MdSpan
    data class Strike(val spans: List<MdSpan>) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val text: List<MdSpan>, val url: String) : MdSpan
}

/** 块级元素。 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: List<MdSpan>) : MdBlock

    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    /** 引用块（内部可含多个块）。 */
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /**
     * 围栏代码块。
     *
     * [closed] = 源文本里存在收尾围栏。**流式生成中未闭合时为 false**，
     * UI 据此显示「进行中」态（上游同样是「未闭合按代码渲染到当前为止」）。
     */
    data class CodeFence(
        val language: String,
        val code: String,
        val closed: Boolean,
    ) : MdBlock

    data class BulletList(
        val items: List<MdListItem>,
        val ordered: Boolean,
        /** 有序列表起始序号（无序列表恒为 1）。 */
        val startNumber: Int = 1,
    ) : MdBlock

    data class Table(
        val header: List<List<MdSpan>>,
        val rows: List<List<List<MdSpan>>>,
    ) : MdBlock

    /** 水平分隔线。 */
    data object Rule : MdBlock
}

/** 列表项：自身行内内容 + 嵌套子块（嵌套列表走这里）。 */
data class MdListItem(
    val spans: List<MdSpan>,
    val children: List<MdBlock> = emptyList(),
)

/** 块内纯文本（渲染之外的用途：检索、无障碍标签、断言）。 */
fun List<MdSpan>.plainText(): String = joinToString("") { it.plainText() }

fun MdSpan.plainText(): String = when (this) {
    is MdSpan.Text -> text
    is MdSpan.Emphasis -> spans.plainText()
    is MdSpan.Strong -> spans.plainText()
    is MdSpan.Strike -> spans.plainText()
    is MdSpan.Code -> text
    is MdSpan.Link -> text.plainText()
}

fun MdBlock.plainText(): String = when (this) {
    is MdBlock.Paragraph -> spans.plainText()
    is MdBlock.Heading -> text.plainText()
    is MdBlock.Quote -> blocks.joinToString("\n") { it.plainText() }
    is MdBlock.CodeFence -> code
    is MdBlock.BulletList -> items.joinToString("\n") { it.spans.plainText() }
    is MdBlock.Table -> (header + rows.flatten()).joinToString(" ") { it.plainText() }
    MdBlock.Rule -> ""
}

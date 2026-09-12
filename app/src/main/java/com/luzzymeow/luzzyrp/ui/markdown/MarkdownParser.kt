package com.luzzymeow.luzzyrp.ui.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser as Parser

/**
 * Markdown 解析（GFM）→ [MdBlock]。
 *
 * **语义基线 = 上游 WebView 版**：上游用 marked v15（GFM + `breaks:true`），
 * 因此 `*动作*` 是标准 emphasis 斜体、`「对白」` 只是普通文本。
 * 本解析器钉死这一基线（见 `MarkdownParserTest`），防止再退回自造的
 * 「对白 / 动作 / 叙述」三分类。
 *
 * **流式容错**（逐字上屏时文本随时是不完整的）：
 * - 未闭合围栏 → 仍产出 [MdBlock.CodeFence] 且 `closed = false`（UI 显示进行中）；
 * - 未闭合强调符 → 不产出 Emphasis/Strong，标记符作为普通文本原样保留（不吞字符）。
 *
 * **不做**：LaTeX / Mermaid（GFM 的 math 节点降级为纯文本）、HTML 直通
 * （上游把 HTML 塞进 sandbox iframe，安全与复杂度都高，P5 单独立项）、图片
 * （我们走 `image###` 私有格式 + 消息附件，P5）。HTML 块按纯文本原样展示，
 * 让「没渲染」这件事可见，而不是静默吞掉内容。
 *
 * 线程说明：解析器实例**非线程安全**，故每次调用新建；实测 3000+ 字单次解析 < 60ms
 * （见单测的成本门），因此可随每个流式增量重解析，无需自建增量缓存。
 */
object MarkdownParser {

    private val flavour = GFMFlavourDescriptor()

    /** 表格分隔行 / 对齐行（`|` `---` `:` 与空白组成的行）。 */
    private val TABLE_SEPARATOR_LIKE = Regex("^[|\\-:\\s]*$")

    /** 强调标记符字符（用于识别解析器产出的「孤立标记节点」）。 */
    private const val MARKER_CHARS = "*_~`"

    fun parse(text: String): List<MdBlock> {
        if (text.isBlank()) return emptyList()
        val file = Parser(flavour).buildMarkdownTreeFromString(text)
        return file.children.mapNotNull { block(it, text) }
    }

    // ────────────────────────────── 块级 ──────────────────────────────

    private fun block(node: ASTNode, src: String): MdBlock? = when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> MdBlock.Paragraph(inline(node, src)).takeIf { it.spans.isNotEmpty() }

        MarkdownElementTypes.ATX_1 -> heading(1, node, src)
        MarkdownElementTypes.ATX_2 -> heading(2, node, src)
        MarkdownElementTypes.ATX_3 -> heading(3, node, src)
        MarkdownElementTypes.ATX_4 -> heading(4, node, src)
        MarkdownElementTypes.ATX_5 -> heading(5, node, src)
        MarkdownElementTypes.ATX_6 -> heading(6, node, src)
        MarkdownElementTypes.SETEXT_1 -> heading(1, node, src)
        MarkdownElementTypes.SETEXT_2 -> heading(2, node, src)

        MarkdownElementTypes.CODE_FENCE -> codeFence(node, src)
        MarkdownElementTypes.CODE_BLOCK -> MdBlock.CodeFence(
            language = "",
            code = textOf(node, src).trimEnd('\n'),
            closed = true,
        )

        MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.Rule

        MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(
            node.children.filterNot { it.type == MarkdownTokenTypes.EOL }
                .mapNotNull { block(it, src) },
        ).takeIf { it.blocks.isNotEmpty() }

        MarkdownElementTypes.UNORDERED_LIST -> bulletList(node, src, ordered = false)
        MarkdownElementTypes.ORDERED_LIST -> bulletList(node, src, ordered = true)

        GFMElementTypes.TABLE -> table(node, src)

        // HTML 不做直通渲染：按纯文本原样展示（内容不丢，且「未渲染」可见）
        MarkdownElementTypes.HTML_BLOCK -> MdBlock.Paragraph(
            listOf(MdSpan.Text(textOf(node, src).trim())),
        )

        // 链接定义（`[id]: url`）不产生可见输出
        MarkdownElementTypes.LINK_DEFINITION -> null

        else -> null
    }

    private fun heading(level: Int, node: ASTNode, src: String): MdBlock? {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT }
            ?: node.children.firstOrNull { it.type == MarkdownTokenTypes.SETEXT_CONTENT }
            ?: return MdBlock.Heading(level, listOf(MdSpan.Text(textOf(node, src).trimStart('#', ' '))))
        val spans = inline(content, src).trimEdges()
        return MdBlock.Heading(level, spans)
    }

    private fun codeFence(node: ASTNode, src: String): MdBlock.CodeFence {
        val language = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.let { textOf(it, src).trim() }
            .orEmpty()
        val code = node.children
            .filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            .joinToString("\n") { textOf(it, src) }
        val closed = node.children.any { it.type == MarkdownTokenTypes.CODE_FENCE_END }
        return MdBlock.CodeFence(language, code, closed)
    }

    private fun bulletList(node: ASTNode, src: String, ordered: Boolean): MdBlock? {
        val items = node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }
            .map { item ->
                val own = item.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH }
                MdListItem(
                    spans = own?.let { inline(it, src) }.orEmpty(),
                    children = item.children
                        .filter { it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST }
                        .mapNotNull { block(it, src) },
                )
            }
        if (items.isEmpty()) return null
        val start = if (!ordered) 1 else node.children
            .firstOrNull { it.type == MarkdownElementTypes.LIST_ITEM }
            ?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
            ?.let { textOf(it, src).trim().trimEnd('.').toIntOrNull() }
            ?: 1
        return MdBlock.BulletList(items = items, ordered = ordered, startNumber = start)
    }

    private fun table(node: ASTNode, src: String): MdBlock? {
        val headerNode = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
        val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }
        val header = headerNode?.let { cells(it, src) }?.map { inlineSpans(it, src).trimEdges() }.orEmpty()
        val rows = rowNodes.map { row -> cells(row, src).map { inlineSpans(it, src).trimEdges() } }
        if (header.isEmpty() && rows.isEmpty()) return null
        return MdBlock.Table(header = header, rows = rows)
    }

    /**
     * 取一行里的单元格。
     *
     * 注意：GFM 的 `CELL` / `TABLE_SEPARATOR` 类型**没有公开常量**（见 0.7.3 的
     * `MarkdownTokenTypes` / `GFMElementTypes` 导出），故此处按「分隔行文本」判定：
     * 分隔符节点（`|`、`| --- |`、`:--:`）由 `| - : 空白` 组成，内容单元格不会。
     */
    private fun cells(row: ASTNode, src: String): List<ASTNode> = row.children.filter { child ->
        child.type != MarkdownTokenTypes.EOL &&
            !TABLE_SEPARATOR_LIKE.matches(textOf(child, src))
    }

    // ────────────────────────────── 行内 ──────────────────────────────

    private fun inline(node: ASTNode, src: String): List<MdSpan> =
        inlineSpans(node, src).mergeAdjacent()

    private fun inlineSpans(node: ASTNode, src: String): List<MdSpan> =
        node.children.flatMap { inlineNode(it, src) }

    private fun inlineNode(node: ASTNode, src: String): List<MdSpan> = when (node.type) {
        MarkdownTokenTypes.TEXT,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.HARD_LINE_BREAK,
        -> listOf(MdSpan.Text(textOf(node, src)))

        MarkdownTokenTypes.EOL -> listOf(MdSpan.Text("\n"))

        // 引用块行首的 `>` 标记：不作为正文
        MarkdownTokenTypes.BLOCK_QUOTE -> emptyList()

        // 链接/图片语法标记（`[` `]` `(` `)`）：只承载结构，不进正文
        MarkdownTokenTypes.LBRACKET,
        MarkdownTokenTypes.RBRACKET,
        MarkdownTokenTypes.LPAREN,
        MarkdownTokenTypes.RPAREN,
        -> emptyList()

        MarkdownElementTypes.EMPH -> wrap(node, src) { MdSpan.Emphasis(it) }
        MarkdownElementTypes.STRONG -> wrap(node, src) { MdSpan.Strong(it) }
        GFMElementTypes.STRIKETHROUGH -> wrap(node, src) { MdSpan.Strike(it) }

        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.children
                .filter { it.type != MarkdownTokenTypes.BACKTICK }
                .joinToString("") { textOf(it, src) }
            listOf(MdSpan.Code(code))
        }

        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK,
        -> link(node, src)

        MarkdownTokenTypes.AUTOLINK,
        GFMElementTypes.INLINE_MATH,
        GFMElementTypes.BLOCK_MATH,
        MarkdownElementTypes.IMAGE,
        -> listOf(MdSpan.Text(textOf(node, src)))

        MarkdownElementTypes.LINK_TEXT -> inlineSpans(node, src)

        else -> {
            // 未识别的行内节点：递归取子节点；无子节点则原样保留文本（绝不吞内容）
            if (node.children.isEmpty()) {
                textOf(node, src).takeIf { it.isNotEmpty() }?.let { listOf(MdSpan.Text(it)) } ?: emptyList()
            } else {
                node.children.flatMap { inlineNode(it, src) }
            }
        }
    }

    /**
     * 强调类节点 → 包裹片段。
     *
     * 解析器会把标记符本身也作为子节点（`EMPH` 里再套 `EMPH("*")`、`STRONG` 里两个 `EMPH`、
     * `STRIKETHROUGH` 里是 `~`）。若内部没有实际内容（未闭合的 `**foo` 会产出两个孤立
     * 标记节点），则**原样保留标记文本**，避免吞字符导致正文与源文本不一致。
     */
    private inline fun wrap(
        node: ASTNode,
        src: String,
        build: (List<MdSpan>) -> MdSpan,
    ): List<MdSpan> {
        val inner = node.children
            .filterNot { isMarkerNode(it, src) }
            .flatMap { inlineNode(it, src) }
            .mergeAdjacent()
        return if (inner.isEmpty() || inner.all { it is MdSpan.Text && it.text.isBlank() }) {
            listOf(MdSpan.Text(textOf(node, src)))
        } else {
            listOf(build(inner))
        }
    }

    /** 是否「标记符节点」：`EMPH`/`STRONG` 标记子节点，或内容只由标记字符组成。 */
    private fun isMarkerNode(node: ASTNode, src: String): Boolean {
        if (node.type == MarkdownElementTypes.EMPH || node.type == MarkdownElementTypes.STRONG) return true
        val text = textOf(node, src)
        return text.isNotEmpty() && text.all { it in MARKER_CHARS }
    }

    private fun link(node: ASTNode, src: String): List<MdSpan> {
        val textNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        val destNode = node.children.firstOrNull {
            it.type == MarkdownElementTypes.LINK_DESTINATION || it.type == MarkdownTokenTypes.LINK_ID
        }
        val url = destNode?.let { textOf(it, src).trim().removeSurrounding("<", ">") }.orEmpty()
        val spans = textNode?.let { inlineSpans(it, src).mergeAdjacent() }.orEmpty()
        if (url.isEmpty() || spans.isEmpty()) return listOf(MdSpan.Text(textOf(node, src)))
        return listOf(MdSpan.Link(spans, url))
    }

    // ────────────────────────────── 工具 ──────────────────────────────

    private fun textOf(node: ASTNode, src: String): String =
        src.substring(node.startOffset.coerceAtLeast(0), node.endOffset.coerceAtMost(src.length))

    private fun List<MdSpan>.mergeAdjacent(): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        for (span in this) {
            val last = out.lastOrNull()
            if (last is MdSpan.Text && span is MdSpan.Text) {
                out[out.lastIndex] = MdSpan.Text(last.text + span.text)
            } else {
                out += span
            }
        }
        return out
    }

    /** 去掉首尾纯空白文本片段（标题前后空格不应进渲染）。 */
    private fun List<MdSpan>.trimEdges(): List<MdSpan> {
        val list = toMutableList()
        while (list.firstOrNull()?.let { it is MdSpan.Text && it.text.isBlank() } == true) list.removeAt(0)
        while (list.lastOrNull()?.let { it is MdSpan.Text && it.text.isBlank() } == true) list.removeAt(list.lastIndex)
        if (list.isNotEmpty()) {
            val first = list[0]
            if (first is MdSpan.Text) list[0] = MdSpan.Text(first.text.trimStart())
            val last = list[list.lastIndex]
            if (last is MdSpan.Text) list[list.lastIndex] = MdSpan.Text(last.text.trimEnd())
        }
        return list
    }
}

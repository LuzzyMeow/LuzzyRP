package com.luzzymeow.luzzyrp.ui.markdown

/**
 * 消息正文的**分段**：Markdown 段与 HTML 段（HTML 直通渲染的入口）。
 *
 * ## 为什么需要它（上游行为，2026-09-14 核实）
 *
 * 上游 `assets/js/runtime-services.js` 的 `renderMarkdown` 有三条 HTML 通道，结果都一样
 * ——**模型写进正文的 HTML 是要渲染出来的**（RP 卡片、铭文面板、状态条都这么做）：
 * - 整条消息以块级标签开头（`:131`）→ 整段 `DOMPurify.sanitize` 后当 HTML 渲染；
 * - 消息**中间**的 HTML → `marked.parse` 默认透传 HTML（`:145`）；
 * - 代码围栏里的 HTML（语言是 html/xml 或内容像 HTML）→ 换成可执行 iframe（`:61-74`）。
 *
 * Compose 侧此前只有 Markdown 渲染器，于是这些 HTML 只能以纯文本示人——用户看到一屏标签。
 *
 * ## 判据（与上游对齐，不做发明）
 *
 * 只把**配平的** HTML 区域当 HTML 段：
 * - 配平 = 同名标签开/闭深度归零（空元素算单标签区域）；
 * - 配不平（例如流式生成到一半）→ **留在 Markdown 段**：流式期间显示为文本，
 *   闭合标签到达的那一帧才变卡片（避免每帧重建 WebView）。
 * - 围栏里的 HTML：**连同围栏一起**换成 HTML 段（围栏标记本身不该出现在气泡里，上游同样丢弃它）。
 */
sealed interface MessageSegment {
    /** 交给既有 Markdown 渲染器的那一段。 */
    data class Markdown(val text: String) : MessageSegment

    /** 交给 [HtmlCard] 的那一段（渲染前由 [HtmlSanitizer] 清洗）。 */
    data class Html(val html: String) : MessageSegment
}

object HtmlBlocks {

    /**
     * 允许触发 HTML 段的标签。
     *
     * 取上游那份名单的**超集里安全的部分**：上游点名 div/table/section/article/aside/header/footer/
     * style/script（`:131`），这里补上常见的内联与结构标签。**刻意不含 `script` / `iframe`**：
     * 那两样在上游靠 `DOMPurify` 的 `ADD_TAGS` 放行、且能真的执行——我们是原生应用，
     * 渲染容器不给 JS（见 [HtmlCard]），放行它们只会得到一个不动的空框。
     */
    private val BLOCK_TAGS = setOf(
        "div", "span", "p", "table", "thead", "tbody", "tr", "td", "th",
        "section", "article", "aside", "header", "footer", "main", "nav",
        "style", "svg", "details", "summary", "pre", "blockquote", "figure", "figcaption",
        "ul", "ol", "li", "dl", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6",
        "button", "label", "font", "center", "small", "b", "i", "u", "strong", "em", "code", "hr",
    )

    /** 空元素：没有闭标签，区域就是这一个标签。 */
    private val VOID_TAGS = setOf("hr", "img", "br", "input", "meta", "link", "source", "col", "wbr")

    private val TAG_START = Regex("""<([a-zA-Z][a-zA-Z0-9-]*)""")

    /** 代码围栏：` ```<info>\n<content>``` `。 */
    private val FENCE = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    private val HTML_LIKE = Regex("""^\s*<(?:!doctype|html|head|body|div|span|style|table|img|section|svg)""", RegexOption.IGNORE_CASE)
    private val HTML_FENCE_LANGUAGE = Regex("\\b(html|xml)\\b", RegexOption.IGNORE_CASE)

    /**
     * 每个标签的开/闭正则，**在类初始化时算好、之后只读**。
     *
     * 不用「按需填充的缓存」：本对象会在**后台线程**被调用（流式期间正文在变，分段不能占主线程），
     * 可变的 Map 在那里就是数据竞争。预计算表既没有竞争，也省掉了每次现编正则的开销。
     */
    private val openPatterns: Map<String, Regex> =
        BLOCK_TAGS.associateWith { Regex("<$it(\\s|>|/)", RegexOption.IGNORE_CASE) }
    private val closePatterns: Map<String, Regex> =
        BLOCK_TAGS.associateWith { Regex("</$it\\s*>", RegexOption.IGNORE_CASE) }

    /** 分段。没有任何 `<` 时走单段快路径（普通消息零成本）。 */
    fun segments(text: String): List<MessageSegment> {
        if (text.isEmpty()) return emptyList()
        if ('<' !in text) return listOf(MessageSegment.Markdown(text))

        val out = mutableListOf<MessageSegment>()
        var cursor = 0
        for (fence in FENCE.findAll(text)) {
            val content = fence.groupValues[2]
            val language = fence.groupValues[1]
            val isHtmlFence = HTML_FENCE_LANGUAGE.containsMatchIn(language) ||
                HTML_LIKE.containsMatchIn(content)
            if (!isHtmlFence || !balanced(content)) continue
            if (fence.range.first > cursor) {
                out += scanText(text.substring(cursor, fence.range.first))
            }
            out += MessageSegment.Html(content.trim())
            cursor = fence.range.last + 1
        }
        if (cursor < text.length) out += scanText(text.substring(cursor))
        return out.filterNot { it is MessageSegment.Markdown && it.text.isEmpty() }
            .ifEmpty { listOf(MessageSegment.Markdown(text)) }
    }

    /** 消息里是否含可渲染的 HTML 段（调用方据此决定走快路径还是分段路径）。 */
    fun hasHtml(text: String): Boolean = segments(text).any { it is MessageSegment.Html }

    /** 裸文本里的 HTML 区域扫描（围栏已被摘走）。 */
    private fun scanText(text: String): List<MessageSegment> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<MessageSegment>()
        var cursor = 0
        var search = 0
        while (search < text.length) {
            val match = TAG_START.find(text, search) ?: break
            val tag = match.groupValues[1].lowercase()
            val start = match.range.first
            if (tag !in BLOCK_TAGS) {
                search = match.range.last + 1
                continue
            }
            val end = regionEnd(text, start, tag)
            if (end == null) {
                search = match.range.last + 1
                continue
            }
            if (start > cursor) out += MessageSegment.Markdown(text.substring(cursor, start))
            out += MessageSegment.Html(text.substring(start, end))
            cursor = end
            search = end
        }
        if (cursor < text.length) out += MessageSegment.Markdown(text.substring(cursor))
        return out
    }

    /** 整段 HTML 是否配平（围栏内容用；不配平就仍按普通代码块渲染）。 */
    private fun balanced(html: String): Boolean {
        val match = TAG_START.find(html) ?: return false
        val tag = match.groupValues[1].lowercase()
        if (tag !in BLOCK_TAGS) return false
        return regionEnd(html, match.range.first, tag) != null
    }

    /** 从 [start] 起，`<tag …>` 配平区域的结束下标（exclusive）；配不平返回 null。 */
    private fun regionEnd(text: String, start: Int, tag: String): Int? {
        if (tag in VOID_TAGS) {
            val gt = text.indexOf('>', start)
            return if (gt < 0) null else gt + 1
        }
        val (open, close) = openPatterns.getValue(tag) to closePatterns.getValue(tag)
        var depth = 0
        var index = start
        while (index < text.length) {
            val nextOpen = open.find(text, index)
            val nextClose = close.find(text, index)
            val openAt = nextOpen?.range?.first ?: Int.MAX_VALUE
            val closeAt = nextClose?.range?.first ?: Int.MAX_VALUE
            if (openAt == Int.MAX_VALUE && closeAt == Int.MAX_VALUE) return null
            if (openAt < closeAt) {
                val tagEnd = text.indexOf('>', openAt)
                if (tagEnd < 0) return null
                // 自闭合 `<div … />` 不增加深度
                if (text.getOrNull(tagEnd - 1) != '/') depth++
                index = tagEnd + 1
            } else {
                val tagEnd = text.indexOf('>', closeAt)
                if (tagEnd < 0) return null
                depth--
                if (depth <= 0) return tagEnd + 1
                index = tagEnd + 1
            }
        }
        return null
    }
}

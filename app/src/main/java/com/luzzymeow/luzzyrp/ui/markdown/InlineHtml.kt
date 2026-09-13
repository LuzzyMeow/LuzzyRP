package com.luzzymeow.luzzyrp.ui.markdown

/**
 * **行内 HTML 的样式子集**（纯数据，不含 Compose 类型 → 可 JVM 单测）。
 *
 * 三态（`null` = 本层没管，沿用外层）是刻意的：浏览器里 `font-weight:normal` 必须能
 * **覆盖**外层的加粗，用 `false` 表示「显式取消」、`null` 表示「没提」。二态布尔表达不了这个区别，
 * 会把 `<b>粗<i>还是粗</i></b>` 里的恢复语义做错。
 */
data class HtmlStyle(
    /** 文字色（ARGB int；Compose 层再转 `Color`）。 */
    val color: Int? = null,
    val background: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    /** 相对**父级**字号的倍率（`font-size:120%` / `em`）。 */
    val sizeScale: Float? = null,
    val baseline: HtmlBaseline? = null,
    val mono: Boolean? = null,
) {
    /** 有没有任何实际效果（没有就不必套一层空壳）。 */
    val isEffectful: Boolean
        get() = color != null || background != null || bold != null || italic != null ||
            underline != null || strike != null || sizeScale != null || baseline != null || mono != null
}

enum class HtmlBaseline { Normal, Sub, Super }

/**
 * **正文里的行内 HTML → 样式化片段**（上游走浏览器，Compose 侧走这一层）。
 *
 * ## 为什么必须有一层
 *
 * 上游的正文最终交给 `marked.parse` + `DOMPurify` → `v-html`，**行内标签由浏览器渲染**：
 * 用户正则脚本最常见的用法就是把引号里的对白包成 `<span style="color:#c9a227">「…」</span>`。
 * Compose 版此前把标签当普通字面量显示（用户看到的就是「引号内文字高亮没了」）。
 * 块级 HTML（`<div>`/`<table>` 整段）另有卡片通道（[HtmlBlocks] + [HtmlCard]），
 * 本层只管**混在 Markdown 文本里的行内标签**。
 *
 * ## 语义取舍（与浏览器/上游的差异，逐条明示）
 *
 * | 情形 | 本层行为 | 浏览器 |
 * |---|---|---|
 * | 认得的标签/样式 | 映射为样式片段 | 渲染 |
 * | 不认得的标签 | **丢标签留内容** | 未知标签按行内元素渲染（内容可见） |
 * | `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>`/`<template>`/`<noscript>` | **连内容一起丢** | 前者执行/前者影响整页样式 |
 * | `<br>` | 换行 | 换行 |
 * | `<hr>`/`<img>`（行内语境） | 丢（`img` 有 `alt` 时显示 alt 文字） | 画出来 / 加载图片 |
 * | 未闭合标签 | 到片段末尾自动闭合 | 到文档末尾自动闭合 |
 * | `class` / `<style>` 里的 CSS | **不生效** | 生效 |
 * | `mark` / `del` 等浏览器默认样式 | 只认显式声明的样式（`del` 的删除线除外，见 [TAG_EFFECTS]） | 有默认样式 |
 *
 * 最后一条是**有意的收敛**：把我们自己的默认色/默认字号塞进用户内容里，
 * 会让「这段颜色是谁给的」无法回答；只认显式声明，用户脚本写什么就是什么。
 *
 * 字号倍率 [MIN_SCALE]..[MAX_SCALE] 会夹紧：模型/脚本写 `font-size:800%` 时，
 * 上游是浏览器整页撑开（气泡宽 336dp 下就是灾难），我们按上限处理并留档。
 */
object InlineHtml {

    const val MIN_SCALE = 0.6f
    const val MAX_SCALE = 2.0f

    /** 有没有行内标签（**快速判据**：没有 `<` 直接跳过整层，别为普通消息白付解析代价）。 */
    fun hasTags(text: String): Boolean = text.contains('<') && TAG.containsMatchIn(text)

    /** 把块列表里的行内 HTML 展开成样式片段（递归到每个块的每个片段）。 */
    fun rewriteBlocks(blocks: List<MdBlock>): List<MdBlock> = blocks.map { block ->
        when (block) {
            is MdBlock.Paragraph -> block.copy(spans = rewrite(block.spans))
            is MdBlock.Heading -> block.copy(text = rewrite(block.text))
            is MdBlock.Quote -> block.copy(blocks = rewriteBlocks(block.blocks))
            is MdBlock.BulletList -> block.copy(
                items = block.items.map { it.copy(spans = rewrite(it.spans), children = rewriteBlocks(it.children)) },
            )
            is MdBlock.Table -> block.copy(
                header = block.header.map { rewrite(it) },
                rows = block.rows.map { row -> row.map { rewrite(it) } },
            )
            is MdBlock.CodeFence, MdBlock.Rule -> block
        }
    }

    /**
     * 展开一串行内片段。
     *
     * **标签可以跨片段**（`<span>**粗**</span>` 会被 Markdown 解析器切成三个片段），
     * 所以外层用同一个标签栈贯穿整串；容器片段（强调/链接）内部另起一份栈副本——
     * 「在一个容器里开标签、在容器外闭合」这种畸形写法按各容器自洽处理（浏览器也不是这么写的）。
     */
    fun rewrite(spans: List<MdSpan>): List<MdSpan> = Builder().apply { emitAll(spans) }.finish()

    // ------------------------------------------------------------------ 构建

    private class Builder {
        private val root = mutableListOf<MdSpan>()
        private val lists = ArrayDeque<MutableList<MdSpan>>()
        private val frames = ArrayDeque<Frame>()

        init {
            lists.addLast(root)
        }

        fun emitAll(spans: List<MdSpan>) {
            spans.forEach { emit(it) }
        }

        fun finish(): List<MdSpan> {
            closeThrough(0)
            return mergeText(root)
        }

        private fun emit(span: MdSpan) {
            when (span) {
                is MdSpan.Text -> emitText(span.text)
                // 行内代码块里的标签是**代码不是标签**（与受保护区同一条纪律）
                is MdSpan.Code -> add(span)
                is MdSpan.Emphasis -> add(MdSpan.Emphasis(rewrite(span.spans)))
                is MdSpan.Strong -> add(MdSpan.Strong(rewrite(span.spans)))
                is MdSpan.Strike -> add(MdSpan.Strike(rewrite(span.spans)))
                is MdSpan.Link -> add(MdSpan.Link(rewrite(span.text), span.url))
                is MdSpan.Html -> add(MdSpan.Html(rewrite(span.spans), span.style))
            }
        }

        /** 文本片段：按标签切成「文字 / 标签」交替处理。 */
        private fun emitText(text: String) {
            if (text.isEmpty()) return
            if (!text.contains('<')) {
                add(MdSpan.Text(decodeEntities(text)))
                return
            }
            var cursor = 0
            for (match in TAG.findAll(text)) {
                if (match.range.first > cursor) add(MdSpan.Text(decodeEntities(text.substring(cursor, match.range.first))))
                handleTag(match)
                cursor = match.range.last + 1
            }
            if (cursor < text.length) add(MdSpan.Text(decodeEntities(text.substring(cursor))))
        }

        private fun handleTag(match: MatchResult) {
            val closing = match.groupValues[1].isNotEmpty()
            val name = match.groupValues[2].lowercase()
            val attrs = match.groupValues[3]
            val selfClosing = match.value.trimEnd().endsWith("/>")
            if (closing) {
                popUntil(name)
                return
            }
            if (name in DROP_CONTENT) {
                if (!selfClosing) open(Frame.Drop)
                return
            }
            when (name) {
                "br", "hr" -> add(MdSpan.Text("\n"))
                "img" -> attrValue(attrs, "alt")?.takeIf { it.isNotBlank() }?.let { add(MdSpan.Text(it)) }
                "a" -> {
                    val href = attrValue(attrs, "href")?.trim().orEmpty()
                    val style = styleOf(name, attrs)
                    when {
                        href.startsWith("http://") || href.startsWith("https://") -> open(Frame.Link(href))
                        style.isEffectful -> open(Frame.Styled(style, name))
                        selfClosing -> Unit
                        else -> open(Frame.Passthrough(name))
                    }
                }
                else -> {
                    if (selfClosing || name in VOID) return
                    val style = styleOf(name, attrs)
                    open(if (style.isEffectful) Frame.Styled(style, name) else Frame.Passthrough(name))
                }
            }
        }

        /** 开一层：**帧与子列表必须同时入栈**（两栈平行，`lists` 恒比 `frames` 多一层）。 */
        private fun open(frame: Frame) {
            frames.addLast(frame)
            lists.addLast(mutableListOf())
        }

        private fun add(span: MdSpan) {
            if (frames.any { it is Frame.Drop }) return
            lists.last().add(span)
        }

        /** 关到 [name] 那一层为止（找不到就什么都不做——与浏览器对多余闭标签的处理一致）。 */
        private fun popUntil(name: String) {
            val index = frames.indexOfLast { it.matches(name) }
            if (index >= 0) closeThrough(index)
        }

        /**
         * 收口到只剩 [target] 层（从最内层往外）。
         *
         * 帧与「子列表」是**平行栈**：`lists` 恒比 `frames` 多一层（最外层是 root）。
         */
        private fun closeThrough(target: Int) {
            while (frames.size > target) {
                val frame = frames.removeLast()
                val children = lists.removeLast()
                val parent = lists.last()
                when (frame) {
                    is Frame.Styled ->
                        if (children.isNotEmpty()) parent.add(MdSpan.Html(mergeText(children), frame.style))
                    is Frame.Link -> parent.add(MdSpan.Link(mergeText(children), frame.href))
                    is Frame.Passthrough -> parent.addAll(children)
                    // 丢弃帧的内容直接扔掉：`<script>` 里是代码不是正文
                    Frame.Drop -> Unit
                }
            }
        }

        private sealed interface Frame {
            val tag: String

            fun matches(name: String): Boolean = tag == name

            data class Styled(val style: HtmlStyle, override val tag: String) : Frame

            data class Link(val href: String, override val tag: String = "a") : Frame

            data class Passthrough(override val tag: String) : Frame

            /** `<script>`/`<style>` 这类：内容整体丢弃。 */
            data object Drop : Frame {
                override val tag: String = "script"

                // 任何一个丢弃类标签的闭标签都算它的闭标签（浏览器同：分别闭合各自的元素）
                override fun matches(name: String): Boolean = name in DROP_CONTENT
            }
        }
    }

    // ------------------------------------------------------------------ 标签 → 样式

    /** 语义标签的固有样式（不含属性）。 */
    private fun tagStyle(name: String): HtmlStyle = when (name) {
        "b", "strong" -> HtmlStyle(bold = true)
        "i", "em", "cite", "var" -> HtmlStyle(italic = true)
        "u", "ins" -> HtmlStyle(underline = true)
        "s", "strike", "del" -> HtmlStyle(strike = true)
        "code", "kbd", "samp", "tt" -> HtmlStyle(mono = true)
        "small" -> HtmlStyle(sizeScale = 0.85f)
        "big" -> HtmlStyle(sizeScale = 1.2f)
        "sub" -> HtmlStyle(baseline = HtmlBaseline.Sub, sizeScale = 0.8f)
        "sup" -> HtmlStyle(baseline = HtmlBaseline.Super, sizeScale = 0.8f)
        else -> HtmlStyle()
    }

    /** 标签固有样式 + `style` 属性 + `font` 的 `color`/`face`/`size`。 */
    private fun styleOf(name: String, attrs: String): HtmlStyle {
        var style = tagStyle(name)
        cssStyle(attrValue(attrs, "style")).forEach { (key, value) ->
            style = applyDeclaration(style, key, value)
        }
        if (name == "font") {
            attrValue(attrs, "color")?.let { parseColor(it) }?.let { style = style.copy(color = it) }
            attrValue(attrs, "size")?.trim()?.toIntOrNull()?.let { size ->
                scaleForFontSizeAttr(size)?.let { style = style.copy(sizeScale = it) }
            }
        }
        return style
    }

    /** CSS 声明 → 样式。**只认显式声明的属性**，其余一律忽略（含 `class`）。 */
    private fun applyDeclaration(style: HtmlStyle, rawKey: String, rawValue: String): HtmlStyle {
        val value = rawValue.trim().lowercase()
        if (value.isEmpty() || value == "inherit" || value == "initial" || value == "unset") return style
        return when (rawKey.trim().lowercase()) {
            "color" -> parseColor(rawValue)?.let { style.copy(color = it) } ?: style
            "background", "background-color" -> parseColor(rawValue)?.let { style.copy(background = it) } ?: style
            "font-weight" -> when {
                value == "bold" || value == "bolder" -> style.copy(bold = true)
                value == "normal" || value == "lighter" -> style.copy(bold = false)
                else -> value.toIntOrNull()?.let { style.copy(bold = it >= 600) } ?: style
            }
            "font-style" -> when (value) {
                "italic", "oblique" -> style.copy(italic = true)
                "normal" -> style.copy(italic = false)
                else -> style
            }
            "text-decoration", "text-decoration-line" -> when {
                value == "none" -> style.copy(underline = false, strike = false)
                else -> style.copy(
                    underline = if ("underline" in value) true else style.underline,
                    strike = if ("line-through" in value) true else style.strike,
                )
            }
            "font-size" -> parseFontSize(rawValue)?.let { style.copy(sizeScale = it) } ?: style
            "font-family" -> if ("mono" in value || "courier" in value || "consolas" in value) {
                style.copy(mono = true)
            } else {
                style
            }
            else -> style
        }
    }

    /** `font-size`：`%` / `em` / `px` / 关键字（未识别返回 null = 不改）。 */
    internal fun parseFontSize(raw: String): Float? {
        val value = raw.trim().lowercase()
        return when {
            value.isEmpty() -> null
            value.endsWith("%") -> value.dropLast(1).trim().toFloatOrNull()?.div(100f)
            value.endsWith("em") || value.endsWith("rem") ->
                value.removeSuffix("rem").removeSuffix("em").trim().toFloatOrNull()
            value.endsWith("px") -> value.dropLast(2).trim().toFloatOrNull()?.div(16f)
            else -> FONT_SIZE_KEYWORDS[value]
        }?.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** `<font size="1..7">`（HTML3 的档位，浏览器仍在支持）。 */
    private fun scaleForFontSizeAttr(size: Int): Float? = FONT_SIZE_ATTR[size]

    // ------------------------------------------------------------------ 属性 / 颜色 / 实体

    /**
     * 取属性值（双引号 / 单引号 / 裸值三种写法）。
     *
     * 属性名做**边界匹配**：`data-x-color="red"` 里的 `color` 不该被当成 `color`。
     */
    internal fun attrValue(attrs: String, name: String): String? {
        ATTRIBUTE.findAll(attrs).forEach { match ->
            if (match.groupValues[1].lowercase() == name) {
                val doubleQuoted = match.groupValues[2]
                val singleQuoted = match.groupValues[3]
                return if (doubleQuoted.isNotEmpty()) doubleQuoted
                else if (singleQuoted.isNotEmpty()) singleQuoted
                else match.groupValues[4]
            }
        }
        return null
    }

    /** `style="a:b;c:d"` → 声明序列（丢掉 `!important` 与空项）。 */
    internal fun cssStyle(raw: String?): List<Pair<String, String>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { declaration ->
            val index = declaration.indexOf(':')
            if (index <= 0) return@mapNotNull null
            val key = declaration.substring(0, index)
            val value = declaration.substring(index + 1).substringBefore("!").trim()
            if (value.isEmpty()) null else key.trim() to value
        }
    }

    /**
     * CSS 颜色（`#rgb` `#rgba` `#rrggbb` `#rrggbbaa` `rgb()` `rgba()` 与常用具名色）。
     *
     * 解析不出返回 null = **不改样式**（而不是塞一个猜测的颜色）。
     */
    internal fun parseColor(raw: String): Int? {
        val value = raw.trim().lowercase()
        NAMED_COLORS[value]?.let { return it }
        if (value.startsWith("#")) {
            val hex = value.drop(1)
            if (hex.any { !it.isDigit() && it !in 'a'..'f' }) return null
            return when (hex.length) {
                3 -> hex8(expandShortHex(hex + "f"))
                4 -> hex8(expandShortHex(hex))
                6 -> hex.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
                // CSS 是 #RRGGBBAA，Android int 是 AARRGGBB → 通道要重排
                8 -> hex.toLongOrNull(16)?.let { rgbaToArgb(it) }
                else -> null
            }
        }
        if (value.startsWith("rgb")) {
            val body = value.substringAfter('(', "").substringBefore(')')
            if (body.isEmpty()) return null
            val parts = body.replace("/", " ").split(',', ' ', '\t')
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            val r = channel(parts[0]) ?: return null
            val g = channel(parts[1]) ?: return null
            val b = channel(parts[2]) ?: return null
            val a = if (parts.size >= 4) alpha(parts[3]) ?: return null else 255
            return argb(a, r, g, b)
        }
        return null
    }

    /** `#RGB`/`#RGBA` 简写展开成 8 位 `RRGGBBAA`。 */
    private fun expandShortHex(digits: String): String? {
        if (digits.length != 4) return null
        val out = StringBuilder()
        digits.forEach { out.append(it).append(it) }
        return out.toString()
    }

    /** 8 位 `RRGGBBAA` → ARGB int。 */
    private fun hex8(digits: String?): Int? =
        digits?.toLongOrNull(16)?.let { rgbaToArgb(it) }

    /** `0xRRGGBBAA` → ARGB int（通道重排）。 */
    private fun rgbaToArgb(rgba: Long): Int {
        val r = ((rgba shr 24) and 0xFF).toInt()
        val g = ((rgba shr 16) and 0xFF).toInt()
        val b = ((rgba shr 8) and 0xFF).toInt()
        val a = (rgba and 0xFF).toInt()
        return argb(a, r, g, b)
    }

    private fun channel(raw: String): Int? = when {
        raw.endsWith("%") -> raw.dropLast(1).toFloatOrNull()?.let { (it * 2.55f).toInt() }
        else -> raw.toIntOrNull()
    }?.coerceIn(0, 255)

    private fun alpha(raw: String): Int? = when {
        raw.endsWith("%") -> raw.dropLast(1).toFloatOrNull()?.let { (it * 2.55f).toInt() }
        raw.toFloatOrNull()?.let { it <= 1f } == true -> (raw.toFloat() * 255f).toInt()
        else -> raw.toIntOrNull()
    }?.coerceIn(0, 255)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    /** 常见命名字符串（够覆盖手写/模型写的颜色词；未收录的按「不识别」处理）。 */
    private val NAMED_COLORS: Map<String, Int> = mapOf(
        "transparent" to 0x00000000,
        "black" to 0xFF000000.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(),
        "lime" to 0xFF00FF00.toInt(),
        "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(),
        "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(),
        "pink" to 0xFFFFC0CB.toInt(),
        "gray" to 0xFF808080.toInt(),
        "grey" to 0xFF808080.toInt(),
        "silver" to 0xFFC0C0C0.toInt(),
        "cyan" to 0xFF00FFFF.toInt(),
        "aqua" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(),
        "fuchsia" to 0xFFFF00FF.toInt(),
        "brown" to 0xFFA52A2A.toInt(),
        "gold" to 0xFFFFD700.toInt(),
        "teal" to 0xFF008080.toInt(),
        "navy" to 0xFF000080.toInt(),
        "maroon" to 0xFF800000.toInt(),
        "olive" to 0xFF808000.toInt(),
        "crimson" to 0xFFDC143C.toInt(),
        "tomato" to 0xFFFF6347.toInt(),
        "salmon" to 0xFFFA8072.toInt(),
        "khaki" to 0xFFF0E68C.toInt(),
        "beige" to 0xFFF5F5DC.toInt(),
        "ivory" to 0xFFFFFFF0.toInt(),
        "lavender" to 0xFFE6E6FA.toInt(),
        "violet" to 0xFFEE82EE.toInt(),
        "indigo" to 0xFF4B0082.toInt(),
        "turquoise" to 0xFF40E0D0.toInt(),
        "skyblue" to 0xFF87CEEB.toInt(),
        "steelblue" to 0xFF4682B4.toInt(),
        "slategray" to 0xFF708090.toInt(),
        "darkgray" to 0xFFA9A9A9.toInt(),
        "lightgray" to 0xFFD3D3D3.toInt(),
        "lightgrey" to 0xFFD3D3D3.toInt(),
        "darkred" to 0xFF8B0000.toInt(),
        "darkgreen" to 0xFF006400.toInt(),
        "darkblue" to 0xFF00008B.toInt(),
    )

    private val FONT_SIZE_KEYWORDS: Map<String, Float> = mapOf(
        "xx-small" to 0.6f,
        "x-small" to 0.7f,
        "small" to 0.85f,
        "smaller" to 0.85f,
        "medium" to 1f,
        "large" to 1.2f,
        "larger" to 1.2f,
        "x-large" to 1.4f,
        "xx-large" to 1.6f,
    )

    private val FONT_SIZE_ATTR: Map<Int, Float> = mapOf(
        1 to 0.6f,
        2 to 0.75f,
        3 to 1f,
        4 to 1.2f,
        5 to 1.5f,
        6 to 1.8f,
        7 to 2f,
    )

    /** 连内容一起丢的标签（脚本 / 样式 / 嵌入对象 / 模板）。 */
    private val DROP_CONTENT = setOf(
        "script",
        "style",
        "iframe",
        "object",
        "embed",
        "applet",
        "template",
        "noscript",
        "svg",
        "math",
    )

    private val VOID = setOf("area", "base", "col", "input", "link", "meta", "param", "source", "track", "wbr")

    /** 行内标签（含属性；`[\w:-]` 认 `xlink:href` 这类带命名空间的写法）。 */
    private val TAG = Regex(
        "<(/?)([a-zA-Z][\\w:-]*)((?:[^\"'>]|\"[^\"]*\"|'[^']*')*)>",
    )

    private val ATTRIBUTE = Regex(
        "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
    )

    /** 基本实体（浏览器会解，正文里写 `&lt;` 就是想显示 `<`）。 */
    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match -> ENTITY_MAP[match.value.lowercase()] ?: match.value }
    }

    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    private val ENTITY_MAP: Map<String, String> = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&amp;" to "&",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&nbsp;" to "\u00A0",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&hellip;" to "…",
        "&times;" to "×",
    )
}

/** 合并相邻的纯文本片段（拆标签时会切出很多小段，合并后 AnnotatedString 更短、diff 更稳）。 */
private fun mergeText(spans: List<MdSpan>): List<MdSpan> {
    val out = mutableListOf<MdSpan>()
    for (span in spans) {
        val last = out.lastOrNull()
        if (last is MdSpan.Text && span is MdSpan.Text) {
            out[out.lastIndex] = MdSpan.Text(last.text + span.text)
        } else {
            out += span
        }
    }
    return out
}

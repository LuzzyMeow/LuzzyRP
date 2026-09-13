package com.luzzymeow.luzzyrp.ui.markdown

/**
 * 消息 HTML 的**清洗**（安全边界，不可省）。
 *
 * ## 与上游的差别（刻意为之，如实登记）
 *
 * 上游把整页应用和消息 HTML 放在**同一个文档**里，`DOMPurify` 的 `ADD_TAGS` 甚至放行
 * `script` / `iframe` / `onclick`（`runtime-services.js:51-56`）——那在它们的架构里是「可执行
 * HTML 卡片」特性。我们不一样：**消息 HTML 来自模型（不可信输入）**，渲染容器是原生应用里的
 * 一个 WebView。所以这里做两件事：
 *
 * 1. **不放行脚本**：`<script>` / `<iframe>` / `<object>` / `srcdoc` / 内联事件（`on*`）一律删除；
 *    JS 也在容器侧关掉（[HtmlCard]），这是双保险而不是重复。
 * 2. **保留样式**：`style` / `class` / `<style>` 全留——卡片的样子就是模型写的 CSS，
 *    「保真」与「安全」在这里不冲突（CSS 不会执行代码）。
 *
 * ## 三处「保真但不安全/不可用」的处置（设计门评审要求，2026-09-14）
 *
 * - **固定高度与裁剪**（`height` / `max-height` / `overflow*`）→ 从内联样式里**去掉**：
 *   容器按内容量高且没有滚动条，留着就是**静默裁掉内容**；
 * - **表单类死控件**（`input` / `select` / `textarea` …）→ 删除；`button` / `label` → 降级成 `span`：
 *   JS 关着，它们「可点但点了没反应」比不渲染更糟；
 * - **正则全部预编译**（不在调用时现编、也不用可变缓存）：这个对象可能被后台线程调用，
 *   可变状态在这里就是数据竞争。
 *
 * 天花板（`ponytail:` 有意简化）：不解析 HTML 树，只用**标签级扫描**做删/留。
 * 够用于「删危险标签与属性」这个目标；若将来要支持可执行卡片（动画/交互），
 * 升级路径是换成一个真正的沙箱（无桥、无网、单独的 WebView 进程）而不是放宽这里的规则。
 */
object HtmlSanitizer {

    /** 容器型危险标签：**连同内容一起删**；未闭合时删到结尾（宁可少显示，也不放残片进来）。 */
    private val DROP_CONTAINER = listOf(
        "script", "iframe", "object", "embed", "applet", "template", "noscript", "frame", "frameset", "form",
    )

    /** 空元素与表单类：只删标签（及闭合标签），内容留下。 */
    private val DROP_TAGS = listOf(
        "link", "meta", "base", "input", "select", "textarea", "option", "fieldset", "legend",
    )

    /** JS 关着 → 点了没反应的控件：降级成 `span`（保留样式，去掉「可点」的假象）。 */
    private val DEAD_CONTROLS = listOf("button", "label")

    private val CONTAINER_PAIRED = DROP_CONTAINER.map { regex("<$it\\b[^>]*>[\\s\\S]*?</$it\\s*>") }
    private val CONTAINER_OPEN = DROP_CONTAINER.map { regex("<$it\\b[^>]*>[\\s\\S]*$") }
    private val VOID_OR_FIELD = DROP_TAGS.flatMap { listOf(regex("<$it\\b[^>]*/?>"), regex("</$it\\s*>")) }
    private val CONTROL_OPEN = DEAD_CONTROLS.map { regex("<$it\\b([^>]*)>") }
    private val CONTROL_CLOSE = DEAD_CONTROLS.map { regex("</$it\\s*>") }

    private val EVENT_ATTR = regex("""\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""")
    private val SCRIPT_URL = Regex("""(?i)(javascript|vbscript)\s*:""")
    private val HTML_DATA_URL = Regex("""(?i)data\s*:\s*text/html""")
    private val ATTR = regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""")
    private val ENTITY = Regex("""&#(x?[0-9a-fA-F]+);|&(colon|tab|newline|amp|lt|gt|quot|apos);""")

    /** 内联样式里去掉的声明（固定高度与裁剪 → 会静默裁内容）。 */
    private val CLIPPING_PROPS = setOf("height", "max-height", "overflow", "overflow-x", "overflow-y")

    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    fun sanitize(html: String): String {
        if (html.isBlank()) return ""
        var out = html
        CONTAINER_PAIRED.forEach { out = it.replace(out, "") }
        CONTAINER_OPEN.forEach { out = it.replace(out, "") }
        VOID_OR_FIELD.forEach { out = it.replace(out, "") }
        CONTROL_OPEN.forEach { out = it.replace(out, "<span$1>") }
        CONTROL_CLOSE.forEach { out = it.replace(out, "</span>") }
        out = out.replace(EVENT_ATTR, "")
        return cleanAttributes(out)
    }

    /**
     * 属性取值是否指可执行内容。
     *
     * 先**解实体**（`java&#115;cript:` 是浏览器看得懂、正则看不懂的写法），
     * 再**去掉控制字符与空白**（`java\tscript:` / `java\nscript:` 在 URL 里等价于 `javascript:`）。
     */
    private fun isDangerousUrl(value: String): Boolean {
        val decoded = ENTITY.replace(value) { match ->
            val hex = match.groupValues[1]
            val named = match.groupValues[2]
            when {
                hex.startsWith("x", ignoreCase = true) -> hex.drop(1).toIntOrNull(16)?.toChar()?.toString() ?: match.value
                hex.isNotEmpty() -> hex.toIntOrNull()?.toChar()?.toString() ?: match.value
                else -> when (named.lowercase()) {
                    "colon" -> ":"
                    "tab" -> "\t"
                    "newline" -> "\n"
                    else -> match.value
                }
            }
        }
        val compact = decoded.filter { it.code > 0x20 }
        return SCRIPT_URL.containsMatchIn(compact) || HTML_DATA_URL.containsMatchIn(compact)
    }

    /** 逐标签清属性（保留 style/class/id/src 等，删危险取值与 srcdoc）。 */
    private fun cleanAttributes(html: String): String {
        val builder = StringBuilder(html.length)
        var index = 0
        while (index < html.length) {
            val lt = html.indexOf('<', index)
            if (lt < 0) {
                builder.append(html, index, html.length)
                break
            }
            val gt = html.indexOf('>', lt)
            if (gt < 0) {
                builder.append(html, index, html.length)
                break
            }
            builder.append(html, index, lt)
            builder.append(cleanTag(html.substring(lt, gt + 1)))
            index = gt + 1
        }
        return builder.toString()
    }

    private fun cleanTag(tag: String): String {
        if (tag.startsWith("<!--") || tag.startsWith("<!")) return tag
        // 闭合标签没有属性：**原样返回**。曾经在这里按「属性解析」处理，`</span>` 被拼成了 `<>`
        // ——真机目测抓到的（卡片上每一行都多出一个 `<>`），纯文本断言看不见这种问题。
        if (tag.startsWith("</")) {
            val name = tag.removePrefix("</").takeWhile { it.isLetterOrDigit() || it == '-' }
            return if (name.isEmpty()) "" else "</$name>"
        }
        val head = tag.takeWhile { it != ' ' && it != '\t' && it != '\n' && it != '\r' && it != '>' && it != '/' }
        val selfClosing = tag.trimEnd().endsWith("/>")
        val body = tag.substring(head.length).trimEnd().removeSuffix(">").removeSuffix("/")
        val kept = ATTR.findAll(body).mapNotNull { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            when {
                name.equals("srcdoc", ignoreCase = true) -> null
                isDangerousUrl(value) -> null
                name.equals("style", ignoreCase = true) -> "$name=${stripClipping(value)}"
                else -> "$name=$value"
            }
        }.toList()
        val attributes = if (kept.isEmpty()) "" else " " + kept.joinToString(" ")
        return head + attributes + if (selfClosing) " />" else ">"
    }

    /**
     * 去掉内联样式里的**固定高度与裁剪**声明。
     *
     * 为什么必须去：容器高度按内容量（`wrap_content`）且**没有滚动条**，所以
     * `height:120px; overflow:hidden` 的卡片会把超出部分**静默裁掉**——用户看不到，
     * 也没有任何办法看到（设计门硬要求：不许静默裁内容）。去掉之后卡片按内容自然长高。
     */
    private fun stripClipping(styleValue: String): String {
        val trimmed = styleValue.trim()
        val quote = trimmed.firstOrNull()?.takeIf { it == '"' || it == '\'' }?.toString() ?: ""
        val body = trimmed.removeSurrounding("\"").removeSurrounding("'")
        val kept = body.split(';').mapNotNull { declaration ->
            val property = declaration.substringBefore(':').trim().lowercase()
            declaration.trim().takeIf { it.isNotEmpty() && property !in CLIPPING_PROPS }
        }
        return "$quote${kept.joinToString("; ")}$quote"
    }

    /** 明文（无标签）时的兜底：整段转义，避免它被 WebView 当 HTML 解析。 */
    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * 包成完整文档：**禁网 + 禁 JS 的 CSP** + 透明底 + 主题/字号兜底 + 减少动效。
     *
     * 三条基线都是**低特异性**（只写在 `body` / 通配上），模型元素级的 CSS 永远赢：
     * - **主题色兜底**：只写了深底、没写 `color` 的卡片，若沿用容器默认色会在亮主题下变成
     *   黑字压深底（不可读）——所以显式给一个可读的默认色（语义同上游「继承页面主题」）；
     * - **字号兜底**：与正文一致的基准字号/行高，避免卡片里的字与气泡完全脱节；
     * - **减少动效**：系统关掉动画时（`ANIMATOR_DURATION_SCALE = 0`）卡内 `animation/transition`
     *   一律停用——DESIGN §7 的既有纪律，`<style>` 里的动画也要守。
     */
    fun document(
        html: String,
        textColorCss: String = "#1A1A1A",
        linkColorCss: String = "#8C5A3C",
        fontFamily: String = "sans-serif",
        baseFontSizePx: Int = 15,
        reduceMotion: Boolean = false,
    ): String = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy"
              content="default-src 'none'; script-src 'none'; object-src 'none'; frame-src 'none';
                       connect-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:;">
        <style>
          html,body{margin:0;padding:0;background:transparent;}
          body{color:$textColorCss;font-family:$fontFamily;font-size:${baseFontSizePx}px;line-height:1.6;
               word-wrap:break-word;overflow-wrap:anywhere;}
          a{color:$linkColorCss;}
          img{max-width:100%;height:auto;}
          table{border-collapse:collapse;max-width:100%;}
          *{max-width:100%;box-sizing:border-box;${if (reduceMotion) MOTION_OFF else ""}}
          @media (prefers-reduced-motion: reduce) {
            *,*::before,*::after{animation:none !important;transition:none !important;}
          }
        </style>
        </head><body>${sanitize(html)}</body></html>
    """.trimIndent()

    /** 通配规则里追加的「停用动效」声明（系统动画时长为 0 时使用）。 */
    private const val MOTION_OFF = "animation:none !important;transition:none !important;"
}

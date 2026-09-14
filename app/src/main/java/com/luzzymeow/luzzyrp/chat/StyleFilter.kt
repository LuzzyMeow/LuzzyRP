package com.luzzymeow.luzzyrp.chat

/**
 * **文风过滤**（上游 `filterBlockedStyleText`，`app.js:1470-1503`）。
 *
 * ## 它是什么（先说清楚，因为它是本仓库唯一「会删模型正文」的功能）
 *
 * 上游有一张**中文短语黑名单**（「不容置疑」「嘴角勾起一抹弧度」「指尖发白」「极其」…
 * 以及「共 N 个字」这类字数声明），命中即从 AI 正文里**整句或整词删掉**，并在界面上
 * 把命中片段列出来给用户看（`getStyleFilterHitSegments` 负责高亮）。
 * 目的是压掉 AI 写作里那批高度重复的腔调词。
 *
 * 用户 2026-09-14 拍板：**做，默认开，照上游**。
 *
 * ## 为什么它是「与正则分开的一件事」（DESIGN-compose §27.6-2 的登记理由）
 *
 * 正则脚本是用户**自己写的**、可预期、可关；文风过滤是**内置的删改行为**，
 * 用户看不到规则表、也看不到「这句话原本写了什么」。把它混进正则会让
 * 「我的正则为什么不生效」失去判据，所以它是独立一层、独立文件、独立用例。
 *
 * ## 上游的三条闸门（都照抄，缺一条就会误删）
 *
 * | 闸门 | 出处 | 作用 |
 * |---|---|---|
 * | `settings.styleFilterEnabled` 为假 → 原样返回 | `app.js:1472` | 总开关 |
 * | 整条内容是「独立渲染内容」→ 原样返回 | `app.js:1473`、`:1439` | 卡片/HTML/围栏整段不是叙述文字，删它等于毁内容 |
 * | **只对 `assistant` 生效** | `app.js:4090` | 用户自己的话一个字都不许动 |
 *
 * ## 两条作用域规则（照抄上游，都不是想当然）
 *
 * 1. **变量块之后不筛**：`filterEnd = 变量块下标 ?? 文末`（`app.js:1475-1476`）。
 *    变量块是隐藏指令，块里是 JSON——去筛它只会把 JSON 删坏；
 * 2. **引号里的对白不筛**：按 `quotedDialoguePattern` 切成「对白 / 非对白」交替片段，
 *    只处理**奇数下标以外**（即非对白）的部分（`app.js:1479`）。
 *    台词是角色的声音，不是叙述腔调——上游明确把它排除在外。
 *
 * ## 与上游的一处**有意偏离**
 *
 * 上游会顺带 `collect` 命中片段、并把它们交给界面做高亮（`app.js:1494-1496`）。
 * 我们**只删、不高亮**：高亮是视觉产出，必须过一次设计门与真机目视（本仓库纪律），
 * 而当前轮次没有真机。命中片段仍然返回（[Result.removed]），供将来接面板或写日志用。
 *
 * 本对象是**纯函数、无 Android 依赖**，可 JVM 单测。
 */
object StyleFilter {

    /** 一次过滤的结果。 */
    data class Result(
        val text: String,
        /** 被删掉的片段（已按上游 `normalizeStyleFilterHit` 归一，供将来做面板/日志）。 */
        val removed: List<String>,
    ) {
        val changed: Boolean get() = removed.isNotEmpty()
    }

    /**
     * 过滤（上游 `filterBlockedStyleText`）。
     *
     * @param enabled 总开关（上游 `settings.styleFilterEnabled`，默认**开**）
     */
    fun filter(text: String, enabled: Boolean = true): Result {
        if (!enabled || text.isEmpty()) return Result(text, emptyList())
        // 独立渲染内容整段跳过：它是卡片/HTML/代码，不是叙述文字
        if (isStandaloneRenderedContent(text)) return Result(text, emptyList())

        // 变量块之后不筛（块里是 JSON）；没有变量块时 filterEnd = 文末
        val filterEnd = UiTemplateUpdates.find(text)?.index ?: text.length
        if (filterEnd <= 0) return Result(text, emptyList())

        val removed = mutableListOf<String>()
        val filtered = RegexScripts.parts(text.substring(0, filterEnd))
            .joinToString("") { part ->
                // 受保护区（代码/标签/思考块）一律不动 —— 上游走 transformUnprotectedText
                if (part.protected) part.text else filterPlain(part.text, removed)
            } + text.substring(filterEnd)

        return Result(filtered, removed.mapNotNull { normalizeHit(it) }.filter { it.isNotEmpty() })
    }

    /**
     * 一段**非受保护**文本的过滤（上游那串链式 replace，顺序不能换）。
     *
     * 先按对白/非对白切分（[rebuild]），再对非对白段跑 [clean]。
     */
    private fun filterPlain(source: String, removed: MutableList<String>): String = rebuild(source, removed)

    /**
     * 逐段重建：把源码切成「非对白段 / 对白段」交替序列，**只清理非对白段**。
     *
     * 为什么不用 `String.split`：上游靠「`split` 结果里下标奇偶」判对白
     * （`app.js:1479` 的 `index % 2`），那是**依赖 JS `split` 把捕获组插进结果**的隐式行为。
     * Kotlin 的 `Regex.split` 有同样的插入行为，但两个语言对「多捕获组」的处理并不完全等价
     * （上游那条正则只有一个捕获组，恰好等价）。逐段重建不依赖这个细节，
     * 语义一眼可见 —— 代价只是一次 `findAll`。
     */
    private fun rebuild(source: String, removed: MutableList<String>): String {
        val out = StringBuilder()
        var cursor = 0
        for (match in QUOTED_DIALOGUE.findAll(source)) {
            if (match.range.first > cursor) {
                out.append(clean(source.substring(cursor, match.range.first), removed))
            }
            out.append(match.value) // 对白原样保留：台词是角色的声音，不是叙述腔调
            cursor = match.range.last + 1
        }
        if (cursor < source.length) out.append(clean(source.substring(cursor), removed))
        return out.toString()
    }

    /** 上游那串链式 replace（`app.js:1480-1492`），顺序照抄。 */
    private fun clean(source: String, removed: MutableList<String>): String {
        if (source.isEmpty()) return source
        var out = source

        // ① 字数声明句：「共 3 个字…」这类元叙述——删句留前缀（前缀是句读符）
        out = WORD_COUNT_SENTENCE.replace(out) { match ->
            val prefix = match.groupValues[1]
            removed += match.value.removePrefix(prefix).trim()
            prefix
        }
        // ② 命中黑名单的整句
        out = BLOCKED_SENTENCE.replace(out) { match ->
            removed += match.value.trim()
            ""
        }
        // ③ 分句（指尖…发白）
        out = PALE_FINGER_CLAUSE.replace(out) { match ->
            removed += match.value.trim()
            ""
        }
        // ④ 分句（微微泛 / 像在 / 上扬 …）
        out = BLOCKED_CLAUSE.replace(out) { match ->
            removed += match.value.trim()
            ""
        }
        // ⑤ 单词（极其）
        out = BLOCKED_WORD.replace(out) { match ->
            removed += match.value
            ""
        }
        // ⑥ 收尾清理：删完留下的孤立标点与空行（上游 5 条 replace，顺序照抄）
        out = LEADING_PUNCTUATION.replace(out, "")
        out = REPEATED_PUNCTUATION.replace(out) { it.value.last().toString() }
        out = PUNCTUATION_BEFORE_END.replace(out, "$1")
        out = TRAILING_SPACES.replace(out, "\n")
        return BLANK_LINES.replace(out, "\n\n")
    }

    /**
     * 「独立渲染内容」（上游 `standaloneRenderedContentPattern`，`app.js:1439`）：
     * 以围栏 / doctype / xml / html / 各类块级标签**开头**的内容整段不筛。
     *
     * 注意是**开头锚定**（`^`）：一段正常叙述里出现 `<div>` 不算独立渲染内容；
     * 但整条消息从头就是一个 `<div>` 卡片，那它整段都是内容本身。
     */
    internal fun isStandaloneRenderedContent(text: String): Boolean =
        STANDALONE_RENDERED.containsMatchIn(text)

    /** 上游 `normalizeStyleFilterHit`：去首尾空白、去前导标点、去首尾的 Markdown 加粗标记。 */
    internal fun normalizeHit(fragment: String): String? {
        var out = fragment.trim()
        out = LEADING_COMMA.replace(out, "")
        out = LEADING_BOLD.replace(out, "")
        out = TRAILING_BOLD.replace(out, "")
        return out.trim().ifEmpty { null }
    }

    // ------------------------------------------------------------------ 规则表

    /**
     * 命中即删**整句**的短语表（上游 `blockedStyleSentencePattern`，`app.js:1433`）。
     *
     * 结构逐字对应上游：`[^句读]* (?:ALT) [^句读]* (?:句读+ 引号*) ?`。
     * 也就是「从上一个句读之后，一直删到下一个完整句读（含收尾引号）」。
     *
     * 三处**刻意的写法差异**（都为了 Java/JS 方言对齐）：
     * ① 上游的 `$` 在 JS 里只匹配串尾 / 行尾，Java 里还会匹配「末尾换行之前」→ 统一用 `\z`；
     * ② 上游靠 `g` 标志逐处替换，Kotlin 的 `replace` 默认即全局；
     * ③ 尾部的 `(?:\*\*|__)?` 保留（删除时要连 Markdown 加粗标记一起吃掉）。
     */
    private val BLOCKED_SENTENCE = Regex(
        "[^。！？!?\\n]*" +
            "(?:" +
            "不容置疑" +
            "|(?:不易|难以)(?:察觉|觉察)" +
            "|(?:微|几)不可察" +
            "|一抹|弧度|生理性|微微泛|因为用力|像在|风箱|手术刀|上扬|带着一种" +
            "|语气很平|声音很平" +
            "|(?:指尖|指节|指关节)[^。！？!?\\n]*(?:发白|泛白)" +
            "|像(?:是)?[^。！？!?\\n]*?[，,]\\s*又像(?:是)?" +
            "|不是[^。！？!?\\n]*?(?:而是|就是|[，,]\\s*(?:是|(?:更|倒|反倒)?像是))" +
            ")" +
            "[^。！？!?\\n]*" +
            "(?:[。！？!?]+[”’」』】）)]*(?:\\*\\*|__)?)?",
    )

    /**
     * 字数声明句（上游 `standaloneWordCountSentencePattern`，`app.js:1434`）：
     * 「（句读前缀）共 N 个字…」。**保留**前缀（句读符），只删声明本身。
     */
    private val WORD_COUNT_SENTENCE = Regex(
        "(^|[。！？!?\\n]+[”’」』】）)]*)[ \\t]*(?:\\*\\*|__)?" +
            "(?:\\d+|[零〇一二两三四五六七八九十百千万]+)个字[^。！？!?\\n]*" +
            "(?:[。！？!?]+[”’」』】）)]*(?:\\*\\*|__)?)?",
        RegexOption.MULTILINE,
    )

    /** 分句级：`（句读）…指尖…发白…（句读）`（上游 `paleFingerClausePattern`，`app.js:1435`）。 */
    private val PALE_FINGER_CLAUSE = Regex(
        "(?:^|[，,；;])[^，,。！？!?；;\\n]*(?:指尖|指节|指关节)[^，,。！？!?；;\\n]*(?:发白|泛白)" +
            "[^，,。！？!?；;\\n]*(?=$|[，,。！？!?；;\\n])",
        RegexOption.MULTILINE,
    )

    /**
     * 分句级：另一批短语（上游 `blockedStyleClausePattern`，`app.js:1436`）。
     *
     * 字符类里排除了 `*_`：这一档专治「分句里的一个词」，而 Markdown 加粗标记会让分句判断失真
     * （上游把它写进字符类，我们逐字保留）。
     */
    private val BLOCKED_CLAUSE = Regex(
        "(?:^|[，,；;])[^，,。！？!?；;\\n*_]*(?:微微泛|因为用力|像在|风箱|手术刀|上扬|带着一种)" +
            "[^，,。！？!?；;\\n*_]*(?=(?:\\*\\*|__)?[ \\t]*(?:$|[，,。！？!?；;\\n]))",
        RegexOption.MULTILINE,
    )

    /** 单词级（上游 `blockedStyleWordPattern`，`app.js:1437`）。 */
    private val BLOCKED_WORD = Regex("极其")

    /**
     * 对白片段（上游 `quotedDialoguePattern`，`app.js:1438`）：三种引号包裹。
     *
     * `[\s\S]` 而不是 `.`：对白可以跨行，`s` 标志在 Java 里要显式给，直接写 `[\s\S]` 更稳。
     */
    private val QUOTED_DIALOGUE = Regex("(“[\\s\\S]*?”|『[\\s\\S]*?』|\"[\\s\\S]*?\")")

    /**
     * 「独立渲染内容」（上游 `standaloneRenderedContentPattern`，`app.js:1439`）。
     *
     * 开头允许空白与注释；然后是围栏 / doctype / xml / html / 一批块级标签。
     */
    private val STANDALONE_RENDERED = Regex(
        "^(?:\\s|<!--[\\s\\S]*?-->)*(?:```|<!doctype\\b|<\\?xml\\b|<html\\b" +
            "|<(?:head|body|style|script|template|svg|canvas|iframe|div|section|article|aside" +
            "|header|footer|main|nav|form|table|ul|ol|pre|p|img)\\b)",
        RegexOption.IGNORE_CASE,
    )

    // 收尾清理（上游 `app.js:1488-1492`，顺序即语义）
    private val LEADING_PUNCTUATION = Regex("^[ \\t]*[，,；;]+", RegexOption.MULTILINE)
    private val REPEATED_PUNCTUATION = Regex("[，,；;]{2,}")
    private val PUNCTUATION_BEFORE_END = Regex("[，,；;]+([。！？!?])")
    private val TRAILING_SPACES = Regex("[ \\t]+\\n")
    private val BLANK_LINES = Regex("\\n{3,}")

    // normalizeStyleFilterHit 的三条（上游 `app.js:1451-1456`）
    private val LEADING_COMMA = Regex("^[，,；;]\\s*")
    private val LEADING_BOLD = Regex("^(?:\\*\\*|__)")
    private val TRAILING_BOLD = Regex("(?:\\*\\*|__)$")
}

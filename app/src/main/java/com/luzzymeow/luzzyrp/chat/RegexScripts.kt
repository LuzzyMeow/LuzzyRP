package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * **正则脚本**（上游「正则」功能的一条规则）。
 *
 * 字段名与上游 `regexScripts` 数组逐项对应（`core-utils.js:521` 的 `normalizeRegexScript`），
 * 别名键（`scriptName`/`findRegex`/`replaceString`/`regexFlags`/`disabled`）在 [from] 里收编——
 * 迁移进来的老数据就长这样。
 */
data class RegexScript(
    val name: String,
    /** 匹配式（**可能**是 `/pattern/flags` 形态，解析在 [RegexScripts.apply] 里）。 */
    val pattern: String,
    val flags: String,
    val replacement: String,
    /** 作用位置：1 = 用户消息，2 = AI 消息（上游 `placement`）。 */
    val placement: Set<Int>,
    /** `global` / `character`（只影响保存位置，不影响套用）。 */
    val scope: String,
    /** 勾了「仅 Markdown（用户可见）」——发提示词时跳过。 */
    val markdownOnly: Boolean,
    /** 勾了「仅提示词（AI 可见）」——显示时跳过。 */
    val promptOnly: Boolean,
    val minDepth: Int?,
    val maxDepth: Int?,
    val enabled: Boolean,
) {
    companion object {
        /** 上游的特殊脚本名：命中时**用内置的图片标签正则**取代用户写的 pattern（`app.js:4054,4068`）。 */
        const val IMAGE_GEN_NAME = "NAI画图正则"

        fun fromAll(elements: List<JsonElement>, fallbackScope: String = "character"): List<RegexScript> =
            elements.mapNotNull { from(it, fallbackScope) }

        /**
         * 解析一条脚本；**解析不出对象返回 null**（调用方按「这条不存在」处理）。
         *
         * 上游的空值判定是 JS 的 falsy（空串也算缺），所以这里用 `takeUnless { isEmpty }`
         * 而不是单纯的 `?:`——否则「regex 为空串 + findRegex 有值」这种数据会丢掉匹配式。
         */
        fun from(element: JsonElement, fallbackScope: String = "character"): RegexScript? {
            val obj = element as? JsonObject ?: return null
            val name = obj.text("name").ifEmpty { obj.text("scriptName") }
            val markdownOnly = obj.bool("markdownOnly") ?: false
            val promptOnly = obj.bool("promptOnly") ?: false
            return RegexScript(
                name = name,
                pattern = obj.text("regex").ifEmpty { obj.text("findRegex") },
                flags = obj.text("flags").ifEmpty { obj.text("regexFlags") }.ifEmpty { "g" },
                replacement = obj.text("replacement").ifEmpty { obj.text("replaceString") },
                placement = obj.ints("placement") ?: setOf(PLACEMENT_USER, PLACEMENT_ASSISTANT),
                scope = if (obj.text("scope") == "global" || fallbackScope == "global") "global" else "character",
                markdownOnly = markdownOnly,
                // 两项都勾时 promptOnly 让位（上游 normalizeRegexScript 末段）
                promptOnly = if (markdownOnly && promptOnly) false else promptOnly,
                minDepth = obj.int("minDepth"),
                maxDepth = obj.int("maxDepth"),
                enabled = obj.bool("enabled")
                    ?: obj.bool("disabled")?.let { !it }
                    ?: true,
            )
        }

        const val PLACEMENT_USER = 1
        const val PLACEMENT_ASSISTANT = 2
    }
}

/**
 * **把正则脚本套用到文本上**（上游 `processRegex`，`app.js:4014-4091`）。
 *
 * ## 为什么它是一条独立的一层
 *
 * 这是「上游正文渲染链」的第一段：**显示前**先用用户脚本改写正文，改写结果里可以带 HTML
 * （典型用法就是把某段文字包成 `<span style="color:…">` 做高亮）。Compose 版此前只搬了数据
 * （`regex` / `global_regex` 记录已入库）却没有消费方，于是**用户配好的正则一条也不生效**。
 *
 * ## 与上游逐条对齐的语义（都有出处，改动前先读那几行）
 *
 * | 语义 | 出处 |
 * |---|---|
 * | `{{user}}` → 用户名（含内空格、大小写不敏感） | `app.js:627` `replaceUserNamePlaceholder` |
 * | `system` 角色直接返回（不套脚本） | `app.js:4019` |
 * | 「NAI画图正则」排到最后（稳定排序） | `app.js:4020-4024` |
 * | `enabled === false` 跳过 | `app.js:4028` |
 * | `placement` 判角色（缺省 `[1,2]`） | `app.js:4032-4034` |
 * | 显示跳 `promptOnly`；提示词跳「仅用户可见」（`markdownOnly` 或两项都没勾） | `app.js:4037-4039` |
 * | `minDepth`/`maxDepth` 判深度 | `app.js:4042-4043` |
 * | `/pattern/flags` 形态 + `(?s)(?i)(?m)` 搬进 flags | `app.js:4057-4067`、`core-utils.js:281` |
 * | 匹配式不含 `<`/`>`/围栏时**只改写非受保护区**；含则整段直改 | `app.js:4072-4084` |
 * | 编译失败只跳过该条（不中断其余脚本） | `app.js:4086-4088` |
 * | 受保护区：HTML 块 / 注释 / 代码围栏 / 行内代码 / 任意标签 / 思考块 | `core-utils.js:293-309` |
 *
 * ## 两处**有意保留的偏离**（都登记在 `DESIGN-compose`，不是漏做）
 *
 * 1. **文风过滤**（`filterBlockedStyleText`，`app.js:1470`）没有实现——那是「删改 AI 措辞」
 *    的独立功能（`settings.styleFilterEnabled` 默认开），与正则不是同一件事，
 *    混进来会让「正则为什么不生效」的排查失去判据。单独立项。
 * 2. **JS 正则与 Java 正则的方言差**：`u`/`y` 两个 flag 无对应物（忽略），
 *    `$`` / `$'` 之外的替换语法**逐字实现**（见 [expandReplacement]），
 *    Java 独有的写法（占有量词等）我们也能跑——上游跑不了的脚本我们可能跑得动，反之不会。
 *
 * 本对象是**纯函数、无 Android 依赖**（可 JVM 单测），因为它同时要给显示与提示词两条路径用。
 */
object RegexScripts {

    /** 套用场景：显示（正文上屏前）/ 提示词（发给模型前）。 */
    enum class Mode { Display, Prompt }

    /** `user` 角色消息（上游字符串口径，便于与迁移数据对齐）。 */
    private const val ROLE_SYSTEM = "system"
    private const val ROLE_USER = "user"
    private const val ROLE_ASSISTANT = "assistant"
    private const val ROLE_TOOL = "tool"

    /**
     * 按上游顺序套用全部脚本。
     *
     * @param depth 距最新一条消息的距离（提示词路径用；显示通常传 0）
     * @param userName `{{user}}` 的替换值（用户档案里的名字）
     */
    fun apply(
        text: String,
        scripts: List<RegexScript>,
        role: LlmRole,
        mode: Mode,
        depth: Int = 0,
        userName: String = "",
    ): String {
        if (text.isEmpty()) return ""
        val roleId = roleId(role)
        var result = USER_PLACEHOLDER.replace(text) { userName.trim() }
        if (roleId == ROLE_SYSTEM) return result
        for (script in ordered(scripts)) {
            if (!applies(script, roleId, mode, depth)) continue
            val resolved = resolve(script) ?: continue
            val regex = compile(resolved) ?: continue
            result = run(result, resolved, regex, script)
        }
        return result
    }

    /**
     * 角色口径（**必须**与上游字符串一致）。
     *
     * `tool` 保持独立、不折算成 `user`：上游的 `placement` 判定只认 `user`/`assistant`，
     * 折算成 `user` 会给工具消息平白加一道「只对用户生效」的过滤——那是上游没有的行为。
     */
    internal fun roleId(role: LlmRole): String = when (role) {
        LlmRole.SYSTEM -> ROLE_SYSTEM
        LlmRole.USER -> ROLE_USER
        LlmRole.ASSISTANT -> ROLE_ASSISTANT
        LlmRole.TOOL -> ROLE_TOOL
    }

    // ------------------------------------------------------------------ 逐条判据

    /** 「NAI画图正则」恒排最后，其余保持原序（上游用 `Array.sort` + 稳定比较器）。 */
    private fun ordered(scripts: List<RegexScript>): List<RegexScript> =
        scripts.sortedBy { if (it.name == RegexScript.IMAGE_GEN_NAME) 1 else 0 }

    private fun applies(script: RegexScript, roleId: String, mode: Mode, depth: Int): Boolean {
        if (!script.enabled) return false
        if (roleId == ROLE_USER && RegexScript.PLACEMENT_USER !in script.placement) return false
        if (roleId == ROLE_ASSISTANT && RegexScript.PLACEMENT_ASSISTANT !in script.placement) return false
        val userOnly = script.markdownOnly || (!script.markdownOnly && !script.promptOnly)
        if (mode == Mode.Display && script.promptOnly) return false
        if (mode == Mode.Prompt && userOnly) return false
        if (script.minDepth != null && depth < script.minDepth) return false
        if (script.maxDepth != null && depth > script.maxDepth) return false
        return true
    }

    /** 解析后的匹配式（`/pattern/flags` 已拆开、内联修饰符已搬进 flags）。 */
    internal data class Resolved(val pattern: String, val flags: String)

    internal fun resolve(script: RegexScript): Resolved? {
        var pattern = if (script.name == RegexScript.IMAGE_GEN_NAME) IMAGE_TAG_PATTERN else script.pattern
        if (pattern.isEmpty()) return null
        var flags = script.flags.ifEmpty { "g" }
        if (pattern.startsWith("/") && pattern.lastIndexOf('/') > 0) {
            val lastSlash = pattern.lastIndexOf('/')
            val candidate = pattern.substring(lastSlash + 1)
            if (candidate.all { it in JS_FLAG_CHARS }) {
                flags = candidate
                pattern = pattern.substring(1, lastSlash)
            }
        }
        for (modifier in INLINE_MODIFIERS) {
            val marker = "(?$modifier)"
            if (marker in pattern) {
                pattern = pattern.replace(marker, "")
                if (modifier !in flags) flags += modifier
            }
        }
        return Resolved(pattern, flags)
    }

    /** 编译失败 = 跳过该条（上游 `catch` + `console.error`，不打断其余脚本）。 */
    internal fun compile(resolved: Resolved): Regex? = runCatching {
        val options = buildSet {
            if ('i' in resolved.flags) add(RegexOption.IGNORE_CASE)
            if ('m' in resolved.flags) add(RegexOption.MULTILINE)
            if ('s' in resolved.flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        Regex(resolved.pattern, options)
    }.getOrNull()

    /**
     * 套用一条脚本。
     *
     * 上游的分支（`app.js:4072-4084`）：匹配式**不含** `<`/`>`/围栏时，先看「整段文本就是第一个匹配」
     * 的包裹型用法（替换结果里仍含原文 → 直接用，避免给面板里每段文字重复套壳），否则**只改写
     * 非受保护区**；含标签/围栏的匹配式是用户显式要动那些区域，整段直改。
     */
    private fun run(text: String, resolved: Resolved, regex: Regex, script: RegexScript): String {
        val global = 'g' in resolved.flags
        val replace = { source: String -> replace(source, regex, script.replacement, global) }
        if ('<' !in resolved.pattern && '>' !in resolved.pattern && "```" !in resolved.pattern) {
            val whole = regex.find(text)
            val wrapped = if (whole != null && whole.value == text) replace(text) else null
            return if (wrapped != null && wrapped.contains(text)) wrapped
            else parts(text).joinToString("") { if (it.protected) it.text else replace(it.text) }
        }
        return replace(text)
    }

    // ------------------------------------------------------------------ 受保护区

    /** 一段文本是否受正则保护（上游 `splitProtectedText` 的产物形状）。 */
    internal data class TextPart(val text: String, val protected: Boolean)

    /**
     * 切分受保护区（上游 `core-utils.js:293-309`）。
     *
     * 两级：**先**按思考块（`parseCot` 的 ranges）切，**再**在每个空白片段里按
     * HTML/代码模式切——顺序不能颠倒，思考块优先级更高。
     */
    internal fun parts(text: String): List<TextPart> {
        if (text.isEmpty()) return emptyList()
        val cot = cotRanges(text)
        if (cot.isEmpty()) return scanProtected(text)
        val out = mutableListOf<TextPart>()
        var cursor = 0
        for (range in cot) {
            out += scanProtected(text.substring(cursor, range.first))
            out += TextPart(text.substring(range.first, range.last + 1), true)
            cursor = range.last + 1
        }
        out += scanProtected(text.substring(cursor))
        return out.filter { it.text.isNotEmpty() }
    }

    /** HTML 块 / 注释 / 代码围栏 / 行内代码 / 任意标签（上游 protectedContentPattern 的移植）。 */
    private fun scanProtected(text: String): List<TextPart> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<TextPart>()
        var cursor = 0
        for (match in PROTECTED.findAll(text)) {
            if (match.range.first > cursor) out += TextPart(text.substring(cursor, match.range.first), false)
            out += TextPart(match.value, true)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) out += TextPart(text.substring(cursor), false)
        return out
    }

    /**
     * 思考块范围（`<thinking>` / `</thinking>`…）。
     *
     * **这是 `parseCot` 的最小可用子集**：只求「哪些范围算思考块」用于保护，
     * 不产出上游那套 `cot`/`main`/`closingTags` 拆分——**把思考块单独渲染成节点**仍是未做项
     * （见 `DESIGN-compose` §26 的登记），本函数不假装做了那件事。
     *
     * 与上游一致的要点：重复开标签仍算思考（只有真闭合才结束）、未闭合算到文末、
     * 代码围栏与行内代码里的标签**不算**思考标签（上游把围栏也放进同一 token 正则）。
     */
    internal fun cotRanges(text: String): List<IntRange> {
        if (!text.contains('<')) return emptyList()
        val code = CODE_RANGES.findAll(text).map { it.range }.toList()
        val inCode = { index: Int -> code.any { index >= it.first && index <= it.last } }
        val open = ArrayDeque<String>()
        val ranges = mutableListOf<IntRange>()
        var blockStart = 0
        for (match in COT_TOKEN.findAll(text)) {
            if (inCode(match.range.first)) continue
            val tag = match.groupValues[2].lowercase()
            if (match.groupValues[1].isEmpty()) {
                if (open.isEmpty()) blockStart = match.range.first
                if (tag !in open) open.addLast(tag)
            } else if (open.isNotEmpty() && open.last() == tag) {
                open.removeLast()
                if (open.isEmpty()) ranges += blockStart..match.range.last
            }
        }
        if (open.isNotEmpty()) ranges += blockStart..(text.length - 1)
        return ranges
    }

    // ------------------------------------------------------------------ 替换语义

    /**
     * JS `String.replace` 的替换串展开（`g` 缺省时只替换第一处）。
     *
     * 为什么要自己写而不直接用 Kotlin 的 `Regex.replace`：JS 多出 `$&`（整个匹配）、
     * `` $` ``（匹配前文）、`$'`（匹配后文）、`$$`（字面 `$`）四种写法，而用户脚本里
     * **正经用它们做包裹高亮**（`<span>$&</span>` 就是最典型的一条）。Java 的
     * `Matcher.replaceAll` 只认 `$n`/`\`，照搬会把 `$&` 原样吐回正文。
     */
    internal fun replace(source: String, regex: Regex, replacement: String, global: Boolean): String {
        if (replacement.isEmpty()) {
            val matches = regex.findAll(source).toList()
            if (matches.isEmpty()) return source
            val targets = if (global) matches else matches.take(1)
            val out = StringBuilder()
            var last = 0
            for (m in targets) {
                out.append(source, last, m.range.first)
                last = m.range.last + 1
            }
            out.append(source, last, source.length)
            return out.toString()
        }
        val out = StringBuilder()
        var last = 0
        var matched = false
        for (m in regex.findAll(source)) {
            out.append(source, last, m.range.first)
            out.append(expandReplacement(replacement, m, source))
            last = m.range.last + 1
            matched = true
            if (!global) break
        }
        if (!matched) return source
        out.append(source, last, source.length)
        return out.toString()
    }

    /** 逐字符展开替换串（规则对齐 ECMAScript `GetSubstitution`）。 */
    internal fun expandReplacement(replacement: String, match: MatchResult, source: String): String {
        if ('$' !in replacement) return replacement
        val out = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            if (replacement[i] != '$' || i == replacement.length - 1) {
                out.append(replacement[i])
                i++
                continue
            }
            when (val sigil = replacement[i + 1]) {
                '$' -> { out.append('$'); i += 2 }
                '&' -> { out.append(match.value); i += 2 }
                '`' -> { out.append(source, 0, match.range.first); i += 2 }
                '\'' -> { out.append(source, match.range.last + 1, source.length); i += 2 }
                '<' -> {
                    val close = replacement.indexOf('>', i + 2)
                    val name = if (close < 0) "" else replacement.substring(i + 2, close)
                    val group = if (name.isEmpty()) null else namedGroup(match, name)
                    if (group == null) {
                        // 组不存在 → 整段原样（`$<nope>` 就显示成 `$<nope>`）
                        if (close < 0) { out.append('$'); i++ } else { out.append(replacement, i, close + 1); i = close + 1 }
                    } else {
                        out.append(group)
                        i = close + 1
                    }
                }
                in '0'..'9' -> {
                    val digits = replacement.length - i - 1
                    val first = sigil - '0'
                    val last1 = match.groups.size - 1
                    val two = if (digits >= 2 && replacement[i + 2].isDigit()) first * 10 + (replacement[i + 2] - '0') else -1
                    when {
                        two in 1..last1 -> { out.append(match.groups[two]?.value.orEmpty()); i += 3 }
                        first in 1..last1 -> { out.append(match.groups[first]?.value.orEmpty()); i += 2 }
                        two >= 0 -> { out.append(replacement, i, i + 3); i += 3 }
                        else -> { out.append(replacement, i, i + 2); i += 2 }
                    }
                }
                else -> { out.append('$'); i++ }
            }
        }
        return out.toString()
    }

    /** 具名组（无该名字时返回 null；Kotlin 的 `get(name)` 会抛，所以兜一层）。 */
    private fun namedGroup(match: MatchResult, name: String): String? =
        runCatching { (match.groups as? MatchNamedGroupCollection)?.get(name)?.value }.getOrNull()

    // ------------------------------------------------------------------ 常量

    private val USER_PLACEHOLDER = Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE)

    private const val JS_FLAG_CHARS = "gimsuy"

    private val INLINE_MODIFIERS = listOf('s', 'i', 'm')

    /** 上游 `getImageTagRegex()`（`core-utils.js:72`）：NAI 的 `image###tags###` 行。 */
    private const val IMAGE_TAG_PATTERN = """image###((?:(?!image###|###)[^\r\n])*?)(?:###|(?=\r?\n)|\z)"""

    /**
     * 受保护区总模式（`core-utils.js:293` 的逐字移植）。
     *
     * 三处**刻意的写法差异**：
     * ① 上游的 `$` 在 JS 里只匹配串尾，Java 里还会匹配「末尾换行之前」→ 全部换成 `\z`；
     * ② 上游用 `String.split`，这里用 `findAll` 等价切分；
     * ③ `[^"'<>]` 等字符类原样保留（Java 与 JS 在此一致）。
     *
     * 开闭标签成对但**跨行**（`[\s\S]`），所以模式必须用 DOTALL 之外的写法：`[\s\S]` 本身即跨行。
     */
    private val PROTECTED = Regex(
        "<!DOCTYPE html>[\\s\\S]*?</html>" +
            "|<html\\b[^>]*>[\\s\\S]*?</html>" +
            "|<script\\b[^>]*>[\\s\\S]*?</script>" +
            "|<style\\b[^>]*>[\\s\\S]*?</style>" +
            "|<!DOCTYPE html>[\\s\\S]*\\z" +
            "|<html\\b[^>]*>[\\s\\S]*\\z" +
            "|<script\\b[^>]*>[\\s\\S]*\\z" +
            "|<style\\b[^>]*>[\\s\\S]*\\z" +
            "|<ui_template_updates\\b[^>]*>[\\s\\S]*?(?:</ui_template_updates>|\\z)" +
            "|<!--[\\s\\S]*?(?:-->|\\z)" +
            "|```[\\s\\S]*?```" +
            "|```[\\s\\S]*\\z" +
            "|`[^`]+`" +
            "|</?[a-zA-Z][\\w:-]*(?:[^\"'<>]|\"[^\"]*\"|'[^']*')*>",
        RegexOption.IGNORE_CASE,
    )

    /** 只取代码围栏与行内代码（思考块扫描要跳过它们）。 */
    private val CODE_RANGES = Regex("```[\\s\\S]*?```|```[\\s\\S]*\\z|`[^`]+`")

    /** 思考块开闭标签（`parseCot` token 列表里的 `thinking|think|cot` 部分）。 */
    private val COT_TOKEN = Regex("""<\s*(/?)\s*(thinking|think|cot)\s*>""", RegexOption.IGNORE_CASE)
}

// ---------------------------------------------------------------------- JSON 取值

/** 取字符串字段；非字符串/缺失/null 都给空串。 */
private fun JsonObject.text(key: String): String = this[key]?.let { element ->
    if (element is JsonNull || element !is JsonPrimitive || !element.isString) "" else element.content
} ?: ""

private fun JsonObject.bool(key: String): Boolean? = this[key]?.let { element ->
    if (element is JsonNull) null else (element as? JsonPrimitive)?.booleanOrNull
}

private fun JsonObject.int(key: String): Int? = this[key]?.let { element ->
    if (element is JsonNull) null else (element as? JsonPrimitive)?.intOrNull
}

/** 取整数数组（上游 `placement`）；缺失给 null（调用方回落默认 `[1,2]`）。 */
private fun JsonObject.ints(key: String): Set<Int>? {
    val array = this[key] as? JsonArray ?: return null
    return array.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.toSet()
}

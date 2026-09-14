package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **正则脚本（显示期）** 的纯函数门禁。
 *
 * 为什么这些用例值得写：这是「用户配好的正则一条也不生效」的修复点，判据全部来自上游
 * `processRegex`（`app.js:4014-4091`）与 `splitProtectedText`（`core-utils.js:293-309`）的
 * **逐条语义**——哪个角色跳过、哪种勾选跳过、哪些区域不许改、`$&` 怎么展开。
 * 这类语义改坏了不报错、只是用户的高亮悄悄没了，只能靠用例钉住。
 *
 * 替换串里的 `$` 一律写成 `\$`：Kotlin 模板串里 `$` 的行为容易看走眼，
 * 而这些用例的全部意义就是「`$` 到用户手里是什么」。
 */
class RegexScriptsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun script(
        pattern: String,
        replacement: String,
        name: String = "t",
        flags: String = "g",
        placement: Set<Int> = setOf(1, 2),
        markdownOnly: Boolean = false,
        promptOnly: Boolean = false,
        enabled: Boolean = true,
        minDepth: Int? = null,
        maxDepth: Int? = null,
    ) = RegexScript(
        name = name,
        pattern = pattern,
        flags = flags,
        replacement = replacement,
        placement = placement,
        scope = "global",
        markdownOnly = markdownOnly,
        promptOnly = promptOnly,
        minDepth = minDepth,
        maxDepth = maxDepth,
        enabled = enabled,
    )

    private fun display(
        text: String,
        vararg scripts: RegexScript,
        role: LlmRole = LlmRole.ASSISTANT,
        userName: String = "",
    ) = RegexScripts.apply(
        text = text,
        scripts = scripts.toList(),
        role = role,
        mode = RegexScripts.Mode.Display,
        userName = userName,
    )

    // ────────────────────────── 基本套用

    @Test
    fun `引号包裹高亮——最典型的一条用户脚本`() {
        val out = display(
            "他说「今晚有雨」。",
            script("「([^」]*)」", "<span style=\"color:#e0a\">「\$1」</span>"),
        )
        assertEquals("他说<span style=\"color:#e0a\">「今晚有雨」</span>。", out)
    }

    @Test
    fun `斜杠包裹的匹配式与 flags 会被拆开`() {
        assertEquals("ab", display("aXb", script("/x/i", "")))
    }

    @Test
    fun `内联修饰符搬进 flags（(?i) 大小写不敏感）`() {
        assertEquals("ab", display("aXb", script("(?i)x", "")))
    }

    @Test
    fun `flags 里没有 g 时只替换第一处`() {
        assertEquals("1-a", display("a-a", script("/a/", "1")))
        assertEquals("1-1", display("a-a", script("/a/g", "1")))
    }

    @Test
    fun `替换串支持 $& 与 $$`() {
        assertEquals("<b>词</b>", display("词", script("词", "<b>\$&</b>")))
        assertEquals("价格 \$5", display("价格 5", script("5", "\$5")))
        assertEquals("\$", display("x", script("x", "\$\$")))
    }

    @Test
    fun `替换串支持匹配前文与后文`() {
        assertEquals("前[前|后]后", display("前中后", script("中", "[\$`|\$']")))
    }

    @Test
    fun `替换串支持具名组`() {
        assertEquals("值=7", display("v7", script("v(?<n>\\d)", "值=\$<n>")))
        // 组名不存在 → 整段原样吐回（不吞字符）
        assertEquals("\$<nope>", display("x", script("x", "\$<nope>")))
    }

    @Test
    fun `两位数组号越界时回落到一位数组`() {
        // 只有 1 个组：$12 应该是「组1 + 字面 2」
        assertEquals("a2", display("a", script("(a)", "\$12")))
    }

    // ────────────────────────── 角色 / 勾选 / 深度判据

    @Test
    fun `placement 只对勾了的位置生效`() {
        val aiOnly = script("x", "1", placement = setOf(2))
        assertEquals("1", display("x", aiOnly, role = LlmRole.ASSISTANT))
        assertEquals("x", display("x", aiOnly, role = LlmRole.USER))
    }

    @Test
    fun `system 角色不套脚本`() {
        assertEquals("x", display("x", script("x", "1"), role = LlmRole.SYSTEM))
    }

    @Test
    fun `显示期跳过仅提示词的脚本`() {
        assertEquals("x", display("x", script("x", "1", promptOnly = true)))
    }

    @Test
    fun `enabled 为 false 的脚本跳过`() {
        assertEquals("x", display("x", script("x", "1", enabled = false)))
    }

    @Test
    fun `深度区间之外跳过`() {
        assertEquals("x", display("x", script("x", "1", minDepth = 2)))
        assertEquals("1", display("x", script("x", "1", maxDepth = 2)))
    }

    @Test
    fun `未给 placement 的旧数据默认全部生效`() {
        val legacy = RegexScript.from(
            json.parseToJsonElement("""{"name":"old","regex":"x","replacement":"1"}"""),
        )!!
        assertEquals(setOf(1, 2), legacy.placement)
        assertTrue(legacy.enabled)
        assertEquals("1", display("x", legacy))
    }

    // ────────────────────────── 受保护区

    @Test
    fun `代码围栏内不被改写`() {
        val text = "正文 x\n\n```\nx\n```\n"
        assertEquals("正文 1\n\n```\nx\n```\n", display(text, script("x", "1")))
    }

    @Test
    fun `行内代码内不被改写`() {
        assertEquals("看 `x` 与 1", display("看 `x` 与 x", script("x", "1")))
    }

    @Test
    fun `HTML 标签本身不被改写，标签外的正文照改`() {
        assertEquals(
            "<span class=\"x\">正文 1</span>",
            display("<span class=\"x\">正文 x</span>", script("x", "1")),
        )
    }

    @Test
    fun `匹配式含尖括号时整段直改（用户可以显式动标签）`() {
        assertEquals("<i>x</i>", display("<b>x</b>", script("<b>(.*?)</b>", "<i>\$1</i>")))
    }

    @Test
    fun `思考块内不被改写`() {
        assertEquals(
            "<thinking>x</thinking>正文 1",
            display("<thinking>x</thinking>正文 x", script("x", "1")),
        )
    }

    @Test
    fun `未闭合的思考块算到文末`() {
        assertEquals(
            "正文 1\n<thinking>还有 x",
            display("正文 x\n<thinking>还有 x", script("x", "1")),
        )
    }

    @Test
    fun `代码围栏里的 thinking 标签不算思考块`() {
        assertEquals(
            "```\n<thinking>x\n```\n\n1",
            display("```\n<thinking>x\n```\n\nx", script("x", "1")),
        )
    }

    @Test
    fun `ui_template_updates 块受保护`() {
        assertEquals(
            "<ui_template_updates>{\"x\":1}</ui_template_updates>\n正文 1",
            display("<ui_template_updates>{\"x\":1}</ui_template_updates>\n正文 x", script("x", "1")),
        )
    }

    @Test
    fun `受保护区切分：空白片段与保护区交替且不丢字符`() {
        val text = "a `code` b <!-- c --> d"
        val parts = RegexScripts.parts(text)
        assertEquals(text, parts.joinToString("") { it.text })
        assertEquals(listOf(false, true, false, true, false), parts.map { it.protected })
    }

    // ────────────────────────── 包裹型用法 / 边界

    @Test
    fun `整段包裹只套一次壳（不给每段文字重复套）`() {
        // 取巧路径的判据：整段匹配 + 替换结果仍含原文 → 直接用整段结果。
        // 不用取巧路径（逐受保护区包裹）会把代码围栏切开、变成三个壳。
        assertEquals(
            "<div>一\n```\nx\n```\n二</div>",
            display("一\n```\nx\n```\n二", script("(?s)一.*二", "<div>\$&</div>")),
        )
    }

    @Test
    fun `全局替换在串尾的空匹配也会替换（与 JS 一致）`() {
        // `"abc".replace(/.*/g, "X")` 在 JS 里是 "XX" —— 这里不做「看起来更合理」的修正
        assertEquals("XX", display("abc", script(".*", "X")))
    }

    @Test
    fun `用户名占位符在套脚本之前替换`() {
        assertEquals("你好，鹿溪", display("你好，{{user}}", script("鹿溪", "鹿溪"), userName = "鹿溪"))
        assertEquals("你好，鹿溪", display("你好，{{ USER }}", userName = " 鹿溪 "))
    }

    @Test
    fun `空文本直接返回空`() {
        assertEquals("", display("", script("x", "1")))
    }

    @Test
    fun `匹配式编译失败只跳过该条，其余照常`() {
        assertEquals("x 2", display("x y", script("([", "1"), script("y", "2")))
    }

    /**
     * **NAI 生图正则被显式跳过**（用户 2026-09-14 拍板，理由见 [RegexScripts.IMAGE_GEN_UNSUPPORTED]）。
     *
     * 这条用例**取代**了原先那条「NAI 用内置图片标签匹配式」——不是语义变了，
     * 而是整条脚本在判定阶段就被拦下了（原先那条断言的是「放行之后」的行为，
     * 现在放行被关掉了，所以它不再可达）。
     *
     * 跳过的效果：正文里保留 `image###…###` **原文**，而不是渲染一张永远不出图的空卡片。
     */
    @Test
    fun `NAI画图正则被显式跳过（不放行生图管线）`() {
        val text = "image###1girl, solo###"
        assertEquals(
            "替换必须不发生",
            text,
            display(text, script("这条 pattern 会被忽略", "[图]", name = RegexScript.IMAGE_GEN_NAME)),
        )
        // 跳过只针对这一条：同一次调用里的其它脚本照常生效
        assertEquals(
            "[图]image###1girl, solo###",
            display(
                text,
                script("这条 pattern 会被忽略", "[图]", name = RegexScript.IMAGE_GEN_NAME),
                script("^", "[图]"),
            ),
        )
    }

    /** 跳过是**按名字**判的（上游对这条脚本的识别就是名字，`app.js:4021`）。 */
    @Test
    fun `名字不同的脚本不受生图跳过影响`() {
        assertEquals(
            "[图]",
            display("x", script("x", "[图]", name = "NAI画图正则2")),
        )
    }

    /** 跳过的常量本身（将来生图管线做好后翻它即可恢复；这条用例是那个开关的守卫）。 */
    @Test
    fun `生图跳过开关当前为开`() {
        assertTrue(
            "若这里变红：说明有人放行了生图正则。放行前请先读 RegexScripts.IMAGE_GEN_UNSUPPORTED 的说明——" +
                "卡片图片靠上游 JS 管线填、我们的卡片网络被禁，放行会得到一张永远不出图的空卡片",
            RegexScripts.IMAGE_GEN_UNSUPPORTED,
        )
    }

    @Test
    fun `多条脚本按顺序依次套用`() {
        assertEquals("c", display("a", script("a", "b"), script("b", "c")))
    }

    // ────────────────────────── 数据解析

    @Test
    fun `解析别名键（老数据的 findRegex replaceString disabled）`() {
        val parsed = RegexScript.from(
            json.parseToJsonElement(
                """{"scriptName":"别名","findRegex":"x","replaceString":"1","regexFlags":"i","disabled":true}""",
            ),
        )!!
        assertEquals("别名", parsed.name)
        assertEquals("x", parsed.pattern)
        assertEquals("1", parsed.replacement)
        assertEquals("i", parsed.flags)
        assertFalse(parsed.enabled)
    }

    @Test
    fun `两项都勾时 promptOnly 让位（上游 normalize 末段）`() {
        val parsed = RegexScript.from(
            json.parseToJsonElement("""{"name":"n","regex":"x","markdownOnly":true,"promptOnly":true}"""),
        )!!
        assertTrue(parsed.markdownOnly)
        assertFalse(parsed.promptOnly)
    }

    @Test
    fun `非对象元素不产生脚本`() {
        assertNull(RegexScript.from(json.parseToJsonElement("\"just a string\"")))
    }

    @Test
    fun `仅用户可见的脚本不进提示词路径`() {
        val s = script("x", "1", markdownOnly = true)
        assertEquals("1", display("x", s))
        assertEquals("x", RegexScripts.apply("x", listOf(s), LlmRole.ASSISTANT, RegexScripts.Mode.Prompt))
    }
}

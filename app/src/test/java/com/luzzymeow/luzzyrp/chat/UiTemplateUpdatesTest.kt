package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **UI 模板变量块**（`<ui_template_updates>`）的纯函数门禁。
 *
 * 这一组用例守的是「模型念配置数据」那个症状：块里的 JSON 此前会整段显示成正文。
 * 判据全部来自上游 `data-services.js:1650-1691` 的**逐条语义**——认哪一块、算到哪结束、
 * JSON 容忍哪几种形态。三条最容易写错、也最容易被后来的改动碰坏的：
 *
 * 1. **只认受保护区之外的最后一块**：代码围栏里的示例标签**不是**变量块（模型真的会贴）；
 * 2. **未闭合算到文末**：模型流式输出被截断时块就是没闭合的，那时也得剥；
 * 3. **非法 JSON 不炸界面**：解析失败必须是可判定的返回值，而不是抛到渲染链上。
 */
class UiTemplateUpdatesTest {

    private val block = "<ui_template_updates>\n{\"hp\": 3}\n</ui_template_updates>"

    // ────────────────────────── 剥除

    @Test
    fun `剥除后正文不含块`() {
        val text = "墙上的铭文亮了一下。\n\n$block"
        val stripped = UiTemplateUpdates.strip(text)

        assertFalse("块必须消失", stripped.contains("ui_template_updates"))
        assertFalse("块里的 JSON 也不该留在正文里", stripped.contains("hp"))
        assertEquals("墙上的铭文亮了一下。", stripped)
    }

    /**
     * 上游的剥除口径是「取块之前的部分」——**块之后的东西也一并不要**。
     * 协议规定块在正文结束后追加，所以这不会伤到正文；但写成断言才不会被「顺手改好」。
     */
    @Test
    fun `块之后的尾巴也一并去掉（照上游口径）`() {
        val text = "正文\n$block\n这里是块后面的文字"
        assertEquals("正文", UiTemplateUpdates.strip(text))
    }

    @Test
    fun `没有块时逐字返回`() {
        // 连 trim 都不做：用户正文本身的前后空行不该被我们改写
        val text = "\n\n就是一段普通正文，带 <b>标签</b>。\n\n"
        assertEquals(text, UiTemplateUpdates.strip(text))
    }

    @Test
    fun `空串与只有块的安全`() {
        assertEquals("", UiTemplateUpdates.strip(""))
        assertEquals("", UiTemplateUpdates.strip(block))
        // 纯空白**没有块** → 原样返回（上游 `findUiTemplateUpdateBlock` 判空即返回 source），
        // 不是 trim 成空串：剥除只在真的找到块时才动文本
        assertEquals("   ", UiTemplateUpdates.strip("   "))
    }

    @Test
    fun `未闭合的块算到文末`() {
        val text = "正文在此\n<ui_template_updates>\n{\"hp\": 3"
        assertEquals("正文在此", UiTemplateUpdates.strip(text))
    }

    /**
     * **代码围栏里的标签不是变量块**——模型经常在正文里贴一段协议示例（讲格式、写教程）。
     * 那段必须留在正文：剥掉它等于把正文吃掉一半，而且用户根本看不出为什么少了一段。
     */
    @Test
    fun `代码围栏里的示例标签不算变量块`() {
        val text = "格式是这样：\n```\n<ui_template_updates>\n{\"hp\": 3}\n</ui_template_updates>\n```\n就这么多"
        assertEquals("围栏里的示例必须原样留在正文", text, UiTemplateUpdates.strip(text))
    }

    @Test
    fun `行内码里的标签同样不算`() {
        val text = "用 `<ui_template_updates>` 包住变量块"
        assertEquals(text, UiTemplateUpdates.strip(text))
    }

    /** 注释里的标签也不算（上游把 `<!--…-->` 放进受保护区同一批）。 */
    @Test
    fun `注释里的标签不算`() {
        val text = "<!-- <ui_template_updates>{}</ui_template_updates> --> 正文"
        assertEquals(text, UiTemplateUpdates.strip(text))
    }

    /**
     * **只认最后一块**：先有一段示例（在围栏里，不算），末尾才是真块 → 认末尾那块。
     * 这条同时证明「逆序扫描」确实生效：顺序扫描会先撞上示例。
     */
    @Test
    fun `只认受保护区之外的最后一块`() {
        val text = "讲个格式：\n```\n<ui_template_updates>\n{\"示例\": 1}\n</ui_template_updates>\n```\n看懂了。\n\n$block"
        val stripped = UiTemplateUpdates.strip(text)

        assertFalse("末尾的真块必须被剥掉", stripped.contains("hp"))
        assertTrue("围栏里的示例必须还在", stripped.contains("{\"示例\": 1}"))
        assertTrue("正文也必须还在", stripped.contains("看懂了。"))
    }

    /** 开标签带属性也要认（上游正则写作 `<ui_template_updates\b[^>]*>`）。 */
    @Test
    fun `开标签带属性仍被认作变量块`() {
        val text = "正文\n<ui_template_updates data-x=\"1\">\n{\"hp\": 3}\n</ui_template_updates>"
        assertEquals("正文", UiTemplateUpdates.strip(text))
    }

    @Test
    fun `大小写不敏感`() {
        val text = "正文\n<UI_TEMPLATE_UPDATES>\n{\"hp\": 3}\n</UI_TEMPLATE_UPDATES>"
        assertEquals("正文", UiTemplateUpdates.strip(text))
    }

    @Test
    fun `找到的块带原文下标与块体`() {
        val text = "一二三\n$block"
        val found = UiTemplateUpdates.find(text)
        assertNotNull("应能找到块", found)

        assertEquals("下标必须指向**原文**的开标签（剥除点）", 4, found!!.index)
        assertEquals(4, text.indexOf('<'))
        assertEquals("块体是标签之间那段", "{\"hp\": 3}", found.body.trim())
        assertTrue("raw 是整块", found.raw.startsWith("<ui_template_updates>"))
        assertTrue(found.raw.endsWith("</ui_template_updates>"))
    }

    /**
     * **受保护区外的最后一块**与「思考块」的交互：CoT 在正文之前时，
     * 变量块仍是「非保护片段里的最后一个」。
     */
    @Test
    fun `与思考块共存时仍能正确剥除`() {
        val text = "<thinking>我想过了</thinking>\n正文在此\n$block"
        val stripped = UiTemplateUpdates.strip(text)

        assertTrue("思维链仍在（它不是我们的职责）", stripped.contains("<thinking>"))
        assertTrue("正文仍在", stripped.contains("正文在此"))
        assertFalse("变量块被剥掉", stripped.contains("hp"))
    }

    // ────────────────────────── 解析

    @Test
    fun `单模板形态直接给变量对象`() {
        val parsed = UiTemplateUpdates.parse("{\"hp\": 3, \"name\": \"阿烛\"}")

        assertTrue(parsed.ok)
        assertEquals(1, parsed.updates.size)
        assertEquals("单模板的 id 是空串（调用方按唯一模板兜底，照上游）", "", parsed.updates[0].id)
        val vars = parsed.updates[0].variables.jsonObject
        assertEquals(3, vars["hp"]!!.jsonPrimitive.content.toInt())
        assertEquals("阿烛", vars["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `单模板也可以是数组`() {
        val parsed = UiTemplateUpdates.parse("[1, 2, 3]")

        assertTrue(parsed.ok)
        assertEquals(1, parsed.updates.size)
        assertEquals("", parsed.updates[0].id)
    }

    /**
     * **``` 围栏容忍**：提示词明说不要包 Markdown，但模型照做——这是真实数据里最常见的形态。
     * 上游为此专门写了两次 `replace`（`data-services.js:1673-1674`）。
     */
    @Test
    fun `容忍 json 围栏`() {
        for (wrapped in listOf(
            "```json\n{\"hp\": 3}\n```",
            "```\n{\"hp\": 3}\n```",
            "```JSON {\"hp\": 3} ```",
        )) {
            val parsed = UiTemplateUpdates.parse(wrapped)
            assertTrue("应能解析：$wrapped", parsed.ok)
            assertEquals(1, parsed.updates.size)
            assertEquals(3, parsed.updates[0].variables.jsonObject["hp"]!!.jsonPrimitive.content.toInt())
        }
    }

    /**
     * **多模板形态**：数组、每个成员有字符串 `id` 与 `variables` 键 → 拆成多条更新。
     * 触发条件是 `expectedTemplateCount > 1`（上游 `expectedTemplates.length > 1`）——
     * 同一段 JSON 在单模板下必须**不拆**（那时它只是一个数组变量）。
     */
    @Test
    fun `多模板形态按成员拆开`() {
        val raw = "[{\"id\":\" status \",\"variables\":{\"hp\":3}},{\"id\":\"mood\",\"variables\":{}}]"
        val parsed = UiTemplateUpdates.parse(raw, expectedTemplateCount = 2)

        assertTrue(parsed.ok)
        assertEquals(2, parsed.updates.size)
        assertEquals("id 要去空白（上游 .trim()）", "status", parsed.updates[0].id)
        assertEquals("mood", parsed.updates[1].id)
        assertEquals(3, parsed.updates[0].variables.jsonObject["hp"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `同一段 JSON 在单模板下不拆`() {
        val raw = "[{\"id\":\"status\",\"variables\":{\"hp\":3}}]"
        val parsed = UiTemplateUpdates.parse(raw, expectedTemplateCount = 1)

        assertEquals("单模板下它只是一个数组变量", 1, parsed.updates.size)
        assertEquals("", parsed.updates[0].id)
    }

    /** 成员判据缺一条就不算多模板形态（上游要求：对象、非数组、有字符串 id、有 variables 键）。 */
    @Test
    fun `成员判据不全时回落成单条`() {
        val cases = listOf(
            "[{\"variables\":{\"hp\":3}}]",             // 没有 id
            "[{\"id\":\"a\"}]",                        // 没有 variables 键
            "[{\"id\":123,\"variables\":{}}]",         // id 不是字符串
            "[[{\"id\":\"a\",\"variables\":{}}]]",     // 成员是数组
        )
        for (raw in cases) {
            val parsed = UiTemplateUpdates.parse(raw, expectedTemplateCount = 2)
            assertTrue(parsed.ok)
            assertEquals("应回落成单条：$raw", 1, parsed.updates.size)
            assertEquals("", parsed.updates[0].id)
        }
    }

    /** `variables` 值为 null 也算「有该键」（上游用 `hasOwnProperty`）。 */
    @Test
    fun `variables 为 null 也算多模板成员`() {
        val parsed = UiTemplateUpdates.parse(
            "[{\"id\":\"a\",\"variables\":null},{\"id\":\"b\",\"variables\":{}}]",
            expectedTemplateCount = 2,
        )
        assertEquals(2, parsed.updates.size)
        assertEquals("a", parsed.updates[0].id)
    }

    // ────────────────────────── 非法输入（本组是「不炸界面」的判据）

    @Test
    fun `非法 JSON 返回错误而不是抛异常`() {
        val parsed = UiTemplateUpdates.parse("{不是 JSON")

        assertFalse(parsed.ok)
        assertTrue("错误信息要带得出原因", parsed.error!!.contains("JSON变量块格式错误"))
        assertTrue("不应产出任何更新", parsed.updates.isEmpty())
    }

    @Test
    fun `空内容与纯空白不算错误`() {
        assertEquals(0, UiTemplateUpdates.parse("").updates.size)
        assertTrue(UiTemplateUpdates.parse("").ok)
        assertTrue(UiTemplateUpdates.parse("   \n  ").ok)
    }

    /** 空块（`<ui_template_updates></ui_template_updates>`）→ 没有更新，且**不是**错误。 */
    @Test
    fun `空变量块剥除正常且解析为空`() {
        val text = "正文\n<ui_template_updates></ui_template_updates>"
        val (stripped, parsed) = UiTemplateUpdates.stripAndParse(text)

        assertEquals("正文", stripped)
        assertTrue(parsed.ok)
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun `stripAndParse 在没有块时给出原样正文与空结果`() {
        val text = "只有正文"
        val (stripped, parsed) = UiTemplateUpdates.stripAndParse(text)

        assertEquals(text, stripped)
        assertNull(parsed.error)
        assertTrue(parsed.isEmpty)
    }

    /**
     * **块内 JSON 不参与正则与行内 HTML**（任务给的判据原文）。
     *
     * 做法就是先剥后渲染：剥掉之后，正文里既没有 `<` 也没有那些引号，于是
     * ① 显示期正则改不到它；② 行内 HTML 解析器也看不到它——两者都不需要知道变量块存在。
     * 这里把这条链**实际跑一遍**，而不是只断言 `strip` 的返回值。
     */
    @Test
    fun `块内 JSON 不参与正则与行内 HTML`() {
        val scripts = listOf(
            RegexScript(
                name = "把所有数字包成高亮",
                pattern = "\\d+",
                flags = "g",
                replacement = "<span style=\"color:#e0a\">\$&</span>",
                placement = setOf(1, 2),
                scope = "global",
                markdownOnly = false,
                promptOnly = false,
                minDepth = null,
                maxDepth = null,
                enabled = true,
            ),
        )
        val raw = "他数了数：2 颗糖。\n\n<ui_template_updates>\n{\"hp\": 3, \"note\": \"<span>\"}\n</ui_template_updates>"

        val stripped = UiTemplateUpdates.strip(raw)
        val applied = RegexScripts.apply(
            text = stripped,
            scripts = scripts,
            role = com.luzzymeow.luzzyrp.chat.llm.LlmRole.ASSISTANT,
            mode = RegexScripts.Mode.Display,
        )

        assertTrue("正文里的数字被正则改到了（前提自证）", applied.contains(">2<"))
        assertFalse("块里的数字 3 不该被改", applied.contains(">3<"))
        assertFalse("块里的 JSON 键名不该出现在正文里", applied.contains("hp"))
        assertFalse("块内那个 <span> 也不该进正文", applied.contains("<span>\""))

        // 行内 HTML 解析器认的是「有没有标签」——剥除后的文本里没有块内那个 `<span>`，
        // 于是它只能看到正则自己产出的高亮标签（这正是我们要的顺序：先剥块，再走渲染链）
        assertTrue("行内层应认得出正则产出的 <span>（前提自证）", com.luzzymeow.luzzyrp.ui.markdown.InlineHtml.hasTags(applied))
        assertFalse(
            "块被剥掉后，行内层不该再见到块里的那个 <span>",
            com.luzzymeow.luzzyrp.ui.markdown.InlineHtml.hasTags("他数了数：2 颗糖。"),
        )
    }

    /** 剥除是**纯函数**：同输入同输出（A5 的判据在 A2 上同样要成立）。 */
    @Test
    fun `剥除与解析都是确定性的`() {
        val text = "正文\n$block\n尾巴"
        assertEquals(UiTemplateUpdates.strip(text), UiTemplateUpdates.strip(text))

        val first = UiTemplateUpdates.parse("{\"a\":1,\"b\":[1,2]}")
        val second = UiTemplateUpdates.parse("{\"a\":1,\"b\":[1,2]}")
        assertEquals(first.updates.size, second.updates.size)
        assertEquals(first.updates[0].variables.toString(), second.updates[0].variables.toString())
        assertEquals(first.error, second.error)
    }

    /** 大块 JSON 不应引起数量级退化（宽松界，只拦灾难性回归）。 */
    @Test
    fun `大块 JSON 的解析成本`() {
        val big = (1..500).joinToString(",", "{", "}") { "\"k$it\": $it" }
        val text = "正文\n<ui_template_updates>\n$big\n</ui_template_updates>"

        val stripped = kotlin.system.measureNanoTime { repeat(50) { UiTemplateUpdates.strip(text) } } / 50
        println("PERF UiTemplateUpdates.strip=${stripped / 1000.0}µs (text=${text.length}字)")

        assertEquals("正文", UiTemplateUpdates.strip(text))
        assertTrue("剥除超过 50ms 说明数量级变坏了，实际 ${stripped / 1000.0}µs", stripped < 50_000_000)
    }

    /** 解析出的变量对象必须能被调用方按类型读（不是只有字符串）。 */
    @Test
    fun `变量类型保真`() {
        val parsed = UiTemplateUpdates.parse("{\"n\":3,\"f\":1.5,\"b\":true,\"z\":null,\"a\":[1],\"o\":{\"x\":1}}")
        val vars = parsed.updates[0].variables as JsonObject

        assertTrue(vars["n"] is JsonPrimitive)
        assertEquals("3", (vars["n"] as JsonPrimitive).content)
        assertEquals(true, (vars["b"] as JsonPrimitive).content.toBoolean())
        assertEquals(kotlinx.serialization.json.JsonNull, vars["z"])
        assertTrue(vars["a"] is kotlinx.serialization.json.JsonArray)
        assertTrue(vars["o"] is JsonObject)
    }
}

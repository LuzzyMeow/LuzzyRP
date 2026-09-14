package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CoT 标记解析单测（`CotParser`）。
 *
 * **第一条用例直接用真实迁移数据**：夹具里那条 1847 字符的 assistant 消息，
 * 思维链就是内联在 `content` 里的（`<thinking>[情景意图分析]…</thinking>`，`reasoning` 字段为空）。
 * 这是把「思维链被当正文渲染」这个真实缺陷钉住的那条断言 —— 手写样例不会长这样。
 *
 * 其余几条覆盖移植时**绝不能简化**的行为：代码块里的标签不算 CoT、嵌套、以及流式半截标签。
 */
class CotParserTest {

    private val realCotMessage: String by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(text).jsonObject
        val entries = root["databases"]!!.jsonObject["RPHubDB"]!!.jsonObject["entries"]!!.jsonObject
        val branch = entries["rp_hub_chat_e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555__branch__a4e75ca8-07d2-440b-b55a-84e71eb2b1fe"]!!
            .jsonArray
        // 取那条最长、且内联了思维链的消息
        branch.map { it.jsonObject["content"]!!.jsonPrimitive.content }
            .maxByOrNull { if (it.contains("<thinking>")) it.length else -1 }!!
    }

    @Test
    fun `真实数据里内联的思维链被完整剥出`() {
        val parsed = CotParser.parse(realCotMessage)

        assertTrue("应识别出 CoT 标记", parsed.hadCot)
        assertTrue("有闭合标签 → 已完结", parsed.isFinished)
        assertTrue("思维链里应含[情景意图分析]", parsed.cot.contains("[情景意图分析]"))
        assertFalse("正文里不该再出现 thinking 标签", parsed.main.contains("<thinking>"))
        assertFalse("正文里不该再出现闭合标签", parsed.main.contains("</thinking>"))
        // 这条消息的真实形态是「CoT 在前、正文在后」：剥离后正文从剧情时间戳开始，
        // 而且**不能被连坐吃掉**（第一版测试以为它是「只有 CoT」，被真实数据纠正了）
        assertTrue("正文应保留剧情时间行，实际=${parsed.main.take(40)}", parsed.main.startsWith("【第1日 15时】"))
        assertTrue("正文应含场景描写", parsed.main.contains("楼梯是木头的"))
        assertTrue("思维链应含分析段", parsed.cot.contains("[设定分析]"))
        // **互不污染**才是真判据（长度比例不是：这条消息正好是「CoT 约 700 字 + 正文约 1100 字」）
        assertFalse("正文里不该残留思维链的任何一段", parsed.main.contains("[情景意图分析]"))
        assertFalse("正文里不该残留设定分析", parsed.main.contains("[设定分析]"))
        assertFalse("思维链里不该混进正文", parsed.cot.contains("楼梯是木头的"))
    }

    @Test
    fun `think 与 cot 变体同样识别`() {
        for (tag in listOf("thinking", "think", "THINKING", "cot")) {
            val parsed = CotParser.parse("<$tag>想了想</$tag>正文在这里")
            assertTrue("$tag 应被识别", parsed.hadCot)
            assertEquals("正文在这里", parsed.main)
            assertEquals("想了想", parsed.cot)
        }
    }

    @Test
    fun `嵌套标记整体算 CoT`() {
        val parsed = CotParser.parse("<thinking>外层<cot>内层</cot>外层尾巴</thinking>真正文")
        assertEquals("真正文", parsed.main)
        assertTrue(parsed.cot.contains("外层"))
        assertTrue(parsed.cot.contains("内层"))
        assertTrue(parsed.cot.contains("外层尾巴"))
        assertFalse(parsed.cot.contains("<cot>"))
    }

    /**
     * 模型经常在正文里贴一段**含 `<thinking>` 的示例代码**（讲提示词、讲格式时）。
     * 那段必须留在正文——否则正文会被吃掉一半。上游为此把围栏/行内码排在裸标签之前。
     */
    @Test
    fun `代码围栏与行内码里的标签不算 CoT`() {
        val fenced = CotParser.parse("看这段格式：\n```\n<thinking>示例</thinking>\n```\n就这样")
        assertFalse("围栏里的标签不该触发 CoT", fenced.hadCot)
        assertTrue("围栏内容应留在正文", fenced.main.contains("<thinking>示例</thinking>"))

        val inline = CotParser.parse("用 `<thinking>` 包住，像这样")
        assertFalse("行内码里的标签不该触发 CoT", inline.hadCot)

        // HTML 块与注释同理（上游把 html/script/style 整块跳过）
        val html = CotParser.parse("<script>var a = '<thinking>'</script>正文")
        assertFalse("script 块里的标签不该触发 CoT", html.hadCot)
        val comment = CotParser.parse("<!-- <thinking>注释</thinking> -->正文")
        assertFalse("注释里的标签不该触发 CoT", comment.hadCot)
    }

    @Test
    fun `流式半截标签先不展示`() {
        val partial = CotParser.parse("正文开始<thin")
        assertEquals("正文开始", partial.main)
        assertFalse("半截标签不算已开始 CoT", partial.hadCot)

        val closingPartial = CotParser.parse("<thinking>想了半天</thin")
        assertTrue(partial.hadCot || closingPartial.hadCot)
        assertEquals("想了半天", closingPartial.cot)
        assertEquals("", closingPartial.main)
        assertFalse("未闭合 → 未完结", closingPartial.isFinished)
    }

    @Test
    fun `没有标记时原样返回且不留痕`() {
        val plain = "就是一段普通正文，带 <b>粗体</b> 和行内代码。"
        val parsed = CotParser.parse(plain)
        assertFalse(parsed.hadCot)
        assertEquals(plain, parsed.main)
        assertEquals("", parsed.cot)
    }

    @Test
    fun `空串与纯空白安全`() {
        assertEquals(CotParser.Parsed.EMPTY, CotParser.parse(""))
        assertEquals("", CotParser.parse("   ").main)
    }

    @Test
    fun `rewrap 把思维链按原样装回（编辑正文时不丢 CoT）`() {
        val original = "<thinking>我想过了</thinking>\n原来的正文"
        val rewrapped = CotParser.rewrap(original, "改过的正文")
        assertTrue(rewrapped.startsWith("改过的正文"))
        assertTrue("思维链必须还在", rewrapped.contains("<thinking>"))
        assertTrue(rewrapped.contains("我想过了"))
        // 再解析一次应当还原成「新正文 + 原思维链」
        val again = CotParser.parse(rewrapped)
        assertEquals("改过的正文", again.main)
        assertEquals("我想过了", again.cot)

        // 原文本来没有 CoT → 不做任何包装
        assertEquals("改过的正文", CotParser.rewrap("普通消息", "改过的正文"))
    }

    /**
     * **成本剖面**（不是断言题）：确认真实长度的正文解析在微秒级，
     * 从而把「滚动掉帧」的嫌疑从解析器身上摘掉（或摘不掉）。
     *
     * 纪律：宽松上限只拦灾难性退化；具体数字打印出来供人比对。
     * 注意缓存的存在——所以这里分「冷」（每次新文本）与「热」（同一文本重复问）两组。
     */
    @Test
    fun `解析成本的量级`() {
        val body = "【第1日 15时】\n\n楼梯是木头的，踩上去确实会响。" + "正文段落，约两千字的量级。".repeat(120)
        val cot = "<thinking>\n[情景意图分析] " + "分析内容。".repeat(60) + "\n</thinking>\n\n" + body

        // 冷：每次换一个新文本（模拟滚动时不断出现新消息）
        val cold = kotlin.system.measureNanoTime {
            repeat(200) { i -> CotParser.parse(cot + "\n<!-- $i -->") }
        } / 200
        // 热：同一文本重复问（模拟同一消息在一次滚动里被问很多次）
        val hot = kotlin.system.measureNanoTime {
            repeat(2000) { CotParser.parse(cot) }
        } / 2000
        println("PERF CotParser cold=${cold / 1000.0}µs hot=${hot / 1000.0}µs (text=${cot.length}字)")

        // ⚠️ 这两条是**灾难性回归的冒烟界**，不是性能门禁（会话 74 修正）。
        //
        // 原来的冷解析界是 1ms，而实测冷解析**空载约 180µs** —— 只有 5.5× 余量。
        // 一旦本机同时有 gradle 编译 + adb 装机（会话 74 的真实场景），实测就飘到 **1246µs**
        // 而**假红**（`484 tests completed, 1 failed`，差值仅 0.25ms）。
        // 本仓库的纪律是「门禁判据必须确定性；概率性判据不用」—— 用挂钟时间卡这么紧
        // 本质上就是概率门禁。所以：界放宽到**空载值的 30 倍以上**，只用来抓「数量级变坏」
        // （例如正则回溯爆炸）；真正的性能判断看上面那行 `PERF` 的**数字**，由人看趋势。
        assertTrue("单次冷解析超过 6ms 说明数量级变坏了，实际 ${cold / 1000.0}µs", cold < 6_000_000)
        assertTrue("热路径应该几乎零成本，实际 ${hot / 1000.0}µs", hot < 500_000)
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
    }

    // ══════════════════════════ A3：完整移植（ranges / rawCot / closingTags）

    /** `ranges` 必须能**从原文里切回**思维链——这正是上游把它当受保护区用的前提。 */
    @Test
    fun `ranges 能在原文里精确切出思维链`() {
        val text = "前言\n<thinking>想了半天</thinking>\n正文在此"
        val parsed = CotParser.parse(text)

        assertEquals("应恰好一块", 1, parsed.ranges.size)
        val slice = text.substring(parsed.ranges[0].first, parsed.ranges[0].last + 1)
        assertEquals("<thinking>想了半天</thinking>", slice)
    }

    /** 多块：正文夹在两段思维链之间，两块的 range 都要对。 */
    @Test
    fun `多段思维链各自成一块`() {
        val text = "<thinking>第一段</thinking>中间正文<cot>第二段</cot>结尾"
        val parsed = CotParser.parse(text)

        assertEquals(2, parsed.ranges.size)
        assertEquals(
            "<thinking>第一段</thinking>",
            text.substring(parsed.ranges[0].first, parsed.ranges[0].last + 1),
        )
        assertEquals(
            "<cot>第二段</cot>",
            text.substring(parsed.ranges[1].first, parsed.ranges[1].last + 1),
        )
    }

    /** 嵌套时 range 必须是**外层**那一整块（内层开标签不许改块起点）。 */
    @Test
    fun `嵌套时 range 覆盖外层整块`() {
        val text = "前面<thinking>外层<cot>内层</cot>尾巴</thinking>后面"
        val parsed = CotParser.parse(text)

        assertEquals(1, parsed.ranges.size)
        val slice = text.substring(parsed.ranges[0].first, parsed.ranges[0].last + 1)
        assertTrue("必须从外层开标签起", slice.startsWith("<thinking>"))
        assertTrue("必须到外层闭标签止", slice.endsWith("</thinking>"))
        assertTrue(slice.contains("内层"))
    }

    /** **未闭合**时 range 延伸到文末（上游 `end: text.length`）。 */
    @Test
    fun `未闭合的 range 延伸到文末`() {
        val text = "正文\n<thinking>还没想完"
        val parsed = CotParser.parse(text)

        assertFalse(parsed.isFinished)
        assertEquals(1, parsed.ranges.size)
        assertEquals("起点是开标签", text.indexOf("<thinking>"), parsed.ranges[0].first)
        assertEquals("终点是文末", text.length - 1, parsed.ranges[0].last)
    }

    /** 围栏里的标签**不进 ranges**——否则正则会把示例代码当受保护区，正文里那段就改不动了。 */
    @Test
    fun `代码围栏里的标签不进 ranges`() {
        val parsed = CotParser.parse("看这段：\n```\n<thinking>示例</thinking>\n```\n正文")
        assertTrue("围栏里的标签不算思维链", parsed.ranges.isEmpty())
    }

    @Test
    fun `没有 CoT 时 ranges 为空`() {
        assertTrue(CotParser.parse("就是普通正文").ranges.isEmpty())
        assertTrue(CotParser.parse("").ranges.isEmpty())
    }

    /** `closingTags`：未闭合时给出「补上就能配平」的闭标签，逆序。 */
    @Test
    fun `未闭合时给出补齐用的闭标签`() {
        assertEquals("</thinking>", CotParser.parse("<thinking>想").closingTags)
        assertEquals("</cot></thinking>", CotParser.parse("<thinking>外层<cot>内层").closingTags)
    }

    @Test
    fun `已闭合时没有待补的闭标签`() {
        assertEquals("", CotParser.parse("<thinking>想完了</thinking>正文").closingTags)
        assertEquals("", CotParser.parse("普通正文").closingTags)
    }

    /**
     * `closingTags` 的**用途**（不是只给个字符串）：把它补到「原文 + 闭标签」之后，
     * 再解析一次就应当**闭合**。上游正是用它做编辑态的「改正文不丢未闭合的思维链」
     * （`app.js:5446-5447`）。
     */
    @Test
    fun `补上 closingTags 之后即可闭合并得到完整思维链`() {
        val text = "<thinking>想了半天"
        val parsed = CotParser.parse(text)
        val repaired = text + parsed.closingTags

        val again = CotParser.parse(repaired)
        assertTrue("补完之后应当是闭合状态", again.isFinished)
        assertEquals("想了半天", again.cot)
        assertEquals("", again.closingTags)
    }

    /** `rawCot`：思维链**原文**（含其中的标签），与 [Parsed.cot] 内容一致。 */
    @Test
    fun `rawCot 是思维链原文`() {
        val parsed = CotParser.parse("<thinking>第一句\n第二句</thinking>正文")

        assertEquals("第一句\n第二句", parsed.rawCot)
        assertEquals(parsed.cot, parsed.rawCot)
        assertFalse("rawCot 不该含 CoT 标记本身", parsed.rawCot.contains("<thinking>"))
    }

    /**
     * **`ranges` 是唯一真源**：`RegexScripts` 的受保护区从这里取（A3 把原来的重复实现收编了）。
     * 这条用例把两边的**一致性**钉住——同一段文本，`parts()` 里的保护片段必须等于 ranges。
     */
    @Test
    fun `受保护区的思考块与 ranges 逐块一致`() {
        val text = "前言\n<thinking>x</thinking>\n正文 <b>粗</b>"
        val parsed = CotParser.parse(text)
        val protectedParts = RegexScripts.parts(text).filter { it.protected && it.text.contains("thinking") }

        assertEquals("块数必须一致", parsed.ranges.size, protectedParts.size)
        parsed.ranges.forEachIndexed { index, range ->
            assertEquals(
                "第 $index 块的原文切片必须一致",
                text.substring(range.first, range.last + 1),
                protectedParts[index].text,
            )
        }
    }

    /** 委托之后 `cotRanges` 与 `parse().ranges` 必须是同一个答案（防止有人再写第二份实现）。 */
    @Test
    fun `cotRanges 与 ranges 同源`() {
        for (text in listOf(
            "<thinking>a</thinking>b",
            "<thinking>未闭合",
            "```\n<thinking>围栏</thinking>\n```",
            "无标记正文",
        )) {
            assertEquals("同一段文本必须只有一个答案：$text", CotParser.parse(text).ranges, RegexScripts.cotRanges(text))
        }
    }

    /**
     * **缺陷 #4 的纯逻辑一半**：真实数据里「思维链被当正文渲染」的症状，
     * 现在能被完整地拆成「正文（main）+ 思维链（cot/ranges）」两半。
     */
    @Test
    fun `真实数据里的内联 CoT 能同时给出正文与可回填的范围`() {
        val parsed = CotParser.parse(realCotMessage)

        assertTrue("应识别出 CoT", parsed.hadCot)
        assertTrue("真实数据是闭合的", parsed.isFinished)
        assertTrue("ranges 非空（渲染成节点要靠它定位）", parsed.ranges.isNotEmpty())
        assertEquals("补标签为空", "", parsed.closingTags)
        // ranges 拼起来 + main 应当能还原原文（内容守恒，除了被 trim 掉的首尾空白）
        val rebuilt = parsed.ranges.joinToString("") { realCotMessage.substring(it.first, it.last + 1) }
        assertTrue("从 ranges 切回来的必须含思维链开头", rebuilt.contains("[情景意图分析]"))
        assertFalse("正文里不许再出现思维链", parsed.main.contains("[情景意图分析]"))
    }
}
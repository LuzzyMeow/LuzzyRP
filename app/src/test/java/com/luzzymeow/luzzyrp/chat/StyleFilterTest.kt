package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **文风过滤**（`StyleFilter`）的纯函数门禁。
 *
 * 这一组用例的性质与别处不同：**它是唯一会删模型正文的功能**。删多了用户看不出来
 * （少了几句话，他会以为是模型本来就这么写的），删少了又等于没做。
 * 所以判据分两类：
 *
 * - **该删的**：上游黑名单里的短语（含整句 / 分句 / 单词三档）与字数声明句；
 * - **绝不许删的**（这类更容易写错，用例更密）：
 *   引号里的对白、代码围栏、行内码、HTML 标签与卡片、思考块、变量块。
 *
 * ## 本文件里所有期望值都是**跑出来的**，不是推理出来的
 *
 * 第一版用例凭直觉写期望，5 条红——**每一条都是我把上游语义想当然了**。
 * 于是改用真值表：把上游 `app.js:1433-1439` 那 6 条正则原文抽出来，
 * 用 node 跑一遍上游自己那条链式 replace，得到的输出即期望值。
 * 三处最反直觉、也因此最值得钉住的上游语义：
 *
 * | 现象 | 上游实际行为 | 直觉（错的） |
 * |---|---|---|
 * | `「不容置疑。」` | **会被删** —— 对白保护只认 `“”` / `『』` / `"`，**不认 `「」`** | 以为中日式引号也受保护 |
 * | `他向前一步，指尖因为用力而发白，然后开口。` | **整句都删** —— 整句档从上一个句读一路吃到下一个句读 | 以为只删「因为用力而发白」那截 |
 * | `共十二个字。` | **不匹配** —— 字数档的前缀只认「串首或句读」，`共` 挡住了 | 以为「共 N 个字」是典型形态 |
 *
 * 这三条不是实现缺陷，是**照抄上游**的结果（用户 2026-09-14 拍板「照上游」）；
 * 写成断言是为了下次有人觉得「这不合理」时，能立刻看到它是有出处、有真值的决定。
 */
class StyleFilterTest {

    private fun filter(text: String, enabled: Boolean = true) = StyleFilter.filter(text, enabled)

    // ────────────────────────── 该删的：三档 + 字数声明

    @Test
    fun `整句档：命中短语的整句被删掉`() {
        val out = filter("他看着她，嘴角勾起一抹弧度，不容置疑。下一句照常。")
        assertEquals("下一句照常。", out.text)
        assertTrue("命中片段要能报出来（将来做面板用）", out.removed.any { it.contains("不容置疑") })
    }

    /**
     * 命中短语所在的**整个句子**被删，前后句照留（真值表实测）。
     *
     * ## 一个如实登记的发现：分句档被整句档完全遮蔽
     *
     * 上游链式顺序是「整句档 → 指尖分句档 → 其他分句档 → 单词档」（`app.js:1484-1487`），
     * 而**两个分句档的短语集是整句档短语集的子集**：
     * 分句档 `微微泛|因为用力|像在|风箱|手术刀|上扬|带着一种` 与
     * `指尖|指节|指关节 … 发白|泛白` —— 逐条都能在整句档里找到。
     *
     * 于是整句档先把那一句吃干净，分句档看到的文本里**已经没有任何命中短语**，
     * 永远匹配不到（真值表逐一验证：7 个探针全部只留下前一句）。
     * 这不是我们的实现问题，是上游规则表的固有形态。
     *
     * **处理方式**：照抄保留（不删规则、不改语义），但**不假装它有用**——
     * 用例断言的是**观测到的真值**，注释写清它为什么不触发。将来若要用它，
     * 得先改整句档的优先级，那是偏离上游、要先问用户的决定。
     */
    @Test
    fun `命中短语所在的整句被删，前后句照留`() {
        assertEquals(
            "风从窗缝里挤进来。",
            filter("风从窗缝里挤进来。他因为用力而握紧了杯子，然后开口。").text,
        )
        assertEquals("A。", filter("A。他像在害怕，然后开口。").text)
        assertEquals("A。", filter("A。他的嘴角上扬，然后开口。").text)
    }

    /**
     * **整句档会连短语前面的同句内容一起删**（真值表 B 行）——这是最反直觉的一档，
     * 单独写成用例，免得下次有人把它当 bug「修」掉。
     */
    @Test
    fun `整句档：指尖发白命中时整句被删`() {
        assertEquals("", filter("他向前一步，指尖因为用力而发白，然后开口。").text)
        // 句读把整句切开之后，只有后半句被吃
        assertEquals("他向前一步。", filter("他向前一步。指尖因为用力而发白，然后开口。").text)
    }

    @Test
    fun `单词档：极其被删`() {
        val out = filter("这里极其安静。")
        assertEquals("这里安静。", out.text)
        assertTrue(out.removed.contains("极其"))
    }

    /**
     * 字数声明句：删声明、**留句读前缀**——上游的替换串是 `prefix`（`app.js:1480-1483`）。
     *
     * 注意用「十二个字」而**不是**「共十二个字」：后者的 `共` 挡住了前缀匹配，
     * 上游根本不会删它（真值表 E 行）。这条用例同时是那个坑的守卫。
     */
    @Test
    fun `字数声明句被删且保留前缀`() {
        val out = filter("他沉默了。十二个字。")
        assertFalse("声明必须消失", out.text.contains("个字"))
        assertTrue("前一句与其句读要留着", out.text.contains("他沉默了。"))
        assertTrue("命中片段应含那条声明", out.removed.any { it.contains("个字") })

        // 反面：带「共」的形态上游不匹配 → 逐字保留（别顺手把它「修好」，那是偏离上游）
        val withGong = "他沉默了。共十二个字。"
        assertEquals("带「共」的形态上游不删，我们也不删", withGong, filter(withGong).text)
    }

    @Test
    fun `没有命中时逐字返回`() {
        val text = "钟楼顶上长着一整树红苹果，风一吹就落下来两个。"
        val out = filter(text)
        assertEquals(text, out.text)
        assertTrue(out.removed.isEmpty())
        assertFalse(out.changed)
    }

    // ────────────────────────── 绝不许删的：这组是重点

    /**
     * **对白保护只认三种引号**：`“”` / `『』` / `"`（上游 `app.js:1438` 逐字如此）。
     *
     * `「」` 不在其中 —— 真值表实测「他开口了：「不容置疑。」」会被**整个删空**。
     * 这是照抄上游的结果（用户拍板「照上游」），不是漏做；要改就得先问用户，
     * 因为那会同时改变上游版的显示内容（同一份数据两边看到的不一样）。
     */
    @Test
    fun `三种受保护的引号里的对白不被筛`() {
        for (quoted in listOf(
            "“不容置疑。”",
            "『他极其平静。』",
            "\"指尖发白，像在害怕。\"",
        )) {
            val text = "他开口了：$quoted"
            assertEquals("对白必须逐字保留：$quoted", text, filter(text).text)
            assertTrue("对白里的短语不该进命中列表：$quoted", out(text).removed.isEmpty())
        }
    }

    private fun out(text: String) = filter(text)

    /** `「」` 的**如实登记**：上游不保护它，所以它会被删（真值表 G 行）。 */
    @Test
    fun `直角引号不在保护集内——与上游同（如实登记）`() {
        assertEquals("", filter("他开口了：「不容置疑。」").text)
    }

    /** 对白与非对白混排时，只筛非对白那几段（`「」` 段按上游口径会被筛）。 */
    @Test
    fun `对白与叙述混排时只筛叙述`() {
        val out = filter("“不容置疑。”他极其平静地说。“我们走吧。”")
        assertEquals("“不容置疑。”他平静地说。“我们走吧。”", out.text)
    }

    /** 代码围栏受保护（上游 `transformUnprotectedText`）。 */
    @Test
    fun `代码围栏内不被筛`() {
        val text = "看这段：\n```\n极其 不容置疑 一抹弧度\n```\n就这样"
        assertEquals(text, filter(text).text)
    }

    @Test
    fun `行内码内不被筛`() {
        val text = "用 `极其` 这个词"
        assertEquals(text, filter(text).text)
    }

    /** 思考块受保护：删它等于把模型的推理改坏（而且它是折叠展示的，本来就看不到）。 */
    @Test
    fun `思考块内不被筛`() {
        val text = "<thinking>这里极其重要，不容置疑。</thinking>\n正文在此"
        val out = filter(text)
        assertTrue("思维链必须逐字保留", out.text.contains("这里极其重要，不容置疑。"))
    }

    /** **变量块之后不筛**（上游 `filterEnd = 变量块下标`，`app.js:1475-1476`）。 */
    @Test
    fun `变量块之后的内容不参与筛选`() {
        val text = "正文。\n<ui_template_updates>\n{\"note\": \"极其重要\"}\n</ui_template_updates>"
        val out = filter(text)

        assertTrue("块（含其后的任何内容）必须原样", out.text.contains("极其重要"))
        assertFalse("块之后不该有命中记录", out.removed.any { it.contains("极其") })
    }

    /** **独立渲染内容整段跳过**（上游 `app.js:1473`）——卡片是内容本身，不是叙述。 */
    @Test
    fun `独立渲染内容整段跳过`() {
        for (text in listOf(
            "<div style=\"background:#0d1416\">极其重要的面板</div>",
            "```\n极其\n```",
            "<!DOCTYPE html>\n<html><body>极其</body></html>",
            "<table><tr><td>极其</td></tr></table>",
            "\n\n  <style>.a{color:red}</style>",
        )) {
            assertEquals("整段跳过：$text", text, filter(text).text)
        }
    }

    /**
     * 但**开头锚定**是有意义的：一段正常叙述中间出现 `<div>` **不算**独立渲染内容，
     * 那时黑名单短语照筛（上游用的就是 `^`）。
     */
    @Test
    fun `叙述中间出现的标签不影响筛选`() {
        val out = filter("他极其平静地说，然后举起<div>牌子</div>。")
        assertFalse("开头不是标签 → 照筛", out.text.contains("极其"))
    }

    // ────────────────────────── 开关与幂等

    @Test
    fun `关掉开关时一个字节都不动`() {
        val text = "他极其平静地说，嘴角勾起一抹弧度。"
        val out = filter(text, enabled = false)
        assertEquals(text, out.text)
        assertTrue(out.removed.isEmpty())
    }

    @Test
    fun `空串安全`() {
        assertEquals("", filter("").text)
        // 纯空白没有可删的东西 → 原样返回（上游那串 replace 不动空白）
        assertEquals("   ", filter("   ").text)
    }

    /** 纯函数：同输入同输出（A5 的判据在此同样适用）。 */
    @Test
    fun `过滤是确定性的`() {
        val text = "他极其平静，指尖发白。十二个字。“对白不容置疑。”"
        val first = filter(text)
        val second = filter(text)
        assertEquals(first.text, second.text)
        assertEquals(first.removed, second.removed)
    }

    /**
     * **幂等性**：已经筛过的文本再筛一次不应继续变。
     *
     * 为什么重要：这条链会在**每次重组/滚动**上重跑（`remember` 只挡同参数的重组，
     * 换主题、换脚本集都会让它重算）。若不幂等，用户滚动几次就会看到正文越来越短——
     * 而且不报错。真值表已实测上游幂等（`下一句在。` 二次不变）。
     */
    @Test
    fun `二次过滤不再变化`() {
        val once = filter("他极其平静，嘴角勾起一抹弧度，不容置疑。下一句在。")
        assertEquals("下一句在。", once.text)
        val twice = filter(once.text)
        assertEquals(once.text, twice.text)
        assertTrue("第二次不该再删东西", twice.removed.isEmpty())
    }

    /** 收尾清理：删完不留「两个句号」之外的怪标点（真值表 L 行：`他说完了。，极其。` → `他说完了。。`）。 */
    @Test
    fun `删完不留孤立逗号`() {
        val out = filter("他说完了。，极其。")
        assertEquals("他说完了。。", out.text)
        assertFalse("不该出现行首逗号", Regex("^[ \\t]*[，,；;]", RegexOption.MULTILINE).containsMatchIn(out.text))
        assertFalse("不该出现连续逗号", out.text.contains("，，"))
        assertFalse("不该出现三连空行", out.text.contains("\n\n\n"))
    }

    /** `normalizeStyleFilterHit` 的逐条（去标点、去加粗标记）。 */
    @Test
    fun `命中片段会被归一`() {
        assertEquals("不容置疑", StyleFilter.normalizeHit("，不容置疑"))
        assertEquals("极其", StyleFilter.normalizeHit("**极其**"))
        assertEquals("一抹弧度", StyleFilter.normalizeHit(" ,**一抹弧度** "))
        assertEquals(null, StyleFilter.normalizeHit("   "))
        assertEquals(null, StyleFilter.normalizeHit("，"))
    }

    /** 独立渲染内容的判据本身（调用方与将来复用者都会问「什么算」）。 */
    @Test
    fun `独立渲染内容判据`() {
        assertTrue(StyleFilter.isStandaloneRenderedContent("<div>x</div>"))
        assertTrue(StyleFilter.isStandaloneRenderedContent("  \n<section>x</section>"))
        assertTrue(StyleFilter.isStandaloneRenderedContent("<!-- c --><p>x</p>"))
        assertFalse(StyleFilter.isStandaloneRenderedContent("这是正文，中间有个 <div>"))
        assertFalse(StyleFilter.isStandaloneRenderedContent("「对白」开始"))
    }

    /**
     * **成本剖面**（不是性能门禁）：规则表是 5 条含回溯的正则，长正文上跑一遍的成本
     * 要能被看见——真机掉帧排查时第一现场就是这里。
     */
    @Test
    fun `过滤成本的量级`() {
        val body = "他极其平静地说完了这句话，然后转身离开，没有回头。".repeat(80)
        val cold = kotlin.system.measureNanoTime { repeat(50) { StyleFilter.filter(body) } } / 50
        println("PERF StyleFilter=${cold / 1000.0}µs (text=${body.length}字)")

        assertTrue("单次超过 50ms 说明数量级变坏了，实际 ${cold / 1000.0}µs", cold < 50_000_000)
    }
}

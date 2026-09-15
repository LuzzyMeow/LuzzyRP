package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆内容的浏览与改写（记忆页「记忆内容」段的纯逻辑）。
 *
 * 这一层出错的方式**全都不报错**：改错一条、删掉一条兄弟、`enabled` 只写了一半——
 * 界面上看起来都「成功」了，只有下次读库才发现数据被改坏。所以判据要卡在字节上：
 * 未命中的条目必须**逐字未变**。
 */
class MemoryBrowserTest {

    private fun vector(turn: Int, text: String, id: String? = null, enabled: Boolean? = null) =
        buildJsonObject {
            if (id != null) put("id", id)
            put("turn", turn)
            put("paragraph", text)
            put("embeddingDims", 1024)
            put("embeddingModel", "text-embedding-3-small")
            if (enabled != null) put("enabled", enabled)
        }

    private fun classic(turn: Int, summary: String, id: String? = null) = buildJsonObject {
        if (id != null) put("id", id)
        put("turn", turn)
        put("summary", summary)
    }

    @Test
    fun `投影读出正文与元信息`() {
        val items = MemoryBrowser.items(listOf(vector(3, "钟楼顶上的红苹果树", id = "a")), MemoryBrowser.VECTOR)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("a", item.id)
        assertEquals(3, item.turn)
        assertEquals("第 3 轮", item.turnLabel)
        assertEquals("钟楼顶上的红苹果树", item.text)
        assertTrue(item.hasEmbedding)
        assertEquals("text-embedding-3-small · 1024 维", item.metaLabel)
        assertTrue(item.enabled)
    }

    @Test
    fun `缺 id 时按下标合成身份（与 store 的规则一致）`() {
        val items = MemoryBrowser.items(listOf(vector(1, "甲"), vector(2, "乙")), MemoryBrowser.VECTOR)
        assertEquals(listOf("vector-0", "vector-1"), items.map { it.id })
    }

    @Test
    fun `排序按轮次升序、同轮按原下标`() {
        val raw = listOf(vector(5, "第五轮", id = "e"), vector(1, "第一轮", id = "a"), vector(1, "第一轮之二", id = "b"))
        val items = MemoryBrowser.items(raw, MemoryBrowser.VECTOR)
        assertEquals(listOf("a", "b", "e"), items.map { it.id })
        // 解析失败的条目被跳过，且**不占用** id 序号（坏数据不许让好数据的身份漂移）
        val mixed = listOf(JsonPrimitive("坏数据"), vector(2, "正常", id = "ok"))
        assertEquals(listOf("ok"), MemoryBrowser.items(mixed, MemoryBrowser.VECTOR).map { it.id })
    }

    @Test
    fun `向量形态优先 paragraph、缺了回落 summary`() {
        val p = MemoryBrowser.items(listOf(vector(1, "段落正文", id = "a")), MemoryBrowser.VECTOR).first()
        assertEquals("段落正文", p.text)
        val onlySummary = MemoryBrowser.items(listOf(classic(1, "只有摘要", id = "b")), MemoryBrowser.VECTOR).first()
        assertEquals("只有摘要", onlySummary.text)
    }

    @Test
    fun `启停只改目标那一条，兄弟逐字未变`() {
        val a = vector(1, "甲", id = "a")
        val b = vector(2, "乙", id = "b")
        val out = MemoryBrowser.setEnabled(listOf(a, b), MemoryBrowser.VECTOR, "b", false)
        assertEquals(a.toString(), out[0].toString())
        assertEquals("false", ((out[1] as JsonObject)["enabled"] as JsonPrimitive).content)
        // 再读回来：状态确实变了（这正是「开关弹回去」缺陷的判据）
        val reread = MemoryBrowser.items(out, MemoryBrowser.VECTOR).first { it.id == "b" }
        assertFalse(reread.enabled)
    }

    @Test
    fun `改写正文写回原本承载正文的那个键`() {
        // 有 paragraph：写 paragraph
        val withParagraph = MemoryBrowser.setText(
            listOf(vector(1, "旧", id = "a")), MemoryBrowser.VECTOR, "a", "新",
        )
        assertEquals("新", ((withParagraph[0] as JsonObject)["paragraph"] as JsonPrimitive).content)

        // 只有 summary 的老分片：写 summary（写错键 = 编辑后看不到变化）
        val onlySummary = MemoryBrowser.setText(
            listOf(classic(1, "旧摘要", id = "b")), MemoryBrowser.VECTOR, "b", "新摘要",
        )
        assertEquals("新摘要", ((onlySummary[0] as JsonObject)["summary"] as JsonPrimitive).content)
        assertEquals("新摘要", MemoryBrowser.items(onlySummary, MemoryBrowser.VECTOR).first().text)

        // 总结形态恒写 summary
        val classicOut = MemoryBrowser.setText(
            listOf(classic(2, "旧", id = "c")), MemoryBrowser.CLASSIC, "c", "新",
        )
        assertEquals("新", ((classicOut[0] as JsonObject)["summary"] as JsonPrimitive).content)
    }

    @Test
    fun `改写时其余键逐字保留（不许顺手规范化）`() {
        val raw = buildJsonObject {
            put("id", "a")
            put("turn", 7)
            put("paragraph", "旧")
            put("embeddingDims", 1024)
            put("customField", "别动我")
        }
        val out = MemoryBrowser.setText(listOf(raw), MemoryBrowser.VECTOR, "a", "新")
        val obj = out[0] as JsonObject
        assertEquals("别动我", (obj["customField"] as JsonPrimitive).content)
        assertEquals(1024, (obj["embeddingDims"] as JsonPrimitive).content.toInt())
        assertEquals(7, (obj["turn"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `删除只删目标，其余保持原顺序`() {
        val raw = listOf(vector(1, "甲", id = "a"), vector(2, "乙", id = "b"), vector(3, "丙", id = "c"))
        val out = MemoryBrowser.remove(raw, MemoryBrowser.VECTOR, "b")
        assertEquals(2, out.size)
        assertEquals(raw[0].toString(), out[0].toString())
        assertEquals(raw[2].toString(), out[1].toString())
    }

    @Test
    fun `删除对缺 id 的条目同样准确（按合成身份定位）`() {
        val raw = listOf(vector(1, "甲"), vector(2, "乙"), vector(3, "丙"))
        val out = MemoryBrowser.remove(raw, MemoryBrowser.VECTOR, "vector-1")
        assertEquals(listOf("甲", "丙"), MemoryBrowser.items(out, MemoryBrowser.VECTOR).map { it.text })
    }

    @Test
    fun `改不存在的 id 时原样返回（不做半套改动）`() {
        val raw = listOf(vector(1, "甲", id = "a"))
        assertEquals(raw.toString(), MemoryBrowser.setEnabled(raw, MemoryBrowser.VECTOR, "不存在", false).toString())
        assertEquals(raw.toString(), MemoryBrowser.setText(raw, MemoryBrowser.VECTOR, "不存在", "x").toString())
        assertEquals(raw.toString(), MemoryBrowser.remove(raw, MemoryBrowser.VECTOR, "不存在").toString())
    }

    @Test
    fun `预览按字符数截断并压平空白`() {
        val long = vector(1, "甲".repeat(200) + "\n\n乙", id = "a")
        val item = MemoryBrowser.items(listOf(long), MemoryBrowser.VECTOR).first()
        assertTrue(item.preview(10).endsWith("…"))
        assertEquals(11, item.preview(10).length)
        assertFalse(item.preview().contains("\n"))
    }

    @Test
    fun `空分组与全是坏数据都给空列表`() {
        assertTrue(MemoryBrowser.items(emptyList(), MemoryBrowser.VECTOR).isEmpty())
        assertTrue(MemoryBrowser.items(listOf(JsonPrimitive(1), JsonPrimitive("x")), MemoryBrowser.VECTOR).isEmpty())
    }
}

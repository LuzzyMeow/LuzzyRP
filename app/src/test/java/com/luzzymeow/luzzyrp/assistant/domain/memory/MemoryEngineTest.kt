package com.luzzymeow.luzzyrp.assistant.domain.memory

import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [VectorMath] / [Retriever] 单测（PLAN §7.2：纯 Kotlin 余弦，零 NDK）。 */
class MemoryEngineTest {

    @Test
    fun `float32 小端编码解码往返一致`() {
        val v = floatArrayOf(1.5f, -2.25f, 0f, 3.125f)
        val bytes = VectorMath.encode(v)
        assertEquals(v.size * 4, bytes.size)
        val back = VectorMath.decode(bytes)
        assertTrue(back!!.contentEquals(v))
    }

    @Test
    fun `解码脏数据返回 null 而不抛异常`() {
        assertNull(VectorMath.decode(null))
        assertNull(VectorMath.decode(ByteArray(0)))
        assertNull(VectorMath.decode(ByteArray(3)))
    }

    @Test
    fun `余弦相似度基本性质`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val c = floatArrayOf(0f, 1f, 0f)
        assertEquals(1f, VectorMath.cosine(a, b), 1e-6f)
        assertEquals(0f, VectorMath.cosine(a, c), 1e-6f)
        assertEquals(-1f, VectorMath.cosine(a, floatArrayOf(-1f, 0f, 0f)), 1e-6f)
    }

    @Test
    fun `余弦对零向量与维度不一致返回 0`() {
        assertEquals(0f, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 1e-6f)
    }

    private fun candidates(): List<MemoryCandidate> = listOf(
        MemoryCandidate("m1", "喜欢三段式周报", "preference", 300L, floatArrayOf(1f, 0f, 0f)),
        MemoryCandidate("m2", "设计评审改到周三", "fact", 200L, floatArrayOf(0f, 1f, 0f)),
        MemoryCandidate("m3", "沙盒里 rg 更快", "note", 100L, floatArrayOf(0.9f, 0.1f, 0f)),
    )

    @Test
    fun `embed 模式按相似度排序并过滤阈值`() {
        val hits = Retriever(topK = 8, threshold = 0.35f)
            .recall(MemoryMode.EMBED, floatArrayOf(1f, 0f, 0f), candidates())
        assertEquals(listOf("m1", "m3"), hits.map { it.id })
        assertTrue(hits.all { it.similarity != null })
    }

    @Test
    fun `embed 模式下高阈值过滤掉低相似项`() {
        // m3 = (0.9, 0.1, 0) 与查询 (1,0,0) 的余弦 ≈ 0.9939 —— 阈值 0.995 应把它滤掉
        val hits = Retriever(threshold = 0.995f)
            .recall(MemoryMode.EMBED, floatArrayOf(1f, 0f, 0f), candidates())
        assertEquals(listOf("m1"), hits.map { it.id })
    }

    @Test
    fun `无查询向量时 embed 退化为全文（降级路径）`() {
        val hits = Retriever().recall(MemoryMode.EMBED, null, candidates())
        assertEquals(listOf("m1", "m2", "m3"), hits.map { it.id }) // 按时间倒序
        assertTrue(hits.all { it.similarity == null })
    }

    @Test
    fun `hybrid 模式合并向量命中与最近条目且去重`() {
        val hits = Retriever(topK = 8, threshold = 0.35f, recentCount = 2)
            .recall(MemoryMode.HYBRID, floatArrayOf(1f, 0f, 0f), candidates())
        // 向量命中 m1,m3 + 最近 2 条 m1,m2 → 去重后 m1,m3,m2
        assertEquals(listOf("m1", "m3", "m2"), hits.map { it.id })
        assertEquals(hits.map { it.id }.distinct().size, hits.size)
    }

    @Test
    fun `full 模式按时间倒序且无相似度`() {
        val hits = Retriever(topK = 2).recall(MemoryMode.FULL, floatArrayOf(1f, 0f, 0f), candidates())
        assertEquals(listOf("m1", "m2"), hits.map { it.id })
        assertTrue(hits.all { it.similarity == null })
    }

    @Test
    fun `向量为空的候选在 embed 模式下被跳过`() {
        val mixed = candidates() + MemoryCandidate("m4", "无向量", "note", 400L, null)
        val hits = Retriever(threshold = 0.0f).recall(MemoryMode.EMBED, floatArrayOf(1f, 0f, 0f), mixed)
        assertTrue(hits.none { it.id == "m4" })
    }

    @Test
    fun `记忆块渲染空召回返回空串`() {
        assertEquals("", MemoryBlockFormatter.format(emptyList()))
        val block = MemoryBlockFormatter.format(
            listOf(RecalledMemory("m1", "喜欢三段式周报", "preference", 0.88f))
        )
        assertTrue(block.startsWith("## 长期记忆"))
        assertTrue(block.contains("0.88"))
    }

    @Test
    fun `未知模式归一为 full`() {
        assertEquals(MemoryMode.FULL, MemoryMode.fromId(null))
        assertEquals(MemoryMode.FULL, MemoryMode.fromId("nonsense"))
        assertEquals(MemoryMode.EMBED, MemoryMode.fromId("embed"))
        assertEquals(MemoryMode.HYBRID, MemoryMode.fromId("hybrid"))
    }
}

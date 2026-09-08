package com.luzzymeow.luzzyrp.assistant.domain.memory

import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode

/**
 * 记忆召回（PLAN §7.1/§7.2）。
 *
 * 三种模式（`full` / `embed` / `hybrid`）与阈值/TopK 语义见 [com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode]：
 * - `full`：无嵌入模型 → 记忆全文注入（按 token 预算裁剪，最近优先）；
 * - `embed`：有嵌入模型 → 向量召回 Top-K（默认 8）+ 阈值（默认 0.35）；
 * - `hybrid`：向量召回 Top-K + 最近 N 条事实（默认 5，去重）。
 *
 * **自动降级**：`embed`/`hybrid` 调用嵌入失败 → 本轮退化为 `full` 并记日志 + UI 提示。
 */

/** 待召回的候选（由数据层映射而来）。 */
data class MemoryCandidate(
    val id: String,
    val content: String,
    val type: String,
    val createdAtMillis: Long,
    val embedding: FloatArray?,
)

/** 召回结果。 */
data class RecalledMemory(
    val id: String,
    val content: String,
    val type: String,
    val similarity: Float?,
)

/** 纯 Kotlin 检索器（无 Android 依赖，可单测）。 */
class Retriever(
    val topK: Int = DEFAULT_TOP_K,
    val threshold: Float = DEFAULT_THRESHOLD,
    val recentCount: Int = DEFAULT_RECENT,
) {

    /** 以新的 topK 派生一个检索器（其余参数保持）。 */
    fun withTopK(topK: Int): Retriever =
        if (topK == this.topK) this else Retriever(topK, threshold, recentCount)

    /**
     * @param queryEmbedding 用户输入的嵌入；为 null 表示无法向量检索（退化为全文）
     */
    fun recall(
        mode: MemoryMode,
        queryEmbedding: FloatArray?,
        candidates: List<MemoryCandidate>,
    ): List<RecalledMemory> = when (mode) {
        MemoryMode.EMBED ->
            if (queryEmbedding == null) full(candidates) else vector(queryEmbedding, candidates)

        MemoryMode.HYBRID -> {
            val vectorHits = if (queryEmbedding == null) emptyList() else vector(queryEmbedding, candidates)
            mergeDistinct(vectorHits, recent(candidates, recentCount))
        }

        else -> full(candidates)
    }

    /** 全文模式：按时间倒序取到预算为止。 */
    fun full(candidates: List<MemoryCandidate>, limit: Int = topK): List<RecalledMemory> =
        candidates.sortedByDescending { it.createdAtMillis }
            .take(limit)
            .map { RecalledMemory(it.id, it.content, it.type, null) }

    /** 向量模式：余弦排序 + 阈值过滤。 */
    fun vector(queryEmbedding: FloatArray, candidates: List<MemoryCandidate>): List<RecalledMemory> =
        candidates.asSequence()
            .mapNotNull { candidate ->
                val vec = candidate.embedding ?: return@mapNotNull null
                val sim = VectorMath.cosine(queryEmbedding, vec)
                if (sim < threshold) null
                else RecalledMemory(candidate.id, candidate.content, candidate.type, sim)
            }
            .sortedByDescending { it.similarity ?: 0f }
            .take(topK)
            .toList()

    /** 最近 N 条（hybrid 的补充项）。 */
    fun recent(candidates: List<MemoryCandidate>, count: Int = recentCount): List<RecalledMemory> =
        candidates.sortedByDescending { it.createdAtMillis }
            .take(count)
            .map { RecalledMemory(it.id, it.content, it.type, null) }

    private fun mergeDistinct(
        primary: List<RecalledMemory>,
        secondary: List<RecalledMemory>,
    ): List<RecalledMemory> {
        val seen = primary.mapTo(mutableSetOf()) { it.id }
        return primary + secondary.filter { seen.add(it.id) }
    }

    companion object {
        const val DEFAULT_TOP_K = 8
        const val DEFAULT_THRESHOLD = 0.35f
        const val DEFAULT_RECENT = 5
    }
}

/**
 * 记忆块渲染（进系统提示词）。
 *
 * 空召回返回空串（不注入空标题）。条目带类型与相似度，便于模型判断可信度。
 */
object MemoryBlockFormatter {

    fun format(hits: List<RecalledMemory>): String {
        if (hits.isEmpty()) return ""
        val body = hits.joinToString("\n") { hit ->
            val sim = hit.similarity?.let { "（相似度 ${"%.2f".format(it)}）" } ?: ""
            "- [${hit.type}] ${hit.content}$sim"
        }
        return "## 长期记忆\n$body"
    }
}

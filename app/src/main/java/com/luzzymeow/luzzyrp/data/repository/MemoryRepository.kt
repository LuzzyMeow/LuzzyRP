package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.model.MemoryItem
import com.luzzymeow.luzzyrp.core.model.SummaryItem
import com.luzzymeow.luzzyrp.core.model.SummaryLevel
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.db.dao.MemoryDao
import com.luzzymeow.luzzyrp.data.db.dao.SummaryDao
import com.luzzymeow.luzzyrp.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.serializer
import java.util.UUID
import kotlin.math.sqrt

/**
 * ACE 长期记忆仓库 + 三级摘要仓库。
 *
 * [INVARIANT-ACE] ACE 三步循环（Execute → Reflect → Update）的数据面：
 *   - Execute：topK(query) 检索 + 命中刷新 lastRetrievedAt；
 *   - Reflect：recordFeedback 更新 helpful/harmful/neutral 计数；
 *   - Update：upsertWithDedup 嵌入去重（余弦 ≥ 阈值 → 合并计数、更新内容）+ 容量淘汰。
 */

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val vectorIndex: VectorIndex,
    private val settingsStore: SettingsStore,
) {

    fun observeAll(): Flow<List<MemoryItem>> = memoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<MemoryItem>> = memoryDao.observeActive().map { list -> list.map { it.toDomain() } }

    /** 一次性获取激活记忆内容（ACE Update 去重用）。 */
    suspend fun getActiveContents(): List<String> = memoryDao.getActive().map { it.content }

    /**
     * Execute 步骤：向量 Top-K 检索（嵌入供应商未配置时退化为全量激活条目）。
     * 命中条目刷新 lastRetrievedAt（淘汰算法的失活依据）。
     */
    suspend fun recall(query: String, queryEmbedding: FloatArray?, topK: Int): List<MemoryItem> {
        val active = memoryDao.getActive().map { it.toDomain() }
        if (active.isEmpty()) return emptyList()

        val ranked: List<MemoryItem> = if (queryEmbedding != null && queryEmbedding.isNotEmpty()) {
            val hits = vectorIndex.search("memory_vec", "memory_id", queryEmbedding, topK)
            val byId = active.associateBy { it.id }
            hits.mapNotNull { (id, distance) -> byId[id]?.copy() }.ifEmpty {
                // 向量未命中（索引空）→ 退化为关键词包含匹配
                keywordFallback(active, query).take(topK)
            }
        } else {
            keywordFallback(active, query).take(topK)
        }

        // 刷新检索时间
        val now = System.currentTimeMillis()
        ranked.forEach { item ->
            memoryDao.getById(item.id)?.let { memoryDao.upsert(it.copy(lastRetrievedAt = now)) }
        }
        return ranked
    }

    /** Update 步骤：嵌入去重入库（余弦 ≥ 阈值 → 更新既有条目而非新增）。 */
    suspend fun upsertWithDedup(
        content: String,
        category: String,
        embedding: FloatArray?,
        sourceConversationId: String?,
        threshold: Float,
    ): MemoryItem {
        val now = System.currentTimeMillis()
        if (embedding != null && embedding.isNotEmpty()) {
            // Top-K=1 最近邻 + 冗余嵌入的真实余弦判定（阈值默认 0.92）
            val nearest = vectorIndex.search("memory_vec", "memory_id", embedding, 1).firstOrNull()?.first
            val existing = nearest?.let { memoryDao.getById(it) }
            val existingVec = existing?.embeddingJson?.let { decodeVector(it) }
            if (existing != null && existingVec != null &&
                cosineSimilarity(existingVec, embedding) >= threshold
            ) {
                // 去重合并：保留更长内容，重复确认视为一致性信号
                val merged = existing.copy(
                    content = if (content.length > existing.content.length) content else existing.content,
                    updatedAt = now,
                )
                memoryDao.upsert(merged)
                return merged.toDomain()
            }
        }

        val item = MemoryItem(
            id = UUID.randomUUID().toString(),
            content = content,
            category = category,
            sourceConversationId = sourceConversationId,
            createdAt = now,
            updatedAt = now,
            lastRetrievedAt = now,
        )
        val entity = item.toEntity().copy(
            embeddingJson = embedding?.let { encodeVector(it) },
        )
        memoryDao.upsert(entity)
        embedding?.let { vectorIndex.upsert("memory_vec", "memory_id", item.id, it) }
        enforceCapacity()
        return item
    }

    /** Reflect 步骤：记录反馈计数。 */
    suspend fun recordFeedback(id: String, feedback: Feedback) {
        val existing = memoryDao.getById(id) ?: return
        memoryDao.upsert(
            when (feedback) {
                Feedback.HELPFUL -> existing.copy(helpful = existing.helpful + 1, updatedAt = System.currentTimeMillis())
                Feedback.HARMFUL -> existing.copy(harmful = existing.harmful + 1, updatedAt = System.currentTimeMillis())
                Feedback.NEUTRAL -> existing.copy(neutral = existing.neutral + 1, updatedAt = System.currentTimeMillis())
            }
        )
    }

    suspend fun setActive(id: String, active: Boolean) {
        memoryDao.getById(id)?.let { memoryDao.upsert(it.copy(active = active, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun updateContent(id: String, content: String) {
        memoryDao.getById(id)?.let {
            memoryDao.upsert(it.copy(content = content, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun delete(id: String) {
        memoryDao.delete(id)
        vectorIndex.delete("memory_vec", "memory_id", id)
    }

    /** 容量淘汰：超出上限时按评分淘汰最弱条目（删除而非停用，腾出向量空间）。 */
    private suspend fun enforceCapacity() {
        val capacity = settingsStore.settingsFlow.first().memoryCapacity
        var count = memoryDao.count()
        while (count > capacity) {
            val weakest = memoryDao.getWeakest(count - capacity)
            if (weakest.isEmpty()) break
            weakest.forEach { delete(it.id) }
            count = memoryDao.count()
        }
    }

    private fun keywordFallback(items: List<MemoryItem>, query: String): List<MemoryItem> {
        if (query.isBlank()) return items
        val tokens = query.split(Regex("[\\s，。,.!?？、；;：:]+")).filter { it.isNotBlank() }
        return items.sortedByDescending { item -> tokens.count { item.content.contains(it) } }
    }

    private fun MemoryEntity.toDomain(): MemoryItem = MemoryItem(
        id = id, content = content, category = category, helpful = helpful,
        harmful = harmful, neutral = neutral, active = active,
        sourceConversationId = sourceConversationId, createdAt = createdAt,
        updatedAt = updatedAt, lastRetrievedAt = lastRetrievedAt,
    )

    private fun MemoryItem.toEntity(): MemoryEntity = MemoryEntity(
        id = id, content = content, category = category, helpful = helpful,
        harmful = harmful, neutral = neutral, active = active,
        sourceConversationId = sourceConversationId, createdAt = createdAt,
        updatedAt = updatedAt, lastRetrievedAt = lastRetrievedAt,
    )

    enum class Feedback { HELPFUL, HARMFUL, NEUTRAL }
}

/** 三级摘要仓库（A 每轮 ≤50 / B 每 10 轮 ≤10 / C 每 50 轮永久）。 */
class SummaryRepository(
    private val summaryDao: SummaryDao,
    private val vectorIndex: VectorIndex,
) {

    suspend fun getByConversation(conversationId: String): List<SummaryItem> =
        summaryDao.getByConversation(conversationId).map { it.toDomain() }

    suspend fun getByLevel(conversationId: String, level: SummaryLevel): List<SummaryItem> =
        summaryDao.getByLevel(conversationId, level.name.lowercase()).map { it.toDomain() }

    suspend fun upsert(summary: SummaryItem, embedding: FloatArray? = null) {
        summaryDao.upsert(summary.toEntity())
        embedding?.let {
            vectorIndex.upsert("summary_vec", "summary_id", summary.id, it)
        }
    }

    suspend fun deleteByRounds(conversationId: String, level: SummaryLevel, rounds: List<Int>) {
        summaryDao.deleteByRounds(conversationId, level.name.lowercase(), rounds)
    }

    companion object {
        /** A 级摘要字数上限。 */
        const val A_MAX_CHARS = 50
        /** B 级条数上限（每次合并产出）。 */
        const val B_MAX_ITEMS = 10
        /** B 级合并周期（轮）。 */
        const val B_INTERVAL_ROUNDS = 10
        /** C 级固化周期（轮）。 */
        const val C_INTERVAL_ROUNDS = 50
    }
}

private fun encodeVector(vector: FloatArray): String =
    vector.joinToString(prefix = "[", postfix = "]") { f ->
        if (f.isNaN() || f.isInfinite()) "0" else f.toString()
    }

private fun decodeVector(json: String): FloatArray? = runCatching {
    kotlinx.serialization.json.Json.parseToJsonElement(json)
        .let { it as kotlinx.serialization.json.JsonArray }
        .map { it.jsonPrimitive.content.toFloat() }
        .toFloatArray()
}.getOrNull()

private val kotlinx.serialization.json.JsonElement.jsonPrimitive: kotlinx.serialization.json.JsonPrimitive
    get() = this as kotlinx.serialization.json.JsonPrimitive

/** 余弦相似度（嵌入去重用）。 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || a.size != b.size) return 0f
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom <= 0f) 0f else dot / denom
}

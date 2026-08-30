package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.model.InjectionPosition
import com.luzzymeow.luzzyrp.core.model.RecallStrategy
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.Worldbook
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import com.luzzymeow.luzzyrp.data.db.dao.WorldbookDao
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.random.Random

/**
 * 世界书仓库：CRUD + 三策略召回（常驻 / 关键词 / 向量）。
 *
 * [INVARIANT-KV] 注入分层：BEFORE_CHAR / AFTER_CHAR 进入系统提示稳定前缀，
 * AT_DEPTH 进入动态尾段——条目的 position 决定其 KV 层级，组装器按此分层。
 */
class WorldbookRepository(
    private val worldbookDao: WorldbookDao,
    private val vectorIndex: VectorIndex,
) {

    fun observeAll(): Flow<List<Worldbook>> = worldbookDao.observeAll().map { list ->
        list.map { book -> book.toDomain(worldbookDao.getEntries(book.id).map { it.toDomain() }) }
    }

    suspend fun getById(id: String): Worldbook? {
        val book = worldbookDao.getById(id) ?: return null
        return book.toDomain(worldbookDao.getEntries(id).map { it.toDomain() })
    }

    suspend fun save(book: Worldbook) {
        worldbookDao.upsert(
            WorldbookEntity(
                id = book.id, name = book.name, enabled = book.enabled, cardId = book.cardId,
                createdAt = book.createdAt, updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun saveEntry(entry: WorldbookEntry) {
        worldbookDao.upsertEntry(entry.toEntity())
    }

    suspend fun deleteEntry(entryId: String) {
        worldbookDao.deleteEntry(entryId)
        vectorIndex.delete("worldbook_vec", "entry_id", entryId)
    }

    /** 为条目写入向量索引。 */
    suspend fun indexEntry(entry: WorldbookEntry, embedding: FloatArray) {
        vectorIndex.upsert("worldbook_vec", "entry_id", entry.id, embedding)
        worldbookDao.setVectorIndexed(entry.id, true)
    }

    /**
     * 三策略召回。
     *
     * @param conversationMessages 会话消息序列（供关键词扫描）
     * @param queryEmbedding 最近上下文的嵌入（向量策略；null = 跳过向量召回）
     * @param topK 向量召回条数上限
     * @return 命中条目按 position/order 排序
     */
    suspend fun recall(
        bookIds: List<String>,
        conversationMessages: List<UIMessage>,
        queryEmbedding: FloatArray?,
        topK: Int = 6,
    ): RecallResult {
        val books = bookIds.mapNotNull { getById(it) }.filter { it.enabled }
        if (books.isEmpty()) return RecallResult(emptyList(), emptyList())

        val scanText = conversationMessages.takeLast(8).joinToString("\n") { it.textContent() }
        val alreadyInjected = mutableListOf<String>()

        fun rollPass(entry: WorldbookEntry): Boolean = when {
            !entry.enabled -> false
            entry.probability < 100 && Random.nextInt(100) >= entry.probability -> false
            else -> true
        }

        val constant = mutableListOf<WorldbookEntry>()
        val dynamic = mutableListOf<WorldbookEntry>()

        for (book in books) {
            val entries = book.entries.filter { rollPass(it) }

            // 1) 常驻策略
            entries.filter { RecallStrategy.CONSTANT in it.strategies }.forEach { entry ->
                (if (entry.position == InjectionPosition.AT_DEPTH) dynamic else constant).add(entry)
                alreadyInjected.add(entry.id)
            }

            // 2) 关键词策略（含一次递归扫描）
            var keywordHits = entries.filter { RecallStrategy.KEYWORD in it.strategies }
                .filter { matchesKeys(it, scanText) }
            // 递归：命中条目的 content 触发其他未命中条目（一层）
            val recursionHit = entries.filter { it.recursion && it !in keywordHits }
                .filter { entry -> keywordHits.any { hit -> hit.content.containsAny(keysOf(entry, scanText)) } }
            keywordHits = (keywordHits + recursionHit).distinctBy { it.id }
            keywordHits.forEach { entry ->
                (if (entry.position == InjectionPosition.AT_DEPTH) dynamic else constant).add(entry)
                alreadyInjected.add(entry.id)
            }

            // 3) 向量策略
            if (queryEmbedding != null && queryEmbedding.isNotEmpty()) {
                val vectorCandidates = entries.filter { RecallStrategy.VECTOR in it.strategies }
                if (vectorCandidates.isNotEmpty()) {
                    val hits = vectorIndex.search("worldbook_vec", "entry_id", queryEmbedding, topK)
                    val byId = vectorCandidates.associateBy { it.id }
                    hits.forEach { (id, distance) ->
                        val entry = byId[id] ?: return@forEach
                        // sqlite-vec L2 距离 → 相似度近似（单位向量下 cos ≈ 1 - d²/2）
                        val similarity = 1f - (distance * distance) / 2f
                        if (similarity >= entry.minSimilarity && entry.id !in alreadyInjected) {
                            (if (entry.position == InjectionPosition.AT_DEPTH) dynamic else constant).add(entry)
                            alreadyInjected.add(entry.id)
                        }
                    }
                }
            }
        }

        return RecallResult(
            stableEntries = constant.sortedBy { it.order }.distinctBy { it.id },
            dynamicEntries = dynamic.sortedBy { it.order }.distinctBy { it.id },
        )
    }

    private fun matchesKeys(entry: WorldbookEntry, text: String): Boolean {
        val target = if (entry.caseSensitive) text else text.lowercase()
        fun present(key: String): Boolean {
            val k = if (entry.caseSensitive) key else key.lowercase()
            return if (entry.useRegex) {
                runCatching { Regex(k).containsMatchIn(target) }.getOrDefault(false)
            } else {
                target.contains(k)
            }
        }
        if (entry.keys.isEmpty()) return false
        if (!entry.keys.any { present(it) }) return false
        // 次级关键词：声明了则必须同时命中
        if (entry.keysSecondary.isNotEmpty() && !entry.keysSecondary.all { present(it) }) return false
        return true
    }

    private fun keysOf(entry: WorldbookEntry, text: String): List<String> = entry.keys

    private fun String.containsAny(keys: List<String>): Boolean = keys.any { contains(it, ignoreCase = true) }

    data class RecallResult(
        /** 稳定前缀条目（BEFORE_CHAR / AFTER_CHAR）。 */
        val stableEntries: List<WorldbookEntry>,
        /** 动态尾段条目（AT_DEPTH）。 */
        val dynamicEntries: List<WorldbookEntry>,
    )
}

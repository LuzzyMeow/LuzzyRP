package com.luzzymeow.luzzyrp.assistant.runtime.memory

import com.luzzymeow.luzzyrp.assistant.data.db.dao.MemoryDao
import com.luzzymeow.luzzyrp.assistant.data.db.entity.MemoryEntity
import com.luzzymeow.luzzyrp.assistant.domain.memory.EmbeddingClient
import com.luzzymeow.luzzyrp.assistant.domain.memory.EmbeddingException
import com.luzzymeow.luzzyrp.assistant.domain.memory.MemoryCandidate
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.domain.memory.Retriever
import com.luzzymeow.luzzyrp.assistant.domain.memory.VectorMath
import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryHit
import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryStore
import java.util.UUID

/** 嵌入配置（来自助手设置 + Web 端供应商只读镜像；**含密钥，禁止落日志**）。 */
data class EmbeddingConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val modelRef: String,
)

/**
 * 记忆存取实现（PLAN §7）：Room + 纯 Kotlin 余弦检索。
 *
 * 三种模式的行为差异在 [search] 内体现：
 * - 未配置嵌入模型（[embeddingConfig] 返回 null）→ 全文按最近排序；
 * - 配置了但调用失败 → **自动降级全文**并记一条诊断（[onDegraded]），不阻断对话。
 */
class RoomMemoryStore(
    private val dao: MemoryDao,
    private val embeddingClient: EmbeddingClient = EmbeddingClient(),
    private val embeddingConfig: suspend (assistantId: String) -> EmbeddingConfig? = { null },
    private val retriever: Retriever = Retriever(),
    private val now: () -> Long = System::currentTimeMillis,
    private val onDegraded: (String) -> Unit = {},
) : MemoryStore {

    override suspend fun write(
        content: String,
        type: String,
        scope: String,
        assistantId: String,
        conversationId: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        val ts = now()
        val config = runCatching { embeddingConfig(assistantId) }.getOrNull()
        val embedded = config?.let { runCatching { embedSync(content, it) }.getOrNull() }
        dao.upsert(
            MemoryEntity(
                id = id,
                assistantId = assistantId,
                scope = if (scope == MemoryEntity.SCOPE_GLOBAL) MemoryEntity.SCOPE_GLOBAL else MemoryEntity.SCOPE_ASSISTANT,
                type = type.ifBlank { MemoryEntity.TYPE_NOTE },
                content = content,
                source = MemoryEntity.SOURCE_AGENT,
                conversationId = conversationId,
                embedding = embedded?.first?.let { VectorMath.encode(it) },
                embeddingModelRef = embedded?.second,
                dim = embedded?.first?.size ?: 0,
                createdAt = ts,
                updatedAt = ts,
                lastUsedAt = null,
            )
        )
        return id
    }

    override suspend fun search(query: String, assistantId: String, topK: Int): List<MemoryHit> {
        val candidates = dao.getVisibleTo(assistantId, CANDIDATE_LIMIT).map { it.toCandidate() }
        if (candidates.isEmpty()) return emptyList()

        val config = runCatching { embeddingConfig(assistantId) }.getOrNull()
        val queryVector = if (config != null) {
            runCatching { embedSync(query, config).first }
                .onFailure { onDegraded("嵌入失败，已降级为全文检索：${it.message ?: it.javaClass.simpleName}") }
                .getOrNull()
        } else null

        val mode = when {
            config == null -> MemoryMode.FULL
            queryVector == null -> MemoryMode.FULL
            else -> MemoryMode.EMBED
        }
        val hits = retriever.withTopK(topK).recall(mode, queryVector, candidates)
        if (hits.isNotEmpty()) {
            runCatching { dao.touchUsed(hits.map { it.id }, now()) }
            return hits.map { MemoryHit(it.id, it.content, it.type, MemoryEntity.SCOPE_ASSISTANT, 0L, it.similarity) }
        }
        // 向量/全文都没命中 → LIKE 兜底（中文单字等场景）
        return dao.searchByContent(assistantId, escapeLike(query), topK).map {
            MemoryHit(it.id, it.content, it.type, it.scope, it.createdAt, null)
        }
    }

    override suspend fun update(id: String, content: String): Boolean {
        val existing = dao.getById(id) ?: return false
        val config = runCatching { embeddingConfig(existing.assistantId) }.getOrNull()
        val embedded = config?.let { runCatching { embedSync(content, it) }.getOrNull() }
        dao.update(
            existing.copy(
                content = content,
                embedding = embedded?.first?.let { VectorMath.encode(it) } ?: existing.embedding,
                embeddingModelRef = embedded?.second ?: existing.embeddingModelRef,
                dim = embedded?.first?.size ?: existing.dim,
                updatedAt = now(),
            )
        )
        return true
    }

    override suspend fun delete(id: String): Boolean {
        if (dao.getById(id) == null) return false
        dao.deleteById(id)
        return true
    }

    override suspend fun list(assistantId: String, scope: String?, limit: Int): List<MemoryHit> {
        val rows = if (scope.isNullOrBlank()) dao.getByAssistant(assistantId)
        else dao.getByScope(assistantId, scope)
        return rows.take(limit).map { MemoryHit(it.id, it.content, it.type, it.scope, it.createdAt, null) }
    }

    /** 后台补嵌：把没有向量的可见记忆补上（写入时嵌入失败的回填路径）。 */
    suspend fun backfillEmbeddings(assistantId: String, limit: Int = 32): Int {
        val config = runCatching { embeddingConfig(assistantId) }.getOrNull() ?: return 0
        val pending = dao.getVisibleTo(assistantId, limit).filter { it.embedding == null || it.dim == 0 }
        if (pending.isEmpty()) return 0
        val vectors = runCatching { embeddingClient.embed(pending.map { it.content }, config.baseUrl, config.apiKey, config.model) }
            .getOrElse { return 0 }
        pending.zip(vectors).forEach { (entity, vector) ->
            runCatching {
                dao.updateEmbedding(
                    entity.id,
                    VectorMath.encode(vector),
                    config.modelRef,
                    vector.size,
                    now(),
                )
            }
        }
        return pending.size
    }

    private suspend fun embedSync(text: String, config: EmbeddingConfig): Pair<FloatArray, String> {
        val vector = embeddingClient.embedOne(text, config.baseUrl, config.apiKey, config.model)
        return vector to config.modelRef
    }

    private fun MemoryEntity.toCandidate() = MemoryCandidate(
        id = id,
        content = content,
        type = type,
        createdAtMillis = createdAt,
        embedding = VectorMath.decode(embedding),
    )

    private fun escapeLike(input: String): String = input
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    companion object {
        /** 候选池上限：先按最近取，再算余弦（万条量级 <10ms，PLAN §7.2）。 */
        const val CANDIDATE_LIMIT = 500
    }
}

package com.luzzymeow.luzzyrp.data.ai.tools

import com.luzzymeow.luzzyrp.core.model.MemoryItem
import com.luzzymeow.luzzyrp.core.model.Worldbook
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import com.luzzymeow.luzzyrp.data.repository.MemoryRepository
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository
import com.luzzymeow.luzzyrp.core.model.parsedInput
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 内置主动工具组（协议层注册，模型自主调用）。
 *
 * [INVARIANT-AGENTIC] 这里只放「主动工具」；被动工具（向量记忆召回/世界书召回）
 * 在 PromptAssembler 中预执行并注入系统提示（不变性 A6）。
 * 当前模型以文本标签协议调用（TagToolCallParser）或原生 function calling 均可。
 */
object BuiltinTools {

    fun create(
        worldbookRepository: WorldbookRepository,
        memoryRepository: MemoryRepository,
        bookIdsProvider: suspend () -> List<String>,
    ): ToolRegistry = ToolRegistry(
        listOf(
            worldKeywordSearch(worldbookRepository, bookIdsProvider),
            memorySearch(memoryRepository),
            currentTime(),
        )
    )

    /**
     * world_keyword_search：按关键词检索绑定的世界书条目（叙事查证）。
     * [INVARIANT-AGENTIC] 供模型在第二轮思考中主动调用，满足 >1 次主动调用约束。
     */
    private fun worldKeywordSearch(
        repository: WorldbookRepository,
        bookIdsProvider: suspend () -> List<String>,
    ) = ToolDef(
        name = "world_keyword_search",
        description = "按关键词检索当前世界书（世界观/地点/人物/物品设定）。叙事中需要查证设定细节时调用。",
        parametersJson = """
            {"type":"object","properties":{
                "keywords":{"type":"array","items":{"type":"string"},"description":"1-5 个具体关键词（如：酒馆、第二次降临、银币）"}
            },"required":["keywords"]}
        """.trimIndent(),
        systemPrompt = """
            - world_keyword_search：按关键词检索世界书设定。参数 keywords 为具体名词数组。
              叙事涉及地点、组织、历史事件、物品时必须先检索再落笔，禁止编造设定。
        """.trimIndent(),
    ) { args ->
        val keywords = args.keywords().take(5)
        if (keywords.isEmpty()) return@ToolDef ToolOutputs.error("keywords 不能为空")
        val bookIds = bookIdsProvider()
        val entries = mutableListOf<WorldbookEntry>()
        for (id in bookIds) {
            val book = repository.getById(id) ?: continue
            entries += book.entries.filter { it.enabled }
        }
        // 简单包含匹配（世界书主动检索为关键词工具；向量召回属被动层）
        val scored = entries.map { entry ->
            val text = entry.comment + "\n" + entry.content
            text to keywords.count { text.contains(it, ignoreCase = true) }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        if (scored.isEmpty()) {
            ToolOutputs.text("""{"results": [], "note": "未命中任何世界书条目"}""")
        } else {
            val payload = buildJsonObject {
                put("results", JsonArray(scored.map { (text, _) ->
                    buildJsonObject { put("content", JsonPrimitive(text.trim())) }
                }))
            }
            ToolOutputs.text(payload.toString())
        }
    }

    /** memory_search：按关键词检索长期记忆（回顾往事）。 */
    private fun memorySearch(repository: MemoryRepository) = ToolDef(
        name = "memory_search",
        description = "按关键词检索长期记忆（过往事件、承诺、人物关系）。需要回顾往事细节时调用。",
        parametersJson = """
            {"type":"object","properties":{
                "query":{"type":"string","description":"检索词（具体的人名/事件/物品）"}
            },"required":["query"]}
        """.trimIndent(),
        systemPrompt = """
            - memory_search：按关键词检索长期记忆。参数 query 为检索词。
              提及过去的约定、事件、人物关系时先检索，保证前后一致。
        """.trimIndent(),
    ) { args ->
        val query = args.str("query") ?: return@ToolDef ToolOutputs.error("query 不能为空")
        val items = repository.recall(query, null, topK = 6)
        val payload = buildJsonObject {
            put("results", JsonArray(items.map { buildJsonObject { put("content", JsonPrimitive(it.content)) } }))
        }
        ToolOutputs.text(payload.toString())
    }

    /** current_time：当前真实时间（时间线锚定）。 */
    private fun currentTime() = ToolDef(
        name = "current_time",
        description = "获取当前真实日期时间（故事内时间锚定用）。",
        parametersJson = """{"type":"object","properties":{}}""",
        systemPrompt = "- current_time：获取当前真实日期与星期，无参数。",
    ) { _ ->
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        ToolOutputs.text(now.toString())
    }

    // —— 参数提取辅助 ——

    private fun JsonElement.str(key: String): String? =
        (this as? kotlinx.serialization.json.JsonObject)?.get(key) ?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content

    private fun JsonElement.keywords(): List<String> =
        (this as? kotlinx.serialization.json.JsonObject)?.get("keywords")
            ?.let { it as? JsonArray }?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()
}

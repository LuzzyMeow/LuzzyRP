package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryHit
import com.luzzymeow.luzzyrp.assistant.domain.tool.MemoryStore
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 记忆工具（PLAN §7.3）：`memory_write` / `memory_search` / `memory_update` / `memory_delete` / `memory_list`。
 *
 * 记忆按**助手**隔离（PLAN §7.4：与 RP-Hub 的角色维度记忆完全独立）。
 * 有嵌入模型时写入同步生成向量；失败则先落盘、后台补嵌（由 [MemoryStore] 实现负责）。
 */
class MemoryWriteTool(private val store: MemoryStore) : Tool {
    override val name = "memory_write"
    override val description =
        "写入一条长期记忆。当用户透露偏好、事实、待办，或你判断该信息后续会用到时调用。" +
            "内容要自足（不依赖上下文也能读懂）。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "content" to Schema.string("记忆内容，一句话说清"),
            "type" to Schema.string("类型", enumValues = listOf("fact", "preference", "task", "note")),
            "scope" to Schema.string("作用域", enumValues = listOf("assistant", "global")),
        ),
        required = listOf("content"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val content = args.str("content") ?: return@runCatching ToolResult.Error("缺少 content 参数")
        val type = args.str("type") ?: "note"
        val scope = args.str("scope") ?: "assistant"
        val id = store.write(content, type, scope, ctx.assistantId, ctx.conversationId)
        ToolResult.Ok("已记住（id=$id，type=$type，scope=$scope）")
    }.getOrElse { ToolResult.Error(it.message ?: "写入记忆失败") }
}

class MemorySearchTool(private val store: MemoryStore) : Tool {
    override val name = "memory_search"
    override val description = "检索长期记忆。回答涉及用户历史偏好/事实的问题前先查一次。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "query" to Schema.string("检索词"),
            "topK" to Schema.integer("返回条数，默认 8"),
        ),
        required = listOf("query"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val query = args.str("query") ?: return@runCatching ToolResult.Error("缺少 query 参数")
        val topK = args["topK"]?.jsonPrimitive?.intOrNull ?: 8
        val hits = store.search(query, ctx.assistantId, topK)
        if (hits.isEmpty()) return@runCatching ToolResult.Ok("（没有匹配的记忆）")
        ToolResult.Ok(hits.joinToString("\n") { formatHit(it) })
    }.getOrElse { ToolResult.Error(it.message ?: "检索记忆失败") }
}

class MemoryUpdateTool(private val store: MemoryStore) : Tool {
    override val name = "memory_update"
    override val description = "更新一条记忆的内容（会重算向量）。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "id" to Schema.string("记忆 id"),
            "content" to Schema.string("新内容"),
        ),
        required = listOf("id", "content"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val id = args.str("id") ?: return@runCatching ToolResult.Error("缺少 id 参数")
        val content = args.str("content") ?: return@runCatching ToolResult.Error("缺少 content 参数")
        if (store.update(id, content)) ToolResult.Ok("已更新 $id") else ToolResult.Error("未找到记忆 $id")
    }.getOrElse { ToolResult.Error(it.message ?: "更新记忆失败") }
}

class MemoryDeleteTool(private val store: MemoryStore) : Tool {
    override val name = "memory_delete"
    override val description = "删除一条记忆（需用户审批）。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("id" to Schema.string("记忆 id")),
        required = listOf("id"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val id = args.str("id") ?: return@runCatching ToolResult.Error("缺少 id 参数")
        if (store.delete(id)) ToolResult.Ok("已删除 $id") else ToolResult.Error("未找到记忆 $id")
    }.getOrElse { ToolResult.Error(it.message ?: "删除记忆失败") }
}

class MemoryListTool(private val store: MemoryStore) : Tool {
    override val name = "memory_list"
    override val description = "列出最近的记忆条目。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "scope" to Schema.string("作用域过滤", enumValues = listOf("assistant", "global")),
            "limit" to Schema.integer("返回条数，默认 20"),
        ),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val scope = args.str("scope")
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 20
        val hits = store.list(ctx.assistantId, scope, limit)
        if (hits.isEmpty()) return@runCatching ToolResult.Ok("（还没有记忆）")
        ToolResult.Ok(hits.joinToString("\n") { formatHit(it) })
    }.getOrElse { ToolResult.Error(it.message ?: "列出记忆失败") }
}

private fun formatHit(hit: MemoryHit): String {
    val sim = hit.similarity?.let { " · ${"%.2f".format(it)}" } ?: ""
    return "- [${hit.type}] ${hit.content}（id=${hit.id}$sim）"
}

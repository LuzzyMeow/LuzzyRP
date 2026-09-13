package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.WorldEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 世界书检索工具（模型可自主调用，应用侧真实执行）。
 *
 * 与 WebView 版世界书激活同语义：关键词命中即激活条目；差别是本阶段由**模型发起**
 * （function calling），而非生成前无条件扫描（生成前的无条件扫描见 [WorldBookActivator]）。
 *
 * 关键点：`execute` 收到的参数字符串来自模型流式增量拼装（[com.luzzymeow.luzzyrp.chat.llm.ToolCallAccumulator]），
 * 解析失败时按空关键词处理（返回未命中，不抛异常）。
 *
 * ## 数据源（P5-A 起走真库）
 *
 * 早先这里读的是**硬编码的演示书** `VanioCard.worldBook`——真角色导入后，工具查的却是 Vanio 的设定。
 * 现在由调用方通过 [withEntries] 注入**本次请求实际装配的条目**（全局 + 角色绑定 + 已启用），
 * 于是「模型查到的」与「生成前扫到的」是同一份数据。
 * [execute] 的无参重载保留演示书作默认值，只为「单独使用工具时不崩」与既有测试的兼容。
 */
object WorldBookTool {
    const val Name = "world_info_lookup"

    /** OpenAI 形态工具定义（仅 OpenAI 协议发送，见 LlmRequest.tools）。 */
    val schema: JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put(
            "function",
            buildJsonObject {
                put("name", JsonPrimitive(Name))
                put(
                    "description",
                    JsonPrimitive(
                        "检索角色世界书条目（人物设定、地点、事件、规则等细节）。" +
                            "当回复需要具体设定依据时调用。",
                    ),
                )
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "keywords",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("array"))
                                        put(
                                            "items",
                                            buildJsonObject { put("type", JsonPrimitive("string")) },
                                        )
                                        put("description", JsonPrimitive("检索关键词，2~4 个"))
                                    },
                                )
                            },
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("keywords"))))
                    },
                )
            },
        )
    }

    val schemas: List<JsonObject> = listOf(schema)

    /**
     * 用**真实条目**造一个执行器（`AgentLoop(toolRunner = …)` 用这个）。
     *
     * 条目在构造时就固定：一次请求内不会变，模型多次调用看到的是同一份世界书。
     */
    fun withEntries(entries: List<WorldEntry>): (String, String) -> String =
        { name, args -> execute(name, args, entries) }

    /**
     * 执行一次真实检索（默认用演示书——仅为兼容单独使用；正常路径见 [withEntries]）。
     *
     * @param rawArguments 模型给的工具参数原始 JSON（可能不合法 → 视为未命中）。
     * @return 结果 JSON 文本（回填给模型的 `tool` 消息，同时供思考节点展示）。
     */
    fun execute(toolName: String, rawArguments: String): String =
        execute(toolName, rawArguments, null)

    /** [entries] 为 null 时回落演示书（`VanioCard.worldBook`）。 */
    fun execute(toolName: String, rawArguments: String, entries: List<WorldEntry>?): String {
        if (toolName != Name) {
            return """{"error":"未知工具：$toolName"}"""
        }
        val keywords = parseKeywords(rawArguments)
        val hits = lookup(keywords, entries)
        return buildJsonObject {
            put("matched", JsonArray(hits.map { JsonPrimitive(it.displayName) }))
            put("entries", JsonPrimitive(hits.size))
            put(
                "content",
                JsonArray(
                    hits.map { e ->
                        buildJsonObject {
                            put("title", JsonPrimitive(e.displayName))
                            put("text", JsonPrimitive(e.content))
                        }
                    },
                ),
            )
        }.toString()
    }

    /**
     * 关键词命中检索（命中任一 key 即激活）。
     *
     * 匹配口径与生成前扫描**一致**（大小写不敏感子串；`useRegex` 走正则），
     * 免得「扫得到的查不到」这种两套语义。命中后保持世界书顺序。
     */
    fun lookup(keywords: List<String>, entries: List<WorldEntry>? = null): List<WorldEntry> {
        if (keywords.isEmpty()) return emptyList()
        val source = entries ?: VanioCard.worldBook.map { it.toWorldEntry() }
        return source.filter { entry ->
            if (!entry.enabled) return@filter false
            if (entry.constant) return@filter true
            val probe = keywords.joinToString("\n")
            WorldBookActivator.matches(entry, probe)
        }
    }

    /** 解析模型参数里的 keywords（非法 JSON / 缺字段 → 空列表）。 */
    fun parseKeywords(rawArguments: String): List<String> {
        val trimmed = rawArguments.trim()
        if (trimmed.isEmpty()) return emptyList()
        val obj: JsonObject = runCatching {
            com.luzzymeow.luzzyrp.chat.llm.JsonLenient.json.parseToJsonElement(trimmed).jsonObject
        }.getOrNull() ?: return emptyList()
        val arr = obj["keywords"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            .filter { it.isNotBlank() }
    }

    /** 世界书命中 → 注入 system 的文本块（模型侧可直接引用；空命中返回空串）。 */
    fun renderForPrompt(hits: List<WorldEntry>): String {
        if (hits.isEmpty()) return ""
        val body = hits.joinToString("\n") { "【${it.displayName}】${it.content}" }
        return "<world_info>\n$body\n</world_info>"
    }
}

/** 演示书条目 → 真条目视图（只为 [lookup] 的默认路径服务）。 */
private fun VanioCard.WorldEntry.toWorldEntry(): WorldEntry = WorldEntry(
    comment = title,
    content = content,
    keys = keys,
)

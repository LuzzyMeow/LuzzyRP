package com.luzzymeow.luzzyrp.chat

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
 * （function calling），而非生成前无条件扫描。
 *
 * 关键点：`execute` 收到的参数字符串来自模型流式增量拼装（[com.luzzymeow.luzzyrp.chat.llm.ToolCallAccumulator]），
 * 解析失败时按空关键词处理（返回未命中，不抛异常）。
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
                        "检索角色世界书条目（钟楼、苹果树、嬷嬷的巡视、教堂后墙、恶魔体征等设定细节）。" +
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
     * 执行一次真实检索。
     *
     * @param rawArguments 模型给的工具参数原始 JSON（可能不合法 → 视为未命中）。
     * @return 结果 JSON 文本（回填给模型的 `tool` 消息，同时供思考节点展示）。
     */
    fun execute(toolName: String, rawArguments: String): String {
        if (toolName != Name) {
            return """{"error":"未知工具：$toolName"}"""
        }
        val keywords = parseKeywords(rawArguments)
        val hits = lookup(keywords)
        return buildJsonObject {
            put("matched", JsonArray(hits.map { JsonPrimitive(it.title) }))
            put("entries", JsonPrimitive(hits.size))
            put(
                "content",
                JsonArray(
                    hits.map { e ->
                        buildJsonObject {
                            put("title", JsonPrimitive(e.title))
                            put("text", JsonPrimitive(e.content))
                        }
                    },
                ),
            )
        }.toString()
    }

    /** 关键词命中检索（命中任一 key 即激活；保持世界书顺序）。 */
    fun lookup(keywords: List<String>): List<VanioCard.WorldEntry> {
        if (keywords.isEmpty()) return emptyList()
        return VanioCard.worldBook.filter { entry ->
            entry.keys.any { key -> keywords.any { kw -> kw.contains(key) || key.contains(kw) } }
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
    fun renderForPrompt(hits: List<VanioCard.WorldEntry>): String {
        if (hits.isEmpty()) return ""
        val body = hits.joinToString("\n") { "【${it.title}】${it.content}" }
        return "<world_info>\n$body\n</world_info>"
    }
}

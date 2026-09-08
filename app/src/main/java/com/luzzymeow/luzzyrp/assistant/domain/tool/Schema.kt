package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * JSON Schema 构造 DSL（工具参数声明用，PLAN §12.3）。
 *
 * 只覆盖工具声明所需的子集：object / array / string / number / integer / boolean /
 * enum / required / description。**不引入额外依赖**（kotlinx-serialization 已在依赖表）。
 */
object Schema {

    fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String> = emptyList(),
        description: String? = null,
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(properties))
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
        if (description != null) put("description", JsonPrimitive(description))
        // 严格模式：拒绝未声明字段（OpenAI strict tool calling 兼容）
        put("additionalProperties", JsonPrimitive(false))
    }

    fun string(
        description: String? = null,
        enumValues: List<String>? = null,
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        if (description != null) put("description", JsonPrimitive(description))
        if (!enumValues.isNullOrEmpty()) {
            put("enum", JsonArray(enumValues.map { JsonPrimitive(it) }))
        }
    }

    fun number(description: String? = null): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("number"))
        if (description != null) put("description", JsonPrimitive(description))
    }

    fun integer(description: String? = null): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("integer"))
        if (description != null) put("description", JsonPrimitive(description))
    }

    fun boolean(description: String? = null): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("boolean"))
        if (description != null) put("description", JsonPrimitive(description))
    }

    fun array(items: JsonObject, description: String? = null): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("items", items)
        if (description != null) put("description", JsonPrimitive(description))
    }

    /** 无参数工具用空对象 schema。 */
    fun empty(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(emptyMap()))
        put("additionalProperties", JsonPrimitive(false))
    }
}

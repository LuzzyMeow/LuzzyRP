package com.luzzymeow.luzzyrp.core.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response

/** 全局 JSON（与 core:common JsonInstant 同参，供 core:ai 内部解析使用）。 */
val AiJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

/** 附加请求头 Map → Headers。 */
fun Map<String, String>.toHeaders(): Headers = Headers.Builder().apply {
    forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) add(k, v) }
}.build()

/**
 * 深合并附加请求体（extraBodyJson / customBody）。
 * 顶层与嵌套 JsonObject 递归合并；其余类型以附加体覆盖。
 */
fun JsonObject.mergeCustomBody(extra: String): JsonObject {
    if (extra.isBlank()) return this
    return try {
        val extraObj = AiJson.parseToJsonElement(extra).let {
            if (it is JsonObject) it else return this
        }
        deepMerge(this, extraObj)
    } catch (_: Exception) {
        this
    }
}

private fun deepMerge(base: JsonObject, extra: JsonObject): JsonObject {
    val result = base.toMutableMap()
    for ((key, value) in extra) {
        val existing = result[key]
        result[key] = if (existing is JsonObject && value is JsonObject) {
            deepMerge(existing, value)
        } else {
            value
        }
    }
    return JsonObject(result)
}

/**
 * 深合并多个 JSON 对象字符串（后覆盖前，嵌套 JsonObject 递归合并）。
 * 任意一段非法/为空则跳过；全部非法返回空对象字符串。
 */
fun mergeJsonBodies(vararg bodies: String): String {
    var acc: JsonObject = JsonObject(emptyMap())
    for (body in bodies) {
        if (body.isBlank()) continue
        runCatching {
            val obj = AiJson.parseToJsonElement(body)
            if (obj is JsonObject) acc = deepMerge(acc, obj)
        }
    }
    return AiJson.encodeToString(JsonObject.serializer(), acc)
}

/** 安全读取响应体（失败返回 null 而非抛异常）。 */
fun Response.stringSafe(): String? = try {
    body?.string()
} catch (_: Exception) {
    null
}

/**
 * 解析供应商错误响应的 detail 字段为异常。
 * 兼容 {"error": {"message": ...}} 与 {"error": "msg"} 两种形态。
 */
fun JsonElement.parseErrorDetail(): Exception {
    return try {
        val obj = this.jsonObject
        val error = obj["error"]
        val message = when (error) {
            is JsonObject -> error["message"]?.jsonPrimitive?.content ?: error.toString()
            is JsonPrimitive -> error.content
            else -> this.toString()
        }
        Exception(message)
    } catch (_: Exception) {
        Exception(this.toString())
    }
}

/** JsonArray/JsonObject/JsonPrimitive 的空安全访问扩展。 */
fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

/** JsonObject 构建辅助：put 可空字符串（null 时跳过）。 */
fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    if (value != null) put(key, value)
}

/** 提取 API 错误消息的可读文本（含响应码上下文）。 */
fun buildApiErrorMessage(code: Int, body: String?): String = buildString {
    append("HTTP $code")
    if (!body.isNullOrBlank()) {
        append("：")
        append(
            runCatching {
                AiJson.parseToJsonElement(body).parseErrorDetail().message
            }.getOrNull() ?: body.take(500)
        )
    }
}

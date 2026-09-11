package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 宽松 JSON 解析（LLM 输出与工具参数容错用）。
 *
 * 模型产出的工具参数经常出现：尾随逗号、单引号、markdown 代码围栏包裹、
 * 空字符串等。此处只做**保守容错**——解析失败返回空对象，由调用方按
 * 「参数非法」处理，**绝不抛异常**。
 *
 * 本文件自 v1.5.0 的助手模块（commit 0392b662 前）原样恢复，仅改包名。
 */
object JsonLenient {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
        explicitNulls = false
    }

    fun parseObjectOrEmpty(raw: String): JsonObject {
        val text = stripFence(raw).trim()
        if (text.isEmpty()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text) as? JsonObject }
            .getOrNull() ?: JsonObject(emptyMap())
    }

    /** 去掉 markdown 代码围栏与前后噪声，尽量取出最外层 JSON 对象。 */
    fun stripFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val firstNewline = s.indexOf('\n')
            if (firstNewline > 0) s = s.substring(firstNewline + 1)
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end)
            s = s.trim()
        }
        // 取第一个 '{' 到最后一个 '}'（容忍模型在 JSON 前后加解释文字）
        val start = s.indexOf('{')
        val stop = s.lastIndexOf('}')
        return if (start in 0 until stop) s.substring(start, stop + 1) else s
    }
}

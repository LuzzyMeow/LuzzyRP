package com.luzzymeow.luzzyrp.core.common

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 单例。
 *
 * [INVARIANT-KV] 稳定序列化配置（HARD_REQUIREMENTS 规定 8）：
 *   - encodeDefaults = true：字段序与默认值输出保持确定性，同一模型两次序列化逐字节相等，
 *     这是 KV 缓存前缀稳定性的基础。
 *   - 禁止开启 prettyPrint（多出的空白会改变请求体字节，破坏缓存命中）。
 *   - 禁止改用其他 Json 实例序列化「会进入模型请求体的消息结构」。
 */
val JsonInstant: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    allowSpecialFloatingPointValues = true
}

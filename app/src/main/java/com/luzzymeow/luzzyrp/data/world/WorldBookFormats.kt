package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON 解析的唯一实例（与 `LuzzyStore` 同一套宽容设置）。 */
internal object WorldBookJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)
}

/**
 * 世界书**导入格式识别**（v3.1）。
 *
 * 认三种形态（都是真实世界里会遇到的）：
 *
 * | 形态 | 长什么样 | 来源 |
 * |---|---|---|
 * | ① 裸数组 | `[{comment, content, keys, …}, …]` | 我们自己的导出（旧版）/ 手写 |
 * | ② ST 条目映射 | `{entries: {"0": {...}, "1": {...}}}` | SillyTavern 世界书文件 |
 * | ③ CCv3 角色书 | 角色卡里的 `character_book: {entries: [...]}` 或 `{entries: {...}}` | 角色卡规范 v3 |
 *
 * 另外认我们自己的导出形态 `{name, entries: [...]}`（③ 的数组变体，带名字）。
 *
 * **失败即返回 null**（调用方如实报错）——不静默返回空列表，那会让用户以为「导入成功但书是空的」。
 */
object WorldBookFormats {

    /** 解析出条目列表（保持文件内顺序）。解析不出返回 null。 */
    fun parseEntries(text: String): List<JsonElement>? {
        val root = runCatching { WorldBookJson.parse(text) }.getOrNull() ?: return null
        return entriesOf(root)
    }

    /**
     * 从文件里取书名（没有则 null → 调用方用文件名兜底）。
     *
     * ST 的世界书文件本身不存书名（文件名就是书名），所以这条主要服务我们自己的导出与 CCv3。
     */
    fun parseName(text: String): String? {
        val root = WorldBookJson.parse(text) as? JsonObject ?: return null
        // ① 我们自己的导出 / 角色书外层
        (root["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { return it }
        (root["character_book"] as? JsonObject)?.let { book ->
            (book["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { return it }
        }
        // ② 角色卡顶层（导入角色卡时用角色名当书名）
        (root["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { return it }
        (root["data"] as? JsonObject)?.let { data ->
            (data["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { return it }
        }
        return null
    }

    /**
     * 从任意 JSON 形态里挖出条目数组。
     *
     * 顺序：裸数组 → `entries`（数组或对象映射）→ `character_book.entries` → 角色卡 `data.character_book`。
     */
    fun entriesOf(root: JsonElement): List<JsonElement>? = when (root) {
        is JsonArray -> root.toList().takeIf { it.isNotEmpty() }
        is JsonObject -> {
            entriesIn(root)
                ?: (root["character_book"] as? JsonObject)?.let { entriesIn(it) }
                ?: (root["data"] as? JsonObject)?.let { data ->
                    entriesIn(data)
                        ?: (data["character_book"] as? JsonObject)?.let { entriesIn(it) }
                }
        }
        else -> null
    }

    /** 容器里的 `entries`：数组原样；对象映射按其 key 的**数值序**展开（ST 的 uid 是数字串）。 */
    private fun entriesIn(container: JsonObject): List<JsonElement>? =
        when (val entries = container["entries"]) {
            is JsonArray -> entries.toList().takeIf { it.isNotEmpty() }
            is JsonObject -> entries.entries
                .sortedBy { (key, _) -> key.toIntOrNull() ?: Int.MAX_VALUE }
                .map { (_, value) -> value }
                .takeIf { it.isNotEmpty() }
            else -> null
        }
}

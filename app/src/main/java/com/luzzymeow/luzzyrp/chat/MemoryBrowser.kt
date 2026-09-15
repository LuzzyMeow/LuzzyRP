package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * **记忆内容的浏览与改写**（记忆系统页的纯逻辑层）。
 *
 * ## 为什么单独立一层
 *
 * 记忆在库里是**整组读写**的 JSON 数组（`memories` 表按 `(scopeId, kind)` 分组，
 * 见 `LuzzyStore.replaceMemories`）。于是「改一条」在实现上必然是「读整组 → 改一条 → 写整组」，
 * 而这个往返里最容易错的正是**改哪一条、以及改动有没有误伤兄弟**：
 * 下标写错一位、`enabled` 只改了列没改 payload、删除后 id 串位——都会静默改坏用户数据。
 *
 * 所以这一层全是**纯函数**：[Item] 是只读投影，三个改写函数输入输出都是
 * `List<JsonElement>`，同输入同输出，可 JVM 单测。
 *
 * ## 字段口径（逐条对齐旧数据，见 `MemoryStats`）
 *
 * | 形态 | 正文键 | 身份 |
 * |---|---|---|
 * | 向量分片 `vector` | `paragraph`（极少数老数据回落到 `summary`） | `id` |
 * | 总结记忆 `classic` | `summary` | `id` |
 *
 * 其余字段（`turn` / `enabled` / `chunkMode` / `sourceRole` / `sourceName` /
 * `embeddingDims` / `embeddingModel`）**原样背着走**：本层只改自己声明要改的那一个键，
 * 其余键在所有改写函数里逐字保留——这是「编辑一条不该重写它的一生」的落地方式。
 */
object MemoryBrowser {

    /** 向量分片。 */
    const val VECTOR = "vector"

    /** 总结记忆。 */
    const val CLASSIC = "classic"

    /** 页面上一条可操作记忆的投影。 */
    data class Item(
        /** 稳定身份（旧数据里恒存在；缺了由 [LuzzyStore] 按下标补，见其说明）。 */
        val id: String,
        val kind: String,
        /** 分组内下标（**身份的一部分**：id 缺失时靠它定位）。 */
        val index: Int,
        /** 覆盖到第几轮（0 = 没有该字段）。 */
        val turn: Int,
        val enabled: Boolean,
        /** 正文（编辑器的初始内容）。 */
        val text: String,
        val embeddingDims: Int,
        val embeddingModel: String,
        val sourceRole: String,
        val sourceName: String,
        val chunkMode: String,
    ) {
        /** 是否带向量（页面上「已嵌入 / 待嵌入」的判据）。 */
        val hasEmbedding: Boolean get() = embeddingDims > 0

        /**
         * 一行元信息（旧版 `memoryManagerShardModelLabel` 的等价物）。
         *
         * 三档依次退让：**模型 + 维度** → **来源角色** → 「未标来源」。
         * 不显示空串：列表里一行空白会让人以为是渲染坏了。
         */
        val metaLabel: String
            get() = when {
                embeddingModel.isNotBlank() && embeddingDims > 0 -> "$embeddingModel · ${embeddingDims} 维"
                sourceName.isNotBlank() -> "来源 $sourceName"
                sourceRole.isNotBlank() -> "来源 $sourceRole"
                else -> "未标来源"
            }

        /** 轮次标签（`第 3 轮` / `轮次未知`）。 */
        val turnLabel: String get() = if (turn > 0) "第 $turn 轮" else "轮次未知"

        /** 列表里的正文预览（折叠态用；按字符数截断，不按行）。 */
        fun preview(maxChars: Int = 96): String {
            val flat = text.replace(Regex("\\s+"), " ").trim()
            return if (flat.length <= maxChars) flat else flat.take(maxChars) + "…"
        }
    }

    /** 分组投影：解析失败的条目**跳过**（不把坏数据渲染成空行），并按轮次排序。 */
    fun items(elements: List<JsonElement>, kind: String): List<Item> {
        val parsed = elements.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = obj.str("id").takeIf { it.isNotBlank() } ?: "$kind-$index"
            Item(
                id = id,
                kind = kind,
                index = index,
                turn = obj.int("turn"),
                // 缺字段 = 启用（与旧数据语义一致：老分片没有 enabled 这个键）
                enabled = obj.bool("enabled") ?: true,
                text = bodyOf(obj, kind),
                embeddingDims = obj.int("embeddingDims"),
                embeddingModel = obj.str("embeddingModel"),
                sourceRole = obj.str("sourceRole"),
                sourceName = obj.str("sourceName"),
                chunkMode = obj.str("chunkMode"),
            )
        }
        // 稳定排序：轮次升序，同轮按原下标（**不是按 id**——id 在旧数据里可能是随机串，
        // 按它排会让每次都换序；原下标是唯一与旧版展示一致的稳定键）
        return parsed.sortedWith(compareBy({ if (it.turn > 0) it.turn else Int.MAX_VALUE }, { it.index }))
    }

    /** 取正文：向量形态优先 `paragraph`，回落 `summary`（老夹具里两种都出现过）。 */
    fun bodyOf(obj: JsonObject, kind: String): String =
        if (kind == VECTOR) obj.str("paragraph").ifBlank { obj.str("summary") } else obj.str("summary")

    /**
     * 启停一条。
     *
     * **同时改 payload 与列**：`enabled` 在库里有两份（payload 内 + `memories.enabled` 列，
     * 后者由 `replaceMemories` 从 payload 派生）。只改一处 → 下次读回来又是一样的值，
     * 界面表现为「开关自己弹回去」。
     */
    fun setEnabled(
        elements: List<JsonElement>,
        kind: String,
        id: String,
        enabled: Boolean,
    ): List<JsonElement> = mapById(elements, kind, id) { obj ->
        JsonObject(obj.toMutableMap().apply { this["enabled"] = JsonPrimitive(enabled) })
    }

    /** 改写正文（向量形态写 `paragraph`，总结形态写 `summary`；其余键逐字保留）。 */
    fun setText(
        elements: List<JsonElement>,
        kind: String,
        id: String,
        text: String,
    ): List<JsonElement> = mapById(elements, kind, id) { obj ->
        // 写回**原本承载正文的那个键**：向量分片绝大多数是 `paragraph`，
        // 但老夹具里有少量只有 `summary`（见 [bodyOf]）——写错键的后果是「编辑后看起来没变」，
        // 因为读回来的还是另一个键里的旧文本。
        val key = when {
            kind != VECTOR -> "summary"
            obj.containsKey("paragraph") -> "paragraph"
            obj.containsKey("summary") -> "summary"
            else -> "paragraph"
        }
        JsonObject(obj.toMutableMap().apply { this[key] = JsonPrimitive(text) })
    }

    /** 删掉一条（**只删这一条**，其余保持原顺序）。 */
    fun remove(elements: List<JsonElement>, kind: String, id: String): List<JsonElement> =
        elements.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull element
            if (identityOf(obj, index, kind) == id) null else element
        }

    /**
     * 按 id 改写一条；**找不到就原样返回**。
     *
     * 「找不到」= 界面上的列表与库里的分组不一致（并发写入 / 刚被别处清空）。
     * 此时正确的行为是**什么都不做**——退回一个「已改好」的假象会在下一次读库时自我打脸。
     */
    private fun mapById(
        elements: List<JsonElement>,
        kind: String,
        id: String,
        transform: (JsonObject) -> JsonObject,
    ): List<JsonElement> = elements.mapIndexed { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexed element
        if (identityOf(obj, index, kind) == id) transform(obj) else element
    }

    /** 元素身份：优先 `id`，缺失时按 `kind-下标` 合成（与 `LuzzyStore.replaceMemories` 同一规则）。 */
    fun identityOf(obj: JsonObject, index: Int, kind: String): String =
        obj.str("id").takeIf { it.isNotBlank() } ?: "$kind-$index"

    private fun JsonObject.str(key: String): String = this[key].let { element ->
        if (element == null || element is JsonNull || element !is JsonPrimitive || !element.isString) "" else element.content
    }

    private fun JsonObject.int(key: String): Int = this[key].let { element ->
        val primitive = element as? JsonPrimitive ?: return 0
        primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * **角色卡页的纯逻辑**（v3.2 重建）。
 *
 * ## 为什么单独立一层
 *
 * 卡片上要显示的四个派生值（世界书条数 / 正则条数 / 是否收藏 / 描述）全都藏在角色卡的
 * `payload` JSON 里，而那份 payload 是**旧版整体搬过来的**（键名逐字保留，见 `CharacterEntity`）。
 * 「从 JSON 里取一个数」这种活在界面里写就意味着只能起模拟器看，取错键名也不会报错——
 * 表现为卡片底部永远显示「0 世界书」。
 *
 * 所以派生与过滤都放这里：纯函数、可 JVM 单测、键名口径一处定义。
 *
 * ## 键名口径（对齐旧版 `ui-components.js` 的 `CharacterCard`）
 *
 * | 显示项 | 来源键 | 缺省 |
 * |---|---|---|
 * | 世界书条数 | `worldInfo`（数组长度） | 0 |
 * | 正则条数 | `regexScripts`（数组长度） | 0 |
 * | 收藏 | `favoriteAt` > 0（旧版 `Number(char.favoriteAt) > 0`） | 未收藏 |
 * | 描述 | `description`（搜索用；不直接上卡片） | 空 |
 * | 头像 | 列上的 `avatarPath`（迁移时抽成了文件） | 首字 monogram |
 */
object CharacterCards {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 卡片一行（只带显示与筛选需要的字段）。 */
    data class Row(
        val uuid: String,
        val name: String,
        val avatarPath: String?,
        /** 描述（搜索命中用；不直接显示在卡片上）。 */
        val description: String,
        val worldInfoCount: Int,
        val regexCount: Int,
        val favorite: Boolean,
        val isActive: Boolean,
        val createdAt: Long,
    ) {
        /** 搜索命中：名称或描述包含关键词（大小写不敏感，两头去空格）。 */
        fun matches(query: String): Boolean {
            val keyword = query.trim()
            if (keyword.isEmpty()) return true
            return name.contains(keyword, ignoreCase = true) ||
                description.contains(keyword, ignoreCase = true)
        }

        /** 无头像时的首字（空名回落到「角」——旧版占位符同字）。 */
        val monogram: String get() = name.trim().take(1).ifBlank { "角" }
    }

    /**
     * 从库行解析。
     *
     * `payload` 解析失败**不抛**：给一个只有名字的卡片（用户仍然能看到它、能删它），
     * 而不是让整页因为一张坏卡起不来。
     */
    fun parse(
        uuid: String,
        name: String,
        avatarPath: String?,
        payload: String,
        isActive: Boolean,
        createdAt: Long,
    ): Row {
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        return Row(
            uuid = uuid,
            name = name.ifBlank { "未命名角色" },
            avatarPath = avatarPath,
            description = obj?.text("description").orEmpty(),
            worldInfoCount = obj?.arraySize("worldInfo") ?: 0,
            regexCount = obj?.arraySize("regexScripts") ?: 0,
            favorite = obj?.favorite() ?: false,
            isActive = isActive,
            createdAt = createdAt,
        )
    }

    /**
     * 展示排序：**当前使用的最前**，然后收藏，然后按名字。
     *
     * 与旧版一致的两条：当前角色置顶、收藏前置。名字作末位稳定键——否则同权重卡片
     * 每次重组的顺序可能不同（列表跳动）。
     */
    fun sorted(rows: List<Row>): List<Row> = rows.sortedWith(
        compareByDescending<Row> { it.isActive }
            .thenByDescending { it.favorite }
            .thenBy { it.name },
    )

    /** 在 payload 上写收藏状态（保留其余键**逐字不动**，加时间戳便于将来「按收藏时间排序」）。 */
    fun withFavorite(payload: String, favorite: Boolean, now: Long): String {
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return payload
        val updated = obj.toMutableMap().apply {
            this["favoriteAt"] = if (favorite) JsonPrimitive(now) else JsonPrimitive(0)
        }
        return JsonObject(updated).toString()
    }

    private fun JsonObject.text(key: String): String = this[key].let { element ->
        if (element == null || element is JsonNull || element !is JsonPrimitive || !element.isString) "" else element.content
    }

    private fun JsonObject.arraySize(key: String): Int = (this[key] as? JsonElement)?.let { element ->
        (element as? JsonArray)?.size ?: 0
    } ?: 0

    /** `favoriteAt` > 0 即收藏；字符串形态也认（旧数据里数字常是字符串）。 */
    private fun JsonObject.favorite(): Boolean {
        val element = this["favoriteAt"] ?: return false
        val primitive = element as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        return (primitive.longOrNull ?: primitive.content.toLongOrNull() ?: 0L) > 0L
    }
}

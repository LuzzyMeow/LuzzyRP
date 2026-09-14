package com.luzzymeow.luzzyrp.data.transfer

import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * D2 导入导出的**序列化半**（纯 Kotlin，JVM 可测；SAF 的运行时半在设置页 + 宿主）。
 *
 * ## 为什么预设/世界书是「零映射」
 *
 * `records` 表的 payload 本来就是**旧结构键名逐字保留**的 JSON（P4-B 的迁移零丢失前提）。
 * 导出/导入在这里做任何映射都只会引入两份真源——所以导出 = 原样数组，导入 = 原样回填。
 *
 * ## 角色卡为什么必须注入 uuid
 *
 * 存储主键 `uuid` 是迁移器定的：旧数据里有就用，没有就按**内容哈希补**——所以它**不一定
 * 在 payload 里**。导出时若不把 uuid 写进 JSON，导出→导入就会全部生成新身份，
 * 同一张卡在库里出现两份。注入 `uuid` 键后，导入按「同 uuid 覆盖」回到原样。
 */
object TransferFormat {

    private val json = Json

    // ---------------- 预设 / 世界书：records 原样数组 ----------------

    fun exportRecords(records: List<JsonElement>): String = JsonArray(records).toString()

    /** 顶层必须是 JSON 数组；不是就抛 [IllegalArgumentException]（带实际类型，不静默吞）。 */
    fun importRecords(text: String): List<JsonElement> {
        val element = parseOrThrow(text)
        return element as? JsonArray
            ?: throw IllegalArgumentException("导入文件的顶层必须是数组，实际是 ${typeName(element)}")
    }

    // ---------------- 角色卡 ----------------

    /**
     * 每张卡 = 存储 payload 原样（`data` 外壳与顶层平铺两种形态都**原样保留**，不归一）+
     * 注入 `uuid` 键（身份必须随卡走，见类注释）。
     */
    fun exportCharacters(rows: List<CharacterEntity>): String =
        JsonArray(rows.map { row -> withUuidInjected(row) }).toString()

    /**
     * 解析导入文件 → 角色卡实体。接受单对象（上游单卡导出）或数组（本应用导出格式）。
     * 缺 `name` 的条目直接抛错（一个坏文件不该静默变出无名卡）；缺 uuid 的生成新身份。
     */
    fun importCharacters(text: String): List<CharacterEntity> {
        val element = parseOrThrow(text)
        val items = element as? JsonArray ?: listOf(element)
        return items.map { toEntity(it) }
    }

    private fun toEntity(element: JsonElement): CharacterEntity {
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("角色卡条目必须是 JSON 对象，实际是 ${typeName(element)}")
        // V2 卡的 name 在 data 外壳里；旧形态在顶层（与 PromptInputSource.characterViewOf 同一判据）
        val source = (obj["data"] as? JsonObject) ?: obj
        val name = source.stringOf("name")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("角色卡缺少 name 字段")
        val selfUuid = (obj.stringOf("uuid") ?: obj.stringOf("id"))?.takeIf { it.isNotBlank() }
        return CharacterEntity(
            uuid = selfUuid ?: UUID.randomUUID().toString(),
            name = name,
            // JSON 导入不带二进制头像；payload 里的 base64 内联头像会随卡原样走
            avatarPath = null,
            createdAt = System.currentTimeMillis(),
            payload = obj.toString(),
        )
    }

    private fun withUuidInjected(row: CharacterEntity): JsonElement {
        val parsed = runCatching { json.parseToJsonElement(row.payload) }.getOrNull()
        val obj = parsed as? JsonObject
            ?: JsonObject(mapOf("name" to JsonPrimitive(row.name)))
        return JsonObject(obj.toMutableMap().apply { put("uuid", JsonPrimitive(row.uuid)) })
    }

    // ---------------- 公共 ----------------

    private fun parseOrThrow(text: String): JsonElement =
        runCatching { json.parseToJsonElement(text) }.getOrElse {
            throw IllegalArgumentException("导入文件不是合法 JSON：${it.message}")
        }

    private fun typeName(element: JsonElement): String = when (element) {
        is JsonArray -> "数组"
        is JsonObject -> "对象"
        is JsonPrimitive -> "标量（${element.content.take(20)}）"
        else -> element.toString().take(20)
    }

    private fun JsonObject.stringOf(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

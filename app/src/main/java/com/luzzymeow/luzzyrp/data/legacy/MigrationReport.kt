package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 迁移报告的数据模型（D3；纯 Kotlin）。
 *
 * 数据源是迁移成功后写的 `kv["legacy.migrationCounts"]`（12 个数字字段，写入点
 * `MigrationWriter.markMigrated`）+ `kv["legacy.migratedAt"]`。**没有迁移记录时
 * `parse` 返回 null**——调用方要显式呈现「未迁移」态，不许静默显示空表。
 */
data class MigrationReport(
    val migratedAt: Long,
    val characters: Int,
    val branches: Int,
    val messages: Int,
    val vectorMemories: Int,
    val classicMemories: Int,
    val worldEntries: Int,
    val legacyWorldEntriesDropped: Int,
    val regexes: Int,
    val presets: Int,
    val usage: Int,
    val profiles: Int,
    val assets: Int,
) {
    /** 展示行（标签 → 数值文案）；顺序即注入顺序的叙述（角色 → 会话 → 记忆 → …）。 */
    fun rows(): List<Pair<String, String>> = listOf(
        "角色" to "$characters",
        "剧情分支" to "$branches",
        "消息" to "$messages",
        "记忆（向量）" to "$vectorMemories",
        "记忆（经典）" to "$classicMemories",
        "世界书条目" to "$worldEntries",
        "忽略的遗留世界书" to "$legacyWorldEntriesDropped",
        "正则脚本" to "$regexes",
        "预设" to "$presets",
        "用量记录" to "$usage",
        "用户档案" to "$profiles",
        "附件资产" to "$assets",
    )

    /** 迁移时间（本地时区）；0（旧版本没写时间键）显示占位。 */
    val timeText: String
        get() = if (migratedAt <= 0L) "时间未知" else
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(migratedAt))

    companion object {
        /** 从 kv 值解析；`counts` 不是对象（无记录）→ null。字段缺省按 0（旧版本写入的键集可能更小）。 */
        fun parse(counts: JsonElement?, migratedAtRaw: String?): MigrationReport? {
            val obj = counts as? JsonObject ?: return null
            return MigrationReport(
                migratedAt = migratedAtRaw?.toLongOrNull() ?: 0L,
                characters = obj.intOf("characters"),
                branches = obj.intOf("branches"),
                messages = obj.intOf("messages"),
                vectorMemories = obj.intOf("vectorMemories"),
                classicMemories = obj.intOf("classicMemories"),
                worldEntries = obj.intOf("worldEntries"),
                legacyWorldEntriesDropped = obj.intOf("legacyWorldEntriesDropped"),
                regexes = obj.intOf("regexes"),
                presets = obj.intOf("presets"),
                usage = obj.intOf("usage"),
                profiles = obj.intOf("profiles"),
                assets = obj.intOf("assets"),
            )
        }

        private fun JsonObject.intOf(field: String): Int =
            (this[field] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    }
}

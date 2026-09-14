package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 旧两桶世界书 → **多书模型**的一次性迁移（v3.1）。
 *
 * ## 迁移什么（旧数据一个不丢）
 *
 * | 旧位置 | 新位置 |
 * |---|---|
 * | `records(kind=global_worldinfo)`（全局桶） | 一本书「默认世界书」+ 加入全局启用列表 |
 * | 每个角色卡 payload 的 `worldInfo` 数组 | 一本书「<角色名> 的世界书」+ 该角色绑定 |
 *
 * ## 三条纪律
 *
 * 1. **只读旧数据**：旧键（`global_worldinfo` 记录、角色卡 `worldInfo` 字段）**一个都不删**——
 *    回滚安全，且沿袭「迁移对旧数据只读」的既有纪律（与 WebView 迁移同款）。
 * 2. **幂等**：靠 `kv["worldbook.migrated"]` 标记；重复运行直接跳过。
 *    标记在**全部书建完之后**才写（中途失败 → 下次重来，不会留下半套）。
 * 3. **纯函数**：本对象的 [plan] 输入输出都是纯数据（不碰 Room / Android），
 *    只有 [apply] 做 IO → 迁移逻辑可用 JVM 单测逐条断言。
 */
object WorldBookMigration {

    /** 一次迁移要做的事（纯数据）。 */
    data class Plan(
        /** 要建的书：名字 + 条目（保持原顺序）。 */
        val books: List<PlannedBook>,
        val skipped: Boolean,
    ) {
        data class PlannedBook(val name: String, val entries: List<JsonElement>, val bindToCharacterUuid: String?)
    }

    /**
     * 算出迁移计划（**纯函数**）。
     *
     * @param alreadyMigrated 迁移标记是否已置位
     * @param globalWorldInfo 旧全局桶（`records(global_worldinfo)`）
     * @param characterWorldInfos 每个角色的 `(uuid, 角色名, worldInfo 数组)`
     */
    fun plan(
        alreadyMigrated: Boolean,
        globalWorldInfo: List<JsonElement>,
        characterWorldInfos: List<Triple<String, String, List<JsonElement>>>,
    ): Plan {
        if (alreadyMigrated) return Plan(emptyList(), skipped = true)
        val books = buildList {
            if (globalWorldInfo.isNotEmpty()) {
                add(Plan.PlannedBook(DEFAULT_BOOK_NAME, globalWorldInfo, null))
            }
            characterWorldInfos.forEach { (uuid, name, entries) ->
                if (entries.isNotEmpty()) {
                    add(Plan.PlannedBook(characterBookName(name), entries, uuid))
                }
            }
        }
        return Plan(books, skipped = false)
    }

    /** 角色书的命名（ST 同款：「<角色名> 的世界书」）。 */
    fun characterBookName(characterName: String?): String =
        "${characterName?.takeIf { it.isNotBlank() } ?: "角色"} 的世界书"

    const val DEFAULT_BOOK_NAME = "默认世界书"

    /**
     * 从角色卡 payload 里读旧的 `worldInfo` 数组（顶层或 `data` 外壳里）。
     *
     * 与 `WorldBookRepository.characterWorldInfo` 同一判据（两处都认，避免迁移与旧读路径不一致）。
     */
    fun worldInfoOf(payloadText: String?): List<JsonElement> {
        if (payloadText.isNullOrBlank()) return emptyList()
        val root = runCatching { WorldBookJson.parse(payloadText) }.getOrNull() as? JsonObject ?: return emptyList()
        val source = (root["data"] as? JsonObject) ?: root
        return (source["worldInfo"] as? JsonArray)?.toList().orEmpty()
    }
}

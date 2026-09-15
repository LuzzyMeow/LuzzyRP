package com.luzzymeow.luzzyrp.data.world

import com.luzzymeow.luzzyrp.chat.TimedEffects
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 世界书（多书架构，v3.1）——**书是一等公民**，条目属于书。
 *
 * ## 为什么从「两桶」改成「多书」
 *
 * 旧模型（P4-C）只有两个位置：全局桶 `records(global_worldinfo)` + 角色卡 payload 里的
 * `worldInfo` 数组。它表达了「全局生效 or 绑定当前角色」，但**没有「书」这个概念**——
 * 无法给一组条目起名、无法同时启用多本、无法把一本整体导入导出。
 * 参考实现（SillyTavern）与用户给的参考图都是**多书**模型：列表 → 选一本 → 编条目。
 *
 * ## 存储布局（复用现有 records 表，**零 schema 变更**）
 *
 * | 存什么 | kind | owner | slot |
 * |---|---|---|---|
 * | 书本身 | `LoreBook` | `<bookId>` | 0 |
 * | 条目 | `worldbook_entry` | `<bookId>` | 书内顺序 |
 *
 * 一条条目一行（不是整本一行）：与消息表同款理由——高频增量写不该重写整本书。
 *
 * ## 兼容
 *
 * 旧的两桶数据**一个都不删**：迁移（[WorldBookMigration]）把它们复制成书，
 * 原键原样保留 → 回滚安全，且「迁移对旧数据只读」的纪律沿袭。
 */
data class LoreBook(
    val id: String,
    val name: String,
    val entries: List<WorldEntry> = emptyList(),
    /** 条目在存储里的原始 payload（与 [entries] 同序；保存时 patch-merge 用）。 */
    val rawEntries: List<JsonElement> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {

    val entryCount: Int get() = entries.size

    /** 启用中的条目数（列表行上显示「N 条 · M 启用」）。 */
    val enabledCount: Int get() = entries.count { it.enabled }

    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "createdAt" to JsonPrimitive(createdAt),
            "updatedAt" to JsonPrimitive(updatedAt),
        ),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        const val KIND_BOOK = "LoreBook"
        const val KIND_ENTRY = "worldbook_entry"

        /** 书 id：`book-<毫秒>-<随机>`。**不用内容哈希**（书会改名，内容会变）。 */
        fun newId(): String = "book-${System.currentTimeMillis()}-${(1000..9999).random()}"

        fun from(id: String, payload: JsonElement?): LoreBook {
            val obj = payload as? JsonObject
            return LoreBook(
                id = id,
                name = (obj?.get("name") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_NAME,
                createdAt = (obj?.get("createdAt") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                updatedAt = (obj?.get("updatedAt") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
            )
        }

        fun entriesOf(raws: List<JsonElement>): List<WorldEntry> = raws.map { WorldEntry.from(it) }

        /** 条目组 → 导出形态（ST 可读：`{name, entries: [...]}`；条目字段用规范名）。 */
        fun toExportJson(book: LoreBook): JsonObject = JsonObject(
            mapOf(
                "name" to JsonPrimitive(book.name),
                "entries" to JsonArray(book.entries.map { it.mergeInto(JsonObject(emptyMap())) }),
            ),
        )

        const val DEFAULT_NAME = "未命名世界书"
    }
}

/**
 * 世界书集合快照（列表页 / 激活层用）。
 *
 * [enabledGlobally] = ST 的「global World Info selector」等价物：全局启用的书 id 列表。
 * [boundToCharacter] = 当前角色绑定的书 id 列表（ST 的 character lore）。
 */
data class LoreBookSet(
    val books: List<LoreBook> = emptyList(),
    val enabledGlobally: List<String> = emptyList(),
    val boundToCharacter: List<String> = emptyList(),
    val characterName: String? = null,
) {

    val isEmpty: Boolean get() = books.isEmpty()

    fun byId(id: String): LoreBook? = books.firstOrNull { it.id == id }

    /**
     * 同上，但**带上定时效果 key**（`<bookId>#<书内下标>`）。
     *
     * key 用「书 id + 下标」而不是内容哈希：ST 用内容哈希，改一个字效果就失联；
     * 我们改用位置身份（稳定），条目被编辑时由 UI 保存路径显式 `TimedEffects.forget`。
     */
    fun activePairs(): List<Pair<String, WorldEntry>> {
        val ids = boundToCharacter + enabledGlobally
        return ids.distinct().flatMap { id ->
            val book = byId(id) ?: return@flatMap emptyList()
            book.entries.mapIndexed { index, entry -> TimedEffects.keyOf(book.id, index) to entry }
        }
    }
}

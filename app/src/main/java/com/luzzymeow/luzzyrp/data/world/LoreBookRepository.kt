package com.luzzymeow.luzzyrp.data.world

import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 世界书**多书读写门面**（v3.1）。
 *
 * 与旧 [WorldBookRepository] 的分工（**并存**，不是替换）：
 *
 * | 类 | 负责 |
 * |---|---|
 * | [LoreBookRepository]（本类） | 多书 CRUD、全局启用列表、角色绑定、导入导出 |
 * | `WorldBookRepository` | 旧两桶读写（全局桶 + 角色卡内嵌 `worldInfo`）——过渡期保留 |
 *
 * 过渡策略：迁移把旧两桶**复制**成书（旧键不删），随后界面只读多书模型；
 * 旧类保留是为了「回滚安全」与「迁移器仍要写旧桶」两条路径。
 *
 * ## 存储
 *
 * - 书：`records(kind=worldbook, owner=<bookId>, slot=0)`
 * - 条目：`records(kind=worldbook_entry, owner=<bookId>, slot=书内顺序)`
 * - 全局启用：`kv["worldbook.enabledBooks"] = ["<bookId>", …]`
 * - 角色绑定：角色卡 payload 的 `worldBookIds: [String]`（**旧 `worldInfo` 键不动**）
 */
class LoreBookRepository(private val store: LuzzyStore) {

    // ---------------------------------------------------------------- 全局扫描设置

    /**
     * 全局扫描设置（扫描深度 / 最大扫描深度）。
     *
     * **为什么在这里而不是 `WorldBookRepository`**（v3.2 修正一处断链）：
     * 世界书路由用的是**本类**（v3.1 多书架构），而设置方法原先只在另一个仓库里——
     * 于是页面拿不到它，两个滑杆**在界面上完全不可达**（功能写好了、没人接出来）。
     * 现在真源在本类；`WorldBookRepository` 的同名方法转调这里，避免两处各读一次键。
     */
    suspend fun settings(): WorldInfoSettings =
        WorldInfoSettings.from(store.json(LuzzyStore.KEY_WORLDINFO_SETTINGS))

    suspend fun saveSettings(settings: WorldInfoSettings) =
        store.putJson(LuzzyStore.KEY_WORLDINFO_SETTINGS, settings.toJson())

    // ---------------------------------------------------------------- 读

    /** 全部书（按 updatedAt 倒序 = 最近编辑在前；未编辑过回落 id 序）。 */
    suspend fun all(): List<LoreBook> {
        val bookRows = store.recordsOfKind(LoreBook.KIND_BOOK)
        return bookRows.map { row ->
            val book = LoreBook.from(row.owner, parse(row.payload))
            val entries = store.records(LoreBook.KIND_ENTRY, row.owner)
            book.copy(
                entries = LoreBook.entriesOf(entries),
                rawEntries = entries,
            )
        }.sortedWith(compareByDescending<LoreBook> { it.updatedAt }.thenBy { it.id })
    }

    suspend fun byId(id: String): LoreBook? {
        val row = store.records(LoreBook.KIND_BOOK, id).firstOrNull() ?: return null
        val entries = store.records(LoreBook.KIND_ENTRY, id)
        return LoreBook.from(id, row).copy(entries = LoreBook.entriesOf(entries), rawEntries = entries)
    }

    /** 全局启用的书 id 列表（ST 的 global selector）。 */
    suspend fun enabledGlobally(): List<String> =
        (store.json(KEY_ENABLED_BOOKS) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()

    /** 角色绑定的书 id 列表（ST 的 character lore）。 */
    suspend fun boundTo(characterUuid: String?): List<String> {
        if (characterUuid == null) return emptyList()
        val payload = characterPayload(characterUuid) ?: return emptyList()
        return parseIds(payload["worldBookIds"])
    }

    /**
     * 可绑定的角色（绑定选择器用：uuid + 名字，按名字排序）。
     *
     * 只取界面要显示的两样，不把角色卡 payload（可能几百 KB、含内联头像）搬给界面。
     */
    suspend fun bindableCharacters(): List<BindableCharacter> =
        store.characters()
            .map { BindableCharacter(it.uuid, it.name.ifBlank { "未命名角色" }) }
            .sortedBy { it.name }

    /**
     * **反向查询**：哪些角色绑定了这本书。
     *
     * 为什么需要它：[boundTo] 是「角色 → 书」，而书列表页问的是「这本书 → 哪些角色」。
     * 绑定关系**只有一处真源**（角色卡 payload 的 `worldBookIds`），所以这里是**读**而不是
     * 另存一份索引——两份索引迟早会漂移，而漂移的表现是「界面显示已绑定、实际不生效」。
     */
    suspend fun boundCharacters(bookId: String): List<String> =
        store.characters()
            .filter { row ->
                val payload = runCatching { parse(row.payload) }.getOrNull() as? JsonObject
                payload != null && bookId in parseIds(payload["worldBookIds"])
            }
            .map { it.uuid }

    /**
     * 组装**当前生效的集合**（列表页显示启用态 / 激活层取条目）。
     *
     * @param characterUuid 当前角色（null = 空库演示态 → 只有全局启用的书）
     */
    suspend fun load(characterUuid: String?): LoreBookSet {
        val books = all()
        val characterName = characterUuid?.let { store.character(it)?.name }
        return LoreBookSet(
            books = books,
            enabledGlobally = enabledGlobally(),
            boundToCharacter = boundTo(characterUuid),
            characterName = characterName,
        )
    }

    // ---------------------------------------------------------------- 写（书）

    suspend fun create(name: String, entries: List<JsonElement> = emptyList()): String {
        val id = LoreBook.newId()
        val now = System.currentTimeMillis()
        store.transaction {
            store.replaceRecords(
                LoreBook.KIND_BOOK,
                id,
                listOf(LoreBook(id = id, name = name, createdAt = now, updatedAt = now).toJson()),
                now,
            )
            if (entries.isNotEmpty()) {
                store.replaceRecords(LoreBook.KIND_ENTRY, id, entries, now)
            }
        }
        return id
    }

    suspend fun rename(id: String, name: String) = mutateBook(id) { it.copy(name = name) }

    /** 删除一本书（连同条目与两处引用；引用清理在事务里做）。 */
    suspend fun delete(id: String) {
        store.transaction {
            store.replaceRecords(LoreBook.KIND_BOOK, id, emptyList())
            store.replaceRecords(LoreBook.KIND_ENTRY, id, emptyList())
            setEnabled(id, false)
            // 角色绑定里的引用：逐个角色清理（角色数量有限，直接全扫）
            store.characters().forEach { row ->
                val payload = runCatching { parse(row.payload) }.getOrNull() as? JsonObject ?: return@forEach
                val ids = parseIds(payload["worldBookIds"])
                if (id !in ids) return@forEach
                val next = JsonObject(
                    payload.toMutableMap().apply { this["worldBookIds"] = idsToJson(ids - id) },
                )
                store.upsertCharacter(row.copy(payload = next.toString()))
            }
        }
    }

    /** 全局启用/停用（ST selector）。 */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = enabledGlobally().toMutableList()
        if (enabled) {
            if (id !in current) current += id
        } else {
            current.remove(id)
        }
        store.putJson(KEY_ENABLED_BOOKS, idsToJson(current))
    }

    /** 角色绑定/解绑。 */
    suspend fun setBoundTo(characterUuid: String, bookId: String, bound: Boolean) {
        val row = store.character(characterUuid) ?: return
        val payload = (runCatching { parse(row.payload) }.getOrNull() as? JsonObject) ?: JsonObject(emptyMap())
        val current = parseIds(payload["worldBookIds"]).toMutableList()
        if (bound) {
            if (bookId !in current) current += bookId
        } else {
            current.remove(bookId)
        }
        val next = JsonObject(payload.toMutableMap().apply { this["worldBookIds"] = idsToJson(current) })
        store.upsertCharacter(row.copy(payload = next.toString()))
    }

    // ---------------------------------------------------------------- 写（条目）

    /** 新增或保存一条（`slot = null` = 追加到末尾）。 */
    suspend fun upsertEntry(bookId: String, slot: Int?, entry: WorldEntry) {
        store.transaction {
            val raws = store.records(LoreBook.KIND_ENTRY, bookId).toMutableList()
            val original = slot?.let { raws.getOrNull(it) }
            val payload = entry.mergeInto(original ?: JsonObject(emptyMap()))
            if (slot != null && slot in raws.indices) {
                raws[slot] = payload
            } else {
                raws += payload
            }
            store.replaceRecords(LoreBook.KIND_ENTRY, bookId, raws, System.currentTimeMillis())
            touch(bookId)
        }
    }

    suspend fun setEntryEnabled(bookId: String, slot: Int, enabled: Boolean) {
        store.transaction {
            val raws = store.records(LoreBook.KIND_ENTRY, bookId).toMutableList()
            if (slot !in raws.indices) return@transaction
            val base = WorldEntry.flattened(raws[slot]).toMutableMap()
            base.remove("disable")
            base.remove("disabled")
            base["enabled"] = JsonPrimitive(enabled)
            raws[slot] = JsonObject(base)
            store.replaceRecords(LoreBook.KIND_ENTRY, bookId, raws, System.currentTimeMillis())
            touch(bookId)
        }
    }

    suspend fun removeEntry(bookId: String, slot: Int) {
        store.transaction {
            val raws = store.records(LoreBook.KIND_ENTRY, bookId).toMutableList()
            if (slot !in raws.indices) return@transaction
            raws.removeAt(slot)
            store.replaceRecords(LoreBook.KIND_ENTRY, bookId, raws, System.currentTimeMillis())
            touch(bookId)
        }
    }

    /** 复制一条（照参考图条目行的「复制」动作；副本插在原条目**之后**）。 */
    suspend fun duplicateEntry(bookId: String, slot: Int) {
        store.transaction {
            val raws = store.records(LoreBook.KIND_ENTRY, bookId).toMutableList()
            val source = raws.getOrNull(slot) ?: return@transaction
            raws.add(slot + 1, source)
            store.replaceRecords(LoreBook.KIND_ENTRY, bookId, raws, System.currentTimeMillis())
            touch(bookId)
        }
    }

    /** 书内移动一格（`-1` 上移 / `+1` 下移）。 */
    suspend fun moveEntry(bookId: String, slot: Int, delta: Int) {
        store.transaction {
            val raws = store.records(LoreBook.KIND_ENTRY, bookId).toMutableList()
            val target = slot + delta
            if (slot !in raws.indices || target !in raws.indices) return@transaction
            val moved = raws.removeAt(slot)
            raws.add(target, moved)
            store.replaceRecords(LoreBook.KIND_ENTRY, bookId, raws, System.currentTimeMillis())
            touch(bookId)
        }
    }

    // ---------------------------------------------------------------- 导入导出

    /** 导出为 ST 可读形态。 */
    suspend fun exportJson(bookId: String): String? =
        byId(bookId)?.let { LoreBook.toExportJson(it).toString() }

    /**
     * 导入（新建一本书，**不合并**）。
     *
     * @return 新书 id；解析不出条目时返回 null（调用方如实报错，不静默丢数据）
     */
    suspend fun importJson(text: String, fallbackName: String): String? {
        val entries = WorldBookFormats.parseEntries(text) ?: return null
        val name = WorldBookFormats.parseName(text)?.takeIf { it.isNotBlank() } ?: fallbackName
        return create(name, entries)
    }

    // ---------------------------------------------------------------- 迁移

    /**
     * 旧两桶 → 多书的一次性迁移（幂等；**只读旧数据**）。
     *
     * 计划由 [WorldBookMigration.plan] 纯函数算出，本方法只负责执行与落标记。
     *
     * @return 建了几本书（0 = 无事可做或已迁移过）
     */
    suspend fun migrateLegacyWorldInfo(): Int {
        val already = store.string(KEY_MIGRATED) == "true"
        val globalWorldInfo = store.records(LuzzyStore.RECORD_GLOBAL_WORLDINFO)
        val characterWorldInfos: List<Triple<String, String, List<JsonElement>>> =
            store.characters().map { row ->
                Triple(row.uuid, row.name, WorldBookMigration.worldInfoOf(row.payload))
            }
        val plan = WorldBookMigration.plan(already, globalWorldInfo, characterWorldInfos)
        if (plan.skipped || plan.books.isEmpty()) {
            // 已迁移过：什么都不做。没东西可迁：**也要落标记**——
            // 否则每次启动都扫一遍角色卡（白读）。
            if (!plan.skipped) store.putString(KEY_MIGRATED, "true")
            return 0
        }
        plan.books.forEach { planned ->
            val id = create(planned.name, planned.entries)
            if (planned.bindToCharacterUuid == null) {
                // 旧全局桶 → 顺带加入全局启用（保持「迁移前生效的东西迁移后仍生效」）
                setEnabled(id, true)
            } else {
                setBoundTo(planned.bindToCharacterUuid, id, true)
            }
        }
        store.putString(KEY_MIGRATED, "true")
        return plan.books.size
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun mutateBook(id: String, transform: (LoreBook) -> LoreBook) {
        val current = byId(id) ?: return
        val now = System.currentTimeMillis()
        store.replaceRecords(LoreBook.KIND_BOOK, id, listOf(transform(current).copy(updatedAt = now).toJson()), now)
    }

    /** 触碰 updatedAt（条目变更时）。 */
    private suspend fun touch(bookId: String) {
        val current = byId(bookId) ?: return
        store.replaceRecords(
            LoreBook.KIND_BOOK,
            bookId,
            listOf(current.copy(updatedAt = System.currentTimeMillis()).toJson()),
            System.currentTimeMillis(),
        )
    }

    private suspend fun characterPayload(uuid: String): JsonObject? {
        val text = store.character(uuid)?.payload ?: return null
        return runCatching { parse(text) }.getOrNull() as? JsonObject
    }

    private fun parse(text: String): JsonElement? =
        runCatching { WorldBookJson.parse(text) }.getOrNull()

    companion object {
        /** kv 键：全局启用的书 id 列表。 */
        const val KEY_ENABLED_BOOKS = "worldbook.enabledBooks"
        /** kv 键：多书迁移标记（幂等）。 */
        const val KEY_MIGRATED = "worldbook.migrated"

        private fun parseIds(element: JsonElement?): List<String> =
            (element as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()

        private fun idsToJson(ids: List<String>): JsonArray = JsonArray(ids.map { JsonPrimitive(it) })
    }
}

/** 绑定选择器里的一行（只带界面要用的两样，见 [LoreBookRepository.bindableCharacters]）。 */
data class BindableCharacter(val uuid: String, val name: String)

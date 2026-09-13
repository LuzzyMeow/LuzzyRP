package com.luzzymeow.luzzyrp.data.world

import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 世界书的读写门面（W1）。界面只认这一层，不直接碰 Room，也不直接碰 [WorldBookOps]。
 *
 * ## 两个存储位置（不能只认一个）
 *
 * | 归属 | 存在哪 |
 * |---|---|
 * | 全局 | `records(kind=global_worldinfo, owner="")` |
 * | 绑定角色 | **角色卡 payload 里的 `worldInfo` 数组**（随角色卡一起走，与上游一致） |
 *
 * 角色绑定条目**刻意不抽出**成独立表：抽出来会在角色卡里留下第二份副本 → 双真源。
 * 代价是写入要做一次「角色行 payload 的读-改-写」，集中在本类里（保留 payload 的其余键）。
 *
 * ## 写入粒度
 *
 * 每次操作都是「读两个桶 → 纯函数算出新桶 → **只写变化的那个桶**」，整体在一个事务里。
 * 只写变化的那一侧，是为了避免「改一条全局条目却重写了整张角色卡」（角色卡可能很大）。
 */
class WorldBookRepository(
    private val store: LuzzyStore,
    private val sessions: ChatSessionRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------- 读

    /** 当前角色（kv 里记的，空库为 null）的世界书快照。 */
    suspend fun load(): WorldBook = load(sessions.currentCharacterUuid())

    suspend fun load(characterUuid: String?): WorldBook {
        val global = store.records(LuzzyStore.RECORD_GLOBAL_WORLDINFO)
        val character = characterWorldInfo(characterUuid)
        val rows = buildList {
            global.forEachIndexed { index, element ->
                add(row(EntryRef(WorldScope.Global, index), element))
            }
            character.forEachIndexed { index, element ->
                add(row(EntryRef(WorldScope.Character, index), element))
            }
        }
        return WorldBook(
            rows = rows,
            characterUuid = characterUuid,
            characterName = characterUuid?.let { store.character(it)?.name },
        )
    }

    private fun row(ref: EntryRef, element: JsonElement) =
        WorldRow(ref = ref, entry = WorldEntry.from(element), group = WorldBookOps.effectiveScope(element))

    /** 全局设置（扫描深度 / 最大扫描深度）。 */
    suspend fun settings(): WorldInfoSettings = WorldInfoSettings.from(store.json(LuzzyStore.KEY_WORLDINFO_SETTINGS))

    // ---------------------------------------------------------------- 写

    suspend fun saveSettings(settings: WorldInfoSettings) =
        store.putJson(LuzzyStore.KEY_WORLDINFO_SETTINGS, settings.toJson())

    /** 新增（`ref = null`）或保存一条；改归属即**跨桶移动**（目标末尾）。 */
    suspend fun upsert(ref: EntryRef?, entry: WorldEntry) = mutate { WorldBookOps.upsert(it, ref, entry) }

    suspend fun setEnabled(ref: EntryRef, enabled: Boolean) = mutate { WorldBookOps.setEnabled(it, ref, enabled) }

    suspend fun remove(ref: EntryRef) = mutate { WorldBookOps.remove(it, ref) }

    /** 上移/下移一格（顺序 = 注入顺序，所以这是真语义）。 */
    suspend fun move(ref: EntryRef, delta: Int) = mutate { WorldBookOps.move(it, ref, delta) }

    private suspend fun mutate(op: (WorldBookOps.Buckets) -> WorldBookOps.Buckets) {
        val characterUuid = sessions.currentCharacterUuid()
        store.transaction {
            val before = WorldBookOps.Buckets(
                global = store.records(LuzzyStore.RECORD_GLOBAL_WORLDINFO),
                character = characterWorldInfo(characterUuid),
            )
            val after = op(before)
            val now = System.currentTimeMillis()
            if (after.global != before.global) {
                store.replaceRecords(LuzzyStore.RECORD_GLOBAL_WORLDINFO, "", after.global, now)
            }
            if (after.character != before.character) {
                val uuid = requireNotNull(characterUuid) {
                    "没有当前角色，不能写角色绑定条目（界面应在无角色时禁用该选项）"
                }
                writeCharacterWorldInfo(uuid, after.character)
            }
        }
    }

    // ---------------------------------------------------------------- 角色卡 payload

    private suspend fun characterWorldInfo(uuid: String?): List<JsonElement> {
        if (uuid == null) return emptyList()
        val payload = characterPayload(uuid) ?: return emptyList()
        return (payload["worldInfo"] as? JsonArray)?.toList() ?: emptyList()
    }

    /** 读-改-写角色卡 payload：**只替换 `worldInfo` 一个键**，其余键原样保留。 */
    private suspend fun writeCharacterWorldInfo(uuid: String, entries: List<JsonElement>) {
        val row = store.character(uuid) ?: error("角色行不存在：$uuid")
        val payload = characterPayload(uuid) ?: JsonObject(emptyMap())
        val next = JsonObject(payload.toMutableMap().apply { this["worldInfo"] = JsonArray(entries) })
        store.upsertCharacter(row.copy(payload = next.toString()))
    }

    private suspend fun characterPayload(uuid: String): JsonObject? {
        val text = store.character(uuid)?.payload ?: return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
    }
}

/** 界面用的一行：**物理位置**（[ref]，动作按它寻址）+ 类型化视图 + **展示分组**（有效归属）。 */
data class WorldRow(
    val ref: EntryRef,
    val entry: WorldEntry,
    /** 可能与 `ref.scope` 不同：角色卡里躺着 `scope=global` 的条目时按全局展示（上游同）。 */
    val group: WorldScope,
)

/** 世界书快照。`rows` 保持物理顺序（全局在前、角色在后），界面按 [WorldRow.group] 分组渲染。 */
data class WorldBook(
    val rows: List<WorldRow>,
    val characterUuid: String?,
    val characterName: String?,
) {
    val hasCharacter: Boolean get() = characterUuid != null
    val globalRows: List<WorldRow> get() = rows.filter { it.group == WorldScope.Global }
    val characterRows: List<WorldRow> get() = rows.filter { it.group == WorldScope.Character }
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * 全局世界书设置（上游 `worldInfoSettings`，默认 `scanDepth=2, maxDepth=0`）。
 *
 * `maxDepth=0` 表示**不限制**（不是「扫 0 条」）——上游语义如此，界面上显示为「不限制」。
 * 读取时按界面滑杆的量程夹取（0-20 / 0-50）：老数据里若有越界值，滑杆不会因此崩掉。
 */
data class WorldInfoSettings(
    val scanDepth: Int = DEFAULT_SCAN_DEPTH,
    val maxDepth: Int = 0,
) {

    val unlimitedDepth: Boolean get() = maxDepth == 0

    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "scanDepth" to JsonPrimitive(scanDepth.coerceIn(0, MAX_SCAN_DEPTH)),
            "maxDepth" to JsonPrimitive(maxDepth.coerceIn(0, MAX_MAX_DEPTH)),
        ),
    )

    companion object {
        const val DEFAULT_SCAN_DEPTH = 2
        const val MAX_SCAN_DEPTH = 20
        const val MAX_MAX_DEPTH = 50

        fun from(element: JsonElement?): WorldInfoSettings {
            val obj = element as? JsonObject ?: return WorldInfoSettings()
            return WorldInfoSettings(
                scanDepth = (obj["scanDepth"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?.coerceIn(0, MAX_SCAN_DEPTH) ?: DEFAULT_SCAN_DEPTH,
                maxDepth = (obj["maxDepth"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?.coerceIn(0, MAX_MAX_DEPTH) ?: 0,
            )
        }
    }
}

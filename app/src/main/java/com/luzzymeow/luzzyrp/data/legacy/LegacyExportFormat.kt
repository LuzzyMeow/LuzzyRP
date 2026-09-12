package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * 旧库导出物（迁移通道的**唯一线格式**）。
 *
 * 形状：
 * ```json
 * { "fixtureVersion": 1,
 *   "databases": { "RPHubDB": {"version": 1, "entries": {"rp_hub_characters": [...] }},
 *                  "SillyTavernDB": {"version": 1, "entries": {...}} } }
 * ```
 *
 * **同一个格式**同时服务两处，这是刻意的：测试夹具
 * `app/src/test/resources/legacy/webview-db-fixture.json` 就是它，
 * 迁移 WebView（`ext/luzzy-migrate.html`）产出的也是它。于是**夹具即真实输入**，
 * 迁移器的单测不需要模拟器、也不需要另一套桩。
 */
data class LegacyDb(
    val mainVersion: Int?,
    val main: Map<String, JsonElement>,
    val legacyVersion: Int?,
    val legacy: Map<String, JsonElement>,
) {

    val isEmpty: Boolean get() = main.isEmpty() && legacy.isEmpty()

    companion object {
        const val MAIN_DB_NAME = "RPHubDB"
        const val LEGACY_DB_NAME = "SillyTavernDB"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): LegacyDb {
            val root = json.parseToJsonElement(text)
            return fromExportObject(root as? JsonObject ?: JsonObject(emptyMap()))
        }

        fun fromExportObject(root: JsonObject): LegacyDb {
            val databases = root["databases"]?.asObjectOrNull() ?: JsonObject(emptyMap())
            val main = readDatabase(databases[MAIN_DB_NAME])
            val legacy = readDatabase(databases[LEGACY_DB_NAME])
            return LegacyDb(
                mainVersion = main.first,
                main = main.second,
                legacyVersion = legacy.first,
                legacy = legacy.second,
            )
        }

        /** 返回 (version, entries)。`entries` 缺失/为 null（页面探测到空库）即空表。 */
        private fun readDatabase(element: JsonElement?): Pair<Int?, Map<String, JsonElement>> {
            val obj = element?.asObjectOrNull() ?: return null to emptyMap()
            val version = obj["version"]?.asIntOrNull()
            val entries = obj["entries"]?.asObjectOrNull() ?: return version to emptyMap()
            return version to entries.mapValues { it.value }
        }
    }
}

/**
 * 两库合并索引（坑 3：**新库优先**）。
 *
 * [get] 给出优先级最高的那一条（上游 `dbGetWithLegacy` 语义）；[all] 给出**同一逻辑键的全部来源**。
 *
 * 为什么还要 [all]：上游的「新键优先」是**整键替换**——新库里只要存在 `characters`，
 * 旧库的 `silly_tavern_characters` 就整体不看。对数组型记录（角色/人设）那样做**会丢整张角色卡**，
 * 而角色卡带 uuid、身份明确，合并是安全的。于是策略定为：
 * **有稳定身份的记录按身份合并（新库版本优先），其余键仍走整键优先并留下「被遮蔽」的可见记录。**
 */
class LegacyIndex private constructor(
    private val entries: Map<String, List<Entry>>,
) {

    /** [fromNewDb] 供报告使用：让「这条数据是从旧库捞回来的」可见。 */
    data class Entry(val rawKey: String, val value: JsonElement, val fromNewDb: Boolean)

    val size: Int get() = entries.size

    fun get(logicalKey: String): Entry? = entries[logicalKey]?.firstOrNull()

    fun value(logicalKey: String): JsonElement? = get(logicalKey)?.value

    /** 同一逻辑键的所有来源，**新库在前**（顺序确定 → 合并结果可复现）。 */
    fun all(logicalKey: String): List<Entry> = entries[logicalKey].orEmpty()

    fun scoped(namespace: String, scope: ScopeId): Entry? = get(LegacyKeys.scopedLogical(namespace, scope))

    /** 遍历带作用域的键，交出 (namespace, scopeId, entry)。非法作用域按 null 交出，由调用方计入跳过。 */
    fun forEachScoped(namespace: String, onEach: (ScopeId?, String, Entry) -> Unit) {
        val head = namespace + "_"
        for ((logical, bucket) in entries) {
            if (!logical.startsWith(head) || logical.length == head.length) continue
            onEach(LegacyKeys.parseScope(logical.substring(head.length)), logical, bucket.first())
        }
    }

    fun keys(): Set<String> = entries.keys

    companion object {
        fun of(main: Map<String, JsonElement>, legacy: Map<String, JsonElement>): LegacyIndex {
            val buckets = LinkedHashMap<String, MutableList<Entry>>()
            // 新库先入。
            // 注意：**不做 rawKey 去重**——两个库里都可能有 `silly_tavern_characters`
            // （旧前缀可以同时存在于新旧两个库），它们是**两份不同的数据**，去重会把旧库那份吞掉。
            for ((raw, value) in main) {
                buckets.getOrPut(LegacyKeys.logicalKey(raw)) { mutableListOf() }
                    .add(Entry(raw, value, fromNewDb = true))
            }
            for ((raw, value) in legacy) {
                buckets.getOrPut(LegacyKeys.logicalKey(raw)) { mutableListOf() }
                    .add(Entry(raw, value, fromNewDb = false))
            }
            return LegacyIndex(buckets.mapValues { it.value.toList() })
        }
    }
}

// ---- JsonElement 取值小工具（DOM API，不需要 @Serializable 编译器插件）----
//
// 为什么用 DOM 而不是数据类反序列化：kotlinx-serialization 在本项目**只装了运行时**
// （AGP 9 内置 Kotlin，刻意未引 serialization 编译器插件，见 libs.versions.toml 注释）。
// 迁移器只需要读+搬运字段，DOM 足够且零构建改动。

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.asArrayOrNull(): List<JsonElement>? =
    runCatching { jsonArray }.getOrNull()

internal fun JsonElement.asStringOrNull(): String? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

internal fun JsonElement.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.intOrNull

internal fun JsonElement.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull

internal fun JsonObject.string(field: String): String? = this[field]?.asStringOrNull()

internal fun JsonObject.array(field: String): List<JsonElement> = this[field]?.asArrayOrNull() ?: emptyList()

internal fun JsonObject.intOf(field: String): Int? = this[field]?.asIntOrNull()

internal fun JsonObject.longOf(field: String): Long? = this[field]?.asLongOrNull()

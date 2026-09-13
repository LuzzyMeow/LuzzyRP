package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 条目在**界面上的位置**：归属 + 该归属数组内的下标。
 *
 * 归属只有两级（全局 / 绑定当前角色），与存储的两个位置一一对应：
 * 全局 → `records(kind=global_worldinfo)`；角色 → 角色卡 payload 里的 `worldInfo` 数组。
 * 下标即身份（旧数据里本来就没有稳定 id）。
 */
data class EntryRef(val scope: WorldScope, val slot: Int)

/**
 * 世界书数组级操作（**纯函数**，不碰 Room / 不碰 Android）。
 *
 * 单测拿真实夹具的 payload 直接喂进来就能断言——不必起模拟器。
 * 写入纪律（计划 §2.4）都在这里体现：
 * - **顺序即身份**：新增/删除/排序都表现为「重写整个数组」，不做按名字匹配；
 * - **跨桶移动是一次操作**：改归属 = 从源数组移除 + **追加到目标数组末尾**；
 * - **未编辑的字段不动**：只有「全字段保存」走 [WorldEntry.mergeInto]（patch-merge），
 *   启停这种单点改动只补一个键（[setEnabled]）——不顺手把 payload 重排成规范形态。
 */
object WorldBookOps {

    /** 操作的输入：两张数组（无角色时 `character` 传空列表）。 */
    data class Buckets(
        val global: List<JsonElement> = emptyList(),
        val character: List<JsonElement> = emptyList(),
    )

    /**
     * **有效归属**：与上游合并视图同规则——`scope == 'global'` 或名字属于
     * [WorldEntry.SYSTEM_NAMES] 的条目，无论物理存在哪个桶，都按**全局**展示。
     *
     * 这样「角色卡里躺着一条 scope=global 的条目」不会在界面上被归错组；用户下次保存它时，
     * 归属会按选定值落回正确的桶（上游也是「保存时才按 scope 拆桶」）。
     */
    fun effectiveScope(element: JsonElement): WorldScope {
        val entry = WorldEntry.from(element)
        return if (entry.scope == WorldScope.Global || entry.comment in WorldEntry.SYSTEM_NAMES) {
            WorldScope.Global
        } else {
            WorldScope.Character
        }
    }

    /** 把每个桶里的条目按**有效归属**重新分组（用于展示）。 */
    fun groupByEffectiveScope(buckets: Buckets): Map<WorldScope, List<JsonElement>> {
        val global = mutableListOf<JsonElement>()
        val character = mutableListOf<JsonElement>()
        buckets.global.forEach { if (effectiveScope(it) == WorldScope.Global) global += it else character += it }
        buckets.character.forEach { if (effectiveScope(it) == WorldScope.Global) global += it else character += it }
        return mapOf(WorldScope.Global to global, WorldScope.Character to character)
    }

    /**
     * 新增或保存一条。
     *
     * @param ref `null` = 新增（追加到 [WorldEntry.scope] 指定的桶末尾）；
     *            非 null = 覆盖 [ref] 处的那条，若归属变了则**跨桶移动**（目标末尾）。
     */
    fun upsert(
        buckets: Buckets,
        ref: EntryRef?,
        entry: WorldEntry,
    ): Buckets {
        val global = buckets.global.toMutableList()
        val character = buckets.character.toMutableList()
        val original = ref?.let { elementAt(global, character, it) }
        val payload = entry.mergeInto(original ?: JsonObject(emptyMap()))

        if (ref == null) {
            target(global, character, entry.scope) += payload
            return Buckets(global, character)
        }
        if (ref.scope == entry.scope) {
            target(global, character, entry.scope)[ref.slot] = payload
            return Buckets(global, character)
        }
        source(global, character, ref.scope).removeAt(ref.slot)
        target(global, character, entry.scope) += payload
        return Buckets(global, character)
    }

    /**
     * 启停：**只**改写 `enabled` 一个键。
     *
     * 必须同时删掉 `disable` / `disabled` 别名——上游按 `enabled && !disable` 读，
     * 留着别名会把刚打开的开关又读成关（改了没生效）。
     */
    fun setEnabled(buckets: Buckets, ref: EntryRef, enabled: Boolean): Buckets =
        mapAt(buckets, ref) { element ->
            val base = WorldEntry.flattened(element).toMutableMap()
            base.remove("disable")
            base.remove("disabled")
            base["enabled"] = JsonPrimitive(enabled)
            JsonObject(base)
        }

    /** 删除一条。 */
    fun remove(buckets: Buckets, ref: EntryRef): Buckets = mapAt(buckets, ref) { null }

    /** 上移/下移一格（越界返回原样，不报错——界面上的按钮本就不该越界）。 */
    fun move(buckets: Buckets, ref: EntryRef, delta: Int): Buckets {
        val list = source(buckets.global.toMutableList(), buckets.character.toMutableList(), ref.scope)
        val target = ref.slot + delta
        if (ref.slot !in list.indices || target !in list.indices) return buckets
        val moved = list.removeAt(ref.slot)
        list.add(target, moved)
        return when (ref.scope) {
            WorldScope.Global -> buckets.copy(global = list)
            WorldScope.Character -> buckets.copy(character = list)
        }
    }

    // ------------------------------------------------------------------ 内部

    private fun source(
        global: MutableList<JsonElement>,
        character: MutableList<JsonElement>,
        scope: WorldScope,
    ): MutableList<JsonElement> = if (scope == WorldScope.Global) global else character

    private fun target(
        global: MutableList<JsonElement>,
        character: MutableList<JsonElement>,
        scope: WorldScope,
    ): MutableList<JsonElement> = source(global, character, scope)

    private fun elementAt(
        global: List<JsonElement>,
        character: List<JsonElement>,
        ref: EntryRef,
    ): JsonElement? = source(global.toMutableList(), character.toMutableList(), ref.scope).getOrNull(ref.slot)

    /** 对 [ref] 处的那条做一次替换（返回 null 表示删除）。越界时原样返回。 */
    private fun mapAt(buckets: Buckets, ref: EntryRef, transform: (JsonElement) -> JsonElement?): Buckets {
        val global = buckets.global.toMutableList()
        val character = buckets.character.toMutableList()
        val list = source(global, character, ref.scope)
        if (ref.slot !in list.indices) return buckets
        val replaced = transform(list[ref.slot])
        if (replaced == null) list.removeAt(ref.slot) else list[ref.slot] = replaced
        return Buckets(global, character)
    }
}

package com.luzzymeow.luzzyrp.data.preset

import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.serialization.json.JsonElement

/**
 * 预设的读写门面（W1）。存储是**一个扁平数组**（`records(kind=presets, owner="")`），
 * 顺序即注入顺序——所以每次操作都是「整数组重写」，一次事务。
 *
 * `index` 就是身份（旧数据没有 id），界面拿到 [PresetRow.index] 后原样传回来寻址。
 */
class PresetRepository(private val store: LuzzyStore) {

    suspend fun all(): List<PresetRow> = rows(store.records(LuzzyStore.RECORD_PRESETS))

    /** 聊天侧面板用：只列**开启**的条目（上游组装请求时 `enabled=false` 直接丢弃）。 */
    suspend fun enabled(): List<PresetRow> = all().filter { it.entry.enabled }

    suspend fun upsert(ref: Int?, entry: PresetEntry) =
        mutate { PresetOps.upsert(it, ref, entry) }

    suspend fun setEnabled(index: Int, enabled: Boolean) =
        mutate { PresetOps.setEnabled(it, index, enabled) }

    suspend fun remove(index: Int) = mutate { PresetOps.remove(it, index) }

    /** 上移/下移一格（改的是注入顺序）。 */
    suspend fun move(index: Int, delta: Int) = mutate { PresetOps.move(it, index, delta) }

    private fun rows(elements: List<JsonElement>): List<PresetRow> =
        elements.mapIndexed { index, element -> PresetRow(index, PresetEntry.from(element)) }

    private suspend fun mutate(op: (List<JsonElement>) -> List<JsonElement>) = store.transaction {
        val before = store.records(LuzzyStore.RECORD_PRESETS)
        val after = op(before)
        if (after != before) {
            store.replaceRecords(LuzzyStore.RECORD_PRESETS, "", after, System.currentTimeMillis())
        }
    }
}

/** 界面用的一行：下标（身份）+ 类型化视图。 */
data class PresetRow(val index: Int, val entry: PresetEntry)

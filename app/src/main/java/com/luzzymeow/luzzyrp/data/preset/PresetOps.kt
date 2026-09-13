package com.luzzymeow.luzzyrp.data.preset

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 预设数组级操作（**纯函数**，可 JVM 单测）。
 *
 * 与 [com.luzzymeow.luzzyrp.data.world.WorldBookOps] 同纪律：**下标即身份**，
 * 新增/删除/排序都表现为「重写整个数组」；顺序即注入顺序，所以 `move` 是真的改语义。
 *
 * 预设只有一组（没有归属维度），因此引用就是一个下标；`upsert(ref = null)` 表示新增（追加到末尾）。
 */
object PresetOps {

    fun upsert(list: List<JsonElement>, ref: Int?, entry: PresetEntry): List<JsonElement> {
        val result = list.toMutableList()
        if (ref == null) {
            result += entry.mergeInto(JsonObject(emptyMap()))
        } else if (ref in result.indices) {
            result[ref] = entry.mergeInto(result[ref])
        }
        return result
    }

    /**
     * 启停：只改写 `enabled` 一个键（不顺手把 payload 重排成规范形态）。
     *
     * 预设这边**不需要**清理别名：上游读 `enabled !== false`，没有 `disable` 那套取反写法，
     * 也不存在「别名优先于规范名」的读取顺序（对比世界书的 `use_regex`，那里必须删别名）。
     */
    fun setEnabled(list: List<JsonElement>, index: Int, enabled: Boolean): List<JsonElement> {
        if (index !in list.indices) return list
        val result = list.toMutableList()
        val base = (result[index] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        base["enabled"] = JsonPrimitive(enabled)
        result[index] = JsonObject(base)
        return result
    }

    fun remove(list: List<JsonElement>, index: Int): List<JsonElement> {
        if (index !in list.indices) return list
        return list.toMutableList().apply { removeAt(index) }
    }

    /** 上移/下移一格（越界原样返回——按钮本就不该越界）。 */
    fun move(list: List<JsonElement>, index: Int, delta: Int): List<JsonElement> {
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return list
        return list.toMutableList().apply { add(target, removeAt(index)) }
    }
}

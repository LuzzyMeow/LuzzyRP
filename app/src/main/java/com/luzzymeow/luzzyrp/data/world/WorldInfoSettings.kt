package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 全局世界书设置（上游 `worldInfoSettings`，默认 `scanDepth=2, maxDepth=0`）。
 *
 * `maxDepth=0` 表示**不限制**（不是「扫 0 条」）——上游语义如此，界面上显示为「不限制」。
 * 读取时按界面滑杆的量程夹取（0-20 / 0-50）：老数据里若有越界值，滑杆不会因此崩掉。
 *
 * **单独成文件**（而不是留在 `WorldBookRepository.kt` 里）：请求组装是纯 Kotlin 层，
 * 不想为了用一个设置类而把 Android 依赖拖进来（`WorldBookRepository` 依赖 Room）。
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

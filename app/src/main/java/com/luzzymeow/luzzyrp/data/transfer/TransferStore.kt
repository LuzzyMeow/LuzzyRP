package com.luzzymeow.luzzyrp.data.transfer

import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.serialization.json.JsonElement

/**
 * D2 导入导出的**落库半**（薄壳：文本格式与校验在 [TransferFormat]，这里只管「哪个桶」与「怎么回填」）。
 *
 * ## 覆盖语义（有意与迁移器不同）
 *
 * 导入 = **这次导入就是当前状态**（整组覆盖 / 同 uuid 覆盖）——用户在文件管理器里选了这个文件，
 * 就是要让设备变成文件里的样子；这与初次迁移的「uuid 合并保旧记录」是两种意图，不共用。
 */
class TransferStore(private val store: LuzzyStore) {

    // ---------------- 导出 ----------------

    suspend fun exportPresets(): String =
        TransferFormat.exportRecords(store.records(LuzzyStore.RECORD_PRESETS))

    /**
     * 世界书导出取**生效桶**（W0 裁决，上游 `app.js:1906-1914` 同口径）：全局桶非空取全局；
     * 否则回落旧桶（迁移器把两键并存时的旧键也搬了进来，但上游语义是忽略它）。
     * 角色绑定的世界书住在角色卡 payload 里，随角色卡导出，不在这里。
     */
    suspend fun exportWorldInfo(): String {
        val global = store.records(LuzzyStore.RECORD_GLOBAL_WORLDINFO)
        val effective = global.ifEmpty { store.records(LuzzyStore.RECORD_WORLDINFO) }
        return TransferFormat.exportRecords(effective)
    }

    suspend fun exportCharacters(): String = TransferFormat.exportCharacters(store.characters())

    // ---------------- 导入 ----------------

    /** 导入 = 整组覆盖。返回导入条数。 */
    suspend fun importPresets(text: String): Int {
        val payloads = TransferFormat.importRecords(text)
        store.replaceRecords(LuzzyStore.RECORD_PRESETS, "", payloads)
        return payloads.size
    }

    /** 世界书导入固定回写全局桶（现行生效口径；旧桶是 W0 裁决的幽灵数据，不回写）。返回导入条数。 */
    suspend fun importWorldInfo(text: String): Int {
        val payloads = TransferFormat.importRecords(text)
        store.replaceRecords(LuzzyStore.RECORD_GLOBAL_WORLDINFO, "", payloads)
        return payloads.size
    }

    /**
     * 角色卡导入：同 uuid 覆盖（**保留**该卡已有的头像文件与创建时间——payload 换了，
     * 但头像文件是按 uuid 落盘的资产，名字/正文变了不等于头像变了），缺 uuid 的新建。
     * 返回 (新增, 覆盖)。
     */
    suspend fun importCharacters(text: String): Pair<Int, Int> {
        var created = 0
        var replaced = 0
        TransferFormat.importCharacters(text).forEach { incoming ->
            val existing = store.character(incoming.uuid)
            store.upsertCharacter(
                if (existing == null) incoming
                else existing.copy(name = incoming.name, payload = incoming.payload),
            )
            if (existing == null) created++ else replaced++
        }
        return created to replaced
    }

    /** 预设导出（供 UI 取数时的类型提示，避免 UI 直接碰 JsonElement）。 */
    suspend fun presetCount(): Int = store.records(LuzzyStore.RECORD_PRESETS).size

    suspend fun worldInfoCount(): Int {
        val global = store.records(LuzzyStore.RECORD_GLOBAL_WORLDINFO)
        return global.ifEmpty { store.records(LuzzyStore.RECORD_WORLDINFO) }.size
    }

    suspend fun characterCount(): Int = store.characters().size
}

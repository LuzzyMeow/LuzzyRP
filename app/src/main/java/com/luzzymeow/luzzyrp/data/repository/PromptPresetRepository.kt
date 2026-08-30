package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.common.JsonInstant
import com.luzzymeow.luzzyrp.core.model.PresetEntry
import com.luzzymeow.luzzyrp.core.model.PromptPreset
import com.luzzymeow.luzzyrp.data.db.dao.PromptPresetDao
import com.luzzymeow.luzzyrp.data.db.entity.PromptPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * 提示词预设仓库（7.2：菜单「预设」）。
 * 预设 = 名称 + 提示词条目列表；条目独立启停/编辑/复制；
 * 启用的预设经 PromptAssembler 注入（相对位置 → system 层；@Depth → 消息列）。
 */
class PromptPresetRepository(private val presetDao: PromptPresetDao) {

    fun observeAll(): Flow<List<PromptPreset>> = presetDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): PromptPreset? = presetDao.getById(id)?.toDomain()

    suspend fun save(preset: PromptPreset): PromptPreset {
        val toSave = (
            if (preset.id.isBlank()) preset.copy(id = UUID.randomUUID().toString()) else preset
            ).copy(updatedAt = System.currentTimeMillis())
        presetDao.upsert(toSave.toEntity())
        return toSave
    }

    suspend fun delete(id: String) = presetDao.delete(id)

    /** 复制预设（含条目），名称追加「副本」。 */
    suspend fun duplicate(preset: PromptPreset): PromptPreset {
        val copy = preset.copy(
            id = UUID.randomUUID().toString(),
            name = preset.name + " 副本",
            readonly = false,
            builtin = false,
            entries = preset.entries.map { it.copy(id = UUID.randomUUID().toString()) },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        presetDao.upsert(copy.toEntity())
        return copy
    }

    private fun PromptPresetEntity.toDomain(): PromptPreset = PromptPreset(
        id = id,
        name = name,
        entries = runCatching {
            JsonInstant.decodeFromString(ListSerializer(PresetEntry.serializer()), entriesJson)
        }.getOrDefault(emptyList()),
        builtin = builtin,
        readonly = readonly,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun PromptPreset.toEntity(): PromptPresetEntity = PromptPresetEntity(
        id = id,
        name = name,
        entriesJson = JsonInstant.encodeToString(ListSerializer(PresetEntry.serializer()), entries),
        builtin = builtin,
        readonly = readonly,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

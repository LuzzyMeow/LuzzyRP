package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.model.Favorite
import com.luzzymeow.luzzyrp.core.model.RegexScript
import com.luzzymeow.luzzyrp.data.db.dao.FavoriteDao
import com.luzzymeow.luzzyrp.data.db.dao.RegexDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 正则脚本仓库（五作用域 × 四时机 × 深度区间的存储面；执行在 data/ai/transform）。 */
class RegexRepository(private val regexDao: RegexDao) {

    fun observeAll(): Flow<List<RegexScript>> = regexDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<RegexScript> = regexDao.getAll().map { it.toDomain() }

    suspend fun save(script: RegexScript): RegexScript {
        val toSave = if (script.id.isBlank()) script.copy(id = UUID.randomUUID().toString()) else script
        regexDao.upsert(toSave.toEntity())
        return toSave
    }

    suspend fun delete(id: String) = regexDao.delete(id)

    /** 首次启动植入内置预设（已存在则跳过）。 */
    suspend fun ensurePresets() {
        if (regexDao.presetCount() > 0) return
        com.luzzymeow.luzzyrp.core.model.RegexPresets.ALL.forEach { regexDao.upsert(it.toEntity()) }
    }
}

/** 收藏仓库。 */
class FavoriteRepository(private val favoriteDao: FavoriteDao) {

    fun observeAll(): Flow<List<Favorite>> = favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(favorite: Favorite): Favorite {
        val toSave = favorite.copy(id = UUID.randomUUID().toString(), createdAt = kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis()))
        favoriteDao.upsert(toSave.toEntity())
        return toSave
    }

    suspend fun removeByMessage(conversationId: String, messageId: String) {
        favoriteDao.getByMessage(conversationId, messageId)?.let { favoriteDao.delete(it.id) }
    }

    suspend fun delete(id: String) = favoriteDao.delete(id)
}

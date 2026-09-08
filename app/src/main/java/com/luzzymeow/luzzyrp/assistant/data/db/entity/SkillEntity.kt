package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 技能（PLAN v1.5.0 §4.1 表 skill；需求 4）。
 *
 * 正文为 Markdown（source: builtin | file | url）。enabledGlobal 为全局开关，
 * 与助手的绑定开关见 SkillBindingEntity。
 */
@Entity(tableName = "skill")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** Markdown 技能正文 */
    val body: String,
    /** builtin | file | url */
    val source: String,
    val enabledGlobal: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
    }
}

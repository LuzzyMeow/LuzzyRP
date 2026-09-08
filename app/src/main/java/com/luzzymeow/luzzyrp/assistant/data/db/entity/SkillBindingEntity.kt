package com.luzzymeow.luzzyrp.assistant.data.db.entity

import androidx.room.Entity

/**
 * 技能与助手的绑定（PLAN v1.5.0 §4.1 表 skill_binding，复合主键 skillId + assistantId）。
 */
@Entity(tableName = "skill_binding", primaryKeys = ["skillId", "assistantId"])
data class SkillBindingEntity(
    val skillId: String,
    val assistantId: String,
    val enabled: Boolean,
)

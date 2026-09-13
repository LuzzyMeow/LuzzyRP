package com.luzzymeow.luzzyrp.data.preset

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.luzzymeow.luzzyrp.data.world.asBool
import com.luzzymeow.luzzyrp.data.world.asText

/**
 * 预设条目的**类型化视图**（纯 Kotlin）。
 *
 * 上游模型极简（`app.js:974-989`）：一个**扁平数组**，元素只有
 * `{name, role, content, enabled}`——**没有**预设集/分组/「当前激活」的概念，
 * **数组顺序就是注入顺序**。所以这里不发明 id 或排序字段。
 *
 * 读取（`normalizePreset`）：
 * - `name` 缺省为上游的 `'New Preset'`——**我们不用它**：界面显示 [displayName] 的「未命名条目」，
 *   存进去的还是空串（不替用户编造英文名字）；
 * - `role` 认 `role | presetRole | type`，白名单外一律回落 `system`；
 * - `enabled` 只在**显式 false** 时算关。
 */
data class PresetEntry(
    val name: String = "",
    val role: PresetRole = PresetRole.System,
    val content: String = "",
    val enabled: Boolean = true,
) {

    val displayName: String get() = name.ifBlank { "未命名条目" }

    /** 列表行的支撑文本：正文首行（空正文给出可读的说明，而不是空白行）。 */
    val summary: String
        get() = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: if (content.isBlank()) "（正文为空）" else content.take(40)

    /** 正文为空且开启 → 上游在组装请求时会丢弃（`app.js:4534-4536`）。界面如实提示。 */
    val ignoredByAssembly: Boolean get() = enabled && content.isBlank()

    fun mergeInto(original: JsonElement): JsonObject {
        val base = (original as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        base["name"] = JsonPrimitive(name)
        base["role"] = JsonPrimitive(role.id)
        base["content"] = JsonPrimitive(content)
        base["enabled"] = JsonPrimitive(enabled)
        return JsonObject(base)
    }

    companion object {
        fun from(element: JsonElement): PresetEntry {
            val obj = element as? JsonObject ?: JsonObject(emptyMap())
            return PresetEntry(
                name = obj["name"].asText(""),
                role = PresetRole.fromId(
                    // 上游顺序：role 优先，再 presetRole、type
                    (obj["role"] ?: obj["presetRole"] ?: obj["type"]).asText(""),
                ),
                content = obj["content"].asText(""),
                enabled = obj["enabled"].asBool(true),
            )
        }
    }
}

/** 注入角色（上游 `normalizePresetRole` 的白名单）。 */
enum class PresetRole(val id: String, val label: String) {
    /** 进 system 提示词（拼装顺序见计划附录 A）。 */
    System("system", "系统提示词"),

    /** 作为独立 user 消息插入（紧随首条 system 之后）。 */
    User("user", "User 消息"),

    /** 作为独立 assistant 消息插入。 */
    Assistant("assistant", "AI 消息"),
    ;

    companion object {
        fun fromId(raw: String?): PresetRole = entries.firstOrNull { it.id == raw } ?: System
    }
}

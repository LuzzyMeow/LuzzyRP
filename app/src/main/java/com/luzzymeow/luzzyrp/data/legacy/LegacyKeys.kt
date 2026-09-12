package com.luzzymeow.luzzyrp.data.legacy

/**
 * 旧存储的键命名空间与「作用域」解析（纯 Kotlin，无 Android 依赖）。
 *
 * 依据上游 `assets/js/data-services.js` 与 `data-services.js` 的 story-branches 模块：
 * - 存储前缀有两套：新库写 `rp_hub_`，更早版本写 `silly_tavern_`（且旧前缀可能仍留在新库里）；
 * - 逻辑键 = 去掉前缀后的名字（`characters`、`chat_<scope>`、`memories_<scope>` …）；
 * - **作用域拼接**：主线是**裸 `<charUuid>`**，分支是 `<charUuid>__branch__<branchId>`。
 *   这条「main = 裸 uuid」是迁移最容易写错的地方——把它写成 `<uuid>__branch__main`
 *   会造出两套互相看不见的键。
 *
 * 详见 `docs/DESIGN-migration.md` §3 与 §5（坑 1/2/3/8）。
 */
object LegacyKeys {

    const val STORAGE_PREFIX = "rp_hub_"
    const val LEGACY_STORAGE_PREFIX = "silly_tavern_"
    const val BRANCH_SEPARATOR = "__branch__"
    const val MAIN_BRANCH_ID = "main"

    /** 非作用域（全局）逻辑键。 */
    const val CHARACTERS = "characters"
    const val SETTINGS = "settings"
    const val PRESETS = "presets"
    const val REGEX = "regex"
    const val GLOBAL_REGEX = "global_regex"
    const val WORLDINFO = "worldinfo"
    const val GLOBAL_WORLDINFO = "global_worldinfo"
    const val WORLDINFO_SETTINGS = "worldinfo_settings"
    const val USER = "user"
    const val USER_PROFILES = "user_profiles"
    const val ACTIVE_PROFILE_ID = "active_profile_id"
    const val LAST_ACTIVE_CHAR = "last_active_char"
    const val MEMORY_SETTINGS = "memory_settings"
    const val ACTIVE_TOOLS = "active_tools"
    const val GLOBAL_UI_TEMPLATES = "global_ui_templates"
    const val TOKEN_USAGE_HISTORY = "token_usage_history"

    /** 带作用域的逻辑键前缀（顺序不敏感：彼此不构成前缀关系）。 */
    val SCOPED_NAMESPACES = listOf("classic_memories", "memories", "chat")

    /** 去前缀。两套前缀互不为前缀，先命中先算。 */
    fun logicalKey(raw: String): String = when {
        raw.startsWith(STORAGE_PREFIX) -> raw.substring(STORAGE_PREFIX.length)
        raw.startsWith(LEGACY_STORAGE_PREFIX) -> raw.substring(LEGACY_STORAGE_PREFIX.length)
        else -> raw
    }

    fun scopedLogical(namespace: String, scope: ScopeId): String = "${namespace}_${scope.suffix()}"

    /**
     * 解析作用域后缀。
     *
     * `"<uuid>"` → 主线；`"<uuid>__branch__<bid>"` → 分支。
     * 空段（`"__branch__x"`、`"u__branch__"`）判为非法——宁可跳过并报告，也不要造出半截 id。
     */
    fun parseScope(suffix: String): ScopeId? {
        if (suffix.isBlank()) return null
        val at = suffix.indexOf(BRANCH_SEPARATOR)
        if (at < 0) return ScopeId(suffix, MAIN_BRANCH_ID)
        val uuid = suffix.substring(0, at)
        val branchId = suffix.substring(at + BRANCH_SEPARATOR.length)
        if (uuid.isBlank() || branchId.isBlank()) return null
        return ScopeId(uuid, branchId)
    }
}

/**
 * 一个数据作用域：**角色 × 分支**（分支为主线时 [branchId] 为 `main`）。
 *
 * 这是本项目「会话」的本体——上游没有平铺的会话对象，会话就是 (角色, 分支) 这一对。
 */
data class ScopeId(val characterUuid: String, val branchId: String = LegacyKeys.MAIN_BRANCH_ID) {

    val isMain: Boolean get() = branchId == LegacyKeys.MAIN_BRANCH_ID

    /** 拼回存储后缀（主线 = 裸 uuid，**这是上游的约定**）。 */
    fun suffix(): String =
        if (isMain) characterUuid else "$characterUuid${LegacyKeys.BRANCH_SEPARATOR}$branchId"

    override fun toString(): String = suffix()
}

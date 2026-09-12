package com.luzzymeow.luzzyrp.data.store

import androidx.room.withTransaction
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 新数据层的读写门面（P4-B）。UI 只认这一层，不直接碰 Dao / Room。
 *
 * ## 两条贯穿性约定
 *
 * 1. **payload 是 JSON 文本**：写入的 [JsonElement] 都 `toString()` 落列，读的时候再 parse。
 *    键名与旧结构逐字一致 → 迁移零丢失，且日后要用新字段**不需要改表**。
 * 2. **kv 的值一律是 JSON 文本**（字符串带引号、对象是对象）。
 *    这样「这个键存的是字符串还是 JSON」不靠记忆；见 [putString] / [string]。
 *
 * ## 作用域
 *
 * 会话/记忆按 `scopeId` 分桶，取值与旧存储**完全一致**（[ScopeId.suffix]：
 * 主线是裸角色 uuid，分支是 `<uuid>__branch__<branchId>`）。刻意不做二次编码——
 * 迁移时就能原样搬运，也让两代存储的「会话」概念对得上。
 */
class LuzzyStore(private val db: LuzzyDatabase) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------- 角色

    suspend fun characters(): List<CharacterEntity> = db.characters().all()

    suspend fun character(uuid: String): CharacterEntity? = db.characters().byId(uuid)

    suspend fun upsertCharacter(entity: CharacterEntity) = db.characters().upsert(entity)

    suspend fun characterCount(): Int = db.characters().count()

    // ---------------------------------------------------------------- 分支

    suspend fun branches(characterUuid: String): List<BranchEntity> = db.branches().of(characterUuid)

    /** 分支的 `activeBranchId`（旧 `branches_<uuid>.activeBranchId`）。 */
    suspend fun activeBranchId(characterUuid: String): String? =
        db.branches().meta(characterUuid)?.activeBranchId

    /** 覆盖某角色的分支列表与该角色的当前分支（一次事务）。 */
    suspend fun replaceBranches(
        characterUuid: String,
        branches: List<BranchEntity>,
        activeBranchId: String,
        version: Int = 1,
    ) = db.withTransaction {
        db.branches().deleteOf(characterUuid)
        if (branches.isNotEmpty()) db.branches().upsertAll(branches)
        db.branches().upsertMeta(
            BranchMetaEntity(characterUuid, version, activeBranchId),
        )
    }

    // ---------------------------------------------------------------- 消息

    suspend fun messages(scope: ScopeId): List<MessageEntity> = db.messages().of(scope.suffix())

    suspend fun messageCount(scope: ScopeId): Int = db.messages().countOf(scope.suffix())

    /** 全部有消息的作用域（跨角色平铺会话总览要用）。 */
    suspend fun conversationScopes(): List<ScopeId> = db.messages().scopes().mapNotNull { raw ->
        val at = raw.indexOf("__branch__")
        if (at < 0) ScopeId(raw) else ScopeId(raw.substring(0, at), raw.substring(at + "__branch__".length))
    }

    /**
     * 追加一条消息。
     *
     * **必须由调用方给出 [sortIndex]**（= 当前条数），而不是在这里查 count：
     * 流式生成期间若一边追加一边查计数，会出现「同一条被写两次」或「跳号」。
     * 顺序是旧数据的唯一身份，宁可在调用点算准。
     */
    suspend fun appendMessage(entity: MessageEntity) = db.messages().append(entity)

    /** 覆盖一个作用域的全部消息（编辑/删除/重跑后重写该段）。 */
    suspend fun replaceMessages(scope: ScopeId, entities: List<MessageEntity>) = db.withTransaction {
        db.messages().deleteScope(scope.suffix())
        if (entities.isNotEmpty()) db.messages().upsertAll(entities)
    }

    /** 删掉 `sortIndex >= fromIndex` 的尾部（「删除此消息及之后」）。 */
    suspend fun deleteMessagesFrom(scope: ScopeId, fromIndex: Int) =
        db.messages().deleteFrom(scope.suffix(), fromIndex)

    suspend fun deleteScope(scope: ScopeId) = db.messages().deleteScope(scope.suffix())

    // ---------------------------------------------------------------- 记忆

    suspend fun memories(scope: ScopeId, kind: String): List<JsonElement> =
        db.memories().of(scope.suffix(), kind).mapNotNull { parseOrNull(it.payload) }

    suspend fun replaceMemories(scope: ScopeId, kind: String, payloads: List<JsonElement>) =
        db.withTransaction {
            db.memories().deleteGroup(scope.suffix(), kind)
            val rows = payloads.mapIndexed { index, element ->
                val obj = element as? kotlinx.serialization.json.JsonObject
                MemoryEntity(
                    scopeId = scope.suffix(),
                    kind = kind,
                    // 记忆 id 在旧数据里恒存在；缺了就按下标造一个稳定 id（不随机，保证幂等）
                    id = obj?.get("id")?.let { (it as? JsonPrimitive)?.content }
                        ?: "$kind-$index",
                    sortIndex = index,
                    turn = obj?.get("turn")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() },
                    enabled = obj?.get("enabled")?.let { (it as? JsonPrimitive)?.content != "false" } ?: true,
                    payload = element.toString(),
                )
            }
            if (rows.isNotEmpty()) db.memories().upsertAll(rows)
        }

    // ---------------------------------------------------------------- 其余集合

    suspend fun records(kind: String, owner: String = ""): List<JsonElement> =
        db.records().of(kind, owner).mapNotNull { parseOrNull(it.payload) }

    suspend fun recordsOfKind(kind: String): List<RecordEntity> = db.records().ofKind(kind)

    suspend fun replaceRecords(kind: String, owner: String, payloads: List<JsonElement>) =
        db.withTransaction {
            db.records().deleteGroup(kind, owner)
            val rows = payloads.mapIndexed { index, element ->
                RecordEntity(
                    kind = kind,
                    owner = owner,
                    slot = index,
                    updatedAt = 0L,
                    payload = element.toString(),
                )
            }
            if (rows.isNotEmpty()) db.records().upsertAll(rows)
        }

    // ---------------------------------------------------------------- 键值

    suspend fun putJson(key: String, value: JsonElement?) = db.kv().put(
        KvEntity(key, value?.toString() ?: JsonNull.toString()),
    )

    suspend fun json(key: String): JsonElement? = db.kv().get(key)?.let { parseOrNull(it) }

    /** 存字符串（内部按 JSON 字符串编码，读用 [string]）。 */
    suspend fun putString(key: String, value: String?) = db.kv().put(
        KvEntity(key, value?.let { JsonPrimitive(it).toString() } ?: JsonNull.toString()),
    )

    suspend fun string(key: String): String? =
        (json(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    suspend fun remove(key: String) = db.kv().remove(key)

    suspend fun keys(): List<String> = db.kv().keys()

    // ---------------------------------------------------------------- 附件

    suspend fun attachments(): List<AttachmentEntity> = db.attachments().all()

    suspend fun upsertAttachments(entities: List<AttachmentEntity>) =
        if (entities.isEmpty()) Unit else db.attachments().upsertAll(entities)

    // ---------------------------------------------------------------- 事务

    /** 让调用方把多步操作包在一个事务里（迁移导入用）。 */
    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    /**
     * 清空全部**内容**表（迁移导入前调用）。
     *
     * 刻意**不清 `kv`**：迁移完成标记、用户设置、当前角色选择都在那里——
     * 迁移要能反复导入而不丢设置。
     */
    suspend fun clearContentTables() {
        db.messages().clear()
        db.memories().clear()
        db.records().clear()
        db.attachments().clear()
        db.branches().clearMeta()
        db.branches().clear()
        db.characters().clear()
    }

    private fun parseOrNull(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()

    companion object {
        /** kv 键：迁移完成标记（P4-A 的 G4「中断可续」靠它）。 */
        const val KEY_LEGACY_MIGRATED = "legacy.migrated"
        const val KEY_LEGACY_MIGRATED_AT = "legacy.migratedAt"
        const val KEY_LEGACY_MIGRATION_COUNTS = "legacy.migrationCounts"
        const val KEY_ACTIVE_CHARACTER = "active.characterUuid"
        const val KEY_ACTIVE_PROFILE = "active.profileUuid"
        const val KEY_SETTINGS = "settings"
        const val KEY_MEMORY_SETTINGS = "memorySettings"
        const val KEY_ACTIVE_TOOLS = "activeTools"
        const val KEY_WORLDINFO_SETTINGS = "worldinfoSettings"
        const val KEY_GLOBAL_UI_TEMPLATES = "globalUiTemplates"
        const val KEY_USER = "user"

        /** records 表的 kind 取值。 */
        const val RECORD_WORLDINFO = "worldinfo"
        const val RECORD_GLOBAL_WORLDINFO = "global_worldinfo"
        const val RECORD_REGEX = "regex"
        const val RECORD_GLOBAL_REGEX = "global_regex"
        const val RECORD_PRESETS = "presets"
        const val RECORD_USAGE = "token_usage_history"
        const val RECORD_PROFILE = "profile"

        const val MEMORY_VECTOR = "vector"
        const val MEMORY_CLASSIC = "classic"
    }
}

package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.UUID

/**
 * 旧库 → [MigratedData] 的迁移器。**纯 Kotlin、无 Android 依赖、无 IO**。
 *
 * 这条边界是刻意的：夹具（真实旧数据导出）直接喂进来就能断言，**不需要模拟器**，
 * 于是「12 条坑逐条处理」才真的是可回归的单测，而不是发版前的人工走查。
 *
 * 坑位与处置的对照见 `docs/DESIGN-migration.md` §5；下面每个 `// 坑 N` 注释指回那里。
 */
object LegacyMigrator {

    data class Options(
        /** 内联 base64 超过这个长度就解出来落文件（坑 6）。小头像留内联，免得为几 KB 建文件。 */
        val inlineBase64Threshold: Int = 4096,
        val extractAvatars: Boolean = true,
        val extractMessageAttachments: Boolean = true,
        val avatarDir: String = "assets/avatars",
        val attachmentDir: String = "assets/attachments",
    )

    // ------------------------------------------------------------------ 入口

    fun migrate(db: LegacyDb, options: Options = Options()): MigratedData {
        val index = LegacyIndex.of(db.main, db.legacy)
        val ctx = Context(index, options)
        if (db.isEmpty) return ctx.empty()

        val rawCharacters = ctx.identityMergedArray(LegacyKeys.CHARACTERS, "uuid")
        val characters = rawCharacters.mapIndexedNotNull { i, el -> ctx.characterOf(i, el) }

        val branchesByCharacter = LinkedHashMap<String, List<MigratedBranch>>()
        val activeBranchByCharacter = LinkedHashMap<String, String>()
        for (character in characters) {
            val (branches, active) = ctx.branchesOf(character)
            branchesByCharacter[character.uuid] = branches
            activeBranchByCharacter[character.uuid] = active
        }

        val conversations = ctx.conversationsOf(characters, branchesByCharacter)
        val vectorMemories = ctx.scopedPayloads("memories")
        val classicMemories = ctx.scopedPayloads("classic_memories")

        // 坑 10：记忆指向的角色不存在 → 不能静默丢。补一个占位角色把它接住。
        val orphanUuids = (vectorMemories.keys + classicMemories.keys)
            .map { it.characterUuid }
            .filter { uuid -> characters.none { it.uuid == uuid } }
            .distinct()
            .sorted()
        var allCharacters = characters
        if (orphanUuids.isNotEmpty()) {
            allCharacters = characters + orphanUuids.map { ctx.orphanCharacter(it) }
            for (uuid in orphanUuids) {
                branchesByCharacter[uuid] = listOf(ctx.synthesizedMainBranch(createdAt = 0L))
                activeBranchByCharacter[uuid] = LegacyKeys.MAIN_BRANCH_ID
                ctx.note("孤立作用域 $uuid 的记忆没有宿主角色，已补占位角色接住（未丢弃）")
            }
        }

        val memorySettings = ctx.normalizedMemorySettings()

        // 人设 / 当前用户的头像同样是内联 base64（坑 6），一并抽文件
        val profiles = ctx.identityMergedArray(LegacyKeys.USER_PROFILES, "uuid").map { profile ->
            val uuid = (profile as? JsonObject)?.string("uuid").orEmpty()
            ctx.extractAvatarIn(profile, "${options.avatarDir}/profile-$uuid") ?: profile
        }
        val user = ctx.extractAvatarIn(index.value(LegacyKeys.USER), "${options.avatarDir}/user-self")

        return MigratedData(
            characters = allCharacters,
            branchesByCharacter = branchesByCharacter,
            activeBranchByCharacter = activeBranchByCharacter,
            conversations = conversations,
            vectorMemories = vectorMemories,
            classicMemories = classicMemories,
            worldEntries = ctx.payloadArrayOf(LegacyKeys.WORLDINFO),
            globalWorldEntries = ctx.payloadArrayOf(LegacyKeys.GLOBAL_WORLDINFO),
            regexes = ctx.payloadArrayOf(LegacyKeys.REGEX),
            globalRegexes = ctx.payloadArrayOf(LegacyKeys.GLOBAL_REGEX),
            presets = ctx.payloadArrayOf(LegacyKeys.PRESETS),
            usage = ctx.payloadArrayOf(LegacyKeys.TOKEN_USAGE_HISTORY),
            profiles = profiles,
            activeProfileId = ctx.activeProfileId(profiles),
            activeCharacterUuid = ctx.activeCharacterUuid(allCharacters),
            settings = index.value(LegacyKeys.SETTINGS),
            memorySettings = memorySettings,
            activeTools = index.value(LegacyKeys.ACTIVE_TOOLS),
            worldInfoSettings = index.value(LegacyKeys.WORLDINFO_SETTINGS),
            globalUiTemplates = index.value(LegacyKeys.GLOBAL_UI_TEMPLATES),
            user = user,
            assets = ctx.assets.toList(),
            skipped = ctx.skipped.toList(),
            notes = ctx.notes.toList(),
        )
    }

    /**
     * 内容哈希生成稳定 uuid（v3/MD5 形态，标准库 `nameUUIDFromBytes`）。
     *
     * 只在**原数据没有 uuid** 时用。用内容哈希而不是随机 UUID 是为了幂等（G2）：
     * 同一份旧数据迁移两次必须得到同一个 id，否则第二次会变成「两个角色」。
     */
    fun deterministicUuid(vararg parts: String): String =
        UUID.nameUUIDFromBytes(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8)).toString()

    /** 解析 `data:<mime>;base64,<payload>`；不是内联 base64 就返回 null（已是路径的情况要原样保留）。 */
    fun parseDataUrl(value: String): Pair<String, ByteArray>? {
        if (!value.startsWith("data:")) return null
        val comma = value.indexOf(',')
        if (comma < 0) return null
        val meta = value.substring(5, comma)
        if (!meta.endsWith(";base64")) return null
        val mime = meta.removeSuffix(";base64").ifBlank { "application/octet-stream" }
        val bytes = runCatching { Base64.getDecoder().decode(value.substring(comma + 1)) }.getOrNull()
            ?: return null
        return mime to bytes
    }

    internal fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/avif" -> "avif"
        else -> "bin"
    }

    /** `emptyTurns` 的规范键：`<作用域后缀>:<模式>`（模式缺失时不留尾冒号）。 */
    internal fun canonicalEmptyTurnsKey(scope: ScopeId, mode: String): String =
        if (mode.isEmpty()) scope.suffix() else "${scope.suffix()}:$mode"

    // ------------------------------------------------------------------ 上下文（收集器）

    private class Context(val index: LegacyIndex, val options: Options) {
        val assets = mutableListOf<ExtractedAsset>()
        val skipped = mutableListOf<SkippedRecord>()
        val notes = mutableListOf<String>()

        /** 会话键占用表：防止 `chat_<n>` 数字回落被两个角色重复认领（坑 2）。 */
        private val claimedConversationKeys = mutableSetOf<String>()

        fun note(message: String) {
            if (!notes.contains(message)) notes += message
        }

        fun skip(key: String, reason: String) {
            skipped += SkippedRecord(key, reason)
        }

        fun empty() = MigratedData(
            characters = emptyList(),
            branchesByCharacter = emptyMap(),
            activeBranchByCharacter = emptyMap(),
            conversations = emptyList(),
            vectorMemories = emptyMap(),
            classicMemories = emptyMap(),
            worldEntries = emptyList(),
            globalWorldEntries = emptyList(),
            regexes = emptyList(),
            globalRegexes = emptyList(),
            presets = emptyList(),
            usage = emptyList(),
            profiles = emptyList(),
            activeProfileId = null,
            activeCharacterUuid = null,
            settings = null,
            memorySettings = null,
            activeTools = null,
            worldInfoSettings = null,
            globalUiTemplates = null,
            user = null,
            assets = emptyList(),
            skipped = emptyList(),
            notes = emptyList(),
        )

        /**
         * **按身份字段合并**同一逻辑键的所有来源（坑 3 的强化）。
         *
         * 上游「新键优先」是整键替换，会把旧库整张角色卡丢掉。角色卡/人设带 uuid、身份明确，
         * 故这里改为逐条合并：**同 uuid 采用新库版本**，旧库独有的条目照常保留。
         * 没有身份字段的条目按内容去重（保证既不丢也不重复）。
         *
         * 顺序确定（新库在前、数组内保持原序）→ 结果可复现 → 幂等（G2）成立。
         */
        fun identityMergedArray(logicalKey: String, identityField: String): List<JsonElement> {
            val buckets = index.all(logicalKey)
            if (buckets.isEmpty()) return emptyList()
            val byIdentity = LinkedHashMap<String, JsonElement>()
            for (entry in buckets) {
                if (entry.value is JsonNull) continue
                val list = entry.value.asArrayOrNull()
                if (list == null) {
                    skip(entry.rawKey, "期望数组，实际是 ${typeName(entry.value)}")
                    continue
                }
                var shadowedCount = 0
                for ((i, raw) in list.withIndex()) {
                    val obj = raw as? JsonObject
                    if (obj == null) {
                        skip("${entry.rawKey}[$i]", "不是对象（${typeName(raw)}）")
                        continue
                    }
                    val identity = obj.string(identityField)?.takeIf { it.isNotBlank() }
                    val key = identity ?: "content:${raw}"
                    if (byIdentity.containsKey(key)) {
                        if (identity != null) shadowedCount++
                        continue
                    }
                    byIdentity[key] = raw
                }
                if (shadowedCount > 0) {
                    note("${entry.rawKey} 中 $shadowedCount 条记录与更高优先级的来源同 $identityField，采用高优先级版本")
                }
                if (!entry.fromNewDb) note("${entry.rawKey} 只在旧库存在，已补回")
            }
            return byIdentity.values.toList()
        }

        /** 整条载荷搬运（字段名逐字保持）。类型不符只报告，不猜。 */
        fun payloadArrayOf(logicalKey: String): List<JsonElement> {
            val entry = index.get(logicalKey) ?: return emptyList()
            if (entry.value is JsonNull) return emptyList()
            val list = entry.value.asArrayOrNull()
            if (list == null) {
                skip(entry.rawKey, "期望数组，实际是 ${typeName(entry.value)}")
                return emptyList()
            }
            if (!entry.fromNewDb) note("${entry.rawKey} 只存在于旧库，已补回")
            return list
        }

        /** 遍历某命名空间下**所有作用域**的键（坑 1：作用域后缀的解析统一走 LegacyKeys）。 */
        fun scopedPayloads(namespace: String): Map<ScopeId, List<JsonElement>> {
            val result = LinkedHashMap<ScopeId, List<JsonElement>>()
            index.forEachScoped(namespace) { scope, logical, entry ->
                if (scope == null) {
                    skip(entry.rawKey, "作用域后缀非法（缺角色 uuid 或分支 id）")
                    return@forEachScoped
                }
                if (entry.value is JsonNull) return@forEachScoped
                val list = entry.value.asArrayOrNull()
                if (list == null) {
                    skip(entry.rawKey, "期望数组，实际是 ${typeName(entry.value)}")
                    return@forEachScoped
                }
                if (!entry.fromNewDb) note("${entry.rawKey} 只存在于旧库，已补回")
                result[scope] = list
            }
            return result
        }

        // -------------------------------------------------------------- 角色

        fun characterOf(indexInArray: Int, element: JsonElement): MigratedCharacter? {
            val obj = element as? JsonObject
            if (obj == null) {
                skip("${LegacyKeys.CHARACTERS}[$indexInArray]", "不是对象（${typeName(element)}）")
                return null
            }
            var synthesized = false
            val uuid = obj.string("uuid")?.takeIf { it.isNotBlank() } ?: run {
                synthesized = true
                val generated = LegacyMigrator.deterministicUuid(
                    obj.string("name").orEmpty(),
                    obj.longOf("createdAt")?.toString().orEmpty(),
                    indexInArray.toString(),
                )
                note("角色「${obj.string("name").orEmpty()}」没有 uuid，按内容哈希补 $generated（保证幂等）")
                generated
            }
            val avatarValue = obj.string("avatar")
            val avatarPath = if (options.extractAvatars) extractInline(avatarValue, "${options.avatarDir}/$uuid") else null
            val fields = if (avatarPath != null) put(obj, "avatar", JsonPrimitive(avatarPath)) else obj
            return MigratedCharacter(
                uuid = uuid,
                name = obj.string("name").orEmpty(),
                avatarPath = avatarPath,
                worldInfo = obj.array("worldInfo"),
                regexScripts = obj.array("regexScripts"),
                uiTemplates = obj.array("uiTemplates"),
                fields = fields,
                synthesizedUuid = synthesized,
            )
        }

        fun orphanCharacter(uuid: String) = MigratedCharacter(
            uuid = uuid,
            name = "（孤立数据）${uuid.take(8)}",
            avatarPath = null,
            worldInfo = emptyList(),
            regexScripts = emptyList(),
            uiTemplates = emptyList(),
            fields = JsonObject(mapOf("uuid" to JsonPrimitive(uuid), "name" to JsonPrimitive("（孤立数据）${uuid.take(8)}"))),
        )

        // -------------------------------------------------------------- 分支

        fun synthesizedMainBranch(createdAt: Long) = MigratedBranch(
            id = LegacyKeys.MAIN_BRANCH_ID,
            name = "主线",
            parentId = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            forkFloor = 0,
            floorCount = 0,
            messageCount = 0,
            wordCount = 0,
        )

        /**
         * 分支读取：复刻上游 `normalizeStoryBranches` 的关键不变量——
         * 主线必须存在、`parentId` 必须指向真实分支（否则回落到 main）、id 去重。
         */
        fun branchesOf(character: MigratedCharacter): Pair<List<MigratedBranch>, String> {
            val logical = "branches_${character.uuid}"
            val entry = index.get(logical)
            val container = entry?.value as? JsonObject
            if (entry != null && container == null) {
                skip(entry.rawKey, "期望对象，实际是 ${typeName(entry.value)}")
            }
            if (entry != null && !entry.fromNewDb) note("${entry.rawKey} 只存在于旧库，已补回")

            val parsed = mutableListOf<MigratedBranch>()
            val seen = mutableSetOf<String>()
            for ((i, raw) in (container?.array("branches") ?: emptyList()).withIndex()) {
                val obj = raw as? JsonObject
                if (obj == null) {
                    skip("$logical.branches[$i]", "不是对象")
                    continue
                }
                val id = obj.string("id")?.trim().orEmpty()
                if (id.isEmpty()) {
                    skip("$logical.branches[$i]", "缺 id")
                    continue
                }
                if (!seen.add(id)) {
                    skip("$logical.branches[$i]", "分支 id 重复：$id")
                    continue
                }
                val createdAt = obj.longOf("createdAt") ?: character.fields.longOf("createdAt") ?: 0L
                parsed += MigratedBranch(
                    id = id,
                    // 主线名字恒为「主线」（上游 normalize 也这么钉）
                    name = if (id == LegacyKeys.MAIN_BRANCH_ID) "主线" else obj.string("name").orEmpty().ifBlank { "分支 ${i + 1}" },
                    parentId = if (id == LegacyKeys.MAIN_BRANCH_ID) null else obj.string("parentId"),
                    createdAt = createdAt,
                    updatedAt = obj.longOf("updatedAt") ?: createdAt,
                    forkFloor = obj.intOf("forkFloor") ?: 0,
                    floorCount = obj.intOf("floorCount") ?: 0,
                    messageCount = obj.intOf("messageCount") ?: 0,
                    wordCount = obj.intOf("wordCount") ?: 0,
                )
            }

            val ordered = mutableListOf<MigratedBranch>()
            val main = parsed.firstOrNull { it.isMain } ?: synthesizedMainBranch(
                character.fields.longOf("createdAt") ?: 0L,
            )
            ordered += main
            ordered += parsed.filter { !it.isMain }.sortedBy { it.createdAt }
            val validIds = ordered.map { it.id }.toSet()
            val repaired = ordered.map { branch ->
                if (branch.parentId != null && branch.parentId !in validIds) {
                    note("分支「${branch.name}」的 parentId=${branch.parentId} 不存在，已回落到主线")
                    branch.copy(parentId = LegacyKeys.MAIN_BRANCH_ID)
                } else {
                    branch
                }
            }
            val requested = container?.string("activeBranchId") ?: LegacyKeys.MAIN_BRANCH_ID
            val active = if (repaired.any { it.id == requested }) requested else LegacyKeys.MAIN_BRANCH_ID
            return repaired to active
        }

        // -------------------------------------------------------------- 会话

        fun conversationsOf(
            characters: List<MigratedCharacter>,
            branchesByCharacter: Map<String, List<MigratedBranch>>,
        ): List<MigratedConversation> {
            val result = mutableListOf<MigratedConversation>()
            // 原数组下标 = 上游 `currentCharacterIndex`，也用于 chat_<n> 数字回落（坑 2）
            for ((indexInArray, character) in characters.withIndex()) {
                for (branch in branchesByCharacter[character.uuid].orEmpty()) {
                    val scope = ScopeId(character.uuid, branch.id)
                    val logical = LegacyKeys.scopedLogical("chat", scope)
                    val direct = index.get(logical)
                    val chosen = when {
                        direct != null -> direct
                        branch.isMain -> numericFallback(indexInArray, logical)
                        else -> null
                    } ?: continue
                    if (!claimedConversationKeys.add(chosen.rawKey)) {
                        skip(chosen.rawKey, "该会话键已被其他角色认领（数字索引回落冲突）")
                        continue
                    }
                    if (!chosen.fromNewDb) note("${chosen.rawKey} 只存在于旧库，已补回")
                    val messages = parseMessages(chosen, scope)
                    if (messages.isNotEmpty()) {
                        result += MigratedConversation(scope = scope, messages = messages, sourceKey = chosen.rawKey)
                    }
                }
            }
            return result
        }

        /**
         * 坑 2：更早的版本用过 `chat_<数字下标>` 作会话键。
         * 上游 `loadStoredChatHistory(char, fallbackIndex)` 就有这条回落，这里照做。
         */
        private fun numericFallback(indexInArray: Int, expectedLogical: String): LegacyIndex.Entry? {
            val candidate = index.get("chat_$indexInArray") ?: return null
            note("会话键 $expectedLogical 缺失，回落到旧格式 chat_$indexInArray")
            return candidate
        }

        fun parseMessages(entry: LegacyIndex.Entry, scope: ScopeId): List<MigratedMessage> {
            val list = entry.value.asArrayOrNull()
            if (list == null) {
                skip(entry.rawKey, "期望消息数组，实际是 ${typeName(entry.value)}")
                return emptyList()
            }
            val result = mutableListOf<MigratedMessage>()
            for ((i, raw) in list.withIndex()) {
                val obj = raw as? JsonObject
                if (obj == null) {
                    skip("${entry.rawKey}[$i]", "消息不是对象（${typeName(raw)}）")
                    continue
                }
                val role = obj.string("role")?.takeIf { it.isNotBlank() }
                val content = obj.string("content")
                if (role == null || content == null) {
                    skip(
                        "${entry.rawKey}[$i]",
                        "消息缺 ${if (role == null) "role" else "content"}（有内容但形态非法，跳过并报告）",
                    )
                    continue
                }
                // 坑 5：界面临时态一律丢（它们是「这一屏要不要播动画」，不是内容）
                val dropped = LegacyModelDefaults.TRANSIENT_MESSAGE_FIELDS.filter { obj.containsKey(it) }.sorted()
                var rest = obj.toMutableMap()
                dropped.forEach { rest.remove(it) }
                rest.remove("role"); rest.remove("name"); rest.remove("content"); rest.remove("reasoning")
                rest.remove("imageAttachments"); rest.remove("id")

                val attachments = obj.array("imageAttachments").mapIndexed { n, item ->
                    extractAttachment(item, scope, i, n) ?: item
                }
                val avatarPath = if (options.extractAvatars) {
                    extractInline(obj.string("avatar"), "${options.avatarDir}/user-${scope.characterUuid}")
                } else {
                    null
                }
                if (avatarPath != null) rest["avatar"] = JsonPrimitive(avatarPath)

                result += MigratedMessage(
                    role = role,
                    name = obj.string("name"),
                    content = content,
                    reasoning = obj.string("reasoning"),
                    id = obj.string("id"),
                    // 坑 4：旧数据没有时间戳，顺序**只能**靠数组下标
                    sortIndex = i,
                    imageAttachments = attachments,
                    droppedTransient = dropped,
                    rest = JsonObject(rest),
                )
            }
            return result
        }

        private fun extractAttachment(item: JsonElement, scope: ScopeId, messageIndex: Int, nth: Int): JsonElement? {
            if (!options.extractMessageAttachments) return null
            val obj = item as? JsonObject ?: return null
            val dataUrl = obj.string("dataUrl") ?: return null
            val path = extractInline(dataUrl, "${options.attachmentDir}/${scope.suffix()}/$messageIndex-$nth")
                ?: return null
            return JsonObject(obj.toMutableMap().apply { this["dataUrl"] = JsonPrimitive(path) })
        }

        /** 把内联 base64 解出来登记为待落文件资产，返回新路径；不需要抽取则返回 null（保持原值）。 */
        fun extractInline(value: String?, basePath: String): String? {
            if (value.isNullOrBlank() || value.length <= options.inlineBase64Threshold) return null
            val (mime, bytes) = LegacyMigrator.parseDataUrl(value) ?: return null
            val path = "$basePath.${LegacyMigrator.extensionFor(mime)}"
            if (assets.none { it.path == path }) assets += ExtractedAsset(path, mime, bytes)
            return path
        }

        // -------------------------------------------------------------- 全局记录

        /**
         * 坑 8：`memory_settings.emptyTurns` 的键是内嵌的作用域标识 `<scope>:<mode>`。
         *
         * 本轮规范化后的形态与旧形态**一致**（都是 `uuid[__branch__bid]`），所以这一步实际是
         * **校验 + 剔除畸形键**——但它必须存在：不校验就会把一条悬空键悄悄带进新库，
         * 而「看到过它」正是本坑的验收点。新存储若改用别的 scope 标识，这里是唯一要改的地方。
         */
        fun normalizedMemorySettings(): JsonElement? {
            val entry = index.get(LegacyKeys.MEMORY_SETTINGS) ?: return null
            val obj = entry.value as? JsonObject ?: run {
                if (entry.value !is JsonNull) skip(entry.rawKey, "期望对象，实际是 ${typeName(entry.value)}")
                return entry.value
            }
            if (!entry.fromNewDb) note("${entry.rawKey} 只存在于旧库，已补回")
            val emptyTurns = obj["emptyTurns"] as? JsonObject ?: return obj
            val rewritten = LinkedHashMap<String, JsonElement>()
            for ((key, value) in emptyTurns) {
                val colon = key.lastIndexOf(':')
                val suffix = if (colon > 0) key.substring(0, colon) else key
                val mode = if (colon > 0) key.substring(colon + 1) else ""
                val scope = LegacyKeys.parseScope(suffix)
                if (scope == null) {
                    skip("${entry.rawKey}.emptyTurns.$key", "内嵌作用域键非法")
                    continue
                }
                rewritten[canonicalEmptyTurnsKey(scope, mode)] = value
            }
            return JsonObject(obj.toMutableMap().apply { this["emptyTurns"] = JsonObject(rewritten) })
        }

        /** 坑 9：`last_active_char` 是 `characters[]` 的**下标**，不是 uuid。 */
        fun activeCharacterUuid(characters: List<MigratedCharacter>): String? {
            val entry = index.get(LegacyKeys.LAST_ACTIVE_CHAR) ?: return null
            val indexValue = entry.value.asIntOrNull()
            if (indexValue == null) {
                skip(entry.rawKey, "期望数字下标，实际是 ${typeName(entry.value)}")
                return null
            }
            val uuid = characters.getOrNull(indexValue)?.uuid
            if (uuid == null) {
                skip(entry.rawKey, "下标 $indexValue 越界（共 ${characters.size} 个角色）")
                return null
            }
            return uuid
        }

        fun activeProfileId(profiles: List<JsonElement>): String? {
            val entry = index.get(LegacyKeys.ACTIVE_PROFILE_ID) ?: return null
            val id = entry.value.asStringOrNull()
            if (id.isNullOrBlank()) {
                skip(entry.rawKey, "期望非空字符串，实际是 ${typeName(entry.value)}")
                return null
            }
            // 必须是真实存在的人设：`user_profiles` 缺失也一并判非法——
            // 留一个悬空 id 会让新界面指向不存在的人设，比丢掉它更糟。
            val known = profiles.mapNotNull { (it as? JsonObject)?.string("uuid") }
            if (id !in known) {
                skip(entry.rawKey, "指向的人设 $id 不在 user_profiles 里（共 ${known.size} 个）")
                return null
            }
            return id
        }

        // -------------------------------------------------------------- 小工具

        fun put(obj: JsonObject, key: String, value: JsonElement): JsonObject =
            JsonObject(obj.toMutableMap().apply { this[key] = value })

        /** 人设/用户的头像也要抽文件（它们同样是内联 base64）。 */
        fun extractAvatarIn(payload: JsonElement?, basePath: String): JsonElement? {
            val obj = payload as? JsonObject ?: return payload
            val path = extractInline(obj.string("avatar"), basePath) ?: return payload
            return put(obj, "avatar", JsonPrimitive(path))
        }
    }

    internal fun typeName(element: JsonElement): String = when (element) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> if (element.isString) "string" else "primitive"
    }
}

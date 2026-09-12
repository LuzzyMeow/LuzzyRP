package com.luzzymeow.luzzyrp.data.store

import com.luzzymeow.luzzyrp.data.legacy.ExtractedAsset
import com.luzzymeow.luzzyrp.data.legacy.MigratedData
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 迁移导入：把 [MigratedData]（迁移器的产出）落进新数据层。
 *
 * ## 幂等（G2）
 *
 * 语义定为「**这次导入就是当前状态**」：在一个事务里**先清内容表、再全量写入**。
 * 于是同一份数据导入两次结果**逐行一致**，不需要逐条比对去重，也不会翻倍。
 * 注意**不清 `kv` 表**——迁移完成标记与用户设置在那里。
 *
 * ## 顺序（文件先于事务）
 *
 * 附件（base64 头像/图片）**先落文件、再进事务**。反过来会出现「事务回滚了但文件已经在」
 * 或「事务提交了但文件没写成功」两种坏状态；文件是幂等的（同路径同内容覆盖），
 * 所以放前面最安全。DB 里只留路径 + 索引行。
 *
 * ## 不设置迁移标记
 *
 * 标记由调用方（迁移入口）在**成功之后**写：本类只负责「把数据放进去」，
 * 是否算迁移完成由上层决定（避免「导入失败但标记已写」）。
 */
class MigrationWriter(
    private val store: LuzzyStore,
    private val filesDir: File,
) {

    data class Outcome(
        val characters: Int,
        val branches: Int,
        val messages: Int,
        val vectorMemories: Int,
        val classicMemories: Int,
        val worldEntries: Int,
        val regexes: Int,
        val presets: Int,
        val usage: Int,
        val profiles: Int,
        val assets: Int,
    )

    /** 同步写文件（调用方在 IO 线程）；返回实际落盘的相对路径集合。 */
    fun writeAssets(assets: List<ExtractedAsset>): List<String> {
        val written = mutableListOf<String>()
        for (asset in assets) {
            val target = File(filesDir, asset.path)
            target.parentFile?.mkdirs()
            val existing = target.takeIf { it.isFile && it.length().toInt() == asset.byteCount && it.readBytes().contentEquals(asset.bytes) }
            if (existing == null) target.writeBytes(asset.bytes)
            written += asset.path
        }
        return written
    }

    /**
     * 导入全部数据（事务内）。[activeCharacterUuid] / [activeProfileId] 同时写进 kv。
     */
    suspend fun import(
        data: MigratedData,
        activeCharacterUuid: String? = data.activeCharacterUuid,
        activeProfileId: String? = data.activeProfileId,
    ): Outcome {
        val assetPaths = writeAssets(data.assets)

        return store.transaction {
            // 1) 清内容表（不碰 kv）
            store.clearContentTables()

            // 2) 角色
            for (character in data.characters) {
                store.upsertCharacter(
                    CharacterEntity(
                        uuid = character.uuid,
                        name = character.name,
                        avatarPath = character.avatarPath,
                        createdAt = character.fields["createdAt"]?.safeLong() ?: 0L,
                        payload = character.fields.toString(),
                    ),
                )
            }

            // 3) 分支
            var branchCount = 0
            for ((characterUuid, branches) in data.branchesByCharacter) {
                val rows = branches.map { branch ->
                    BranchEntity(
                        characterUuid = characterUuid,
                        branchId = branch.id,
                        name = branch.name,
                        parentId = branch.parentId,
                        createdAt = branch.createdAt,
                        updatedAt = branch.updatedAt,
                        forkFloor = branch.forkFloor,
                        messageCount = branch.messageCount,
                        wordCount = branch.wordCount,
                        isMain = branch.isMain,
                    )
                }
                branchCount += rows.size
                store.replaceBranches(
                    characterUuid = characterUuid,
                    branches = rows,
                    activeBranchId = data.activeBranchByCharacter[characterUuid]
                        ?: branches.firstOrNull { it.isMain }?.id
                        ?: "main",
                )
            }

            // 4) 会话消息
            var messageCount = 0
            for (conversation in data.conversations) {
                val scopeId = conversation.scope.suffix()
                val rows = conversation.messages.map { message ->
                    MessageEntity(
                        scopeId = scopeId,
                        sortIndex = message.sortIndex,
                        id = message.id,
                        role = message.role,
                        name = message.name,
                        content = message.content,
                        reasoning = message.reasoning,
                        // imageAttachments 是**内容**（不是界面临时态），必须一起落盘。
                        // 它被迁移器提成了类型化字段，所以这里显式并回 payload，键名保持原样。
                        payload = payloadOf(message.rest, message.imageAttachments),
                    )
                }
                store.replaceMessages(conversation.scope, rows)
                messageCount += rows.size
            }

            // 5) 记忆（向量 / 经典）
            var vectorCount = 0
            for ((scope, list) in data.vectorMemories) {
                store.replaceMemories(scope, LuzzyStore.MEMORY_VECTOR, list)
                vectorCount += list.size
            }
            var classicCount = 0
            for ((scope, list) in data.classicMemories) {
                store.replaceMemories(scope, LuzzyStore.MEMORY_CLASSIC, list)
                classicCount += list.size
            }

            // 6) 其余集合（整表读写）
            store.replaceRecords(LuzzyStore.RECORD_WORLDINFO, "", data.worldEntries)
            store.replaceRecords(LuzzyStore.RECORD_GLOBAL_WORLDINFO, "", data.globalWorldEntries)
            store.replaceRecords(LuzzyStore.RECORD_REGEX, "", data.regexes)
            store.replaceRecords(LuzzyStore.RECORD_GLOBAL_REGEX, "", data.globalRegexes)
            store.replaceRecords(LuzzyStore.RECORD_PRESETS, "", data.presets)
            store.replaceRecords(LuzzyStore.RECORD_USAGE, "", data.usage)
            // 人设按 uuid 各占一行：owner 留空、slot 用下标（人设集合同样是整表读写）
            store.replaceRecords(LuzzyStore.RECORD_PROFILE, "", data.profiles)

            // 7) 单值设置与当前选择
            store.putJson(LuzzyStore.KEY_SETTINGS, data.settings)
            store.putJson(LuzzyStore.KEY_MEMORY_SETTINGS, data.memorySettings)
            store.putJson(LuzzyStore.KEY_ACTIVE_TOOLS, data.activeTools)
            store.putJson(LuzzyStore.KEY_WORLDINFO_SETTINGS, data.worldInfoSettings)
            store.putJson(LuzzyStore.KEY_GLOBAL_UI_TEMPLATES, data.globalUiTemplates)
            store.putJson(LuzzyStore.KEY_USER, data.user)
            store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, activeCharacterUuid)
            store.putString(LuzzyStore.KEY_ACTIVE_PROFILE, activeProfileId)

            // 8) 附件索引
            val assetRows = data.assets
                .filter { it.path in assetPaths }
                .map { AttachmentEntity(it.path, it.mimeType, it.byteCount, origin = "migration") }
            store.upsertAttachments(assetRows)

            Outcome(
                characters = data.characters.size,
                branches = branchCount,
                messages = messageCount,
                vectorMemories = vectorCount,
                classicMemories = classicCount,
                worldEntries = data.worldEntries.size + data.globalWorldEntries.size,
                regexes = data.regexes.size + data.globalRegexes.size,
                presets = data.presets.size,
                usage = data.usage.size,
                profiles = data.profiles.size,
                assets = assetRows.size,
            )
        }
    }

    /** 已迁移过就不再导（G4：中断可续靠「成功后才写标记」，不是靠这里）。 */
    suspend fun alreadyMigrated(): Boolean = store.string(LuzzyStore.KEY_LEGACY_MIGRATED) == "true"

    /** 迁移成功后调用：写标记 + 计数（`kv` 不参与 [import] 的清表）。 */
    suspend fun markMigrated(outcome: Outcome, migratedAt: Long) {
        store.putString(LuzzyStore.KEY_LEGACY_MIGRATED, "true")
        store.putString(LuzzyStore.KEY_LEGACY_MIGRATED_AT, migratedAt.toString())
        store.putJson(
            LuzzyStore.KEY_LEGACY_MIGRATION_COUNTS,
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "characters" to JsonPrimitive(outcome.characters),
                    "branches" to JsonPrimitive(outcome.branches),
                    "messages" to JsonPrimitive(outcome.messages),
                    "vectorMemories" to JsonPrimitive(outcome.vectorMemories),
                    "classicMemories" to JsonPrimitive(outcome.classicMemories),
                    "worldEntries" to JsonPrimitive(outcome.worldEntries),
                    "regexes" to JsonPrimitive(outcome.regexes),
                    "presets" to JsonPrimitive(outcome.presets),
                    "usage" to JsonPrimitive(outcome.usage),
                    "profiles" to JsonPrimitive(outcome.profiles),
                    "assets" to JsonPrimitive(outcome.assets),
                ),
            ),
        )
    }
}

/** 从 payload 里安全取一个长整数（旧数据里 `createdAt` 是毫秒数；缺省或形态异常都回 0）。 */
private fun JsonElement?.safeLong(): Long = try {
    this?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
} catch (e: IllegalArgumentException) {
    0L
}

/**
 * 组装消息 payload：旧结构里除被提成列的那几个字段之外的**全部字段**，键名逐字保留。
 *
 * [imageAttachments] 单独传进来的原因见调用点：它在迁移器里是类型化字段，
 * 但仍是内容，必须落盘（键名不变 → 兼容读）。
 */
private fun payloadOf(rest: kotlinx.serialization.json.JsonObject, imageAttachments: List<JsonElement>): String =
    kotlinx.serialization.json.JsonObject(
        rest.toMutableMap().apply {
            this["imageAttachments"] = kotlinx.serialization.json.JsonArray(imageAttachments)
        },
    ).toString()

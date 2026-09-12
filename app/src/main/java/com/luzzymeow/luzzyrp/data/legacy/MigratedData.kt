package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 迁移的目标模型（**存储无关**）。
 *
 * 为什么不做成完整强类型领域模型：消费这些数据的是 P4-B 的新存储与 UI，
 * 而那两处的字段需求还没定（P4-B 的存储选型 spike 都还没跑）。现在把
 * 记忆/世界书/预设/正则/用量都建成数据类，等于**先猜一遍**再让 P4-B 改一遍。
 *
 * 因此这里的分工是：
 * - **强类型**：新界面马上就要用的部分（角色标识、分支、消息、作用域）；
 * - **原样载荷**（[JsonElement]）：其余记录整条搬运，字段名与旧结构**逐字保持一致**
 *   —— 这正是「迁移零丢失」的前提。P4-B 按消费方需要再把它们定型。
 *
 * 详见 `docs/DESIGN-migration.md` §7。
 */
object LegacyModelDefaults {
    /** 消息里的界面临时态：迁移**必须丢弃**（坑 5）。它们是「这一屏要不要播动画」，不是内容。 */
    val TRANSIENT_MESSAGE_FIELDS = setOf(
        "shouldAnimate",
        "skipReveal",
        "isCotOpen",
        "isReasoningOpen",
        "isReasoningUserToggled",
        "isReasoningAutoCollapsed",
        "isSummaryOpen",
    )
}

/**
 * 从 base64 dataURL 解出来的附件（头像 / 消息图片），落文件后 DB 内只留路径。
 *
 * `equals` 按内容比：默认的 `ByteArray` 比较是**引用**比较，会让「迁移两次结果相等」
 * 这个幂等判据（G2）假红。
 */
class ExtractedAsset(
    val path: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val byteCount: Int get() = bytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtractedAsset) return false
        return path == other.path && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int =
        (path.hashCode() * 31 + mimeType.hashCode()) * 31 + bytes.contentHashCode()

    override fun toString(): String = "ExtractedAsset($path, $mimeType, ${bytes.size}B)"
}

/** 一条消息（字段名对齐旧结构；临时态已剔除）。 */
data class MigratedMessage(
    val role: String,
    val name: String?,
    val content: String,
    val reasoning: String?,
    /** 上游用 `id` 关联分支/记忆来源；`first_mes` 那条可能没有。 */
    val id: String?,
    /** 旧数据**没有时间戳**，顺序只能靠数组下标（坑 4）。 */
    val sortIndex: Int,
    val imageAttachments: List<JsonElement>,
    val droppedTransient: List<String>,
    /** 其余字段原样保留（含 `isSelf` / `avatar` / 未来新增字段）。 */
    val rest: JsonObject,
)

/** 一段会话 = 一个作用域（角色 × 分支）的消息序列。 */
data class MigratedConversation(
    val scope: ScopeId,
    val messages: List<MigratedMessage>,
    /** 数据实际来自哪个键（诊断用；数字索引回落时能看出「这条是从 chat_<n> 捞的」）。 */
    val sourceKey: String,
)

/** 分支（含上游的分支元数据；主线恒存在）。 */
data class MigratedBranch(
    val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val forkFloor: Int,
    val floorCount: Int,
    val messageCount: Int,
    val wordCount: Int,
) {
    val isMain: Boolean get() = id == LegacyKeys.MAIN_BRANCH_ID
}

/** 角色卡（头像可能已被抽成文件，[avatarPath] 非空时 [fields] 里的 `avatar` 已换成路径）。 */
data class MigratedCharacter(
    val uuid: String,
    val name: String,
    val avatarPath: String?,
    val worldInfo: List<JsonElement>,
    val regexScripts: List<JsonElement>,
    val uiTemplates: List<JsonElement>,
    /** 其余字段原样保留。 */
    val fields: JsonObject,
    /** true = 原数据缺 uuid，按内容哈希补的（见 [LegacyMigrator.deterministicUuid]）。 */
    val synthesizedUuid: Boolean = false,
)

/** 一条被跳过的记录：**只跳过不中断**，但必须留下原因（G3）。 */
data class SkippedRecord(val key: String, val reason: String)

/**
 * 迁移结果（一次迁移的全部产出）。
 *
 * 幂等（G2）的口径：**同样的输入跑两次，本对象逐字段相等**——
 * 所以这里不出现时间戳、随机 id、随机顺序。
 */
data class MigratedData(
    val characters: List<MigratedCharacter>,
    val branchesByCharacter: Map<String, List<MigratedBranch>>,
    val activeBranchByCharacter: Map<String, String>,
    val conversations: List<MigratedConversation>,
    val vectorMemories: Map<ScopeId, List<JsonElement>>,
    val classicMemories: Map<ScopeId, List<JsonElement>>,
    val worldEntries: List<JsonElement>,
    val globalWorldEntries: List<JsonElement>,
    val regexes: List<JsonElement>,
    val globalRegexes: List<JsonElement>,
    val presets: List<JsonElement>,
    val usage: List<JsonElement>,
    val profiles: List<JsonElement>,
    val activeProfileId: String?,
    val activeCharacterUuid: String?,
    val settings: JsonElement?,
    val memorySettings: JsonElement?,
    val activeTools: JsonElement?,
    val worldInfoSettings: JsonElement?,
    val globalUiTemplates: JsonElement?,
    val user: JsonElement?,
    val assets: List<ExtractedAsset>,
    val skipped: List<SkippedRecord>,
    /** 需要人看一眼的异常但不丢数据的情况（如：从旧库补回、补了 uuid、用了数字索引回落）。 */
    val notes: List<String>,
) {

    // ---- 计数口径：验收判据（G1）就是比这张表 ----
    val characterCount: Int get() = characters.size
    val branchCount: Int get() = branchesByCharacter.values.sumOf { it.size }
    val messageCount: Int get() = conversations.sumOf { it.messages.size }
    val vectorMemoryCount: Int get() = vectorMemories.values.sumOf { it.size }
    val classicMemoryCount: Int get() = classicMemories.values.sumOf { it.size }
    val worldEntryCount: Int get() = worldEntries.size + globalWorldEntries.size
    val regexCount: Int get() = regexes.size + globalRegexes.size

    fun counts(): Map<String, Int> = linkedMapOf(
        "characters" to characterCount,
        "branches" to branchCount,
        "conversations" to conversations.size,
        "messages" to messageCount,
        "vectorMemories" to vectorMemoryCount,
        "classicMemories" to classicMemoryCount,
        "worldEntries" to worldEntryCount,
        "regexes" to regexCount,
        "presets" to presets.size,
        "usage" to usage.size,
        "profiles" to profiles.size,
        "assets" to assets.size,
        "skipped" to skipped.size,
    )
}

/** 空载荷便捷常量。 */
internal val EMPTY_OBJECT = JsonObject(emptyMap())

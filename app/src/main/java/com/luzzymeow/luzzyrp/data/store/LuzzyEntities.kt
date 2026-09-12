package com.luzzymeow.luzzyrp.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 新数据层的表结构（P4-B，Room）。
 *
 * ## 为什么消息单独建表、其余记录塞 JSON 列
 *
 * 判别依据是**写模式**，不是「规范化好看」：
 * - **消息**是唯一会被**高频增量写**的数据：流式生成时一条回复在不断变长，用户连续对话
 *   会持续追加。若把一整段会话存成一列 JSON，每次落盘都要**重写整段历史**
 *   （重度用户的会话可达数 MB）——那是 IO 风暴。一消息一行 + `scopeId` 索引，
 *   追加/更新只碰一行。
 * - **其余记录**（世界书 / 正则 / 预设 / 用量 / 人设）在旧实现里本来就是**整表读写**
 *   （界面改完整个数组一次保存），元素也没有稳定 id。给它们建表只会把「一次写」拆成
 *   N 次写，还额外引入「元素身份」这个旧数据里根本不存在的概念。
 *
 * ## payload 列的含义与纪律
 *
 * `payload` 是该记录的**其余字段的整段 JSON，键名与旧结构逐字一致**。
 * 这是「迁移零丢失」的前提：新代码只把**确实要查询/展示**的字段提成列，
 * 其余的照原样背着走；日后要用哪个字段，再从 payload 里取，不需要改表。
 *
 * 这就是 rikkahub 的「一行 + JSON 列」模型；`docs/DESIGN-migration.md` §8 记了选型依据。
 */

/** 角色卡。`uuid` 是旧数据里就有的稳定身份，直接做主键。 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    /** base64 头像抽取成文件后的相对路径（见 [MigrationWriter]）；未抽取时为 null。 */
    val avatarPath: String?,
    val createdAt: Long,
    /** 角色卡其余字段（含 worldInfo / regexScripts / uiTemplates 等内嵌数组）。 */
    val payload: String,
)

/** 剧情分支。主线恒为 `main`，且必须存在（迁移器已保证）。 */
@Entity(tableName = "branches", primaryKeys = ["characterUuid", "branchId"])
data class BranchEntity(
    val characterUuid: String,
    val branchId: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val forkFloor: Int,
    val messageCount: Int,
    val wordCount: Int,
    val isMain: Boolean,
)

/**
 * 一条消息。
 *
 * **主键是 (scopeId, sortIndex) 而不是消息 `id`**：旧数据里 `first_mes` 那条通常**没有 id**，
 * 而顺序是数组下标——这正是旧数据的真实身份。用自造 id 做键会在重复导入时产生重复行。
 */
@Entity(
    tableName = "messages",
    primaryKeys = ["scopeId", "sortIndex"],
    indices = [Index("scopeId")],
)
data class MessageEntity(
    val scopeId: String,
    val sortIndex: Int,
    val id: String?,
    val role: String,
    val name: String?,
    val content: String,
    val reasoning: String?,
    /** `imageAttachments` 与其余字段的 JSON。 */
    val payload: String,
)

/** 记忆分片（向量 `kind="vector"` / 经典 `kind="classic"`）。向量四字段原样躺在 payload 里。 */
@Entity(tableName = "memories", primaryKeys = ["scopeId", "kind", "id"])
data class MemoryEntity(
    val scopeId: String,
    val kind: String,
    val id: String,
    val sortIndex: Int,
    val turn: Int?,
    val enabled: Boolean,
    val payload: String,
)

/** 分支容器的元信息（旧 `branches_<uuid>` 里的 `version` / `activeBranchId`）。 */
@Entity(tableName = "branch_meta")
data class BranchMetaEntity(
    @PrimaryKey val characterUuid: String,
    val version: Int,
    val activeBranchId: String,
)

/**
 * 其余「整表读写」集合的统一落点。
 *
 * `kind` ∈ {worldinfo, global_worldinfo, regex, global_regex, presets, token_usage_history, profile}；
 * `owner` 为归属（角色 uuid / 空串表示全局）；`slot` 是该元素在旧数组里的**下标**（身份即位置）。
 */
@Entity(tableName = "records", primaryKeys = ["kind", "owner", "slot"])
data class RecordEntity(
    val kind: String,
    val owner: String,
    val slot: Int,
    val updatedAt: Long,
    /** 该元素的整段 JSON（键名与旧结构逐字一致）。 */
    val payload: String,
)

/**
 * 键值设置。值统一是 **JSON 文本**（字符串带引号，对象是对象），
 * 免得「这个键存的是字符串还是 JSON」靠记忆。
 */
@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** 抽取出来的二进制附件（头像 / 消息图片）索引：`path` 相对 `filesDir`。 */
@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val path: String,
    val mimeType: String,
    val byteCount: Int,
    /** 来源记录（便于清理孤儿文件），如 `character:<uuid>` / `message:<scope>:<index>`。 */
    @ColumnInfo(defaultValue = "") val origin: String,
)

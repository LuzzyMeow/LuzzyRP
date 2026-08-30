package com.luzzymeow.luzzyrp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体层。
 *
 * 设计原则（与 rikkahub 同构）：复杂嵌套结构（消息列表/JSON 配置）以 JSON TEXT
 * 承载，Room 只负责关系与索引——schema 简单稳定，AutoMigration 友好。
 * JSON 编解码统一走仓库层（JsonInstant），DAO 与实体不感知序列化细节。
 */

/** 会话表。 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String = "新对话",
    /** 绑定角色卡 id（null = 普通对话）。 */
    val cardId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** List<String> JSON。 */
    val worldbookIdsJson: String = "[]",
    /** List<String> JSON。 */
    val regexIdsJson: String = "[]",
    val uiTemplateId: String? = null,
    val enableMemory: Boolean = true,
    /** ContextConfig JSON。 */
    val contextConfigJson: String = "{}",
    /** SummaryCounters JSON。 */
    val summaryCountersJson: String = "{}",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** 最后一条消息时间（列表排序）。 */
    val lastMessageAt: Long = 0,
    /** 当前叶节点 id。 */
    val currentNodeId: String? = null,
)

/** 消息节点表（分支树；重 roll 产生兄弟节点）。 */
@Entity(
    tableName = "message_nodes",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId"), Index("parentId")],
)
data class MessageNodeEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val parentId: String? = null,
    /** 当前选中子分支下标。 */
    val selectIndex: Int = 0,
    /** List<UIMessage> JSON。 */
    val messagesJson: String = "[]",
    val createdAt: Long = 0,
)

/** 角色卡表（SillyTavern v2/v3 全字段 + LuzzyRP 扩展）。 */
@Entity(tableName = "character_cards")
data class CharacterCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    /** List<String> JSON（alt_greetings）。 */
    val altGreetingsJson: String = "[]",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    /** List<String> JSON。 */
    val tagsJson: String = "[]",
    val creator: String = "",
    val characterVersion: String = "",
    val exampleDialogs: String = "",
    val stSpecVersion: String? = null,
    /** 原始卡片 JSON（导出 PNG 时原样写回）。 */
    val stRawJson: String? = null,
    val avatarPath: String? = null,
    /** builtin / imported / created。 */
    val source: String = "created",
    val readonly: Boolean = false,
    val worldbookId: String? = null,
    /** List<String> JSON。 */
    val regexIdsJson: String = "[]",
    val uiTemplateId: String? = null,
    /** 背景图路径。 */
    val backgroundPath: String? = null,
    val backgroundOpacity: Int = 45,
    val backgroundBlur: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** 世界书表。 */
@Entity(tableName = "worldbooks")
data class WorldbookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** 绑定角色卡（卡片世界书）；null = 全局。 */
    val cardId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** 世界书条目表（三策略召回参数）。 */
@Entity(
    tableName = "worldbook_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorldbookEntity::class,
            parentColumns = ["id"],
            childColumns = ["worldbookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("worldbookId")],
)
data class WorldbookEntryEntity(
    @PrimaryKey val id: String,
    val worldbookId: String,
    val comment: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    /** List<RecallStrategy> JSON。 */
    val strategiesJson: String = "[\"keyword\"]",
    /** List<String> JSON（主关键词）。 */
    val keysJson: String = "[]",
    /** List<String> JSON（次级关键词）。 */
    val keysSecondaryJson: String = "[]",
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val recursion: Boolean = false,
    val probability: Int = 100,
    /** before_char / after_char / before_example / after_example / at_depth。 */
    val position: String = "before_char",
    val depth: Int = 4,
    /** at_depth 注入角色：system / user / assistant（v2 新增列）。 */
    @ColumnInfo(defaultValue = "SYSTEM")
    val depthRole: String = "SYSTEM",
    val order: Int = 100,
    val vectorIndexed: Boolean = false,
    /** 向量召回相似度阈值。 */
    val minSimilarity: Float = 0.55f,
)

/** 正则脚本表。 */
@Entity(tableName = "regex_scripts")
data class RegexScriptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val find: String,
    val replace: String = "{{match}}",
    /** List<RegexScope> JSON。 */
    val scopesJson: String = "[\"ai\"]",
    /** List<RegexTiming> JSON。 */
    val timingsJson: String = "[\"display\"]",
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val enabled: Boolean = true,
    val cardId: String? = null,
    /** 内置预设标识（可停用不可删除）。 */
    val preset: String? = null,
)

/** ACE 长期记忆表。 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String = "其他",
    val helpful: Int = 0,
    val harmful: Int = 0,
    val neutral: Int = 0,
    val active: Boolean = true,
    val sourceConversationId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastRetrievedAt: Long = 0,
    /** 嵌入向量 JSON（冗余存储：去重余弦计算用；Top-K 检索走 memory_vec 虚表）。 */
    val embeddingJson: String? = null,
)

/** 三级摘要表。 */
@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class SummaryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** a / b / c。 */
    val level: String,
    val roundIndex: Int,
    val content: String,
    val createdAt: Long = 0,
)

/** 提示词预设表（v2 新增：预设 = 名称 + 条目列表 JSON）。 */
@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** List<PresetEntry> JSON。 */
    val entriesJson: String = "[]",
    val builtin: Boolean = false,
    val readonly: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** 收藏表。 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    /** message / conversation。 */
    val type: String,
    val conversationId: String,
    val nodeId: String? = null,
    val messageId: String? = null,
    val excerpt: String = "",
    val cardName: String? = null,
    val createdAt: Long = 0,
)

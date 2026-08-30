package com.luzzymeow.luzzyrp.data.repository

import com.luzzymeow.luzzyrp.core.common.JsonInstant
import com.luzzymeow.luzzyrp.core.model.CardSource
import com.luzzymeow.luzzyrp.core.model.ChatBackground
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.core.model.ContextConfig
import com.luzzymeow.luzzyrp.core.model.Conversation
import com.luzzymeow.luzzyrp.core.model.Favorite
import com.luzzymeow.luzzyrp.core.model.FavoriteType
import com.luzzymeow.luzzyrp.core.model.InjectionPosition
import com.luzzymeow.luzzyrp.core.model.MessageNode
import com.luzzymeow.luzzyrp.core.model.RecallStrategy
import com.luzzymeow.luzzyrp.core.model.RegexScope
import com.luzzymeow.luzzyrp.core.model.RegexScript
import com.luzzymeow.luzzyrp.core.model.RegexTiming
import com.luzzymeow.luzzyrp.core.model.SummaryCounters
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.Worldbook
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import com.luzzymeow.luzzyrp.data.db.entity.CharacterCardEntity
import com.luzzymeow.luzzyrp.data.db.entity.ConversationEntity
import com.luzzymeow.luzzyrp.data.db.entity.FavoriteEntity
import com.luzzymeow.luzzyrp.data.db.entity.MessageNodeEntity
import com.luzzymeow.luzzyrp.data.db.entity.RegexScriptEntity
import com.luzzymeow.luzzyrp.data.db.entity.SummaryEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntryEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 实体 ↔ 领域模型映射。
 * [INVARIANT-KV] 消息 JSON 编解码只允许经由本文件与 JsonInstant：
 *   固定字段序保证同一历史两次序列化逐字节一致（KV 缓存稳定性）。
 */

private val stringListSerializer = ListSerializer(String.serializer())

private inline fun <reified T> decode(json: String, fallback: T): T =
    runCatching { Json.decodeFromString<T>(json) }.getOrDefault(fallback)

// —— 会话 ——

fun ConversationEntity.toDomain(nodes: List<MessageNode>): Conversation = Conversation(
        id = id,
        title = title,
        nodes = nodes,
        currentNodeId = currentNodeId,
        cardId = cardId,
        worldbookIds = decode(worldbookIdsJson, emptyList()),
        regexIds = decode(regexIdsJson, emptyList()),
        uiTemplateId = uiTemplateId,
        enableMemory = enableMemory,
        contextConfig = decode(contextConfigJson, ContextConfig()),
        summaryCounters = decode(summaryCountersJson, SummaryCounters()),
        pinned = pinned,
        archived = archived,
        createdAt = createdAt.takeIf { it > 0 }?.let { kotlin.time.Instant.fromEpochMilliseconds(it) },
        updatedAt = updatedAt.takeIf { it > 0 }?.let { kotlin.time.Instant.fromEpochMilliseconds(it) },
    )

fun Conversation.toEntity(lastMessageAt: Long): ConversationEntity = ConversationEntity(
        id = id,
        title = title,
        cardId = cardId,
        pinned = pinned,
        archived = archived,
        worldbookIdsJson = JsonInstant.encodeToString(stringListSerializer, worldbookIds),
        regexIdsJson = JsonInstant.encodeToString(stringListSerializer, regexIds),
        uiTemplateId = uiTemplateId,
        enableMemory = enableMemory,
        contextConfigJson = JsonInstant.encodeToString(ContextConfig.serializer(), contextConfig),
        summaryCountersJson = JsonInstant.encodeToString(SummaryCounters.serializer(), summaryCounters),
        createdAt = (createdAt?.toEpochMilliseconds() ?: 0L),
        updatedAt = (updatedAt?.toEpochMilliseconds() ?: 0L),
        lastMessageAt = lastMessageAt,
        currentNodeId = currentNodeId,
    )

// —— 消息节点 ——

fun MessageNodeEntity.toDomain(): MessageNode = MessageNode(
        id = id,
        parentId = parentId,
        selectIndex = selectIndex,
        messages = decode(messagesJson, emptyList<UIMessage>()),
        createdAt = createdAt.takeIf { it > 0 }?.let { kotlin.time.Instant.fromEpochMilliseconds(it) },
    )

fun MessageNode.toEntity(conversationId: String): MessageNodeEntity = MessageNodeEntity(
        id = id,
        conversationId = conversationId,
        parentId = parentId,
        selectIndex = selectIndex,
        messagesJson = JsonInstant.encodeToString(ListSerializer(UIMessage.serializer()), messages),
        createdAt = (createdAt?.toEpochMilliseconds() ?: System.currentTimeMillis()),
    )

// —— 角色卡 ——

fun CharacterCardEntity.toDomain(): CharacterCard = CharacterCard(
        id = id,
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMes = firstMes,
        altGreetings = decode(altGreetingsJson, emptyList()),
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        tags = decode(tagsJson, emptyList()),
        creator = creator,
        characterVersion = characterVersion,
        exampleDialogs = exampleDialogs,
        stSpecVersion = stSpecVersion,
        stRawJson = stRawJson,
        avatarPath = avatarPath,
        source = decode("\"$source\"", CardSource.CREATED),
        readonly = readonly,
        worldbookId = worldbookId,
        regexIds = decode(regexIdsJson, emptyList()),
        uiTemplateId = uiTemplateId,
        chatBackground = ChatBackground(path = backgroundPath, opacity = backgroundOpacity, blur = backgroundBlur),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CharacterCard.toEntity(): CharacterCardEntity = CharacterCardEntity(
        id = id,
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMes = firstMes,
        altGreetingsJson = JsonInstant.encodeToString(stringListSerializer, altGreetings),
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        tagsJson = JsonInstant.encodeToString(stringListSerializer, tags),
        creator = creator,
        characterVersion = characterVersion,
        exampleDialogs = exampleDialogs,
        stSpecVersion = stSpecVersion,
        stRawJson = stRawJson,
        avatarPath = avatarPath,
        source = source.name.lowercase(),
        readonly = readonly,
        worldbookId = worldbookId,
        regexIdsJson = JsonInstant.encodeToString(stringListSerializer, regexIds),
        uiTemplateId = uiTemplateId,
        backgroundPath = chatBackground.path,
        backgroundOpacity = chatBackground.opacity,
        backgroundBlur = chatBackground.blur,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// —— 世界书 ——

fun WorldbookEntryEntity.toDomain(): WorldbookEntry = WorldbookEntry(
        id = id,
        worldbookId = worldbookId,
        comment = comment,
        content = content,
        enabled = enabled,
        strategies = decode(strategiesJson, listOf(RecallStrategy.KEYWORD)),
        keys = decode(keysJson, emptyList()),
        keysSecondary = decode(keysSecondaryJson, emptyList()),
        caseSensitive = caseSensitive,
        useRegex = useRegex,
        recursion = recursion,
        probability = probability,
        position = decode("\"$position\"", InjectionPosition.BEFORE_CHAR),
        depth = depth,
        order = order,
        vectorIndexed = vectorIndexed,
        minSimilarity = minSimilarity,
    )

fun WorldbookEntry.toEntity(): WorldbookEntryEntity = WorldbookEntryEntity(
        id = id,
        worldbookId = worldbookId,
        comment = comment,
        content = content,
        enabled = enabled,
        strategiesJson = JsonInstant.encodeToString(
            ListSerializer(kotlinx.serialization.serializer<RecallStrategy>()),
            strategies,
        ),
        keysJson = JsonInstant.encodeToString(stringListSerializer, keys),
        keysSecondaryJson = JsonInstant.encodeToString(stringListSerializer, keysSecondary),
        caseSensitive = caseSensitive,
        useRegex = useRegex,
        recursion = recursion,
        probability = probability,
        position = position.name.lowercase(),
        depth = depth,
        order = order,
        vectorIndexed = vectorIndexed,
        minSimilarity = minSimilarity,
    )

fun WorldbookEntity.toDomain(entries: List<WorldbookEntry>): Worldbook = Worldbook(
        id = id,
        name = name,
        enabled = enabled,
        cardId = cardId,
        entries = entries,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// —— 正则 ——

fun RegexScriptEntity.toDomain(): RegexScript = RegexScript(
        id = id,
        name = name,
        find = find,
        replace = replace,
        scopes = decode(scopesJson, listOf(RegexScope.AI)),
        timings = decode(timingsJson, listOf(RegexTiming.DISPLAY)),
        minDepth = minDepth,
        maxDepth = maxDepth,
        enabled = enabled,
        cardId = cardId,
        preset = preset,
    )

fun RegexScript.toEntity(): RegexScriptEntity = RegexScriptEntity(
        id = id,
        name = name,
        find = find,
        replace = replace,
        scopesJson = JsonInstant.encodeToString(
            ListSerializer(kotlinx.serialization.serializer<RegexScope>()), scopes,
        ),
        timingsJson = JsonInstant.encodeToString(
            ListSerializer(kotlinx.serialization.serializer<RegexTiming>()), timings,
        ),
        minDepth = minDepth,
        maxDepth = maxDepth,
        enabled = enabled,
        cardId = cardId,
        preset = preset,
    )

// —— 收藏 ——

fun FavoriteEntity.toDomain(): Favorite = Favorite(
        id = id,
        type = decode("\"$type\"", FavoriteType.MESSAGE),
        conversationId = conversationId,
        nodeId = nodeId,
        messageId = messageId,
        excerpt = excerpt,
        cardName = cardName,
        createdAt = createdAt.takeIf { it > 0 }?.let { kotlin.time.Instant.fromEpochMilliseconds(it) },
    )

fun Favorite.toEntity(): FavoriteEntity = FavoriteEntity(
        id = id,
        type = type.name.lowercase(),
        conversationId = conversationId,
        nodeId = nodeId,
        messageId = messageId,
        excerpt = excerpt,
        cardName = cardName,
        createdAt = createdAt?.toEpochMilliseconds() ?: System.currentTimeMillis(),
    )

// —— 摘要 ——

fun SummaryEntity.toDomain(): com.luzzymeow.luzzyrp.core.model.SummaryItem =
        com.luzzymeow.luzzyrp.core.model.SummaryItem(
            id = id,
            conversationId = conversationId,
            level = when (level) {
                "a" -> com.luzzymeow.luzzyrp.core.model.SummaryLevel.A
                "b" -> com.luzzymeow.luzzyrp.core.model.SummaryLevel.B
                else -> com.luzzymeow.luzzyrp.core.model.SummaryLevel.C
            },
            roundIndex = roundIndex,
            content = content,
            createdAt = createdAt,
        )

fun com.luzzymeow.luzzyrp.core.model.SummaryItem.toEntity(): SummaryEntity = SummaryEntity(
        id = id,
        conversationId = conversationId,
        level = when (level) {
            com.luzzymeow.luzzyrp.core.model.SummaryLevel.A -> "a"
            com.luzzymeow.luzzyrp.core.model.SummaryLevel.B -> "b"
            com.luzzymeow.luzzyrp.core.model.SummaryLevel.C -> "c"
        },
        roundIndex = roundIndex,
        content = content,
        createdAt = createdAt,
    )

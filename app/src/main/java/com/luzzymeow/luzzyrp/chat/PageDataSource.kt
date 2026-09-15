package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.data.legacy.MigrationReport
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **页面取数层**（批 C C1）：把「五个假数据页」要的真实数据从库里取出来。
 *
 * ## 为什么单独立一层（而不是写在各个 Composable 里）
 *
 * 与 [PromptInputSource] 同一条纪律：
 * - **可测**：聚合算术是纯函数（[UsageAggregate]/[MemoryStats]），这一层只做「读 + 映射」；
 * - **一处口径**：五个页面若各自读库，很容易出现「用量页说 9 次、记忆页说 12 条」这种
 *   同一份数据两种数法的问题；
 * - **降级可预期**：读失败一律给**空结果**而不是抛（页面显示「还没有数据」），
 *   而不是白屏或崩溃——这一条在真机上比在单测里重要得多。
 *
 * ## 本轮**不做**的事（如实登记，别当成已完成）
 *
 * 页面**视觉**（排版、亮/暗、可见性）不在本轮范围：无真机时任何「看得见」的声明都是空的。
 * 本类只保证「页面拿到的数字来自真实库」。真机可见性判据在 `HANDOFF-p5-static.md` B 栏。
 */
class PageDataSource(private val store: LuzzyStore) {

    /** 用量聚合（读 `token_usage_history` 全量）。读失败 → 空汇总（页面显示「还没有记录」）。 */
    suspend fun usage(): UsageAggregate.Summary = runCatching {
        val records = store.records(LuzzyStore.RECORD_USAGE)
            .mapNotNull { UsageAggregate.Record.from(it) }
        UsageAggregate.summarize(records)
    }.getOrElse { UsageAggregate.summarize(emptyList()) }

    /**
     * 用量**原始记录**（趋势图与筛选要逐条看时间戳/供应商/模型，聚合后的分桶不够用）。
     *
     * 与 [usage] 读同一份数据、同一层解析（都走 [UsageAggregate.Record.from]），
     * 所以「总计说 9 次、图表说 8 次」这类口径分叉不会发生。
     */
    suspend fun usageRecords(): List<UsageAggregate.Record> = runCatching {
        store.records(LuzzyStore.RECORD_USAGE).mapNotNull { UsageAggregate.Record.from(it) }
    }.getOrElse { emptyList() }

    /**
     * 清空用量记录（**破坏性操作**，调用方必须先过确认框）。
     *
     * 返回清掉的条数：返回 0 说明本来就没有——不谎报「已清空 N 条」。
     * 只动 `token_usage_history` 这一种记录，**不碰**会话/记忆/世界书。
     */
    suspend fun clearUsage(): Int = runCatching {
        val count = store.records(LuzzyStore.RECORD_USAGE).size
        store.replaceRecords(LuzzyStore.RECORD_USAGE, "", emptyList())
        count
    }.getOrDefault(0)


    /**
     * 迁移报告（D3）：迁移成功后写的计数 + 时间；**没迁移过 → null**（界面显式呈现
     * 「未迁移」，不许当空表渲染）。读失败也按未迁移降级（与全层「读失败给空结果」同口径）。
     */
    suspend fun migrationReport(): MigrationReport? = runCatching {
        MigrationReport.parse(
            counts = store.json(LuzzyStore.KEY_LEGACY_MIGRATION_COUNTS),
            migratedAtRaw = store.string(LuzzyStore.KEY_LEGACY_MIGRATED_AT),
        )
    }.getOrNull()

    /**
     * 记忆统计：**向量分片**与**总结记忆**各给一份（页面分两段展示，与上游两种模式同义）。
     *
     * ## 作用域是「角色 × **当前分支**」，不是「角色 × 主线」
     *
     * 这是本轮**自己踩到的一个真缺陷**：第一版写的是 `ScopeId(uuid)`（默认落到主线 `main`），
     * 而会话的本体是 (角色, 分支) 这一对——用户的活跃会话**经常不在主线上**
     * （仪器化夹具里活跃分支就是 `b1`）。落在主线上会得到「记忆页说 0 条、对话里明明有记忆」
     * 这种**静默错数**：不报错、不崩溃，只是数字永远不对。
     *
     * 所以 [branchId] 必须由调用方从活跃会话取；取不到时才回落主线
     * （`ScopeId` 的默认值就是 `main`，与上游「主线 = 裸 uuid」同）。
     *
     * 没有当前角色时返回空——不把全部角色的记忆混在一起算（那会让用户以为总共有那么多）。
     */
    suspend fun memory(characterUuid: String?, branchId: String? = null): MemoryPair {
        val uuid = characterUuid?.takeIf { it.isNotBlank() } ?: return MemoryPair.empty()
        return runCatching {
            val scope = ScopeId(uuid, branchId?.takeIf { it.isNotBlank() } ?: MAIN_BRANCH)
            MemoryPair(
                vector = MemoryStats.summarize(
                    store.memories(scope, LuzzyStore.MEMORY_VECTOR).mapNotNull { MemoryStats.entryFrom(it) },
                ),
                classic = MemoryStats.summarize(
                    store.memories(scope, LuzzyStore.MEMORY_CLASSIC).mapNotNull { MemoryStats.entryFrom(it) },
                ),
            )
        }.getOrElse { MemoryPair.empty() }
    }

    /**
     * 角色列表（真列表 + 头像路径）。
     *
     * 只取**页面要显示的三样**（名字 / 头像 / 是不是当前角色），不把整张卡搬给界面——
     * payload 可能有几百 KB（含内联头像），传给 Compose 只会让它每帧比较一份大对象。
     */
    suspend fun characters(activeUuid: String?): List<CharacterRow> = runCatching {
        store.characters()
            .map { row ->
                CharacterRow(
                    uuid = row.uuid,
                    name = row.name.ifBlank { "未命名角色" },
                    avatarPath = row.avatarPath,
                    isActive = row.uuid == activeUuid,
                )
            }
            // 稳定排序：当前角色最前，其余按名字（否则每次重组顺序可能变 → 列表跳动）
            .sortedWith(compareByDescending<CharacterRow> { it.isActive }.thenBy { it.name })
    }.getOrElse { emptyList() }

    // ───────────────────────── 角色卡页（v3.2 重建） ─────────────────────────

    /**
     * 角色卡片的完整投影（含世界书/正则条数、收藏、描述）。
     *
     * 与 [characters] 的分工：那个是给**别处**（记忆页的作用域下拉）用的轻量行；
     * 这里是角色卡页自己的行——它要显示卡面与徽标，读的是 payload 里的派生字段。
     */
    suspend fun characterCards(): List<CharacterCards.Row> = runCatching {
        val active = activeCharacter()
        CharacterCards.sorted(
            store.characters().map { row ->
                CharacterCards.parse(
                    uuid = row.uuid,
                    name = row.name,
                    avatarPath = row.avatarPath,
                    payload = row.payload,
                    isActive = row.uuid == active,
                    createdAt = row.createdAt,
                )
            },
        )
    }.getOrElse { emptyList() }

    /**
     * 删除一张角色卡（级联清它的全部会话数据，见 `LuzzyStore.deleteCharacter`）。
     *
     * 若删的是当前角色，**顺手清掉 active 标记**：否则下一次进聊天页会去读一个不存在的角色，
     * 表现为「聊天页空着但也没报错」。
     */
    suspend fun deleteCharacter(uuid: String): Boolean = runCatching {
        val wasActive = activeCharacter() == uuid
        store.deleteCharacter(uuid)
        if (wasActive) store.remove(LuzzyStore.KEY_ACTIVE_CHARACTER)
        true
    }.getOrDefault(false)

    /** 收藏 / 取消收藏（写 payload 的 `favoriteAt`，其余键逐字保留）。 */
    suspend fun setCharacterFavorite(uuid: String, favorite: Boolean): Boolean = runCatching {
        val row = store.character(uuid) ?: return@runCatching false
        val payload = CharacterCards.withFavorite(row.payload, favorite, System.currentTimeMillis())
        store.upsertCharacter(row.copy(payload = payload))
        true
    }.getOrDefault(false)

    /** 切换当前角色（聊天页与记忆页都认 `kv[active.characterUuid]`）。 */
    suspend fun setActiveCharacter(uuid: String): Boolean = runCatching {
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, uuid)
        true
    }.getOrDefault(false)


    /** 当前角色 uuid（`kv[active.characterUuid]`；null = 空库演示态）。 */
    suspend fun activeCharacter(): String? = runCatching {
        store.string(LuzzyStore.KEY_ACTIVE_CHARACTER)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ───────────────────────── 记忆内容（v3.2 记忆页重建） ─────────────────────────

    /**
     * 某作用域下某一形态的**全部可操作条目**（按轮次排序，纯投影）。
     *
     * 与 [memory]（只给统计）的分工：统计卡要的是数字，管理器要的是**每一条**。
     * 两个入口读的是同一份数据，口径不会分叉（都走 [MemoryBrowser] / [MemoryStats]）。
     */
    suspend fun memoryItems(
        characterUuid: String?,
        branchId: String?,
        kind: String,
    ): List<MemoryBrowser.Item> {
        val scope = scopeOf(characterUuid, branchId) ?: return emptyList()
        return runCatching {
            MemoryBrowser.items(store.memories(scope, kind), kind)
        }.getOrElse { emptyList() }
    }

    /** 启停一条（写 payload + 列两处，见 [MemoryBrowser.setEnabled]）。 */
    suspend fun setMemoryEnabled(
        characterUuid: String?,
        branchId: String?,
        kind: String,
        id: String,
        enabled: Boolean,
    ): Boolean = mutateMemories(characterUuid, branchId, kind) {
        MemoryBrowser.setEnabled(it, kind, id, enabled)
    }

    /** 改写一条的正文。 */
    suspend fun setMemoryText(
        characterUuid: String?,
        branchId: String?,
        kind: String,
        id: String,
        text: String,
    ): Boolean = mutateMemories(characterUuid, branchId, kind) {
        MemoryBrowser.setText(it, kind, id, text)
    }

    /** 删掉一条。 */
    suspend fun deleteMemory(
        characterUuid: String?,
        branchId: String?,
        kind: String,
        id: String,
    ): Boolean = mutateMemories(characterUuid, branchId, kind) {
        MemoryBrowser.remove(it, kind, id)
    }

    /**
     * 清空某作用域的记忆（**两种形态一起**，与旧版「清空此角色记忆」同义）。
     *
     * 返回清掉的条数（页面提示要报数；返回 0 说明本来就没有——不谎报「已清空 N 条」）。
     */
    suspend fun clearMemories(characterUuid: String?, branchId: String?): Int {
        val scope = scopeOf(characterUuid, branchId) ?: return 0
        return runCatching {
            val vector = store.memories(scope, LuzzyStore.MEMORY_VECTOR).size
            val classic = store.memories(scope, LuzzyStore.MEMORY_CLASSIC).size
            store.replaceMemories(scope, LuzzyStore.MEMORY_VECTOR, emptyList())
            store.replaceMemories(scope, LuzzyStore.MEMORY_CLASSIC, emptyList())
            vector + classic
        }.getOrDefault(0)
    }

    /**
     * 记忆管理器的两个下拉（角色 + 该角色的分支）。
     *
     * 只列**真的存在**的分支：旧数据里某些角色只有主线，此时下拉不显示分支项
     * （旧版同义——分支选项多于一个才渲染分支选择器）。
     */
    suspend fun memoryScopes(): List<MemoryScopeOption> = runCatching {
        val active = activeCharacter()
        store.characters().map { character ->
            val branches = runCatching { store.branches(character.uuid) }.getOrDefault(emptyList())
            MemoryScopeOption(
                uuid = character.uuid,
                name = character.name.ifBlank { "未命名角色" },
                isActive = character.uuid == active,
                branches = branches.map {
                    BranchOption(id = it.branchId, name = it.name.ifBlank { "分支" }, isMain = it.isMain)
                },
                activeBranchId = runCatching { store.activeBranchId(character.uuid) }.getOrNull() ?: MAIN_BRANCH,
            )
        }
            // 稳定排序：当前角色最前，其余按名字（与 characters() 同一纪律：列表不许自己换序）
            .sortedWith(compareByDescending<MemoryScopeOption> { it.isActive }.thenBy { it.name })
    }.getOrElse { emptyList() }

    /** 召回设置（记忆页「记忆引擎」卡）。 */
    suspend fun recallOptions(): RecallOptions = runCatching {
        RecallOptions.from(store.json(LuzzyStore.KEY_MEMORY_SETTINGS) as? JsonObject)
    }.getOrDefault(RecallOptions())

    /**
     * 写回召回设置。
     *
     * **只替换 `recall` 子对象**（[RecallOptions.mergeInto]）：`memorySettings` 里还有迁移进来的
     * `emptyTurns` 等字段，整对象覆盖会把它们抹掉。
     */
    suspend fun saveRecallOptions(options: RecallOptions): Boolean = runCatching {
        val current = store.json(LuzzyStore.KEY_MEMORY_SETTINGS) as? JsonObject
        store.putJson(LuzzyStore.KEY_MEMORY_SETTINGS, RecallOptions.mergeInto(current, options))
        true
    }.getOrDefault(false)

    /**
     * 检索测试：拿**真实会话历史**跑一次召回，返回命中（记忆页「检索」按钮）。
     *
     * 与发送路径**同源**：都走 `RecallEngine.turnsOf`（快照不算一轮）+ 同一个 [RecallOptions]。
     * 页面上的「相关度」因此与思考节点里那行数字是同一套算法，不是另做一份演示。
     */
    suspend fun recallPreview(
        characterUuid: String?,
        branchId: String?,
        query: String,
        options: RecallOptions,
    ): List<RecallEngine.Hit> {
        val scope = scopeOf(characterUuid, branchId) ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return runCatching {
            val history = store.messages(scope).mapNotNull { row ->
                when (row.role) {
                    "user" -> LlmMessage(LlmRole.USER, row.content)
                    "assistant" -> LlmMessage(LlmRole.ASSISTANT, row.content)
                    // 快照行（role = 'snapshot'）不是对话：混进来会让轮号虚高、片段是噪声
                    else -> null
                }
            }
            options.search(RecallEngine.turnsOf(history), query)
        }.getOrElse { emptyList() }
    }

    /** 当前作用域的用户轮数（检索卡的抬头行；0 = 还没有可检索的历史）。 */
    suspend fun turnCount(characterUuid: String?, branchId: String?): Int {
        val scope = scopeOf(characterUuid, branchId) ?: return 0
        return runCatching { store.messages(scope).count { it.role == "user" } }.getOrDefault(0)
    }

    /** 「按 id 改写某一形态的整组」——读 → 改 → 写整组（纯函数在 [MemoryBrowser]）。 */
    private suspend fun mutateMemories(
        characterUuid: String?,
        branchId: String?,
        kind: String,
        transform: (List<JsonElement>) -> List<JsonElement>,
    ): Boolean {
        val scope = scopeOf(characterUuid, branchId) ?: return false
        return runCatching {
            store.replaceMemories(scope, kind, transform(store.memories(scope, kind)))
            true
        }.getOrDefault(false)
    }

    /** 作用域（uuid + 分支）；uuid 缺失返回 null（页面此时显示「还没有角色」）。 */
    private fun scopeOf(characterUuid: String?, branchId: String?): ScopeId? {
        val uuid = characterUuid?.takeIf { it.isNotBlank() } ?: return null
        return ScopeId(uuid, branchId?.takeIf { it.isNotBlank() } ?: MAIN_BRANCH)
    }


    /**
     * 某角色当前所在的**活跃分支**（`branch_meta.activeBranchId`；取不到回落主线）。
     *
     * 记忆页必须用它——见 [memory] 的说明（会话 76 踩到的静默错数）。
     */
    suspend fun activeBranch(characterUuid: String): String = runCatching {
        store.activeBranchId(characterUuid)?.takeIf { it.isNotBlank() } ?: MAIN_BRANCH
    }.getOrElse { MAIN_BRANCH }

    /** 会话总量：角色数 / 分支数 / 消息数（记忆页与设置页的抬头行）。 */
    suspend fun conversationTotals(): Totals = runCatching {
        Totals(
            characters = store.characterCount(),
            branches = store.allBranches().size,
            messages = store.scopeStats().sumOf { it.messageCount },
        )
    }.getOrElse { Totals(0, 0, 0) }

    /** 用户档案（设置页绑定 → 喂 A2 的用户信息块）。 */
    suspend fun userProfile(): PromptAssembler.UserView = runCatching {
        val json = store.json(LuzzyStore.KEY_USER) as? JsonObject ?: return@runCatching PromptAssembler.UserView()
        PromptAssembler.UserView(
            name = json.text("name"),
            description = json.text("description"),
            preferences = json.text("preferences"),
        )
    }.getOrElse { PromptAssembler.UserView() }

    /** 角色卡页的一行（只带显示所需字段）。 */
    data class CharacterRow(
        val uuid: String,
        val name: String,
        val avatarPath: String?,
        val isActive: Boolean,
    )

    /** 两种记忆形态的统计。 */
    data class MemoryPair(val vector: MemoryStats.Summary, val classic: MemoryStats.Summary) {
        companion object {
            fun empty() = MemoryPair(MemoryStats.summarize(emptyList()), MemoryStats.summarize(emptyList()))
        }
    }

    /** 会话总量。 */
    data class Totals(val characters: Int, val branches: Int, val messages: Int)
}

/**
 * 记忆管理器的作用域候选（一个角色 + 它的分支清单）。
 *
 * [activeBranchId] 是该角色**当前活跃**的分支（`branch_meta`），记忆页切换角色时用它做默认值——
 * 否则切过去会落在主线上，而用户的会话往往不在主线（会话 76 踩过的静默错数）。
 */
data class MemoryScopeOption(
    val uuid: String,
    val name: String,
    val isActive: Boolean,
    val branches: List<BranchOption>,
    val activeBranchId: String,
)

/** 一个分支（记忆管理器的作用域下拉项）。 */
data class BranchOption(val id: String, val name: String, val isMain: Boolean)

/** 取字符串字段；缺 / null / 非字符串都给空串。 */
private fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** 主线分支 id（上游约定：主线 = 裸 uuid，分支才带后缀）。 */
private const val MAIN_BRANCH = "main"

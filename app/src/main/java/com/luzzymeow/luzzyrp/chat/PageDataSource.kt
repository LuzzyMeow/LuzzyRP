package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
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

    /** 当前角色 uuid（`kv[active.characterUuid]`；null = 空库演示态）。 */
    suspend fun activeCharacter(): String? = runCatching {
        store.string(LuzzyStore.KEY_ACTIVE_CHARACTER)?.takeIf { it.isNotBlank() }
    }.getOrNull()

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

/** 取字符串字段；缺 / null / 非字符串都给空串。 */
private fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** 主线分支 id（上游约定：主线 = 裸 uuid，分支才带后缀）。 */
private const val MAIN_BRANCH = "main"

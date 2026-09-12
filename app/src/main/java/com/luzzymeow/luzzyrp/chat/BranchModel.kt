package com.luzzymeow.luzzyrp.chat

/**
 * 剧情分支（**上游语义**：一次「会话」= 角色 × 剧情分支）。
 *
 * 命名与字段对齐上游 `data-services.js` 的 branch 对象（`id / name / parentId /
 * createdAt / forkFloor / floorCount / wordCount`），存储键形态同为
 * `rp_hub_chat_{角色}__branch__{分支}`——P4 数据层按此粒度整体存取即可。
 *
 * 数值纪律：楼数与字数**一律由真实消息算出**（见 [BranchStat.of]），不预置、不编造。
 */
data class ChatBranch(
    val id: String,
    val name: String,
    /** 父分支 id；主线为 null。 */
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val isMain: Boolean = false,
    /** 从父分支的第几楼分叉（主线为 null）。 */
    val forkFloor: Int? = null,
) {
    companion object {
        const val MainId = "main"

        /** 上游限制：分支名 ≤ 30 字（ui-components.js 编辑弹窗同值）。 */
        const val MaxNameLength = 30

        fun main(name: String = "主线") = ChatBranch(
            id = MainId,
            name = name,
            parentId = null,
            createdAt = 0L,
            isMain = true,
            forkFloor = null,
        )
    }
}

/** 分支的实时统计（真实消息产出；仅供展示与排序参考）。 */
data class BranchStat(val floorCount: Int, val wordCount: Int) {
    /** 上游口径：过万显示「x.x 万字」，否则「N 字」。 */
    val wordCountLabel: String
        get() = if (wordCount >= 10_000) "%.1f 万字".format(wordCount / 10_000.0) else "$wordCount 字"

    val floorLabel: String get() = "$floorCount 楼"

    companion object {
        val Empty = BranchStat(0, 0)

        /** 从真实消息文本算统计（楼数 = 消息数；字数 = 字符数，上游同口径）。 */
        fun of(texts: List<String>): BranchStat = BranchStat(
            floorCount = texts.size,
            wordCount = texts.sumOf { it.length },
        )
    }
}

/**
 * 分支集合（不可变；所有操作返回新实例，便于 Compose 状态与单测）。
 *
 * 排序与上游一致：**主线优先，其余按创建时间升序**。
 * 约束：主线不可改名、不可删除（上游同样禁用主线的编辑与删除）。
 */
data class BranchTree(
    val branches: List<ChatBranch>,
    val activeId: String = ChatBranch.MainId,
) {
    init {
        require(branches.any { it.id == ChatBranch.MainId }) { "分支集合必须含主线" }
    }

    val active: ChatBranch
        get() = branches.firstOrNull { it.id == activeId } ?: branches.first()

    /** 展示顺序：主线 → 其余按创建时间（同级内稳定）。 */
    fun sorted(): List<ChatBranch> = branches.sortedWith(
        compareByDescending<ChatBranch> { it.isMain }.thenBy { it.createdAt },
    )

    /** 血统深度（主线 0，子分支 1…），用于列表缩进表达层级。 */
    fun depthOf(id: String): Int {
        var depth = 0
        var current = branches.firstOrNull { it.id == id } ?: return 0
        val guard = mutableSetOf(current.id)
        while (current.parentId != null) {
            val parent = branches.firstOrNull { it.id == current.parentId } ?: break
            if (!guard.add(parent.id)) break // 防御：数据异常时不死循环
            depth++
            current = parent
        }
        return depth
    }

    fun childrenOf(id: String): List<ChatBranch> = branches.filter { it.parentId == id }

    /** 改名：主线不可改；超长按上限截断（上游 30 字限制同语义）。 */
    fun rename(id: String, name: String): BranchTree {
        val target = branches.firstOrNull { it.id == id } ?: return this
        if (target.isMain) return this
        val trimmed = name.trim().take(ChatBranch.MaxNameLength)
        if (trimmed.isEmpty()) return this
        return copy(branches = branches.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    /** 新建子分支（从 [forkFloor] 楼分叉）；id 重复时返回原状。 */
    fun addChild(
        parentId: String,
        id: String,
        name: String,
        forkFloor: Int,
        createdAt: Long,
    ): BranchTree {
        if (branches.any { it.id == id }) return this
        val parent = branches.firstOrNull { it.id == parentId } ?: return this
        val child = ChatBranch(
            id = id,
            name = name.trim().take(ChatBranch.MaxNameLength).ifEmpty { "新分支" },
            parentId = parent.id,
            createdAt = createdAt,
            isMain = false,
            forkFloor = forkFloor,
        )
        return copy(branches = branches + child)
    }

    /** 删除分支及其所有后代；主线不可删。当前分支被删时回退到主线。 */
    fun delete(id: String): BranchTree {
        val target = branches.firstOrNull { it.id == id } ?: return this
        if (target.isMain) return this
        val doomed = mutableSetOf(id)
        var changed = true
        while (changed) {
            changed = false
            branches.forEach { b ->
                if (b.parentId != null && b.parentId in doomed && doomed.add(b.id)) changed = true
            }
        }
        val kept = branches.filterNot { it.id in doomed }
        return copy(
            branches = kept,
            activeId = if (activeId in doomed) ChatBranch.MainId else activeId,
        )
    }

    fun switchTo(id: String): BranchTree =
        if (branches.any { it.id == id }) copy(activeId = id) else this

    companion object {
        fun single(): BranchTree = BranchTree(listOf(ChatBranch.main()))
    }
}

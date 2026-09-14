package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.world.WorldEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 世界书**定时效果**（v3.1，对齐 SillyTavern `WorldInfoTimedEffects`）。
 *
 * ## 三种效果（单位 = 消息条数，0 = 关）
 *
 * - **粘性 sticky**：激活后保持 N 条消息；**粘性期内忽略概率检查**（ST 规则）；
 * - **冷却 cooldown**：激活后 N 条消息内不能再激活；**粘性结束时立即开始冷却**（ST 规则）；
 * - **延迟 delay**：聊天消息数 < N 时**不能**激活（`delay = 1` → 空聊天不能激活）。
 *
 * ## 时间线（ST 官方文档示例，本实现的验收样例）
 *
 * `sticky=3, cooldown=2, delay=2`：
 * ```
 * M0 delay（消息数不够）
 * M1 激活
 * M2 sticky / M3 sticky / M4 sticky
 * M5 cooldown / M6 cooldown
 * M7 可再次激活
 * ```
 *
 * ## 状态放哪（**不进消息序列**）
 *
 * 状态是「跨轮的记忆」，但它**不能**变成消息——那会改写已进历史的字节、让每轮前缀断裂。
 * 因此由调用方持久化在 `kv[scopeId]["worldbook.timedEffects"]`（见 `LoreBookRepository` 的兄弟路径），
 * 本对象只做**纯函数推进**：输入旧状态 + 条目 + 当前消息数，输出新状态。可 JVM 单测。
 *
 * ## 与 ST 的差异（如实登记）
 *
 * ST 把效果记在「聊天元数据」里并让**分支继承父聊天状态**；我们本轮**分支各自独立**
 * （复制分支不带走效果状态）——继承语义要等分支复制链路一起改（见 PLAN §2.4）。
 *
 * ST 用条目 `hash` 标识效果归属；我们直接用**书 id + 条目在书内的下标**组成的 key
 * （`entryKey` 由调用方给出），避免内容一改 hash 就变的脆弱性——条目被编辑时
 * 由调用方显式 `forget`（ST 的「修改条目强制移除效果」语义，在 UI 保存路径触发）。
 */
object TimedEffects {

    /** 一个生效窗口（消息序号，闭开区间 `[start, end)` 语义按 ST：`chat.length >= end` 即结束）。 */
    data class Window(val start: Int, val end: Int, val protected: Boolean = false)

    /**
     * 状态：`key → 窗口`。key 由调用方定义（推荐 `"<bookId>#<slot>"`）。
     */
    data class State(
        val sticky: Map<String, Window> = emptyMap(),
        val cooldown: Map<String, Window> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = sticky.isEmpty() && cooldown.isEmpty()
    }

    /** 本轮某条目的判定结果。 */
    data class Verdict(
        /** 因粘性而**直接激活**（调用方应跳过概率检查）。 */
        val stickyActive: Boolean,
        /** 因冷却（或延迟）而**本轮不可激活**。 */
        val blocked: Boolean,
    )

    /**
     * 本轮判定（**纯函数**）：查该条目的粘性/冷却窗口。
     *
     * @param chatLength 当前聊天的消息条数（含 greeting；用于 delay 与窗口判定）
     */
    fun check(state: State, key: String, chatLength: Int): Verdict {
        val stickyWindow = state.sticky[key]
        if (stickyWindow != null && chatLength < stickyWindow.end) {
            return Verdict(stickyActive = true, blocked = false)
        }
        val cooldownWindow = state.cooldown[key]
        if (cooldownWindow != null && chatLength < cooldownWindow.end) {
            return Verdict(stickyActive = false, blocked = true)
        }
        return Verdict(stickyActive = false, blocked = false)
    }

    /**
     * 推进状态（**纯函数**）：清理过期窗口 + 为新激活的条目种下窗口。
     *
     * @param entries 本轮**全部候选条目**（`key to 条目`）——粘性结束时需要查它的 cooldown 配置，
     *        而那一轮该条目通常**不在** [activated] 里（它已经不再靠关键词激活了），
     *        所以必须从全量条目里查，否则「粘性→冷却」这条 ST 规则会静默失效
     * @param activated 本轮**实际写入请求**的条目（`key to 条目`）
     * @return 新状态（调用方负责持久化）
     */
    fun advance(
        state: State,
        chatLength: Int,
        entries: List<Pair<String, WorldEntry>> = emptyList(),
        activated: List<Pair<String, WorldEntry>> = emptyList(),
    ): State {
        val configOf = entries.toMap()

        // ① 清理：窗口过期 → 移除；粘性结束且配了冷却 → **立即转入冷却**（ST 规则）
        val nextSticky = state.sticky.filterValues { chatLength < it.end }.toMutableMap()
        val nextCooldown = state.cooldown.filterValues { chatLength < it.end }.toMutableMap()

        state.sticky.forEach { (key, window) ->
            if (chatLength < window.end) return@forEach
            val cooldownLength = configOf[key]?.cooldown ?: return@forEach
            if (cooldownLength > 0 && key !in nextCooldown && activated.none { it.first == key }) {
                nextCooldown[key] = Window(chatLength, chatLength + cooldownLength)
            }
        }

        // ② 种下 / 续期：**已存在的窗口不刷新时长**（ST 规则：重复触发不刷新）
        activated.forEach { (key, entry) ->
            if (entry.sticky > 0 && key !in nextSticky) {
                nextSticky[key] = Window(chatLength, chatLength + entry.sticky)
            }
            if (entry.cooldown > 0 && key !in nextCooldown && entry.sticky <= 0) {
                // 有粘性时冷却在粘性结束后才开始（见 ①）；无粘性 → 立即进入
                nextCooldown[key] = Window(chatLength, chatLength + entry.cooldown)
            }
        }

        return State(nextSticky, nextCooldown)
    }

    /**
     * 聊天**未推进**时清理（ST 规则：swipe / 删除最后一条 → 移除效果）。
     *
     * 判据：窗口的 `start >= chatLength` 且非 protected（ST 的 `#checkTimedEffectOfType` 同式）。
     */
    fun pruneOnRewind(state: State, chatLength: Int): State = State(
        sticky = state.sticky.filterValues { it.protected || it.start < chatLength },
        cooldown = state.cooldown.filterValues { it.protected || it.start < chatLength },
    )

    /**
     * 条目被编辑 → 强制移除其效果（ST 规则：修改条目会移除正在生效的定时效果）。
     *
     * @param key 被编辑条目的 key（也用于删除条目时清理）
     */
    fun forget(state: State, key: String): State = State(
        sticky = state.sticky - key,
        cooldown = state.cooldown - key,
    )

    /** 条目 key 的推荐构造（书 id + 书内下标 → 稳定、与内容无关）。 */
    fun keyOf(bookId: String, slot: Int): String = "$bookId#$slot"

    // ------------------------------------------------------------------ 序列化（持久化到 kv）

    /** 状态 → JSON（存 `kv["worldbook.timedEffects.<branchId>"]`）。 */
    fun toJson(state: State): JsonObject = JsonObject(
        mapOf(
            "sticky" to windowsToJson(state.sticky),
            "cooldown" to windowsToJson(state.cooldown),
        ),
    )

    fun fromJson(element: JsonElement?): State {
        val obj = element as? JsonObject ?: return State()
        return State(
            sticky = windowsFromJson(obj["sticky"]),
            cooldown = windowsFromJson(obj["cooldown"]),
        )
    }

    private fun windowsToJson(windows: Map<String, Window>): JsonObject = JsonObject(
        windows.mapValues { (_, window) ->
            JsonObject(
                mapOf(
                    "start" to JsonPrimitive(window.start),
                    "end" to JsonPrimitive(window.end),
                    "protected" to JsonPrimitive(window.protected),
                ),
            )
        },
    )

    private fun windowsFromJson(element: JsonElement?): Map<String, Window> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (key, value) ->
            val window = value as? JsonObject ?: return@mapNotNull null
            val start = (window["start"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@mapNotNull null
            val end = (window["end"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@mapNotNull null
            val isProtected = (window["protected"] as? JsonPrimitive)?.content == "true"
            key to Window(start, end, isProtected)
        }.toMap()
    }

    /** kv 键（分支作用域：ST 的「效果只作用于激活它的那条聊天」的等价物）。 */
    fun kvKey(branchId: String): String = "worldbook.timedEffects.$branchId"
}

package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.world.WorldBookRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 「生效」的取数层（A1b）：把角色 / 用户 / 预设 / 世界书从**真库**取出来，
 * 组成 [PromptAssembler.Input] 与本次激活的世界书条目。
 *
 * 单独成类而不是写在 `ChatPage` 里，理由是**可测**：这层要处理的形状问题不少——
 * 角色卡 payload 的键名、空库的演示态回落、世界书的激活扫描。
 * 放进 Compose 页面就只能靠仪器化测试覆盖，代价高得多。
 *
 * ## 本类**不**负责快照的 retained（会话 74 修正）
 *
 * A1b 时这里还按会话作用域在 `kv` 里记一行「上次已发出的快照文本」（`snapshot.<scope>`），
 * A6 重做时删掉了，因为那是错的：**日志本身就是真源**——最后一条快照消息就是上一个已发出的
 * 快照。kv 版本在「重新生成历史中段」与「快照落盘失败」两种情形下都会给出错误的去重判据
 * （详见 [RequestBuilder] 的类注释）。取数层只取数，不持有会话状态。
 *
 * **调用约定**：`bundle()` → [RequestBuilder.plan]（算请求与落盘顺序）→ 按 `Plan.appends`
 * 顺序落盘 → 再把 `Plan.request` 交给引擎。
 */
class PromptInputSource(
    private val store: LuzzyStore,
    private val sessions: ChatSessionRepository,
    private val presets: PresetRepository,
    private val worldBook: WorldBookRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 一次取全：组装输入所需的一切。 */
    data class Bundle(
        val input: PromptAssembler.Input,
        /** 本次**激活**的世界书条目（工具执行器也要用同一份）。 */
        val activatedWorldEntries: List<com.luzzymeow.luzzyrp.data.world.WorldEntry>,
        val characterUuid: String?,
        val branchId: String,
    )

    /**
     * 组装输入。
     *
     * @param characterUuid 当前角色（null = 空库演示态；此时不注入角色/世界书）
     * @param branchId 当前分支（决定快照 retained 的键）
     * @param history 既有对话（由调用方从界面状态构造，保持 `raw`）
     * @param userText 本轮输入
     * @param recentMessages 最近若干条消息正文（用于世界书扫描；按时间升序）
     */
    suspend fun bundle(
        characterUuid: String?,
        branchId: String,
        history: List<com.luzzymeow.luzzyrp.chat.llm.LlmMessage>,
        userText: String,
        recentMessages: List<String>,
    ): Bundle {
        val characterRow = characterUuid?.let { store.character(it) }
        val character = characterRow?.let { characterViewOf(it) }

        // 世界书：读全量（全局 + 角色绑定）→ 按当前设置扫描激活
        val settings = worldBook.settings()
        val rows = if (characterUuid != null) worldBook.load(characterUuid).rows else worldBook.load(null).rows
        val activated = WorldBookActivator.activate(
            rows = rows,
            recentMessages = recentMessages,
            settings = settings,
        )

        val input = PromptAssembler.Input(
            character = character,
            user = userInfo(),
            presets = presets.all().map { it.entry },
            worldEntries = activated,
            toolHint = TOOL_HINT,
            history = history,
            userText = userText,
            // retainedSnapshot / emitSnapshot 由 RequestBuilder.plan 统一决定（日志是唯一真源）
        )
        return Bundle(
            input = input,
            activatedWorldEntries = activated,
            characterUuid = characterUuid,
            branchId = branchId,
        )
    }

    // ------------------------------------------------------------------ 取数细节

    /**
     * 角色卡 → 组装视图。
     *
     * 字段名照上游（`name` / `description` / `personality` / `mes_example` / `first_mes`）；
     * payload 里可能有嵌套（V2 卡的 `data` 外壳），两种形态都认。
     */
    fun characterViewOf(row: CharacterEntity): PromptAssembler.CharacterView? {
        val payload = runCatching { json.parseToJsonElement(row.payload) }.getOrNull() as? JsonObject
            ?: return PromptAssembler.CharacterView(name = row.name)
        // V2 卡：{ data: { name, description, ... } }；旧形态：字段直接在顶层
        val source = (payload["data"] as? JsonObject) ?: payload
        return PromptAssembler.CharacterView(
            name = source.str("name")?.takeIf { it.isNotBlank() } ?: row.name,
            description = source.str("description").orEmpty(),
            personality = source.str("personality").orEmpty(),
            mesExample = source.str("mes_example").orEmpty(),
            firstMes = source.str("first_mes").orEmpty(),
        )
    }

    /**
     * 用户信息块的数据源：`kv["user"]`（迁移进来的用户档案）。
     *
     * 上游字段是 `name` / `description` / `preferences`（`built-in-content.js:59-64`）；
     * 没配过时返回空（[PromptAssembler.buildUserInfo] 会整块跳过，不注入空壳）。
     */
    suspend fun userInfo(): PromptAssembler.UserView {
        val element = store.json(LuzzyStore.KEY_USER) as? JsonObject ?: return PromptAssembler.UserView()
        return PromptAssembler.UserView(
            name = element.str("name").orEmpty(),
            description = element.str("description").orEmpty(),
            preferences = element.str("preferences").orEmpty(),
        )
    }

    /**
     * **生效的正则脚本**（上游 `combineRegexScriptsForCharacter`，`app.js:3143-3150`）：
     * 全局脚本在前 + 当前角色卡自带脚本在后，顺序即套用顺序。
     *
     * 上游的回落口径照搬：`global_regex` 存在就**只用它**（老键 `regex` 忽略，`app.js:2484-2488`），
     * 否则用 `regex` 那份当基础列表；角色卡里的 `scope === 'global'` 条目要排掉
     * （它们已经在全局那份里，不去重就会跑两遍）。
     *
     * 顺序不能改：正则脚本是**依次套用**的，前一条的产物是后一条的输入。
     *
     * @param characterUuid null（空库演示态）时只有全局脚本
     */
    suspend fun regexScripts(characterUuid: String?): List<RegexScript> {
        val global = store.records(LuzzyStore.RECORD_GLOBAL_REGEX)
        val base = if (global.isNotEmpty()) {
            RegexScript.fromAll(global, fallbackScope = "global")
        } else {
            // 老键 `regex`（全局/角色还没分家时的形态，`app.js:2486-2488`）
            RegexScript.fromAll(store.records(LuzzyStore.RECORD_REGEX))
        }
        val row = characterUuid?.let { store.character(it) } ?: return base
        val payload = runCatching { json.parseToJsonElement(row.payload) }.getOrNull() as? JsonObject
        val source = (payload?.get("data") as? JsonObject) ?: payload ?: return base
        val own = (source["regexScripts"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { RegexScript.from(it, fallbackScope = "character") }
            ?.filter { it.scope != "global" }
            .orEmpty()
        return base + own
    }

    /** `{{user}}` 的替换值（显示期正则与正文渲染都要用）。 */
    suspend fun userName(): String = userInfo().name

    companion object {
        /**
         * 工具使用提示（静态文本 → 属稳定块）。
         *
         * 措辞沿用演示时代那句的语义，但**去掉了具体设定名**（钟楼/苹果树是 Vanio 的设定，
         * 写进所有人物的 system 会让模型以为那是通用规则）。
         */
        const val TOOL_HINT: String =
            "回复涉及具体设定细节（时间、地点、人物关系、物品来历）时，可先调用世界书检索工具查证再回答；" +
                "查到的条目内容可直接用于叙述，不必复述工具调用过程。"
    }
}

/** 从 JsonObject 取字符串（缺 / null / 非字符串都给 null）。 */
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

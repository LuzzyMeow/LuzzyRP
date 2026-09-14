package com.luzzymeow.luzzyrp.data.world

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 世界书条目的**类型化视图**（纯 Kotlin，无 Android 依赖 → 可直接单测）。
 *
 * ## 读取：复刻上游 `normalizeWorldInfoEntry`（`core-utils.js:556-624`）
 *
 * 旧数据里同一个概念有多种写法，全部要认：
 * - `extensions` 对象里的键会**摊平**进条目（且**覆盖**同名顶层键——上游就是这顺序），然后删掉 `extensions`；
 * - 别名：`keys|key`、`use_regex|useRegex`、`insertion_order|order`、`scan_depth|scanDepth`、
 *   `useProbability|use_probability`；`disable|disabled` 是 `enabled` 的**取反**写法；
 * - `keys` 是字符串时按 `,` 或 `，` 拆开、去空白、丢空项；
 * - `position` 认 7 个合法值 + SillyTavern 风格别名 + 数字映射 `{0:before_char, 1:after_char, 2/3:global_note, 4:at_depth}`；
 * - 缺字段一律回落上游默认值（见 [WorldEntry] 的默认值）。
 *
 * ## 写入：[WorldEntry.mergeInto] 是 patch-merge，不是重建对象
 *
 * 以**原 payload 为底**，只覆盖本视图暴露的键 → 未暴露的键（含未知的、将来上游新增的）永不丢失。
 * **一处刻意的例外**：写入时会把上面那批**别名键删掉**。原因不是洁癖——上游读值时别名优先
 * （`getValue(['use_regex','useRegex'])` 先命中 `use_regex`），若不删，我们写进 `useRegex` 的新值
 * 会被旧别名盖住，表现为「改了没生效」。删别名 = 把表示归一，信息量不丢（`disable` 表达的就是
 * `!enabled`，而我们本来就会写 `enabled`）。
 *
 * `scope` 的**归属**判定（哪条算全局）不在这里做：那取决于条目**存在哪个桶**，由
 * [WorldBookOps]/`WorldBookRepository` 决定；本类只负责把 `scope` 字段原样读出来。
 */
data class WorldEntry(
    val comment: String = "",
    val content: String = "",
    val keys: List<String> = emptyList(),
    val enabled: Boolean = true,
    val scope: WorldScope = WorldScope.Character,
    val position: WorldPosition = WorldPosition.AtDepth,
    val order: Int = 0,
    val depth: Int = 4,
    /** `null` = 继承全局设置（上游默认）。 */
    val scanDepth: Int? = null,
    val probability: Int = 100,
    val useProbability: Boolean = true,
    val useRegex: Boolean = false,
    val constant: Boolean = false,
    // ---- v3.1（世界书对齐 SillyTavern，2026-09-15）：全部给默认值 → 旧数据零破坏 ----

    /** 次级关键词（ST `keysecondary`）：与主键按 [secondaryLogic] 组合。 */
    val secondaryKeys: List<String> = emptyList(),
    /** 次级关键词逻辑（ST `selectiveLogic`，数字映射 0/1/2/3）。 */
    val secondaryLogic: SecondaryLogic = SecondaryLogic.AndAny,
    /**
     * 区分大小写（ST `caseSensitive`，可条目级覆盖全局）。
     * `true` 时正则键不再强制加 `i`。
     */
    val caseSensitive: Boolean = false,
    /**
     * 全词匹配（ST `matchWholeWords`）。
     *
     * ⚠️ **默认 false = 有意偏离 ST**（ST 默认 true）：其官方文档明示「中日韩等不使用空格
     * 分词的语言应关闭，否则会匹配不到」。本应用用户以中文为主，默认开会让中文条目**静默失效**。
     * 界面上写明「中文建议关闭」。
     */
    val matchWholeWords: Boolean = false,
    /** `@Depth` 注入的角色（ST `@Depth` 三档）——按缓存安全路径落在尾部快照内。 */
    val depthRole: DepthRole = DepthRole.System,
    /** 粘性：激活后保持 N 条消息（0 = 关）。粘性期内**忽略概率**（ST 规则）。 */
    val sticky: Int = 0,
    /** 冷却：激活后 N 条消息内不能再激活（0 = 关）。粘性结束时立即开始冷却。 */
    val cooldown: Int = 0,
    /** 延迟：聊天消息数 < N 时不能激活（0 = 关；ST `delay=1` = 空聊天不能激活）。 */
    val delay: Int = 0,
) {

    /** 无关键词且非常驻 → 这条永远不会被触发。列表里如实提示，但不阻止保存。 */
    val neverTriggers: Boolean get() = !constant && keys.isEmpty()

    /** 没名字的条目也要能读——显示名回落，但**不写回**存储（不替用户编造名字）。 */
    val displayName: String get() = comment.ifBlank { "未命名条目" }

    /** `constant` 为真时不需要关键词；`useRegex` 为真时按正则匹配。 */
    val triggerSummary: String
        get() = when {
            constant -> "常驻：不匹配关键词"
            keys.isEmpty() -> "无关键词"
            useRegex -> "正则：${keys.joinToString("，")}"
            else -> "关键词：${keys.joinToString(" / ")}"
        }

    /**
     * patch-merge：把本视图的字段写回 [original]，**其余键原样保留**。
     *
     * 写出的键名一律用上游的规范名（`useRegex` 而不是 `use_regex`…），并把被取代的别名删掉。
     */
    fun mergeInto(original: JsonElement): JsonObject {
        val base = flattened(original).toMutableMap()
        ALIAS_KEYS.forEach { base.remove(it) }
        base["comment"] = JsonPrimitive(comment)
        base["content"] = JsonPrimitive(content)
        base["keys"] = JsonArray(keys.map { JsonPrimitive(it) })
        base["enabled"] = JsonPrimitive(enabled)
        base["scope"] = JsonPrimitive(scope.id)
        base["position"] = JsonPrimitive(position.id)
        base["order"] = JsonPrimitive(order)
        base["depth"] = JsonPrimitive(depth)
        base["scanDepth"] = scanDepth?.let { JsonPrimitive(it) } ?: JsonNull
        base["probability"] = JsonPrimitive(probability.coerceIn(0, 100))
        base["useProbability"] = JsonPrimitive(useProbability)
        base["useRegex"] = JsonPrimitive(useRegex)
        base["constant"] = JsonPrimitive(constant)
        // ---- v3.1 新字段（ST 对齐；写规范名，别名在 ALIAS_KEYS 里删） ----
        base["secondaryKeys"] = JsonArray(secondaryKeys.map { JsonPrimitive(it) })
        base["secondaryLogic"] = JsonPrimitive(secondaryLogic.id)
        base["caseSensitive"] = JsonPrimitive(caseSensitive)
        base["matchWholeWords"] = JsonPrimitive(matchWholeWords)
        base["depthRole"] = JsonPrimitive(depthRole.id)
        base["sticky"] = JsonPrimitive(sticky.coerceAtLeast(0))
        base["cooldown"] = JsonPrimitive(cooldown.coerceAtLeast(0))
        base["delay"] = JsonPrimitive(delay.coerceAtLeast(0))
        return JsonObject(base)
    }

    companion object {

        /** 上游 `systemWorldInfoNames`：这几条永远算全局（`core-utils.js:952`）。 */
        val SYSTEM_NAMES = setOf("自动生图")

        /** 写入时被规范名取代的别名键（读取时认，写回时删）。 */
        internal val ALIAS_KEYS = listOf(
            "key",
            "use_regex",
            "insertion_order",
            "scan_depth",
            "use_probability",
            "disable",
            "disabled",
            "extensions",
            // ---- v3.1：ST 的原生键名（读取时优先，写回时归一为我们的规范名） ----
            "keysecondary",
            "selectiveLogic",
            "role",
            "sticky_start",
            "cooldown_start",
        )

        fun from(element: JsonElement): WorldEntry {
            val obj = flattened(element)
            return WorldEntry(
                comment = obj.field("comment").asText(""),
                content = obj.field("content").asText(""),
                keys = parseKeys(obj.field("keys", "key")),
                enabled = obj.field("enabled").asBool(true) && !obj.field("disable", "disabled").asBool(false),
                scope = WorldScope.fromId(obj.field("scope").asTextOrNull()),
                position = WorldPosition.fromRaw(obj.field("position")),
                order = obj.field("insertion_order", "order").asInt(0) ?: 0,
                depth = obj.field("depth").asInt(4) ?: 4,
                scanDepth = obj.field("scan_depth", "scanDepth").asInt(null),
                probability = obj.field("probability").asInt(100) ?: 100,
                useProbability = obj.field("useProbability", "use_probability").asBool(true),
                useRegex = obj.field("use_regex", "useRegex").asBool(false),
                constant = obj.field("constant").asBool(false),
                // ---- v3.1 ----
                secondaryKeys = parseKeys(obj.field("secondaryKeys", "keysecondary")),
                secondaryLogic = SecondaryLogic.fromRaw(obj.field("secondaryLogic", "selectiveLogic")),
                caseSensitive = obj.field("caseSensitive").asBool(false),
                matchWholeWords = obj.field("matchWholeWords").asBool(false),
                depthRole = DepthRole.fromRaw(obj.field("depthRole", "role")),
                sticky = obj.field("sticky").asInt(0) ?: 0,
                cooldown = obj.field("cooldown").asInt(0) ?: 0,
                delay = obj.field("delay").asInt(0) ?: 0,
            )
        }

        /** `extensions` 摊平（其值**覆盖**同名顶层键——与上游一致）并删除该键。 */
        internal fun flattened(element: JsonElement): JsonObject {
            val obj = element as? JsonObject ?: return JsonObject(emptyMap())
            val extensions = obj["extensions"] as? JsonObject ?: return obj
            val merged = obj.toMutableMap()
            for ((key, value) in extensions) {
                if (value !== JsonNull) merged[key] = value
            }
            merged.remove("extensions")
            return JsonObject(merged)
        }

        /** 上游 `keys`：数组照收；字符串按 `,` / `，` 拆；其它形态算空。 */
        fun parseKeys(value: JsonElement?): List<String> = when (value) {
            null, is JsonNull -> emptyList()
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            is JsonPrimitive -> {
                if (!value.isString) emptyList()
                else value.content.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
            }
            else -> emptyList()
        }

        /** 编辑框 → keys（与 [parseKeys] 同一套分隔符）。 */
        fun keysFromText(text: String): List<String> = parseKeys(JsonPrimitive(text))

        fun keysToText(keys: List<String>): String = keys.joinToString("，")
    }
}

/** 条目归属（与存储桶一一对应）。 */
enum class WorldScope(val id: String, val label: String) {
    Global("global", "全局"),
    Character("character", "绑定当前角色"),
    ;

    companion object {
        fun fromId(raw: String?): WorldScope = if (raw == Global.id) Global else Character
    }
}

/**
 * 次级关键词逻辑（ST `selectiveLogic`：0/1/2/3）。
 *
 * | 值 | 语义（ST 官方文档原文） |
 * |---|---|
 * | [AndAny] | 主键命中 **且** 任一次级键命中 |
 * | [NotAll] | 主键命中，但次级键**不是全部**命中（全中则阻止激活） |
 * | [NotAny] | 主键命中 **且** 次级键**一个都不**命中 |
 * | [AndAll] | 主键命中 **且** 全部次级键都命中 |
 */
enum class SecondaryLogic(val id: String, val numeric: Int, val label: String) {
    AndAny("and_any", 0, "与任一（AND ANY）"),
    NotAll("not_all", 1, "非全部（NOT ALL）"),
    NotAny("not_any", 2, "非任一（NOT ANY）"),
    AndAll("and_all", 3, "与全部（AND ALL）"),
    ;

    companion object {
        fun fromId(raw: String?): SecondaryLogic? = entries.firstOrNull { it.id == raw }

        /** 数字（ST 存储形态）或字符串 id；认不出回落 [AndAny]（ST 默认）。 */
        fun fromRaw(value: JsonElement?): SecondaryLogic {
            if (value == null || value is JsonNull) return AndAny
            val text = (value as? JsonPrimitive)?.content ?: return AndAny
            if (!value.isString) {
                text.trim().toIntOrNull()?.let { n -> return entries.firstOrNull { it.numeric == n } ?: AndAny }
            }
            return fromId(text.lowercase().replace(' ', '_')) ?: AndAny
        }
    }
}

/**
 * `@Depth` 注入的角色（ST `@Depth` 三档：⚙️system / 👤user / 🤖assistant）。
 *
 * **落点是尾部快照内的分段标注，不是插进历史**——插历史会改写已进历史的字节、
 * 让每轮前缀从插入点断裂（详见 `docs/RESEARCH-worldbook-sillytavern.md` §4）。
 */
enum class DepthRole(val id: String, val label: String) {
    System("system", "系统（system）"),
    User("user", "用户（user）"),
    Assistant("assistant", "助手（assistant）"),
    ;

    companion object {
        fun fromId(raw: String?): DepthRole? = entries.firstOrNull { it.id == raw }

        fun fromRaw(value: JsonElement?): DepthRole {
            if (value == null || value is JsonNull) return System
            val text = (value as? JsonPrimitive)?.content ?: return System
            return fromId(text.lowercase().trim()) ?: System
        }
    }
}

/** 注入位置（上游 7 个合法值 + v3.1 的对话示例两档）。 */
enum class WorldPosition(val id: String, val label: String) {
    SystemTop("system_top", "系统提示词开头"),
    GlobalNote("global_note", "全局注释（system）"),
    BeforeChar("before_char", "角色描述之前"),
    AfterChar("after_char", "角色描述之后"),
    ExampleTop("example_top", "对话示例之前"),
    ExampleBottom("example_bottom", "对话示例之后"),
    AtDepth("at_depth", "按深度插入"),
    UserTop("user_top", "用户消息上方"),
    AssistantTop("assistant_top", "AI 消息上方"),
    ;

    companion object {

        /** SillyTavern 风格别名（上游 `positionAliases`）。 */
        private val ALIASES = mapOf(
            "before_character" to BeforeChar,
            "after_character" to AfterChar,
            "character_top" to BeforeChar,
            "character_bottom" to AfterChar,
            // v3.1：对话示例两档有真实锚点（角色块里的示例段前后），不再退化为角色描述前后
            "before_examples" to ExampleTop,
            "after_examples" to ExampleBottom,
            "example_top" to ExampleTop,
            "example_bottom" to ExampleBottom,
            "an_top" to GlobalNote,
            "author_note" to GlobalNote,
            "an_bottom" to GlobalNote,
        )

        private val NUMERIC = mapOf(
            0 to BeforeChar,
            1 to AfterChar,
            2 to GlobalNote,
            3 to GlobalNote,
            4 to AtDepth,
            // ST 的 5/6 = EM_top / EM_bottom
            5 to ExampleTop,
            6 to ExampleBottom,
        )

        fun fromId(raw: String?): WorldPosition? = entries.firstOrNull { it.id == raw }

        /** 字符串（小写、空格→下划线、先查别名）或数字；认不出回落 [AtDepth]（上游同）。 */
        fun fromRaw(value: JsonElement?): WorldPosition {
            if (value == null || value is JsonNull) return AtDepth
            val text = (value as? JsonPrimitive)?.content ?: return AtDepth
            if (!value.isString) text.toIntOrNull()?.let { return NUMERIC[it] ?: AtDepth }
            val normalized = text.lowercase().replace(' ', '_')
            return ALIASES[normalized] ?: fromId(normalized) ?: AtDepth
        }
    }
}

// -------------------------------------------------------------------------- 取值小工具
//
// 都是上游 `getValue(keys, fallback)` / `normalizeBoolean` / `toNumber` 的等价物：
// 「多个候选键按顺序取第一个存在的」「字符串 'false' 要当假」「数字与数字字符串都认」。

/** 取第一个**存在且非 JSON null** 的字段。 */
internal fun JsonObject.field(vararg names: String): JsonElement? {
    for (name in names) {
        val value = this[name]
        if (value != null && value !is JsonNull) return value
    }
    return null
}

/** 取文本；非文本类型回落 [fallback]。 */
internal fun JsonElement?.asText(fallback: String): String {
    if (this == null || this is JsonNull) return fallback
    return (this as? JsonPrimitive)?.content ?: fallback
}

/** 取文本；缺省 / null / 非文本一律 null（`scope` 这类「有就认、没有就按默认」的字段用）。 */
internal fun JsonElement?.asTextOrNull(): String? {
    if (this == null || this is JsonNull) return null
    return (this as? JsonPrimitive)?.content
}

/** 上游 `normalizeBoolean`：字符串 `'false'`/'true'` 要当真假；数字 0/非 0 按 JS 真值语义。 */
internal fun JsonElement?.asBool(fallback: Boolean): Boolean {
    if (this == null || this is JsonNull) return fallback
    val primitive = this as? JsonPrimitive ?: return fallback
    if (primitive.isString) {
        return when (primitive.content.lowercase()) {
            "false" -> false
            "true" -> true
            "" -> false
            else -> true
        }
    }
    return primitive.content != "false" && primitive.content != "0"
}

/** 上游 `toNumber`：数字与数字字符串都认（`"4"` / `4` / `4.0` → 4）；认不出回落 [fallback]。 */
internal fun JsonElement?.asInt(fallback: Int?): Int? {
    val primitive = this as? JsonPrimitive ?: return fallback
    val text = primitive.content
    return text.toIntOrNull()
        ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
        ?: fallback
}

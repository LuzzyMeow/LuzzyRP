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
) {

    /** 无关键词且非常驻 → 这条永远不会被触发。列表里如实提示，但不阻止保存。 */
    val neverTriggers: Boolean get() = !constant && keys.isEmpty()

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

/** 注入位置（上游 7 个合法值；标签见计划附录 B）。 */
enum class WorldPosition(val id: String, val label: String) {
    SystemTop("system_top", "系统提示词开头"),
    GlobalNote("global_note", "全局注释（system）"),
    BeforeChar("before_char", "角色描述之前"),
    AfterChar("after_char", "角色描述之后"),
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
            "before_examples" to BeforeChar,
            "after_examples" to AfterChar,
            "example_top" to BeforeChar,
            "example_bottom" to AfterChar,
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

package com.luzzymeow.luzzyrp.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **UI 模板变量块**（`<ui_template_updates>{json}</ui_template_updates>`）的剥除与解析。
 *
 * ## 为什么必须剥掉它（不是一个「可选的美化」）
 *
 * 这是上游的**隐藏指令通道**：模型在正文末尾追加一块 JSON，前端读了它去更新模板变量面板，
 * 而**块本身不属于正文**——上游的提示词原文就是这么写的：
 * 「在正文结束后追加一个隐藏变量更新块；变量块只给前端读取，不属于正文，不要在正文中提到它」
 * （`built-in-content.js:170-188`）。
 *
 * 我们此前没有消费方，于是这块 JSON **整段显示成文字**。用户看到的是模型在正文里念了一段
 * 配置数据——而且它出现的位置恰好是正文末尾，很容易被当成「模型抽风」。
 *
 * ## 移植的三条语义（都有出处，改动前先读那几行）
 *
 * | 语义 | 出处 |
 * |---|---|
 * | 只认**受保护区之外**的**最后一块** | `data-services.js:1652`（`findLastUnprotectedMatch`） |
 * | 从该块开标签起**到文末**都算块内（未闭合也认） | `data-services.js:1655-1656` |
 * | 剥除 = 取块之前的部分再 `trimEnd` | `data-services.js:1665-1669` |
 *
 * 「受保护区之外」这一条是**要点**：模型经常在正文里贴一段**示例**（代码围栏里的标签、
 * 或者引号里提到的协议文本）。那段是给人看的内容，不能因为长得像标签就把它连正文一起删掉。
 * 所以判定沿用 [RegexScripts.parts] 那套受保护区切分（HTML 块 / 注释 / 围栏 / 行内码 / 任意标签 /
 * 思考块），只在**非保护**的片段里找开标签。
 *
 * ## 与上游的一处**有意偏离**（如实登记）
 *
 * 上游的 `findLastUnprotectedMatch` 用 `includeUiTemplateUpdates: true` 让匹配器**看得见
 * 变量块自己的开标签**（`core-utils.js:319-321`）——因为那个块在正则层面属于「受保护内容」。
 * 我们照抄这个例外：找开标签时允许在「本来就是变量块」的保护片段里匹配，
 * 否则永远找不到块（它自己把自己保护掉了）。判据是**片段以开标签开头**，与上游逐字相同。
 *
 * ## 与上游的另一处偏离：非法 JSON **不进界面**
 *
 * 上游把 JSON 解析错误抛给调用方（`data-services.js:1678-1684` 造一个带 `jsonSource` 的
 * `SyntaxError`），由界面层决定怎么提示。我们是**剥除与解析分家**：
 * - [strip] 只做剥除，**永远不抛**——它跑在正文渲染链上，抛异常等于整条消息渲染不出来；
 * - [parse] 返回 [Parsed] 而不是抛：解析失败时 `updates` 为空、`error` 带上原因，
 *   由调用方决定记日志还是提示。无真机时这是唯一能验的形态（纯函数单测）。
 *
 * 本对象是**纯函数、无 Android 依赖**，可 JVM 单测。
 */
object UiTemplateUpdates {

    /** 开标签（上游 `data-services.js:1653` 同一条正则）。 */
    private val OPEN_TAG = Regex("""<ui_template_updates\b[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * 整个块的开闭（含未闭合到文末的形态）。
     *
     * 与 [RegexScripts] 里那条 PROTECTED 分支**故意保持同形**：两处描述的必须是同一件事，
     * 否则会出「这块算不算受保护」与「这块算不算变量块」两个答案。
     */
    private val FULL_BLOCK = Regex(
        """<ui_template_updates\b[^>]*>[\s\S]*?(?:</ui_template_updates\b[^>]*>|\z)""",
        RegexOption.IGNORE_CASE,
    )

    /** 闭标签（容忍属性，上游用的是 `</ui_template_updates>` 字面量，我们放宽一点点）。 */
    private val CLOSE_TAG = Regex("""</ui_template_updates\b[^>]*>""", RegexOption.IGNORE_CASE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 找到的变量块。[index] 是开标签在**原文**里的下标（剥除点）。 */
    data class Block(val index: Int, val raw: String, val body: String)

    /**
     * 剥除变量块（上游 `stripUiTemplateUpdateBlock`）。
     *
     * 「剥除」的定义就是**把块及其之后的一切都去掉**（`source.slice(0, match.index).trimEnd()`）：
     * 块之后若有正文，上游也一并不要——因为协议规定变量块在**正文结束后**追加，
     * 它后面本来就只该是空行。照抄这个语义，免得我们比上游多留一段谁也没预期的尾巴。
     *
     * 没有块 → **原样返回**（连 trim 都不做，避免把「用户正文本身就带前后空行」改掉）。
     */
    fun strip(text: String): String {
        val block = find(text) ?: return text
        // 上游用的是 trimEnd（不是 trimStart）：块之前的正文可能故意以空行开头
        return text.substring(0, block.index).trimEnd()
    }

    /**
     * 找**受保护区之外的最后一块**。
     *
     * 逆序扫描非保护片段：从最后一段往前找，第一段里出现开标签的就是它。
     * 这样「正文里先贴了一段示例、末尾才是真块」时，认的是末尾那一块。
     */
    fun find(text: String): Block? {
        if (text.isEmpty() || !text.contains("<ui_template_updates", ignoreCase = true)) return null
        val parts = RegexScripts.parts(text)
        // 逆序遍历：片段列表是顺序的，所以用倒序 index 拿到「原文里最靠后的那个候选」
        for (i in parts.indices.reversed()) {
            val part = parts[i]
            val searchable = if (part.protected) searchableOpenTag(part.text) else part.text
            if (searchable.isEmpty()) continue
            val match = lastOpenTagIn(searchable) ?: continue
            val index = offsetOf(parts, i) + match.range.first
            val raw = FULL_BLOCK.find(text, index)?.takeIf { it.range.first == index }?.value
                ?: text.substring(index)
            val body = raw
                .removePrefix(OPEN_TAG.find(raw)?.value ?: "")
                .let { CLOSE_TAG.replace(it, "") }
            return Block(index = index, raw = raw, body = body)
        }
        return null
    }

    /**
     * 保护片段里**可被搜索的部分**：只有「片段本身就是变量块开标签开头」时才放行，
     * 且只放行开标签那一段（上游 `core-utils.js:319-321` 的语义）。
     *
     * 为什么不能整段放行：保护片段可能是代码围栏——围栏里出现 `<ui_template_updates>`
     * 是**模型在讲协议**，那种情况必须当作正文（不能剥）。
     */
    private fun searchableOpenTag(partText: String): String =
        OPEN_TAG.find(partText)?.takeIf { it.range.first == 0 }?.value.orEmpty()

    /** 片段在原文里的起始下标（累加前面所有片段的长度）。 */
    private fun offsetOf(parts: List<RegexScripts.TextPart>, index: Int): Int {
        var offset = 0
        for (i in 0 until index) offset += parts[i].text.length
        return offset
    }

    /** 取字符串里**最后一个**开标签（上游 `findLastUnprotectedMatch` 的 `.pop()` 语义）。 */
    private fun lastOpenTagIn(source: String) = OPEN_TAG.findAll(source).lastOrNull()

    // ------------------------------------------------------------------ 解析

    /**
     * 一次解析的结果。
     *
     * @param updates 模板更新列表。[id] 为空串表示「单模板形态」（调用方按唯一模板兜底，
     *        上游 `data-services.js:1690` 同）
     * @param error 解析失败原因（成功时为 null）。**不抛异常**——这是与上游有意的差别，
     *        理由见类注释
     */
    data class Parsed(
        val updates: List<Update>,
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null
        /** 空块（`<ui_template_updates></ui_template_updates>`）→ 没有更新，但不是错误。 */
        val isEmpty: Boolean get() = updates.isEmpty()

        companion object {
            val EMPTY = Parsed(emptyList())
        }
    }

    /** 一条更新：[id] 为模板 id（单模板形态为空串），[variables] 是该模板的变量对象。 */
    data class Update(val id: String, val variables: JsonElement)

    /**
     * 解析变量块里的 JSON（上游 `parseUiTemplateUpdates`，`data-services.js:1671-1691`）。
     *
     * 三种容忍（都照抄上游，缺一条真实数据就会解析失败）：
     * 1. **``` 围栏**：模型很爱把 JSON 包在 ```json 里（提示词明说不要，但它照做）；
     *    上游在 `data-services.js:1673-1674` 做了两次 replace（头尾各剥一次围栏）；
     * 2. **单模板形态**：直接是变量对象（`{"hp": 3}`）或数组；
     * 3. **多模板形态**：当 [expectedTemplateCount] > 1 且是数组、且每个成员都有字符串 `id`
     *    与 `variables` 键时，按成员拆成多条更新。
     *
     * @param expectedTemplateCount 当前模板数（决定要不要按多模板形态拆；
     *        上游 `expectedTemplates.length > 1`）
     */
    fun parse(rawContent: String, expectedTemplateCount: Int = 1): Parsed {
        val source = stripFence(rawContent)
        if (source.isEmpty()) return Parsed.EMPTY
        val element = runCatching { json.parseToJsonElement(source) }.getOrElse { error ->
            return Parsed(
                updates = emptyList(),
                error = "JSON变量块格式错误：${error.message?.take(200).orEmpty()}",
            )
        }
        if (expectedTemplateCount > 1 &&
            element is JsonArray &&
            element.isNotEmpty() &&
            element.all { it.isMultiTemplateMember() }
        ) {
            return Parsed(
                updates = element.map { item ->
                    val obj = item as JsonObject
                    Update(
                        id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty(),
                        variables = obj["variables"] ?: JsonNull,
                    )
                },
            )
        }
        return Parsed(updates = listOf(Update(id = "", variables = element)))
    }

    /** 便捷入口：剥除 + 解析（调用方通常两件事都要做）。 */
    fun stripAndParse(text: String, expectedTemplateCount: Int = 1): Pair<String, Parsed> {
        val block = find(text)
        val stripped = strip(text)
        if (block == null) return stripped to Parsed.EMPTY
        return stripped to parse(block.body, expectedTemplateCount)
    }

    /**
     * 剥围栏（上游 `data-services.js:1672-1675` 的逐条等价）。
     *
     * 上游是「先 trim → 去头部 ```(json)? → 去尾部 ``` → 再 trim」，顺序不能换：
     * 先 trim 才能让头部围栏落在串首被 `^` 锚到。
     */
    internal fun stripFence(rawContent: String): String = rawContent
        .trim()
        .replace(HEAD_FENCE, "")
        .replace(TAIL_FENCE, "")
        .trim()

    private val HEAD_FENCE = Regex("""^```(?:json)?\s*""", RegexOption.IGNORE_CASE)
    private val TAIL_FENCE = Regex("""\s*```$""")

    /**
     * 多模板成员判据（上游 `data-services.js:1686-1687` 的逐条等价）：
     * 是对象、**不是数组**、`id` 是字符串、**有 `variables` 键**（值为 null 也算有）。
     */
    private fun JsonElement.isMultiTemplateMember(): Boolean {
        val obj = this as? JsonObject ?: return false
        val id = obj["id"] as? JsonPrimitive ?: return false
        if (!id.isString) return false
        return obj.containsKey("variables")
    }
}

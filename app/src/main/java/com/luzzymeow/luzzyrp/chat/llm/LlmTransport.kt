package com.luzzymeow.luzzyrp.chat.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LLM 传输层：把 OpenAI / Anthropic / Gemini 三协议的差异**全部收敛在实现类内**，
 * 上层只面对 [LlmRequest] → `Flow<LlmDelta>`。
 *
 * v2.0 定位：本包是「聊天传输层」——HTTP + SSE + 协议线格式 + 流式装配。
 * 上下文装配（消息怎么拼）与渲染仍归 WebView 侧的 JS；Kotlin 只负责把
 * JS 给的请求**逐字节保真**地发出去，并把响应增量回推。
 *
 * 超时约定：空闲 120s / 连接 30s / 重试 2 次（指数退避，仅幂等且未收到数据帧时）。
 *
 * 本接口与相关数据类自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改编。
 */
interface LlmTransport {
    /**
     * 流式请求。取消 = 取消本 Flow 的收集（实现须关闭底层 SSE 连接）。
     *
     * 实现**不得**抛异常打断流程：网络/协议错误统一转 [LlmDelta.error]，
     * 由调用方（[com.luzzymeow.luzzyrp.chat.ChatJobs]）决定如何回报。
     */
    fun stream(request: LlmRequest): Flow<LlmDelta>
}

/** 会话消息角色（三协议归一）。 */
enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 模型发起的一次工具调用（已从各协议归一）。
 *
 * 自 v1.5.0 的 `assistant.domain.tool.ToolCall` 迁入本包——传输层不该依赖
 * 已被移除的工具/工作区体系；字段语义保持不变。
 */
data class ToolCall(
    /** 协议侧 id（OpenAI `tool_call.id` / Anthropic `tool_use.id` / Gemini 合成 id）。 */
    val id: String,
    /** 工具名。 */
    val name: String,
    /** 解析后的参数对象（解析失败时为空对象）。 */
    val arguments: JsonObject = JsonObject(emptyMap()),
    /** 原始参数字符串（保留给审计与错误提示；解析失败时非空）。 */
    val rawArguments: String = arguments.toString(),
) {
    /**
     * OpenAI `tool_calls[]` 条目形态 `{id, type:"function", function:{name, arguments}}`。
     *
     * 这是 JS 侧事件契约要求的形状（与 OpenAI 原生结构一致），故由本层统一产出，
     * 避免前端再拼一次。
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("type", JsonPrimitive("function"))
        put(
            "function",
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("arguments", JsonPrimitive(rawArguments))
            },
        )
    }
}

/**
 * 一条会话消息（三协议归一的中间表示）。
 *
 * [raw] 是 JS 传入的**原始 OpenAI 形态对象**（可能带 `reasoning_content` /
 * `reasoning_details` / `extra_content` 等本层不认识的字段）。wire fidelity 要求
 * OpenAI 路径**逐字节原样转发**，因此 [raw] 非空时优先用它；为空（由 Kotlin 侧
 * 构造，如单测）时按下面的字段合成。
 */
data class LlmMessage(
    val role: LlmRole,
    /** 正文纯文本视图（数组 content 时取 text 分片拼接）。 */
    val content: String = "",
    /** 原始 content（字符串或 parts 数组）；OpenAI 路径原样转发。 */
    val rawContent: JsonElement? = null,
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    /** JS 传入的原始 OpenAI 形态消息对象；非空时 OpenAI 路径原样转发。 */
    val raw: JsonObject? = null,
) {
    /**
     * 归一消息 → OpenAI 形态对象（[raw] 优先，无 raw 时按字段合成）。
     *
     * 三协议转换共用此入口，保证「同一份 JS 输入 → 各协议转换」的输入一致。
     */
    fun toOpenAiJson(): JsonObject = raw ?: buildJsonObject {
        when (role) {
            LlmRole.SYSTEM -> {
                put("role", JsonPrimitive("system"))
                put("content", rawContent ?: JsonPrimitive(content))
            }

            LlmRole.USER -> {
                put("role", JsonPrimitive("user"))
                put("content", rawContent ?: JsonPrimitive(content))
            }

            LlmRole.ASSISTANT -> {
                put("role", JsonPrimitive("assistant"))
                put("content", rawContent ?: JsonPrimitive(content))
                if (toolCalls.isNotEmpty()) {
                    put("tool_calls", JsonArray(toolCalls.map { it.toJson() }))
                }
            }

            LlmRole.TOOL -> {
                put("role", JsonPrimitive("tool"))
                put("content", rawContent ?: JsonPrimitive(content))
                toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                name?.let { put("name", JsonPrimitive(it)) }
            }
        }
    }
}

/** 一次请求的完整参数（含每模型可覆盖的采样参数与请求体扩展）。 */
data class LlmRequest(
    val messages: List<LlmMessage>,
    /** 供应商协议：`openai` | `anthropic` | `gemini`。 */
    val protocol: String,
    /**
     * OpenAI 协议为**完整端点**（JS 已拼好 `/chat/completions`，原样 POST）；
     * Anthropic 为**裸 base**（JS 已剥掉 `/chat/completions`，原样 POST）；
     * Gemini 为裸 base（端点由 [GeminiWire] 拼接）。
     */
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /** 是否流式（Anthropic/Gemini 的 body 里 `stream` 用得到）。 */
    val stream: Boolean = true,
    val reasoningEffort: String? = null,
    /** 工具定义（**OpenAI 形态**原始对象数组，仅 OpenAI 协议发送）。 */
    val tools: List<JsonObject> = emptyList(),
    /** 抗截断：追加 `output_reply` 工具并要求模型用它提交正文。 */
    val replyInTool: Boolean = false,
    /** 强制工具调用（工坊 Diff 场景）。 */
    val requireTool: Boolean = false,
    /** 请求体扩展（用户 JSON；JS 侧即用对象展开语义，后者覆盖前者）。 */
    val extraBody: JsonObject? = null,
)

/**
 * 流式增量（三协议归一）。
 *
 * 同一帧可能同时含 reasoning 与 content；`toolCalls` 为**增量分片**，
 * 由 [ToolCallAccumulator] 按 index/id 拼装。
 */
data class LlmDelta(
    val reasoning: String? = null,
    val content: String? = null,
    val toolCalls: List<ToolCallDelta> = emptyList(),
    val usage: Usage? = null,
    /**
     * 供应商**原始** usage 对象（OpenAI/Anthropic 取 `usage`，Gemini 取 `usageMetadata`）。
     *
     * JS 侧 `normalizeApiUsage` 认得比 OpenAI 更宽的字段（缓存读写、reasoning tokens 等），
     * 故原样透传而不是只给归一后的 input/output。
     */
    val rawUsage: JsonObject? = null,
    /** 流结束原因（`stop` / `tool_calls` / `length` / `error` …）。 */
    val finishReason: String? = null,
    val error: LlmError? = null,
) {
    data class Usage(val input: Int, val output: Int)
}

/** 工具调用增量分片（OpenAI `delta.tool_calls[]` / Anthropic `content_block_start|delta`）。 */
data class ToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    /** 参数字符串分片（需按 index 顺序拼接后再解析 JSON）。 */
    val argumentsChunk: String? = null,
)

/** 归一化错误（不携带密钥；[retryable] 决定是否重试）。 */
data class LlmError(
    val message: String,
    val retryable: Boolean = false,
    /** HTTP 状态码（如有）。 */
    val httpStatus: Int? = null,
)

/**
 * 工具调用分片累加器：按 [ToolCallDelta.index] 聚合 id/name/arguments，
 * 在 `finishReason == "tool_calls"`（或流结束）时产出完整 [ToolCall]。
 *
 * 纯 Kotlin，可单测。**线程不安全**——每次请求独占一个实例。
 */
class ToolCallAccumulator {
    private data class Acc(
        var id: String? = null,
        var name: String? = null,
        val args: StringBuilder = StringBuilder(),
    )

    private val slots = linkedMapOf<Int, Acc>()

    fun accept(deltas: List<ToolCallDelta>) {
        for (d in deltas) {
            val acc = slots.getOrPut(d.index) { Acc() }
            d.id?.let { acc.id = it }
            d.name?.let { acc.name = it }
            d.argumentsChunk?.let { acc.args.append(it) }
        }
    }

    fun isEmpty(): Boolean = slots.isEmpty()

    /** 产出完整调用；参数非法 JSON 时 `rawArguments` 保留原文、`arguments` 为空对象。 */
    fun build(): List<ToolCall> = slots.entries.map { (index, acc) ->
        val raw = acc.args.toString()
        ToolCall(
            id = acc.id ?: "call_$index",
            name = acc.name ?: "",
            arguments = JsonLenient.parseObjectOrEmpty(raw),
            rawArguments = raw,
        )
    }

    /**
     * OpenAI `tool_calls[]` 形态的**累积快照**（JS 事件契约要求）。
     *
     * 名字是「快照」而不是「增量」：每帧都给出截至当前的全部调用，
     * 前端不必自己维护累加状态。
     */
    fun snapshotJson(): JsonArray = JsonArray(build().map { it.toJson() })

    /** 累积快照的紧凑 JSON 文本（无调用时为空数组字面量）。 */
    fun snapshotString(): String = snapshotJson().toString()
}

// ---------- 三协议转换共用的宽松取值助手（非对象/非数组一律回退默认值，绝不抛异常） ----------

/** 宽松取对象字段。 */
internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

/** 宽松取数组字段。 */
internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

/** 宽松取字符串字段。 */
internal fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

/** 宽松取数组元素为对象（非对象元素丢弃）。 */
internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

/** 取首元素为对象。 */
internal fun JsonArray.firstObjectOrNull(): JsonObject? = firstOrNull() as? JsonObject

/** 宽松解析一帧 JSON 文本为对象；失败返回 null（不抛异常）。 */
internal fun parseJsonObjectOrNull(payload: String): JsonObject? {
    val trimmed = payload.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { JsonLenient.json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
}

/** 取 content 的纯文本视图（字符串原样；数组取 `text` 分片拼接）。 */
internal fun JsonElement?.contentText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonArray -> objects().joinToString("") { it.strOrNull("text").orEmpty() }
    else -> ""
}

/** usage 计数字段的宽容读取（键名按协议不同，给一串候选）。 */
internal fun JsonObject.usageCount(vararg keys: String): Int =
    keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull?.toIntOrNull() } ?: 0

/** 是否为 JSON 字符串（等价 JS `typeof x === 'string'`；数字/布尔/JsonNull 都算 false）。 */
internal fun JsonElement?.isJsonString(): Boolean = (this as? JsonPrimitive)?.isString == true

/** 数据 URL（`data:<mime>;base64,<data>`）→ mime 与 base64 载荷；不匹配返回 null。 */
internal fun parseBase64DataUrl(url: String): Pair<String, String>? {
    val match = DATA_URL_PATTERN.matchEntire(url) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

private val DATA_URL_PATTERN = Regex("data:([^;]+);base64,(.*)")

/**
 * 原位替换对象的 `content` 键（保留其余键与整体键序）。
 *
 * 对应 JS 里的 `last.content = [...]`：既保留原对象上的其它字段，
 * 又不把 `content` 挪到末尾（原先没有该键时才追加到末尾）。
 */
internal fun JsonObject.withContent(value: JsonElement): JsonObject {
    val merged = LinkedHashMap<String, JsonElement>(this)
    merged["content"] = value
    return JsonObject(merged)
}

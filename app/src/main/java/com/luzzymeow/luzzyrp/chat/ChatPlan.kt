package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.ToolCall
import com.luzzymeow.luzzyrp.chat.llm.contentText
import com.luzzymeow.luzzyrp.chat.llm.objOrNull
import com.luzzymeow.luzzyrp.chat.llm.objects
import com.luzzymeow.luzzyrp.chat.llm.parseJsonObjectOrNull
import com.luzzymeow.luzzyrp.chat.llm.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * `chatStart` 入参（JS 产出的 plan）→ [LlmRequest] 的解析层。
 *
 * 设计原则：**JS 交出什么，Kotlin 就发什么**。
 * messages / tools 都是 OpenAI 形态的原始对象，本层只做「取字段 → 建索引」，
 * 不重排、不裁字段（[LlmMessage.raw] 保留原对象，OpenAI 路径逐字节转发）。
 *
 * 只拒三类真正无法执行的情况：JSON 解析失败、缺 `jobId`、`protocol` 不在三协议内。
 * 其余（如 baseUrl / apiKey 为空）交给传输层产出**可读的错误事件**——
 * 让 JS 收到 `{"type":"error"}` 比静默返回 `""` 更有用。
 */
object ChatPlan {

    /** 解析结果：成功带 jobId 与请求；失败带原因（只进日志，不跨 JS 边界）。 */
    sealed interface Result {
        data class Ok(val jobId: String, val request: LlmRequest) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(planJson: String): Result {
        val root = parseJsonObjectOrNull(planJson)
            ?: return Result.Invalid("plan 不是合法 JSON 对象")

        val jobId = root.strOrNull("jobId").orEmpty()
        if (jobId.isBlank()) return Result.Invalid("plan 缺少 jobId")

        val protocol = root.strOrNull("protocol").orEmpty().trim().lowercase()
        if (protocol !in SUPPORTED_PROTOCOLS) {
            return Result.Invalid("plan 的 protocol \"$protocol\" 不在 $SUPPORTED_PROTOCOLS 内")
        }

        return Result.Ok(jobId = jobId, request = toRequest(root, protocol))
    }

    private fun toRequest(root: JsonObject, protocol: String): LlmRequest = LlmRequest(
        messages = root["messages"]?.let { (it as? JsonArray)?.objects()?.map(::toMessage) }.orEmpty(),
        protocol = protocol,
        baseUrl = root.strOrNull("baseUrl").orEmpty(),
        apiKey = root.strOrNull("apiKey").orEmpty(),
        model = root.strOrNull("model").orEmpty(),
        temperature = root.strOrNull("temperature")?.toDoubleOrNull(),
        maxTokens = root.strOrNull("maxTokens")?.toIntOrNull(),
        stream = root.strOrNull("stream")?.toBooleanStrictOrNull() ?: true,
        reasoningEffort = root.strOrNull("reasoningEffort"),
        tools = root["tools"]?.let { (it as? JsonArray)?.objects() }.orEmpty(),
        replyInTool = root.strOrNull("replyInTool")?.toBooleanStrictOrNull() ?: false,
        requireTool = root.strOrNull("requireTool")?.toBooleanStrictOrNull() ?: false,
        extraBody = root.objOrNull("extraBody"),
    )

    /** OpenAI 形态消息对象 → 归一消息（[LlmMessage.raw] 保留原对象）。 */
    private fun toMessage(raw: JsonObject): LlmMessage = LlmMessage(
        role = roleOf(raw.strOrNull("role")),
        content = raw["content"].contentText(),
        rawContent = raw["content"],
        reasoning = raw.strOrNull("reasoning_content") ?: raw.strOrNull("reasoning"),
        toolCalls = raw["tool_calls"]?.let { (it as? JsonArray)?.objects()?.mapNotNull(::toToolCall) }.orEmpty(),
        toolCallId = raw.strOrNull("tool_call_id") ?: raw.strOrNull("tool_use_id"),
        name = raw.strOrNull("name"),
        raw = raw,
    )

    /** OpenAI `tool_calls[]` 条目 → [ToolCall]（`arguments` 是 JSON 字符串，非法时留空对象）。 */
    private fun toToolCall(entry: JsonObject): ToolCall? {
        val function = entry.objOrNull("function") ?: return null
        val name = function.strOrNull("name") ?: return null
        val rawArguments = function.strOrNull("arguments").orEmpty()
        return ToolCall(
            id = entry.strOrNull("id").orEmpty(),
            name = name,
            arguments = com.luzzymeow.luzzyrp.chat.llm.JsonLenient.parseObjectOrEmpty(rawArguments),
            rawArguments = rawArguments,
        )
    }

    private fun roleOf(role: String?): LlmRole = when (role) {
        "system" -> LlmRole.SYSTEM
        "assistant" -> LlmRole.ASSISTANT
        "tool" -> LlmRole.TOOL
        else -> LlmRole.USER
    }

    /** 原生传输支持的协议（与 [com.luzzymeow.luzzyrp.chat.llm.RoutingTransport.SUPPORTED_PROTOCOLS] 同源）。 */
    val SUPPORTED_PROTOCOLS: List<String> = com.luzzymeow.luzzyrp.chat.llm.RoutingTransport.SUPPORTED_PROTOCOLS
}

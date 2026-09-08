package com.luzzymeow.luzzyrp.assistant.domain.llm

import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * LLM 传输层（PLAN §5.3）：把 OpenAI / Anthropic / Gemini 三协议的差异**全部收敛在实现类内**，
 * 上层只面对 [LlmRequest] → `Flow<LlmDelta>`。
 *
 * 超时约定（PLAN §5.3）：空闲 120s / 连接 30s / 重试 2 次（指数退避，仅幂等请求）。
 */
interface LlmTransport {
    /**
     * 流式请求。取消 = 取消本 Flow 的收集（实现须关闭底层 SSE 连接）。
     *
     * 实现**不得**抛异常打断流程：网络/协议错误统一转 [LlmDelta.Error]，
     * 由 [com.luzzymeow.luzzyrp.assistant.domain.loop.AgentLoop] 决定是否重试。
     */
    fun stream(request: LlmRequest): Flow<LlmDelta>
}

/** 会话消息角色（三协议归一）。 */
enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 一条会话消息（三协议归一的中间表示）。
 *
 * - `toolCalls` 仅 ASSISTANT 消息使用（模型请求调用工具）；
 * - `toolCallId` / `name` 仅 TOOL 消息使用（工具结果回灌）。
 */
data class LlmMessage(
    val role: LlmRole,
    val content: String = "",
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
)

/** 一次请求的完整参数（含每助手可覆盖的采样参数与请求体扩展）。 */
data class LlmRequest(
    val messages: List<LlmMessage>,
    /** 供应商协议：`openai` | `anthropic` | `gemini`（由供应商配置决定）。 */
    val protocol: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val reasoningEffort: String? = null,
    /** 工具 schema（JSON Schema 数组，直接进请求体 `tools`）。 */
    val tools: List<JsonObject> = emptyList(),
    /** 强制工具调用（工坊 Diff 场景用 `requireTool`，PLAN §18.2）。 */
    val requireTool: Boolean = false,
    /** 请求体扩展（用户 JSON；禁止覆盖 messages/tools/stream，见 PLAN §11.1）。 */
    val extraBody: JsonObject? = null,
    /** 供应商自定义请求头（如 Anthropic `anthropic-version`）。 */
    val extraHeaders: Map<String, String> = emptyMap(),
)

/**
 * 流式增量（三协议归一）。
 *
 * 同一帧可能同时含 reasoning 与 content；`toolCalls` 为**增量分片**，
 * 由 [com.luzzymeow.luzzyrp.assistant.domain.llm.ToolCallAccumulator] 按 index/id 拼装。
 */
data class LlmDelta(
    val reasoning: String? = null,
    val content: String? = null,
    val toolCalls: List<ToolCallDelta> = emptyList(),
    val usage: Usage? = null,
    /** 流结束原因（`stop` / `tool_calls` / `length` / `error`）。 */
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
 * 纯 Kotlin，可单测。
 */
class ToolCallAccumulator {
    private data class Acc(var id: String? = null, var name: String? = null, val args: StringBuilder = StringBuilder())
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
    fun build(): List<ToolCall> = slots.entries.map { (_, acc) ->
        val raw = acc.args.toString()
        ToolCall(
            id = acc.id ?: "call_${acc.hashCode()}",
            name = acc.name ?: "",
            arguments = com.luzzymeow.luzzyrp.assistant.domain.llm.JsonLenient.parseObjectOrEmpty(raw),
            rawArguments = raw,
        )
    }
}

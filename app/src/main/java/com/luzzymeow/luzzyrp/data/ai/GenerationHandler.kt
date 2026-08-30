package com.luzzymeow.luzzyrp.data.ai

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.params.ToolDefinition
import com.luzzymeow.luzzyrp.core.ai.provider.ProviderGateway
import com.luzzymeow.luzzyrp.core.ai.tag.TagToolCallParser
import com.luzzymeow.luzzyrp.core.model.FinishReason
import com.luzzymeow.luzzyrp.core.model.MessageChunk
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.ToolApprovalState
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.core.model.handleMessageChunk
import com.luzzymeow.luzzyrp.core.model.parsedInput
import com.luzzymeow.luzzyrp.data.ai.tools.ToolDef
import com.luzzymeow.luzzyrp.data.ai.tools.ToolOutputs
import com.luzzymeow.luzzyrp.data.ai.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.UUID

/**
 * ═══════════════════════════════════════════════════════════════════
 * [INVARIANT-AGENTIC] 生成处理器 —— Agentic 六大不变性落点（规定 2）
 * ═══════════════════════════════════════════════════════════════════
 *
 * A1  maxSteps = 256 for 循环硬上限（本文件 [MAX_STEPS]）；
 * A2  工具结果原地回填（tool.copy(output=…)），绝不新建 TOOL 角色消息；
 * A3  三 break：无未执行工具 / 存在 Pending 等待审批 / 无可执行工具；
 * A4  ToolApprovalState 五状态机续跑（resume 已批准/拒绝的工具后继续循环）；
 * A5  禁止 tool_choice = required（协议层仅 AUTO/NONE，见 Provider 实现）；
 * A6  被动工具不进工具表 —— 经系统提示注入 + PromptAssembler 预执行。
 *
 * [INVARIANT-STREAMING] 流式不变性（规定 1）：
 *   每个模型增量经 [handleMessageChunk] 合并后立即 emit [GenerationEvent.Messages]
 *   —— 1 字 = 1 次更新，思考卡片与正文气泡直连 StateFlow，无节流无缓冲。
 *   标签兜底（TagToolCallParser）只抑制工具协议文本上屏，不节流正文。
 *
 * RP 两阶段闭环（maxLoops = 3）：
 *   [maxToolRounds] 限定主动工具轮次（RP 模式 = 3）：轮次内 = 阶段一
 *   「推理 + 工具规划」（流入思考卡片/工具卡片）；轮次耗尽后不再提供工具 =
 *   阶段二「基于结果生成最终回复」（流入正文气泡）。
 *   普通对话模式传 [UNLIMITED_TOOL_ROUNDS]（仅受 maxSteps 限制）。
 *
 * ★ 修改本文件任何循环/回填/break 逻辑前，必须重读 HARD_REQUIREMENTS.md 规定 1/2。★
 */
class GenerationHandler(
    private val providerManager: ProviderGateway,
) {

    /** 生成事件流：UI/ChatService 据此更新会话单一真源。 */
    sealed interface GenerationEvent {
        /** 每个模型增量后的完整消息列表（单一真源直写）。 */
        data class Messages(val messages: List<UIMessage>) : GenerationEvent

        /** 一轮工具执行完成（原地回填后）。 */
        data class ToolRound(val round: Int, val messages: List<UIMessage>) : GenerationEvent

        /** 出现待审批工具，循环暂停（等待 ChatService 续跑）。 */
        data class ApprovalNeeded(val messages: List<UIMessage>) : GenerationEvent

        /** 生成完成（正文收口）。 */
        data class Completed(val messages: List<UIMessage>, val finishReason: String?) : GenerationEvent

        /** 生成失败。 */
        data class Failed(val error: Throwable) : GenerationEvent
    }

    data class Request(
        val setting: ProviderSetting,
        val messages: List<UIMessage>,
        val params: TextGenerationParams,
        /** 主动工具注册表（空 = 无工具模式）。 */
        val tools: ToolRegistry,
        /** 主动工具轮次上限（RP = 3；普通 = UNLIMITED_TOOL_ROUNDS）。 */
        val maxToolRounds: Int = 3,
        /** 是否启用文本标签兜底（无原生 FC 模型）。 */
        val tagFallback: Boolean = false,
    )

    fun generate(request: Request): Flow<GenerationEvent> = flow {
        var messages = request.messages
        var toolRounds = 0
        var finishReason: String? = null

        // ── 续跑入口（A4）：上一轮因审批暂停，先执行已批准/拒绝的工具 ──
        val resume = collectResumableTools(messages, request.tools)
        if (resume.isNotEmpty()) {
            messages = executeAndBackfill(messages, resume, request.tools) { updated ->
                emit(GenerationEvent.Messages(updated))
            }
            toolRounds++
            emit(GenerationEvent.ToolRound(toolRounds, messages))
        }

        // ── A1：maxSteps = 256 硬上限 ──
        for (step in 0 until MAX_STEPS) {

            // 阶段判定：工具轮次耗尽 → 阶段二（不再提供工具，模型基于结果写正文）
            val offerTools = toolRounds < request.maxToolRounds && !request.tools.isEmpty()

            val stepParams = request.params.copy(
                tools = if (offerTools) request.tools.toDefinitions() else emptyList(),
            )

            // ── 流式生成（1 字 = 1 次更新）──
            val parser = if (request.tagFallback) TagToolCallParser() else null
            var streamError: Throwable? = null

            try {
                providerManager.streamText(request.setting, messages, stepParams)
                    .collect { chunk ->
                        messages = applyChunk(messages, chunk, parser, request.tools, offerTools)
                        emit(GenerationEvent.Messages(messages))
                        chunk.choices.firstOrNull()?.finishReason?.let { fr ->
                            if (fr != "unknown") finishReason = fr
                        }
                    }
            } catch (e: CancellationException) {
                throw e   // Stop 按钮语义：取消必须穿透
            } catch (t: Throwable) {
                streamError = t
            }

            // 流尾兜底：未闭合标签的尽力解析
            if (parser != null) {
                val tail = parser.finish()
                if (tail.toolCalls.isNotEmpty()) {
                    messages = appendTagToolCalls(messages, tail.toolCalls, request.tools)
                    emit(GenerationEvent.Messages(messages))
                }
            }

            if (streamError != null) {
                emit(GenerationEvent.Failed(streamError))
                return@flow
            }

            // ── 收集末消息未执行工具（A3 三 break 判定）──
            val last = messages.lastOrNull()
            val pendingTools = last?.parts
                ?.filterIsInstance<UIMessagePart.Tool>()
                ?.filter { !it.isExecuted }
                .orEmpty()

            if (pendingTools.isEmpty()) {
                // break 之一：模型未调用工具（阶段二正文或纯文本回复）→ 收口
                emit(GenerationEvent.Completed(messages, finishReason))
                return@flow
            }

            // break 之二：等待用户审批 → 暂停，交还 ChatService
            if (pendingTools.any { it.isPendingApproval }) {
                emit(GenerationEvent.ApprovalNeeded(messages))
                return@flow
            }

            // ── 执行工具并原地回填（A2）──
            messages = executeAndBackfill(messages, pendingTools, request.tools) { updated ->
                emit(GenerationEvent.Messages(updated))
            }
            toolRounds++
            emit(GenerationEvent.ToolRound(toolRounds, messages))
            // break 之三（无可执行工具）已由 pendingTools.isEmpty() 覆盖；
            // 循环继续 → 下一请求携带回填后的工具结果（KV 前缀不变，仅追加）
        }

        // 256 步硬上限触底：按完成收口（防御性，正常交互不会到达）
        emit(GenerationEvent.Completed(messages, finishReason))
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // 增量处理：合并 + 标签兜底
    // ------------------------------------------------------------------

    /**
     * 应用单个 SSE chunk：
     *   - 原生工具调用 delta 与推理 delta 直接经 handleMessageChunk 合并；
     *   - 文本 delta 先过 TagToolCallParser（标签内容不上屏，闭合后转工具调用）。
     */
    private fun applyChunk(
        messages: List<UIMessage>,
        chunk: MessageChunk,
        parser: TagToolCallParser?,
        registry: ToolRegistry,
        offerTools: Boolean,
    ): List<UIMessage> {
        val delta = chunk.choices.firstOrNull()?.delta
        if (delta == null) {
            return messages.handleMessageChunk(chunk)
        }

        val processedParts = mutableListOf<UIMessagePart>()
        for (part in delta.parts) {
            when (part) {
                is UIMessagePart.Text -> {
                    if (parser == null) {
                        processedParts.add(part)
                    } else {
                        val result = parser.feed(part.text)
                        if (result.visibleText.isNotEmpty()) {
                            processedParts.add(UIMessagePart.Text(result.visibleText))
                        }
                        result.toolCalls.forEach { call ->
                            processedParts.add(buildToolPart(call.name, call.argumentsJson, registry, offerTools))
                        }
                    }
                }

                is UIMessagePart.Tool -> {
                    // 原生 FC 工具调用同样执行审批判定（needsApproval → Pending，五状态机 A4）
                    val def = registry.find(part.toolName)
                    val needsApproval = def != null && def.needsApproval(part.parsedInput())
                    processedParts.add(
                        if (needsApproval) part.copy(approvalState = ToolApprovalState.Pending) else part
                    )
                }

                else -> processedParts.add(part)
            }
        }

        val processedDelta = delta.copy(parts = processedParts)
        return messages.handleMessageChunk(
            chunk.copy(choices = chunk.choices.map { it.copy(delta = processedDelta) })
        )
    }

    private fun buildToolPart(
        name: String,
        argumentsJson: String,
        registry: ToolRegistry,
        offerTools: Boolean,
    ): UIMessagePart.Tool {
        val def = registry.find(name)
        val input = kotlinx.serialization.json.JsonPrimitive(argumentsJson)
        // 审批判定：工具声明需要审批（needsApproval）→ 到达即置 Pending（五状态机）
        val needsApproval = if (def == null) {
            false
        } else {
            val parsed = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(argumentsJson.ifBlank { "{}" })
            }.getOrElse { kotlinx.serialization.json.buildJsonObject { } }
            def.needsApproval(parsed)
        }
        return UIMessagePart.Tool(
            toolCallId = "tag_" + UUID.randomUUID().toString(),
            toolName = name,
            input = input,
            output = null,
            approvalState = if (needsApproval) ToolApprovalState.Pending else ToolApprovalState.Auto,
        )
    }

    private fun appendTagToolCalls(
        messages: List<UIMessage>,
        calls: List<TagToolCallParser.ParsedToolCall>,
        registry: ToolRegistry,
    ): List<UIMessage> {
        if (calls.isEmpty()) return messages
        val last = messages.lastOrNull() ?: return messages
        val newParts = calls.map { buildToolPart(it.name, it.argumentsJson, registry, offerTools = true) }
        return messages.dropLast(1) + last.copy(parts = last.parts + newParts)
    }

    // ------------------------------------------------------------------
    // 工具执行与原地回填（A2/A4）
    // ------------------------------------------------------------------

    /** 续跑收集：末消息中「已批准 / 已拒绝 / 已作答」但未回填结果的工具。 */
    private fun collectResumableTools(messages: List<UIMessage>, registry: ToolRegistry): List<UIMessagePart.Tool> {
        val last = messages.lastOrNull() ?: return emptyList()
        return last.parts.filterIsInstance<UIMessagePart.Tool>().filter {
            !it.isExecuted && it.approvalState !is ToolApprovalState.Auto && it.approvalState !is ToolApprovalState.Pending
        }
    }

    /**
     * 执行工具并把结果**原地回填**到同一 Tool part（A2：不新建 TOOL 消息）。
     * Denied → 回填拒绝说明；Answered → 回填用户答案；异常 → 回填错误 JSON
     * （CancellationException 重抛，保证 Stop 生效）。
     */
    private suspend fun executeAndBackfill(
        messages: List<UIMessage>,
        tools: List<UIMessagePart.Tool>,
        registry: ToolRegistry,
        onProgress: suspend (List<UIMessage>) -> Unit,
    ): List<UIMessage> {
        var current = messages
        val last = current.lastOrNull() ?: return current

        val updatedParts = last.parts.toMutableList()
        for (tool in tools) {
            val output: List<UIMessagePart> = when (val state = tool.approvalState) {
                is ToolApprovalState.Denied ->
                    ToolOutputs.text("""{"status":"denied","reason":${kotlinx.serialization.json.JsonPrimitive(state.reason)}}""")

                is ToolApprovalState.Answered ->
                    ToolOutputs.text(state.answer)

                ToolApprovalState.Approved, ToolApprovalState.Auto, ToolApprovalState.Pending -> {
                    val def: ToolDef? = registry.find(tool.toolName)
                    try {
                        def?.execute(tool.parsedInput())
                            ?: ToolOutputs.text("""{"error":"未知工具：${tool.toolName}"}""")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        ToolOutputs.error(t.message ?: t.javaClass.simpleName)
                    }
                }
            }

            val index = updatedParts.indexOfFirst {
                it is UIMessagePart.Tool && it.toolCallId == tool.toolCallId
            }
            if (index >= 0) {
                // ★ 原地回填：只更新该 part 的 output，不改变消息序列结构（KV 稳定）
                updatedParts[index] = (updatedParts[index] as UIMessagePart.Tool).copy(output = output)
            }
            current = current.dropLast(1) + last.copy(parts = updatedParts.toList())
            onProgress(current)
        }
        return current
    }

    companion object {
        /** A1：Agentic 循环硬上限。 */
        const val MAX_STEPS = 256

        /** 普通对话模式：工具轮次不受 RP 两阶段限制（仅受 MAX_STEPS 约束）。 */
        const val UNLIMITED_TOOL_ROUNDS = Int.MAX_VALUE
    }
}

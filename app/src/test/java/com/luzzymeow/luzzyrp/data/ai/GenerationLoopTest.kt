package com.luzzymeow.luzzyrp.data.ai

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.provider.ProviderGateway
import com.luzzymeow.luzzyrp.core.ai.provider.Provider
import com.luzzymeow.luzzyrp.core.model.MessageChunk
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ModelCapability
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessageChoice
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.data.ai.tools.ToolDef
import com.luzzymeow.luzzyrp.data.ai.tools.ToolOutputs
import com.luzzymeow.luzzyrp.data.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [INVARIANT-AGENTIC] 生成循环守护测试（HARD_REQUIREMENTS 规定 2）：
 * 原地回填 / 三 break / 审批暂停与续跑 / 两阶段收口。
 */
class GenerationLoopTest {

    /** 假网关：按预设脚本逐请求回放流式 chunk。 */
    private class FakeGateway(
        private val scripts: List<List<MessageChunk>>,
    ) : ProviderGateway {

        val requests = mutableListOf<List<UIMessage>>()
        private var index = 0

        override fun resolve(setting: ProviderSetting): Provider<ProviderSetting> = throw UnsupportedOperationException()
        override suspend fun listModels(setting: ProviderSetting): List<Model> = emptyList()

        override suspend fun generateText(
            setting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk = throw UnsupportedOperationException()

        override fun streamText(
            setting: ProviderSetting,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> {
            requests.add(messages)
            val script = scripts.getOrNull(index++) ?: emptyList()
            return flowOf(*script.toTypedArray())
        }

        override suspend fun generateEmbedding(setting: ProviderSetting, texts: List<String>): List<FloatArray> = emptyList()
    }

    private fun textDelta(text: String) = MessageChunk(
        choices = listOf(UIMessageChoice(delta = UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Text(text)))))
    )

    private fun reasoningDelta(text: String) = MessageChunk(
        choices = listOf(UIMessageChoice(delta = UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Reasoning(text)))))
    )

    private fun toolCallDelta(id: String, name: String, args: String) = MessageChunk(
        choices = listOf(
            UIMessageChoice(
                delta = UIMessage(
                    role = Role.ASSISTANT,
                    parts = listOf(UIMessagePart.Tool(toolCallId = id, toolName = name, input = JsonPrimitive(args))),
                )
            )
        )
    )

    private val setting = ProviderSetting(
        id = "p", name = "t", baseUrl = "https://x", apiKey = "k",
        models = listOf(Model(id = "m", capabilities = ModelCapability(tool = true))),
    )

    private val baseMessages = listOf(UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text("开始"))))

    @Test
    fun `tool round executes in place then final narrative`() = runTest {
        val executed = mutableListOf<String>()
        val registry = ToolRegistry(
            listOf(
                ToolDef(
                    name = "world_keyword_search",
                    description = "d",
                    parametersJson = "{}",
                    systemPrompt = "",
                ) { args ->
                    executed += (args as JsonObject).toString()
                    ToolOutputs.text("""{"results":[]}""")
                }
            )
        )
        val gateway = FakeGateway(
            listOf(
                // 阶段一：思考 + 主动工具调用
                listOf(reasoningDelta("第一轮：分析场景"), toolCallDelta("c1", "world_keyword_search", """{"keywords":["酒馆"]}""")),
                // 阶段二：基于结果生成正文
                listOf(textDelta("（你推开木门，暖黄的灯光涌了出来。）")),
            )
        )
        val handler = GenerationHandler(gateway)
        val events = handler.generate(
            GenerationHandler.Request(
                setting = setting,
                messages = baseMessages,
                params = TextGenerationParams(model = Model(id = "m")),
                tools = registry,
                maxToolRounds = 3,
            )
        ).toList()

        // 工具已执行
        assertEquals(1, executed.size)
        // 两条请求：第一条带工具定义（阶段一），第二条不带（阶段二）
        assertEquals(2, gateway.requests.size)
        // 消息结构：1 user + 1 assistant（工具结果原地回填，无新增 TOOL 消息）
        val finalEvent = events.filterIsInstance<GenerationHandler.GenerationEvent.Completed>().single()
        val visible = finalEvent.messages.filter { it.role != Role.SYSTEM }
        assertEquals(2, visible.size)
        val assistant = visible[1]
        val toolPart = assistant.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertNotNull(toolPart.output)   // ★ 原地回填
        assertEquals(1, assistant.parts.count { it is UIMessagePart.Tool })
        // 完成事件含最终正文
        assertTrue(finalEvent.messages.last().textContent().contains("暖黄的灯光"))
    }

    @Test
    fun `approval pending pauses loop and resume executes approved tool`() = runTest {
        val executed = mutableListOf<Boolean>()
        val registry = ToolRegistry(
            listOf(
                ToolDef(
                    name = "dangerous_tool",
                    description = "d",
                    parametersJson = "{}",
                    systemPrompt = "",
                    needsApproval = { true },
                ) { _ ->
                    executed += true
                    ToolOutputs.text("""{"ok":true}""")
                }
            )
        )
        val gateway = FakeGateway(
            listOf(
                listOf(toolCallDelta("c1", "dangerous_tool", "{}")),
                listOf(textDelta("叙事正文。")),
            )
        )
        val handler = GenerationHandler(gateway)

        // 第一次运行：出现审批暂停
        val events = handler.generate(
            GenerationHandler.Request(setting, baseMessages, TextGenerationParams(Model(id = "m")), registry, 3)
        ).toList()
        val approval = events.filterIsInstance<GenerationHandler.GenerationEvent.ApprovalNeeded>().single()
        assertTrue(executed.isEmpty())

        // 用户批准 → 续跑
        val messages = approval.messages.map { m ->
            m.copy(parts = m.parts.map { p ->
                if (p is UIMessagePart.Tool && p.toolCallId == "c1") p.copy(approvalState = com.luzzymeow.luzzyrp.core.model.ToolApprovalState.Approved) else p
            })
        }
        val resumeEvents = handler.generate(
            GenerationHandler.Request(setting, messages, TextGenerationParams(Model(id = "m")), registry, 3)
        ).toList()
        val completed = resumeEvents.filterIsInstance<GenerationHandler.GenerationEvent.Completed>().single()
        assertEquals(1, executed.size)
        assertTrue(completed.messages.last().textContent().contains("叙事正文"))
    }

    @Test
    fun `max tool rounds caps active tool phase`() = runTest {
        // 模型坚持不停调用工具：脚本给 4 轮工具调用
        val rounds = (1..4).map { i ->
            listOf(toolCallDelta("c$i", "noop", "{}"), textDelta("第${i}轮后试图继续"))
        }
        val executed = mutableListOf<String>()
        val registry = ToolRegistry(
            listOf(
                ToolDef("noop", "d", "{}", "") { _ ->
                    executed += "x"
                    ToolOutputs.text("{}")
                }
            )
        )
        val gateway = FakeGateway(rounds + listOf(listOf(textDelta("最终叙事。"))))
        val handler = GenerationHandler(gateway)
        val events = handler.generate(
            GenerationHandler.Request(setting, baseMessages, TextGenerationParams(Model(id = "m")), registry, maxToolRounds = 3)
        ).toList()
        // 工具执行仍会被处理（模型坚持），但阶段三之后的请求不再提供工具定义
        val completed = events.filterIsInstance<GenerationHandler.GenerationEvent.Completed>().single()
        assertTrue(completed.messages.last().textContent().contains("最终叙事"))
    }

    @Test
    fun `tag fallback parses tool calls without native fc`() = runTest {
        val executed = mutableListOf<String>()
        val registry = ToolRegistry(
            listOf(
                ToolDef("memory_search", "d", "{}", "") { args ->
                    executed += (args as JsonObject).toString()
                    ToolOutputs.text("""{"results":["鹿溪爱吃烤鱼"]}""")
                }
            )
        )
        // 无原生 FC 模型：以 <tool_calls> 标签调用
        val script1 = listOf(
            textDelta("让我查一下。<tool_calls>memory_search:{\"query\":\"烤鱼\"}</tool_calls>"),
        )
        val script2 = listOf(textDelta("（你想起了上次的烤鱼香。）"))
        val gateway = FakeGateway(listOf(script1, script2))
        val handler = GenerationHandler(gateway)
        val events = handler.generate(
            GenerationHandler.Request(
                setting, baseMessages, TextGenerationParams(Model(id = "m")), registry, 3, tagFallback = true,
            )
        ).toList()

        assertEquals(1, executed.size)
        val completed = events.filterIsInstance<GenerationHandler.GenerationEvent.Completed>().single()
        val visible = completed.messages.filter { it.role != Role.SYSTEM }
        // 标签内容不得出现在正文
        assertTrue(!visible.last().textContent().contains("<tool_calls>"))
        assertTrue(visible.last().textContent().contains("烤鱼香"))
    }
}

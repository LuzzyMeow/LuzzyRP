package com.luzzymeow.luzzyrp.core.ai

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.provider.openai.OpenAICompatibleProvider
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ModelCapability
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.ProviderType
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.ToolApprovalState
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [INVARIANT-KV] KV 前缀稳定性 Golden 测试（HARD_REQUIREMENTS 规定 8）。
 *
 * 保证：同一份历史消息两次组装出的协议请求体**逐字节相等**——
 * 这是供应商侧 KV 缓存命中的充要前提。任何导致前缀漂移的改动都会让本测试变红。
 */
class KvPrefixStabilityGoldenTest {

    private val provider = OpenAICompatibleProvider(OkHttpClient())

    private fun history(): List<UIMessage> {
        val system = UIMessage(
            role = Role.SYSTEM,
            parts = listOf(UIMessagePart.Text("你将扮演鹿溪。【世界设定】\n银月酒馆位于旧城北岸。")),
        )
        val user1 = UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text("推门进入酒馆。")))
        val assistant1 = UIMessage(
            role = Role.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning(thinking = "检索地点设定……"),
                UIMessagePart.Tool(
                    toolCallId = "call_1",
                    toolName = "world_keyword_search",
                    input = JsonPrimitive("""{"keywords":["银月酒馆"]}"""),
                    output = listOf(UIMessagePart.Text("""{"results":[{"content":"银月酒馆：旧城北岸，经营者为半兽人格蕾娜。"}]}""")),
                ),
                UIMessagePart.Text("（你推开银月酒馆的木门，暖黄的灯光涌了出来。）"),
            ),
        )
        val user2 = UIMessage(role = Role.USER, parts = listOf(UIMessagePart.Text("走向吧台点一杯麦酒。")))
        return listOf(system, user1, assistant1, user2)
    }

    @Test
    fun `identical history serializes byte-identical request prefix`() {
        val setting = testSetting()
        val params = testParams()
        val first = provider.buildProtocolMessages(history()).toString()
        val second = provider.buildProtocolMessages(history()).toString()
        assertEquals(first, second)
    }

    @Test
    fun `request prefix contains no volatile fields`() {
        val setting = testSetting()
        val params = testParams()
        val serialized = provider.buildProtocolMessages(history()).toString()
        // 易变字段不得进入前缀
        assertTrue(!serialized.contains("createdAt"))
        assertTrue(!serialized.contains("usage"))
        assertTrue(!serialized.contains("approvalState"))
        assertTrue(!serialized.contains("toolCallId\""))  // tool_call_id 出现在工具结果对中，但参数名是 tool_call_id —— 此处校验不带引号尾缀的字段泄漏
    }

    @Test
    fun `tool result round trips as role tool adjacent to assistant`() {
        val serialized = provider.buildProtocolMessages(history()).toString()
        // assistant(tool_calls) 与 tool 结果必须成对相邻
        val toolCallsIndex = serialized.indexOf(""""tool_calls"""")
        val toolResultIndex = serialized.indexOf(""""tool_call_id"""")
        assertTrue(toolCallsIndex in 0 until toolResultIndex)
    }

    private fun testSetting() = ProviderSetting(
        id = "p1", name = "测试", type = ProviderType.OPENAI,
        baseUrl = "https://api.example.com", apiKey = "sk-test",
        models = listOf(Model(id = "glm-5.2", capabilities = ModelCapability(reasoning = true, tool = false))),
    )

    private fun testParams() = TextGenerationParams(
        model = Model(id = "glm-5.2", capabilities = ModelCapability(reasoning = true, tool = false)),
        temperature = 0.8,
    )
}

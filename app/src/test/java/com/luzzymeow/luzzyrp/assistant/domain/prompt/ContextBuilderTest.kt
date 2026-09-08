package com.luzzymeow.luzzyrp.assistant.domain.prompt

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ContextBuilder] 单测：变量替换 / 技能与记忆注入 / 历史压缩（含摘要失败退化）。 */
class ContextBuilderTest {

    /** 2023-11-14T22:13:20Z（UTC）——用于固定 `{{time}}` / `{{date}}`。 */
    private val fixedNow = 1_700_000_000_000L

    private fun spec(
        systemPrompt: String = "助手提示词",
        skills: List<SkillDocument> = emptyList(),
        toolConventions: String = "",
        memoryLimit: Int = 8,
    ) = PromptSpec(
        assistantName = "小助手",
        systemPrompt = systemPrompt,
        model = "gpt-test",
        workspacePath = "/data/ws/a1",
        skills = skills,
        toolConventions = toolConventions,
        nowMillis = fixedNow,
        zoneId = "UTC",
        memoryLimit = memoryLimit,
    )

    private fun user(text: String) = LlmMessage(role = LlmRole.USER, content = text)
    private fun assistant(text: String) = LlmMessage(role = LlmRole.ASSISTANT, content = text)

    // ---------- 变量 ----------

    @Test
    fun `六个变量全部替换`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(
            spec(
                systemPrompt = "{{assistant_name}}|{{time}}|{{date}}|{{model}}|{{workspace}}|{{memory_count}}"
            ),
            recalled = listOf(MemoryItem("m1", "内容一")),
        )
        // 变量段在最前，其后是记忆块
        assertTrue(prompt.startsWith("小助手|22:13|2023-11-14|gpt-test|/data/ws/a1|1"))
        assertTrue(prompt.contains("## 助手记忆（1 条）"))
    }

    @Test
    fun `未声明的变量原样保留便于排查`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(spec(systemPrompt = "值={{unknown_var}}"), recalled = emptyList())
        assertTrue(prompt.contains("{{unknown_var}}"))
    }

    @Test
    fun `空提示词使用默认人格并替换名字`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(spec(systemPrompt = ""), recalled = emptyList())
        assertTrue(prompt.contains("小助手"))
        assertFalse(prompt.contains("{{assistant_name}}"))
    }

    @Test
    fun `renderVariables 纯函数`() {
        assertEquals("a-b", ContextBuilder.renderVariables("{{x}}-{{y}}", mapOf("x" to "a", "y" to "b")))
        assertEquals("", ContextBuilder.renderVariables("", mapOf("x" to "a")))
    }

    // ---------- 技能 ----------

    @Test
    fun `全局技能先于助手技能注入`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(
            spec(
                skills = listOf(
                    SkillDocument(name = "周报", body = "助手技能正文", scope = SkillScope.ASSISTANT),
                    SkillDocument(name = "通用", body = "全局技能正文", scope = SkillScope.GLOBAL),
                )
            ),
            recalled = emptyList(),
        )
        val globalAt = prompt.indexOf("全局技能正文")
        val assistantAt = prompt.indexOf("助手技能正文")
        assertTrue(globalAt >= 0 && assistantAt > globalAt)
        assertTrue(prompt.contains("## 全局技能（1）"))
        assertTrue(prompt.contains("## 助手技能（1）"))
    }

    @Test
    fun `技能声明工具仅作提示`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(
            spec(skills = listOf(SkillDocument(name = "周报", body = "正文", tools = listOf("memory_search", "ask_user")))),
            recalled = emptyList(),
        )
        assertTrue(prompt.contains("memory_search、ask_user"))
    }

    @Test
    fun `技能正文里的变量也被替换`() {
        val builder = ContextBuilder()
        val prompt = builder.buildSystemPrompt(
            spec(skills = listOf(SkillDocument(name = "s", body = "工作区是 {{workspace}}"))),
            recalled = emptyList(),
        )
        assertTrue(prompt.contains("工作区是 /data/ws/a1"))
    }

    // ---------- 记忆 ----------

    @Test
    fun `记忆块按条目渲染并计入 memory_count`() {
        val provider = MemoryProvider { _, _, _ ->
            listOf(
                MemoryItem("m1", "用户喜欢简短回答", type = "preference", score = 0.82),
                MemoryItem("m2", "项目叫 LuzzyRP"),
            )
        }
        val builder = ContextBuilder(memory = provider)
        val messages = runBlocking {
            builder.build(spec(systemPrompt = "记忆数={{memory_count}}"), emptyList(), "你好")
        }
        val system = messages.first().content
        assertTrue(system.contains("记忆数=2"))
        assertTrue(system.contains("## 助手记忆（2 条）"))
        assertTrue(system.contains("[preference] 用户喜欢简短回答（相似度 0.82）"))
        assertTrue(system.contains("- 项目叫 LuzzyRP"))
    }

    @Test
    fun `记忆召回异常退化为无记忆块`() {
        val builder = ContextBuilder(memory = MemoryProvider { _, _, _ -> throw RuntimeException("嵌入服务挂了") })
        val messages = runBlocking { builder.build(spec(systemPrompt = "数={{memory_count}}"), emptyList(), "你好") }
        val system = messages.first().content
        assertTrue(system.contains("数=0"))
        assertFalse(system.contains("助手记忆"))
    }

    // ---------- 装配结构 ----------

    @Test
    fun `build 产出 system 历史 user 顺序`() {
        val builder = ContextBuilder()
        val messages = runBlocking {
            builder.build(spec(toolConventions = "## 工具使用约定\n- get_time"), listOf(user("旧问题"), assistant("旧回答")), "新问题")
        }
        assertEquals(
            listOf(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER),
            messages.map { it.role },
        )
        assertEquals("新问题", messages.last().content)
        assertTrue(messages.first().content.contains("## 工具使用约定"))
    }

    // ---------- 压缩 ----------

    private fun longHistory(): List<LlmMessage> = listOf(
        user("第一轮问题" + "甲".repeat(300)),
        assistant("第一轮回答" + "乙".repeat(300)),
        user("第二轮问题"),
        assistant("第二轮回答"),
    )

    @Test
    fun `超限时压缩保留最近 N 轮并生成 system 摘要`() {
        val summarizer = Summarizer { messages -> "摘要覆盖 ${messages.size} 条消息" }
        val builder = ContextBuilder(summarizer = summarizer, maxContextTokens = 100, compressionRatio = 0.7, keepRecentTurns = 1)
        val history = longHistory()
        assertTrue(builder.needsCompression(history))

        val compressed = runBlocking { builder.compress(history) }
        assertEquals(3, compressed.size)
        assertEquals(LlmRole.SYSTEM, compressed[0].role)
        assertTrue(compressed[0].content.startsWith(ContextBuilder.SUMMARY_HEADER))
        assertTrue(compressed[0].content.contains("摘要覆盖 2 条消息"))
        assertEquals(listOf("第二轮问题", "第二轮回答"), compressed.drop(1).map { it.content })
    }

    @Test
    fun `摘要失败退化为截断且保留最近轮次`() {
        val builder = ContextBuilder(summarizer = Summarizer { null }, maxContextTokens = 100, compressionRatio = 0.7, keepRecentTurns = 1)
        val compressed = runBlocking { builder.compress(longHistory()) }
        assertEquals(LlmRole.SYSTEM, compressed[0].role)
        assertTrue(compressed[0].content.startsWith(ContextBuilder.TRUNCATED_HEADER))
        assertEquals("第二轮问题", compressed[1].content)
    }

    @Test
    fun `摘要抛异常不打断并退化`() {
        val builder = ContextBuilder(
            summarizer = Summarizer { throw RuntimeException("摘要模型不可用") },
            maxContextTokens = 100,
            compressionRatio = 0.7,
            keepRecentTurns = 1,
        )
        val compressed = runBlocking { builder.compress(longHistory()) }
        assertTrue(compressed[0].content.startsWith(ContextBuilder.TRUNCATED_HEADER))
    }

    @Test
    fun `压缩不切断工具调用对`() {
        val history = listOf(
            user("第一轮"),
            LlmMessage(role = LlmRole.ASSISTANT, content = "", toolCalls = listOf(TOOL_CALL)),
            LlmMessage(role = LlmRole.TOOL, content = "12:00", toolCallId = "call_1"),
            assistant("现在 12:00"),
            user("第二轮问题"),
            assistant("第二轮回答"),
        )
        val builder = ContextBuilder(summarizer = Summarizer { "摘要" }, maxContextTokens = 1, compressionRatio = 0.7, keepRecentTurns = 1)
        val compressed = runBlocking { builder.compress(history) }
        // 保留窗口从「第二轮问题」开始，工具对落在摘要里而不是被拦腰切断
        assertEquals("第二轮问题", compressed[1].content)
        assertEquals(LlmRole.ASSISTANT, compressed[2].role)
    }

    @Test
    fun `轮数不足时不压缩`() {
        val builder = ContextBuilder(summarizer = Summarizer { "不应被调用" }, maxContextTokens = 1, compressionRatio = 0.7, keepRecentTurns = 3)
        val history = listOf(user("一"), assistant("二"))
        val compressed = runBlocking { builder.compress(history) }
        assertEquals(history, compressed)
    }

    @Test
    fun `cutIndex 以 USER 为轮边界`() {
        val builder = ContextBuilder(keepRecentTurns = 2)
        val history = listOf(user("u1"), assistant("a1"), user("u2"), assistant("a2"), user("u3"), assistant("a3"))
        assertEquals(2, builder.cutIndex(history, 2))
        assertEquals(0, builder.cutIndex(history, 5))
        assertEquals(0, builder.cutIndex(history, 0))
    }

    @Test
    fun `token 估算按字符数除 3_5`() {
        assertEquals(0, ContextBuilder.estimateTokens(""))
        assertEquals(4, ContextBuilder.estimateTokens("a".repeat(14)))
        assertEquals(6, ContextBuilder.estimateTokens("a".repeat(21)))
        assertEquals(8, ContextBuilder.estimateMessagesTokens(listOf(user("a".repeat(14)))))
    }

    private companion object {
        val TOOL_CALL = com.luzzymeow.luzzyrp.assistant.domain.tool.ToolCall(
            id = "call_1",
            name = "get_time",
            arguments = kotlinx.serialization.json.JsonObject(emptyMap()),
        )
    }
}

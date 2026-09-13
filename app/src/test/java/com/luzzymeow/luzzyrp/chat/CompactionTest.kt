package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **压缩（B5）** 的纯函数门禁：阈值、预算、配对完整性、摘要请求的原前缀重放。
 *
 * 全部确定性：不联网、不看挂钟、不依赖 Android。压缩是「把上下文切一刀」的操作，
 * 切错一次就是丢历史，所以每一条不变式都单独立一条用例钉住。
 */
class CompactionTest {

    private fun user(text: String) = LlmMessage(role = LlmRole.USER, content = text, fromHistory = true)
    private fun assistant(text: String) = LlmMessage(role = LlmRole.ASSISTANT, content = text, fromHistory = true)
    private fun system(text: String) = LlmMessage(role = LlmRole.SYSTEM, content = text)

    /** 造一段「历史 + 本轮输入」的请求（形状与 PromptAssembler 的产物一致）。 */
    private fun conversation(
        turns: Int,
        charsPerMessage: Int = 400,
        systemChars: Int = 200,
    ): List<LlmMessage> = buildList {
        add(system("S".repeat(systemChars)))
        for (i in 1..turns) {
            add(user("用户第 $i 轮" + "甲".repeat(charsPerMessage)))
            add(assistant("模型第 $i 轮" + "乙".repeat(charsPerMessage)))
        }
        // 本轮输入（非历史，必须永远保留）
        add(LlmMessage(role = LlmRole.USER, content = "本轮输入"))
    }

    // ---------------------------------------------------------------- 估算

    @Test
    fun `CJK 一字约一 token 其余四字符约一 token`() {
        assertEquals(4, ContextBudget.estimateText("甲乙丙丁"))
        assertEquals(1, ContextBudget.estimateText("abcd"))
        assertEquals(2, ContextBudget.estimateText("abcde")) // 向上取整
        assertEquals(0, ContextBudget.estimateText(""))
    }

    @Test
    fun `估算随内容增长且含每条消息的结构开销`() {
        val one = ContextBudget.estimate(listOf(user("甲乙丙丁")))
        val two = ContextBudget.estimate(listOf(user("甲乙丙丁"), assistant("甲乙丙丁")))
        assertEquals(one * 2, two)
        assertTrue("结构开销要计入，不能只数正文", one > 4)
    }

    @Test
    fun `工具调用参数也计入估算`() {
        val plain = LlmMessage(role = LlmRole.ASSISTANT, content = "", fromHistory = true)
        val withCall = plain.copy(
            toolCalls = listOf(ToolCall(id = "c1", name = "world_info_lookup", rawArguments = "x".repeat(400))),
        )
        assertTrue(ContextBudget.estimateOne(withCall) > ContextBudget.estimateOne(plain) + 100)
    }

    // ---------------------------------------------------------------- 触发

    @Test
    fun `阈值按 contextWindow 的 0_8 计算`() {
        assertEquals(8_000, Compaction.triggerTokens(10_000))
        assertFalse(Compaction.needed(listOf(user("短")), 10_000))
    }

    @Test
    fun `contextWindow 为 0 或负数时永不触发（关闭自动压缩）`() {
        val huge = conversation(turns = 40)
        assertFalse(Compaction.needed(huge, 0))
        assertFalse(Compaction.needed(huge, -1))
    }

    @Test
    fun `超过阈值时触发`() {
        val chat = conversation(turns = 20, charsPerMessage = 400)
        val tokens = ContextBudget.estimate(chat)
        assertTrue(Compaction.needed(chat, tokens)) // 100% > 80%
        assertFalse(Compaction.needed(chat, tokens * 3))
    }

    // ---------------------------------------------------------------- 裁切计划

    @Test
    fun `只裁历史且保留最新的一段尾部`() {
        val chat = conversation(turns = 10, charsPerMessage = 400, systemChars = 100)
        val plan = Compaction.plan(chat, keepTokens = 1_000)!!
        val kept = chat.drop(plan.cut)
        assertTrue("尾部要保留", kept.isNotEmpty())
        assertTrue("保留段必须更短", kept.size < chat.size)
        assertTrue("本轮输入永远保留", kept.last().content == "本轮输入")
        assertTrue("保留段按预算封顶", plan.keptTokens <= 1_000 + ContextBudget.estimateOne(kept.first()))
        assertTrue(
            "裁掉的只有历史（system 头留在序列里，但不当它是历史）",
            chat.take(plan.cut).filterNot { it.role == LlmRole.SYSTEM }.all { it.fromHistory },
        )
    }

    @Test
    fun `system 头与非历史消息永不参与裁切`() {
        val chat = conversation(turns = 10, charsPerMessage = 400)
        val plan = Compaction.plan(chat, keepTokens = 10)!!
        val rebuilt = Compaction.rebuild(chat, plan.cut, "简报正文")
        assertEquals("system 头必须还在", LlmRole.SYSTEM, rebuilt.first().role)
        assertEquals("system 内容不得改动", chat.first().content, rebuilt.first().content)
        assertEquals("本轮输入必须还在", "本轮输入", rebuilt.last().content)
    }

    @Test
    fun `保留段永远至少含最新一个单位`() {
        // 预算小到装不下任何一条：仍要保留紧邻本轮输入的那条历史（否则模型只看到简报）
        val chat = conversation(turns = 5, charsPerMessage = 2_000)
        val plan = Compaction.plan(chat, keepTokens = 1)!!
        assertTrue("至少保留一条历史", plan.kept >= 1)
        assertTrue(plan.cut < chat.size - 1)
    }

    @Test
    fun `没有任何历史时不裁（返回 null）`() {
        val onlyTurn = listOf(system("人设"), LlmMessage(role = LlmRole.USER, content = "你好"))
        assertNull(Compaction.plan(onlyTurn, keepTokens = 10))
    }

    // ---------------------------------------------------------------- 配对完整性（B5 的硬约束）

    @Test
    fun `不切开 tool_call 与 tool_result 配对`() {
        val chat = buildList {
            add(system("人设"))
            add(user("第一句"))
            // 一次工具调用：assistant(tool_calls) → tool(结果) → assistant(正文)
            add(
                LlmMessage(
                    role = LlmRole.ASSISTANT,
                    toolCalls = listOf(ToolCall(id = "call_1", name = "world_info_lookup")),
                    fromHistory = true,
                ),
            )
            add(
                LlmMessage(
                    role = LlmRole.TOOL,
                    content = "工具结果" + "丙".repeat(600),
                    toolCallId = "call_1",
                    name = "world_info_lookup",
                    fromHistory = true,
                ),
            )
            add(assistant("正文" + "丁".repeat(600)))
            add(LlmMessage(role = LlmRole.USER, content = "本轮输入"))
        }
        // 预算刚好装得下「tool 结果 + 正文」，装不下它们的调用者
        val plan = Compaction.plan(chat, keepTokens = ContextBudget.estimateOne(chat[3]) + 5)!!
        val kept = chat.drop(plan.cut)
        assertTrue("保留段不能以 tool 结果开头（那样响应就没有配对的调用了）", kept.first().role != LlmRole.TOOL)
        val lostCallIds = chat.take(plan.cut).flatMap { it.toolCalls }.map { it.id }
        val keptResultIds = kept.filter { it.role == LlmRole.TOOL }.mapNotNull { it.toolCallId }
        assertTrue("裁掉调用就必须同时裁掉它的结果", keptResultIds.none { it in lostCallIds })
    }

    @Test
    fun `保留段里的每个 tool 结果都有它的调用`() {
        val chat = buildList {
            add(system("人设"))
            add(user("问"))
            add(
                LlmMessage(
                    role = LlmRole.ASSISTANT,
                    toolCalls = listOf(ToolCall(id = "c1", name = "t"), ToolCall(id = "c2", name = "t")),
                    fromHistory = true,
                ),
            )
            add(LlmMessage(role = LlmRole.TOOL, content = "r1", toolCallId = "c1", fromHistory = true))
            add(LlmMessage(role = LlmRole.TOOL, content = "r2", toolCallId = "c2", fromHistory = true))
            add(assistant("正文"))
            add(user("又问"))
            add(
                LlmMessage(
                    role = LlmRole.ASSISTANT,
                    toolCalls = listOf(ToolCall(id = "c3", name = "t")),
                    fromHistory = true,
                ),
            )
            add(LlmMessage(role = LlmRole.TOOL, content = "r3", toolCallId = "c3", fromHistory = true))
            add(assistant("正文2"))
            add(LlmMessage(role = LlmRole.USER, content = "本轮输入"))
        }
        val plan = Compaction.plan(chat, keepTokens = 6)!!
        val kept = chat.drop(plan.cut)
        val keptCallIds = kept.flatMap { it.toolCalls }.map { it.id }.toSet()
        kept.filter { it.role == LlmRole.TOOL }.forEach { result ->
            assertTrue("结果 ${result.toolCallId} 的调用必须也在保留段里", result.toolCallId in keptCallIds)
        }
    }

    // ---------------------------------------------------------------- 摘要请求：原前缀重放（吃一次缓存）

    @Test
    fun `摘要请求的消息是原请求的逐字节前缀加一条指令`() {
        val chat = conversation(turns = 8, charsPerMessage = 300)
        val plan = Compaction.plan(chat, keepTokens = 800)!!
        assertEquals("前缀必须是原请求的前 cut 条，一条不改", chat.take(plan.cut), plan.summaryRequest.dropLast(1))
        assertEquals("指令追加在最后", LlmRole.USER, plan.summaryRequest.last().role)
        assertEquals(Compaction.Instruction, plan.summaryRequest.last().content)
        assertTrue("前缀必须是非空的历史", plan.cut > 1)
    }

    @Test
    fun `重建结果是 头 + 简报 + 保留尾部`() {
        val chat = conversation(turns = 8, charsPerMessage = 300)
        val plan = Compaction.plan(chat, keepTokens = 800)!!
        val rebuilt = Compaction.rebuild(chat, plan.cut, "这是简报")
        // 裁掉 dropped 条、插入 1 条简报 → 条数净变化 -dropped + 1
        assertEquals(chat.size - plan.dropped + 1, rebuilt.size)
        val summaryAt = plan.cut - plan.dropped
        assertTrue("简报落在被裁区域的末尾（原位置）", rebuilt[summaryAt].content.contains("这是简报"))
        assertEquals("保留尾部逐字节不变", chat.drop(plan.cut), rebuilt.drop(summaryAt + 1))
        assertEquals("头（system/预设）逐字节不变", chat.take(summaryAt), rebuilt.take(summaryAt))
    }

    @Test
    fun `简报消息不算用户发言也不算普通历史`() {
        val message = Compaction.summaryMessage("简报")
        assertTrue("不能算一轮用户发言（召回轮号会虚高）", message.runtimeSnapshot)
        assertTrue("它是定稿的历史，不参与相邻合并", message.fromHistory)
        assertEquals("wire 上它就是一条 user 消息", LlmRole.USER, message.role)
    }

    @Test
    fun `压缩后估算确实下降`() {
        val chat = conversation(turns = 12, charsPerMessage = 400)
        val plan = Compaction.plan(chat, keepTokens = 1_000)!!
        val rebuilt = Compaction.rebuild(chat, plan.cut, "简报".repeat(50))
        assertTrue(
            "压缩必须真的更小（否则就是白付一次摘要调用）",
            ContextBudget.estimate(rebuilt) < ContextBudget.estimate(chat),
        )
    }

    @Test
    fun `计划里带上账目：裁掉多少条、保留多少条`() {
        val chat = conversation(turns = 10, charsPerMessage = 400)
        val plan = Compaction.plan(chat, keepTokens = 1_000)!!
        assertEquals(chat.size - plan.cut, plan.kept)
        assertEquals(chat.take(plan.cut).count { it.fromHistory }, plan.dropped)
        assertNotNull(plan.tokensBefore)
        assertTrue(plan.tokensBefore > 0)
    }
}

package com.luzzymeow.luzzyrp.data.chat

import com.luzzymeow.luzzyrp.data.store.MessageEntity
import com.luzzymeow.luzzyrp.ui.pages.chat.AiResult
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatMessage
import com.luzzymeow.luzzyrp.ui.pages.chat.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息的**存储编解码**（A6）——纯 JVM 单测（不需要 Room 实例）。
 *
 * 为什么值得单独钉：这两条认回规则都属于「重启后不能少东西」，而它们**坏了也不报错**——
 * - 尾部快照认不回来 → 重启后它变成一条用户发言（内容是 `Current runtime context…`），
 *   还会被算进楼数；
 * - 思考内容认不回来 → 重启后消息里少一块（内联 `<thinking>` 那一支）。
 *
 * 所以这里既测「编码出的 role/payload 正确」，也测**往返**（decode(encode(x)) == x）。
 */
class ChatMessageCodecTest {

    private fun row(
        message: ChatMessage,
        role: String = ChatSessionRepository.roleOf(message),
        payload: String = ChatSessionRepository.payloadOf(message),
        reasoning: String? = null,
        name: String? = message.name,
    ) = MessageEntity(
        scopeId = "char-1",
        sortIndex = 0,
        id = null,
        role = role,
        name = name,
        content = message.text(),
        reasoning = reasoning,
        payload = payload,
    )

    // ---------------------------------------------------------------- 编码

    @Test
    fun `快照落盘时 role 独立成一种并带 payload 标记（双保险）`() {
        val snapshot = ChatMessage.Snapshot("Current runtime context. 今晚下着雨。")
        assertEquals(ChatSessionRepository.ROLE_SNAPSHOT, ChatSessionRepository.roleOf(snapshot))
        assertTrue(ChatSessionRepository.isSnapshotPayload(ChatSessionRepository.payloadOf(snapshot)))
        assertTrue(
            "原文必须逐字落盘（它就是请求里那一条）",
            row(snapshot).content.contains("Current runtime context"),
        )
    }

    @Test
    fun `普通消息不带快照标记`() {
        assertEquals("user", ChatSessionRepository.roleOf(ChatMessage.User("你好")))
        assertEquals("assistant", ChatSessionRepository.roleOf(ChatMessage.Ai(results = listOf(AiResult(raw = "嗯")))))
        assertFalse(ChatSessionRepository.isSnapshotPayload(ChatSessionRepository.payloadOf(ChatMessage.User("你好"))))
        assertFalse(
            ChatSessionRepository.isSnapshotPayload(
                ChatSessionRepository.payloadOf(ChatMessage.Ai(results = listOf(AiResult(raw = "嗯")))),
            ),
        )
    }

    @Test
    fun `坏 payload 一律当普通消息，不抛异常`() {
        assertFalse(ChatSessionRepository.isSnapshotPayload(""))
        assertFalse(ChatSessionRepository.isSnapshotPayload("{"))
        assertFalse(ChatSessionRepository.isSnapshotPayload("""{"imageAttachments":[]}"""))
        assertFalse(ChatSessionRepository.isSnapshotPayload("""{"luzzySnapshot":"yes"}"""))
    }

    // ---------------------------------------------------------------- 往返

    @Test
    fun `三种消息都能逐字往返`() {
        val messages = listOf(
            ChatMessage.User("一句原话"),
            ChatMessage.Snapshot("Current runtime context. 设定。"),
            ChatMessage.Ai(
                name = "谢昭",
                results = listOf(AiResult(raw = "他的回答", thinkNodes = emptyList())),
            ),
        )
        messages.forEach { original ->
            val restored = decodeMessage(row(original))
            assertEquals("往返后类型必须相同：${original::class.simpleName}", original, restored)
        }
    }

    // ---------------------------------------------------------------- 认回（旧行兼容）

    @Test
    fun `只带 payload 标记、role 写成 user 的旧式快照也认得回来`() {
        // 双保险的用途：将来若有人用别的写法落盘快照（例如经迁移通道进来），
        // 只要带标记就必须认得回来——否则它会变成一条用户发言。
        val entity = MessageEntity(
            scopeId = "char-1",
            sortIndex = 3,
            id = null,
            role = "user",
            name = "运行时上下文",
            content = "Current runtime context. 旧式落盘。",
            reasoning = null,
            payload = """{"luzzySnapshot":true,"extra":1}""",
        )
        assertTrue(decodeMessage(entity) is ChatMessage.Snapshot)
    }

    @Test
    fun `普通 user 行仍旧是用户消息`() {
        val entity = MessageEntity(
            scopeId = "char-1",
            sortIndex = 1,
            id = "m1",
            role = "user",
            name = "你",
            content = "原话",
            reasoning = null,
            // 旧数据里用户消息带 isSelf/avatar/imageAttachments（多余字段必须能背过去）
            payload = """{"isSelf":true,"imageAttachments":[]}""",
        )
        assertEquals(ChatMessage.User("原话"), decodeMessage(entity))
    }

    @Test
    fun `assistant 行的内联思维链与 reasoning 列都还原成节点（两处都认）`() {
        val inlineOnly = MessageEntity(
            scopeId = "char-1",
            sortIndex = 2,
            id = null,
            role = "assistant",
            name = "谢昭",
            content = "<thinking>先想了想</thinking>正文在这里",
            reasoning = null,
            payload = "{}",
        )
        val restored = decodeMessage(inlineOnly) as ChatMessage.Ai
        assertTrue("内联 CoT 那一支必须还原出节点", restored.thinkNodes.isNotEmpty())
        assertEquals("正文必须剥掉思维链", "正文在这里", restored.body)

        val bothSources = inlineOnly.copy(reasoning = "另一段思考")
        val two = (decodeMessage(bothSources) as ChatMessage.Ai).thinkNodes
        assertEquals("两处内容不同时都要保留", 2, two.size)
    }

    @Test
    fun `reasoning 列与内联 CoT 相同时不重复记节点`() {
        val entity = MessageEntity(
            scopeId = "char-1",
            sortIndex = 2,
            id = null,
            role = "assistant",
            name = "谢昭",
            content = "<thinking>同一段</thinking>正文",
            reasoning = "同一段",
            payload = "{}",
        )
        assertEquals(1, (decodeMessage(entity) as ChatMessage.Ai).thinkNodes.size)
    }

    // ---------------------------------------------------------------- B3/B4：工具轨迹与中断标记

    private fun aiWith(
        trail: List<com.luzzymeow.luzzyrp.chat.ToolStep> = emptyList(),
        interrupted: Boolean = false,
    ) = ChatMessage.Ai(
        name = "谢昭",
        results = listOf(AiResult(raw = "正文", toolTrail = trail, interrupted = interrupted)),
    )

    @Test
    fun `工具轨迹随消息落库并逐字往返`() {
        val steps = listOf(
            com.luzzymeow.luzzyrp.chat.ToolStep("world_info_lookup", """{"keywords":["钟楼"]}""", "两条设定"),
            com.luzzymeow.luzzyrp.chat.ToolStep("world_info_lookup", """{"keywords":["苹果"]}""", "一条设定"),
        )
        val original = aiWith(trail = steps)
        val restored = decodeMessage(row(original)) as ChatMessage.Ai

        assertEquals("轨迹要原样回来（否则模型下一轮看不见自己查过什么）", steps, restored.current.toolTrail)
    }

    @Test
    fun `未拿到结果的那条落库为 null（不是空串）`() {
        // null 与 "" 是两件事：前者会触发「结果未知」修复，后者会被当成「工具返回了空」
        val original = aiWith(trail = listOf(com.luzzymeow.luzzyrp.chat.ToolStep("t", "{}", null)))
        val payload = ChatSessionRepository.payloadOf(original)
        assertTrue("payload 里要显式写 null：$payload", payload.contains("null"))

        val restored = decodeMessage(row(original)) as ChatMessage.Ai
        assertEquals(null, restored.current.toolTrail.single().result)
    }

    @Test
    fun `中断标记随消息落库并认回`() {
        val original = aiWith(interrupted = true)
        assertEquals(true, (decodeMessage(row(original)) as ChatMessage.Ai).current.interrupted)

        val normal = aiWith()
        assertEquals(false, (decodeMessage(row(normal)) as ChatMessage.Ai).current.interrupted)
    }

    @Test
    fun `没有轨迹也没有中断标记的普通消息仍旧写空对象`() {
        assertEquals("{}", ChatSessionRepository.payloadOf(aiWith()))
        assertEquals("{}", ChatSessionRepository.payloadOf(ChatMessage.User("你好")))
    }

    @Test
    fun `坏轨迹数据一律当没有轨迹，绝不抛异常`() {
        assertTrue(ChatSessionRepository.toolTrailOf("{").isEmpty())
        assertTrue(ChatSessionRepository.toolTrailOf("""{"luzzyToolTrail":"不是数组"}""").isEmpty())
        assertTrue(ChatSessionRepository.toolTrailOf("""{"luzzyToolTrail":[{"args":"{}"}]}""").isEmpty())
        assertTrue("缺 result 键按「没有结果」处理（安全方向）",
            ChatSessionRepository.toolTrailOf("""{"luzzyToolTrail":[{"name":"t","args":"{}"}]}""")
                .single().result == null)
        assertFalse(ChatSessionRepository.interruptedOf("不是 JSON"))
    }

    // ---------------------------------------------------------------- B5：压缩水位线

    @Test
    fun `压缩简报落盘时 role 独立成一种，正文逐字保留`() {
        val summary = ChatMessage.Compacted("角色是谢昭；用户在找钟楼上的红苹果树。")
        assertEquals(ChatSessionRepository.ROLE_COMPACTED, ChatSessionRepository.roleOf(summary))
        assertEquals(
            "简报正文必须逐字落盘（它就是请求里那一条的来源）",
            "角色是谢昭；用户在找钟楼上的红苹果树。",
            row(summary).content,
        )
        assertEquals("独立 role 让它天然被楼数统计排除", "compacted", row(summary).role)
    }

    @Test
    fun `压缩水位线能逐字往返（重启后仍认得回来）`() {
        val original = ChatMessage.Compacted("简报：他们正在找那棵树。")
        assertEquals(original, decodeMessage(row(original)))
    }

    @Test
    fun `简报不会被误认成用户发言或 AI 消息`() {
        val entity = MessageEntity(
            scopeId = "char-1",
            sortIndex = 7,
            id = null,
            role = ChatSessionRepository.ROLE_COMPACTED,
            name = "对话简报",
            content = "[对话简报]\n\n简报",
            reasoning = null,
            payload = "{}",
        )
        val restored = decodeMessage(entity)
        assertTrue("认错的后果是「用户以为自己说过这段话」", restored is ChatMessage.Compacted)
    }

    // ---------------------------------------------------------------- C3：多候选持久化

    private fun multiCandidate() = ChatMessage.Ai(
        name = "谢昭",
        results = listOf(
            AiResult(raw = "第一版回答", finishReason = "stop", elapsedMs = 1_200L),
            AiResult(
                raw = "第二版回答",
                thinkNodes = listOf(
                    com.luzzymeow.luzzyrp.ui.pages.chat.ThinkNode.Brainstorm(text = "先想了想", seconds = 3.5, streaming = false),
                ),
                finishReason = "length",
                usage = com.luzzymeow.luzzyrp.chat.UsageInfo(input = 100, output = 20, cached = 80),
                elapsedMs = 2_400L,
                toolTrail = listOf(com.luzzymeow.luzzyrp.chat.ToolStep("world_info_lookup", """{"keywords":["钟楼"]}""", "两条设定")),
            ),
            AiResult(raw = "第三版回答", interrupted = true),
        ),
        index = 1,
    )

    @Test
    fun `多候选整组落库并在重启后逐字段回来`() {
        val original = multiCandidate()
        val restored = decodeMessage(row(original)) as ChatMessage.Ai

        assertEquals("候选条数必须回来（否则切换器消失）", 3, restored.results.size)
        assertEquals("当前展示的候选下标必须回来（否则应用像改了他的选择）", 1, restored.index)
        assertEquals("当前正文对得上", "第二版回答", restored.raw)
        assertEquals("各候选的正文逐字回来", listOf("第一版回答", "第二版回答", "第三版回答"), restored.results.map { it.raw })
    }

    @Test
    fun `候选的用量 耗时 结束原因 中断标记都回来`() {
        val restored = decodeMessage(row(multiCandidate())) as ChatMessage.Ai

        val second = restored.results[1]
        assertEquals("stop", restored.results[0].finishReason)
        assertEquals(1_200L, restored.results[0].elapsedMs)
        assertEquals("length", second.finishReason)
        assertEquals(2_400L, second.elapsedMs)
        assertEquals("用量三个数都要回来（缓存命中数用于命中率展示）", 80, second.usage?.cached)
        assertEquals(100, second.usage?.input)
        assertEquals(20, second.usage?.output)
        assertEquals("第三版的中断标记", true, restored.results[2].interrupted)
        assertEquals("未中断的候选不误标", false, restored.results[0].interrupted)
    }

    @Test
    fun `候选的思考正文与工具轨迹都回来`() {
        val restored = decodeMessage(row(multiCandidate())) as ChatMessage.Ai
        val second = restored.results[1]

        val brainstorm = second.thinkNodes.filterIsInstance<com.luzzymeow.luzzyrp.ui.pages.chat.ThinkNode.Brainstorm>()
        assertEquals("reasoning 正文是模型说过的话，必须回来", 1, brainstorm.size)
        assertEquals("先想了想", brainstorm.single().text)
        assertEquals(3.5, brainstorm.single().seconds, 0.001)
        assertEquals("历史思考不是「正在流式」", false, brainstorm.single().streaming)

        assertEquals("该候选自己的工具轨迹随候选走", 1, second.toolTrail.size)
        assertEquals("world_info_lookup", second.toolTrail.single().name)
    }

    @Test
    fun `单候选不写候选数组（payload 不白撑大）`() {
        val single = ChatMessage.Ai(results = listOf(AiResult(raw = "只有一版")))
        assertEquals("{}", ChatSessionRepository.payloadOf(single))
        assertTrue("没有候选键时读回空表", ChatSessionRepository.candidatesOf("{}").isEmpty())
    }

    @Test
    fun `老行没有候选键时走单候选回落路径`() {
        val entity = MessageEntity(
            scopeId = "char-1",
            sortIndex = 4,
            id = null,
            role = "assistant",
            name = "谢昭",
            content = "老消息正文",
            reasoning = null,
            payload = "{}",
        )
        val restored = decodeMessage(entity) as ChatMessage.Ai
        assertEquals("回落成单候选（与候选键出现之前的行为一致）", 1, restored.results.size)
        assertEquals("老消息正文", restored.raw)
        assertEquals(0, restored.index)
    }

    @Test
    fun `坏候选数据一律回落单候选，绝不抛异常`() {
        assertTrue(ChatSessionRepository.candidatesOf("{").isEmpty())
        assertTrue(ChatSessionRepository.candidatesOf("""{"luzzyCandidates":"不是数组"}""").isEmpty())
        assertTrue("缺 raw 的候选丢掉", ChatSessionRepository.candidatesOf("""{"luzzyCandidates":[{"finishReason":"stop"}]}""").isEmpty())
        assertEquals("越界/坏下标一律 0", 0, ChatSessionRepository.candidateIndexOf("""{"luzzyCandidateIndex":"abc"}"""))
        assertEquals("负数下标归一为 0", 0, ChatSessionRepository.candidateIndexOf("""{"luzzyCandidateIndex":-3}"""))
    }

    @Test
    fun `候选里坏一个字段不丢整条候选的正文`() {
        // 用量是 null、耗时写坏、思考数组里混了非对象——正文仍然必须读得回来
        val payload = """
            {"luzzyCandidates":[
              {"raw":"第一版","usage":null,"elapsedMs":"坏了","thinkNodes":[1,{"text":"思考"}]},
              {"raw":"第二版"}
            ],"luzzyCandidateIndex":0}
        """.trimIndent()
        val results = ChatSessionRepository.candidatesOf(payload)

        assertEquals(2, results.size)
        assertEquals("第一版", results[0].raw)
        assertEquals(null, results[0].elapsedMs)
        assertEquals(null, results[0].usage)
        assertEquals("能认出的思考节点仍保留", 1, results[0].thinkNodes.size)
    }
}

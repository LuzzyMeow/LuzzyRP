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
}

package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `send_to_rp_chat` 预留工具单测（PLAN §12.2：默认关、未接线明确提示）。 */
class SendToRpChatToolTest {

    private class FakeCtx : ToolContext {
        override val assistantId = "a1"
        override val conversationId = "c1"
        override val workspace = object : WorkspaceAccess {
            override suspend fun list(relativeDir: String): List<WorkspaceEntry> = emptyList()
            override suspend fun read(relativePath: String): ByteArray = ByteArray(0)
            override suspend fun write(relativePath: String, bytes: ByteArray) {}
            override suspend fun delete(relativePath: String) {}
            override suspend fun move(fromRelative: String, toRelative: String) {}
            override suspend fun mkdir(relativeDir: String) {}
            override suspend fun exists(relativePath: String): Boolean = false
        }
        override val cancelled = { false }
        override val log: (String) -> Unit = {}
    }

    private fun args(vararg pairs: Pair<String, String>) = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    @Test
    fun `分级为 T2 默认关闭`() {
        val tool = SendToRpChatTool()
        assertEquals(ToolTier.T2_WRITE_DEVICE, tool.tier)
        assertTrue(!tool.tier.defaultEnabled)
        assertTrue(tool.tier.requiresApproval)
    }

    @Test
    fun `未接线时返回可操作提示`() = runBlocking {
        val result = SendToRpChatTool().execute(args("text" to "你好"), FakeCtx())
        val error = result as ToolResult.Error
        assertTrue(error.message.contains("未启用"))
        assertTrue(error.message.contains("不自动同步"))
    }

    @Test
    fun `接线后写入成功并回报字数`() = runBlocking {
        var captured: Pair<String, String>? = null
        val tool = SendToRpChatTool(RpChatPort { id, text ->
            captured = id to text
            true
        })
        val result = tool.execute(args("conversationId" to "rp-1", "text" to "写进去"), FakeCtx())
        assertTrue(result is ToolResult.Ok)
        assertEquals("rp-1" to "写进去", captured)
        assertTrue((result as ToolResult.Ok).text.contains("3 字符"))
    }

    @Test
    fun `缺 text 报错`() = runBlocking {
        val result = SendToRpChatTool().execute(buildJsonObject { }, FakeCtx())
        assertTrue((result as ToolResult.Error).message.contains("缺少 text"))
    }

    @Test
    fun `端口抛异常不向上传播`() = runBlocking {
        val tool = SendToRpChatTool(RpChatPort { _, _ -> error("bridge down") })
        val result = tool.execute(args("text" to "x"), FakeCtx())
        assertTrue(result is ToolResult.Error)
    }
}

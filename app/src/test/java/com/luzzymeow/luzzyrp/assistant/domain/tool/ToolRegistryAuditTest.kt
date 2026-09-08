package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具审计单测（PLAN §13.2：参数脱敏、结果截断、审计失败不影响执行）。 */
class ToolRegistryAuditTest {

    private class RecordingTool(
        override val name: String = "workspace_write",
        override val tier: ToolTier = ToolTier.T1_WRITE_APP,
        private val result: ToolResult = ToolResult.Ok("ok"),
    ) : Tool {
        override val description = "t"
        override val parameters: JsonObject = Schema.empty()
        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = result
    }

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

    @Test
    fun `参数脱敏只留键名与长度`() {
        val registry = ToolRegistry()
        val call = ToolCall(
            id = "c1",
            name = "workspace_write",
            arguments = buildJsonObject {
                put("path", "secret.txt")
                put("content", "绝密内容不该出现在审计里")
            },
        )
        val redacted = registry.redactArgs(call)
        assertTrue(redacted.contains("path:"))
        assertTrue(redacted.contains("content:"))
        assertTrue("不得回显参数值", !redacted.contains("绝密内容"))
        assertTrue(!redacted.contains("secret.txt"))
    }

    @Test
    fun `执行成功落审计且 ok 为真`() = runBlocking {
        val entries = mutableListOf<AuditEntry>()
        val registry = ToolRegistry(audit = AuditSink { entries += it }, now = { 1_000L })
        registry.register(RecordingTool())
        val result = registry.execute(
            ToolCall("c1", "workspace_write", buildJsonObject { put("path", "a") }),
            FakeCtx(),
        )
        assertTrue(result is ToolResult.Ok)
        assertEquals(1, entries.size)
        assertEquals("workspace_write", entries[0].toolName)
        assertTrue(entries[0].ok)
        assertEquals("a1", entries[0].assistantId)
        assertEquals(0L, entries[0].durationMs)
    }

    @Test
    fun `执行失败落审计且 ok 为假`() = runBlocking {
        val entries = mutableListOf<AuditEntry>()
        val registry = ToolRegistry(audit = AuditSink { entries += it }, now = { 5L })
        registry.register(RecordingTool(result = ToolResult.Error("炸了")))
        registry.execute(ToolCall("c1", "workspace_write", buildJsonObject { }), FakeCtx())
        assertEquals(1, entries.size)
        assertTrue(!entries[0].ok)
        assertTrue(entries[0].resultPreview.contains("炸了"))
    }

    @Test
    fun `审计实现抛异常不影响工具结果`() = runBlocking {
        val registry = ToolRegistry(audit = AuditSink { error("审计炸了") })
        registry.register(RecordingTool())
        val result = registry.execute(ToolCall("c1", "workspace_write", buildJsonObject { }), FakeCtx())
        assertTrue(result is ToolResult.Ok)
    }

    @Test
    fun `结果预览截断到上限`() = runBlocking {
        val entries = mutableListOf<AuditEntry>()
        val registry = ToolRegistry(audit = AuditSink { entries += it })
        registry.register(RecordingTool(result = ToolResult.Ok("x".repeat(2000))))
        registry.execute(ToolCall("c1", "workspace_write", buildJsonObject { }), FakeCtx())
        assertEquals(ToolRegistry.AUDIT_PREVIEW_LIMIT, entries[0].resultPreview.length)
    }
}

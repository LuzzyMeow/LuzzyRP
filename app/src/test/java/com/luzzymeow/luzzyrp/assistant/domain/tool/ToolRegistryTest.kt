package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ToolRegistry] 单测：注册 / 开关过滤 / schema / MCP 命名空间 / 执行错误兜底。 */
class ToolRegistryTest {

    private class FakeTool(
        override val name: String,
        override val description: String = "fake",
        override val tier: ToolTier = ToolTier.T0_READ,
        private val behavior: suspend () -> ToolResult = { ToolResult.Ok("ok") },
    ) : Tool {
        override val parameters: JsonObject = Schema.objectSchema(
            properties = mapOf("q" to Schema.string("查询词")),
            required = listOf("q"),
        )

        var executions: Int = 0

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            executions++
            return behavior()
        }
    }

    private val readTool = FakeTool("get_time")
    private val writeTool = FakeTool("memory_write", tier = ToolTier.T1_WRITE_APP)
    private val deviceTool = FakeTool("terminal_run", tier = ToolTier.T2_WRITE_DEVICE)

    private val context = object : ToolContext {
        override val assistantId: String = "a1"
        override val conversationId: String = "c1"
        override val workspace: WorkspaceAccess = NoWorkspaceAccessStub
        override val cancelled: () -> Boolean = { false }
        override val log: (String) -> Unit = {}
    }

    @Test
    fun `注册 查找 覆盖`() {
        val registry = ToolRegistry(listOf(readTool, writeTool))
        assertEquals(readTool, registry.find("get_time"))
        assertNull(registry.find("nope"))
        assertEquals(2, registry.all().size)

        val replacement = FakeTool("get_time", description = "新版本")
        registry.register(replacement)
        assertEquals("新版本", registry.find("get_time")!!.description)
        registry.unregister("get_time")
        assertNull(registry.find("get_time"))
    }

    @Test
    fun `schema 仅含已启用工具`() {
        val registry = ToolRegistry(listOf(readTool, writeTool, deviceTool))
        // T0 默认开、T1 默认开（需审批）、T2 默认关
        assertEquals(listOf("get_time", "memory_write"), registry.schemas().map { schemaName(it) })
    }

    @Test
    fun `用户开启 T2 后进入 schema 列表`() {
        val gate = ApprovalGate(globalSwitch = { name -> if (name == "terminal_run") true else null })
        val registry = ToolRegistry(listOf(readTool, deviceTool), gate)
        assertTrue(registry.schemas().any { schemaName(it) == "terminal_run" })
    }

    @Test
    fun `schema 结构为 OpenAI 工具声明`() {
        val schema = ToolRegistry(listOf(readTool)).schemas().single()
        assertEquals("function", schema["type"]!!.jsonPrimitive.content)
        val function = schema["function"]!!.jsonObject
        assertEquals("get_time", function["name"]!!.jsonPrimitive.content)
        assertEquals("fake", function["description"]!!.jsonPrimitive.content)
        assertEquals("object", function["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `MCP 命名空间组装与解析`() {
        assertEquals("mcp__serverA__web_search", ToolRegistry.mcpToolName("serverA", "web_search"))
        assertEquals(McpToolRef("serverA", "web_search"), ToolRegistry.parseMcpName("mcp__serverA__web_search"))
        // toolName 含 __ 时按第一个分隔符切分
        assertEquals(McpToolRef("serverA", "a__b"), ToolRegistry.parseMcpName("mcp__serverA__a__b"))
        assertNull(ToolRegistry.parseMcpName("get_time"))
        assertNull(ToolRegistry.parseMcpName("mcp__"))
        assertNull(ToolRegistry.parseMcpName("mcp__onlyserver"))
        assertNull(ToolRegistry.parseMcpName("mcp____tool"))
    }

    @Test
    fun `执行未注册工具返回 Error`() {
        val registry = ToolRegistry(listOf(readTool))
        val result = runBlocking { registry.execute(ToolCall("c1", "nope", JsonObject(emptyMap())), context) }
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("未知工具"))
        assertFalse(result.retryable)
    }

    @Test
    fun `执行已关闭工具返回 Error 且不落到工具实现`() {
        val registry = ToolRegistry(listOf(deviceTool))
        val result = runBlocking { registry.execute(ToolCall("c1", "terminal_run", JsonObject(emptyMap())), context) }
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("已关闭"))
        assertEquals(0, deviceTool.executions)
    }

    @Test
    fun `工具抛异常转 Error 不抛给调用方`() {
        val boom = FakeTool("boom") { throw IllegalStateException("炸了") }
        val registry = ToolRegistry(listOf(boom))
        val result = runBlocking { registry.execute(ToolCall("c1", "boom", JsonObject(emptyMap())), context) }
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("炸了"))
        assertFalse(result.retryable)
    }

    @Test
    fun `正常执行透传结果`() {
        val registry = ToolRegistry(listOf(readTool))
        val result = runBlocking { registry.execute(ToolCall("c1", "get_time", JsonObject(emptyMap())), context) }
        assertEquals(ToolResult.Ok("ok"), result)
        assertEquals(1, readTool.executions)
    }

    @Test
    fun `约定文本只列已启用工具并标注审批`() {
        val registry = ToolRegistry(listOf(readTool, writeTool, deviceTool))
        val conventions = registry.conventions()
        assertTrue(conventions.contains("get_time"))
        assertTrue(conventions.contains("memory_write"))
        assertTrue(conventions.contains("需用户审批"))
        assertFalse(conventions.contains("terminal_run"))
    }

    @Test
    fun `无可用工具时约定文本给出兜底提示`() {
        val conventions = ToolRegistry(emptyList()).conventions()
        assertTrue(conventions.contains("没有可用工具"))
    }

    private fun schemaName(schema: JsonObject): String =
        schema["function"]!!.jsonObject["name"]!!.jsonPrimitive.content

    /** 测试用空工作区（[NoWorkspaceAccess] 在 loop 包，这里给最小实现避免跨包耦合）。 */
    private object NoWorkspaceAccessStub : WorkspaceAccess {
        override suspend fun list(relativeDir: String): List<WorkspaceEntry> = emptyList()
        override suspend fun read(relativePath: String): ByteArray = ByteArray(0)
        override suspend fun write(relativePath: String, bytes: ByteArray) = Unit
        override suspend fun delete(relativePath: String) = Unit
        override suspend fun move(fromRelative: String, toRelative: String) = Unit
        override suspend fun mkdir(relativeDir: String) = Unit
        override suspend fun exists(relativePath: String): Boolean = false
    }
}

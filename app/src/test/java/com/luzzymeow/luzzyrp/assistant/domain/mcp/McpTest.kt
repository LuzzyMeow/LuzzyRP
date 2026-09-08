package com.luzzymeow.luzzyrp.assistant.domain.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** MCP 单测（PLAN §9.2/§9.4：JSON 导入解析、命名空间、响应形态兼容）。 */
class McpTest {

    // ---------- 配置解析 ----------

    @Test
    fun `解析 mcpServers 映射（stdio 与远程混合）`() {
        val raw = """
            {
              "mcpServers": {
                "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"] },
                "remote": { "url": "https://example.com/mcp", "headers": { "Authorization": "Bearer x" } }
              }
            }
        """.trimIndent()
        val list = McpConfigParser.parse(raw)
        assertEquals(2, list.size)
        val fs = list.first { it.name == "filesystem" }
        assertEquals(McpServerConfig.TRANSPORT_STDIO, fs.transport)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-filesystem", "/path"), fs.args)
        val remote = list.first { it.name == "remote" }
        assertEquals(McpServerConfig.TRANSPORT_HTTP, remote.transport)
        assertEquals("Bearer x", remote.headers["Authorization"])
    }

    @Test
    fun `单服务器对象与数组都可解析`() {
        val single = McpConfigParser.parse("""{"name":"r","type":"http","url":"https://a/mcp"}""")
        assertEquals(1, single.size)
        assertEquals("r", single[0].name)
        assertEquals(McpServerConfig.TRANSPORT_HTTP, single[0].transport)

        val array = McpConfigParser.parse("""[{"name":"a","url":"https://a/sse"},{"name":"b","command":"uvx"}]""")
        assertEquals(listOf("a", "b"), array.map { it.name })
        assertEquals(McpServerConfig.TRANSPORT_SSE, array[0].transport)
        assertEquals(McpServerConfig.TRANSPORT_STDIO, array[1].transport)
    }

    @Test
    fun `url 含 sse 推断旧式 SSE 传输`() {
        val list = McpConfigParser.parse("""{"name":"x","url":"https://host/sse"}""")
        assertEquals(McpServerConfig.TRANSPORT_SSE, list[0].transport)
    }

    @Test
    fun `缺少 url 与 command 抛异常`() {
        val e = assertThrows(McpException::class.java) { McpConfigParser.parse("""{"name":"x"}""") }
        assertTrue(e.message!!.contains("缺少 url 或 command"))
    }

    @Test
    fun `非法 JSON 抛异常`() {
        assertThrows(McpException::class.java) { McpConfigParser.parse("not json") }
    }

    @Test
    fun `占位符提取`() {
        val list = McpConfigParser.parse(
            """{"name":"x","url":"https://h/${'$'}{HOST}/mcp","headers":{"Authorization":"Bearer ${'$'}{TOKEN}"}}"""
        )
        assertEquals(listOf("HOST", "TOKEN"), McpConfigParser.placeholders(list[0]))
    }

    // ---------- 命名空间 ----------

    @Test
    fun `命名空间限定与解析`() {
        val qualified = McpNamespace.qualify("srv1", "read_file")
        assertEquals("mcp__srv1__read_file", qualified)
        assertEquals("srv1" to "read_file", McpNamespace.parse(qualified))
    }

    @Test
    fun `工具名含双下划线时按第一个分隔符切分`() {
        assertEquals("srv" to "a__b", McpNamespace.parse("mcp__srv__a__b"))
    }

    @Test
    fun `非 MCP 名返回 null`() {
        assertNull(McpNamespace.parse("workspace_read"))
        assertNull(McpNamespace.parse("mcp__only"))
        assertNull(McpNamespace.parse("mcp__srv__"))
    }

    // ---------- 响应形态 ----------

    private fun client() = McpClient()

    @Test
    fun `纯 JSON 响应解析`() {
        val envelope = client().parseEnvelope("""{"jsonrpc":"2.0","id":7,"result":{"ok":true}}""", 7)
        assertEquals(true, envelope["result"]!!.let { (it as kotlinx.serialization.json.JsonObject)["ok"]!!.toString() == "true" })
    }

    @Test
    fun `SSE 帧响应解析并匹配 id`() {
        val body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"a\":1}}\n\n" +
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"a\":2}}\n\n"
        val envelope = client().parseEnvelope(body, 2)
        assertEquals("2", ((envelope["result"] as kotlinx.serialization.json.JsonObject)["a"]).toString())
    }

    @Test
    fun `批响应取匹配 id 的一条`() {
        val body = """[{"jsonrpc":"2.0","id":1,"result":{"a":1}},{"jsonrpc":"2.0","id":2,"result":{"a":2}}]"""
        val envelope = client().parseEnvelope(body, 1)
        assertEquals("1", ((envelope["result"] as kotlinx.serialization.json.JsonObject)["a"]).toString())
    }

    @Test
    fun `空响应抛异常`() {
        assertThrows(McpException::class.java) { client().parseEnvelope("   ", 1) }
    }

    // ---------- 适配器 ----------

    @Test
    fun `适配器命名空间与分级`() {
        val adapter = McpToolAdapter(
            serverId = "srv",
            serverName = "演示",
            config = McpServerConfig("srv", McpServerConfig.TRANSPORT_HTTP, url = "https://a/mcp"),
            spec = McpToolSpec("read_file", "读取文件", """{"type":"object","properties":{"path":{"type":"string"}}}"""),
        )
        assertEquals("mcp__srv__read_file", adapter.name)
        assertEquals(com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier.T2_WRITE_DEVICE, adapter.tier)
        assertTrue(adapter.description.contains("演示"))
        assertEquals("object", (adapter.parameters["type"]!!).toString().trim('"'))
    }

    @Test
    fun `非法 schema 回退空对象 schema`() {
        val adapter = McpToolAdapter(
            serverId = "srv",
            serverName = "s",
            config = McpServerConfig("srv", McpServerConfig.TRANSPORT_HTTP, url = "https://a/mcp"),
            spec = McpToolSpec("t", "d", "not-json"),
        )
        assertEquals("object", (adapter.parameters["type"]!!).toString().trim('"'))
    }
}

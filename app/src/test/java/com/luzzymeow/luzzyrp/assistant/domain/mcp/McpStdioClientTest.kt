package com.luzzymeow.luzzyrp.assistant.domain.mcp

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [McpStdioClient] 单测：用内存假传输验证 JSON-RPC over stdio 的请求/响应/错误/超时语义。
 *
 * 真实进程路径（proot 沙盒内 `npx` 等）属真机验证范围；本测试锁定协议层逻辑。
 */
class McpStdioClientTest {

    /** 假传输：把写入的行交给「服务器」回调，响应入队。 */
    private class FakeTransport(
        private val server: (String) -> String?,
        private val alive: Boolean = true,
    ) : StdioTransport {
        val written = mutableListOf<String>()
        private val responses = LinkedBlockingQueue<String>()

        override fun writeLine(line: String) {
            written += line
            server(line)?.let { responses.put(it) }
        }

        override fun readLine(timeoutMs: Long): String? =
            responses.poll(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)

        override fun isAlive(): Boolean = alive
        override fun close() {}
    }

    private fun idOf(line: String): String =
        line.substringAfter("\"id\":").substringBefore(',').trim()

    private fun echoServer(line: String): String? {
        val id = idOf(line)
        return when {
            line.contains("tools/list") ->
                """{"jsonrpc":"2.0","id":$id,"result":{"tools":[{"name":"echo","description":"回显","inputSchema":{"type":"object"}}]}}"""
            line.contains("tools/call") ->
                """{"jsonrpc":"2.0","id":$id,"result":{"content":[{"type":"text","text":"pong"}]}}"""
            line.contains("initialize") ->
                """{"jsonrpc":"2.0","id":$id,"result":{"serverInfo":{"name":"fake","version":"9"}}}"""
            else -> null // notifications/initialized 无响应
        }
    }

    @Test
    fun `握手返回 serverInfo`() {
        val client = McpStdioClient(FakeTransport(::echoServer))
        assertEquals("fake 9", client.initialize())
    }

    @Test
    fun `握手后发送 initialized 通知（无 id）`() {
        val transport = FakeTransport(::echoServer)
        McpStdioClient(transport).initialize()
        assertTrue(
            "应发送 notifications/initialized",
            transport.written.any { it.contains("notifications/initialized") && !it.contains("\"id\"") },
        )
    }

    @Test
    fun `tools_list 解析工具声明`() {
        val client = McpStdioClient(FakeTransport(::echoServer))
        client.initialize()
        val tools = client.listTools()
        assertEquals(1, tools.size)
        assertEquals("echo", tools[0].name)
        assertEquals("回显", tools[0].description)
        assertTrue(tools[0].inputSchemaJson.contains("object"))
    }

    @Test
    fun `tools_call 返回文本`() {
        val client = McpStdioClient(FakeTransport(::echoServer))
        client.initialize()
        val result = client.callTool("echo", buildJsonObject { put("x", "1") })
        assertEquals("pong", result.text)
        assertTrue(!result.isError)
    }

    @Test
    fun `错误响应转 McpException 且带方法名`() {
        val client = McpStdioClient(
            FakeTransport(server = { line ->
                val id = idOf(line)
                """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"方法不存在"}}"""
            }),
        )
        val error = runCatching { client.initialize() }.exceptionOrNull()
        assertTrue(error is McpException)
        assertTrue(error!!.message!!.contains("方法不存在"))
        assertTrue(error.message!!.contains("initialize"))
    }

    @Test
    fun `id 不匹配的响应被忽略直到超时`() {
        val client = McpStdioClient(
            FakeTransport(server = { """{"jsonrpc":"2.0","id":999,"result":{}}""" }),
            readTimeoutMs = 300,
        )
        val error = runCatching { client.initialize() }.exceptionOrNull()
        assertTrue(error is McpException)
        assertTrue(error!!.message!!.contains("超时"))
    }

    @Test
    fun `进程不存活时立即报错`() {
        val client = McpStdioClient(FakeTransport(server = ::echoServer, alive = false))
        val error = runCatching { client.initialize() }.exceptionOrNull()
        assertTrue(error is McpException)
        assertTrue(error!!.message!!.contains("未运行"))
    }

    @Test
    fun `请求 id 自增且 payload 是合法 JSON-RPC`() {
        val transport = FakeTransport(::echoServer)
        val client = McpStdioClient(transport)
        client.initialize()
        client.listTools()
        assertEquals(2, transport.written.count { it.contains("\"method\"") && it.contains("\"id\"") })
        assertTrue(transport.written.first().contains("\"jsonrpc\":\"2.0\""))
        assertTrue(transport.written.first().contains("\"params\""))
    }

    @Test
    fun `spawner 返回 null 时 ProcessStdioTransport 抛错`() {
        val error = runCatching {
            ProcessStdioTransport(ProotSpawner { _, _, _ -> null }, "npx", emptyList())
        }.exceptionOrNull()
        assertTrue(error is McpException)
        assertTrue(error!!.message!!.contains("无法启动"))
    }
}

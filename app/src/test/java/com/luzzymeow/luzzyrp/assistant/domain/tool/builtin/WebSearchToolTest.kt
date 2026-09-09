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

/** `web_search` 工具单测（PLAN §12.2：提供方选择、未配置提示、结果渲染）。 */
class WebSearchToolTest {

    private class FakeProvider(
        override val id: String,
        override val displayName: String = id,
        private val configured: Boolean = true,
        private val results: List<SearchResult> = listOf(SearchResult("标题", "https://a.b", "摘要")),
        private val fail: Boolean = false,
    ) : SearchProvider {
        var lastLimit: Int = 0
        override fun isConfigured() = configured
        override suspend fun search(query: String, maxResults: Int): List<SearchResult> {
            lastLimit = maxResults
            if (fail) throw SearchException("boom")
            return results
        }
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

    private fun args(vararg pairs: Pair<String, String>) = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    @Test
    fun `工具分级为 T0 且参数含 query`() {
        val tool = WebSearchTool({ listOf(FakeProvider("x")) })
        assertEquals(ToolTier.T0_READ, tool.tier)
        assertTrue(tool.parameters.toString().contains("query"))
    }

    @Test
    fun `默认提供方渲染结果`() = runBlocking {
        val tool = WebSearchTool({ listOf(FakeProvider("duckduckgo")) })
        val result = tool.execute(args("query" to "kotlin"), FakeCtx())
        val text = (result as ToolResult.Ok).text
        assertTrue(text.contains("标题"))
        assertTrue(text.contains("https://a.b"))
        assertTrue(text.contains("摘要"))
    }

    @Test
    fun `max_results 被夹到上限`() = runBlocking {
        val provider = FakeProvider("x")
        val tool = WebSearchTool({ listOf(provider) })
        tool.execute(args("query" to "q", "max_results" to "99"), FakeCtx())
        assertEquals(WebSearchTool.MAX_LIMIT, provider.lastLimit)
    }

    @Test
    fun `未配置任何提供方时给出可操作提示`() = runBlocking {
        val tool = WebSearchTool({ emptyList() })
        val error = tool.execute(args("query" to "q"), FakeCtx()) as ToolResult.Error
        assertTrue(error.message.contains("未配置搜索提供方"))
        assertTrue(error.message.contains("DuckDuckGo"))
    }

    @Test
    fun `指定引擎被尊重`() = runBlocking {
        val ddg = FakeProvider("duckduckgo", results = listOf(SearchResult("DDG", "https://ddg", "s")))
        val sx = FakeProvider("searxng", results = listOf(SearchResult("SX", "https://sx", "s")))
        val tool = WebSearchTool({ listOf(ddg, sx) })
        val text = (tool.execute(args("query" to "q", "engine" to "searxng"), FakeCtx()) as ToolResult.Ok).text
        assertTrue(text.contains("SX"))
    }

    @Test
    fun `未知引擎报错并列出可选`() = runBlocking {
        val tool = WebSearchTool({ listOf(FakeProvider("duckduckgo")) })
        val error = tool.execute(args("query" to "q", "engine" to "nope"), FakeCtx()) as ToolResult.Error
        assertTrue(error.message.contains("未知搜索提供方"))
        assertTrue(error.message.contains("duckduckgo"))
    }

    @Test
    fun `默认提供方未配置完整时明确提示`() = runBlocking {
        val tool = WebSearchTool(
            { listOf(FakeProvider("searxng", configured = false)) },
            defaultProviderIdProvider = { "searxng" },
        )
        val error = tool.execute(args("query" to "q"), FakeCtx()) as ToolResult.Error
        assertTrue(error.message.contains("尚未配置完整"))
    }

    @Test
    fun `提供方抛异常转 Error 且标记可重试`() = runBlocking {
        val tool = WebSearchTool({ listOf(FakeProvider("x", fail = true)) })
        val error = tool.execute(args("query" to "q"), FakeCtx()) as ToolResult.Error
        assertTrue(error.message.contains("搜索失败"))
        assertTrue(error.retryable)
    }

    @Test
    fun `空结果给出友好提示`() = runBlocking {
        val tool = WebSearchTool({ listOf(FakeProvider("x", results = emptyList())) })
        val text = (tool.execute(args("query" to "q"), FakeCtx()) as ToolResult.Ok).text
        assertTrue(text.contains("没有找到结果"))
    }
}

package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 一条搜索结果（domain 最小视图）。 */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * 搜索提供方端口（PLAN §12.2：内置无 Key 引擎 + 可选自带 Key）。
 *
 * 实现放在 runtime 层（网络访问）；本层只声明契约，便于单测与后续增删引擎。
 * 实现**不得抛异常**（失败返回空列表或抛 [SearchException] 由工具层转提示）。
 */
interface SearchProvider {
    /** 提供方 id（`duckduckgo` / `searxng` / `tavily` / `brave` …）。 */
    val id: String

    /** 展示名（错误提示用）。 */
    val displayName: String

    /** 是否已配置可用（缺 Key / 缺实例地址时为 false）。 */
    fun isConfigured(): Boolean

    suspend fun search(query: String, maxResults: Int): List<SearchResult>
}

/** 搜索失败（工具层转成可操作提示，如「未配置提供方」）。 */
class SearchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * `web_search`（PLAN §12.2，T0 只读）。
 *
 * 提供方优先级：请求参数 `engine` > 默认提供方 > 第一个已配置的提供方。
 * **未配置任何提供方时明确报错**（引导用户去设置页配置），不静默返回空。
 */
class WebSearchTool(
    /** 提供方解析（每次调用时求值：配置可能刚被用户改过）。 */
    private val providersProvider: suspend () -> List<SearchProvider>,
    /** 默认提供方 id 解析（null = 用第一个已配置的）。 */
    private val defaultProviderIdProvider: suspend () -> String? = { null },
    /** 供 schema 展示的可选引擎 id（静态，避免每次构造都求值）。 */
    private val knownProviderIds: List<String> = listOf("duckduckgo", "searxng"),
) : Tool {

    override val name = "web_search"
    override val description =
        "搜索互联网，返回标题/链接/摘要。需要事实、最新信息或你不确定的内容时先搜索，" +
            "再用 web_fetch 打开最相关的结果。"
    override val tier = ToolTier.T0_READ
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "query" to Schema.string("检索词，尽量具体"),
            "max_results" to Schema.integer("返回条数，默认 5，上限 10"),
            "engine" to Schema.string("指定搜索提供方（可选）", enumValues = knownProviderIds),
        ),
        required = listOf("query"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val query = args.str("query") ?: return ToolResult.Error("缺少 query 参数")
        val limit = (args["max_results"]?.jsonPrimitive?.intOrNull ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val requested = args.str("engine")
        val providers = runCatching { providersProvider() }.getOrDefault(emptyList())
        val defaultProviderId = runCatching { defaultProviderIdProvider() }.getOrNull()

        val provider = when {
            requested != null -> providers.firstOrNull { it.id == requested }
                ?: return ToolResult.Error(
                    "未知搜索提供方：$requested（可选：${providers.joinToString(", ") { it.id }}）"
                )
            defaultProviderId != null -> providers.firstOrNull { it.id == defaultProviderId }
            else -> null
        } ?: providers.firstOrNull { it.isConfigured() }
        ?: return ToolResult.Error(
            "未配置搜索提供方。请在「设置」页选择搜索提供方（DuckDuckGo 无需配置；" +
                "SearXNG 需填实例地址）后重试。"
        )

        if (!provider.isConfigured()) {
            return ToolResult.Error("搜索提供方「${provider.displayName}」尚未配置完整，请检查设置。")
        }

        return try {
            val results = provider.search(query, limit)
            if (results.isEmpty()) {
                ToolResult.Ok("（没有找到结果，试试换个说法）")
            } else {
                ToolResult.Ok(
                    results.joinToString("\n") { "- ${it.title}\n  ${it.url}\n  ${it.snippet.take(200)}" }
                )
            }
        } catch (e: SearchException) {
            ToolResult.Error("搜索失败（${provider.displayName}）：${e.message}", retryable = true)
        } catch (e: Exception) {
            ToolResult.Error("搜索失败（${provider.displayName}）：${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 10
    }
}

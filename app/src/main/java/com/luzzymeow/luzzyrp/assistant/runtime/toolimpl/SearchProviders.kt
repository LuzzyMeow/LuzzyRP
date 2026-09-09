package com.luzzymeow.luzzyrp.assistant.runtime.toolimpl

import com.luzzymeow.luzzyrp.assistant.domain.llm.JsonLenient
import com.luzzymeow.luzzyrp.assistant.domain.tool.SsrfGuard
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.SearchException
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.SearchProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.SearchResult
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 搜索提供方实现（PLAN §12.2「内置无 Key 引擎 + 可选自带 Key」）。
 *
 * | id | 鉴权 | 说明 |
 * |----|------|------|
 * | `duckduckgo` | 无 | **默认**；DuckDuckGo Lite HTML 端点，尽力解析（可能被限流） |
 * | `searxng` | 无 | 用户填自建/公共实例地址（推荐：稳定、可自控） |
 * | `tavily` | API Key | `POST https://api.tavily.com/search` |
 * | `brave` | API Key | `GET https://api.search.brave.com/res/v1/web/search` |
 *
 * 所有实现都**不打印** query 之外的任何内容（Key 只进请求头）。
 */

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val FORM_MEDIA = "application/x-www-form-urlencoded".toMediaType()

private fun client(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .build()

private fun ua(): String =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36 LuzzyRP"

/** DuckDuckGo Lite（无 Key，默认提供方）。 */
class DuckDuckGoProvider(
    private val http: OkHttpClient = client(),
) : SearchProvider {

    override val id = "duckduckgo"
    override val displayName = "DuckDuckGo"
    override fun isConfigured() = true

    override suspend fun search(query: String, maxResults: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val body = "q=${URLEncoder.encode(query, "UTF-8")}&kl=wt-wt"
            .toRequestBody(FORM_MEDIA)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", ua())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()
        val html = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw SearchException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        } catch (e: SearchException) {
            throw e
        } catch (e: IOException) {
            throw SearchException("网络错误：${e.message ?: e.javaClass.simpleName}", e)
        }
        DuckDuckGoLiteParser.parse(html).take(maxResults)
    }

    companion object {
        const val ENDPOINT = "https://lite.duckduckgo.com/lite/"
    }
}

/** SearXNG（无 Key，用户提供实例地址；`format=json`）。 */
class SearXngProvider(
    private val baseUrl: String,
    private val http: OkHttpClient = client(),
) : SearchProvider {

    override val id = "searxng"
    override val displayName = "SearXNG"
    override fun isConfigured() = baseUrl.isNotBlank()

    override suspend fun search(query: String, maxResults: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val endpoint = baseUrl.trimEnd('/') + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json"
        SsrfGuard.reasonOf(endpoint)?.let { throw SearchException(it) }
        val request = Request.Builder().url(endpoint).header("User-Agent", ua()).get().build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SearchException("HTTP ${response.code}（实例可能未开启 JSON 格式）")
            response.body?.string().orEmpty()
        }
        val root = runCatching { JsonLenient.json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw SearchException("实例返回的不是 JSON（请确认已开启 format=json）")
        (root["results"] as? JsonArray).orEmpty().take(maxResults).mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SearchResult(
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                url = url,
                snippet = obj["content"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }
}

/**
 * DuckDuckGo Lite HTML 解析（纯函数，可单测）。
 *
 * 结构：结果行是 `<a rel="nofollow" href="…">标题</a>`，摘要紧跟其后的一段文本。
 * 解析策略**宽容**：抽不到就返回空（上层提示「没有找到结果」），绝不抛异常。
 */
object DuckDuckGoLiteParser {

    private val LINK = Regex(
        "<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SNIPPET = Regex(
        "class=\"result-snippet\"[^>]*>(.*?)</td>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(html: String): List<SearchResult> {
        if (html.isBlank()) return emptyList()
        val snippets = SNIPPET.findAll(html).map { strip(it.groupValues[1]) }.toList()
        val out = mutableListOf<SearchResult>()
        var index = 0
        LINK.findAll(html).forEach { match ->
            val url = match.groupValues[1]
            val title = strip(match.groupValues[2])
            if (url.contains("duckduckgo.com")) return@forEach // 跳过引擎自身链接
            if (title.isBlank()) return@forEach
            out += SearchResult(
                title = title,
                url = url,
                snippet = snippets.getOrNull(index) ?: "",
            )
            index++
        }
        return out
    }

    private fun strip(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}

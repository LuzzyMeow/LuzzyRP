package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.SsrfGuard
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `web_fetch`（PLAN §12.2）：抓取网页正文。
 *
 * **安全（硬性要求 11）**：先过 [SsrfGuard]——协议白名单 + DNS 解析层拒绝私网/回环/
 * 链路本地/保留地址（防 SSRF）。30s 上限；正文提取后按字符上限截断（防爆上下文）。
 */
class WebFetchTool(
    private val client: OkHttpClient = defaultClient(),
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val resolver: SsrfGuard.Resolver = SsrfGuard.systemResolver,
) : Tool {

    override val name = "web_fetch"
    override val description = "抓取一个网页并提取正文文本。仅支持 http/https，禁止访问内网地址。"
    override val tier = ToolTier.T0_READ
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "url" to Schema.string("要抓取的 http/https 地址"),
            "extract_mode" to Schema.string("提取模式", enumValues = listOf("text", "raw")),
        ),
        required = listOf("url"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val url = args.str("url") ?: return ToolResult.Error("缺少 url 参数")
        if (!SsrfGuard.schemeAllowed(url)) return ToolResult.Error("仅支持 http/https 地址")
        SsrfGuard.reasonOf(url, resolver)?.let { return ToolResult.Error(it) }

        val mode = args.str("extract_mode") ?: "text"
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ToolResult.Error(
                            "HTTP ${response.code} ${response.message}",
                            retryable = response.code >= 500 || response.code == 429,
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val contentType = response.header("Content-Type").orEmpty()
                    val text = when {
                        mode == "raw" -> body
                        contentType.contains("text/html", true) || body.trimStart().startsWith("<") ->
                            HtmlText.extract(body)
                        else -> body
                    }
                    val clipped = if (text.length > maxChars) {
                        text.take(maxChars) + "\n\n…（已截断，原文 ${text.length} 字符）"
                    } else text
                    ToolResult.Ok(clipped.ifBlank { "（正文为空）" })
                }
            }
        } catch (e: IOException) {
            ToolResult.Error("抓取失败：${e.message ?: e.javaClass.simpleName}", retryable = true)
        } catch (e: Exception) {
            ToolResult.Error("抓取失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 20_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36 LuzzyRP"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

/**
 * 极简 HTML → 正文提取（零依赖）。
 *
 * 只做三件事：去掉 script/style/注释、把块级标签换成换行、合并多余空白并反转义常见实体。
 * 目标是「给模型读得懂」，不追求完美正文抽取。
 */
object HtmlText {

    private val SCRIPT_STYLE = Regex("(?is)<(script|style|noscript|template)[^>]*>.*?</\\1>")
    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val BLOCK_TAGS = Regex(
        "(?i)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer|nav|blockquote|pre)[^>]*>"
    )
    private val ALL_TAGS = Regex("(?s)<[^>]+>")
    private val BLANK_LINES = Regex("\n{3,}")
    private val INLINE_SPACES = Regex("[ \t\u00A0]{2,}")

    fun extract(html: String): String {
        var s = html
        s = COMMENT.replace(s, "")
        s = SCRIPT_STYLE.replace(s, " ")
        s = BLOCK_TAGS.replace(s, "\n")
        s = ALL_TAGS.replace(s, "")
        s = unescape(s)
        s = INLINE_SPACES.replace(s, " ")
        s = s.lines().joinToString("\n") { it.trim() }
        s = BLANK_LINES.replace(s, "\n\n")
        return s.trim()
    }

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

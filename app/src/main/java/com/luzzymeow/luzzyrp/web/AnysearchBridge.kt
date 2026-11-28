package com.luzzymeow.luzzyrp.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors

@Keep
class AnysearchBridge(private val webView: WebView) {

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "AnysearchBridge").apply { isDaemon = true }
    }

    @Keep
    @JavascriptInterface
    fun request(action: String, payloadJson: String, apiKey: String, callbackId: String) {
        if (!CALLBACK_ID_PATTERN.matches(callbackId)) return

        webView.post {
            if (!webView.url.orEmpty().startsWith("file://")) {
                dispatch(
                    callbackId,
                    JSONObject()
                        .put("ok", false)
                        .put("status", 0)
                        .put("requestId", "")
                        .put("error", "untrusted_page")
                )
                return@post
            }
            executor.execute {
                val result = try {
                    executeRequest(action, payloadJson, apiKey)
                } catch (error: Exception) {
                    errorResult(error)
                }
                dispatch(callbackId, result)
            }
        }
    }

    private fun executeRequest(action: String, payloadJson: String, apiKey: String): JSONObject {
        val request = buildRequest(action, payloadJson)
        val connection = request.url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = request.timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Anysearch-Client", "LuzzyRP/2.0.6.2")
            apiKey.trim()
                .replace("\r", "")
                .replace("\n", "")
                .take(512)
                .takeIf { it.isNotEmpty() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.outputStream.use { stream ->
                stream.write(request.body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val requestId = connection.getHeaderField("X-Request-ID").orEmpty()
            if (status !in 200..299) {
                return JSONObject()
                    .put("ok", false)
                    .put("status", status)
                    .put("requestId", requestId)
            }

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return JSONObject()
                .put("ok", true)
                .put("status", status)
                .put("requestId", requestId)
                .put("data", JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequest(action: String, payloadJson: String): RequestSpec {
        val payload = JSONObject(payloadJson)
        return when (action) {
            "search" -> {
                val query = payload.optString("query").trim()
                require(query.isNotEmpty()) { "query_required" }
                val maxResults = payload.optInt("max_results", 5).coerceIn(1, 10)
                RequestSpec(
                    url = URL(SEARCH_ENDPOINT),
                    body = JSONObject()
                        .put("query", query)
                        .put("max_results", maxResults)
                        .put("format", "markdown"),
                    timeoutMs = SEARCH_TIMEOUT_MS
                )
            }

            "extract" -> {
                val rawUrl = payload.optString("url").trim()
                val uri = runCatching { URI(rawUrl) }.getOrNull()
                require(uri != null && uri.scheme in HTTP_SCHEMES && !uri.host.isNullOrBlank()) {
                    "invalid_url"
                }
                RequestSpec(
                    url = URL(EXTRACT_ENDPOINT),
                    body = JSONObject().put("url", rawUrl),
                    timeoutMs = EXTRACT_TIMEOUT_MS
                )
            }

            else -> throw IllegalArgumentException("unsupported_action")
        }
    }

    private fun errorResult(error: Exception): JSONObject {
        val code = when (error) {
            is SocketTimeoutException -> "timeout"
            is UnknownHostException -> "network"
            is IllegalArgumentException -> error.message ?: "invalid_request"
            else -> "request_failed"
        }
        return JSONObject()
            .put("ok", false)
            .put("status", 0)
            .put("requestId", "")
            .put("error", code)
    }

    private fun dispatch(callbackId: String, result: JSONObject) {
        val script = "window.__luzzyAnysearchResolve(" +
            "${JSONObject.quote(callbackId)}," +
            "${JSONObject.quote(result.toString())});"
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private data class RequestSpec(
        val url: URL,
        val body: JSONObject,
        val timeoutMs: Int
    )

    private companion object {
        const val SEARCH_ENDPOINT = "https://api.anysearch.com/v1/search"
        const val EXTRACT_ENDPOINT = "https://api.anysearch.com/v1/extract"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val SEARCH_TIMEOUT_MS = 30_000
        const val EXTRACT_TIMEOUT_MS = 60_000
        val HTTP_SCHEMES = setOf("http", "https")
        val CALLBACK_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}

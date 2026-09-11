package com.luzzymeow.luzzyrp.chat.llm

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 极简 SSE 测试服务器（JDK `ServerSocket`，**不引入任何测试依赖**）。
 *
 * 用途：在 JVM 单测里跑真实的 OkHttp 栈（连接 / 分帧 / 超时 / 取消 / 重试），
 * 让「真流式」在**没有设备**的情况下也能被测到。
 *
 * 行为：按 [scripts] 顺序逐个响应；每个连接读完请求（含 `Content-Length` 请求体）
 * 后写响应并关闭连接（`Connection: close`，便于重试用例断言请求次数）。
 *
 * v2.0 改编：不再固定 `/v1/chat/completions` 路径——三协议现在按各自规则使用
 * baseUrl（OpenAI/Anthropic 原样 POST，Gemini 自己拼 `/v1beta/...`），
 * 因此这里暴露 [origin] 与 [url] 两个入口由用例自行拼。
 *
 * 自 v1.5.0 的助手模块（commit 0392b662 前）恢复并改编。
 */
class RawSseServer(private val scripts: List<Script>) : Closeable {

    /**
     * 一次响应脚本。
     *
     * @param keepOpenMs >0 时写完响应体后保持连接（空闲超时 / 取消用例），否则立即关闭。
     * @param extraHeaders 追加响应头（每行含 CRLF）。
     */
    data class Script(
        val status: Int = 200,
        val body: String = "",
        val contentType: String = "text/event-stream",
        val keepOpenMs: Long = 0L,
        val extraHeaders: String = "",
    )

    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val pending = ArrayDeque(scripts)

    /** 收到的原始请求（请求头 + 请求体），用于断言次数与请求内容。 */
    val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** `http://127.0.0.1:<port>`（无路径）。 */
    val origin: String get() = "http://127.0.0.1:${serverSocket.localPort}"

    /** 便捷：拼一个路径（OpenAI 用例常用 `/v1/chat/completions`）。 */
    fun url(path: String = ""): String = origin + path

    private val worker = Thread({ acceptLoop() }, "raw-sse-server").apply {
        isDaemon = true
        start()
    }

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                return
            }
            try {
                serve(socket)
            } catch (e: IOException) {
                // 客户端取消 / 提前关闭属正常路径
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 5_000
        // 必须按**字节**读请求体：Content-Length 是字节数，按字符读会在 UTF-8 请求体上多读而阻塞
        val input = socket.getInputStream()
        val headBytes = java.io.ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) break
            headBytes.write(next)
            val bytes = headBytes.toByteArray()
            val size = bytes.size
            if (size >= 4 &&
                bytes[size - 4] == '\r'.code.toByte() && bytes[size - 3] == '\n'.code.toByte() &&
                bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()
            ) {
                break
            }
        }
        val headerText = String(headBytes.toByteArray(), Charsets.ISO_8859_1)
        val contentLength = Regex("(?i)content-length:\\s*(\\d+)")
            .find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val bodyBytes = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(bodyBytes, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        requests += headerText + String(bodyBytes, 0, read, Charsets.UTF_8)

        val script = synchronized(pending) { pending.removeFirstOrNull() } ?: Script(status = 500, body = "no script")
        val reason = when (script.status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val out = socket.getOutputStream()
        out.write(
            buildString {
                append("HTTP/1.1 ").append(script.status).append(' ').append(reason).append("\r\n")
                append("Content-Type: ").append(script.contentType).append("\r\n")
                append("Connection: close\r\n")
                append(script.extraHeaders)
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)
        )
        if (script.body.isNotEmpty()) out.write(script.body.toByteArray(Charsets.UTF_8))
        out.flush()
        if (script.keepOpenMs > 0) Thread.sleep(script.keepOpenMs)
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    companion object {
        /** 组装 SSE 响应体：每帧 `data: <json>\n\n`，末尾补 `[DONE]`。 */
        fun sse(vararg frames: String): String =
            frames.joinToString(separator = "") { "data: $it\n\n" } + "data: [DONE]\n\n"

        /** 组装 SSE 响应体：**不补** `[DONE]`（测 Anthropic/Gemini 那种直接关流的收尾）。 */
        fun sseNoDone(vararg frames: String): String =
            frames.joinToString(separator = "") { "data: $it\n\n" }
    }
}

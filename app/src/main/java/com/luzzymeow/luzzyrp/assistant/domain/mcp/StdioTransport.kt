package com.luzzymeow.luzzyrp.assistant.domain.mcp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * stdio 传输抽象（让 [McpStdioClient] 的 JSON-RPC 逻辑可单测）。
 *
 * 生产实现是 [ProcessStdioTransport]（proot 沙盒内进程）；单测可用内存假实现。
 */
interface StdioTransport {
    /** 写一行（自动补换行）。 */
    fun writeLine(line: String)

    /** 读一行；超时返回 null。 */
    fun readLine(timeoutMs: Long): String?

    fun isAlive(): Boolean

    fun close()
}

/**
 * 基于子进程的 stdio 传输（`npx` / `uvx` 等 MCP 服务器）。
 *
 * stdout 由后台守护线程逐行读入队列（避免阻塞写侧）；stderr 单独抽干防止管道写满。
 */
class ProcessStdioTransport(
    spawner: ProotSpawner,
    command: String,
    args: List<String>,
    env: Map<String, String> = emptyMap(),
) : StdioTransport {

    private val process: Process = spawner.spawn(command, args, env)
        ?: throw McpException("无法启动 MCP stdio 进程（沙盒未就绪或命令不存在）")

    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
    private val lines = LinkedBlockingQueue<String>()

    init {
        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        Thread({
            runCatching {
                reader.forEachLine { line -> if (line.isNotBlank()) lines.put(line) }
            }
        }, "mcp-stdio-stdout").apply { isDaemon = true; start() }
        Thread({
            runCatching { process.errorStream.bufferedReader().forEachLine { /* 丢弃日志 */ } }
        }, "mcp-stdio-stderr").apply { isDaemon = true; start() }
    }

    override fun writeLine(line: String) {
        try {
            writer.write(line)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            throw McpException("写入 MCP 进程失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun readLine(timeoutMs: Long): String? =
        try {
            lines.poll(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }

    override fun isAlive(): Boolean = process.isAlive

    override fun close() {
        runCatching { writer.close() }
        runCatching { process.destroyForcibly() }
        lines.clear()
    }
}

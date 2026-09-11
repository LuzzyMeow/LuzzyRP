package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmError
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.ToolCallAccumulator
import com.luzzymeow.luzzyrp.chat.llm.defaultRoutingTransport
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 事件出口契约（宿主实现）。
 *
 * [jobId] 与 [eventJson] 分开传递：JS 侧签名为 `onEvent(jobId, eventJsonString)`，
 * 宿主负责拼成 `evaluateJavascript` 调用串（见 [ChatJsCall.onEvent]）。
 */
fun interface ChatEventSink {
    fun onEvent(jobId: String, eventJson: String)
}

/**
 * 原生聊天传输的**任务管理器**：`chatStart` / `chatAbort` / `chatCapabilities` 的实现体。
 *
 * 线程模型（对应桥接契约的硬要求）：
 * - 三个入口都在 WebView 的 JavaBridge 线程被调用；[start] **立即返回**，
 *   真正的传输跑在 [scope]（SupervisorJob + IO）上；
 * - 事件一律通过 [sink] 交出去，由宿主（[com.luzzymeow.luzzyrp.web.LuzzyBridge]）
 *   保证在 UI 线程调用 `evaluateJavascript`；
 * - 任何异常都在本类内部消化：**绝不跨越 JS 边界抛出去**（抛出去 = 整个 App 白屏）。
 *
 * 事件节流：正文/思考增量按 [flushIntervalMs]（默认 120ms，与 JS 渲染节流同档）合并，
 * 而不是每帧一次 `evaluateJavascript`；流结束时强制冲刷一次。
 */
class ChatJobs(
    /** 事件出口；返回 null 表示宿主还没接上 WebView（此时静默丢弃，不报错）。 */
    private val sink: () -> ChatEventSink?,
    private val transport: LlmTransport = defaultRoutingTransport(),
    private val log: (String) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val flushIntervalMs: Long = FLUSH_INTERVAL_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private val jobs = ConcurrentHashMap<String, Job>()

    /** 当前在跑的任务数（诊断 / 单测用）。 */
    fun activeCount(): Int = jobs.count { it.value.isActive }

    /**
     * 启动一次传输。返回 jobId（成功）或空串（参数非法 / 原生不可用）。
     *
     * **必须立即返回**：这里只做解析与协程登记，不做任何 IO。
     */
    fun start(planJson: String): String {
        val parsed = try {
            ChatPlan.parse(planJson)
        } catch (e: Throwable) {
            log("plan 解析异常：${e.javaClass.simpleName}")
            return ""
        }
        val plan = when (parsed) {
            is ChatPlan.Result.Ok -> parsed
            is ChatPlan.Result.Invalid -> {
                log("plan 非法：${parsed.reason}")
                return ""
            }
        }

        // 同 id 重入（JS 侧重试）→ 换掉旧任务，避免两条流同时往同一个 job 写事件
        jobs.remove(plan.jobId)?.cancel()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            runJob(plan.jobId, plan.request)
        }
        jobs[plan.jobId] = job
        job.invokeOnCompletion { jobs.remove(plan.jobId, job) }
        job.start()
        return plan.jobId
    }

    /** 中止指定任务；返回是否真的中止了（不存在 / 已结束都返回 false）。 */
    fun abort(jobId: String): Boolean {
        val job = jobs.remove(jobId) ?: return false
        if (!job.isActive) return false
        job.cancel()
        return true
    }

    /** 停止全部任务（WebView 销毁 / Activity 结束）。 */
    fun shutdown() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
        log("原生聊天任务已全部停止")
    }

    // ---------- 内部实现 ----------

    private suspend fun runJob(jobId: String, request: LlmRequest) {
        val emitTo: (String) -> Unit = { emit(jobId, it) }
        val batcher = DeltaBatcher(emitTo, nowMillis, flushIntervalMs)
        val accumulator = ToolCallAccumulator()
        var finishReason: String? = null
        var failure: LlmError? = null

        try {
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        delay(flushIntervalMs)
                        batcher.flushIfDue()
                    }
                }
                try {
                    transport.stream(request).collect { delta ->
                        delta.error?.let { failure = it }
                        accumulator.accept(delta.toolCalls)
                        batcher.append(delta.content, delta.reasoning, accumulator)
                        batcher.flushIfDue()
                        delta.rawUsage?.let { emit(jobId, usageEventJson(it)) }
                        delta.finishReason?.let { finishReason = it }
                    }
                } finally {
                    ticker.cancel()
                }
            }
        } catch (e: CancellationException) {
            // 主动中止：冲刷已收到的增量后结束，不发 done/error（JS 侧知道自己点过停止）
            batcher.flush()
            log("任务 $jobId 已中止")
            throw e
        } catch (e: Throwable) {
            // 传输层承诺不抛异常；走到这里说明是没预料到的内部错误——转错误事件而不是崩溃
            failure = LlmError("原生传输内部错误（${e.javaClass.simpleName}）", retryable = false)
        }

        batcher.flush()
        val error = failure
        if (error != null) {
            emit(jobId, errorEventJson(error))
        } else {
            emit(jobId, doneEventJson(finishReason ?: "stop"))
        }
    }

    private fun emit(jobId: String, eventJson: String) {
        val target = try {
            sink()
        } catch (e: Throwable) {
            null
        } ?: return
        try {
            target.onEvent(jobId, eventJson)
        } catch (e: Throwable) {
            log("事件投递失败（${e.javaClass.simpleName}）")
        }
    }

    /** 注入测试用：当前任务快照。 */
    internal fun isActive(jobId: String): Boolean = jobs[jobId]?.isActive == true

    companion object {
        /** 增量合并节流（与 JS `STREAM_RENDER_INTERVAL` 同档：120ms）。 */
        const val FLUSH_INTERVAL_MS: Long = 120L

        /** 能力探测返回值（`chatCapabilities`）。 */
        fun capabilitiesJson(available: Boolean, reason: String? = null): String = buildJsonObject {
            put("available", available)
            reason?.let { put("reason", it) }
            if (available) {
                put(
                    "protocols",
                    JsonArray(
                        com.luzzymeow.luzzyrp.chat.llm.RoutingTransport.SUPPORTED_PROTOCOLS
                            .map { JsonPrimitive(it) }
                    ),
                )
            }
        }.toString()
    }
}

// ---------- 事件 JSON（四型契约；键序固定，JS 侧按字段取值） ----------

/** `{"type":"delta","content":…,"reasoning":…,"toolCalls":[…]}`；toolCalls 仅在非空时出现。 */
internal fun deltaEventJson(content: String, reasoning: String, toolCalls: JsonArray?): String =
    buildJsonObject {
        put("type", "delta")
        put("content", content)
        put("reasoning", reasoning)
        toolCalls?.takeIf { it.isNotEmpty() }?.let { put("toolCalls", it) }
    }.toString()

/** `{"type":"usage","usage":<供应商原始对象>}`（原样透传，JS 的 normalizeApiUsage 认得更宽）。 */
internal fun usageEventJson(usage: JsonObject): String = buildJsonObject {
    put("type", "usage")
    put("usage", usage)
}.toString()

/** `{"type":"done","finishReason":…}`。 */
internal fun doneEventJson(finishReason: String): String = buildJsonObject {
    put("type", "done")
    put("finishReason", finishReason)
}.toString()

/** `{"type":"error","message":…,"retryable":…}`。 */
internal fun errorEventJson(error: LlmError): String = buildJsonObject {
    put("type", "error")
    put("message", error.message)
    put("retryable", error.retryable)
}.toString()

/**
 * 增量合并器：把高频 SSE 帧攒成 ~120ms 一批的 `delta` 事件。
 *
 * 线程安全（ticker 协程与收集循环会并发调用）；[flushIfDue] 到点才发，
 * [flush] 强制发（任务收尾）。
 */
internal class DeltaBatcher(
    private val emit: (String) -> Unit,
    private val nowMillis: () -> Long,
    private val intervalMs: Long,
) {
    private val lock = Any()
    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private var pendingToolCalls: JsonArray? = null
    private var lastFlushAt: Long = 0L

    fun append(contentChunk: String?, reasoningChunk: String?, accumulator: ToolCallAccumulator) {
        val toolCalls = if (accumulator.isEmpty()) null else accumulator.snapshotJson()
        synchronized(lock) {
            contentChunk?.let { content.append(it) }
            reasoningChunk?.let { reasoning.append(it) }
            if (toolCalls != null) pendingToolCalls = toolCalls
        }
    }

    /** 距上次发出已满 [intervalMs] 才发；返回本次是否发出。 */
    fun flushIfDue(): Boolean {
        val now = nowMillis()
        synchronized(lock) {
            if (now - lastFlushAt < intervalMs) return false
            if (!hasPending()) return false
            lastFlushAt = now
            dispatch()
            return true
        }
    }

    /** 强制冲刷（任务收尾）。 */
    fun flush() {
        synchronized(lock) {
            if (!hasPending()) return
            lastFlushAt = nowMillis()
            dispatch()
        }
    }

    private fun hasPending(): Boolean =
        content.isNotEmpty() || reasoning.isNotEmpty() || pendingToolCalls != null

    private fun dispatch() {
        val payload = deltaEventJson(content.toString(), reasoning.toString(), pendingToolCalls)
        content.setLength(0)
        reasoning.setLength(0)
        emit(payload)
    }
}

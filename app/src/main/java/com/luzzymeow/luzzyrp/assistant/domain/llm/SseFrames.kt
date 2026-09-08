package com.luzzymeow.luzzyrp.assistant.domain.llm

/**
 * SSE 分帧（PLAN §5.3，纯 Kotlin、无 Android / OkHttp 依赖，可单测）。
 *
 * 协议要点（W3C EventSource 子集，OpenAI 流式响应使用的部分）：
 * - 帧（event）之间以**空行**分隔：`\n\n` / `\r\n\r\n` / `\r\r`；
 * - 载荷由 `data:` 行给出，同一帧内多行 `data:` 以 `\n` 连接；
 * - 以 `:` 开头的行为注释（keep-alive），`event:` / `id:` / `retry:` 一律忽略；
 * - 帧可能跨 chunk 边界，[Parser] 负责缓存半帧。
 *
 * 本对象只做**分帧**，不做 JSON 解析（JSON 归 [OpenAiWire]）；`[DONE]` 哨兵原样返回，
 * 由调用方判定流结束。
 */
object SseFrames {

    /** OpenAI 流结束哨兵。 */
    const val DONE = "[DONE]"

    /**
     * 解析一个 chunk 中**已完整成帧**的 data 载荷（不完整尾部丢弃——流式场景请用 [Parser]）。
     *
     * 单测入口；空帧、纯注释帧不产出。
     */
    fun parse(chunk: String): List<String> {
        val (frames, _) = splitFrames(chunk)
        return frames.mapNotNull(::framePayload).filter { it.isNotEmpty() }
    }

    /**
     * 把缓冲文本切成「完整帧列表 + 残余半帧」。
     *
     * 换行统一归一化为 `\n`（`\r\n` 与裸 `\r` 都折叠），因此三种分隔符只需处理 `\n\n`。
     */
    fun splitFrames(buffer: String): Pair<List<String>, String> {
        val normalized = normalize(buffer)
        if (normalized.isEmpty()) return emptyList<String>() to ""
        val frames = ArrayList<String>()
        var start = 0
        while (true) {
            val end = normalized.indexOf("\n\n", start)
            if (end < 0) break
            frames += normalized.substring(start, end)
            start = end + 2
        }
        return frames to normalized.substring(start)
    }

    /** 单个完整帧 → data 载荷；无 data 字段返回 null。 */
    fun framePayload(frame: String): String? {
        if (frame.isEmpty()) return null
        val data = StringBuilder()
        var hasData = false
        for (line in frame.split('\n')) {
            if (line.isEmpty() || line.startsWith(":")) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            var value = if (colon < 0) "" else line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
            if (field == "data") {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return if (hasData) data.toString() else null
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * 跨 chunk 的增量分帧器：喂入任意切分的文本，吐出完整帧的 data 载荷。
     *
     * [SseClient] 逐行喂入（`line + "\n"`），空行即触发成帧——这样天然避免
     * UTF-8 多字节字符被 chunk 边界截断的问题。
     */
    class Parser {
        private val buffer = StringBuilder()

        fun accept(chunk: String): List<String> {
            if (chunk.isEmpty()) return emptyList()
            buffer.append(chunk)
            val (frames, rest) = splitFrames(buffer.toString())
            buffer.setLength(0)
            buffer.append(rest)
            return frames.mapNotNull(::framePayload).filter { it.isNotEmpty() }
        }

        /** 流结束时冲刷残余（服务端未以空行收尾的容错）。 */
        fun finish(): List<String> {
            val rest = buffer.toString()
            buffer.setLength(0)
            if (rest.isBlank()) return emptyList()
            return listOfNotNull(framePayload(rest)).filter { it.isNotEmpty() }
        }

        /** 当前缓存的半帧（诊断用）。 */
        fun pending(): String = buffer.toString()
    }
}

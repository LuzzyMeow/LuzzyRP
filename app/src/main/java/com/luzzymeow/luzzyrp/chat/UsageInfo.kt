package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一轮生成的用量与耗时（消息脚注的数据源）。
 *
 * 对齐 rikkahub `ChatMessageNerdLine`（`ui/components/message/ChatMessageNerdLine.kt`）的语义：
 * 把「这轮请求花了多少 token / 多久」变成用户可见的事实，而不是只躺在日志里。
 */
data class UsageInfo(
    val input: Int,
    val output: Int,
    /** 命中前缀缓存的输入 token（取不到为 null）。 */
    val cached: Int? = null,
) {
    companion object {
        /**
         * 从传输层增量里取用量 + 缓存命中数。
         *
         * 缓存字段按供应商差异探测（取不到就是 null，不猜）：
         * DeepSeek `prompt_cache_hit_tokens`；OpenAI `prompt_tokens_details.cached_tokens`。
         */
        fun from(delta: LlmDelta): UsageInfo? {
            val usage = delta.usage ?: return null
            val raw = delta.rawUsage
            return UsageInfo(
                input = usage.input,
                output = usage.output,
                cached = raw?.cachedTokens(),
            )
        }

        private fun JsonObject.cachedTokens(): Int? {
            intOrNull("prompt_cache_hit_tokens")?.let { return it }
            val details = this["prompt_tokens_details"] as? JsonObject ?: return null
            return details.intOrNull("cached_tokens")
        }

        private fun JsonObject.intOrNull(key: String): Int? =
            runCatching { this[key]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
    }
}

/** 脚注文本与截断判定（纯函数，可单测）。 */
object UsageFormat {

    /** 被输出上限截断的结束原因（OpenAI `length`、Anthropic `max_tokens`、Gemini `MAX_TOKENS` 归一后同）。 */
    private val TRUNCATED = setOf("length", "max_tokens")

    fun isTruncated(finishReason: String?): Boolean = finishReason?.lowercase() in TRUNCATED

    /** 截断提示文案（脚注里以告警色追加）。 */
    const val TruncationNotice: String = "已截断（达到输出上限）"

    /**
     * 脚注文本：`输入 1,234（缓存 1,100） · 输出 456 · 12.3s · 37 tok/s`。
     * 无用量且无耗时时返回 null（整行不显示，不留空行）。
     */
    fun line(usage: UsageInfo?, elapsedMs: Long?): String? {
        val parts = mutableListOf<String>()
        if (usage != null) {
            val cached = usage.cached?.takeIf { it > 0 }?.let { "（缓存 ${grouped(it)}）" }.orEmpty()
            parts += "输入 ${grouped(usage.input)}$cached"
            parts += "输出 ${grouped(usage.output)}"
        }
        if (elapsedMs != null && elapsedMs > 0) {
            parts += "%.1fs".format(elapsedMs / 1000.0)
            val seconds = elapsedMs / 1000.0
            if (usage != null && usage.output > 0 && seconds > 0.05) {
                parts += "%.0f tok/s".format(usage.output / seconds)
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** 千分位（数字可读性：1234 → 1,234）。 */
    fun grouped(value: Int): String = "%,d".format(value)
}

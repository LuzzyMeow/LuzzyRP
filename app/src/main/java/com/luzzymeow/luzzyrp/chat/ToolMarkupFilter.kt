package com.luzzymeow.luzzyrp.chat

/**
 * 工具调用「写成文本」时的协议噪声过滤。
 *
 * **为什么需要**（2026-09-12 真机实测发现）：开启工具后，DeepSeek 除正常 `tool_calls` 之外
 * 还会把工具调用再用自家 DSML 文本标记复述一遍，这段**原始协议标记会作为 `content`
 * 流进回复正文**，用户看到的是：
 * ```
 * <| | DSML | | invoke name="world_info_lookup">
 * <| | DSML | | parameter name="query" string="true">钟楼 红苹果树 来历 种下</| | DSML | | parameter>
 * ```
 * 这是协议噪声、永远不是角色扮演内容，必须在**进入正文之前**滤掉。
 *
 * **放在引擎层而不是线格式层**：v2.0 的 wire fidelity 纪律要求 OpenAI 路径「逐字节原样转发」，
 * 不能改帧内容；而「哪些内容是给用户看的」正是引擎的职责。
 *
 * 两条来之不易的正确性约束：
 * ① **要删的是整块，不只是标签**——首版只删 `<| … DSML … >` 标签，结果工具参数文本
 *    （`钟楼 红苹果树 来历 种下`）原样留在正文里（单测当场抓到）。现在从开标签到对应闭标签
 *    之间的内容一并丢弃。
 * ② **不得牺牲逐字流式**（用户定的「1 字 = 1 次更新」）——第二版按「行」缓冲，
 *    正文要等整行结束才上屏，同样被单测抓到。现在**普通文本一个字都不扣**、原样即刻放行；
 *    只有遇到 `<` 且尚未见到 `>` 的半截标签才短暂扣住。
 *
 * 未知情况一律选择「宁可漏出噪声，也不吞掉正文」：块一直等不到闭标签时，超过
 * [MAX_PENDING] 字符或流结束时**把扣住的内容去掉标签后放回**。
 */
object ToolMarkupFilter {

    /** 完整噪声标签：`<| … DSML … >` / `</| … DSML … >`（真实样本含空格填充）。 */
    private val MARKUP_TAG = Regex("""<[/]?\|[^>]{0,64}DSML[^>]{0,120}>""", RegexOption.IGNORE_CASE)

    /** 可能是标签开头的形状（尚未见到 `>`）：`<`、`</`、`<|`、`</|`、`<|…`。 */
    private val TAG_START = Regex("""^<[/]?\|?[^>]*$""")

    /** 未闭合噪声块的等待上限：超过就放弃等待、把内容放回（只去标签）。 */
    private const val MAX_PENDING = 600

    /** 一次性过滤（非流式场景/测试用）。 */
    fun strip(text: String): String {
        val stream = Stream()
        return stream.accept(text) + stream.flush()
    }

    fun containsMarkup(text: String): Boolean = MARKUP_TAG.containsMatchIn(text)

    /** 流式过滤器：喂 delta，立刻返回可上屏文本（普通文本零延迟）。结束时必须调用 [flush]。 */
    class Stream {
        private val buf = StringBuilder()
        private var inBlock = false

        fun accept(chunk: String): String {
            if (chunk.isEmpty()) return ""
            buf.append(chunk)
            val out = StringBuilder()
            var i = 0
            while (i < buf.length) {
                if (inBlock) {
                    val closeAt = closingTagStart(buf, i)
                    if (closeAt < 0) {
                        if (buf.length - i > MAX_PENDING) {
                            // 等不到闭标签：不再吞，去标签后放回（宁可漏噪声，不丢正文）
                            inBlock = false
                            out.append(stripTags(buf.substring(i)))
                            i = buf.length
                            continue
                        }
                        break // 扣住，等下一片
                    }
                    inBlock = false
                    i = afterTag(buf, closeAt)
                    continue
                }

                val c = buf[i]
                if (c != '<') {
                    out.append(c)
                    i++
                    continue
                }
                val gt = buf.indexOfGt(i)
                if (gt < 0) {
                    if (TAG_START.matches(buf.substring(i))) break // 半截标签：扣住
                    out.append(c)
                    i++
                    continue
                }
                val tag = buf.substring(i, gt + 1)
                if (tag.contains("DSML", ignoreCase = true)) {
                    // 开标签 → 进入块模式（标签本身也丢）；孤立闭标签 → 直接丢
                    if (!tag.startsWith("</")) inBlock = true
                    i = afterTag(buf, i)
                    continue
                }
                out.append(tag)
                i = gt + 1
            }

            val rest = buf.substring(i.coerceAtMost(buf.length))
            buf.setLength(0)
            buf.append(rest)
            val text = out.toString()
            return if (text.isBlank()) "" else text
        }

        /** 收尾：扣住的若是未闭合噪声块，去标签放回；否则原样交回（可能是普通文本里的 `<`）。 */
        fun flush(): String {
            val rest = buf.toString()
            buf.setLength(0)
            val cleaned = if (inBlock) stripTags(rest) else rest
            inBlock = false
            return if (cleaned.isBlank()) "" else cleaned
        }

        /** 从 [from] 起找「含 DSML 的闭标签」的起始下标；没有则 -1（含只见到半截闭标签的情形）。 */
        private fun closingTagStart(sb: CharSequence, from: Int): Int {
            var k = from
            while (k < sb.length) {
                if (sb[k] == '<' && k + 1 < sb.length && sb[k + 1] == '/') {
                    val gt = sb.indexOfGt(k)
                    if (gt < 0) return -1
                    if (sb.substring(k, gt + 1).contains("DSML", ignoreCase = true)) return k
                    k = gt + 1
                    continue
                }
                k++
            }
            return -1
        }

        /** 跳过一个标签，并吞掉它后面紧跟的换行（避免留下空行）。 */
        private fun afterTag(sb: CharSequence, tagStart: Int): Int {
            val gt = sb.indexOfGt(tagStart)
            if (gt < 0) return sb.length
            var k = gt + 1
            while (k < sb.length && (sb[k] == '\n' || sb[k] == '\r')) k++
            return k
        }

        private fun CharSequence.indexOfGt(from: Int): Int {
            for (k in from until length) if (this[k] == '>') return k
            return -1
        }

        private fun stripTags(text: String): String = MARKUP_TAG.replace(text) { "" }
    }
}

package com.luzzymeow.luzzyrp.core.common

/**
 * 数量字段单位换算（设置页 6.2）。
 *
 * 支持填写形态（大小写不敏感、可带空格）：
 *   `1024000`（纯数字）· `1024K` · `1024k` · `1M` · `1m` · `1.5M` · `512 k` · `2G`
 *
 * 进制：K = 1024，M = 1024²，G = 1024³（模型上下文长度惯例）。
 * 解析失败返回 null（字段自检提示用）。
 */
object TokenCountParser {

    private val PATTERN = Regex("""^(\d+(?:\.\d+)?)\s*([kKmMgG]?)$""")

    fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val match = PATTERN.matchEntire(text) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "" -> 1L
            "K" -> 1024L
            "M" -> 1024L * 1024
            "G" -> 1024L * 1024 * 1024
            else -> return null
        }
        val value = (number * multiplier).toLong()
        return if (value < 0) null else value
    }

    /** 反向格式化（展示用）：优先 M/K 缩写，<1024 显示原值。 */
    fun format(value: Long): String = when {
        value >= 1024L * 1024 * 1024 && value % (1024L * 1024 * 1024) == 0L -> "${value / (1024L * 1024 * 1024)}G"
        value >= 1024L * 1024 && value % (1024L * 1024) == 0L -> "${value / (1024L * 1024)}M"
        value >= 1024 && value % 1024 == 0L -> "${value / 1024}K"
        else -> value.toString()
    }
}

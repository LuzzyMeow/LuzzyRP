package com.luzzymeow.luzzyrp.assistant.domain.skill

import com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillDocument
import com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillScope

/**
 * 技能 Markdown 解析（PLAN §8.1）。
 *
 * 约定 front-matter（YAML 子集，**不引 YAML 库**）：
 * ```markdown
 * ---
 * name: 周报生成
 * description: 把本周会话与日历整理成周报
 * tools: [calendar_read, memory_search]
 * ---
 * （正文：给模型的流程说明 / 检查清单 / 输出格式约定）
 * ```
 *
 * **校验策略（PLAN §8.2）**：front-matter 解析失败或 `name` 缺失 → 抛
 * [SkillParseException]（拒绝导入并提示，**不静默吞**）。
 *
 * 技能正文是**提示词内容，不是代码**；`tools:` 仅作提示，实际可用性仍由工具全局开关决定（§8.3）。
 */
object SkillLoader {

    private const val FENCE = "---"

    /** 解析结果。 */
    data class Parsed(
        val name: String,
        val description: String,
        val tools: List<String>,
        val body: String,
    )

    /** 解析 Markdown 文本；无 front-matter 时以首个 `#` 标题或文件名兜底。 */
    fun parse(markdown: String, fallbackName: String? = null): Parsed {
        val text = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')

        if (lines.firstOrNull()?.trim() != FENCE) {
            // 无 front-matter：允许，用首个标题/文件名作名字
            val name = firstHeading(lines) ?: fallbackName
                ?: throw SkillParseException("技能缺少 front-matter 且无标题，无法确定名称")
            return Parsed(name, "", emptyList(), text.trim())
        }

        val endIndex = lines.drop(1).indexOfFirst { it.trim() == FENCE }
        if (endIndex < 0) throw SkillParseException("front-matter 未闭合（缺少结束的 $FENCE）")
        val header = lines.subList(1, endIndex + 1)
        val body = lines.drop(endIndex + 2).joinToString("\n").trim()

        var name: String? = null
        var description = ""
        var tools: List<String> = emptyList()
        header.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val separator = line.indexOf(':')
            if (separator <= 0) throw SkillParseException("front-matter 行格式错误（缺少冒号）: $line")
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (key) {
                "name" -> name = value.trim('"', '\'', ' ')
                "description" -> description = value.trim('"', '\'', ' ')
                "tools" -> tools = parseInlineList(value)
                else -> Unit // 未知键忽略（向前兼容）
            }
        }

        val resolvedName = name?.takeIf { it.isNotBlank() } ?: firstHeading(lines) ?: fallbackName
        if (resolvedName.isNullOrBlank()) throw SkillParseException("front-matter 缺少 name 字段")
        return Parsed(resolvedName, description, tools, body)
    }

    /** 转领域注入文档。 */
    fun toDocument(parsed: Parsed, scope: SkillScope): SkillDocument =
        SkillDocument(name = parsed.name, body = parsed.body, description = parsed.description, tools = parsed.tools, scope = scope)

    /** 从文件名推断技能名（去扩展名、下划线转空格）。 */
    fun nameFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()

    private fun firstHeading(lines: List<String>): String? =
        lines.firstOrNull { it.trimStart().startsWith("# ") }?.trim()?.removePrefix("#")?.trim()

    /** 解析 `[a, b, c]` 或 `a, b` 形式的内联列表。 */
    private fun parseInlineList(value: String): List<String> {
        val inner = value.trim().removePrefix("[").removeSuffix("]")
        if (inner.isBlank()) return emptyList()
        return inner.split(',').map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotEmpty() }
    }
}

/** 技能解析失败（导入时向用户明示，不静默吞）。 */
class SkillParseException(message: String) : IllegalArgumentException(message)

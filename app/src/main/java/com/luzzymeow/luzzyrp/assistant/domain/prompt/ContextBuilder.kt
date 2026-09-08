package com.luzzymeow.luzzyrp.assistant.domain.prompt

import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmMessage
import com.luzzymeow.luzzyrp.assistant.domain.llm.LlmRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** 技能来源（PLAN §8.2：全局启用 / 指定助手启用）。 */
enum class SkillScope { GLOBAL, ASSISTANT }

/**
 * 注入系统提示词的技能正文（PLAN §8.1）。
 *
 * [tools] 仅作**提示**（写入提示词），实际可用性仍由工具全局开关决定——技能不得绕过权限（§8.3）。
 */
data class SkillDocument(
    val name: String,
    val body: String,
    val description: String? = null,
    val tools: List<String> = emptyList(),
    val scope: SkillScope = SkillScope.ASSISTANT,
)

/** 记忆注入模式（PLAN §7.1）。 */
enum class MemoryMode(val id: String) {
    FULL("full"),
    EMBED("embed"),
    HYBRID("hybrid"),
    ;

    companion object {
        fun fromId(id: String?): MemoryMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: FULL
    }
}

/** 一条被注入的记忆（domain 侧最小视图，映射自 `memory` 表）。 */
data class MemoryItem(
    val id: String,
    val content: String,
    val type: String? = null,
    /** 相似度（0..1，仅 embed / hybrid 有值）。 */
    val score: Double? = null,
)

/**
 * 记忆端口（PLAN §7）：`full` 全文注入 / `embed` 向量召回 / `hybrid` 召回 + 最近事实。
 *
 * 具体实现（向量暴力扫描、LRU 缓存、嵌入失败降级）在数据/记忆层，domain 只依赖本接口。
 * 实现**不得抛异常**（召回失败返回空列表）；[ContextBuilder] 也做了兜底捕获。
 */
fun interface MemoryProvider {
    suspend fun recall(query: String, mode: MemoryMode, limit: Int): List<MemoryItem>

    companion object {
        /** 无记忆（P1 默认）。 */
        val NONE: MemoryProvider = MemoryProvider { _, _, _ -> emptyList() }
    }
}

/**
 * 历史摘要端口（PLAN §5.4：旧轮转 system 摘要）。
 *
 * 实现由「同一模型 + 同一供应商」异步生成；**失败 / 超时必须返回 null**
 * （[ContextBuilder] 会退化为截断），不得抛异常打断本轮请求。
 */
fun interface Summarizer {
    suspend fun summarize(messages: List<LlmMessage>): String?
}

/**
 * 系统提示词装配输入（PLAN §5.4）。
 *
 * [skills] 由调用方按「全局启用 → 助手启用」顺序传入（[ContextBuilder] 内再按 [SkillScope] 分组，
 * 组内保持传入顺序）。
 */
data class PromptSpec(
    val assistantName: String,
    val systemPrompt: String = "",
    val model: String = "",
    /** 工作区展示路径（`{{workspace}}`）。 */
    val workspacePath: String = "",
    val skills: List<SkillDocument> = emptyList(),
    /** 由 [com.luzzymeow.luzzyrp.assistant.domain.tool.ToolRegistry.conventions] 生成。 */
    val toolConventions: String = "",
    val memoryMode: MemoryMode = MemoryMode.FULL,
    val memoryLimit: Int = ContextBuilder.DEFAULT_MEMORY_LIMIT,
    /** 注入时间（单测固定；默认取当前时间）。 */
    val nowMillis: Long = System.currentTimeMillis(),
    val zoneId: String = ZoneId.systemDefault().id,
    /** 追加在系统提示词末尾的额外内容（预留：工坊 Diff 场景等）。 */
    val extraSystemSuffix: String? = null,
)

/**
 * 上下文装配器（PLAN §5.4）。
 *
 * 装配顺序：
 * ```
 * [system] 助手提示词（变量已替换）
 *          全局启用 Skill 正文 → 助手启用 Skill 正文
 *          记忆块（MemoryProvider 召回）
 *          工具使用约定（ToolRegistry 生成）
 * [历史消息]（超限时压缩：保留最近 N 轮 + 工具对，旧轮转 system 摘要）
 * [本轮用户输入]
 * ```
 *
 * 压缩触发：token 估算（字符数 / 3.5）超过 `maxContextTokens × compressionRatio`（默认 70%）。
 * 摘要失败 → 退化为「保留旧轮尾部 + 截断标记」。
 *
 * 纯 Kotlin（`java.time` 属 JDK，无 Android 依赖），可单测。
 */
class ContextBuilder(
    private val memory: MemoryProvider = MemoryProvider.NONE,
    private val summarizer: Summarizer? = null,
    /** 模型上下文窗口（token）。默认 128K，调用方应按模型能力注入。 */
    private val maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS,
    private val compressionRatio: Double = DEFAULT_COMPRESSION_RATIO,
    private val keepRecentTurns: Int = DEFAULT_KEEP_RECENT_TURNS,
) {

    /** 装配完整消息序列（system + 压缩后的历史 + 本轮输入）。 */
    suspend fun build(spec: PromptSpec, history: List<LlmMessage>, userInput: String): List<LlmMessage> {
        val recalled = recall(spec, userInput)
        val system = buildSystemPrompt(spec, recalled)
        val trimmed = compressIfNeeded(history)
        return buildList {
            add(LlmMessage(role = LlmRole.SYSTEM, content = system))
            addAll(trimmed)
            add(LlmMessage(role = LlmRole.USER, content = userInput))
        }
    }

    /** 召回记忆（失败兜底为空，不打断本轮）。 */
    private suspend fun recall(spec: PromptSpec, query: String): List<MemoryItem> = try {
        memory.recall(query, spec.memoryMode, spec.memoryLimit)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        emptyList()
    }

    /** 构造系统提示词（不含历史）。 */
    fun buildSystemPrompt(spec: PromptSpec, recalled: List<MemoryItem>): String {
        val zone = runCatching { ZoneId.of(spec.zoneId) }.getOrElse { ZoneId.systemDefault() }
        val now = Instant.ofEpochMilli(spec.nowMillis).atZone(zone)
        val variables = mapOf(
            VARIABLE_TIME to now.format(TIME_FORMAT),
            VARIABLE_DATE to now.format(DATE_FORMAT),
            VARIABLE_MODEL to spec.model,
            VARIABLE_ASSISTANT_NAME to spec.assistantName,
            VARIABLE_WORKSPACE to spec.workspacePath,
            VARIABLE_MEMORY_COUNT to recalled.size.toString(),
        )

        val builder = StringBuilder()
        val prompt = renderVariables(spec.systemPrompt, variables).trim()
        builder.append(prompt.ifEmpty { renderVariables(DEFAULT_SYSTEM_PROMPT, variables) })

        appendSkills(builder, "全局技能", spec.skills.filter { it.scope == SkillScope.GLOBAL }, variables)
        appendSkills(builder, "助手技能", spec.skills.filter { it.scope == SkillScope.ASSISTANT }, variables)
        appendMemory(builder, recalled)
        spec.toolConventions.trim().takeIf { it.isNotEmpty() }?.let {
            builder.append("\n\n").append(it)
        }
        spec.extraSystemSuffix?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.append("\n\n").append(it)
        }
        return builder.toString().trim()
    }

    private fun appendSkills(
        builder: StringBuilder,
        title: String,
        skills: List<SkillDocument>,
        variables: Map<String, String>,
    ) {
        if (skills.isEmpty()) return
        builder.append("\n\n## ").append(title).append("（").append(skills.size).append("）")
        skills.forEach { skill ->
            builder.append("\n### ").append(skill.name)
            skill.description?.takeIf { it.isNotBlank() }?.let { builder.append("：").append(it.trim()) }
            builder.append('\n').append(renderVariables(skill.body, variables).trim())
            if (skill.tools.isNotEmpty()) {
                builder.append("\n（该技能声明会用到：").append(skill.tools.joinToString("、")).append("）")
            }
        }
    }

    private fun appendMemory(builder: StringBuilder, recalled: List<MemoryItem>) {
        if (recalled.isEmpty()) return
        builder.append("\n\n## 助手记忆（").append(recalled.size).append(" 条）\n")
        recalled.forEachIndexed { index, item ->
            if (index > 0) builder.append('\n')
            builder.append("- ")
            item.type?.takeIf { it.isNotBlank() }?.let { builder.append('[').append(it.trim()).append("] ") }
            builder.append(item.content.trim())
            item.score?.let { builder.append("（相似度 ").append(formatScore(it)).append("）") }
        }
    }

    // ---------- 压缩（PLAN §5.4） ----------

    /** 是否需要压缩：token 估算超过 `maxContextTokens × compressionRatio`。 */
    fun needsCompression(messages: List<LlmMessage>): Boolean =
        estimateMessagesTokens(messages) > (maxContextTokens * compressionRatio).toInt()

    /** 按需压缩（供 Agent 循环每轮调用）。 */
    suspend fun compressIfNeeded(messages: List<LlmMessage>): List<LlmMessage> =
        if (needsCompression(messages)) compress(messages) else messages

    /**
     * 压缩历史：保留最近 [keepRecentTurns] 轮（以 USER 消息为轮起点，工具对不会被切断），
     * 更早的部分合并为一条 system 摘要；摘要失败退化为截断。
     */
    suspend fun compress(messages: List<LlmMessage>): List<LlmMessage> {
        if (messages.isEmpty()) return messages
        var head = 0
        while (head < messages.size && messages[head].role == LlmRole.SYSTEM) head++
        val systemMessages = messages.subList(0, head)
        val conversation = messages.subList(head, messages.size)
        val cut = cutIndex(conversation, keepRecentTurns)
        if (cut <= 0) return messages

        val older = conversation.subList(0, cut).toList()
        val recent = conversation.subList(cut, conversation.size).toList()
        val summary = summarize(older)
        val summaryMessage = if (summary != null) {
            LlmMessage(role = LlmRole.SYSTEM, content = "$SUMMARY_HEADER\n$summary")
        } else {
            LlmMessage(role = LlmRole.SYSTEM, content = "$TRUNCATED_HEADER\n${truncatedTranscript(older)}")
        }
        return systemMessages + summaryMessage + recent
    }

    /** 摘要生成（失败 / 空结果 → null，绝不抛出）。 */
    private suspend fun summarize(messages: List<LlmMessage>): String? {
        val engine = summarizer ?: return null
        return try {
            engine.summarize(messages)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /** 截断兜底：保留旧轮**尾部**，长度上限为触发阈值字符数的一半。 */
    private fun truncatedTranscript(messages: List<LlmMessage>): String {
        val limit = (maxContextTokens * compressionRatio * CHARS_PER_TOKEN / 2).toInt().coerceAtLeast(256)
        val text = messages.joinToString("\n") { message ->
            "${message.role.name.lowercase()}: ${message.content}"
        }
        return if (text.length <= limit) text else "…（前文已省略）" + text.takeLast(limit)
    }

    /**
     * 保留窗口的起点下标：以 USER 消息为轮边界。
     *
     * 返回 0 表示无可压缩内容（轮数不足）。
     */
    fun cutIndex(conversation: List<LlmMessage>, keepTurns: Int): Int {
        if (keepTurns <= 0) return 0
        val userPositions = conversation.indices.filter { conversation[it].role == LlmRole.USER }
        if (userPositions.size <= keepTurns) return 0
        return userPositions[userPositions.size - keepTurns]
    }

    private fun formatScore(score: Double): String = String.format("%.2f", score)

    companion object {
        /** token 估算系数（PLAN §5.4：字符数 / 3.5 近似）。 */
        const val CHARS_PER_TOKEN: Double = 3.5

        const val DEFAULT_MAX_CONTEXT_TOKENS: Int = 128_000
        const val DEFAULT_COMPRESSION_RATIO: Double = 0.7
        const val DEFAULT_KEEP_RECENT_TURNS: Int = 6
        const val DEFAULT_MEMORY_LIMIT: Int = 8

        const val VARIABLE_TIME = "time"
        const val VARIABLE_DATE = "date"
        const val VARIABLE_MODEL = "model"
        const val VARIABLE_ASSISTANT_NAME = "assistant_name"
        const val VARIABLE_WORKSPACE = "workspace"
        const val VARIABLE_MEMORY_COUNT = "memory_count"

        const val SUMMARY_HEADER = "【历史摘要】"
        const val TRUNCATED_HEADER = "【历史已截断】"

        /** 未填写助手提示词时的兜底人格（保持中性，不做任何内容审查）。 */
        const val DEFAULT_SYSTEM_PROMPT: String =
            "你是 {{assistant_name}}，一个可以调用工具的助手。回答简洁、准确、可直接执行；" +
                "不确定时先澄清，需要信息时先调用工具。"

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** 变量替换：`{{name}}` → 值（未提供的变量原样保留，便于用户排查拼写）。 */
        fun renderVariables(template: String, variables: Map<String, String>): String {
            if (template.isEmpty() || variables.isEmpty()) return template
            var result = template
            variables.forEach { (key, value) -> result = result.replace("{{$key}}", value) }
            return result
        }

        /** 单段文本 token 估算。 */
        fun estimateTokens(text: String): Int =
            if (text.isEmpty()) 0 else ceil(text.length / CHARS_PER_TOKEN).toInt()

        /** 消息列表 token 估算（含每条约 4 token 的角色/结构开销）。 */
        fun estimateMessagesTokens(messages: List<LlmMessage>): Int =
            messages.sumOf { MESSAGE_OVERHEAD_TOKENS + estimateTokens(it.content) }

        private const val MESSAGE_OVERHEAD_TOKENS = 4
    }
}

package com.luzzymeow.luzzyrp.assistant.ui.model

/**
 * 助手层 UI 模型（方向 A · 卷宗）。
 *
 * 与数据层解耦：P0 阶段用 [SampleData] 驱动静态骨架；P1 起由 ViewModel 把 Room 实体映射成这些类型，
 * Composable 只认这里的字段（便于 Preview 与单测）。
 */

data class AssistantUi(
    val id: String,
    val name: String,
    /** 头像占位字符（无头像文件时显示姓名首字）。 */
    val initial: String,
    val modelLabel: String,
    val lastTitle: String?,
    val lastTimeLabel: String?,
    val unread: Boolean = false,
)

data class ConversationUi(
    val id: String,
    val assistantId: String,
    val title: String,
    val summary: String,
    val updatedAtLabel: String,
    /** 日期分组：今天 / 昨天 / 7 天内 / 本月 / 更早（PLAN §6.2）。 */
    val group: String,
)

/** 消息角色（渲染分支用）。 */
enum class MessageRoleUi { USER, ASSISTANT, SYSTEM }

data class MessageUi(
    val id: String,
    val role: MessageRoleUi,
    val content: String,
    /** 思考卡内容（折叠为一行摘要）。 */
    val thinking: ThinkingUi? = null,
    /** 本轮工具卡（默认折叠为单行）。 */
    val tools: List<ToolCardUi> = emptyList(),
    /** 连续 ≥3 个工具 + 思考节点 → 步骤组（PLAN §11.2）。 */
    val stepGroup: StepGroupUi? = null,
    val streaming: Boolean = false,
)

data class ThinkingUi(val summary: String, val fullText: String, val durationLabel: String)

enum class ToolStatusUi { WAITING_APPROVAL, RUNNING, SUCCESS, FAILED }

data class ToolCardUi(
    val name: String,
    val argsSummary: String,
    val status: ToolStatusUi,
    val durationLabel: String?,
    val resultPreview: String? = null,
)

data class StepGroupUi(
    val stepCount: Int,
    val totalDurationLabel: String,
    val steps: List<StepUi>,
)

data class StepUi(val name: String, val detail: String, val ok: Boolean)

data class MemoryUi(
    val id: String,
    val typeLabel: String,
    val content: String,
    val sourceLabel: String,
    val timeLabel: String,
    /** 0f..1f；无嵌入模型（full 模式）时 null，不渲染相似度条。 */
    val similarity: Float? = null,
)

/**
 * P0 静态骨架数据（真实数据接线见 P1）。
 *
 * **刻意保留真实形态**（多助手 / 多分组 / 含思考与工具的消息 / 三模式记忆），
 * 便于交付前肉眼校验密度与层级是否符合 `directions.md` 的规格。
 */
object SampleData {

    val assistants = listOf(
        AssistantUi("a1", "阿墨", "墨", "deepseek-v4-pro", "第 42 周周报", "12 分钟前", unread = true),
        AssistantUi("a2", "拾光", "拾", "glim-5.3", "素材归类与去重", "昨天"),
        AssistantUi("a3", "小满", "满", "deepseek-v4-flash", "日程整理", "9 月 6 日"),
        AssistantUi("a4", "砚秋", "砚", "glim-5.3-flash", "稿件校对", "8 月 30 日"),
    )

    val conversations = listOf(
        ConversationUi("c1", "a1", "第 42 周周报", "本周 5 场会议，2 个里程碑，遗留三条待决事项。", "12 分钟前", "今天"),
        ConversationUi("c2", "a1", "供应商额度告警复盘", "阈值 80% 触发，建议改成分级提醒。", "昨天", "昨天"),
        ConversationUi("c3", "a1", "沙盒里 rg 比 grep 快", "已 apk add ripgrep，替换脚本里的 grep 调用。", "9 月 4 日", "7 天内"),
        ConversationUi("c4", "a1", "设计评审排期", "评审改到下周三，额度复核待办。", "9 月 2 日", "7 天内"),
        ConversationUi("c5", "a1", "长文摘要模板", "周报按「进展 / 风险 / 下周」三段写。", "8 月 28 日", "本月"),
        ConversationUi("c6", "a1", "MCP filesystem 接入", "只允许读工作区内路径，越界直接拒绝。", "8 月 21 日", "本月"),
        ConversationUi("c7", "a1", "早间日程整理", "9:30 站会，14:00 评审，17:00 一对一。", "8 月 12 日", "更早"),
    )

    val messages = listOf(
        MessageUi(
            id = "m1",
            role = MessageRoleUi.USER,
            content = "把第 42 周会议整理成周报。",
        ),
        MessageUi(
            id = "m2",
            role = MessageRoleUi.ASSISTANT,
            content = "",
            thinking = ThinkingUi(
                summary = "正在梳理 5 份会议记录，召回 3 条记忆…",
                fullText = "先读 5 份会议记录，再按「进展 / 风险 / 下周」三段组织；同时召回 3 条与周报格式相关的记忆。",
                durationLabel = "1.2s",
            ),
            stepGroup = StepGroupUi(
                stepCount = 5,
                totalDurationLabel = "12.4s",
                steps = listOf(
                    StepUi("memory_search", "3 命中", ok = true),
                    StepUi("workspace_read", "×2", ok = true),
                    StepUi("workspace_write", "1.2 KB", ok = true),
                ),
            ),
        ),
        MessageUi(
            id = "m3",
            role = MessageRoleUi.ASSISTANT,
            content = "## 第 42 周周报\n\n本周 5 场会议，2 个里程碑，遗留三条待决事项。\n\n- 设计评审改期至下周三\n- 额度复核\n\n已写入 `week-42.md`（1.2 KB）。",
            tools = listOf(
                ToolCardUi("workspace_write", "week-42.md", ToolStatusUi.SUCCESS, "0.8s"),
            ),
        ),
    )

    val memories = listOf(
        MemoryUi("mem1", "事实", "第 42 周会议里，设计评审延到下周三。", "agent · 12 分钟前", "12 分钟前", 0.88f),
        MemoryUi("mem2", "偏好", "周报按「进展 / 风险 / 下周」三段写，不要表格。", "user · 昨天", "昨天", 0.76f),
        MemoryUi("mem3", "任务", "把 week-42 周报发给产品群，等评审改期确认后。", "agent · 昨天", "昨天", 0.71f),
        MemoryUi("mem4", "笔记", "沙盒里 rg 比 grep 快，已 apk add。", "user · 9 月 4 日", "9 月 4 日", 0.64f),
    )

    const val MEMORY_MODE_LABEL = "混合"
    const val MEMORY_TOPK = 8
    const val MEMORY_THRESHOLD = 0.35f
    const val MEMORY_RECENT = 5
}

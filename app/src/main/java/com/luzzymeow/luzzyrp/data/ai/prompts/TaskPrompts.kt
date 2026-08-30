package com.luzzymeow.luzzyrp.data.ai.prompts

/**
 * 一次性提示词模板（非流式任务：摘要调度 / ACE 反思与提取 / 会话标题）。
 * [INVARIANT-KV] 这些任务独立于聊天主链路（不经对话前缀），不影响 KV 命中。
 */
object TaskPrompts {

    /** A 级摘要：每轮生成，≤50 字，盲续五要素（时间/地点/人物/事件/结果）。 */
    fun summaryA(previous: String, roundText: String): String = """
你是一场文字角色扮演的剧情记录员。请把【本轮剧情】压缩成一条不超过 50 字的剧情摘要，供后续"盲续"使用。

要求：
- 必须包含五要素：时间、地点、人物、事件、结果（缺失的要素跳过）；
- 只写事实，不写评价、不写对话原文；
- 与【前情】衔接时使用代词或省略主语，保持连贯；
- 直接输出摘要正文，不要任何前后缀。

【前情】$previous

【本轮剧情】
$roundText
""".trim()

    /** B 级摘要：每 10 轮由 A 级合并，≤10 条。 */
    fun summaryB(aSummaries: List<String>): String = """
以下是最近若干条剧情摘要（按时间顺序）。请把它们合并压缩为不超过 10 条的阶段性摘要列表。

要求：
- 每条不超过 40 字，按时间顺序编号 1. 2. 3.；
- 保留影响后续剧情的关键事实（身份、承诺、伤亡、地点迁移、物品获得）；
- 直接输出列表，不要任何前后缀。

${aSummaries.joinToString("\n") { "- $it" }}
""".trim()

    /** C 级摘要：每 50 轮固化为永久设定。 */
    fun summaryC(bSummaries: List<String>): String = """
以下是一个长篇角色扮演故事到目前为止的阶段摘要。请固化出"永久事实卡"（不超过 8 条）。

要求：
- 只保留未来任何剧情都必须遵守的既成事实（世界观、人物关系、重要代价、长期目标）；
- 每条不超过 60 字；
- 直接输出列表，不要任何前后缀。

${bSummaries.joinToString("\n") { "- $it" }}
""".trim()

    /** ACE Reflect：反思本轮记忆条目的有用性。 */
    fun aceReflect(memoryContents: List<String>, roundText: String): String = """
你是记忆系统的评审。下面列出了本轮对话前注入的长期记忆条目，以及本轮实际发生的对话。
请逐条判断每条记忆在本轮中起到的作用，输出 JSON 数组（不要其他内容）：
[{"id": 序号, "verdict": "helpful|harmful|neutral"}]

- helpful：本轮叙事/决策实际用到了该记忆；
- harmful：本轮内容与该记忆矛盾（记忆可能已过时）；
- neutral：本轮未涉及。

【注入的记忆】
${memoryContents.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n")}

【本轮对话】
$roundText
""".trim()

    /** ACE Update：从本轮对话提取值得长期记住的新事实。 */
    fun aceExtract(roundText: String, existing: List<String>): String = """
从下面的对话中提取值得长期记住的新事实（用户的偏好、设定、承诺、人物关系变化等）。

要求：
- 最多 3 条；每条一句话（不超过 40 字）；
- 只提取"未来剧情需要记住"的持久事实，忽略一次性情绪与寒暄；
- 与【已有记忆】重复或近似的内容不要重复提取；
- 输出 JSON 数组：[{"content": "...", "category": "偏好|事件|关系|设定"}]，无新事实输出 []。

【已有记忆（用于去重）】
${existing.take(20).joinToString("\n") { "- $it" }}

【本轮对话】
$roundText
""".trim()

    /** 会话标题生成。 */
    fun title(firstRoundText: String): String = """
为以下角色扮演对话的开头生成一个 8 字以内的标题（像小说章节名，有意境）。
直接输出标题，不要引号和任何前后缀。

$firstRoundText
""".take(600)
}

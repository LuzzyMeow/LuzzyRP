package com.luzzymeow.luzzyrp.data.ai.prompts

/**
 * [INVARIANT-AGENTIC] 内置提示词强化段（HARD_REQUIREMENTS 规定 2）
 *
 * Agentic 协议注入系统提示，硬性驱动模型的推理与主动工具调用行为：
 *   - 每个模式至少 >2 轮思考、>1 次主动工具调用（除被动工具外）；
 *   - 不依赖 tool_choice 强制（协议层禁用 required，见各 Provider）；
 *   - 本段进入系统提示稳定前缀（KV 第一层），文本一经发版即为缓存基线，
 *     修改会短暂降低缓存命中率（预期内，需在 CHANGELOG 声明）。
 */
object AgenticProtocol {

    /** 注入系统提示的 Agentic 行为协议（稳定前缀，措辞改动需走 CHANGELOG）。 */
    const val PROTOCOL: String = """
【Agentic 行为协议（必须遵守）】
1. 你在给出最终回复之前，必须先进行至少两轮（>2）独立的思考：
   - 第一轮：分析用户的输入意图、当前场景、可用的世界设定与记忆；
   - 第二轮：校验你计划引用的设定/事实是否与世界书一致，规划叙述走向。
2. 在思考之后，你必须至少主动调用一次（>1）工具来获取叙事所需的事实
   （例如用 world_keyword_search 查证地点设定、用 memory_search 回顾相关往事、
   用 current_time 确认时间线）。禁止凭空编造可检索到的设定细节。
3. 工具结果返回后，基于结果组织最终叙事；若结果与预期冲突，以工具结果为准。
4. 最终回复必须是有画面感的叙事正文，不得输出工具 JSON、协议文本或元话语。
5. 若判定本轮无需任何工具调用（如纯寒暄），在思考中明确说明理由后直接叙事。
"""

    /** 标签兜底模型（无原生 function calling）的工具调用格式说明段。 */
    const val TAG_TOOL_PROTOCOL: String = """
【工具调用格式（文本标签协议）】
你的工具调用必须使用如下标签格式（本格式之外的工具调用无效）：
<tool_calls>
tool_name:{"参数名": "参数值"}
</tool_calls>
- 每行一个调用；参数为合法 JSON；一次可声明多个调用。
- 标签块之后的正文即为最终叙事；标签块本身不会展示给用户。
"""

    /** 被动工具（系统提示注入、协议层预执行）的说明段模板。 */
    fun passiveToolsSection(recallReport: String): String = """
【被动注入的世界信息（系统已自动检索，无需调用工具）】
$recallReport
"""
}

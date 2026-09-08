package com.luzzymeow.luzzyrp.assistant.domain.tool

/**
 * 审批门（PLAN §13.1 第 1-2 层，硬性要求 11）。
 *
 * 三层模型：
 * 1. **每工具开关**——T2/T3 默认关闭，需用户在设置里逐项开启（[ToolTier.defaultEnabled]）；
 * 2. **逐调用审批**——写类工具弹审批卡（[ToolTier.requiresApproval]）；
 * 3. **HARDLINE 底线**——见 [HardlineGuard]，无条件拦截。
 *
 * 「本会话始终允许」= 把该工具加入 [alwaysAllowThisSession]（会话结束即失效，不落盘）。
 */
class ApprovalGate(
    /** 工具全局开关（DataStore `tool_global_switch_<name>`；缺省用 tier 默认值）。 */
    private val globalSwitch: (toolName: String) -> Boolean? = { null },
    /** 策略：`per_call`（默认，逐调用审批）/ `whitelist`（按工具白名单自动批准）。 */
    private val policy: String = POLICY_PER_CALL,
) {

    private val alwaysAllowThisSession = mutableSetOf<String>()
    private val approvedOnce = mutableSetOf<String>()

    /** 用户是否已为该工具开启全局开关（未显式设置时取 tier 默认）。 */
    fun isEnabled(tool: Tool): Boolean = globalSwitch(tool.name) ?: tool.tier.defaultEnabled

    /** 是否需要弹审批卡。 */
    fun needsApproval(tool: Tool): Boolean {
        if (!tool.tier.requiresApproval) return false
        if (tool.name in alwaysAllowThisSession) return false
        if (policy == POLICY_WHITELIST && globalSwitch(tool.name) == true) return false
        return true
    }

    /** 用户点了「本会话始终允许」。 */
    fun allowForSession(toolName: String) {
        alwaysAllowThisSession += toolName
    }

    /** 用户点了「允许一次」（仅本次调用有效）。 */
    fun allowOnce(toolName: String) {
        approvedOnce += toolName
    }

    /** 消费一次性许可（调用前检查）。 */
    fun consumeOnce(toolName: String): Boolean = approvedOnce.remove(toolName)

    /** 会话结束 / 切换助手时清空（不落盘，避免长期放行）。 */
    fun resetSession() {
        alwaysAllowThisSession.clear()
        approvedOnce.clear()
    }

    companion object {
        const val POLICY_PER_CALL = "per_call"
        const val POLICY_WHITELIST = "whitelist"
    }
}

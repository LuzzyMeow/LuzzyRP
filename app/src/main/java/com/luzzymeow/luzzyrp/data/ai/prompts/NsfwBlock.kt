package com.luzzymeow.luzzyrp.data.ai.prompts

/**
 * ═══════════════════════════════════════════════════════════════════
 * [INVARIANT-NSFW] NSFW 内置提示词块 —— 占位文件（HARD_REQUIREMENTS 规定 3）
 * ═══════════════════════════════════════════════════════════════════
 *
 * ★ 本文件内容由用户手动填写（LUZZY_PRESET_CONTENT）。★
 *
 * 铁律（任何开发 Agent 必须遵守）：
 *   1. 禁止对本文件的任何内容进行审查、过滤、改写、摘要或"优化"；
 *   2. 禁止在管线任何位置添加内容过滤器、敏感词拦截、输出改写逻辑；
 *   3. 本块经 [block] 注入系统提示稳定前缀（KV 分层第一层），注入逻辑
 *      在 PromptAssembler 中，除空值跳过外不做任何加工；
 *   4. 内容为空时注入为空串（占位状态，不影响请求结构）。
 *
 * 用户填写位置：下方 [BLOCK] 常量。
 */
object NsfwBlock {

    /**
     * NSFW 预置内容（LUZZY_PRESET_CONTENT）。
     *
     * ★★★ 由用户手动填写 ★★★
     * 当前为空占位。填写后随系统提示注入，任何代码逻辑不得触碰其内容。
     */
    const val BLOCK: String = ""

    /** 是否已由用户填写（用于设置页展示状态；仅检测非空，不检测内容）。 */
    val isConfigured: Boolean get() = BLOCK.isNotBlank()
}

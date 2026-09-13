package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage

/**
 * 一次请求的**装配结果**（A6）——引擎只负责「把它发出去、把结果事件化」。
 *
 * ## 为什么要有这个类型
 *
 * 改造前 `AgentLoop.run()` 同时承担两件事：**组装**（拼 system / 预设 / 历史 / 召回 / 快照）
 * 与**传输**（SSE + 工具循环）。后果是组装语义只能靠「发一次请求看结果」来验，
 * 而缓存相关的性质（前缀是否纯追加）根本没法在单测里断言。
 *
 * 拆开之后：[PromptAssembler] 是纯函数、`RequestBuilder` 是纯函数、
 * 引擎是纯传输——三者各自可测，且**只有一条组装路径**（不存在「界面走一条、测试走另一条」）。
 *
 * ## 为什么不把 history/userText 也带进来
 *
 * 组装需要的一切都已经烘进 [messages]。再带一份原始输入就出现了第二个真源：
 * 谁都能「顺手再拼一次」，而两次拼出来的东西未必逐字相同（那正是前缀缓存的杀手）。
 */
data class ChatRequest(
    /** 完整消息序列（system → 预设 → 前置 → 开场白 → 历史 → 快照 → 本轮输入）。 */
    val messages: List<LlmMessage>,
    /**
     * 本轮记忆召回命中（与写进尾部快照的是**同一份**）。
     *
     * 引擎只用它发 [AgentLoop.Event.Recall]（界面思考节点）；用不到时为空。
     */
    val recallHits: List<RecallEngine.Hit> = emptyList(),
)

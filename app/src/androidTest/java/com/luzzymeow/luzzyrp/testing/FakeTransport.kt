package com.luzzymeow.luzzyrp.testing

import com.luzzymeow.luzzyrp.chat.llm.LlmDelta
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 测试用假传输：按脚本回放增量，**零网络**。
 *
 * 放在共享位置的理由：仪器化测试的一条纪律是「UI 测试不联网」（见 `docs/CHAT-REGRESSION.md`）。
 * 接真实存储之后更需要它——一次真实的失败请求会把「生成中」挂很久，
 * 既拖慢用例，也会让 activity 在拆卸时卡在 PAUSED（表现为 teardown 超时这种莫名其妙的红）。
 */
class FakeTransport(private val script: List<LlmDelta> = emptyList()) : LlmTransport {
    override fun stream(request: LlmRequest): Flow<LlmDelta> = flow {
        script.forEach { emit(it) }
    }
}

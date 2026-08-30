package com.luzzymeow.luzzyrp.core.ai.provider

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.model.MessageChunk
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.UIMessage
import kotlinx.coroutines.flow.Flow

/**
 * [INVARIANT-STREAMING] LLM 供应商抽象（真流式不变性 S1/S2）
 *
 * 无状态接口：ProviderSetting 按调用传入，同一 Provider 实现可服务多个供应商档案。
 * 实现清单（ProviderManager 注册）：
 *   - OpenAICompatibleProvider：chat/completions SSE（一等公民，覆盖 GLM/DeepSeek/方舟等）
 *   - AnthropicProvider：messages API SSE
 *   - GoogleProvider：streamGenerateContent?alt=sse
 *
 * 流式实现铁律（规定 1）：
 *   - streamText 返回冷流：callbackFlow + EventSources.newEventSource()
 *   - onEvent → trySend 逐 event 零节流；awaitClose { eventSource.cancel() }
 *   - OkHttpClient readTimeout = 10 MINUTES（DI 注入，禁止改小）
 */
interface Provider<T : ProviderSetting> {

    /** 拉取模型列表（设置页用）。 */
    suspend fun listModels(setting: T): List<Model>

    /** 非流式生成（摘要/标题/反思等一次性任务用）。 */
    suspend fun generateText(
        setting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    /**
     * 真流式生成（聊天主链路）。
     * 返回的 Flow 在收集时发起请求，逐 event 发射 MessageChunk。
     */
    fun streamText(
        setting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>

    /** 文本嵌入（ACE 记忆 / 世界书向量召回）。 */
    suspend fun generateEmbedding(setting: T, texts: List<String>): List<FloatArray>
}

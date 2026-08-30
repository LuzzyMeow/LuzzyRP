package com.luzzymeow.luzzyrp.core.ai.provider

import com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams
import com.luzzymeow.luzzyrp.core.ai.provider.anthropic.AnthropicProvider
import com.luzzymeow.luzzyrp.core.ai.provider.google.GoogleProvider
import com.luzzymeow.luzzyrp.core.ai.provider.openai.OpenAICompatibleProvider
import com.luzzymeow.luzzyrp.core.model.MessageChunk
import com.luzzymeow.luzzyrp.core.model.Model
import com.luzzymeow.luzzyrp.core.model.ProviderSetting
import com.luzzymeow.luzzyrp.core.model.ProviderType
import com.luzzymeow.luzzyrp.core.model.UIMessage
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/**
 * Provider 网关接口：GenerationHandler 依赖此抽象（可测试性），
 * 生产实现为 ProviderManager。
 */
interface ProviderGateway {
    fun resolve(setting: ProviderSetting): Provider<ProviderSetting>
    suspend fun listModels(setting: ProviderSetting): List<Model>
    suspend fun generateText(setting: ProviderSetting, messages: List<UIMessage>, params: com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams): MessageChunk
    fun streamText(setting: ProviderSetting, messages: List<UIMessage>, params: com.luzzymeow.luzzyrp.core.ai.params.TextGenerationParams): Flow<MessageChunk>
    suspend fun generateEmbedding(setting: ProviderSetting, texts: List<String>): List<FloatArray>
}

/**
 * [INVARIANT-STREAMING] Provider 注册表与路由（真流式不变性 S1）
 *
 * 依据 ProviderSetting.type 将调用路由到对应协议实现。
 * OkHttpClient 由 DI 注入（readTimeout = 10 MINUTES 不变性，见 di/DataSourceModule）。
 */
class ProviderManager(private val client: OkHttpClient) : ProviderGateway {

    private val openAICompatible = OpenAICompatibleProvider(client)
    private val anthropic = AnthropicProvider(client)
    private val google = GoogleProvider(client)

    /**
     * 按协议类型路由。三个实现均直接消费 [ProviderSetting]（对 T 无状态），
     * 故此处统一为 Provider<ProviderSetting> 视图，unchecked cast 安全。
     */
    @Suppress("UNCHECKED_CAST")
    override fun resolve(setting: ProviderSetting): Provider<ProviderSetting> = when (setting.type) {
        ProviderType.OPENAI -> openAICompatible as Provider<ProviderSetting>
        ProviderType.ANTHROPIC -> anthropic as Provider<ProviderSetting>
        ProviderType.GOOGLE -> google as Provider<ProviderSetting>
    }

    override suspend fun listModels(setting: ProviderSetting): List<Model> =
        resolve(setting).listModels(setting)

    override suspend fun generateText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = resolve(setting).generateText(setting, messages, params)

    override fun streamText(
        setting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = resolve(setting).streamText(setting, messages, params)

    override suspend fun generateEmbedding(setting: ProviderSetting, texts: List<String>): List<FloatArray> =
        resolve(setting).generateEmbedding(setting, texts)
}

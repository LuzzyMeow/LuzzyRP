package com.luzzymeow.luzzyrp.di

import com.luzzymeow.luzzyrp.core.ai.provider.ProviderGateway
import com.luzzymeow.luzzyrp.core.ai.provider.ProviderManager
import com.luzzymeow.luzzyrp.data.ai.GenerationHandler
import com.luzzymeow.luzzyrp.data.ai.PromptAssembler
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository
import com.luzzymeow.luzzyrp.service.ChatService
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * 生成管线 DI：ProviderGateway / GenerationHandler / PromptAssembler / ChatService。
 *
 * [INVARIANT-AGENTIC] ChatService 持有生成任务（离页不中断）；其 appScope
 * 由应用级 SupervisorScope 提供（DI 中惰性创建，独立于任何 Activity 生命周期）。
 */
val generationModule = module {

    single<ProviderGateway> { ProviderManager(get()) }

    single { GenerationHandler(get()) }

    single { PromptAssembler(get()) }

    single {
        val app = androidApplication()
        val appScope = CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default +
                kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
                    android.util.Log.e("ChatService", "未捕获的生成任务异常", t)
                }
        )
        ChatService(
            appScope = appScope,
            conversationRepository = get(),
            cardRepository = get(),
            worldbookRepository = get(),
            memoryRepository = get(),
            summaryRepository = get(),
            settingsStore = get(),
            providerManager = get(),
            promptAssembler = get(),
        )
    }
}

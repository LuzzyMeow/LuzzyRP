package com.luzzymeow.luzzyrp.di

import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * 基础设施单例。
 *
 * [INVARIANT-STREAMING] OkHttpClient 超时配置属于真流式不变性（HARD_REQUIREMENTS 规定 1）：
 *   - readTimeout = 10 MINUTES：LLM 长思考/长输出期间，SSE 事件间隔可能极长，
 *     任何更小的读超时都会在流中途断开连接，破坏「1 字 = 1 次更新」的逐字流式。
 *   - 禁止修改为更小的值；如需增加其他拦截器，必须保持超时配置原样。
 *   - SSE 建立后由 EventSource 侧取消 call timeout（见 core:ai 流式实现），二者配合生效。
 */
val infrastructureModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)   // [INVARIANT-STREAMING] 禁止改小
            .writeTimeout(120, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)   // 不做协议层 ping，由 SSE 事件本身保活
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * LuzzyRP 全局 DI 模块清单（声明顺序：先依赖后聚合）。
 *
 * 注册顺序（后续里程碑逐层扩充，保持此处为唯一模块清单入口）：
 *   - 基础设施：OkHttpClient（P0 已就绪）
 *   - AI 层：ProviderRegistry / ProviderManager（P2 里程碑加入 aiModule）
 *   - 数据层：Room / DataStore / DAO / 仓库（P3 里程碑加入 dataSourceModule）
 *   - 生成管线：GenerationHandler / ChatService（P4 里程碑加入 generationModule）
 */
val appModules = listOf(
    infrastructureModule,
    dataSourceModule,
    generationModule,
)

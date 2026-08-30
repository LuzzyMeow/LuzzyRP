package com.luzzymeow.luzzyrp

import android.app.Application
import com.luzzymeow.luzzyrp.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlinx.coroutines.launch

/**
 * LuzzyRP 应用入口。
 *
 * 职责：启动 Koin DI 容器（模块清单见 [appModules]）。
 * 后续里程碑中，ChatService / 数据库 / 供应商管理器等单例均在 DI 模块中注册。
 */
class LuzzyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LuzzyApp)
            modules(appModules)
        }
        seedInitialData()
    }

    /**
     * 种子数据（首次启动）：内置角色卡「鹿溪」+ 其世界书 + 正则内置预设。
     * 全部幂等（已存在则跳过）；IO 协程异步执行，不阻塞启动。
     */
    private fun seedInitialData() {
        val koin = org.koin.core.context.GlobalContext.get()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                koin.get<com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository>().ensureBuiltinCard()
                koin.get<com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository>().ensureBuiltinWorldbook()
                koin.get<com.luzzymeow.luzzyrp.data.repository.RegexRepository>().ensurePresets()
            }.onFailure { android.util.Log.e("LuzzyApp", "种子数据初始化失败", it) }
        }
    }
}

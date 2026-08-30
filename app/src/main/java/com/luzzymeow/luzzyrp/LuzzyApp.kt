package com.luzzymeow.luzzyrp

import android.app.Application
import com.luzzymeow.luzzyrp.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

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
    }
}

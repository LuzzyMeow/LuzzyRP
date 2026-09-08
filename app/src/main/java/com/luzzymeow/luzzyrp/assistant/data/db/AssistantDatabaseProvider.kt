package com.luzzymeow.luzzyrp.assistant.data.db

import android.content.Context

/**
 * AssistantDatabase 进程内单例（双重检查锁，线程安全）。
 *
 * 为什么不用 DI 框架：单模块 + 最小依赖是当前工程纪律（PLAN §2.2），一个 object 足够。
 * 多进程场景（当前 App 单进程）需改为每进程各自持有；不要跨进程共享 Room 实例。
 */
object AssistantDatabaseProvider {

    @Volatile
    private var instance: AssistantDatabase? = null

    fun get(context: Context): AssistantDatabase {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            val current = instance
            if (current != null) {
                current
            } else {
                AssistantDatabase.build(context).also { instance = it }
            }
        }
    }

    /** 关闭并清空单例（测试 / 进程内重载用；正常 App 生命周期无需调用）。 */
    fun close() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}

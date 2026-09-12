package com.luzzymeow.luzzyrp.data.store

import android.content.Context
import androidx.room.Room

/**
 * 数据库单例（P4-B）。
 *
 * Room 的 `RoomDatabase` 本身是线程安全且应当复用的：每处 `databaseBuilder(...).build()`
 * 都会新开一个连接池，多处创建会让「同一份数据出现两个视图」并放大锁竞争。
 *
 * 用双重检查 + `@Volatile`；[appContextOnly] 保证不会把 Activity 泄漏进来。
 */
object DatabaseProvider {

    @Volatile
    private var instance: LuzzyDatabase? = null

    fun luzzy(context: Context): LuzzyDatabase {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    private fun build(context: Context): LuzzyDatabase =
        Room.databaseBuilder(context.applicationContext, LuzzyDatabase::class.java, LuzzyDatabase.NAME)
            // 目前只有 version = 1（全新库，与旧 IndexedDB 无关：旧数据走迁移通道进来）。
            // 日后改表必须补 Migration —— 这里是默认拒绝破坏性迁移，宁可启动失败也不要静默清库。
            .build()

    /** 测试用：打开一个指定名字的库（例如每次用例一个临时库）。 */
    fun forTest(context: Context, name: String): LuzzyDatabase =
        Room.databaseBuilder(context.applicationContext, LuzzyDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    /** 测试用：重置单例（避免用例之间互相影响）。 */
    fun resetForTest() {
        synchronized(this) { instance = null }
    }
}

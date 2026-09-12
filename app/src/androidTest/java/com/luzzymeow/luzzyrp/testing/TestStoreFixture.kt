package com.luzzymeow.luzzyrp.testing

import android.content.Context
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.legacy.LegacyDb
import com.luzzymeow.luzzyrp.data.legacy.LegacyMigrator
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyDatabase
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.store.MigrationWriter
import java.io.File

/**
 * 测试用的存储脚手架。
 *
 * **为什么每个用例都要一个独立临时库**：聊天页以前只有内存状态，UI 测试之间互不影响。
 * 接上真实存储之后，「上一次用例写进库里的消息」会改变下一次用例的初始界面 ——
 * 那种失败看起来像「UI 坏了」，实际是测试互相污染。所以这里给每个用例一个独立库文件，
 * 用完删掉。
 *
 * [reopen] 用来模拟「杀进程重启」：关连接、拿**同一个文件**重开一个库实例，
 * 于是「数据还在」只可能来自磁盘，不可能来自内存残留。
 */
class TestStoreFixture private constructor(
    private val appContext: Context,
    private val dbFilePath: String,
    private val assetRoot: File,
) {

    val database: LuzzyDatabase = DatabaseProvider.forTest(appContext, dbFilePath)
    val store = LuzzyStore(database)
    val writer = MigrationWriter(store, assetRoot)
    val repository = ChatSessionRepository(store)

    /** 用样例旧数据把库填起来（走**真实迁移器**，不是手搓实体）。 */
    suspend fun seedFromSample() {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)
    }

    /** 模拟「杀进程重启」：关连接、同一个文件重开。 */
    fun reopen(): TestStoreFixture {
        runCatching { database.close() }
        return TestStoreFixture(appContext, dbFilePath, assetRoot)
    }

    fun close() {
        runCatching { database.close() }
        File(dbFilePath).delete()
        // Room 会附带 -wal / -shm
        File("$dbFilePath-wal").delete()
        File("$dbFilePath-shm").delete()
        assetRoot.deleteRecursively()
    }

    companion object {
        /** [reset] = true 时清掉同名旧库（用例隔离）；`reopen` 走 false 保住数据。 */
        fun create(context: Context, name: String, reset: Boolean = true): TestStoreFixture {
            val app = context.applicationContext
            val dbFile = File(app.cacheDir, "$name.db")
            if (reset) {
                dbFile.delete()
                File("${dbFile.absolutePath}-wal").delete()
                File("${dbFile.absolutePath}-shm").delete()
            }
            val assets = File(app.cacheDir, "$name-assets").apply { mkdirs() }
            return TestStoreFixture(app, dbFile.absolutePath, assets)
        }
    }
}

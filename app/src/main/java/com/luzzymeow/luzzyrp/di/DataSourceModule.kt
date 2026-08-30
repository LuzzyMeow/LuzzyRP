package com.luzzymeow.luzzyrp.di

import androidx.sqlite.db.SupportSQLiteDatabase
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.db.AppDatabase
import com.luzzymeow.luzzyrp.data.db.dao.CharacterCardDao
import com.luzzymeow.luzzyrp.data.db.dao.ConversationDao
import com.luzzymeow.luzzyrp.data.db.dao.FavoriteDao
import com.luzzymeow.luzzyrp.data.db.dao.MemoryDao
import com.luzzymeow.luzzyrp.data.db.dao.MessageNodeDao
import com.luzzymeow.luzzyrp.data.db.dao.RegexDao
import com.luzzymeow.luzzyrp.data.db.dao.SummaryDao
import com.luzzymeow.luzzyrp.data.db.dao.WorldbookDao
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.data.repository.ConversationRepository
import com.luzzymeow.luzzyrp.data.repository.FavoriteRepository
import com.luzzymeow.luzzyrp.data.repository.MemoryRepository
import com.luzzymeow.luzzyrp.data.repository.RegexRepository
import com.luzzymeow.luzzyrp.data.repository.SummaryRepository
import com.luzzymeow.luzzyrp.data.repository.VectorIndex
import com.luzzymeow.luzzyrp.data.repository.WorldbookRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * 数据源 DI 模块：AppDatabase + 8 个 DAO + SettingsStore + 仓库层 + 向量索引。
 */
val dataSourceModule = module {

    single { SettingsStore(androidApplication()) }

    single { AppDatabase.build(androidApplication()) }

    single<ConversationDao> { get<AppDatabase>().conversationDao() }
    single<MessageNodeDao> { get<AppDatabase>().messageNodeDao() }
    single<CharacterCardDao> { get<AppDatabase>().characterCardDao() }
    single<WorldbookDao> { get<AppDatabase>().worldbookDao() }
    single<RegexDao> { get<AppDatabase>().regexDao() }
    single<MemoryDao> { get<AppDatabase>().memoryDao() }
    single<SummaryDao> { get<AppDatabase>().summaryDao() }
    single<FavoriteDao> { get<AppDatabase>().favoriteDao() }
    single { get<AppDatabase>().promptPresetDao() }

    /** 向量索引：以可写库句柄为后端（sqlite-vec 虚表见 AppDatabase.VectorTables）。 */
    single {
        VectorIndex(dbProvider = {
            val db = get<AppDatabase>()
            db.openHelper.writableDatabase
        })
    }

    single { ConversationRepository(get(), get()) }
    single { CharacterCardRepository(androidApplication(), get(), get()) }
    single { WorldbookRepository(get(), get()) }
    single { MemoryRepository(get(), get(), get()) }
    single { SummaryRepository(get(), get()) }
    single { RegexRepository(get()) }
    single { FavoriteRepository(get()) }
    single { com.luzzymeow.luzzyrp.data.repository.PromptPresetRepository(get()) }
}

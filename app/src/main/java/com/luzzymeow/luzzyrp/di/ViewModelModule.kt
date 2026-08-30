package com.luzzymeow.luzzyrp.di

import com.luzzymeow.luzzyrp.ui.pages.character.CharactersViewModel
import com.luzzymeow.luzzyrp.ui.pages.character.CharacterDetailViewModel
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatViewModel
import com.luzzymeow.luzzyrp.ui.pages.favorites.FavoritesViewModel
import com.luzzymeow.luzzyrp.ui.pages.history.HistoryViewModel
import com.luzzymeow.luzzyrp.ui.pages.home.HomeViewModel
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryViewModel
import com.luzzymeow.luzzyrp.ui.pages.presets.PresetDetailViewModel
import com.luzzymeow.luzzyrp.ui.pages.presets.PresetsViewModel
import com.luzzymeow.luzzyrp.ui.pages.profile.ProfileViewModel
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsViewModel
import com.luzzymeow.luzzyrp.ui.pages.worldbook.WorldbookViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * ViewModel DI（Koin）。
 * 带路由参数的 VM（Chat/CharacterDetail）用 viewModel{parametersOf} 工厂；
 * 无参数 VM 用 viewModelOf 构造器注入。
 */
val viewModelModule = module {

    viewModel { (conversationId: String) ->
        ChatViewModel(
            conversationId = conversationId,
            chatService = get(),
            conversationRepository = get(),
            cardRepository = get(),
            settingsStore = get(),
        )
    }

    viewModelOf(::HomeViewModel)

    viewModelOf(::CharactersViewModel)

    viewModel { (cardId: String) ->
        CharacterDetailViewModel(androidApplication(), cardId = cardId, cardRepository = get())
    }

    viewModelOf(::MemoryViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::SettingsViewModel)

    viewModel { ProfileViewModel(androidApplication(), get()) }
    viewModelOf(::PresetsViewModel)
    viewModel { (presetId: String) -> PresetDetailViewModel(presetId, get(), get()) }
}

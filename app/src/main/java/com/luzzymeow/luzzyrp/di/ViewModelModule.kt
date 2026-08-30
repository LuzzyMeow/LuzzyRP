package com.luzzymeow.luzzyrp.di

import com.luzzymeow.luzzyrp.ui.pages.character.CharactersViewModel
import com.luzzymeow.luzzyrp.ui.pages.character.CharacterDetailViewModel
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatViewModel
import com.luzzymeow.luzzyrp.ui.pages.favorites.FavoritesViewModel
import com.luzzymeow.luzzyrp.ui.pages.history.HistoryViewModel
import com.luzzymeow.luzzyrp.ui.pages.home.HomeViewModel
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryViewModel
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsViewModel
import com.luzzymeow.luzzyrp.ui.pages.worldbook.WorldbookViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
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
            settingsStore = get(),
        )
    }

    viewModelOf(::HomeViewModel)

    viewModelOf(::CharactersViewModel)

    viewModel { (cardId: String) ->
        CharacterDetailViewModel(cardId = cardId, cardRepository = get())
    }

    viewModelOf(::WorldbookViewModel)
    viewModelOf(::MemoryViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::SettingsViewModel)
}

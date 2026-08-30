package com.luzzymeow.luzzyrp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavKey
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.ui.navigation.Route
import com.luzzymeow.luzzyrp.ui.pages.character.CharacterDetailPage
import com.luzzymeow.luzzyrp.ui.pages.character.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.favorites.FavoritesPage
import com.luzzymeow.luzzyrp.ui.pages.history.HistoryPage
import com.luzzymeow.luzzyrp.ui.pages.home.HomePage
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsAboutPage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsAppearancePage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsGenerationPage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsPage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsProviderDetailPage
import com.luzzymeow.luzzyrp.ui.pages.settings.SettingsProvidersPage
import com.luzzymeow.luzzyrp.ui.pages.worldbook.WorldbookPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import com.luzzymeow.luzzyrp.ui.theme.ThemeMode
import org.koin.compose.koinInject

/**
 * 应用路由壳（Navigation3）。
 *
 * [INVARIANT-STREAMING] 页面转场不影响生成任务：ChatService 持有任务于
 * appScope，离页返回后 StateFlow 单一真源自动恢复流式渲染（S5）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = koinInject<SettingsStore>()
            val settings by settingsStore.settingsFlow.collectAsState(initial = null)
            LuzzyTheme(mode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                LuzzyAppShell()
            }
        }
    }
}

@Composable
private fun LuzzyAppShell() {
    val backStack = rememberNavBackStack(Route.Home as NavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            // 进入：右滑入 + 淡入（300ms）；退出：左滑出（195ms）—— MotionTokens
            (slideInHorizontally(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER)) { it / 4 } +
                fadeIn(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER)))
                .togetherWith(
                    slideOutHorizontally(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT)) { -it / 6 } +
                        fadeOut(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT))
                )
        },
        popTransitionSpec = {
            (slideInHorizontally(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER)) { -it / 6 } +
                fadeIn(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER)))
                .togetherWith(
                    slideOutHorizontally(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT)) { it / 4 } +
                        fadeOut(tween(com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT))
                )
        },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomePage(
                    onOpenChat = { id -> backStack.add(Route.Chat(id)) },
                    onOpenRoute = { route -> backStack.add(route) },
                )
            }
            entry<Route.Chat> { key ->
                ChatPage(
                    conversationId = key.conversationId,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.Characters> {
                CharactersPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDetail = { id -> backStack.add(Route.CharacterDetail(id)) },
                )
            }
            entry<Route.CharacterDetail> { key ->
                CharacterDetailPage(cardId = key.cardId, onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.Worldbooks> { WorldbookPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.Memory> { MemoryPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.Favorites> { FavoritesPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.History> { HistoryPage(
                onBack = { backStack.removeLastOrNull() },
                onOpenChat = { id -> backStack.add(Route.Chat(id)) },
            ) }
            entry<Route.Settings> { SettingsPage(
                onBack = { backStack.removeLastOrNull() },
                onOpenRoute = { route -> backStack.add(route) },
            ) }
            entry<Route.SettingsProviders> { SettingsProvidersPage(
                onBack = { backStack.removeLastOrNull() },
                onOpenDetail = { id -> backStack.add(Route.SettingsProviderDetail(id)) },
            ) }
            entry<Route.SettingsProviderDetail> { key ->
                SettingsProviderDetailPage(providerId = key.providerId, onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.SettingsGeneration> { SettingsGenerationPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.SettingsAppearance> { SettingsAppearancePage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.SettingsAbout> { SettingsAboutPage(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}

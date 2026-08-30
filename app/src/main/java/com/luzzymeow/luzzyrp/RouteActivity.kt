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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.ui.navigation.Route
import com.luzzymeow.luzzyrp.ui.pages.character.CharacterDetailPage
import com.luzzymeow.luzzyrp.ui.pages.character.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPage
import com.luzzymeow.luzzyrp.ui.pages.home.HomePage
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.presets.PresetDetailPage
import com.luzzymeow.luzzyrp.ui.pages.presets.PresetsPage
import com.luzzymeow.luzzyrp.ui.pages.profile.ProfilePage
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

    // 启动直达（3）：自动打开上次会话；首次默认打开鹿溪对话
    val settingsStore = koinInject<SettingsStore>()
    val cardRepository = koinInject<com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository>()
    val conversationRepository = koinInject<com.luzzymeow.luzzyrp.data.repository.ConversationRepository>()
    val settings by settingsStore.settingsFlow.collectAsState(initial = null)
    var launched by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (launched || settings == null) return@LaunchedEffect
        if (backStack.size != 1) { launched = true; return@LaunchedEffect }
        launched = true
        val target = settings!!.lastConversationId
        if (target.isNotBlank()) {
            backStack.add(Route.Chat(target))
        } else {
            val cardId = settings!!.defaultCardId.ifBlank {
                com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository.BUILTIN_CARD_ID
            }
            val card = cardRepository.getById(cardId)
            // <CUT> 分隔多泡开场白
            val greeting = card?.firstMes?.takeIf { it.isNotBlank() }?.let { firstMes ->
                firstMes.split("<CUT>").map { it.trim() }.filter { it.isNotEmpty() }
                    .map { seg -> com.luzzymeow.luzzyrp.core.model.UIMessage(
                        role = com.luzzymeow.luzzyrp.core.model.Role.ASSISTANT,
                        parts = listOf(com.luzzymeow.luzzyrp.core.model.UIMessagePart.Text(seg))) }
            }.orEmpty()
            val conversation = com.luzzymeow.luzzyrp.core.model.Conversation(
                id = java.util.UUID.randomUUID().toString(),
                title = card?.name?.let { "与$it 的故事" } ?: "新对话",
                cardId = card?.id,
            )
            val created = conversationRepository.createConversation(conversation, greeting)
            backStack.add(Route.Chat(created.id))
        }
    }

    // 全局系统栏留白：内容不与状态栏/导航栏重叠（edge-to-edge 下必需）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                // 转场 v2：fade 常驻（避免滑动残影），位移交由共享轴感 slide¼
                val enterT = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER
                val exitT = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT
                (fadeIn(tween(enterT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingEnter)) +
                    slideInHorizontally(tween(enterT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingEnter)) { it / 6 })
                    .togetherWith(
                        fadeOut(tween(exitT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingExit)) +
                            slideOutHorizontally(tween(exitT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingExit)) { -it / 8 }
                    )
            },
            popTransitionSpec = {
                val enterT = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_ENTER
                val exitT = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.DURATION_EXIT
                (fadeIn(tween(enterT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingEnter)) +
                    slideInHorizontally(tween(enterT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingEnter)) { -it / 8 })
                    .togetherWith(
                        fadeOut(tween(exitT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingExit)) +
                            slideOutHorizontally(tween(exitT, easing = com.luzzymeow.luzzyrp.ui.theme.MotionTokens.EasingExit)) { it / 6 }
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
                        onSwitchChat = { id ->
                            backStack.removeLastOrNull()
                            backStack.add(Route.Chat(id))
                        },
                    )
                }
                entry<Route.Characters> {
                    CharactersPage(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenDetail = { id -> backStack.add(Route.CharacterDetail(id)) },
                    )
                }
                entry<Route.CharacterDetail> { key ->
                    CharacterDetailPage(
                        cardId = key.cardId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenWorldbook = { id ->
                            backStack.add(Route.Worldbook(id))
                        },
                    )
                }
                entry<Route.Worldbook> { key ->
                    WorldbookPage(cardId = key.cardId, onBack = { backStack.removeLastOrNull() })
                }
                entry<Route.MemorySettings> { MemoryPage(onBack = { backStack.removeLastOrNull() }) }
                entry<Route.Presets> { PresetsPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDetail = { id -> backStack.add(Route.PresetDetail(id)) },
                ) }
                entry<Route.PresetDetail> { key ->
                    PresetDetailPage(presetId = key.presetId, onBack = { backStack.removeLastOrNull() })
                }
                entry<Route.Profile> { ProfilePage(onBack = { backStack.removeLastOrNull() }) }
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
}

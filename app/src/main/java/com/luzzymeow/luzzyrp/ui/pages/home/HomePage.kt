package com.luzzymeow.luzzyrp.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.core.model.Conversation
import com.luzzymeow.luzzyrp.core.model.MessageNode
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.repository.CharacterCardRepository
import com.luzzymeow.luzzyrp.data.repository.ConversationRepository
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.navigation.Route
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** 首页 ViewModel：会话列表 / 新建 / 搜索。 */
class HomeViewModel(
    private val conversationRepository: ConversationRepository,
    private val cardRepository: CharacterCardRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = conversationRepository.observeActiveConversations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: StateFlow<String> = _query

    val filtered: StateFlow<List<Conversation>> =
        kotlinx.coroutines.flow.combine(conversations, _query) { list, q ->
            if (q.isBlank()) list else list.filter { it.title.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) { _query.value = q }

    /**
     * 新建会话：默认绑定设置中的角色卡，并把角色卡开场白作为首条消息。
     */
    fun newConversation(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val settings = settingsStore.settingsFlow.first()
            val cardId = settings.defaultCardId.ifBlank { CharacterCardRepository.BUILTIN_CARD_ID }
            val card = cardRepository.getById(cardId)
            // <CUT> 分割多条开场白（5.4：一角色多气泡输出）
            val greeting = card?.firstMes?.takeIf { it.isNotBlank() }?.let { firstMes ->
                firstMes.split("<CUT>")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { segment ->
                        UIMessage(role = Role.ASSISTANT, parts = listOf(UIMessagePart.Text(segment)))
                    }
            } ?: emptyList()
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                title = card?.name?.let { "与$it 的故事" } ?: "新对话",
                cardId = card?.id,
            )
            val created = conversationRepository.createConversation(conversation, greeting)
            onReady(created.id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch { conversationRepository.deleteConversation(id) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { conversationRepository.setPinned(id, pinned) }
    }
}

@Composable
fun HomePage(
    onOpenChat: (String) -> Unit,
    onOpenRoute: (Route) -> Unit,
) {
    // 实际注入见 koinViewModel
    HomePageContent(onOpenChat, onOpenRoute)
}

@Composable
private fun HomePageContent(onOpenChat: (String) -> Unit, onOpenRoute: (Route) -> Unit) {
    val viewModel: HomeViewModel = org.koin.androidx.compose.koinViewModel()
    val conversations by viewModel.filtered.collectAsState()
    val query by viewModel.query.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerMenu(onOpenRoute = {
                    onOpenRoute(it)
                })
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // —— 顶栏 ——
            // 问候区（DESIGN.md v2：时段问候 + 极光渐变标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LuzzySpacing.LG, vertical = LuzzySpacing.SM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(painterResource(LuzzyIcons.Menu.res), contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(com.luzzymeow.luzzyrp.ui.theme.LuzzyIconSize.Emphasis))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        greetingText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "LuzzyRP",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            brush = com.luzzymeow.luzzyrp.ui.theme.AuroraBrush.primary,
                        ),
                    )
                }
                IconButton(onClick = { viewModel.newConversation(onReady = onOpenChat) }) {
                    Icon(painterResource(LuzzyIcons.NewChat.res), contentDescription = "聊天",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(com.luzzymeow.luzzyrp.ui.theme.LuzzyIconSize.Emphasis))
                }
            }

            // —— 搜索 ——
            Row(
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.luzzymeow.luzzyrp.ui.components.LuzzyTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "搜索会话…",
                    leadingIconRes = LuzzyIcons.Search.res,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.size(LuzzySpacing.SM))

            // —— 会话列表（进入 stagger 动画）——
            if (conversations.isEmpty()) {
                com.luzzymeow.luzzyrp.ui.components.EmptyState(
                    iconRes = LuzzyIcons.Quill.res,
                    title = "故事从这里开始",
                    hint = "点击右上角羽毛笔，与鹿溪开始第一段对话。",
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
                verticalArrangement = Arrangement.spacedBy(LuzzySpacing.SM),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        onClick = { onOpenChat(conversation.id) },
                        onDelete = { viewModel.deleteConversation(conversation.id) },
                        onPin = { viewModel.setPinned(conversation.id, !conversation.pinned) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    AuroraSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LuzzySpacing.LG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    conversation.title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(LuzzySpacing.MD))
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (conversation.cardId != null) "角色扮演" else "普通对话",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPin) {
                Icon(
                    painterResource(if (conversation.pinned) LuzzyIcons.StarFilled.res else LuzzyIcons.Star.res),
                    contentDescription = "置顶",
                    tint = if (conversation.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(LuzzyIcons.Delete.res),
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerMenu(onOpenRoute: (Route) -> Unit) {
    Column(modifier = Modifier.padding(LuzzySpacing.LG)) {
        Spacer(Modifier.size(LuzzySpacing.LG))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = painterResource(com.luzzymeow.luzzyrp.R.drawable.luzzy_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(LuzzySpacing.MD))
            Column {
                Text("LuzzyRP", style = MaterialTheme.typography.titleMedium)
                Text("每次对话，都像一本有你的小说。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(LuzzySpacing.XL))
        // 菜单（v0.2.0 重构：4.1-4.7）
        DrawerItem(LuzzyIcons.Chat.res, "聊天", onCloseDrawer = { /* 已在聊天首页 */ })
        DrawerItem(LuzzyIcons.User.res, "角色卡", onCloseDrawer = { onOpenRoute(Route.Characters) })
        DrawerItem(LuzzyIcons.Quill.res, "预设", onCloseDrawer = { onOpenRoute(Route.Presets) })
        DrawerItem(LuzzyIcons.Sparkle.res, "用户档案", onCloseDrawer = { onOpenRoute(Route.Profile) })
        Spacer(Modifier.size(LuzzySpacing.MD))
        DrawerItem(LuzzyIcons.Settings.res, "设置", onCloseDrawer = { onOpenRoute(Route.Settings) })
    }
}

@Composable
private fun DrawerItem(iconRes: Int, label: String, onCloseDrawer: () -> Unit) {
    AuroraSurface(
        onClick = onCloseDrawer,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(iconRes), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(LuzzySpacing.MD))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 时段问候（DESIGN.md v2「HomePage 问候区」）。 */
private fun greetingText(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "午安"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}
